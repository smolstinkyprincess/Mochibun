package one.crinklecod.mochibun.core.keypad

import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.model.FavoriteSlotItem
import one.crinklecod.mochibun.data.model.LauncherApp

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
    val currentPage: Int = 1,
    val isInputActive: Boolean = false
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
    fun openQuickMemo() {}
    fun cycleDrawerSortMode() {}
    fun recordAppLaunch(packageName: String) {}
    fun boopMascot() {}
    fun dismissModal() {}
}

/** Home focus areas (see HomeScreen's `homeFocusArea`). */
const val HOME_FOCUS_FAVORITES = 0
const val HOME_FOCUS_HEADER = 1
const val HOME_FOCUS_DRAWER_HANDLE = 2
const val HOME_FOCUS_CLOCK = 3
const val HOME_FOCUS_MASCOT = 4

/** Every Home focus area in on-screen order, top to bottom. */
private val HOME_FOCUS_FULL_ORDER = intArrayOf(
    HOME_FOCUS_HEADER,
    HOME_FOCUS_CLOCK,
    HOME_FOCUS_MASCOT,
    HOME_FOCUS_FAVORITES,
    HOME_FOCUS_DRAWER_HANDLE
)

/** Whether a Home focus area is currently on screen. */
fun isHomeFocusAreaShown(area: Int, hasFavorites: Boolean): Boolean =
    when (area) {
        HOME_FOCUS_FAVORITES -> hasFavorites
        else -> true
    }

/**
 * D-pad Up ([direction] = -1) / Down (+1) on Home: the next visible area in on-screen order,
 * or null at the top / bottom edge. An area that is hidden (e.g. an empty Favorites card)
 * still moves to its nearest visible neighbour.
 */
fun nextHomeFocusArea(
    current: Int,
    direction: Int,
    hasFavorites: Boolean
): Int? {
    var i = HOME_FOCUS_FULL_ORDER.indexOf(current)
    if (i < 0) return null
    while (true) {
        i += direction
        if (i !in HOME_FOCUS_FULL_ORDER.indices) return null
        val area = HOME_FOCUS_FULL_ORDER[i]
        if (isHomeFocusAreaShown(area, hasFavorites)) return area
    }
}

/** What holding `#` (~400 ms) does in the launcher. */
enum class PoundHoldAction {
    /** Not ours: the Mochibun IME owns `#` while a text field is focused (Kaomoji Wheel). */
    LEAVE_TO_IME,
    /** Home screen: open Quick Memo. */
    QUICK_MEMO,
    /** App drawer or anywhere else: the hold is swallowed. */
    NONE
}

fun poundHoldAction(state: LauncherKeypadState): PoundHoldAction = when {
    state.isInputActive -> PoundHoldAction.LEAVE_TO_IME
    state.isHomeScreenActive && !state.anyModalOpen -> PoundHoldAction.QUICK_MEMO
    else -> PoundHoldAction.NONE
}
