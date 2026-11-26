package one.crinklecod.mochibun

import android.view.KeyEvent
import one.crinklecod.mochibun.core.keypad.WheelMode
import one.crinklecod.mochibun.core.keypad.wheelItemIndexForDigit
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.ClipRingEntry
import one.crinklecod.mochibun.ui.dialogs.KaomojiWheelState
import one.crinklecod.mochibun.ui.dialogs.WheelStage
import one.crinklecod.mochibun.ui.dialogs.handleKaomojiWheelNavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class KaomojiWheelTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private var nowMs = 1_000L

    @Before
    fun setUp() {
        PreferencesManager.init(context)
        nowMs = 1_000L
    }

    private fun newState(clips: List<ClipRingEntry> = emptyList()): KaomojiWheelState =
        KaomojiWheelState(
            clipEntriesProvider = { clips },
            clockMs = { nowMs }
        )

    @Test
    fun stage1CardinalPointingAndSecondTapLock() {
        val state = newState()
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertEquals(0, state.categoryIndex)

        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_RIGHT, context))
        assertEquals(2, state.categoryIndex)
        assertEquals(WheelStage.CATEGORY, state.stage)

        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_DOWN, context))
        // Within 900 ms, RIGHT -> DOWN tilts onto Cute (3)
        assertEquals(3, state.categoryIndex)
        assertEquals(WheelStage.CATEGORY, state.stage)

        // Jump to West (6: Shock), then press West again to lock into Stage 2
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_LEFT, context))
        assertEquals(6, state.categoryIndex)
        assertEquals(WheelStage.CATEGORY, state.stage)

        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_LEFT, context))
        assertEquals(6, state.categoryIndex)
        assertEquals(WheelStage.ITEM, state.stage)
    }

    @Test
    fun stage1TwoTapDiagonalTiltsAllDirectionsWithinWindow() {
        val pairs = listOf(
            Triple(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 1),
            Triple(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, 1),
            Triple(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, 3),
            Triple(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 3),
            Triple(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 5),
            Triple(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, 5),
            Triple(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, 7),
            Triple(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, 7)
        )

        for ((firstKey, secondKey, expectedIndex) in pairs) {
            nowMs += 2_000L
            val state = newState()
            assertTrue(handleKaomojiWheelNavKey(state, firstKey, context))
            nowMs += 300L
            assertTrue(handleKaomojiWheelNavKey(state, secondKey, context))
            assertEquals("Tilt $firstKey -> $secondKey", expectedIndex, state.categoryIndex)
            assertEquals(WheelStage.CATEGORY, state.stage)
        }
    }

    @Test
    fun stage1ExpiredWindowJumpsToSecondCardinalInsteadOfTilting() {
        val state = newState()
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_UP, context))
        assertEquals(0, state.categoryIndex)

        nowMs += 1_000L // > 900 ms tilt window
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_RIGHT, context))
        assertEquals(2, state.categoryIndex)
        assertEquals(WheelStage.CATEGORY, state.stage)
    }

    @Test
    fun stage1Digits1To8LockDirectlyIntoStage2AndBackOrPoundDismisses() {
        val state = newState()
        var confirmed = 0
        var dismissed = 0

        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_6,
                context,
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ }
            )
        )
        assertEquals(5, state.categoryIndex) // Shy
        assertEquals(WheelStage.ITEM, state.stage)
        assertEquals(0, confirmed)
        assertEquals(0, dismissed)

        // Back in Stage 2 unlocks back to Stage 1 without dismissing
        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_BACK,
                context,
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ }
            )
        )
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertEquals(0, dismissed)

        // Back in Stage 1 dismisses
        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_BACK,
                context,
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ }
            )
        )
        assertEquals(1, dismissed)

        // '#' in Stage 1 also dismisses
        val state2 = newState()
        var dismissed2 = 0
        assertTrue(handleKaomojiWheelNavKey(state2, KeyEvent.KEYCODE_POUND, context, onDismiss = { dismissed2++ }))
        assertEquals(1, dismissed2)
    }

    @Test
    fun stage2QuickPickDigitsBrowseAndUpReturn() {
        val state = newState()
        var confirmedText: String? = null
        var dismissed = 0

        // Center in Stage 1 locks Happy (0) into Stage 2
        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_DPAD_CENTER,
                context,
                onConfirm = { confirmedText = state.currentText },
                onDismiss = { dismissed++ }
            )
        )
        assertEquals(WheelStage.ITEM, state.stage)
        assertEquals(null, confirmedText)

        // Left wraps to last item (index 9), Right steps back to index 0, Right steps to 1
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_LEFT, context))
        assertEquals(9, state.itemIndex)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_RIGHT, context))
        assertEquals(0, state.itemIndex)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_RIGHT, context))
        assertEquals(1, state.itemIndex)

        // DPAD_UP in Stage 2 unlocks back to Stage 1
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_UP, context, onDismiss = { dismissed++ }))
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertEquals(0, dismissed)

        // Lock Cute (digit 4 -> index 3) and instant quick-pick 0 (#10 -> index 9)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_4, context))
        assertEquals(3, state.categoryIndex)
        assertEquals(WheelStage.ITEM, state.stage)

        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_0,
                context,
                onConfirm = { confirmedText = state.currentText }
            )
        )
        assertEquals(9, state.itemIndex)
        assertEquals(state.categories[3].items[9], confirmedText)
        assertEquals(WheelMode.KAOMOJI, state.mode)

        // Check digit helper mapping 1-9 -> 0..8 and 0 -> 9
        assertEquals(0, wheelItemIndexForDigit(KeyEvent.KEYCODE_1))
        assertEquals(8, wheelItemIndexForDigit(KeyEvent.KEYCODE_9))
        assertEquals(9, wheelItemIndexForDigit(KeyEvent.KEYCODE_0))
        assertEquals(9, wheelItemIndexForDigit(KeyEvent.KEYCODE_NUMPAD_0))
    }

    @Test
    fun starToggleWorksInBothStages() {
        val clips = listOf(ClipRingEntry("hello", pinned = true))
        val state = newState(clips)

        // Toggle in Stage 1
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_STAR, context))
        assertEquals(WheelMode.CLIPBOARD_SNIPPETS, state.mode)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_STAR, context))
        assertEquals(WheelMode.KAOMOJI, state.mode)
        assertEquals(WheelStage.CATEGORY, state.stage)

        // Enter Stage 2, then toggle with '*'
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_CENTER, context))
        assertEquals(WheelStage.ITEM, state.stage)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_STAR, context))
        assertEquals(WheelMode.CLIPBOARD_SNIPPETS, state.mode)
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertFalse(state.currentText.isBlank())

        // Lock Clips Stage 2, then toggle back to Kaomoji with '*'
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_CENTER, context))
        assertEquals(WheelStage.ITEM, state.stage)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_STAR, context))
        assertEquals(WheelMode.KAOMOJI, state.mode)
        assertEquals(WheelStage.CATEGORY, state.stage)
    }

    @Test
    fun clipsModeUsesSameTwoStageCompassNavigationAndQuickPick() {
        val clips = (1..8).map { ClipRingEntry("clip $it", pinned = it <= 2) }
        val state = newState(clips)
        var confirmedText: String? = null
        var dismissed = 0

        // Switch to Clips & Snippets via '*'
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_STAR, context))
        assertEquals(WheelMode.CLIPBOARD_SNIPPETS, state.mode)
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertEquals(0, state.clipWedgeIndex)

        // Stage 1: cardinal point + 900 ms diagonal tilt to wedge 3 (Cute/SE)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_RIGHT, context))
        assertEquals(2, state.clipWedgeIndex)
        assertEquals(WheelStage.CATEGORY, state.stage)

        nowMs += 300L
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_DOWN, context))
        assertEquals(3, state.clipWedgeIndex)
        assertEquals(WheelStage.CATEGORY, state.stage)

        // Second tap on WEST (wedge 6) locks into Stage 2
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_LEFT, context))
        assertEquals(6, state.clipWedgeIndex)
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_LEFT, context))
        assertEquals(WheelStage.ITEM, state.stage)
        assertEquals("clip 7", state.currentText)

        // Stage 2: Left/Right browse clips
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_RIGHT, context))
        assertEquals(7, state.clipIndex)
        assertEquals("clip 8", state.currentText)
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_DPAD_RIGHT, context))
        assertEquals(0, state.clipIndex)
        assertEquals("clip 1", state.currentText)

        // Stage 2: DPAD_UP / BACK unlocks to Stage 1 without dismissing
        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_BACK,
                context,
                onDismiss = { dismissed++ }
            )
        )
        assertEquals(WheelStage.CATEGORY, state.stage)
        assertEquals(0, dismissed)

        // Stage 1: Digit 1-8 locks that slot into Stage 2 without confirming
        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_5,
                context,
                onConfirm = { confirmedText = state.currentText }
            )
        )
        assertEquals(WheelStage.ITEM, state.stage)
        assertEquals(4, state.clipIndex)
        assertEquals(null, confirmedText)

        // Stage 2: Digit 2 quick-picks clip #2 and confirms immediately
        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_2,
                context,
                onConfirm = { confirmedText = state.currentText }
            )
        )
        assertEquals(1, state.clipIndex)
        assertEquals("clip 2", confirmedText)
    }

    @Test
    fun clipsModeStage1RejectsEmptyDigitSlotAndStage2ConfirmsWithCenter() {
        val clips = listOf(ClipRingEntry("first", pinned = true), ClipRingEntry("second", pinned = false))
        val state = newState(clips)
        var confirmedText: String? = null

        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_STAR, context))
        assertEquals(WheelMode.CLIPBOARD_SNIPPETS, state.mode)

        // Digit 6 corresponds to an empty slot -> stays in Stage 1
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_6, context))
        assertEquals(WheelStage.CATEGORY, state.stage)

        // Digit 2 locks slot 2 into Stage 2, then Center confirms
        assertTrue(handleKaomojiWheelNavKey(state, KeyEvent.KEYCODE_2, context))
        assertEquals(WheelStage.ITEM, state.stage)
        assertEquals("second", state.currentText)

        assertTrue(
            handleKaomojiWheelNavKey(
                state,
                KeyEvent.KEYCODE_DPAD_CENTER,
                context,
                onConfirm = { confirmedText = state.currentText }
            )
        )
        assertEquals("second", confirmedText)
    }
}
