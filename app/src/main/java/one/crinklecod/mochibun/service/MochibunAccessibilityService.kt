package one.crinklecod.mochibun.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import one.crinklecod.mochibun.core.system.KeyHookStatus
import one.crinklecod.mochibun.core.system.RootTouchInjector
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.prefs.CursorPreferences

/**
 * Optional helper for phones without root (enabled by the user in Android Settings >
 * Accessibility). Rooted / LSPosed setups don't need it and behave exactly as before.
 *
 * When enabled it provides:
 *  - Virtual cursor taps and scrolls via [dispatchGesture] (the fallback used by
 *    [RootTouchInjector] when root is unavailable).
 *  - Global actions: Recents, notification shade, Quick Settings, Back.
 *  - Key filtering, only while the LSPosed key hook is NOT active: the cursor toggle key flips
 *    the cursor, and D-pad / Center / 2 / 4 / 6 / 8 drive it while it is visible.
 *
 * No accessibility event types are requested and window content is never read.
 */
class MochibunAccessibilityService : AccessibilityService(), RootTouchInjector.GestureFallback {

    companion object {
        private const val TAG = "MochibunA11y"

        private const val NOT_TAKEN: Byte = 0
        private const val TAKEN_TOGGLE: Byte = 1
        private const val TAKEN_CURSOR: Byte = 2

        /** Finger-on-glass time of a gesture tap (a 1 ms stroke is a plain down + up). */
        const val TAP_DURATION_MS = 1L

        /** Live connected instance, or null when the service is disabled. */
        @Volatile
        var instance: MochibunAccessibilityService? = null
            private set

        /** True when Android lists this service as enabled (it may still be connecting). */
        fun isEnabled(context: Context): Boolean {
            val enabled = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (_: Exception) {
                null
            }
            val component = ComponentName(context, MochibunAccessibilityService::class.java)
            return isComponentListed(enabled, component.flattenToString(), component.flattenToShortString())
        }

        /** Pure parser for the colon-separated ENABLED_ACCESSIBILITY_SERVICES value. */
        internal fun isComponentListed(enabledSetting: String?, vararg names: String): Boolean {
            if (enabledSetting.isNullOrBlank()) return false
            return enabledSetting.split(':').any { entry ->
                names.any { it.equals(entry.trim(), ignoreCase = true) }
            }
        }

        fun openRecents(): Boolean = performGlobal(GLOBAL_ACTION_RECENTS)
        fun openNotifications(): Boolean = performGlobal(GLOBAL_ACTION_NOTIFICATIONS)
        fun openQuickSettings(): Boolean = performGlobal(GLOBAL_ACTION_QUICK_SETTINGS)
        fun goBack(): Boolean = performGlobal(GLOBAL_ACTION_BACK)

        private fun performGlobal(action: Int): Boolean =
            try {
                instance?.performGlobalAction(action) == true
            } catch (e: Exception) {
                Log.w(TAG, "performGlobalAction($action) failed", e)
                false
            }
    }

    /** Per key code: who took its DOWN ([NOT_TAKEN], [TAKEN_TOGGLE], [TAKEN_CURSOR]). */
    private val taken = ByteArray(KeyRouting.KEYCODE_TABLE_SIZE)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Non-root cursor & key service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ------------------------------------------------------------------ gestures

    override fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        return dispatchStroke(path, TAP_DURATION_MS)
    }

    override fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.coerceAtLeast(0f), y1.coerceAtLeast(0f))
            lineTo(x2.coerceAtLeast(0f), y2.coerceAtLeast(0f))
        }
        return dispatchStroke(path, durationMs.coerceAtLeast(1L))
    }

    /** One-step vertical scroll from ([x], [y]): the finger drags by [dy] pixels. */
    fun dispatchScroll(x: Float, y: Float, dy: Float, durationMs: Long = 200L): Boolean =
        dispatchSwipe(x, y, x, y + dy, durationMs)

    private fun dispatchStroke(path: Path, durationMs: Long): Boolean =
        try {
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        } catch (e: Exception) {
            Log.w(TAG, "dispatchGesture failed", e)
            false
        }

    // ------------------------------------------------------------------ key filtering

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode !in taken.indices) return false
        val cursorKey = KeyRouting.cursorKeyFor(keyCode, event.scanCode)

        // Repeats and the release belong to whoever took the DOWN, even if cursor state changed
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
            val owner = taken[keyCode]
            if (owner == NOT_TAKEN) return false
            if (event.action == KeyEvent.ACTION_UP) taken[keyCode] = NOT_TAKEN
            if (owner == TAKEN_CURSOR) {
                VirtualCursorService.dispatchCursorKey(cursorKey, event.action, event.repeatCount)
            }
            return true
        }

        // LSPosed hook present: it already routes these keys inside system_server
        if (KeyHookStatus.isActive(this)) return false

        val route = KeyRouting.route(
            keyCode = keyCode,
            scanCode = event.scanCode,
            toggleKeyCode = CursorPreferences.getToggleKeyCode(this),
            cursorVisible = VirtualCursorService.isCursorVisible,
            inputActive = InputArbiter.isInputActive
        )
        return when (route) {
            KeyRouting.Route.PASS -> false
            KeyRouting.Route.TOGGLE_CURSOR -> {
                taken[keyCode] = TAKEN_TOGGLE
                toggleCursor()
                true
            }
            KeyRouting.Route.CURSOR_KEY -> {
                taken[keyCode] = TAKEN_CURSOR
                VirtualCursorService.dispatchCursorKey(cursorKey, event.action, event.repeatCount)
                true
            }
        }
    }

    private fun toggleCursor() {
        if (VirtualCursorService.isServiceRunning) {
            VirtualCursorService.toggleCursor()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cursor toggle ignored: Display Over Other Apps is not granted")
            return
        }
        try {
            VirtualCursorService.start(this, showCursor = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start VirtualCursorService", e)
        }
    }

    /** Pure key routing for the non-root key filter (unit-tested). */
    object KeyRouting {
        const val KEYCODE_TABLE_SIZE = 512

        enum class Route { PASS, TOGGLE_CURSOR, CURSOR_KEY }

        private const val SIDE_KEY_SCAN_CODE = 62

        fun isToggleKey(keyCode: Int, scanCode: Int, toggleKeyCode: Int): Boolean =
            keyCode == toggleKeyCode || keyCode == KeyEvent.KEYCODE_F4 || scanCode == SIDE_KEY_SCAN_CODE

        /** Cursor key code for an event, keeping digit identity for remapped keypad scan codes. */
        fun cursorKeyFor(keyCode: Int, scanCode: Int): Int =
            MochibunSystemActions.cursorKeyCodeForEvent(keyCode, scanCode) ?: keyCode

        fun route(
            keyCode: Int,
            scanCode: Int,
            toggleKeyCode: Int,
            cursorVisible: Boolean,
            inputActive: Boolean
        ): Route {
            if (isToggleKey(keyCode, scanCode, toggleKeyCode)) return Route.TOGGLE_CURSOR
            if (!cursorVisible) return Route.PASS
            val cursorKey = MochibunSystemActions.cursorKeyCodeForEvent(keyCode, scanCode)
                ?: return Route.PASS
            // Digits type into a focused text field; never steal them from the IME
            if (inputActive && InputArbiter.isTypingKey(cursorKey)) return Route.PASS
            return Route.CURSOR_KEY
        }
    }
}
