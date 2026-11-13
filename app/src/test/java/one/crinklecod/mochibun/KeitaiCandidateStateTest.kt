package one.crinklecod.mochibun

import android.view.KeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import one.crinklecod.mochibun.ui.components.KeitaiCandidatePools
import one.crinklecod.mochibun.ui.components.KeitaiCandidateState
import one.crinklecod.mochibun.ui.components.insertAtCursor
import one.crinklecod.mochibun.ui.components.limitTextLength
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeitaiCandidateStateTest {

    private var now = 1_000L
    private val committed = mutableListOf<String>()
    private val state = KeitaiCandidateState(clock = { now })
    private val onCommit: (String) -> Unit = { committed += it }

    private fun press(keyCode: Int, advanceMs: Long = 100L) {
        now += advanceMs
        assertTrue(state.handleKey(keyCode, onCommit))
    }

    private val current get() = state.candidates[state.selectedIndex]

    @Test
    fun sameDigitWithinTimeoutCyclesThroughPool() {
        press(KeyEvent.KEYCODE_2)
        assertTrue(state.isActive)
        assertEquals("a", current)
        press(KeyEvent.KEYCODE_2)
        assertEquals("b", current)
        press(KeyEvent.KEYCODE_2)
        assertEquals("c", current)
        assertTrue(committed.isEmpty())
    }

    @Test
    fun cyclingWrapsAroundPool() {
        repeat(KeitaiCandidatePools.POOL_2.size) { press(KeyEvent.KEYCODE_2) }
        assertEquals("2", current)
        press(KeyEvent.KEYCODE_2)
        assertEquals("a", current)
    }

    @Test
    fun differentDigitCommitsPendingCandidate() {
        press(KeyEvent.KEYCODE_2)
        press(KeyEvent.KEYCODE_2)
        press(KeyEvent.KEYCODE_3)
        assertEquals(listOf("b"), committed)
        assertEquals("d", current)
        assertArrayEquals(KeitaiCandidatePools.POOL_3, state.candidates)
    }

    @Test
    fun sameDigitAfterTimeoutCommitsAndStartsFresh() {
        press(KeyEvent.KEYCODE_2)
        press(KeyEvent.KEYCODE_2)
        press(KeyEvent.KEYCODE_2, advanceMs = KeitaiCandidateState.DEFAULT_TIMEOUT_MS + 1)
        assertEquals(listOf("b"), committed)
        assertEquals("a", current)
    }

    @Test
    fun starAndOneMapToSymbolPool() {
        press(KeyEvent.KEYCODE_STAR)
        assertArrayEquals(KeitaiCandidatePools.POOL_SYMBOLS, state.candidates)
        assertEquals(".", current)
        // 1 shares the pool but is a different key, so it commits and restarts
        press(KeyEvent.KEYCODE_1)
        assertEquals(listOf("."), committed)
        assertArrayEquals(KeitaiCandidatePools.POOL_SYMBOLS, state.candidates)
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun nonPoolKeyIsNotConsumed() {
        assertFalse(state.handleKey(KeyEvent.KEYCODE_POUND, onCommit))
        assertFalse(state.isActive)
    }

    @Test
    fun horizontalNavigationWrapsAtBoundaries() {
        press(KeyEvent.KEYCODE_0)
        state.navigateLeft()
        assertEquals(KeitaiCandidatePools.POOL_0.last(), current)
        state.navigateRight()
        assertEquals(" ", current)
        state.navigateRight()
        assertEquals("0", current)
    }

    @Test
    fun navigationWhileInactiveIsNoOp() {
        state.navigateLeft()
        state.navigateRight()
        assertFalse(state.isActive)
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun commitActiveCommitsAndResets() {
        press(KeyEvent.KEYCODE_7)
        state.navigateRight()
        state.commitActive(onCommit)
        assertEquals(listOf("q"), committed)
        assertFalse(state.isActive)
        // Next press of the same key starts fresh instead of cycling
        press(KeyEvent.KEYCODE_7)
        assertEquals("p", current)
    }

    @Test
    fun backspaceCancelsPendingCandidateWithoutCommitting() {
        press(KeyEvent.KEYCODE_9)
        press(KeyEvent.KEYCODE_9)
        assertTrue(state.handleBackspace())
        assertFalse(state.isActive)
        assertTrue(committed.isEmpty())
        // With nothing pending, backspace falls through to the text field
        assertFalse(state.handleBackspace())
    }

    @Test
    fun backCancelsCandidateAndSwallowsItsKeyUp() {
        press(KeyEvent.KEYCODE_4)
        assertTrue(state.handleFieldKeyEvent(KeyEvent.KEYCODE_BACK, isDown = true, repeatCount = 0, onCommit))
        assertTrue(state.handleFieldKeyEvent(KeyEvent.KEYCODE_BACK, isDown = false, repeatCount = 0, onCommit))
        // A later BACK with nothing pending is left to the dialog
        assertFalse(state.handleFieldKeyEvent(KeyEvent.KEYCODE_BACK, isDown = true, repeatCount = 0, onCommit))
        assertFalse(state.handleFieldKeyEvent(KeyEvent.KEYCODE_BACK, isDown = false, repeatCount = 0, onCommit))
        assertTrue(committed.isEmpty())
    }

    @Test
    fun verticalNavigationCommitsButIsNotConsumed() {
        press(KeyEvent.KEYCODE_5)
        assertFalse(state.handleFieldKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, isDown = true, repeatCount = 0, onCommit))
        assertEquals(listOf("j"), committed)
        assertFalse(state.isActive)
    }

    @Test
    fun poundKeyCommitsActiveCandidateButIsNotConsumed() {
        press(KeyEvent.KEYCODE_5)
        assertFalse(state.handleFieldKeyEvent(KeyEvent.KEYCODE_POUND, isDown = true, repeatCount = 0, onCommit))
        assertEquals(listOf("j"), committed)
        assertFalse(state.isActive)
    }

    @Test
    fun insertAtCursorReplacesSelectionAndRespectsMaxLength() {
        val value = TextFieldValue("Mochi", TextRange(2))
        assertEquals(TextFieldValue("Moxchi", TextRange(3)), value.insertAtCursor("x"))
        assertEquals(TextFieldValue("Mii", TextRange(2)), TextFieldValue("Mochi", TextRange(1, 4)).insertAtCursor("i"))
        assertEquals(value, value.insertAtCursor("x", maxLength = 5))
    }

    @Test
    fun textLengthLimitPreservesValidImeComposition() {
        val composing = TextFieldValue("Mochi", TextRange(5), TextRange(3, 5))
        assertEquals(composing, composing.limitTextLength(32))
        assertEquals(
            TextFieldValue("Moc", TextRange(3)),
            composing.limitTextLength(3)
        )
    }
}
