package one.crinklecod.mochibun.core.util

import android.media.AudioManager
import android.view.KeyEvent

object MochibunSystemActions {
    const val ACTION_TOGGLE_CURSOR = "one.crinklecod.mochibun.ACTION_TOGGLE_CURSOR"
    const val ACTION_CURSOR_KEY = "one.crinklecod.mochibun.ACTION_CURSOR_KEY"
    const val ACTION_LSK_TRIGGER = "one.crinklecod.mochibun.ACTION_LSK_TRIGGER"
    const val ACTION_BACK_TRIGGER = "one.crinklecod.mochibun.ACTION_BACK_TRIGGER"
    const val ACTION_HOME_TRIGGER = "one.crinklecod.mochibun.ACTION_HOME_TRIGGER"
    const val ACTION_HARDWARE_KEY_TRIGGER = "one.crinklecod.mochibun.ACTION_HARDWARE_KEY_TRIGGER"
    const val ACTION_CURSOR_MODE_CHANGED = "one.crinklecod.mochibun.ACTION_CURSOR_MODE_CHANGED"
    const val ACTION_REQUEST_CURSOR_MODE = "one.crinklecod.mochibun.ACTION_REQUEST_CURSOR_MODE"
    const val ACTION_REQUEST_KEY_HOOK_STATUS =
        "one.crinklecod.mochibun.ACTION_REQUEST_KEY_HOOK_STATUS"
    const val ACTION_KEY_HOOK_STATUS = "one.crinklecod.mochibun.ACTION_KEY_HOOK_STATUS"

    // App -> system_server: current Media Volume Lock state (guarded by CURSOR_MODE_SYNC_PERMISSION)
    const val ACTION_MEDIA_VOLUME_LOCK_CHANGED =
        "one.crinklecod.mochibun.ACTION_MEDIA_VOLUME_LOCK_CHANGED"
    // system_server -> app: ask the app to re-send ACTION_MEDIA_VOLUME_LOCK_CHANGED
    const val ACTION_REQUEST_MEDIA_VOLUME_LOCK =
        "one.crinklecod.mochibun.ACTION_REQUEST_MEDIA_VOLUME_LOCK"

    // App -> system_server: Mochibun IME started / stopped typing in a text field (guarded by
    // CURSOR_MODE_SYNC_PERMISSION). Only sent while `*` is the cursor toggle key.
    const val ACTION_INPUT_ACTIVE_CHANGED = "one.crinklecod.mochibun.ACTION_INPUT_ACTIVE_CHANGED"

    const val SYSTEM_SERVER_BROADCAST_PERMISSION = "android.permission.INTERNAL_SYSTEM_WINDOW"
    const val CURSOR_MODE_SYNC_PERMISSION = "one.crinklecod.mochibun.permission.CURSOR_MODE_SYNC"

    const val EXTRA_KEY_EVENT = "key_event"
    const val EXTRA_KEY_CODE = "keyCode"
    const val EXTRA_ACTION = "action"
    const val EXTRA_REPEAT_COUNT = "repeatCount"
    const val EXTRA_CURSOR_ACTIVE = "isCursorActive"
    const val EXTRA_KEY_HOOK_ACTIVE = "isKeyHookActive"
    const val EXTRA_MEDIA_VOLUME_LOCK_ENABLED = "isMediaVolumeLockEnabled"
    const val EXTRA_INPUT_ACTIVE = "isInputActive"

    /** AT-M120 side key scan code (reported as KEYCODE_F4, or UNKNOWN on some keylayouts). */
    const val SIDE_KEY_SCAN_CODE = 62

    /**
     * Does this key toggle the virtual cursor? Shared by MochibunKeyHook (system_server) and the
     * non-root accessibility key filter so both agree.
     * - The F4 side key (scan 62) always toggles, so the AT-M120 keeps working whatever is chosen.
     * - [toggleKeyCode] (user's choice in Cursor & Hardware) toggles too, except `*` while
     *   [inputActive]: there it types into the focused text field instead.
     */
    fun isCursorToggleKey(keyCode: Int, scanCode: Int, toggleKeyCode: Int, inputActive: Boolean): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F4 || scanCode == SIDE_KEY_SCAN_CODE) return true
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN || keyCode != toggleKeyCode) return false
        return !(keyCode == KeyEvent.KEYCODE_STAR && inputActive)
    }

    /** Volume rocker key -> AudioManager adjust direction, or null for any other key. */
    fun mediaVolumeDirection(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
        else -> null
    }

    /**
     * Media Volume Lock only takes over the rocker in normal audio mode. During calls
     * (MODE_IN_CALL / MODE_IN_COMMUNICATION) and while ringing, stock handling stays in charge
     * so call volume and "press volume to silence the ringer" keep working.
     */
    fun shouldRouteVolumeToMedia(audioMode: Int): Boolean = when (audioMode) {
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_CALL_SCREENING -> false
        else -> true
    }

    fun cursorKeyCodeForEvent(keyCode: Int, scanCode: Int): Int? {
        // Linux keypad scan codes keep digit identity when a vendor keylayout maps 4/6 to D-pad.
        val keypadScrollKey = when (scanCode) {
            3 -> KeyEvent.KEYCODE_2
            5 -> KeyEvent.KEYCODE_4
            7 -> KeyEvent.KEYCODE_6
            9 -> KeyEvent.KEYCODE_8
            else -> null
        }
        if (keypadScrollKey != null) return keypadScrollKey

        return when (keyCode) {
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
            KeyEvent.KEYCODE_NUMPAD_8 -> keyCode
            else -> null
        }
    }
}
