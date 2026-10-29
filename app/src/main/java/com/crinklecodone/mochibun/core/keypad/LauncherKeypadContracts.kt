package com.crinklecodone.mochibun.core.keypad

import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.FavoriteSlotItem
import com.crinklecodone.mochibun.data.model.LauncherApp

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
 * State snapshot required for physical keypad navigation.
 */
data class LauncherKeypadState(
    val anyModalOpen: Boolean,
    val drawerOpen: Boolean,
    val isHomeScreenActive: Boolean,
    val isPageTransitioning: Boolean,
    val pendingTargetPage: Int?,
    val favoriteItemCount: Int,
    val selectedFavoriteIndex: Int,
    val savedFavoriteIndexOnHome: Int,
    val homeFocusArea: Int,
    val centerKeyState: Int,
    val selectedDrawerIndex: Int,
    val visibleApps: List<LauncherApp>,
    val favoriteApps: List<LauncherApp> = emptyList(),
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
    val favoriteItems: List<FavoriteSlotItem> = emptyList(),
    val letterStarts: Map<Char, Int>,
    val poundKeyState: Int = 0,
    val isDrawerRailFocused: Boolean = false,
    val drawerRailHoveredLetter: Char? = null,
    val currentPage: Int = 1
) {
    val isSidePageActive: Boolean
        get() = (currentPage == 0 || currentPage == 2) && !drawerOpen && !anyModalOpen

    val isDrawerActive: Boolean
        get() = drawerOpen && !anyModalOpen

    val isModalActive: Boolean
        get() = anyModalOpen
}

/**
 * Callbacks invoked during physical keypad navigation.
 */
interface LauncherKeypadActions {
    fun setDrawerOpen(open: Boolean)
    fun setSelectedDrawerIndex(index: Int)
    fun setHomeFocusArea(area: Int)
    fun setSelectedFavoriteIndex(index: Int)
    fun setSavedFavoriteIndexOnHome(index: Int)
    fun setPendingTargetPage(page: Int?)
    fun setCenterKeyState(state: Int)
    fun setPoundKeyState(state: Int) {}
    fun setDrawerRailFocused(focused: Boolean) {}
    fun setDrawerRailHoveredLetter(letter: Char?) {}
    fun openContextActionForCurrentFocus()
    fun openFolder(folderId: String)
    fun openLauncherMenu()
    fun openSpeedDial(digit: Int)
    fun openKaomojiWheel() {}
    fun cycleDrawerSortMode() {}
    fun recordAppLaunch(packageName: String) {}
}
