package one.crinklecod.mochibun

import android.view.KeyEvent
import one.crinklecod.mochibun.ui.components.KeitaiCandidateState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeitaiCandidateSetCandidatesTest {

    @Test
    fun setCandidatesPopulatesStripAndResetsSelectionToZero() {
        val state = KeitaiCandidateState()
        state.setCandidates(arrayOf("hello", "hell", "he"))
        assertTrue(state.isActive)
        assertArrayEquals(arrayOf("hello", "hell", "he"), state.candidates)
        assertEquals(0, state.selectedIndex)

        state.navigateRight()
        assertEquals(1, state.selectedIndex)

        state.setCandidates(arrayOf("world", "word"))
        assertTrue(state.isActive)
        assertArrayEquals(arrayOf("world", "word"), state.candidates)
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun setCandidatesWithEmptyArrayResetsState() {
        val state = KeitaiCandidateState()
        state.setCandidates(arrayOf("hello", "hell"))
        state.navigateRight()
        assertTrue(state.isActive)

        state.setCandidates(emptyArray())
        assertFalse(state.isActive)
        assertEquals(0, state.candidates.size)
        assertEquals(0, state.selectedIndex)
    }

    @Test
    fun setCandidatesClearsPreviousMultiTapKeyHistory() {
        val committed = mutableListOf<String>()
        val state = KeitaiCandidateState(clock = { 1_000L })
        state.handleKey(KeyEvent.KEYCODE_2) { committed += it }
        state.setCandidates(arrayOf("hello", "good"))

        // Pressing key 2 after predictive candidates commits the active predictive word instead of cycling
        state.handleKey(KeyEvent.KEYCODE_2) { committed += it }
        assertEquals(listOf("hello"), committed)
        assertEquals("a", state.candidates[state.selectedIndex])
    }
}
