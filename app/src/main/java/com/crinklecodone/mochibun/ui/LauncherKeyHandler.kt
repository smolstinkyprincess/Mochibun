package com.crinklecodone.mochibun.ui

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Triggers a subtle tactile boundary haptic buzz (14ms) when hitting edge limits of lists/carousels.
 */
internal fun performBoundaryHaptic(context: Context) {
    HapticManager.performBoundaryHaptic(context)
}

/**
 * Physical keypad and D-pad event coordinator for the Chinoe AT-M120 flip phone.
 * Decomposes key events into structured, testable sub-handlers for Center, Numeric/T9,
 * D-Pad navigation, and dismissal keys.
 */
internal class LauncherKeyHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val pagerState: PagerState,
    private val listState: LazyListState,
    private val homeTracker: HomeTransitionTracker,
    private val t9State: T9InputState,
    private val actions: LauncherKeypadActions
) {

    fun handleKeyEvent(
        event: ComposeKeyEvent,
        state: LauncherKeypadState
    ): Boolean {
        val nativeEvent = event.nativeKeyEvent

        if (state.anyModalOpen) {
            return false
        }

        if (state.isPageTransitioning) {
            homeTracker.wasActive = false
            return isDpadOrActionKey(nativeEvent.keyCode)
        }

        if (state.isHomeScreenActive) {
            if (!homeTracker.wasActive) {
                homeTracker.wasActive = true
                homeTracker.settledTimestamp = SystemClock.uptimeMillis()
                actions.setSelectedFavoriteIndex(
                    state.savedFavoriteIndexOnHome.coerceIn(
                        0,
                        (state.favoriteItemCount - 1).coerceAtLeast(0)
                    )
                )
                if (state.favoriteItemCount > 0 && state.homeFocusArea != 1) {
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
            if (PreferencesManager.matchesKaomojiTrigger(nativeEvent.keyCode, nativeEvent.scanCode, context)) {
                val wasShortPress = state.poundKeyState == 1
                actions.setPoundKeyState(0)
                if (wasShortPress && PreferencesManager.isKaomojiTriggerLongPress(context)) {
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
                                actions.setDrawerOpen(false)
                            }
                        }
                        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9,
                        in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> {
                            handleNumericKey(
                                KeyEvent(KeyEvent.ACTION_DOWN, nativeEvent.keyCode),
                                state
                            )
                        }
                    }
                }
                return true
            }

            if (nativeEvent.keyCode == KeyEvent.KEYCODE_POUND) {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                actions.openLauncherMenu()
                return true
            }

            if (isDpadOrActionKey(nativeEvent.keyCode) ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_APP_SWITCH ||
                (nativeEvent.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) ||
                (nativeEvent.keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_STAR ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_CALL ||
                isBackDismissKey(nativeEvent.keyCode) ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_F4 ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_F1 ||
                nativeEvent.keyCode == KeyEvent.KEYCODE_CAMERA
            ) {
                return true
            }
        }

        return false
    }

    private fun handleCenterKey(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
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
                                context.packageManager.getLaunchIntentForPackage(
                                    app.packageName
                                )?.let(context::startActivity)
                            }
                        }
                    } else if (state.isHomeScreenActive) {
                        if (state.homeFocusArea == 0 && state.favoriteItemCount > 0) {
                            if (state.favoriteItems.isNotEmpty()) {
                                when (val item = state.favoriteItems.getOrNull(state.selectedFavoriteIndex)) {
                                    is FavoriteSlotItem.App -> {
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

    private fun handleActionDown(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        if (PreferencesManager.matchesKaomojiTrigger(nativeEvent.keyCode, nativeEvent.scanCode, context)) {
            if (!PreferencesManager.isKaomojiTriggerLongPress(context)) {
                if (nativeEvent.repeatCount == 0) {
                    actions.setPoundKeyState(2)
                    performBoundaryHaptic(context)
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    actions.openKaomojiWheel()
                }
                return true
            } else {
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
        }

        if (nativeEvent.scanCode == 59) {
            if (nativeEvent.repeatCount == 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setPendingTargetPage(0)
                scope.launch { pagerState.animateScrollToPage(0) }
            }
            return true
        }
        if (nativeEvent.scanCode == 212) {
            if (nativeEvent.repeatCount == 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setPendingTargetPage(2)
                scope.launch { pagerState.animateScrollToPage(2) }
            }
            return true
        }

        when (nativeEvent.keyCode) {
            KeyEvent.KEYCODE_F1 -> {
                if (nativeEvent.repeatCount == 0) {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setPendingTargetPage(0)
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
                return true
            }

            KeyEvent.KEYCODE_CAMERA -> {
                if (nativeEvent.repeatCount == 0) {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setPendingTargetPage(2)
                    scope.launch { pagerState.animateScrollToPage(2) }
                }
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                if (nativeEvent.repeatCount == 0) {
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    actions.openContextActionForCurrentFocus()
                }
                return true
            }

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

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_F4 -> {
                if (state.drawerOpen) {
                    SoundEffectManager.play(context, SoundEffect.BACK)
                    actions.setDrawerOpen(false)
                    return true
                } else if (state.isHomeScreenActive && (state.homeFocusArea == 1 || state.homeFocusArea == 2) && state.favoriteItemCount > 0) {
                    SoundEffectManager.play(context, SoundEffect.BACK)
                    actions.setHomeFocusArea(0)
                    return true
                } else if (state.isHomeScreenActive) {
                    return true
                } else {
                    return false
                }
            }

            else -> return false
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
            if (nativeEvent.repeatCount == 1) {
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
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> listOf('A', 'B', 'C')
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> listOf('D', 'E', 'F')
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> listOf('G', 'H', 'I')
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> listOf('J', 'K', 'L')
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> listOf('M', 'N', 'O')
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> listOf('P', 'Q', 'R', 'S')
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> listOf('T', 'U', 'V')
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> listOf('W', 'X', 'Y', 'Z')
            else -> emptyList()
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
            val target = state.letterStarts[letter]
                ?: state.letterStarts.entries.firstOrNull { it.key > letter }?.value
                ?: (state.visibleApps.size - 1)
            if (target >= 0 && state.visibleApps.isNotEmpty()) {
                actions.setSelectedDrawerIndex(target)
                scope.launch { listState.scrollToItem(target) }
            }
        }
        return true
    }

    private fun handleDpadUp(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        val wrapAround = PreferencesManager.isDpadWrapAround(context)
        if (state.drawerOpen) {
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

            if (state.homeFocusArea == 2) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(if (state.favoriteItemCount > 0) 0 else 1)
            } else if (state.homeFocusArea == 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(1)
            } else if (nativeEvent.repeatCount == 0) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setSelectedDrawerIndex(0)
                actions.setDrawerOpen(true)
            }
        }
        return true
    }

    private fun handleDpadDown(nativeEvent: KeyEvent, state: LauncherKeypadState): Boolean {
        val wrapAround = PreferencesManager.isDpadWrapAround(context)
        if (state.drawerOpen) {
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

            if (state.homeFocusArea == 1) {
                SoundEffectManager.play(context, SoundEffect.NAV)
                actions.setHomeFocusArea(if (state.favoriteItemCount > 0) 0 else 2)
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

            if (state.homeFocusArea == 0 && state.favoriteItemCount > 0 && state.selectedFavoriteIndex > 0) {
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
            if (state.visibleApps.isNotEmpty()) {
                if (state.selectedDrawerIndex >= state.visibleApps.size - 1) {
                    if (wrapAround) {
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        actions.setSelectedDrawerIndex(0)
                        scope.launch { listState.scrollToItem(0) }
                    } else {
                        performBoundaryHaptic(context)
                    }
                } else {
                    SoundEffectManager.play(context, SoundEffect.NAV)
                    actions.setSelectedDrawerIndex(state.selectedDrawerIndex + 1)
                }
            }
        } else if (state.isHomeScreenActive) {
            val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
            if (isCooldown) return true

            if (state.homeFocusArea == 0 && state.favoriteItemCount > 0 && state.selectedFavoriteIndex < state.favoriteItemCount - 1) {
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
