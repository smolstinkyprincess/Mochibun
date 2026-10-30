package com.crinklecodone.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.model.AppDrawerSortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.emptyList

enum class LidOpenAction(val id: String, val label: String) {
    HOME("home", "Home Screen"),
    RESUME_LAST_APP("resume_last", "Resume Last App"),
    NOTIFICATIONS("notifications", "Notifications Screen");

    companion object {
        fun fromId(id: String?): LidOpenAction = entries.firstOrNull { it.id == id } ?: HOME
    }

    fun next(): LidOpenAction = entries[(ordinal + 1) % entries.size]
    fun previous(): LidOpenAction = entries[(ordinal - 1 + entries.size) % entries.size]
}

/**
 * Centralized preferences manager for Mochibun launcher settings.
 * Exposes reactive StateFlows for Compose consumption without requiring launcher restarts.
 */
object PreferencesManager {
    const val PREFS_NAME = "launcher_preferences"
    const val KEY_WALLPAPER_OVERLAY_ENABLED = "pref_wallpaper_overlay_enabled"
    const val DEFAULT_WALLPAPER_OVERLAY_ENABLED = true
    const val KEY_WALLPAPER_OVERLAY_ROTATION_MODE = "pref_wallpaper_overlay_rotation_mode"
    const val ROTATION_MODE_DAILY = "daily"
    const val DEFAULT_WALLPAPER_OVERLAY_ROTATION_MODE = ROTATION_MODE_DAILY


    const val KEY_SYSTEM_MONITOR_ENABLED = "pref_system_monitor_enabled"
    const val DEFAULT_SYSTEM_MONITOR_ENABLED = true
    const val KEY_FAVORITE_FOLDERS = "favorite_folders_v1"

    const val KEY_FOLDER_GRID_MODE = "pref_folder_grid_mode"
    const val DEFAULT_FOLDER_GRID_MODE = false // Compact List by default

    const val KEY_T9_SEARCH_ENABLED = "pref_t9_search_enabled"
    const val DEFAULT_T9_SEARCH_ENABLED = true

    const val KEY_SYSTEM_MONITOR_INTERVAL = "pref_system_monitor_interval_sec"
    const val DEFAULT_SYSTEM_MONITOR_INTERVAL = 2

    const val KEY_CUSTOM_USER_NAME = "custom_user_name"
    const val DEFAULT_CUSTOM_USER_NAME = ""

    const val KEY_THEME_PRESET = "theme_preset"
    const val DEFAULT_THEME_PRESET = "sakura_dream"

    const val KEY_THEME_BG_COLOR = "theme_bg_color"
    const val KEY_THEME_FG_TEXT_COLOR = "theme_fg_text_color"
    const val KEY_THEME_CARD_BG_COLOR = "theme_card_bg_color"
    const val KEY_THEME_CARD_TEXT_COLOR = "theme_card_text_color"
    const val KEY_THEME_GREETING_COLOR = "theme_greeting_color"
    const val KEY_THEME_GREETING_OUTLINE_COLOR = "theme_greeting_outline_color"
    const val KEY_THEME_GREETING_BG_COLOR = "theme_greeting_bg_color"
    const val KEY_THEME_APP_ACTION_COLOR = "theme_app_action_color"
    const val KEY_THEME_CARD_ROUNDNESS = "theme_card_roundness"

    val THEME_COLOR_KEYS = listOf(
        KEY_THEME_BG_COLOR,
        KEY_THEME_FG_TEXT_COLOR,
        KEY_THEME_CARD_BG_COLOR,
        KEY_THEME_CARD_TEXT_COLOR,
        KEY_THEME_GREETING_COLOR,
        KEY_THEME_GREETING_OUTLINE_COLOR,
        KEY_THEME_GREETING_BG_COLOR,
        KEY_THEME_APP_ACTION_COLOR
    )

    const val KEY_DPAD_WRAP_AROUND = "pref_dpad_wrap_around"
    const val DEFAULT_DPAD_WRAP_AROUND = true

    const val KEY_DPAD_BOUNDARY_HAPTICS = "pref_dpad_boundary_haptics"
    const val DEFAULT_DPAD_BOUNDARY_HAPTICS = true
    const val KEY_LID_OPEN_ACTION = "pref_lid_open_action"
    val DEFAULT_LID_OPEN_ACTION = LidOpenAction.HOME.id
    const val KEY_LID_CLOSE_SNAP_SOUND = "pref_lid_close_snap_sound"
    const val DEFAULT_LID_CLOSE_SNAP_SOUND = true

    const val KEY_KEYPAD_SOUND_MODE = "pref_keypad_sound_mode"
    const val DEFAULT_KEYPAD_SOUND_MODE = "kawaii" // "kawaii", "retro", "off"

    const val KEY_LAUNCHER_AUDIO_STREAM = "launcher_audio_stream"
    const val AUDIO_STREAM_MEDIA = "STREAM_MEDIA"
    const val AUDIO_STREAM_NOTIFICATION = "STREAM_NOTIFICATION"
    const val DEFAULT_LAUNCHER_AUDIO_STREAM = AUDIO_STREAM_MEDIA

    const val KEY_LAUNCHER_SOUND_VOLUME = "launcher_sound_volume"
    const val DEFAULT_LAUNCHER_SOUND_VOLUME = 80 // 80%

    const val KEY_LID_LED_ENABLED = "pref_lid_led_enabled"
    const val DEFAULT_LID_LED_ENABLED = true

    const val KEY_LID_LED_CHARGING = "pref_lid_led_charging"
    const val DEFAULT_LID_LED_CHARGING = true

    const val KEY_APP_DRAWER_SORT_MODE = "pref_app_drawer_sort_mode"
    const val DEFAULT_APP_DRAWER_SORT_MODE = "ALPHABETICAL"

    const val KEY_LID_LED_COLOR = "pref_lid_led_color"
    const val DEFAULT_LID_LED_COLOR = "pink" // "pink", "purple", "white", "green"

    const val KEY_MEDIA_VOLUME_LOCK_ENABLED = "pref_media_volume_lock_enabled"
    const val DEFAULT_MEDIA_VOLUME_LOCK_ENABLED = false

    const val KEY_THEME_FONT_FAMILY = "pref_theme_font_family"
    const val DEFAULT_THEME_FONT_FAMILY = "default"

    const val KEY_SPEED_DIAL_PREFIX = "pref_speed_dial_"

    const val KEY_ROOT_A11Y_WATCHDOG_ENABLED = "pref_root_a11y_watchdog_enabled"
    const val DEFAULT_ROOT_A11Y_WATCHDOG_ENABLED = true
    const val KEY_ROOT_QUICK_SETTINGS_ENABLED = "pref_root_quick_settings_enabled"
    const val DEFAULT_ROOT_QUICK_SETTINGS_ENABLED = true

    const val KEY_WALLPAPER_URI = "wallpaper_uri"
    const val KEY_HIDDEN_APPS = "hidden_apps"
    const val KEY_FAVORITE_ITEMS_ORDERED = "favorite_items_ordered_v1"
    const val KEY_CUSTOM_ICON_PREFIX = "custom_icon_"
    const val KEY_CUSTOM_FOLDER_ICON_PREFIX = "custom_folder_icon_"

    private val _isWallpaperOverlayEnabled = MutableStateFlow(DEFAULT_WALLPAPER_OVERLAY_ENABLED)
    val isWallpaperOverlayEnabled: StateFlow<Boolean> = _isWallpaperOverlayEnabled.asStateFlow()

    private val _wallpaperOverlayRotationMode = MutableStateFlow(DEFAULT_WALLPAPER_OVERLAY_ROTATION_MODE)
    val wallpaperOverlayRotationMode: StateFlow<String> = _wallpaperOverlayRotationMode.asStateFlow()


    private val _isSystemMonitorEnabled = MutableStateFlow(DEFAULT_SYSTEM_MONITOR_ENABLED)
    val isSystemMonitorEnabled: StateFlow<Boolean> = _isSystemMonitorEnabled.asStateFlow()

    private val _isFolderGridMode = MutableStateFlow(DEFAULT_FOLDER_GRID_MODE)
    val isFolderGridMode: StateFlow<Boolean> = _isFolderGridMode.asStateFlow()

    private val _isT9SearchEnabled = MutableStateFlow(DEFAULT_T9_SEARCH_ENABLED)
    val isT9SearchEnabled: StateFlow<Boolean> = _isT9SearchEnabled.asStateFlow()

    private val _isDpadWrapAround = MutableStateFlow(DEFAULT_DPAD_WRAP_AROUND)
    val isDpadWrapAround: StateFlow<Boolean> = _isDpadWrapAround.asStateFlow()

    private val _isDpadBoundaryHaptics = MutableStateFlow(DEFAULT_DPAD_BOUNDARY_HAPTICS)
    val isDpadBoundaryHaptics: StateFlow<Boolean> = _isDpadBoundaryHaptics.asStateFlow()

    private val _lidOpenAction = MutableStateFlow(LidOpenAction.HOME)
    val lidOpenAction: StateFlow<LidOpenAction> = _lidOpenAction.asStateFlow()

    private val _isLidCloseSnapSoundEnabled = MutableStateFlow(DEFAULT_LID_CLOSE_SNAP_SOUND)
    val isLidCloseSnapSoundEnabled: StateFlow<Boolean> = _isLidCloseSnapSoundEnabled.asStateFlow()

    private val _keypadSoundMode = MutableStateFlow(DEFAULT_KEYPAD_SOUND_MODE)
    val keypadSoundMode: StateFlow<String> = _keypadSoundMode.asStateFlow()

    private val _launcherAudioStream = MutableStateFlow(DEFAULT_LAUNCHER_AUDIO_STREAM)
    val launcherAudioStream: StateFlow<String> = _launcherAudioStream.asStateFlow()

    private val _launcherSoundVolume = MutableStateFlow(DEFAULT_LAUNCHER_SOUND_VOLUME)
    val launcherSoundVolume: StateFlow<Int> = _launcherSoundVolume.asStateFlow()

    private val _isLidLedEnabled = MutableStateFlow(DEFAULT_LID_LED_ENABLED)
    val isLidLedEnabled: StateFlow<Boolean> = _isLidLedEnabled.asStateFlow()

    private val _isLidLedChargingEnabled = MutableStateFlow(DEFAULT_LID_LED_CHARGING)
    val isLidLedChargingEnabled: StateFlow<Boolean> = _isLidLedChargingEnabled.asStateFlow()

    private val _appDrawerSortMode = MutableStateFlow(AppDrawerSortMode.ALPHABETICAL)
    val appDrawerSortMode: StateFlow<AppDrawerSortMode> = _appDrawerSortMode.asStateFlow()

    private val _lidLedColor = MutableStateFlow(DEFAULT_LID_LED_COLOR)
    val lidLedColor: StateFlow<String> = _lidLedColor.asStateFlow()

    private val _isMediaVolumeLockEnabled = MutableStateFlow(DEFAULT_MEDIA_VOLUME_LOCK_ENABLED)
    val isMediaVolumeLockEnabled: StateFlow<Boolean> = _isMediaVolumeLockEnabled.asStateFlow()

    private val _systemMonitorInterval = MutableStateFlow(DEFAULT_SYSTEM_MONITOR_INTERVAL)
    val systemMonitorInterval: StateFlow<Int> = _systemMonitorInterval.asStateFlow()

    private val _customUserName = MutableStateFlow(DEFAULT_CUSTOM_USER_NAME)
    val customUserName: StateFlow<String> = _customUserName.asStateFlow()

    private val _themePreset = MutableStateFlow(DEFAULT_THEME_PRESET)
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    private val _themeFontFamily = MutableStateFlow(DEFAULT_THEME_FONT_FAMILY)
    val themeFontFamily: StateFlow<String> = _themeFontFamily.asStateFlow()

    private val _isRootQuickSettingsEnabled = MutableStateFlow(DEFAULT_ROOT_QUICK_SETTINGS_ENABLED)
    val isRootQuickSettingsEnabled: StateFlow<Boolean> = _isRootQuickSettingsEnabled.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val prefs = getPrefs(context)
            _isRootQuickSettingsEnabled.value = prefs.getBoolean(
                KEY_ROOT_QUICK_SETTINGS_ENABLED,
                DEFAULT_ROOT_QUICK_SETTINGS_ENABLED
            )
            _isWallpaperOverlayEnabled.value = prefs.getBoolean(
                KEY_WALLPAPER_OVERLAY_ENABLED,
                DEFAULT_WALLPAPER_OVERLAY_ENABLED
            )
            _wallpaperOverlayRotationMode.value = prefs.getString(
                KEY_WALLPAPER_OVERLAY_ROTATION_MODE,
                DEFAULT_WALLPAPER_OVERLAY_ROTATION_MODE
            ) ?: DEFAULT_WALLPAPER_OVERLAY_ROTATION_MODE
            _isSystemMonitorEnabled.value = prefs.getBoolean(
                KEY_SYSTEM_MONITOR_ENABLED,
                DEFAULT_SYSTEM_MONITOR_ENABLED
            )
            _isFolderGridMode.value = prefs.getBoolean(
                KEY_FOLDER_GRID_MODE,
                DEFAULT_FOLDER_GRID_MODE
            )
            _isT9SearchEnabled.value = prefs.getBoolean(
                KEY_T9_SEARCH_ENABLED,
                DEFAULT_T9_SEARCH_ENABLED
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
            _isDpadWrapAround.value = prefs.getBoolean(
                KEY_DPAD_WRAP_AROUND,
                DEFAULT_DPAD_WRAP_AROUND
            )
            _lidOpenAction.value = LidOpenAction.fromId(
                prefs.getString(KEY_LID_OPEN_ACTION, DEFAULT_LID_OPEN_ACTION)
            )
            _isLidCloseSnapSoundEnabled.value = prefs.getBoolean(
                KEY_LID_CLOSE_SNAP_SOUND,
                DEFAULT_LID_CLOSE_SNAP_SOUND
            )
            _isDpadBoundaryHaptics.value = prefs.getBoolean(
                KEY_DPAD_BOUNDARY_HAPTICS,
                DEFAULT_DPAD_BOUNDARY_HAPTICS
            )
            _keypadSoundMode.value = prefs.getString(
                KEY_KEYPAD_SOUND_MODE,
                DEFAULT_KEYPAD_SOUND_MODE
            ) ?: DEFAULT_KEYPAD_SOUND_MODE
            _launcherAudioStream.value = prefs.getString(
                KEY_LAUNCHER_AUDIO_STREAM,
                DEFAULT_LAUNCHER_AUDIO_STREAM
            ) ?: DEFAULT_LAUNCHER_AUDIO_STREAM
            _launcherSoundVolume.value = prefs.getInt(
                KEY_LAUNCHER_SOUND_VOLUME,
                DEFAULT_LAUNCHER_SOUND_VOLUME
            )
            _isLidLedEnabled.value = prefs.getBoolean(
                KEY_LID_LED_ENABLED,
                DEFAULT_LID_LED_ENABLED
            )
            _isLidLedChargingEnabled.value = prefs.getBoolean(
                KEY_LID_LED_CHARGING,
                DEFAULT_LID_LED_CHARGING
            )
            val sortStr = prefs.getString(KEY_APP_DRAWER_SORT_MODE, DEFAULT_APP_DRAWER_SORT_MODE) ?: DEFAULT_APP_DRAWER_SORT_MODE
            _appDrawerSortMode.value = AppDrawerSortMode.entries.firstOrNull { it.name == sortStr }
                ?: AppDrawerSortMode.ALPHABETICAL
            _lidLedColor.value = prefs.getString(
                KEY_LID_LED_COLOR,
                DEFAULT_LID_LED_COLOR
            ) ?: DEFAULT_LID_LED_COLOR
            _isMediaVolumeLockEnabled.value = prefs.getBoolean(
                KEY_MEDIA_VOLUME_LOCK_ENABLED,
                DEFAULT_MEDIA_VOLUME_LOCK_ENABLED
            )
            _themeFontFamily.value = prefs.getString(
                KEY_THEME_FONT_FAMILY,
                DEFAULT_THEME_FONT_FAMILY
            ) ?: DEFAULT_THEME_FONT_FAMILY
            initialized = true
        }
    }

    /** The Kaomoji Wheel is permanently bound to holding '#'. */
    @Suppress("UNUSED_PARAMETER")
    fun matchesKaomojiTrigger(
        eventKeyCode: Int,
        eventScanCode: Int = 0,
        context: Context? = null
    ): Boolean = eventKeyCode == KeyEvent.KEYCODE_POUND

    fun formatKaomojiKeyName(keyCode: Int, scanCode: Int = 0): String {
        val physical = PhysicalKey.fromKeyCode(keyCode)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null
        if (physical != null) {
            return physical.keyName
        }
        return when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "Key ${keyCode - KeyEvent.KEYCODE_0}"
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "Numpad ${keyCode - KeyEvent.KEYCODE_NUMPAD_0}"
            KeyEvent.KEYCODE_MENU -> "Menu Key"
            KeyEvent.KEYCODE_VOLUME_UP -> "Volume Up"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume Down"
            KeyEvent.KEYCODE_VOLUME_MUTE -> "Volume Mute"
            KeyEvent.KEYCODE_DEL -> "Delete / Clear"
            KeyEvent.KEYCODE_CLEAR -> "Clear Key"
            KeyEvent.KEYCODE_SEARCH -> "Search Key"
            KeyEvent.KEYCODE_FOCUS -> "Camera Focus"
            KeyEvent.KEYCODE_HEADSETHOOK -> "Headset Button"
            KeyEvent.KEYCODE_UNKNOWN -> if (scanCode > 0) "ScanCode $scanCode" else "Unknown Key"
            else -> KeyEvent.keyCodeToString(keyCode)
                .removePrefix("KEYCODE_")
                .replace('_', ' ')
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }
    }

    fun formatKaomojiKeyBadge(keyCode: Int, scanCode: Int = 0): String {
        val physical = PhysicalKey.fromKeyCode(keyCode)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null
        return when (physical) {
            PhysicalKey.POUND -> "#"
            PhysicalKey.STAR -> "*"
            PhysicalKey.F1 -> "F1"
            PhysicalKey.F2 -> "F2"
            PhysicalKey.F3 -> "F3"
            PhysicalKey.F4 -> "F4"
            PhysicalKey.CAMERA -> "CAM"
            PhysicalKey.CALL -> "CALL"
            PhysicalKey.APP_SWITCH -> "REC"
            PhysicalKey.KEY_ENTER -> "OK"
            null -> when (keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "${keyCode - KeyEvent.KEYCODE_0}"
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "${keyCode - KeyEvent.KEYCODE_NUMPAD_0}"
                KeyEvent.KEYCODE_MENU -> "MENU"
                KeyEvent.KEYCODE_VOLUME_UP -> "VOL+"
                KeyEvent.KEYCODE_VOLUME_DOWN -> "VOL-"
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_CLEAR -> "CLR"
                KeyEvent.KEYCODE_UNKNOWN -> if (scanCode > 0) "SC$scanCode" else "KEY"
                else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").take(6)
            }
        }
    }

    fun getKaomojiTriggerBadge(): String = "#"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    fun isT9SearchEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_T9_SEARCH_ENABLED,
            DEFAULT_T9_SEARCH_ENABLED
        )
    }

    fun setT9SearchEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_T9_SEARCH_ENABLED, enabled).apply()
        _isT9SearchEnabled.value = enabled
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

    fun isDpadWrapAround(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_DPAD_WRAP_AROUND,
            DEFAULT_DPAD_WRAP_AROUND
        )
    }

    fun setDpadWrapAround(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_WRAP_AROUND, enabled).apply()
        _isDpadWrapAround.value = enabled
    }

    fun isDpadBoundaryHaptics(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_DPAD_BOUNDARY_HAPTICS,
            DEFAULT_DPAD_BOUNDARY_HAPTICS
        )
    }

    fun getLidOpenAction(context: Context): LidOpenAction =
        LidOpenAction.fromId(getPrefs(context).getString(KEY_LID_OPEN_ACTION, DEFAULT_LID_OPEN_ACTION))

    fun setLidOpenAction(context: Context, action: LidOpenAction) {
        getPrefs(context).edit().putString(KEY_LID_OPEN_ACTION, action.id).apply()
        _lidOpenAction.value = action
    }

    fun isLidCloseSnapSoundEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_LID_CLOSE_SNAP_SOUND, DEFAULT_LID_CLOSE_SNAP_SOUND)

    fun setLidCloseSnapSoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LID_CLOSE_SNAP_SOUND, enabled).apply()
        _isLidCloseSnapSoundEnabled.value = enabled
    }

    fun setDpadBoundaryHaptics(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_BOUNDARY_HAPTICS, enabled).apply()
        _isDpadBoundaryHaptics.value = enabled
    }

    fun getKeypadSoundMode(context: Context? = null): String {
        return if (initialized) {
            _keypadSoundMode.value
        } else if (context != null) {
            getPrefs(context).getString(
                KEY_KEYPAD_SOUND_MODE,
                DEFAULT_KEYPAD_SOUND_MODE
            ) ?: DEFAULT_KEYPAD_SOUND_MODE
        } else {
            _keypadSoundMode.value
        }
    }

    fun setKeypadSoundMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_KEYPAD_SOUND_MODE, mode).commit()
        _keypadSoundMode.value = mode
    }

    fun getLauncherAudioStream(context: Context? = null): String {
        return if (initialized) {
            _launcherAudioStream.value
        } else if (context != null) {
            getPrefs(context).getString(
                KEY_LAUNCHER_AUDIO_STREAM,
                DEFAULT_LAUNCHER_AUDIO_STREAM
            ) ?: DEFAULT_LAUNCHER_AUDIO_STREAM
        } else {
            _launcherAudioStream.value
        }
    }

    fun setLauncherAudioStream(context: Context, stream: String) {
        getPrefs(context).edit().putString(KEY_LAUNCHER_AUDIO_STREAM, stream).commit()
        _launcherAudioStream.value = stream
        SoundEffectManager.updateAudioStream(context)
    }

    fun getLauncherSoundVolume(context: Context? = null): Int {
        return if (initialized) {
            _launcherSoundVolume.value
        } else if (context != null) {
            getPrefs(context).getInt(
                KEY_LAUNCHER_SOUND_VOLUME,
                DEFAULT_LAUNCHER_SOUND_VOLUME
            )
        } else {
            _launcherSoundVolume.value
        }
    }

    fun setLauncherSoundVolume(context: Context, volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        getPrefs(context).edit().putInt(KEY_LAUNCHER_SOUND_VOLUME, clamped).commit()
        _launcherSoundVolume.value = clamped
    }

    fun isLidLedEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_LID_LED_ENABLED,
            DEFAULT_LID_LED_ENABLED
        )
    }

    fun setLidLedEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LID_LED_ENABLED, enabled).apply()
        _isLidLedEnabled.value = enabled
    }

    fun isLidLedChargingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_LID_LED_CHARGING,
            DEFAULT_LID_LED_CHARGING
        )
    }

    fun setLidLedChargingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LID_LED_CHARGING, enabled).apply()
        _isLidLedChargingEnabled.value = enabled
    }

    fun getLidLedColor(context: Context): String {
        return getPrefs(context).getString(
            KEY_LID_LED_COLOR,
            DEFAULT_LID_LED_COLOR
        ) ?: DEFAULT_LID_LED_COLOR
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

    fun setLidLedColor(context: Context, color: String) {
        getPrefs(context).edit().putString(KEY_LID_LED_COLOR, color).apply()
        _lidLedColor.value = color
    }

    fun isMediaVolumeLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_MEDIA_VOLUME_LOCK_ENABLED,
            DEFAULT_MEDIA_VOLUME_LOCK_ENABLED
        )
    }

    fun setMediaVolumeLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MEDIA_VOLUME_LOCK_ENABLED, enabled).apply()
        _isMediaVolumeLockEnabled.value = enabled
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
        getPrefs(context).edit().putString(KEY_THEME_FONT_FAMILY, family).commit()
        _themeFontFamily.value = family
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

    fun getHiddenPackages(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return try {
            val rawObj = prefs.all[KEY_HIDDEN_APPS]
            when (rawObj) {
                is Set<*> -> {
                    val set = rawObj.filterIsInstance<String>().toSet()
                    setHiddenPackages(context, set)
                    set
                }
                is String -> {
                    if (rawObj.isBlank()) emptySet() else rawObj.split(",").toSet()
                }
                else -> emptySet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setHiddenPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putString(KEY_HIDDEN_APPS, packages.joinToString(",")).apply()
    }

    fun addHiddenPackage(context: Context, packageName: String) {
        val current = getHiddenPackages(context).toMutableSet()
        current.add(packageName)
        setHiddenPackages(context, current)
    }

    fun removeHiddenPackage(context: Context, packageName: String) {
        val current = getHiddenPackages(context).toMutableSet()
        current.remove(packageName)
        setHiddenPackages(context, current)
    }

    fun setCustomAppIcon(context: Context, packageName: String, iconName: String?) {
        val key = "$KEY_CUSTOM_ICON_PREFIX$packageName"
        val editor = getPrefs(context).edit()
        if (iconName != null) {
            editor.putString(key, iconName).apply()
        } else {
            editor.remove(key).apply()
        }
    }

    fun setCustomFolderIcon(context: Context, folderId: String, iconName: String?) {
        val key = "$KEY_CUSTOM_FOLDER_ICON_PREFIX$folderId"
        val editor = getPrefs(context).edit()
        if (iconName != null) {
            editor.putString(key, iconName).apply()
        } else {
            editor.remove(key).apply()
        }
    }

    fun saveFavoriteFolders(context: Context, folders: List<FavoriteFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            val obj = JSONObject()
            obj.put("id", folder.id)
            obj.put("name", folder.name)
            obj.put("apps", JSONArray(folder.appPackages))
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_FAVORITE_FOLDERS, array.toString()).apply()
    }

    fun loadFavoriteFolders(context: Context): List<FavoriteFolder> {
        val raw = getPrefs(context).getString(KEY_FAVORITE_FOLDERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<FavoriteFolder>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val appsArray = obj.getJSONArray("apps")
                val apps = mutableListOf<String>()
                for (j in 0 until appsArray.length()) {
                    apps.add(appsArray.getString(j))
                }
                list.add(FavoriteFolder(id = id, name = name, appPackages = apps))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getFavoriteItemKeys(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_FAVORITE_ITEMS_ORDERED, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotEmpty() }
    }

    fun saveFavoriteItemKeys(context: Context, keys: List<String>) {
        getPrefs(context).edit()
            .putString(KEY_FAVORITE_ITEMS_ORDERED, keys.joinToString("|"))
            .apply()
    }

    fun getSpeedDialPackage(context: Context, digit: Int): String? {
        if (digit !in 2..9) return null
        return getPrefs(context).getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
    }

    fun setSpeedDialPackage(context: Context, digit: Int, packageName: String?) {
        if (digit !in 2..9) return
        val prefs = getPrefs(context)
        if (packageName == null) {
            prefs.edit().remove("$KEY_SPEED_DIAL_PREFIX$digit").apply()
        } else {
            prefs.edit().putString("$KEY_SPEED_DIAL_PREFIX$digit", packageName).apply()
        }
    }

    fun getAllSpeedDials(context: Context): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        val prefs = getPrefs(context)
        for (digit in 2..9) {
            val pkg = prefs.getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
            if (pkg != null) {
                map[digit] = pkg
            }
        }
        return map
    }

    fun isRootQuickSettingsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ROOT_QUICK_SETTINGS_ENABLED, DEFAULT_ROOT_QUICK_SETTINGS_ENABLED)
    }

    fun setRootQuickSettingsEnabled(context: Context, enabled: Boolean) {
        _isRootQuickSettingsEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_ROOT_QUICK_SETTINGS_ENABLED, enabled).apply()
    }

    fun isRootA11yWatchdogEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ROOT_A11Y_WATCHDOG_ENABLED, DEFAULT_ROOT_A11Y_WATCHDOG_ENABLED)
    }

    fun setRootA11yWatchdogEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ROOT_A11Y_WATCHDOG_ENABLED, enabled).apply()
    }
}
