package com.crinklecodone.mochibun.ui

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.FavoriteSlotItem
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.isDpadOrActionKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Triggers a subtle tactile boundary haptic buzz (14ms) when hitting edge limits of lists/carousels.
 */
fun performBoundaryHaptic(context: Context) {
    if (!PreferencesManager.isDpadBoundaryHaptics(context)) return
    try {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        val vibrator = vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        vibrator?.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {}
}

/**
 * Tracks home screen visibility and D-pad settlement cooldowns
 * across page swipes and drawer transitions.
 */
class HomeTransitionTracker {
    var wasActive: Boolean = false
    var settledTimestamp: Long = 0L
}

/**
 * Holds cyclic T9 multi-tap keypad state for physical numeric search.
 */
class T9InputState {
    var t9Key: Int? = null
    var t9Index: Int = 0
    var t9Time: Long = 0L
}

/**
 * Centralized physical keypad, D-pad, and shortcut event handler for Mochibun.
 * Isolates keypad navigation logic and maintains strictly deterministic D-pad contracts
 * for the Chinoe AT-M120 flip phone.
 */
fun handleMainLauncherKeyEvent(
    event: ComposeKeyEvent,
    context: Context,
    scope: CoroutineScope,
    pagerState: PagerState,
    listState: LazyListState,
    homeTracker: HomeTransitionTracker,
    t9State: T9InputState,
    anyModalOpen: Boolean,
    drawerOpen: Boolean,
    isHomeScreenActive: Boolean,
    pendingTargetPage: Int?,
    favoriteItemCount: Int,
    selectedFavoriteIndex: Int,
    savedFavoriteIndexOnHome: Int,
    homeFocusArea: Int,
    centerKeyState: Int,
    selectedDrawerIndex: Int,
    visibleApps: List<LauncherApp>,
    favoriteApps: List<LauncherApp> = emptyList(),
    favoriteFolders: List<FavoriteFolder> = emptyList(),
    favoriteItems: List<FavoriteSlotItem> = emptyList(),
    letterStarts: Map<Char, Int>,
    onSetDrawerOpen: (Boolean) -> Unit,
    onSetSelectedDrawerIndex: (Int) -> Unit,
    onSetHomeFocusArea: (Int) -> Unit,
    onSetSelectedFavoriteIndex: (Int) -> Unit,
    onSetSavedFavoriteIndexOnHome: (Int) -> Unit,
    onSetPendingTargetPage: (Int?) -> Unit,
    onSetCenterKeyState: (Int) -> Unit,
    onOpenContextActionForCurrentFocus: () -> Unit,
    onOpenFolder: (id: String) -> Unit,
    onOpenLauncherMenu: () -> Unit,
    onOpenSpeedDial: (digit: Int) -> Unit = {}
): Boolean {
    val nativeEvent = event.nativeKeyEvent

    // Let open dialogs handle their own keys
    if (anyModalOpen) {
        return false
    }

    val isPageTransitioning = !drawerOpen && (pagerState.isScrollInProgress || (pendingTargetPage != null && pendingTargetPage != pagerState.settledPage))
    if (isPageTransitioning) {
        homeTracker.wasActive = false
        if (isDpadOrActionKey(nativeEvent.keyCode)) {
            return true
        }
        return false
    }

    if (isHomeScreenActive) {
        if (!homeTracker.wasActive) {
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
            onSetSelectedFavoriteIndex(
                savedFavoriteIndexOnHome.coerceIn(
                    0,
                    (favoriteItemCount - 1).coerceAtLeast(0)
                )
            )
            if (favoriteItemCount > 0 && homeFocusArea != 1) {
                onSetHomeFocusArea(0)
            }
        }
    } else {
        homeTracker.wasActive = false
    }

    if (!drawerOpen && !isHomeScreenActive) {
        return false
    }

    val isCenterKey = isCenterActivateKey(nativeEvent.keyCode)

    if (isCenterKey) {
        when (nativeEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (nativeEvent.repeatCount == 0) {
                    onSetCenterKeyState(1)
                } else if (centerKeyState == 1) {
                    onSetCenterKeyState(2)
                    onOpenContextActionForCurrentFocus()
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                val wasShortPress = centerKeyState == 1
                onSetCenterKeyState(0)
                if (wasShortPress) {
                    if (drawerOpen) {
                        if (selectedDrawerIndex == -1) {
                            onSetDrawerOpen(false)
                        } else {
                            visibleApps.getOrNull(selectedDrawerIndex)?.let { app ->
                                context.packageManager.getLaunchIntentForPackage(
                                    app.packageName
                                )?.let(context::startActivity)
                            }
                        }
                    } else if (isHomeScreenActive) {
                        if (homeFocusArea == 0 && favoriteItemCount > 0) {
                            if (favoriteItems.isNotEmpty()) {
                                when (val item = favoriteItems.getOrNull(selectedFavoriteIndex)) {
                                    is FavoriteSlotItem.App -> {
                                        context.packageManager.getLaunchIntentForPackage(
                                            item.app.packageName
                                        )?.let(context::startActivity)
                                    }
                                    is FavoriteSlotItem.Folder -> {
                                        onOpenFolder(item.folder.id)
                                    }
                                    null -> {}
                                }
                            } else if (selectedFavoriteIndex < favoriteApps.size) {
                                favoriteApps.getOrNull(selectedFavoriteIndex)
                                    ?.let { app ->
                                        context.packageManager.getLaunchIntentForPackage(
                                            app.packageName
                                        )?.let(context::startActivity)
                                    }
                            } else {
                                favoriteFolders.getOrNull(
                                    selectedFavoriteIndex - favoriteApps.size
                                )?.let { folder ->
                                    onOpenFolder(folder.id)
                                }
                            }
                        } else if (homeFocusArea == 2) {
                            onSetSelectedDrawerIndex(0)
                            onSetDrawerOpen(true)
                        } else {
                            onOpenLauncherMenu()
                        }
                    }
                }
                return true
            }

            else -> return false
        }
    } else if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
        when (nativeEvent.keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_STAR -> {
                if (nativeEvent.repeatCount == 0) {
                    onOpenContextActionForCurrentFocus()
                }
                return true
            }

            KeyEvent.KEYCODE_POUND -> {
                if (nativeEvent.repeatCount == 0) {
                    onOpenLauncherMenu()
                }
                return true
            }

            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1,
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2,
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4,
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5,
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6,
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8,
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> {
                // Keitai Speed Dial: Long-press numeric key 2-9 on Home screen
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

                if (!drawerOpen && speedDialDigit != null && nativeEvent.repeatCount == 1) {
                    val assignedPkg = PreferencesManager.getSpeedDialPackage(context, speedDialDigit)
                    if (!assignedPkg.isNullOrBlank()) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(assignedPkg)
                        if (launchIntent != null) {
                            performBoundaryHaptic(context)
                            context.startActivity(launchIntent)
                            return true
                        }
                    }
                    performBoundaryHaptic(context)
                    onOpenSpeedDial(speedDialDigit)
                    return true
                }

                if (nativeEvent.repeatCount > 0) return true
                if (!drawerOpen) {
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                            onSetPendingTargetPage(0)
                            scope.launch { pagerState.animateScrollToPage(0) }
                            return true
                        }

                        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                            onSetPendingTargetPage(1)
                            scope.launch { pagerState.animateScrollToPage(1) }
                            return true
                        }

                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                            onSetPendingTargetPage(2)
                            scope.launch { pagerState.animateScrollToPage(2) }
                            return true
                        }

                        else -> return true
                    }
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
                    val target = letterStarts[letter]
                        ?: letterStarts.entries.firstOrNull { it.key > letter }?.value
                        ?: (visibleApps.size - 1)
                    if (target >= 0 && visibleApps.isNotEmpty()) {
                        onSetSelectedDrawerIndex(target)
                        scope.launch { listState.scrollToItem(target) }
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                val wrapAround = PreferencesManager.isDpadWrapAround(context)
                if (drawerOpen) {
                    if (selectedDrawerIndex <= 0) {
                        if (selectedDrawerIndex == 0) {
                            onSetSelectedDrawerIndex(-1) // Focus header
                        } else {
                            if (wrapAround && visibleApps.isNotEmpty()) {
                                val target = visibleApps.size - 1
                                onSetSelectedDrawerIndex(target)
                                scope.launch { listState.scrollToItem(target) }
                            } else {
                                performBoundaryHaptic(context)
                                onSetDrawerOpen(false)
                                onSetSelectedDrawerIndex(0)
                            }
                        }
                    } else {
                        onSetSelectedDrawerIndex(
                            (selectedDrawerIndex - 1).coerceAtLeast(0)
                        )
                    }
                } else if (isHomeScreenActive) {
                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                    if (isCooldown) {
                        return true
                    }
                    if (homeFocusArea == 2) {
                        onSetHomeFocusArea(if (favoriteItemCount > 0) 0 else 1)
                    } else if (homeFocusArea == 0) {
                        onSetHomeFocusArea(1)
                    } else if (nativeEvent.repeatCount == 0) {
                        onSetSelectedDrawerIndex(0)
                        onSetDrawerOpen(true)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val wrapAround = PreferencesManager.isDpadWrapAround(context)
                if (drawerOpen) {
                    if (selectedDrawerIndex < 0) {
                        onSetSelectedDrawerIndex(0)
                    } else if (selectedDrawerIndex >= visibleApps.size - 1) {
                        if (wrapAround && visibleApps.isNotEmpty()) {
                            onSetSelectedDrawerIndex(0)
                            scope.launch { listState.scrollToItem(0) }
                        } else {
                            performBoundaryHaptic(context)
                        }
                    } else {
                        onSetSelectedDrawerIndex(
                            (selectedDrawerIndex + 1)
                                .coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))
                        )
                    }
                } else if (isHomeScreenActive) {
                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                    if (isCooldown) {
                        return true
                    }
                    if (homeFocusArea == 1) {
                        onSetHomeFocusArea(if (favoriteItemCount > 0) 0 else 2)
                    } else if (homeFocusArea == 0) {
                        onSetHomeFocusArea(2)
                    } else if (homeFocusArea == 2 && nativeEvent.repeatCount == 0) {
                        onSetSelectedDrawerIndex(0)
                        onSetDrawerOpen(true)
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val wrapAround = PreferencesManager.isDpadWrapAround(context)
                if (drawerOpen) {
                    if (visibleApps.isNotEmpty()) {
                        if (selectedDrawerIndex <= 0) {
                            if (wrapAround) {
                                val target = visibleApps.size - 1
                                onSetSelectedDrawerIndex(target)
                                scope.launch { listState.scrollToItem(target) }
                            } else {
                                performBoundaryHaptic(context)
                            }
                        } else {
                            onSetSelectedDrawerIndex(selectedDrawerIndex - 1)
                        }
                    }
                } else if (isHomeScreenActive) {
                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                    if (isCooldown) {
                        return true
                    }
                    if (homeFocusArea == 0 && favoriteItemCount > 0 && selectedFavoriteIndex > 0) {
                        val newIdx = selectedFavoriteIndex - 1
                        onSetSelectedFavoriteIndex(newIdx)
                        onSetSavedFavoriteIndexOnHome(newIdx)
                    } else if (nativeEvent.repeatCount == 0) {
                        if (pagerState.currentPage > 0) {
                            onSetPendingTargetPage(0)
                            scope.launch { pagerState.animateScrollToPage(0) }
                        } else {
                            performBoundaryHaptic(context)
                        }
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val wrapAround = PreferencesManager.isDpadWrapAround(context)
                if (drawerOpen) {
                    if (visibleApps.isNotEmpty()) {
                        if (selectedDrawerIndex >= visibleApps.size - 1) {
                            if (wrapAround) {
                                onSetSelectedDrawerIndex(0)
                                scope.launch { listState.scrollToItem(0) }
                            } else {
                                performBoundaryHaptic(context)
                            }
                        } else {
                            onSetSelectedDrawerIndex(selectedDrawerIndex + 1)
                        }
                    }
                } else if (isHomeScreenActive) {
                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                    if (isCooldown) {
                        return true
                    }
                    if (homeFocusArea == 0 && favoriteItemCount > 0 && selectedFavoriteIndex < favoriteItemCount - 1) {
                        val newIdx = selectedFavoriteIndex + 1
                        onSetSelectedFavoriteIndex(newIdx)
                        onSetSavedFavoriteIndexOnHome(newIdx)
                    } else if (nativeEvent.repeatCount == 0) {
                        if (pagerState.currentPage < 2) {
                            onSetPendingTargetPage(2)
                            scope.launch { pagerState.animateScrollToPage(2) }
                        } else {
                            performBoundaryHaptic(context)
                        }
                    }
                }
                return true
            }

            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_F4 -> {
                if (drawerOpen) {
                    onSetDrawerOpen(false)
                    return true
                } else if (isHomeScreenActive && (homeFocusArea == 1 || homeFocusArea == 2) && favoriteItemCount > 0) {
                    onSetHomeFocusArea(0)
                    return true
                } else if (isHomeScreenActive) {
                    return true
                } else {
                    return false
                }
            }

            else -> return false
        }
    }
    return false
}
