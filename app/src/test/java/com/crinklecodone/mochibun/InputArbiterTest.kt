package com.crinklecodone.mochibun

import android.view.KeyEvent
import com.crinklecodone.mochibun.service.InputArbiter
import com.crinklecodone.mochibun.service.InputLayer
import com.crinklecodone.mochibun.service.KaomojiOverlayController
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
        InputArbiter.isKaomojiOverlayShowing = false
        InputArbiter.cursorToggleKeyCode = KeyEvent.KEYCODE_UNKNOWN
        for (code in 0 until 512) InputArbiter.setOwner(code, InputLayer.NONE)
    }

    @Test
    fun `owner is remembered until the next press overwrites it`() {
        InputArbiter.setOwner(KeyEvent.KEYCODE_DPAD_CENTER, InputLayer.KAOMOJI_OVERLAY)
        assertEquals(InputLayer.KAOMOJI_OVERLAY, InputArbiter.ownerOf(KeyEvent.KEYCODE_DPAD_CENTER))
        // Other keys are untouched
        assertEquals(InputLayer.NONE, InputArbiter.ownerOf(KeyEvent.KEYCODE_DPAD_LEFT))

        InputArbiter.setOwner(KeyEvent.KEYCODE_DPAD_CENTER, InputLayer.LAUNCHER)
        assertEquals(InputLayer.LAUNCHER, InputArbiter.ownerOf(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `out of range keycodes are ignored safely`() {
        InputArbiter.setOwner(-1, InputLayer.LAUNCHER)
        InputArbiter.setOwner(10_000, InputLayer.LAUNCHER)
        assertEquals(InputLayer.NONE, InputArbiter.ownerOf(-1))
        assertEquals(InputLayer.NONE, InputArbiter.ownerOf(10_000))
    }

    @Test
    fun `cursor takes nothing while its service is not connected`() {
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_F4, 62, cursorVisible = false))
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_DPAD_UP, 0, cursorVisible = true))
    }

    @Test
    fun `toggle key always goes to the cursor, including raw side key scan code`() {
        InputArbiter.cursorToggleKeyCode = KeyEvent.KEYCODE_F4
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_F4, 0, cursorVisible = false))
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_UNKNOWN, 62, cursorVisible = false))
    }

    @Test
    fun `visible cursor takes dpad, center and scroll keys only`() {
        InputArbiter.cursorToggleKeyCode = KeyEvent.KEYCODE_F4
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_DPAD_LEFT, 0, cursorVisible = true))
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_DPAD_CENTER, 0, cursorVisible = true))
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_2, 0, cursorVisible = true))
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_8, 0, cursorVisible = true))
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_4, 0, cursorVisible = true))
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_6, 0, cursorVisible = true))
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_BACK, 0, cursorVisible = true))
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_5, 0, cursorVisible = true))
        // Hidden cursor leaves navigation alone
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_DPAD_LEFT, 0, cursorVisible = false))
    }

    @Test
    fun `kaomoji wheel outranks the cursor for navigation keys`() {
        InputArbiter.cursorToggleKeyCode = KeyEvent.KEYCODE_F4
        InputArbiter.isKaomojiOverlayShowing = true
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_DPAD_UP, 0, cursorVisible = true))
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_DPAD_CENTER, 0, cursorVisible = true))
        assertFalse(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_2, 0, cursorVisible = true))
    }

    @Test
    fun `F4 goes to the cursor even while the kaomoji wheel is open`() {
        InputArbiter.cursorToggleKeyCode = KeyEvent.KEYCODE_F4
        InputArbiter.isKaomojiOverlayShowing = true
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_F4, 0, cursorVisible = false))
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_F4, 62, cursorVisible = true))
        // Raw side key with no keycode mapping
        assertTrue(InputArbiter.cursorTakesPress(KeyEvent.KEYCODE_UNKNOWN, 62, cursorVisible = false))
    }

    @Test
    fun `F4 does not dismiss the kaomoji overlay`() {
        assertFalse(KaomojiOverlayController.isDismissKey(KeyEvent.KEYCODE_F4))
    }

    @Test
    fun `back and pound dismiss the kaomoji overlay`() {
        assertTrue(KaomojiOverlayController.isDismissKey(KeyEvent.KEYCODE_BACK))
        assertTrue(KaomojiOverlayController.isDismissKey(KeyEvent.KEYCODE_POUND))
        // Center confirms (pastes) rather than plain-dismissing
        assertFalse(KaomojiOverlayController.isDismissKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
