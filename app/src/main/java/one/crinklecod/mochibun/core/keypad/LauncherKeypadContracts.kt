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
    val isInputActive: Boolean = false,
    /** Mochibunny's Feed / Pet / Chat pill is open: D-pad and keypad drive the pill only. */
    val isPetPillOpen: Boolean = false,
    /** Selected pill option: [PET_PILL_FEED], [PET_PILL_PET] or [PET_PILL_CHAT]. */
    val petPillSelectedIndex: Int = PET_PILL_FEED
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
    fun openQuickMemo() {}
    fun cycleDrawerSortMode() {}
    fun recordAppLaunch(packageName: String) {}
    fun dismissModal() {}

    /** Opens Mochibunny's Feed / Pet / Chat pill (Center on the focused mascot). */
    fun openPetPill() {}
    fun setPetPillSelection(index: Int) {}
    /** Runs a pill option ([PET_PILL_FEED] / [PET_PILL_PET] / [PET_PILL_CHAT]); the pill stays open. */
    fun activatePetPillOption(index: Int) {}
    /** Closes the pill and puts Home focus back on Mochibunny. */
    fun closePetPill() {}
    /** Any key while the pill is open: restarts its auto-close timer. */
    fun onPetPillInteraction() {}
}

/** Mochibunny pet pill options, left to right (also keypad 1 / 2 / 3). */
const val PET_PILL_FEED = 0
const val PET_PILL_PET = 1
const val PET_PILL_CHAT = 2
const val PET_PILL_OPTION_COUNT = 3

/**
 * D-pad Left ([step] = -1) / Right (+1) in the pet pill: the next option, wrapping around the ends
 * when [wrapAround] is on, or null at an edge (the caller plays the boundary haptic).
 */
fun petPillTargetIndex(current: Int, step: Int, wrapAround: Boolean): Int? {
    val target = current.coerceIn(0, PET_PILL_OPTION_COUNT - 1) + step
    return when {
        target in 0 until PET_PILL_OPTION_COUNT -> target
        wrapAround -> Math.floorMod(target, PET_PILL_OPTION_COUNT)
        else -> null
    }
}

/** The pill closes itself after this long without a key press or tap. */
const val PET_PILL_AUTO_CLOSE_MS = 4_000L

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

/** What the `#`-hold radial wheel is showing. `*` or `0` flips between the two. */
enum class WheelMode {
    KAOMOJI,
    CLIPBOARD_SNIPPETS;

    fun toggled(): WheelMode = if (this == KAOMOJI) CLIPBOARD_SNIPPETS else KAOMOJI
}

/** `*` (keypad or numpad) toggles between Kaomoji and Clips & Snippets in every wheel stage. */
fun isWheelStarToggleKey(keyCode: Int): Boolean = when (keyCode) {
    android.view.KeyEvent.KEYCODE_STAR,
    android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> true
    else -> false
}

/** `*` / `0` (keypad or numpad) switch the wheel between Kaomoji and Clips & Snippets. */
fun isWheelModeToggleKey(keyCode: Int): Boolean = when (keyCode) {
    android.view.KeyEvent.KEYCODE_STAR,
    android.view.KeyEvent.KEYCODE_0,
    android.view.KeyEvent.KEYCODE_NUMPAD_0,
    android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> true
    else -> false
}

/** Keypad / numpad digit `1`–`8` as a 0-based petal index, else null. */
fun wheelPetalForDigit(keyCode: Int): Int? = when (keyCode) {
    in android.view.KeyEvent.KEYCODE_1..android.view.KeyEvent.KEYCODE_8 -> keyCode - android.view.KeyEvent.KEYCODE_1
    in android.view.KeyEvent.KEYCODE_NUMPAD_1..android.view.KeyEvent.KEYCODE_NUMPAD_8 -> keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_1
    else -> null
}

/**
 * Keypad / numpad digit `1`–`9` → indices `0..8` and `0` → index `9` for Stage 2 kaomoji quick-pick.
 */
fun wheelItemIndexForDigit(keyCode: Int): Int? = when (keyCode) {
    in android.view.KeyEvent.KEYCODE_1..android.view.KeyEvent.KEYCODE_9 -> keyCode - android.view.KeyEvent.KEYCODE_1
    in android.view.KeyEvent.KEYCODE_NUMPAD_1..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_1
    android.view.KeyEvent.KEYCODE_0, android.view.KeyEvent.KEYCODE_NUMPAD_0 -> 9
    else -> null
}
