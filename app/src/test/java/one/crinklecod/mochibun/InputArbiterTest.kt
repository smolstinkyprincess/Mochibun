package one.crinklecod.mochibun

import android.view.KeyEvent
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.service.InputArbiter
import one.crinklecod.mochibun.service.InputLayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InputArbiterTest {

    @Before
    fun setUp() {
        reset()
    }

    @After
    fun tearDown() {
        reset()
    }

    private fun reset() {
        InputArbiter.isInputActive = false
        for (code in 0 until 512) InputArbiter.setOwner(code, InputLayer.NONE)
    }

    @Test
    fun `owner is remembered until the next press overwrites it`() {
        InputArbiter.setOwner(KeyEvent.KEYCODE_DPAD_CENTER, InputLayer.LAUNCHER)
        assertEquals(InputLayer.LAUNCHER, InputArbiter.ownerOf(KeyEvent.KEYCODE_DPAD_CENTER))
        // Other keys are untouched
        assertEquals(InputLayer.NONE, InputArbiter.ownerOf(KeyEvent.KEYCODE_DPAD_LEFT))

        InputArbiter.setOwner(KeyEvent.KEYCODE_DPAD_CENTER, InputLayer.NONE)
        assertEquals(InputLayer.NONE, InputArbiter.ownerOf(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `out of range keycodes are ignored safely`() {
        InputArbiter.setOwner(-1, InputLayer.LAUNCHER)
        InputArbiter.setOwner(10_000, InputLayer.LAUNCHER)
        assertEquals(InputLayer.NONE, InputArbiter.ownerOf(-1))
        assertEquals(InputLayer.NONE, InputArbiter.ownerOf(10_000))
    }

    @Test
    fun `system hook forwards all four keypad scroll directions`() {
        assertEquals(
            KeyEvent.KEYCODE_2,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_2, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_2,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_UP, 3)
        )
        assertEquals(
            KeyEvent.KEYCODE_4,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_4, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_6,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_6, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_8,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_8, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_8,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_DOWN, 9)
        )
        assertEquals(
            KeyEvent.KEYCODE_4,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_LEFT, 5)
        )
        assertEquals(
            KeyEvent.KEYCODE_6,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_RIGHT, 7)
        )
        assertEquals(
            KeyEvent.KEYCODE_NUMPAD_4,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_NUMPAD_4, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_NUMPAD_6,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_NUMPAD_6, 0)
        )
        assertEquals(null, MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_5, 0))
    }

    @Test
    fun `typing keys are digits, star and pound only`() {
        assertTrue(InputArbiter.isTypingKey(KeyEvent.KEYCODE_0))
        assertTrue(InputArbiter.isTypingKey(KeyEvent.KEYCODE_9))
        assertTrue(InputArbiter.isTypingKey(KeyEvent.KEYCODE_STAR))
        assertTrue(InputArbiter.isTypingKey(KeyEvent.KEYCODE_POUND))
        assertFalse(InputArbiter.isTypingKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(InputArbiter.isTypingKey(KeyEvent.KEYCODE_BACK))
    }
}
