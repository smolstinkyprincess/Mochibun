package one.crinklecod.mochibun

import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_CLOCK
import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_DRAWER_HANDLE
import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_FAVORITES
import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_HEADER
import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_MASCOT
import one.crinklecod.mochibun.core.keypad.LauncherKeypadState
import one.crinklecod.mochibun.core.keypad.PoundHoldAction
import one.crinklecod.mochibun.core.keypad.nextHomeFocusArea
import one.crinklecod.mochibun.core.keypad.poundHoldAction
import one.crinklecod.mochibun.data.model.ActionType
import one.crinklecod.mochibun.data.prefs.DailyStepState
import one.crinklecod.mochibun.data.prefs.LidOpenAction
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Quick Memo: `#` hold routing, persistence, key remap action, Home focus and steps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class QuickMemoTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        PreferencesManager.getPrefs(context).edit().clear().commit()
        PreferencesManager.setQuickMemoText(context, "")
    }

    private fun keypadState(
        isHomeScreenActive: Boolean = false,
        drawerOpen: Boolean = false,
        anyModalOpen: Boolean = false,
        isInputActive: Boolean = false
    ) = LauncherKeypadState(
        anyModalOpen = anyModalOpen,
        drawerOpen = drawerOpen,
        isHomeScreenActive = isHomeScreenActive,
        isPageTransitioning = false,
        pendingTargetPage = null,
        favoriteItemCount = 0,
        selectedFavoriteIndex = 0,
        savedFavoriteIndexOnHome = 0,
        homeFocusArea = 0,
        centerKeyState = 0,
        selectedDrawerIndex = 0,
        visibleApps = emptyList(),
        letterStarts = emptyMap(),
        isInputActive = isInputActive
    )

    // --- '#' hold routing ---

    @Test
    fun poundHoldOnHomeOpensQuickMemo() {
        assertEquals(PoundHoldAction.QUICK_MEMO, poundHoldAction(keypadState(isHomeScreenActive = true)))
    }

    @Test
    fun poundHoldWhileTypingIsLeftToImeKaomojiWheel() {
        assertEquals(
            PoundHoldAction.LEAVE_TO_IME,
            poundHoldAction(keypadState(isHomeScreenActive = true, isInputActive = true))
        )
        assertEquals(
            PoundHoldAction.LEAVE_TO_IME,
            poundHoldAction(keypadState(anyModalOpen = true, isInputActive = true))
        )
    }

    @Test
    fun poundHoldInDrawerOrModalDoesNothing() {
        assertEquals(PoundHoldAction.NONE, poundHoldAction(keypadState(drawerOpen = true)))
        assertEquals(PoundHoldAction.NONE, poundHoldAction(keypadState(isHomeScreenActive = true, anyModalOpen = true)))
    }

    // --- Persistence ---

    @Test
    fun memoTextPersistsAcrossReload() {
        PreferencesManager.setQuickMemoText(context, "buy strawberry milk\n")

        val prefs = PreferencesManager.getPrefs(context)
        assertEquals("buy strawberry milk", prefs.getString(PreferencesManager.KEY_QUICK_MEMO_TEXT, null))
        assertEquals("buy strawberry milk", PreferencesManager.quickMemoText.value)

        // A fresh reload (e.g. process restart) reads the same values back
        PreferencesManager.setQuickMemoText(context, "x")
        prefs.edit().putString(PreferencesManager.KEY_QUICK_MEMO_TEXT, "from disk").commit()
        PreferencesManager.reload(context)
        assertEquals("from disk", PreferencesManager.quickMemoText.value)
    }

    @Test
    fun memoIsCappedAndClearEmptiesIt() {
        PreferencesManager.setQuickMemoText(context, "a".repeat(200))
        assertEquals(PreferencesManager.QUICK_MEMO_MAX_CHARS, PreferencesManager.quickMemoText.value.length)

        PreferencesManager.clearQuickMemo(context)
        assertEquals("", PreferencesManager.quickMemoText.value)
    }

    @Test
    fun lidOpenActionQuickMemoRoundTrips() {
        assertEquals(LidOpenAction.QUICK_MEMO, LidOpenAction.fromId("quick_memo"))
        PreferencesManager.setLidOpenAction(context, LidOpenAction.QUICK_MEMO)
        assertEquals(LidOpenAction.QUICK_MEMO, PreferencesManager.getLidOpenAction(context))
        // Stepper wraps: Quick Memo is last, Home is first
        assertEquals(LidOpenAction.HOME, LidOpenAction.QUICK_MEMO.next())
        assertEquals(LidOpenAction.QUICK_MEMO, LidOpenAction.HOME.previous())
        // Unknown / missing still defaults to Home
        assertEquals(LidOpenAction.HOME, LidOpenAction.fromId(null))
    }

    // --- Key remap action ---

    @Test
    fun quickMemoRemapActionSerializes() {
        val action = ActionType.OpenQuickMemo
        assertEquals("QUICK_MEMO", action.serialize())
        assertEquals(ActionType.OpenQuickMemo, ActionType.deserialize("QUICK_MEMO"))
        assertEquals("Open Quick Memo", action.displayName())
    }

    // --- Home D-pad chain ---

    @Test
    fun homeChainTopToBottom() {
        fun down(from: Int) = nextHomeFocusArea(from, 1, hasFavorites = true)
        fun up(from: Int) = nextHomeFocusArea(from, -1, hasFavorites = true)

        assertEquals(HOME_FOCUS_CLOCK, down(HOME_FOCUS_HEADER)) // banner ↔ clock, no ticker stop
        assertEquals(HOME_FOCUS_MASCOT, down(HOME_FOCUS_CLOCK))
        assertEquals(HOME_FOCUS_FAVORITES, down(HOME_FOCUS_MASCOT))
        assertEquals(HOME_FOCUS_DRAWER_HANDLE, down(HOME_FOCUS_FAVORITES))
        assertNull(down(HOME_FOCUS_DRAWER_HANDLE)) // past the handle: the drawer opens

        assertEquals(HOME_FOCUS_FAVORITES, up(HOME_FOCUS_DRAWER_HANDLE))
        assertNull(up(HOME_FOCUS_HEADER)) // top edge
    }

    @Test
    fun homeChainSkipsHiddenAreasLikeBefore() {
        // No favorites
        fun down(from: Int) = nextHomeFocusArea(from, 1, hasFavorites = false)
        fun up(from: Int) = nextHomeFocusArea(from, -1, hasFavorites = false)
        assertEquals(HOME_FOCUS_CLOCK, down(HOME_FOCUS_HEADER))
        assertEquals(HOME_FOCUS_DRAWER_HANDLE, down(HOME_FOCUS_MASCOT))
        assertEquals(HOME_FOCUS_MASCOT, up(HOME_FOCUS_DRAWER_HANDLE))
        assertEquals(HOME_FOCUS_HEADER, up(HOME_FOCUS_CLOCK))
        // A hidden area that still holds focus moves to its visible neighbour
        assertEquals(HOME_FOCUS_MASCOT, up(HOME_FOCUS_FAVORITES))
        assertEquals(HOME_FOCUS_DRAWER_HANDLE, down(HOME_FOCUS_FAVORITES))
    }

    // --- Daily step counter ---

    @Test
    fun dailyStepsTrackDeltaSinceMidnightAndSurviveReboots() {
        val day1 = DailyStepState.dayStamp(2026, 318)
        val day2 = DailyStepState.dayStamp(2026, 319)

        var s = DailyStepState.update(null, day1, 1_000L)
        assertEquals(0L, s.stepsToday) // first reading ever is today's zero
        s = DailyStepState.update(s, day1, 1_250L)
        assertEquals(250L, s.stepsToday)

        // Reboot resets the sensor: earlier steps are carried
        s = DailyStepState.update(s, day1, 40L)
        assertEquals(290L, s.stepsToday)

        // Midnight: today starts from the last reading seen yesterday
        s = DailyStepState.update(s, day2, 100L)
        assertEquals(60L, s.stepsToday)
    }
}
