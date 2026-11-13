package com.crinklecodone.mochibun.service

import android.view.KeyEvent

/** Who took a key press. The owner also receives that press's auto-repeats and its release. */
enum class InputLayer {
    NONE,
    LAUNCHER
}

/**
 * In-process key bookkeeping shared by [MochibunImeService] and MainActivity.
 *
 * Global hardware keys (F4 cursor toggle, cursor movement, Home/Back/Recents, function keys) are
 * routed earlier, inside system_server, by MochibunKeyHook. What is left here:
 *  - A per-keycode ownership table, so that when MainActivity claims a new press (ACTION_DOWN,
 *    repeatCount 0) its repeats and release are swallowed too, even if the launcher has since
 *    paused. They never leak to a window that didn't see the DOWN.
 *  - [isInputActive], so launcher shortcuts step aside for keypad typing while a text field has focus.
 *
 * All callers run on the main thread, so reads and writes never overlap. Lookups are array reads
 * only: no allocation on the key path.
 */
object InputArbiter {

    // Fixed size covers every Android keycode (288 on API 31) without asking the framework
    private const val KEYCODE_TABLE_SIZE = 512
    private val owners = arrayOfNulls<InputLayer>(KEYCODE_TABLE_SIZE)

    /**
     * True while an editable field has input focus (any app, including ours). Set by
     * MochibunImeService when it starts and finishes input on a text editor. Keypad digits, '*'
     * and '#' then belong to the field and its IME, not to the launcher.
     */
    @Volatile
    var isInputActive = false

    fun isNewPress(event: KeyEvent): Boolean =
        event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0

    fun ownerOf(keyCode: Int): InputLayer =
        if (keyCode in owners.indices) owners[keyCode] ?: InputLayer.NONE else InputLayer.NONE

    fun setOwner(keyCode: Int, layer: InputLayer) {
        if (keyCode in owners.indices) owners[keyCode] = layer
    }

    /** Keypad keys that type into a focused text field: digits, '*' and '#'. */
    fun isTypingKey(keyCode: Int): Boolean = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9,
        KeyEvent.KEYCODE_STAR,
        KeyEvent.KEYCODE_POUND -> true
        else -> false
    }
}
