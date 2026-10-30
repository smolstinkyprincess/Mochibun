package com.crinklecodone.mochibun.service

import android.view.KeyEvent

/** Who took a key press. The owner also receives that press's auto-repeats and its release. */
enum class InputLayer {
    NONE,
    KEY_CAPTURE,
    KAOMOJI_OVERLAY,
    CURSOR_OVERLAY,
    LAUNCHER,
    SERVICE_REMAP
}

/**
 * Shared key-ownership table for the two accessibility key filters (VirtualCursorService and
 * MochibunAccessibilityService) and MainActivity.
 *
 * Android hands every key event to BOTH accessibility services, in no guaranteed order, before the
 * focused window sees it. So the layers must agree on who owns a press without talking to each
 * other:
 *  - On a new press (ACTION_DOWN, repeatCount 0) every layer evaluates the same [cursorTakesPress]
 *    rule, so exactly one of them claims it.
 *  - The claim is stored per keycode until the next press of that key. Repeats and the release are
 *    routed to the owner, even if the owner has since closed (Kaomoji Wheel dismissed, cursor hidden,
 *    launcher paused). Those events are swallowed instead of reaching a window that never saw the
 *    DOWN.
 *
 * All callers run on the main thread (accessibility callbacks and Activity key dispatch both do),
 * so reads and writes never overlap. Lookups are array reads only: no allocation on the key path.
 */
object InputArbiter {

    // Fixed size covers every Android keycode (288 on API 31) without asking the framework
    private const val KEYCODE_TABLE_SIZE = 512
    private val owners = arrayOfNulls<InputLayer>(KEYCODE_TABLE_SIZE)

    /** Set by KaomojiOverlayController while the global wheel is attached. */
    @Volatile
    var isKaomojiOverlayShowing = false

    /** Cursor toggle key while VirtualCursorService is connected; KEYCODE_UNKNOWN otherwise. */
    @Volatile
    var cursorToggleKeyCode = KeyEvent.KEYCODE_UNKNOWN

    fun isNewPress(event: KeyEvent): Boolean =
        event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0

    fun ownerOf(keyCode: Int): InputLayer =
        if (keyCode in owners.indices) owners[keyCode] ?: InputLayer.NONE else InputLayer.NONE

    fun setOwner(keyCode: Int, layer: InputLayer) {
        if (keyCode in owners.indices) owners[keyCode] = layer
    }

    /**
     * True when the virtual cursor takes this new press. Both services call this, so neither has to
     * know whether the other already handled the event. The toggle key always belongs to the
     * cursor; otherwise the Kaomoji Wheel outranks it.
     */
    fun cursorTakesPress(event: KeyEvent): Boolean =
        cursorTakesPress(event.keyCode, event.scanCode, VirtualCursorService.isCursorVisible)

    internal fun cursorTakesPress(keyCode: Int, scanCode: Int, cursorVisible: Boolean): Boolean {
        val toggle = cursorToggleKeyCode
        if (toggle == KeyEvent.KEYCODE_UNKNOWN) return false
        if (keyCode == toggle || (scanCode == 62 && toggle == KeyEvent.KEYCODE_F4)) return true
        return !isKaomojiOverlayShowing && cursorVisible && isCursorKey(keyCode)
    }

    /** Keys the visible cursor drives: D-pad movement, center tap, 2 / 8 and 4 / 6 scrolling. */
    private fun isCursorKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_2,
        KeyEvent.KEYCODE_NUMPAD_2,
        KeyEvent.KEYCODE_4,
        KeyEvent.KEYCODE_NUMPAD_4,
        KeyEvent.KEYCODE_6,
        KeyEvent.KEYCODE_NUMPAD_6,
        KeyEvent.KEYCODE_8,
        KeyEvent.KEYCODE_NUMPAD_8 -> true
        else -> false
    }
}
