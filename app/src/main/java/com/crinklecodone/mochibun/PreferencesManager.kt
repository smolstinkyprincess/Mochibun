package com.crinklecodone.mochibun

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized preferences manager for Mochibun launcher settings.
 * Exposes reactive StateFlows for Compose consumption without requiring launcher restarts.
 */
object PreferencesManager {
    const val PREFS_NAME = "launcher_preferences"
    const val KEY_PIXELSPRAY_WALLPAPER_ENABLED = "pref_pixelspray_wallpaper_enabled"
    const val DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED = true

    private val _isPixelSprayEnabled = MutableStateFlow(DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED)
    val isPixelSprayEnabled: StateFlow<Boolean> = _isPixelSprayEnabled.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val prefs = getPrefs(context)
            _isPixelSprayEnabled.value = prefs.getBoolean(
                KEY_PIXELSPRAY_WALLPAPER_ENABLED,
                DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED
            )
            initialized = true
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPixelSprayWallpaperEnabled(context: Context): Boolean {
        init(context)
        return _isPixelSprayEnabled.value
    }

    fun setPixelSprayWallpaperEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PIXELSPRAY_WALLPAPER_ENABLED, enabled).apply()
        _isPixelSprayEnabled.value = enabled
    }
}
