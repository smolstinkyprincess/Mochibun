package com.crinklecodone.mochibun.ui


import android.content.Context
import android.content.Intent
import android.util.Log
import android.media.AudioManager
import android.widget.Toast
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import com.android.quickstep.RecentsActivity
import com.crinklecodone.mochibun.core.haptic.HapticManager
import com.crinklecodone.mochibun.core.keypad.HomeTransitionTracker
import com.crinklecodone.mochibun.core.keypad.LauncherKeypadActions
import com.crinklecodone.mochibun.core.keypad.LauncherKeypadState
import com.crinklecodone.mochibun.core.keypad.T9InputState
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.core.system.TorchManager
import com.crinklecodone.mochibun.data.model.ActionType
import com.crinklecodone.mochibun.data.model.FavoriteSlotItem
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.isDpadOrActionKey
import com.crinklecodone.mochibun.ui.screens.firstLetterOf
import com.crinklecodone.mochibun.ui.screens.launchClockApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Triggers a subtle tactile boundary haptic buzz (14ms) when hitting edge limits of lists/carousels.
 */
internal fun performBoundaryHaptic(context: Context) {
    HapticManager.performBoundaryHaptic(context)
}

private val T9_LETTERS_2 = charArrayOf('A', 'B', 'C')
private val T9_LETTERS_3 = charArrayOf('D', 'E', 'F')
private val T9_LETTERS_4 = charArrayOf('G', 'H', 'I')
private val T9_LETTERS_5 = charArrayOf('J', 'K', 'L')
private val T9_LETTERS_6 = charArrayOf('M', 'N', 'O')
private val T9_LETTERS_7 = charArrayOf('P', 'Q', 'R', 'S')
private val T9_LETTERS_8 = charArrayOf('T', 'U', 'V')
private val T9_LETTERS_9 = charArrayOf('W', 'X', 'Y', 'Z')
private val T9_LETTERS_NONE = CharArray(0)

/**
 * Physical keypad and D-pad event coordinator for the Chinoe AT-M120 flip phone.
 * Decomposes key events into structured, testable sub-handlers for Center, Numeric/T9,
 * D-Pad navigation, and dismissal keys.
 */
private const val APP_SWITCH_HOLD_MS = 400L

internal class LauncherKeyHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pagerState: PagerState,
    private val listState: LazyListState,
    private val homeTracker: HomeTransitionTracker,
    private val t9State: T9InputState,
    private val actions: LauncherKeypadActions
) {
    private var lastConsumedDownKeyCode: Int? = null
    private var lastConsumedDownScanCode: Int? = null

    // Recents key held on Home: pending until the hold fires (opens Recents) or the key is released
    // first (a short press, which is the LSK move to Stats). Timed rather than repeat-based, like
    // the Center and '#' holds.
    private var appSwitchHoldJob: Job? = null

    fun handleKeyEvent(
        event: ComposeKeyEvent,
        inputState: LauncherKeypadState
    ): Boolean {
        val nativeEvent = event.nativeKeyEvent

        val isHome = !inputState.drawerOpen &&
            !inputState.anyModalOpen &&
            (inputState.pendingTargetPage == null || inputState.pendingTargetPage == 1) &&
            pagerState.currentPage == 1 &&
            pagerState.targetPage == 1 &&
            pagerState.settledPage == 1 &&
            !pagerState.isScrollInProgress

        val isTransitioning = !inputState.drawerOpen &&
            (pagerState.isScrollInProgress || (inputState.pendingTargetPage != null && inputState.pendingTargetPage != pagerState.settledPage))

        val state = inputState.copy(
            isHomeScreenActive = isHome,
            isPageTransitioning = isTransitioning,
            currentPage = pagerState.currentPage
        )

        if (nativeEvent.action == KeyEvent.ACTION_UP && isAppSwitchKey(nativeEvent)) {
            val pendingHold = appSwitchHoldJob
            if (pendingHold != null) {
                // Released before the hold threshold: short press
                pendingHold.cancel()
                appSwitchHoldJob = null
                navigateToStatsPage()
            }
        }

        if (nativeEvent.action == KeyEvent.ACTION_UP) {
            val matchesDown = (lastConsumedDownKeyCode != null && nativeEvent.keyCode == lastConsumedDownKeyCode) ||
                (lastConsumedDownScanCode != null && nativeEvent.scanCode > 0 && nativeEvent.scanCode == lastConsumedDownScanCode)
            if (matchesDown) {
                lastConsumedDownKeyCode = null
                lastConsumedDownScanCode = null
                return true
            }
        }

        if (state.isModalActive) {
            return false
        }

        val softKeyHandled = handleSoftKey(nativeEvent, state)
        if (softKeyHandled != null) {
            if (softKeyHandled && nativeEvent.action == KeyEvent.ACTION_DOWN) {
                lastConsumedDownKeyCode = nativeEvent.keyCode
                lastConsumedDownScanCode = nativeEvent.scanCode
            }
            return softKeyHandled
        }

        if (state.isPageTransitioning) {
            homeTracker.wasActive = false
            return isDpadOrActionKey(nativeEvent.keyCode)
        }

        if (state.isHomeScreenActive) {
            if (!homeTracker.wasActive) {
                homeTracker.wasActive = true
                homeTracker.settledTimestamp = SystemClock.uptimeMillis()
                val favCount = state.favoriteItemCount.coerceAtLeast(state.favoriteItems.size)
                actions.setSelectedFavoriteIndex(
                    state.savedFavoriteIndexOnHome.coerceIn(
                        0,
                        (favCount - 1).coerceAtLeast(0)
                    )
                )
                if (favCount > 0) {
                    actions.setHomeFocusArea(0)
                }
            }
        } else {
            homeTracker.wasActive = false
        }

        if (!state.drawerOpen && !state.isHomeScreenActive) {
            return false
        }

        if (isCenterActivateKey(nativeEvent.keyCode)) {
            return handleCenterKey(nativeEvent, state)
        }

        if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
            return handleActionDown(nativeEvent, state)
        }

        if (nativeEvent.action == KeyEvent.ACTION_UP) {
            val isSoftKeyOrBack = nativeEvent.scanCode == 59 || nativeEvent.keyCode == KeyEvent.KEYCODE_F1 ||
                nativeEvent.scanCode == 212 || nativeEvent.keyCode == KeyEvent.KEYCODE_CAMERA ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_APP_SWITCH || nativeEvent.scanCode == 580 ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_MENU ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_BACK || nativeEvent.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_F4

            if (isSoftKeyOrBack) {
                return true
            }

            if (PreferencesManager.matchesKaomojiTrigger(nativeEvent.keyCode, nativeEvent.scanCode, context)) {
                val wasShortPress = state.poundKeyState == 1
                actions.setPoundKeyState(0)
                if (wasShortPress) {
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_POUND -> {
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                            actions.openLauncherMenu()
                        }
                        KeyEvent.KEYCODE_STAR,
                        KeyEvent.KEYCODE_MENU -> {
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                            actions.openContextActionForCurrentFocus()
                        }
                        KeyEvent.KEYCODE_F4 -> {
                            if (state.drawerOpen) {
                                SoundEffectManager.play(context, SoundEffect.BACK)
                                if (state.isDrawerRailFocused) {
                                    actions.setDrawerRailFocused(false)
                                } else {
                                    actions.setDrawerOpen(false)
                                }
                            }
                        }
                        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9,
                        in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> {
                            handleNumericKey(nativeEvent, state)
                        }
                    }
                }
                return true
            }

            when (nativeEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    return handleCenterKey(nativeEvent, state)
                }
            }
        }

        return false
    }

    private fun handleCenterKey(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        if (state.drawerOpen && state.isDrawerRailFocused) {
            when (nativeEvent.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (nativeEvent.repeatCount == 0) {
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        actions.setDrawerRailFocused(false)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    return true
                }
            }
        }

        when (nativeEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (nativeEvent.repeatCount == 0) {
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    actions.setCenterKeyState(1)
                } else if (state.centerKeyState == 1) {
                    actions.setCenterKeyState(2)
                    actions.openContextActionForCurrentFocus()
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                val wasShortPress = state.centerKeyState == 1
                actions.setCenterKeyState(0)
                if (wasShortPress) {
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                    if (state.drawerOpen) {
                        if (state.selectedDrawerIndex == -1) {
                            actions.setDrawerOpen(false)
                        } else {
                            state.visibleApps.getOrNull(state.selectedDrawerIndex)?.let { app ->
                                actions.recordAppLaunch(app.packageName)
                                context.packageManager.getLaunchIntentForPackage(
                                    app.packageName
                                )?.let(context::startActivity)
                            }
                        }
                    } else if (state.isHomeScreenActive) {
                        val favCount = state.favoriteItemCount.coerceAtLeast(state.favoriteItems.size)
                        if (state.homeFocusArea == 0 && favCount > 0) {
                            if (state.favoriteItems.isNotEmpty()) {
                                when (val item = state.favoriteItems.getOrNull(state.selectedFavoriteIndex)) {
                                    is FavoriteSlotItem.App -> {
                                        actions.recordAppLaunch(item.app.packageName)
                                        context.packageManager.getLaunchIntentForPackage(
                                            item.app.packageName
                                        )?.let(context::startActivity)
                                    }
                                    is FavoriteSlotItem.Folder -> {
                                        actions.openFolder(item.folder.id)
                                    }
                                    null -> {}
                                }
                            } else if (state.selectedFavoriteIndex < state.favoriteApps.size) {
                                state.favoriteApps.getOrNull(state.selectedFavoriteIndex)?.let { app ->
                                    actions.recordAppLaunch(app.packageName)
                                    context.packageManager.getLaunchIntentForPackage(
                                        app.packageName
                                    )?.let(context::startActivity)
                                }
                            } else {
                                state.favoriteFolders.getOrNull(
                                    state.selectedFavoriteIndex - state.favoriteApps.size
                                )?.let { folder ->
                                    actions.openFolder(folder.id)
                                }
                            }
                        } else if (state.homeFocusArea == 2) {
                            actions.setSelectedDrawerIndex(0)
                            actions.setDrawerOpen(true)
                        } else if (state.homeFocusArea == 3) {
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                            launchClockApp(context)
                        } else if (state.homeFocusArea == 4) {
                            actions.boopMascot()
                        } else {
                            actions.openLauncherMenu()
                        }
                    }
                }
                return true
            }

            else -> return false
        }
    }

    private fun handleSoftKey(
        nativeEvent: KeyEvent,
        state: LauncherKeypadState
    ): Boolean? {
        val isLeftSoftKey = nativeEvent.scanCode == 59 ||
            nativeEvent.keyCode == KeyEvent.KEYCODE_F1 ||
            nativeEvent.keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
            nativeEvent.scanCode == 580 ||
            nativeEvent.keyCode == KeyEvent.KEYCODE_MENU

        if (isLeftSoftKey) {
            if (state.isDrawerActive) {
                // In App Drawer: LSK opens App Options (AppActionDialog) for highlighted app
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    if (nativeEvent.repeatCount == 0) {
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                        actions.openContextActionForCurrentFocus()
                    }
                    return true
                }
                if (nativeEvent.action == KeyEvent.ACTION_UP) {
                    return true
                }
            } else if (state.isHomeScreenActive) {
                // On Home Screen: LSK navigates left to Stats (Page 0), consumes event (blocks stock task switcher).
                // The Recents key waits for release instead: holding it opens Recents.
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    if (nativeEvent.repeatCount == 0) {
                        if (isAppSwitchKey(nativeEvent)) {
                            startAppSwitchHold()
                        } else {
                            navigateToStatsPage()
                        }
                    }
                    return true
                }
                if (nativeEvent.action == KeyEvent.ACTION_UP) {
                    return true
                }
            } else if (state.isSidePageActive) {
                // On Side Pages (Stats Page 0 or Notifs Page 2): LSK retains side navigation, consumes event (blocks stock task switcher)
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    if (nativeEvent.repeatCount == 0) {
                        if (state.currentPage == 2) {
                            // On Notifications Page 2: Navigate left to Stats (Page 0)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            actions.setPendingTargetPage(0)
                            scope.launch { pagerState.animateScrollToPage(0) }
                        } else {
                            // On Stats Page 0: Already on leftmost page, retain side navigation
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                        }
                    }
                    return true
                }
                if (nativeEvent.action == KeyEvent.ACTION_UP) {
                    return true
                }
            } else if (state.isPageTransitioning) {
                return true
            }
        }

        val isRightSoftKey = nativeEvent.keyCode == KeyEvent.KEYCODE_BACK ||
            nativeEvent.keyCode == KeyEvent.KEYCODE_ESCAPE ||
            nativeEvent.keyCode == KeyEvent.KEYCODE_F4 ||
            nativeEvent.scanCode == 212 ||
            nativeEvent.keyCode == KeyEvent.KEYCODE_CAMERA

        if (isRightSoftKey) {
            if (state.isDrawerActive) {
                // In App Drawer: RSK cycles drawer sort mode, consumes event (stays in drawer, does NOT return home)
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    if (nativeEvent.repeatCount == 0) {
                        actions.cycleDrawerSortMode()
                    }
                    return true
                }
                if (nativeEvent.action == KeyEvent.ACTION_UP) {
                    return true
                }
            } else if (state.isHomeScreenActive) {
                // On Home Screen: RSK navigates right to Notifications (Page 2), consumes event (blocks default back minimization).
                // The side key (F4 / scan 62) is consumed without navigating; it belongs to the virtual cursor toggle.
                val isSideKey = nativeEvent.keyCode == KeyEvent.KEYCODE_F4 || nativeEvent.scanCode == 62
                if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                    if (nativeEvent.repeatCount == 0 && !isSideKey) {
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        actions.setPendingTargetPage(2)
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }
                    return true
                }
                if (nativeEvent.action == KeyEvent.ACTION_UP) {
                    return true
                }
            } else if (state.isSidePageActive) {
                // On Side Pages: Let KEYCODE_BACK pass through to Compose BackHandler(enabled = isSidePageActive)
                // which smoothly animates back to Home (Page 1)
                return null
            } else if (state.isPageTransitioning) {
                return true
            }
        }

        return null
    }

    private fun handleActionDown(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        if (PreferencesManager.matchesKaomojiTrigger(nativeEvent.keyCode, nativeEvent.scanCode, context)) {
            if (nativeEvent.repeatCount == 0) {
                SoundEffectManager.play(context, SoundEffect.CLICK)
                actions.setPoundKeyState(1)
            } else if (state.poundKeyState == 1 || nativeEvent.isLongPress) {
                actions.setPoundKeyState(2)
                performBoundaryHaptic(context)
                SoundEffectManager.play(context, SoundEffect.CLICK)
                actions.openKaomojiWheel()
            }
            return true
        }

        when (nativeEvent.keyCode) {

            KeyEvent.KEYCODE_STAR -> {
                if (nativeEvent.repeatCount == 0) {
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    actions.openContextActionForCurrentFocus()
                } else if (nativeEvent.repeatCount == 1) {
                    toggleRingerMode(context)
                }
                return true
            }

            KeyEvent.KEYCODE_POUND -> {
                if (nativeEvent.repeatCount == 0) {
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                }
                return true
            }

            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0,
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1,
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2,
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4,
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5,
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6,
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8,
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> {
                return handleNumericKey(nativeEvent, state)
            }

            KeyEvent.KEYCODE_DPAD_UP -> return handleDpadUp(nativeEvent, state)
            KeyEvent.KEYCODE_DPAD_DOWN -> return handleDpadDown(nativeEvent, state)
            KeyEvent.KEYCODE_DPAD_LEFT -> return handleDpadLeft(nativeEvent, state)
            KeyEvent.KEYCODE_DPAD_RIGHT -> return handleDpadRight(nativeEvent, state)

            else -> return false
        }
    }

    private fun jumpToLetter(letter: Char, state: LauncherKeypadState) {
        val target = state.letterStarts[letter]
            ?: state.letterStarts.entries.filter { it.key > letter }.minByOrNull { it.key }?.value
            ?: (state.visibleApps.size - 1)
        if (target >= 0 && state.visibleApps.isNotEmpty()) {
            actions.setSelectedDrawerIndex(target)
            scope.launch { listState.scrollToItem(target) }
        }
    }

    private fun handleNumericKey(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        if (nativeEvent.repeatCount == 0) {
            SoundEffectManager.play(context, SoundEffect.CLICK)
        }

        val speedDialDigit = when (nativeEvent.keyCode) {
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
            else -> null
        }

        if (!state.drawerOpen && nativeEvent.repeatCount == 1) {
            val config = HardwareRemapManager.getButtonActionConfigForKey(
                context,
                nativeEvent.keyCode,
                nativeEvent.scanCode
            )
            when (val holdAction = config.longPressAction) {
                is ActionType.LaunchApp -> {
                    actions.recordAppLaunch(holdAction.packageName)
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(holdAction.packageName)
                    if (launchIntent != null) {
                        performBoundaryHaptic(context)
                        context.startActivity(launchIntent)
                        return true
                    }
                }
                is ActionType.ToggleFlashlight -> {
                    val isTorchOn = TorchManager.toggleTorch(context)
                    performBoundaryHaptic(context)
                    if (isTorchOn == true) {
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        Toast.makeText(context, "🔦 Flashlight On", Toast.LENGTH_SHORT).show()
                    } else if (isTorchOn == false) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        Toast.makeText(context, "🔦 Flashlight Off", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                is ActionType.GlobalRecents,
                is ActionType.OpenRecentsSystem -> {
                    performBoundaryHaptic(context)
                    try {
                        val intent = android.content.Intent(context, com.android.quickstep.RecentsActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                    return true
                }
                is ActionType.GlobalNotifications -> {
                    performBoundaryHaptic(context)
                    try {
                        @Suppress("WrongConstant")
                        val statusBarService = context.getSystemService("statusbar")
                        val statusBarManager = Class.forName("android.app.StatusBarManager")
                        statusBarManager.getMethod("expandNotificationsPanel").invoke(statusBarService)
                    } catch (_: Exception) {}
                    return true
                }
                is ActionType.GlobalBack -> {
                    performBoundaryHaptic(context)
                    return true
                }
                is ActionType.OpenWidgetsPage -> {
                    performBoundaryHaptic(context)
                    actions.setPendingTargetPage(0)
                    scope.launch { pagerState.animateScrollToPage(0) }
                    return true
                }
                is ActionType.OpenNotificationsPage -> {
                    performBoundaryHaptic(context)
                    actions.setPendingTargetPage(2)
                    scope.launch { pagerState.animateScrollToPage(2) }
                    return true
                }
                is ActionType.None -> {
                    if (speedDialDigit != null) {
                        performBoundaryHaptic(context)
                        actions.openSpeedDial(speedDialDigit)
                        return true
                    }
                }
            }
        }

        if (nativeEvent.keyCode == KeyEvent.KEYCODE_0 || nativeEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_0) {
            if (nativeEvent.repeatCount == 1 && state.isHomeScreenActive) {
                val isTorchOn = TorchManager.toggleTorch(context)
                performBoundaryHaptic(context)
                if (isTorchOn == true) {
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                    Toast.makeText(context, "🔦 Flashlight On", Toast.LENGTH_SHORT).show()
                } else if (isTorchOn == false) {
                    SoundEffectManager.play(context, SoundEffect.BACK)
                    Toast.makeText(context, "🔦 Flashlight Off", Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }

        if (nativeEvent.repeatCount > 0) return true

        if (!state.drawerOpen) {
            return true
        }

        val t9Letters = when (nativeEvent.keyCode) {
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> T9_LETTERS_2
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> T9_LETTERS_3
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> T9_LETTERS_4
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> T9_LETTERS_5
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> T9_LETTERS_6
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> T9_LETTERS_7
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> T9_LETTERS_8
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> T9_LETTERS_9
            else -> T9_LETTERS_NONE
        }

        if (t9Letters.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val currentKey = nativeEvent.keyCode
            val idx = if (t9State.t9Key == currentKey && (now - t9State.t9Time) < 1000L) {
                (t9State.t9Index + 1) % t9Letters.size
            } else {
                0
            }
            t9State.t9Key = currentKey
            t9State.t9Index = idx
            t9State.t9Time = now

            val letter = t9Letters[idx]
            if (state.isDrawerRailFocused) {
                actions.setDrawerRailHoveredLetter(letter)
            }
            jumpToLetter(letter, state)
        }
        return true
    }

    private fun handleDpadUp(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        val wrapAround = PreferencesManager.isDpadWrapAround(context)
        if (state.drawerOpen) {
            if (state.isDrawerRailFocused) {
                val currentLetter = state.drawerRailHoveredLetter ?: 'A'
                val currentIndex = ('A'..'Z').indexOf(currentLetter).coerceAtLeast(0)
                if (currentIndex <= 0) {
                    if (wrapAround) {
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        val newLetter = 'Z'
                        actions.setDrawerRailHoveredLetter(newLetter)
                        jumpToLetter(newLetter, state)
                    } else {
                        performBoundaryHaptic(context)
                    }
                } else {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    val newLetter = ('A' + (currentIndex - 1))
                    actions.setDrawerRailHoveredLetter(newLetter)
                    jumpToLetter(newLetter, state)
                }
                return true
            }

            if (state.selectedDrawerIndex <= 0) {
                if (state.selectedDrawerIndex == 0) {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setSelectedDrawerIndex(-1)
                } else {
                    if (wrapAround && state.visibleApps.isNotEmpty()) {
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        val target = state.visibleApps.size - 1
                        actions.setSelectedDrawerIndex(target)
                        scope.launch { listState.scrollToItem(target) }
                    } else {
                        performBoundaryHaptic(context)
                        actions.setDrawerOpen(false)
                        actions.setSelectedDrawerIndex(0)
                    }
                }
            } else {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setSelectedDrawerIndex((state.selectedDrawerIndex - 1).coerceAtLeast(0))
            }
        } else if (state.isHomeScreenActive) {
            val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
            if (isCooldown) return true

            // Top to bottom: 1 header, 3 clock, 4 mascot, 0 favorites (skipped when empty), 2 drawer handle
            val favCount = state.favoriteItemCount.coerceAtLeast(state.favoriteItems.size)
            if (state.homeFocusArea == 2) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(if (favCount > 0) 0 else 4)
            } else if (state.homeFocusArea == 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(4)
            } else if (state.homeFocusArea == 4) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(3)
            } else if (state.homeFocusArea == 3) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(1)
            } else if (nativeEvent.repeatCount == 0) {
                performBoundaryHaptic(context)
            }
        }
        return true
    }

    private fun handleDpadDown(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        val wrapAround = PreferencesManager.isDpadWrapAround(context)
        if (state.drawerOpen) {
            if (state.isDrawerRailFocused) {
                val currentLetter = state.drawerRailHoveredLetter ?: 'A'
                val currentIndex = ('A'..'Z').indexOf(currentLetter).coerceAtLeast(0)
                if (currentIndex >= 25) {
                    if (wrapAround) {
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        val newLetter = 'A'
                        actions.setDrawerRailHoveredLetter(newLetter)
                        jumpToLetter(newLetter, state)
                    } else {
                        performBoundaryHaptic(context)
                    }
                } else {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    val newLetter = ('A' + (currentIndex + 1))
                    actions.setDrawerRailHoveredLetter(newLetter)
                    jumpToLetter(newLetter, state)
                }
                return true
            }

            if (state.selectedDrawerIndex < 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setSelectedDrawerIndex(0)
            } else if (state.selectedDrawerIndex >= state.visibleApps.size - 1) {
                if (wrapAround && state.visibleApps.isNotEmpty()) {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setSelectedDrawerIndex(0)
                    scope.launch { listState.scrollToItem(0) }
                } else {
                    performBoundaryHaptic(context)
                }
            } else {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setSelectedDrawerIndex(
                    (state.selectedDrawerIndex + 1).coerceIn(0, (state.visibleApps.size - 1).coerceAtLeast(0))
                )
            }
        } else if (state.isHomeScreenActive) {
            val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
            if (isCooldown) return true

            val favCount = state.favoriteItemCount.coerceAtLeast(state.favoriteItems.size)
            if (state.homeFocusArea == 1) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(3)
            } else if (state.homeFocusArea == 3) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(4)
            } else if (state.homeFocusArea == 4) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(if (favCount > 0) 0 else 2)
            } else if (state.homeFocusArea == 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(2)
            } else if (state.homeFocusArea == 2 && nativeEvent.repeatCount == 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setSelectedDrawerIndex(0)
                actions.setDrawerOpen(true)
            }
        }
        return true
    }

    private fun handleDpadLeft(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        val wrapAround = PreferencesManager.isDpadWrapAround(context)
        if (state.drawerOpen) {
            if (state.isDrawerRailFocused) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setDrawerRailFocused(false)
                return true
            }

            if (state.visibleApps.isNotEmpty()) {
                if (state.selectedDrawerIndex <= 0) {
                    if (wrapAround) {
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        val target = state.visibleApps.size - 1
                        actions.setSelectedDrawerIndex(target)
                        scope.launch { listState.scrollToItem(target) }
                    } else {
                        performBoundaryHaptic(context)
                    }
                } else {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setSelectedDrawerIndex(state.selectedDrawerIndex - 1)
                }
            }
        } else if (state.isHomeScreenActive) {
            val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
            if (isCooldown) return true

            val favCount = state.favoriteItemCount.coerceAtLeast(state.favoriteItems.size)
            if (state.homeFocusArea == 0 && favCount > 0 && state.selectedFavoriteIndex > 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                val newIdx = state.selectedFavoriteIndex - 1
                actions.setSelectedFavoriteIndex(newIdx)
                actions.setSavedFavoriteIndexOnHome(newIdx)
            } else if (nativeEvent.repeatCount == 0) {
                if (pagerState.currentPage > 0) {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setPendingTargetPage(0)
                    scope.launch { pagerState.animateScrollToPage(0) }
                } else {
                    performBoundaryHaptic(context)
                }
            }
        }
        return true
    }

    private fun handleDpadRight(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        val wrapAround = PreferencesManager.isDpadWrapAround(context)
        if (state.drawerOpen) {
            if (!state.isDrawerRailFocused) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                val currentLetter = if (state.selectedDrawerIndex in state.visibleApps.indices) {
                    firstLetterOf(state.visibleApps[state.selectedDrawerIndex].name)
                } else {
                    'A'
                }
                val startingLetter = if (currentLetter in 'A'..'Z') currentLetter else 'A'
                actions.setDrawerRailFocused(true)
                actions.setDrawerRailHoveredLetter(startingLetter)
                jumpToLetter(startingLetter, state)
                return true
            }
            return true
        } else if (state.isHomeScreenActive) {
            val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
            if (isCooldown) return true

            val favCount = state.favoriteItemCount.coerceAtLeast(state.favoriteItems.size)
            if (state.homeFocusArea == 0 && favCount > 0 && state.selectedFavoriteIndex < favCount - 1) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                val newIdx = state.selectedFavoriteIndex + 1
                actions.setSelectedFavoriteIndex(newIdx)
                actions.setSavedFavoriteIndexOnHome(newIdx)
            } else if (nativeEvent.repeatCount == 0) {
                if (pagerState.currentPage < 2) {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setPendingTargetPage(2)
                    scope.launch { pagerState.animateScrollToPage(2) }
                } else {
                    performBoundaryHaptic(context)
                }
            }
        }
        return true
    }

    private fun isAppSwitchKey(event: KeyEvent): Boolean =
        event.keyCode == KeyEvent.KEYCODE_APP_SWITCH || event.scanCode == 580

    private fun navigateToStatsPage() {
        SoundEffectManager.play(context, SoundEffect.NAV)
        actions.setPendingTargetPage(0)
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    private fun startAppSwitchHold() {
        appSwitchHoldJob?.cancel()
        appSwitchHoldJob = scope.launch {
            delay(APP_SWITCH_HOLD_MS)
            appSwitchHoldJob = null
            performBoundaryHaptic(context)
            SoundEffectManager.play(context, SoundEffect.SELECT)
            openRecents()
        }
    }

    /** Same task switcher SystemUI's Recents request reaches through MainActivity. */
    private fun openRecents() {
        if (RecentsActivity.shouldThrottleLaunch()) return
        try {
            context.startActivity(
                Intent(context, RecentsActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (e: Exception) {
            Log.w("LauncherKeyHandler", "Failed to open Recents", e)
        }
    }

    private fun toggleRingerMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    performBoundaryHaptic(context)
                    Toast.makeText(context, "📳 Vibrate Mode", Toast.LENGTH_SHORT).show()
                }
                AudioManager.RINGER_MODE_VIBRATE -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    Toast.makeText(context, "🔇 Silent Mode", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    performBoundaryHaptic(context)
                    SoundEffectManager.play(context, SoundEffect.SELECT)
                    Toast.makeText(context, "🔔 Normal Sound Mode", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: SecurityException) {
            val nextMode = if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
            audioManager.ringerMode = nextMode
            performBoundaryHaptic(context)
            val label = if (nextMode == AudioManager.RINGER_MODE_VIBRATE) "📳 Vibrate Mode" else "🔔 Sound Mode"
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }
    }
}
