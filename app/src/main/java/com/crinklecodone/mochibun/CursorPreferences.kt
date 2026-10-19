package com.crinklecodone.mochibun

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

    val DEFAULT_TOGGLE_KEYCODE = KeyEvent.KEYCODE_F4 // Chinoe AT-M120 physical side key
    const val DEFAULT_MOVE_SPEED = 10f // Base pixels per frame
    const val DEFAULT_ACCELERATION = 1.08f // Multiplier while holding D-pad
    const val DEFAULT_CURSOR_SIZE_DP = 32
    const val DEFAULT_GIF_PLAYBACK_SPEED = 0.5f // Calm, gentle low playback speed (~170-200ms frame delay)

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
        return try {
            ClickMethod.valueOf(name ?: ClickMethod.ACCESSIBILITY_GESTURE.name)
        } catch (_: Exception) {
            ClickMethod.ACCESSIBILITY_GESTURE
        }
    }

    fun setClickMethod(context: Context, method: ClickMethod) {
        getPrefs(context).edit().putString(KEY_CLICK_METHOD, method.name).apply()
    }

    fun getHotspotAlignment(context: Context): HotspotAlignment {
        val name = getPrefs(context).getString(KEY_HOTSPOT_ALIGNMENT, HotspotAlignment.TOP_LEFT.name)
        return try {
            HotspotAlignment.valueOf(name ?: HotspotAlignment.TOP_LEFT.name)
        } catch (_: Exception) {
            HotspotAlignment.TOP_LEFT
        }
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
}
