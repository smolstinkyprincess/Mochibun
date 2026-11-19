package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import one.crinklecod.mochibun.data.model.AppDrawerSortMode
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_APP_DRAWER_SORT_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_CUSTOM_USER_NAME
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_FOLDER_GRID_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_HOME_MASCOT_SIZE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_PHOTO_FRAME_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_PHOTO_FRAME_LABEL
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_PHOTO_FRAME_STYLE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_SUB_DISPLAY_TICKER_DARK
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_SUB_DISPLAY_TICKER_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_SYSTEM_MONITOR_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_SYSTEM_MONITOR_INTERVAL
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_THEME_BORDER_STYLE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_THEME_FONT_FAMILY
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_THEME_PRESET
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_WALLPAPER_OVERLAY_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_APP_DRAWER_SORT_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_CUSTOM_USER_NAME
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_FOLDER_GRID_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_HOME_MASCOT_SIZE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_PHOTO_FRAME_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_PHOTO_FRAME_LABEL
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_PHOTO_FRAME_STYLE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_PHOTO_FRAME_URI
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_SUB_DISPLAY_TICKER_DARK
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_SUB_DISPLAY_TICKER_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_SYSTEM_MONITOR_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_SYSTEM_MONITOR_INTERVAL
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_THEME_BORDER_STYLE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_THEME_FONT_FAMILY
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_THEME_PRESET
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_WALLPAPER_OVERLAY_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_WALLPAPER_URI
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import one.crinklecod.mochibun.data.prefs.PreferencesManager.initialized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Theme preset / font / card border, wallpaper & overlay, Home greeting name and mascot size,
 * photo frame, System Monitor & folder layout, drawer sort, and sub-display ticker.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object AppearancePrefs {
    private val _isWallpaperOverlayEnabled = MutableStateFlow(DEFAULT_WALLPAPER_OVERLAY_ENABLED)
    val isWallpaperOverlayEnabled: StateFlow<Boolean> = _isWallpaperOverlayEnabled.asStateFlow()

    private val _isSystemMonitorEnabled = MutableStateFlow(DEFAULT_SYSTEM_MONITOR_ENABLED)
    val isSystemMonitorEnabled: StateFlow<Boolean> = _isSystemMonitorEnabled.asStateFlow()

    private val _isFolderGridMode = MutableStateFlow(DEFAULT_FOLDER_GRID_MODE)
    val isFolderGridMode: StateFlow<Boolean> = _isFolderGridMode.asStateFlow()

    private val _appDrawerSortMode = MutableStateFlow(AppDrawerSortMode.ALPHABETICAL)
    val appDrawerSortMode: StateFlow<AppDrawerSortMode> = _appDrawerSortMode.asStateFlow()

    private val _systemMonitorInterval = MutableStateFlow(DEFAULT_SYSTEM_MONITOR_INTERVAL)
    val systemMonitorInterval: StateFlow<Int> = _systemMonitorInterval.asStateFlow()

    private val _customUserName = MutableStateFlow(DEFAULT_CUSTOM_USER_NAME)
    val customUserName: StateFlow<String> = _customUserName.asStateFlow()

    private val _themePreset = MutableStateFlow(DEFAULT_THEME_PRESET)
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    private val _themeFontFamily = MutableStateFlow(DEFAULT_THEME_FONT_FAMILY)
    val themeFontFamily: StateFlow<String> = _themeFontFamily.asStateFlow()

    private val _themeBorderStyle = MutableStateFlow(DEFAULT_THEME_BORDER_STYLE)
    val themeBorderStyle: StateFlow<String> = _themeBorderStyle.asStateFlow()

    private val _photoFrameUri = MutableStateFlow<String?>(null)
    val photoFrameUri: StateFlow<String?> = _photoFrameUri.asStateFlow()

    private val _photoFrameStyle = MutableStateFlow(DEFAULT_PHOTO_FRAME_STYLE)
    val photoFrameStyle: StateFlow<String> = _photoFrameStyle.asStateFlow()

    private val _photoFrameLabel = MutableStateFlow(DEFAULT_PHOTO_FRAME_LABEL)
    val photoFrameLabel: StateFlow<String> = _photoFrameLabel.asStateFlow()

    private val _isPhotoFrameEnabled = MutableStateFlow(DEFAULT_PHOTO_FRAME_ENABLED)
    val isPhotoFrameEnabled: StateFlow<Boolean> = _isPhotoFrameEnabled.asStateFlow()

    private val _isSubDisplayTickerEnabled = MutableStateFlow(DEFAULT_SUB_DISPLAY_TICKER_ENABLED)
    /** Retro keitai "outer sub-display" status marquee on Home. */
    val isSubDisplayTickerEnabled: StateFlow<Boolean> = _isSubDisplayTickerEnabled.asStateFlow()

    private val _isSubDisplayTickerDark = MutableStateFlow(DEFAULT_SUB_DISPLAY_TICKER_DARK)
    /** true = dark sub-OLED look, false = pastel theme-matched look. */
    val isSubDisplayTickerDark: StateFlow<Boolean> = _isSubDisplayTickerDark.asStateFlow()

    private val _homeMascotSize = MutableStateFlow(HomeMascotSize.SMALL)
    /** Preset size of the mascot sitting on the Home favorites card. */
    val homeMascotSize: StateFlow<HomeMascotSize> = _homeMascotSize.asStateFlow()

    fun reload(prefs: SharedPreferences) {
        _isWallpaperOverlayEnabled.value = prefs.getBoolean(
            KEY_WALLPAPER_OVERLAY_ENABLED,
            DEFAULT_WALLPAPER_OVERLAY_ENABLED
        )
        _isSystemMonitorEnabled.value = prefs.getBoolean(
            KEY_SYSTEM_MONITOR_ENABLED,
            DEFAULT_SYSTEM_MONITOR_ENABLED
        )
        _isFolderGridMode.value = prefs.getBoolean(
            KEY_FOLDER_GRID_MODE,
            DEFAULT_FOLDER_GRID_MODE
        )
        _systemMonitorInterval.value = prefs.getInt(
            KEY_SYSTEM_MONITOR_INTERVAL,
            DEFAULT_SYSTEM_MONITOR_INTERVAL
        )
        _customUserName.value = prefs.getString(
            KEY_CUSTOM_USER_NAME,
            DEFAULT_CUSTOM_USER_NAME
        ) ?: DEFAULT_CUSTOM_USER_NAME
        _themePreset.value = prefs.getString(
            KEY_THEME_PRESET,
            DEFAULT_THEME_PRESET
        ) ?: DEFAULT_THEME_PRESET
        val sortStr = prefs.getString(KEY_APP_DRAWER_SORT_MODE, DEFAULT_APP_DRAWER_SORT_MODE) ?: DEFAULT_APP_DRAWER_SORT_MODE
        _appDrawerSortMode.value = AppDrawerSortMode.entries.firstOrNull { it.name == sortStr }
            ?: AppDrawerSortMode.ALPHABETICAL
        _themeFontFamily.value = prefs.getString(
            KEY_THEME_FONT_FAMILY,
            DEFAULT_THEME_FONT_FAMILY
        ) ?: DEFAULT_THEME_FONT_FAMILY
        _themeBorderStyle.value = CardBorderStyle.normalizeId(
            prefs.getString(KEY_THEME_BORDER_STYLE, DEFAULT_THEME_BORDER_STYLE)
        )
        _photoFrameUri.value = prefs.getString(KEY_PHOTO_FRAME_URI, null)
        _photoFrameStyle.value = prefs.getString(
            KEY_PHOTO_FRAME_STYLE,
            DEFAULT_PHOTO_FRAME_STYLE
        ) ?: DEFAULT_PHOTO_FRAME_STYLE
        _photoFrameLabel.value = prefs.getString(
            KEY_PHOTO_FRAME_LABEL,
            DEFAULT_PHOTO_FRAME_LABEL
        ) ?: DEFAULT_PHOTO_FRAME_LABEL
        _isPhotoFrameEnabled.value = prefs.getBoolean(
            KEY_PHOTO_FRAME_ENABLED,
            DEFAULT_PHOTO_FRAME_ENABLED
        )
        _isSubDisplayTickerEnabled.value = prefs.getBoolean(
            KEY_SUB_DISPLAY_TICKER_ENABLED,
            DEFAULT_SUB_DISPLAY_TICKER_ENABLED
        )
        _isSubDisplayTickerDark.value = prefs.getBoolean(
            KEY_SUB_DISPLAY_TICKER_DARK,
            DEFAULT_SUB_DISPLAY_TICKER_DARK
        )
        _homeMascotSize.value = HomeMascotSize.fromId(
            prefs.getString(KEY_HOME_MASCOT_SIZE, DEFAULT_HOME_MASCOT_SIZE)
        )
    }

    fun isWallpaperOverlayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_WALLPAPER_OVERLAY_ENABLED,
            DEFAULT_WALLPAPER_OVERLAY_ENABLED
        )
    }

    fun setWallpaperOverlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit()
            .putBoolean(KEY_WALLPAPER_OVERLAY_ENABLED, enabled)
            .apply()
        _isWallpaperOverlayEnabled.value = enabled
    }

    fun isSystemMonitorEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_SYSTEM_MONITOR_ENABLED,
            DEFAULT_SYSTEM_MONITOR_ENABLED
        )
    }

    fun setSystemMonitorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SYSTEM_MONITOR_ENABLED, enabled).apply()
        _isSystemMonitorEnabled.value = enabled
    }

    fun setPhotoFrameUri(context: Context, uri: String?) {
        if (uri == null) {
            getPrefs(context).edit().remove(KEY_PHOTO_FRAME_URI).apply()
        } else {
            getPrefs(context).edit().putString(KEY_PHOTO_FRAME_URI, uri).apply()
        }
        _photoFrameUri.value = uri
    }

    fun setPhotoFrameStyle(context: Context, style: String) {
        getPrefs(context).edit().putString(KEY_PHOTO_FRAME_STYLE, style).apply()
        _photoFrameStyle.value = style
    }

    fun setPhotoFrameLabel(context: Context, label: String) {
        getPrefs(context).edit().putString(KEY_PHOTO_FRAME_LABEL, label).apply()
        _photoFrameLabel.value = label
    }

    fun setPhotoFrameEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PHOTO_FRAME_ENABLED, enabled).apply()
        _isPhotoFrameEnabled.value = enabled
    }

    fun isSubDisplayTickerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_SUB_DISPLAY_TICKER_ENABLED,
            DEFAULT_SUB_DISPLAY_TICKER_ENABLED
        )
    }

    fun setSubDisplayTickerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SUB_DISPLAY_TICKER_ENABLED, enabled).apply()
        _isSubDisplayTickerEnabled.value = enabled
    }

    fun isSubDisplayTickerDark(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_SUB_DISPLAY_TICKER_DARK,
            DEFAULT_SUB_DISPLAY_TICKER_DARK
        )
    }

    fun setSubDisplayTickerDark(context: Context, dark: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SUB_DISPLAY_TICKER_DARK, dark).apply()
        _isSubDisplayTickerDark.value = dark
    }

    fun getHomeMascotSize(context: Context): HomeMascotSize =
        if (initialized) {
            _homeMascotSize.value
        } else {
            HomeMascotSize.fromId(
                getPrefs(context).getString(KEY_HOME_MASCOT_SIZE, DEFAULT_HOME_MASCOT_SIZE)
            )
        }

    fun setHomeMascotSize(context: Context, size: HomeMascotSize) {
        getPrefs(context).edit().putString(KEY_HOME_MASCOT_SIZE, size.id).apply()
        _homeMascotSize.value = size
    }

    fun isFolderGridMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_FOLDER_GRID_MODE,
            DEFAULT_FOLDER_GRID_MODE
        )
    }

    fun setFolderGridMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLDER_GRID_MODE, enabled).apply()
        _isFolderGridMode.value = enabled
    }

    fun getSystemMonitorInterval(context: Context): Int {
        return getPrefs(context).getInt(
            KEY_SYSTEM_MONITOR_INTERVAL,
            DEFAULT_SYSTEM_MONITOR_INTERVAL
        )
    }

    fun setSystemMonitorInterval(context: Context, seconds: Int) {
        getPrefs(context).edit().putInt(KEY_SYSTEM_MONITOR_INTERVAL, seconds).apply()
        _systemMonitorInterval.value = seconds
    }

    fun getCustomUserName(context: Context): String {
        return getPrefs(context).getString(
            KEY_CUSTOM_USER_NAME,
            DEFAULT_CUSTOM_USER_NAME
        ) ?: DEFAULT_CUSTOM_USER_NAME
    }

    fun setCustomUserName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_USER_NAME, name).apply()
        _customUserName.value = name
    }

    fun getThemePreset(context: Context): String {
        return getPrefs(context).getString(
            KEY_THEME_PRESET,
            DEFAULT_THEME_PRESET
        ) ?: DEFAULT_THEME_PRESET
    }

    fun setThemePreset(context: Context, preset: String) {
        getPrefs(context).edit().putString(KEY_THEME_PRESET, preset).apply()
        _themePreset.value = preset
    }

    fun getAppDrawerSortMode(context: Context? = null): AppDrawerSortMode {
        return if (initialized) {
            _appDrawerSortMode.value
        } else if (context != null) {
            val sortStr = getPrefs(context).getString(KEY_APP_DRAWER_SORT_MODE, DEFAULT_APP_DRAWER_SORT_MODE) ?: DEFAULT_APP_DRAWER_SORT_MODE
            AppDrawerSortMode.entries.firstOrNull { it.name == sortStr } ?: AppDrawerSortMode.ALPHABETICAL
        } else {
            AppDrawerSortMode.ALPHABETICAL
        }
    }

    fun setAppDrawerSortMode(context: Context, mode: AppDrawerSortMode) {
        getPrefs(context).edit().putString(KEY_APP_DRAWER_SORT_MODE, mode.name).apply()
        _appDrawerSortMode.value = mode
    }

    fun getThemeFontFamily(context: Context? = null): String {
        return if (initialized) {
            _themeFontFamily.value
        } else if (context != null) {
            getPrefs(context).getString(
                KEY_THEME_FONT_FAMILY,
                DEFAULT_THEME_FONT_FAMILY
            ) ?: DEFAULT_THEME_FONT_FAMILY
        } else {
            _themeFontFamily.value
        }
    }

    fun setThemeFontFamily(context: Context, family: String) {
        getPrefs(context).edit().putString(KEY_THEME_FONT_FAMILY, family).apply()
        _themeFontFamily.value = family
    }

    fun getThemeBorderStyle(context: Context? = null): String {
        return if (initialized) {
            _themeBorderStyle.value
        } else if (context != null) {
            CardBorderStyle.normalizeId(
                getPrefs(context).getString(KEY_THEME_BORDER_STYLE, DEFAULT_THEME_BORDER_STYLE)
            )
        } else {
            _themeBorderStyle.value
        }
    }

    fun setThemeBorderStyle(context: Context, borderStyle: String) {
        val normalized = CardBorderStyle.normalizeId(borderStyle)
        getPrefs(context).edit().putString(KEY_THEME_BORDER_STYLE, normalized).apply()
        _themeBorderStyle.value = normalized
    }

    fun getWallpaperUri(context: Context): String? {
        return getPrefs(context).getString(KEY_WALLPAPER_URI, null)
    }

    fun setWallpaperUri(context: Context, uri: String?) {
        if (uri == null) {
            getPrefs(context).edit().remove(KEY_WALLPAPER_URI).apply()
        } else {
            getPrefs(context).edit().putString(KEY_WALLPAPER_URI, uri).apply()
        }
    }
}
