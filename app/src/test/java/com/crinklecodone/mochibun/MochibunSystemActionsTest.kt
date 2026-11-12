package com.crinklecodone.mochibun

import android.view.KeyEvent
import com.crinklecodone.mochibun.core.util.MochibunSystemActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MochibunSystemActionsTest {

    @Test
    fun cursorKeyCodeForEventPreservesDpadAndActivationKeys() {
        assertEquals(
            KeyEvent.KEYCODE_DPAD_UP,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_UP, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_DPAD_DOWN,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_DOWN, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_DPAD_LEFT,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_LEFT, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_RIGHT, 0)
        )
        assertEquals(
            KeyEvent.KEYCODE_ENTER,
            MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_ENTER, 0)
        )
    }

    @Test
    fun cursorKeyCodeForEventMapsNumericKeypadScrollScanCodes() {
        assertEquals(KeyEvent.KEYCODE_2, MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_UP, 3))
        assertEquals(KeyEvent.KEYCODE_4, MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_LEFT, 5))
        assertEquals(KeyEvent.KEYCODE_6, MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_RIGHT, 7))
        assertEquals(KeyEvent.KEYCODE_8, MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_DPAD_DOWN, 9))
    }

    @Test
    fun cursorKeyCodeForEventIgnoresUnrelatedKeys() {
        assertNull(MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_A, 0))
        assertNull(MochibunSystemActions.cursorKeyCodeForEvent(KeyEvent.KEYCODE_1, 0))
    }
}
