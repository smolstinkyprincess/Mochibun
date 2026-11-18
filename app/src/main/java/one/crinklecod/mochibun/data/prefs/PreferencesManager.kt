package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.MediaVolumeLockSync
import one.crinklecod.mochibun.data.model.AppDrawerSortMode
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.model.PhysicalKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.emptyList

enum class LidOpenAction(val id: String, val label: String) {
    HOME("home", "Home Screen"),
    RESUME_LAST_APP("resume_last", "Resume Last App"),
    NOTIFICATIONS("notifications", "Notifications Screen"),
    QUICK_MEMO("quick_memo", "Quick Memo");

    companion object {
        fun fromId(id: String?): LidOpenAction = entries.firstOrNull { it.id == id } ?: HOME
    }

    fun next(): LidOpenAction = entries[(ordinal + 1) % entries.size]
    fun previous(): LidOpenAction = entries[(ordinal - 1 + entries.size) % entries.size]
}

/**
 * Preset sizes for the Home screen mascot (MochibunnyWithSpeechBubble).
 * Small matches the original 56.dp layout.
 */
enum class HomeMascotSize(val id: String, val label: String, val sizeDp: Int) {
    SMALL("small", "Small", 56),
    MEDIUM("medium", "Medium", 72),
    LARGE("large", "Large", 88);

    companion object {
        fun fromId(id: String?): HomeMascotSize = entries.firstOrNull { it.id == id } ?: SMALL
    }

    fun next(): HomeMascotSize = entries[(ordinal + 1) % entries.size]
    fun previous(): HomeMascotSize = entries[(ordinal - 1 + entries.size) % entries.size]
}

/**
 * Today's step count from the cumulative-since-boot `TYPE_STEP_COUNTER` sensor.
 *
 * @param day day stamp (`year * 1000 + dayOfYear`) the state belongs to
 * @param baseline counter value that counts as 0 steps today (since the last reboot)
 * @param carried steps already walked today before a reboot reset the counter
 * @param lastCounter most recent counter reading
 */
data class DailyStepState(val day: Int, val baseline: Long, val carried: Long, val lastCounter: Long) {
    val stepsToday: Long get() = carried + (lastCounter - baseline).coerceAtLeast(0L)

    companion object {
        fun dayStamp(year: Int, dayOfYear: Int): Int = year * 1000 + dayOfYear

        /**
         * Folds a new counter reading into [previous].
         * - New day: today starts from the last reading we saw (steps after it count for today).
         * - Counter went backwards: the phone rebooted, so keep today's steps in [carried].
         */
        fun update(previous: DailyStepState?, today: Int, counter: Long): DailyStepState {
            if (previous == null) return DailyStepState(today, counter, 0L, counter)
            val rebooted = counter < previous.lastCounter
            if (previous.day != today) {
                val baseline = if (rebooted) 0L else previous.lastCounter
                return DailyStepState(today, baseline, 0L, counter)
            }
            if (rebooted) {
                return DailyStepState(today, 0L, previous.stepsToday, counter)
            }
            return previous.copy(lastCounter = counter)
        }
    }
}

/**
 * Centralized preferences manager for Mochibun launcher settings.
 * Exposes reactive StateFlows for Compose consumption without requiring launcher restarts.
 */
object PreferencesManager {
    const val PREFS_NAME = "launcher_preferences"
    const val KEY_WALLPAPER_OVERLAY_ENABLED = "pref_wallpaper_overlay_enabled"
    const val DEFAULT_WALLPAPER_OVERLAY_ENABLED = true


    const val KEY_SYSTEM_MONITOR_ENABLED = "pref_system_monitor_enabled"
    const val DEFAULT_SYSTEM_MONITOR_ENABLED = true
    const val KEY_FAVORITE_FOLDERS = "favorite_folders_v1"

    const val KEY_FOLDER_GRID_MODE = "pref_folder_grid_mode"
    const val DEFAULT_FOLDER_GRID_MODE = false // Compact List by default

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
    const val KEY_THEME_BORDER_STYLE = "theme_border_style"
    const val DEFAULT_THEME_BORDER_STYLE = CardBorderStyle.DEFAULT_ID
    const val KEY_CUSTOM_CARD_BORDER_SLICE_PX = "custom_card_border_slice_px"
    const val DEFAULT_CUSTOM_CARD_BORDER_SLICE_PX = CardBorderStyle.DEFAULT_SLICE_PX

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
    const val KEY_LAST_LAUNCHED_PACKAGE = "pref_last_launched_package"
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

    /** Lid step animation per slot; blank = classic behavior (notification shimmer / solid charging). */
    const val KEY_LID_LED_NOTIFICATION_ANIMATION_ID = "pref_lid_led_animation_id" // pre-slot key, kept
    const val KEY_LID_LED_CHARGING_ANIMATION_ID = "pref_lid_led_charging_animation_id"

    const val KEY_MEDIA_VOLUME_LOCK_ENABLED = "pref_media_volume_lock_enabled"
    const val DEFAULT_MEDIA_VOLUME_LOCK_ENABLED = false

    const val KEY_THEME_FONT_FAMILY = "pref_theme_font_family"
    const val DEFAULT_THEME_FONT_FAMILY = "default"

    const val KEY_SPEED_DIAL_PREFIX = "pref_speed_dial_"

    // Stored key keeps its pre-LSPosed name so existing installs keep their value
    const val KEY_CURSOR_WATCHDOG_ENABLED = "pref_root_a11y_watchdog_enabled"
    const val DEFAULT_CURSOR_WATCHDOG_ENABLED = true
    const val KEY_ROOT_QUICK_SETTINGS_ENABLED = "pref_root_quick_settings_enabled"
    const val DEFAULT_ROOT_QUICK_SETTINGS_ENABLED = true
    const val KEY_HOOK_KEY_ENABLED = "pref_hook_key_enabled"
    const val DEFAULT_HOOK_KEY_ENABLED = true
    const val KEY_HOOK_SYSUI_ENABLED = "pref_hook_sysui_enabled"
    const val DEFAULT_HOOK_SYSUI_ENABLED = true
    const val GLOBAL_HOOK_KEY_ENABLED = "mochibun_hook_key_enabled"
    const val GLOBAL_HOOK_SYSUI_ENABLED = "mochibun_hook_sysui_enabled"
    const val GLOBAL_KEY_HOOK_ALIVE_ELAPSED = "mochibun_key_hook_alive_elapsed"
    const val GLOBAL_KEY_HOOK_ALIVE_BOOT_COUNT = "mochibun_key_hook_alive_boot_count"

    const val KEY_IME_HOLD_DELAY_MS = "pref_ime_hold_delay_ms"
    const val DEFAULT_IME_HOLD_DELAY_MS = 400
    const val MIN_IME_HOLD_DELAY_MS = 250
    const val MAX_IME_HOLD_DELAY_MS = 800
    const val KEY_IME_HOLD_VIBRATION = "pref_ime_hold_vibration"
    const val KEY_IME_HOLD_FOR_DIGIT_ENABLED = "pref_ime_hold_for_digit_enabled"
    const val KEY_IME_AUTO_COMMIT_ENABLED = "pref_ime_auto_commit_enabled"
    const val DEFAULT_IME_HOLD_FOR_DIGIT_ENABLED = true
    const val DEFAULT_IME_AUTO_COMMIT_ENABLED = true
    // T9 multi-tap auto-commit delay (Keyboard → Input Behavior → T9 Auto-Commit Delay)
    const val KEY_IME_IDLE_COMMIT_MS = "pref_ime_idle_commit_ms"
    const val DEFAULT_IME_IDLE_COMMIT_MS = 700
    const val MIN_IME_IDLE_COMMIT_MS = 300
    const val MAX_IME_IDLE_COMMIT_MS = 1500
    const val STEP_IME_IDLE_COMMIT_MS = 100

    // Recents → LSK and Back → RSK remapping on Home / App Drawer. Mirrored into Settings.Global
    // so MochibunKeyHook (system_server, can't read our prefs) knows whether to swallow the keys.
    const val KEY_SOFT_KEY_NAV_ENABLED = "pref_soft_key_nav_enabled"
    const val DEFAULT_SOFT_KEY_NAV_ENABLED = true
    const val GLOBAL_SOFT_KEY_NAV_ENABLED = "mochibun_soft_key_nav_enabled"
    /** Settings.Global mirror of CursorPreferences.getToggleKeyCode for MochibunKeyHook. */
    const val GLOBAL_CURSOR_TOGGLE_KEYCODE = "mochibun_cursor_toggle_keycode"
    const val DEFAULT_IME_HOLD_VIBRATION = true

    // Non-Root Mode (v2.1.0 batch 2). Both defaults depend on the device, so they are computed
    // on read until the user changes the switch (see defaultShowRootOptions / defaultNonRootScreenOffReset).
    const val KEY_SHOW_ROOT_OPTIONS = "pref_show_root_options"
    const val KEY_NON_ROOT_SCREEN_OFF_RESET = "pref_non_root_screen_off_reset"

    const val KEY_WALLPAPER_URI = "wallpaper_uri"

    const val KEY_PHOTO_FRAME_URI = "pref_photo_frame_uri"
    const val KEY_PHOTO_FRAME_STYLE = "pref_photo_frame_style"
    const val DEFAULT_PHOTO_FRAME_STYLE = "INSTAX" // "INSTAX", "PURIKURA", "PEARL_LACE"
    const val KEY_PHOTO_FRAME_LABEL = "pref_photo_frame_label"
    const val DEFAULT_PHOTO_FRAME_LABEL = "Mochi ♡"
    const val KEY_PHOTO_FRAME_ENABLED = "pref_photo_frame_enabled"
    const val DEFAULT_PHOTO_FRAME_ENABLED = true
    const val KEY_SUB_DISPLAY_TICKER_ENABLED = "pref_sub_display_ticker_enabled"
    const val DEFAULT_SUB_DISPLAY_TICKER_ENABLED = true
    const val KEY_SUB_DISPLAY_TICKER_DARK = "pref_sub_display_ticker_dark"
    const val DEFAULT_SUB_DISPLAY_TICKER_DARK = false
    const val KEY_HOME_MASCOT_SIZE = "pref_home_mascot_size"
    val DEFAULT_HOME_MASCOT_SIZE = HomeMascotSize.SMALL.id
    const val KEY_QUICK_MEMO_TEXT = "pref_quick_memo_text"
    /** Quick Memo length cap (a keitai-sized sticky note). */
    const val QUICK_MEMO_MAX_CHARS = 140
    // Daily step counter bookkeeping (see [DailyStepState])
    const val KEY_STEP_DAY = "pref_step_day"
    const val KEY_STEP_BASELINE = "pref_step_baseline"
    const val KEY_STEP_CARRIED = "pref_step_carried"
    const val KEY_STEP_LAST_COUNTER = "pref_step_last_counter"
    const val KEY_HIDDEN_APPS = "hidden_apps"
    const val KEY_FAVORITE_ITEMS_ORDERED = "favorite_items_ordered_v1"
    const val KEY_CUSTOM_ICON_PREFIX = "custom_icon_"
    const val KEY_CUSTOM_FOLDER_ICON_PREFIX = "custom_folder_icon_"

    private val _isWallpaperOverlayEnabled = MutableStateFlow(DEFAULT_WALLPAPER_OVERLAY_ENABLED)
    val isWallpaperOverlayEnabled: StateFlow<Boolean> = _isWallpaperOverlayEnabled.asStateFlow()


    private val _isSystemMonitorEnabled = MutableStateFlow(DEFAULT_SYSTEM_MONITOR_ENABLED)
    val isSystemMonitorEnabled: StateFlow<Boolean> = _isSystemMonitorEnabled.asStateFlow()

    private val _isFolderGridMode = MutableStateFlow(DEFAULT_FOLDER_GRID_MODE)
    val isFolderGridMode: StateFlow<Boolean> = _isFolderGridMode.asStateFlow()


    private val _isDpadWrapAround = MutableStateFlow(DEFAULT_DPAD_WRAP_AROUND)
    val isDpadWrapAround: StateFlow<Boolean> = _isDpadWrapAround.asStateFlow()

    private val _isDpadBoundaryHaptics = MutableStateFlow(DEFAULT_DPAD_BOUNDARY_HAPTICS)
    val isDpadBoundaryHaptics: StateFlow<Boolean> = _isDpadBoundaryHaptics.asStateFlow()

    private val _lidOpenAction = MutableStateFlow(LidOpenAction.HOME)
    val lidOpenAction: StateFlow<LidOpenAction> = _lidOpenAction.asStateFlow()

    private val _isLidCloseSnapSoundEnabled = MutableStateFlow(DEFAULT_LID_CLOSE_SNAP_SOUND)
    val isLidCloseSnapSoundEnabled: StateFlow<Boolean> = _isLidCloseSnapSoundEnabled.asStateFlow()

    private val _isSoftKeyNavEnabled = MutableStateFlow(DEFAULT_SOFT_KEY_NAV_ENABLED)
    val isSoftKeyNavEnabled: StateFlow<Boolean> = _isSoftKeyNavEnabled.asStateFlow()

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

    private val _themeBorderStyle = MutableStateFlow(DEFAULT_THEME_BORDER_STYLE)
    val themeBorderStyle: StateFlow<String> = _themeBorderStyle.asStateFlow()

    private val _isRootQuickSettingsEnabled = MutableStateFlow(DEFAULT_ROOT_QUICK_SETTINGS_ENABLED)
    val isRootQuickSettingsEnabled: StateFlow<Boolean> = _isRootQuickSettingsEnabled.asStateFlow()

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

    private val _quickMemoText = MutableStateFlow("")
    /** Quick Memo text (`#` hold on Home); shown in the sub-display ticker when non-blank. */
    val quickMemoText: StateFlow<String> = _quickMemoText.asStateFlow()

    private val _showRootOptions = MutableStateFlow(false)
    /** Whether root / LSPosed rows are shown in settings. Loaded in [init]. */
    val showRootOptions: StateFlow<Boolean> = _showRootOptions.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            reload(context)
            initialized = true
        }
        // Outside the guard so restore / reset-to-defaults (which call init again) refresh it
        _showRootOptions.value = isShowRootOptions(context)
    }

    /**
     * Re-reads every cached StateFlow from SharedPreferences. Call after anything that rewrites
     * prefs behind the setters (backup restore, factory reset) so observers update immediately.
     */
    fun reload(context: Context) {
        val prefs = getPrefs(context)
        _isRootQuickSettingsEnabled.value = prefs.getBoolean(
            KEY_ROOT_QUICK_SETTINGS_ENABLED,
            DEFAULT_ROOT_QUICK_SETTINGS_ENABLED
        )
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
        _isSoftKeyNavEnabled.value = prefs.getBoolean(
            KEY_SOFT_KEY_NAV_ENABLED,
            DEFAULT_SOFT_KEY_NAV_ENABLED
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
        _quickMemoText.value = prefs.getString(KEY_QUICK_MEMO_TEXT, "") ?: ""
        _showRootOptions.value = isShowRootOptions(context)
    }

    /** `#` is the fixed, always-on Kaomoji Wheel trigger (hold). */
    fun matchesKaomojiTrigger(eventKeyCode: Int): Boolean = eventKeyCode == KeyEvent.KEYCODE_POUND

    fun formatKeyName(keyCode: Int, scanCode: Int = 0): String {
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

    fun formatKeyBadge(keyCode: Int, scanCode: Int = 0): String {
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

    /** Saves the memo text (trimmed of trailing blank lines, capped at [QUICK_MEMO_MAX_CHARS]). */
    fun setQuickMemoText(context: Context, text: String) {
        val clean = text.take(QUICK_MEMO_MAX_CHARS).trimEnd()
        getPrefs(context).edit().putString(KEY_QUICK_MEMO_TEXT, clean).apply()
        _quickMemoText.value = clean
    }

    /** Clear button: empties the memo (which also removes it from the ticker). */
    fun clearQuickMemo(context: Context) {
        setQuickMemoText(context, "")
    }

    fun getDailyStepState(context: Context): DailyStepState? {
        val prefs = getPrefs(context)
        val day = prefs.getInt(KEY_STEP_DAY, -1)
        if (day < 0) return null
        return DailyStepState(
            day = day,
            baseline = prefs.getLong(KEY_STEP_BASELINE, 0L),
            carried = prefs.getLong(KEY_STEP_CARRIED, 0L),
            lastCounter = prefs.getLong(KEY_STEP_LAST_COUNTER, 0L)
        )
    }

    fun setDailyStepState(context: Context, state: DailyStepState) {
        getPrefs(context).edit()
            .putInt(KEY_STEP_DAY, state.day)
            .putLong(KEY_STEP_BASELINE, state.baseline)
            .putLong(KEY_STEP_CARRIED, state.carried)
            .putLong(KEY_STEP_LAST_COUNTER, state.lastCounter)
            .apply()
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

    fun getLastLaunchedPackage(context: Context): String? {
        val pkg = getPrefs(context).getString(KEY_LAST_LAUNCHED_PACKAGE, null)?.trim()
        return if (pkg.isNullOrEmpty() || pkg == context.packageName) null else pkg
    }

    fun setLastLaunchedPackage(context: Context, packageName: String?) {
        val pkg = packageName?.trim()
        if (pkg.isNullOrEmpty() || pkg == context.packageName) return
        getPrefs(context).edit().putString(KEY_LAST_LAUNCHED_PACKAGE, pkg).apply()
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
        getPrefs(context).edit().putString(KEY_KEYPAD_SOUND_MODE, mode).apply()
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
        getPrefs(context).edit().putString(KEY_LAUNCHER_AUDIO_STREAM, stream).apply()
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
        getPrefs(context).edit().putInt(KEY_LAUNCHER_SOUND_VOLUME, clamped).apply()
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

    fun getLidLedNotificationAnimationId(context: Context): String =
        getPrefs(context).getString(KEY_LID_LED_NOTIFICATION_ANIMATION_ID, "") ?: ""

    fun setLidLedNotificationAnimationId(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_LID_LED_NOTIFICATION_ANIMATION_ID, id).apply()
    }

    fun getLidLedChargingAnimationId(context: Context): String =
        getPrefs(context).getString(KEY_LID_LED_CHARGING_ANIMATION_ID, "") ?: ""

    fun setLidLedChargingAnimationId(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_LID_LED_CHARGING_ANIMATION_ID, id).apply()
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
        MediaVolumeLockSync.push(context, enabled)
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

    /** Root options start visible only when a su binary is present. */
    fun defaultShowRootOptions(suPresent: Boolean): Boolean = suPresent

    fun isShowRootOptions(
        context: Context,
        suPresent: Boolean = one.crinklecod.mochibun.core.system.RootShell.isSuBinaryPresent()
    ): Boolean = getPrefs(context).getBoolean(KEY_SHOW_ROOT_OPTIONS, defaultShowRootOptions(suPresent))

    fun setShowRootOptions(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ROOT_OPTIONS, show).apply()
        _showRootOptions.value = show
    }

    /**
     * Root's hall-switch reader or the LSPosed key hook already resets the UI on a real flip
     * close, so the screen-off reset defaults on only when neither is around.
     */
    fun defaultNonRootScreenOffReset(lidDetectionActive: Boolean): Boolean = !lidDetectionActive

    /** True when root (hall-switch reader) or the LSPosed key hook can see the flip close. */
    fun isLidDetectionActive(context: Context): Boolean =
        one.crinklecod.mochibun.core.system.RootShell.isSuBinaryPresent() ||
            one.crinklecod.mochibun.core.system.KeyHookStatus.isActive(context)

    /** [lidDetectionActive] overrides the device probe (tests); it is only consulted while unset. */
    fun isNonRootScreenOffResetEnabled(context: Context, lidDetectionActive: Boolean? = null): Boolean {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_NON_ROOT_SCREEN_OFF_RESET)) {
            return prefs.getBoolean(KEY_NON_ROOT_SCREEN_OFF_RESET, true)
        }
        return defaultNonRootScreenOffReset(lidDetectionActive ?: isLidDetectionActive(context))
    }

    fun setNonRootScreenOffResetEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NON_ROOT_SCREEN_OFF_RESET, enabled).apply()
    }

    fun isKeyHookEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_HOOK_KEY_ENABLED, DEFAULT_HOOK_KEY_ENABLED)

    fun setKeyHookEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_KEY_ENABLED, enabled).apply()
    }

    fun isSystemUiHookEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_HOOK_SYSUI_ENABLED, DEFAULT_HOOK_SYSUI_ENABLED)

    fun setSystemUiHookEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_SYSUI_ENABLED, enabled).apply()
    }

    fun isCursorWatchdogEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CURSOR_WATCHDOG_ENABLED, DEFAULT_CURSOR_WATCHDOG_ENABLED)
    }

    /** How long a keypad key must be held in MochibunImeService to type its digit. */
    fun getImeHoldDelayMs(context: Context): Int =
        getPrefs(context).getInt(KEY_IME_HOLD_DELAY_MS, DEFAULT_IME_HOLD_DELAY_MS)
            .coerceIn(MIN_IME_HOLD_DELAY_MS, MAX_IME_HOLD_DELAY_MS)

    fun setImeHoldDelayMs(context: Context, delayMs: Int) {
        getPrefs(context).edit()
            .putInt(KEY_IME_HOLD_DELAY_MS, delayMs.coerceIn(MIN_IME_HOLD_DELAY_MS, MAX_IME_HOLD_DELAY_MS))
            .apply()
    }

    /** Idle time after which MochibunImeService commits a pending multi-tap letter. */
    fun getImeIdleCommitMs(context: Context): Int =
        getPrefs(context).getInt(KEY_IME_IDLE_COMMIT_MS, DEFAULT_IME_IDLE_COMMIT_MS)
            .coerceIn(MIN_IME_IDLE_COMMIT_MS, MAX_IME_IDLE_COMMIT_MS)

    /** Next auto-commit delay one [STEP_IME_IDLE_COMMIT_MS] notch in [direction] (-1 / +1), clamped. */
    fun stepImeIdleCommitMs(currentMs: Int, direction: Int): Int =
        (currentMs + direction * STEP_IME_IDLE_COMMIT_MS)
            .coerceIn(MIN_IME_IDLE_COMMIT_MS, MAX_IME_IDLE_COMMIT_MS)

    fun setImeIdleCommitMs(context: Context, delayMs: Int) {
        getPrefs(context).edit()
            .putInt(KEY_IME_IDLE_COMMIT_MS, delayMs.coerceIn(MIN_IME_IDLE_COMMIT_MS, MAX_IME_IDLE_COMMIT_MS))
            .apply()
    }

    /** Short vibration when a held key in MochibunImeService types its digit. */
    fun isImeHoldVibrationEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_HOLD_VIBRATION, DEFAULT_IME_HOLD_VIBRATION)

    fun isImeHoldForDigitEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_HOLD_FOR_DIGIT_ENABLED, DEFAULT_IME_HOLD_FOR_DIGIT_ENABLED)

    fun setImeHoldForDigitEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_HOLD_FOR_DIGIT_ENABLED, enabled).apply()
    }

    fun isImeAutoCommitEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_AUTO_COMMIT_ENABLED, DEFAULT_IME_AUTO_COMMIT_ENABLED)

    fun setImeAutoCommitEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_AUTO_COMMIT_ENABLED, enabled).apply()
    }

    fun setImeHoldVibrationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_HOLD_VIBRATION, enabled).apply()
    }

    fun isSoftKeyNavEnabled(context: Context): Boolean =
        if (initialized) {
            _isSoftKeyNavEnabled.value
        } else {
            getPrefs(context).getBoolean(KEY_SOFT_KEY_NAV_ENABLED, DEFAULT_SOFT_KEY_NAV_ENABLED)
        }

    fun setSoftKeyNavEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOFT_KEY_NAV_ENABLED, enabled).apply()
        _isSoftKeyNavEnabled.value = enabled
        syncSoftKeyNavToGlobal(context)
    }

    /**
     * Mirrors the Soft-Key Navigation pref into Settings.Global for MochibunKeyHook. Writes only
     * when the stored global differs. Tries a direct write first (works once WRITE_SECURE_SETTINGS
     * is granted), then falls back to root. Without either, the in-app handler still honours the pref.
     */
    fun syncSoftKeyNavToGlobal(context: Context) {
        mirrorIntToGlobal(context, GLOBAL_SOFT_KEY_NAV_ENABLED, if (isSoftKeyNavEnabled(context)) 1 else 0, 1)
    }

    /**
     * Writes [value] to Settings.Global [name] for MochibunKeyHook, only when it differs from the
     * stored value ([defaultValue] when unset). Direct write first (needs WRITE_SECURE_SETTINGS),
     * then root. Without either the hook keeps reading [defaultValue].
     */
    internal fun mirrorIntToGlobal(context: Context, name: String, value: Int, defaultValue: Int) {
        val resolver = context.contentResolver
        val current = try {
            android.provider.Settings.Global.getInt(resolver, name, defaultValue)
        } catch (_: Exception) {
            Int.MIN_VALUE
        }
        if (current == value) return
        val written = try {
            android.provider.Settings.Global.putInt(resolver, name, value)
        } catch (_: SecurityException) {
            false
        }
        if (!written) {
            one.crinklecod.mochibun.core.system.RootShell.submit("settings put global $name $value")
        }
    }
}
