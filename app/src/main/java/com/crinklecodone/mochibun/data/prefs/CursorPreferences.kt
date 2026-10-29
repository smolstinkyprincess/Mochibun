package com.crinklecodone.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.view.KeyEvent

enum class ClickMethod(val label: String, val description: String) {
    ACCESSIBILITY_GESTURE("Accessibility Gesture", "Native Android dispatchGesture (no root needed)"),
    ROOT_INPUT_TAP("Root Shell", "Direct su -c input tap (fallback for protected surfaces)")
}

enum class HotspotAlignment(val label: String, val description: String) {
    TOP_LEFT("Top-Left Tip", "Pointer clicks at the top-left tip of the cursor (ideal for arrows)"),
    CENTER("Center", "Pointer clicks at the center of the cursor (ideal for crosshairs/paws)")
}

object CursorPreferences {
    const val PREFS_NAME = "virtual_cursor_preferences"

    private const val KEY_TOGGLE_KEYCODE = "cursor_toggle_keycode"
    private const val KEY_MOVE_SPEED = "cursor_move_speed"
    private const val KEY_ACCELERATION = "cursor_acceleration"
    private const val KEY_CLICK_METHOD = "cursor_click_method"
    private const val KEY_HOTSPOT_ALIGNMENT = "cursor_hotspot_alignment"
    private const val KEY_CUSTOM_URI = "cursor_custom_uri"
    private const val KEY_CURSOR_SIZE_DP = "cursor_size_dp"
    private const val KEY_GIF_PLAYBACK_SPEED = "cursor_gif_playback_speed"
    private const val KEY_SCROLL_STEP_PX = "cursor_scroll_step_px"
    private const val KEY_TAP_STEP_PX = "cursor_tap_step_px"
    private const val KEY_MAX_SPEED = "cursor_max_speed"
    private const val KEY_ACCEL_RAMP_MS = "cursor_accel_ramp_ms"

    val DEFAULT_TOGGLE_KEYCODE = KeyEvent.KEYCODE_F4 // Chinoe AT-M120 physical side key
    // Legacy physics values. The cursor no longer uses them, but backups and .mochibun bundles
    // still carry them so older app versions can read files made by this one.
    const val DEFAULT_MOVE_SPEED = 10f
    const val DEFAULT_ACCELERATION = 1.08f
    const val DEFAULT_CURSOR_SIZE_DP = 32
    const val DEFAULT_GIF_PLAYBACK_SPEED = 0.5f // Calm, gentle low playback speed (~170-200ms frame delay)
    const val DEFAULT_SCROLL_STEP_PX = 200 // Default vertical swipe distance for keys 2 & 8
    const val MIN_SCROLL_STEP_PX = 100
    const val MAX_SCROLL_STEP_PX = 500
    const val SCROLL_STEP_INCREMENT_PX = 50

    // Kinetic cursor physics (see CursorPhysics)
    const val DEFAULT_TAP_STEP_PX = 3 // Micro-step per tap; hold starts at this many px per frame
    const val MIN_TAP_STEP_PX = 1
    const val MAX_TAP_STEP_PX = 5
    const val DEFAULT_MAX_SPEED = 650 // Terminal velocity in px/s
    const val MIN_MAX_SPEED = 300
    const val MAX_MAX_SPEED = 1000
    const val MAX_SPEED_INCREMENT = 50
    const val DEFAULT_ACCEL_RAMP_MS = 750 // Time to reach max speed while holding
    const val MIN_ACCEL_RAMP_MS = 250
    const val MAX_ACCEL_RAMP_MS = 1500
    const val ACCEL_RAMP_INCREMENT_MS = 250

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getToggleKeyCode(context: Context): Int {
        return getPrefs(context).getInt(KEY_TOGGLE_KEYCODE, DEFAULT_TOGGLE_KEYCODE)
    }

    fun setToggleKeyCode(context: Context, keyCode: Int) {
        getPrefs(context).edit().putInt(KEY_TOGGLE_KEYCODE, keyCode).apply()
    }

    fun getMoveSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_MOVE_SPEED, DEFAULT_MOVE_SPEED)
    }

    fun setMoveSpeed(context: Context, speed: Float) {
        getPrefs(context).edit().putFloat(KEY_MOVE_SPEED, speed).apply()
    }

    fun getAcceleration(context: Context): Float {
        return getPrefs(context).getFloat(KEY_ACCELERATION, DEFAULT_ACCELERATION)
    }

    fun setAcceleration(context: Context, acceleration: Float) {
        getPrefs(context).edit().putFloat(KEY_ACCELERATION, acceleration).apply()
    }

    fun getClickMethod(context: Context): ClickMethod {
        val name = getPrefs(context).getString(KEY_CLICK_METHOD, ClickMethod.ACCESSIBILITY_GESTURE.name)
        return ClickMethod.entries.firstOrNull { it.name == name } ?: ClickMethod.ACCESSIBILITY_GESTURE
    }

    fun setClickMethod(context: Context, method: ClickMethod) {
        getPrefs(context).edit().putString(KEY_CLICK_METHOD, method.name).apply()
    }

    fun getHotspotAlignment(context: Context): HotspotAlignment {
        val name = getPrefs(context).getString(KEY_HOTSPOT_ALIGNMENT, HotspotAlignment.TOP_LEFT.name)
        return HotspotAlignment.entries.firstOrNull { it.name == name } ?: HotspotAlignment.TOP_LEFT
    }

    fun setHotspotAlignment(context: Context, alignment: HotspotAlignment) {
        getPrefs(context).edit().putString(KEY_HOTSPOT_ALIGNMENT, alignment.name).apply()
    }

    fun getCustomCursorUri(context: Context): Uri? {
        val uriStr = getPrefs(context).getString(KEY_CUSTOM_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (_: Exception) {
            null
        }
    }

    fun setCustomCursorUri(context: Context, uri: Uri?) {
        val editor = getPrefs(context).edit()
        if (uri == null) {
            editor.remove(KEY_CUSTOM_URI)
        } else {
            editor.putString(KEY_CUSTOM_URI, uri.toString())
        }
        editor.apply()
    }

    fun getCursorSizeDp(context: Context): Int {
        return getPrefs(context).getInt(KEY_CURSOR_SIZE_DP, DEFAULT_CURSOR_SIZE_DP)
    }

    fun setCursorSizeDp(context: Context, sizeDp: Int) {
        getPrefs(context).edit().putInt(KEY_CURSOR_SIZE_DP, sizeDp).apply()
    }

    fun getGifPlaybackSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_GIF_PLAYBACK_SPEED, DEFAULT_GIF_PLAYBACK_SPEED)
    }

    fun setGifPlaybackSpeed(context: Context, speed: Float) {
        getPrefs(context).edit().putFloat(KEY_GIF_PLAYBACK_SPEED, speed).apply()
    }

    fun getScrollStepPx(context: Context): Int {
        return getPrefs(context)
            .getInt(KEY_SCROLL_STEP_PX, DEFAULT_SCROLL_STEP_PX)
            .coerceIn(MIN_SCROLL_STEP_PX, MAX_SCROLL_STEP_PX)
    }

    fun setScrollStepPx(context: Context, stepPx: Int) {
        getPrefs(context)
            .edit()
            .putInt(KEY_SCROLL_STEP_PX, stepPx.coerceIn(MIN_SCROLL_STEP_PX, MAX_SCROLL_STEP_PX))
            .apply()
    }

    fun getTapStepPx(context: Context): Int {
        return getPrefs(context)
            .getInt(KEY_TAP_STEP_PX, DEFAULT_TAP_STEP_PX)
            .coerceIn(MIN_TAP_STEP_PX, MAX_TAP_STEP_PX)
    }

    fun setTapStepPx(context: Context, stepPx: Int) {
        getPrefs(context)
            .edit()
            .putInt(KEY_TAP_STEP_PX, stepPx.coerceIn(MIN_TAP_STEP_PX, MAX_TAP_STEP_PX))
            .apply()
    }

    fun getMaxSpeed(context: Context): Int {
        return getPrefs(context)
            .getInt(KEY_MAX_SPEED, DEFAULT_MAX_SPEED)
            .coerceIn(MIN_MAX_SPEED, MAX_MAX_SPEED)
    }

    fun setMaxSpeed(context: Context, pxPerSec: Int) {
        getPrefs(context)
            .edit()
            .putInt(KEY_MAX_SPEED, pxPerSec.coerceIn(MIN_MAX_SPEED, MAX_MAX_SPEED))
            .apply()
    }

    fun getAccelRampMs(context: Context): Int {
        return getPrefs(context)
            .getInt(KEY_ACCEL_RAMP_MS, DEFAULT_ACCEL_RAMP_MS)
            .coerceIn(MIN_ACCEL_RAMP_MS, MAX_ACCEL_RAMP_MS)
    }

    fun setAccelRampMs(context: Context, rampMs: Int) {
        getPrefs(context)
            .edit()
            .putInt(KEY_ACCEL_RAMP_MS, rampMs.coerceIn(MIN_ACCEL_RAMP_MS, MAX_ACCEL_RAMP_MS))
            .apply()
    }
}
