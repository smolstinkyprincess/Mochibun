package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import one.crinklecod.mochibun.data.model.AppDrawerSortMode
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.model.AppLedRuleMode
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.model.LidAppLedRule
import kotlinx.coroutines.flow.StateFlow

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

    /** Folder tray: tilted sticker constellations (true) or a straight 3 × 2 grid (false). */
    const val KEY_FOLDER_SCATTER_LAYOUT = "pref_folder_scatter_layout"
    const val DEFAULT_FOLDER_SCATTER_LAYOUT = true

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
    /** JSON array of [LidAppLedRule] (per-app notification LED overrides). */
    const val KEY_LID_LED_APP_RULES = "pref_lid_led_app_rules"

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
    // T9 multi-tap auto-commit delay (Keyboard → Keypad Feel → Auto-Commit Delay)
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
    /** Number of Quick Memo slots (1..5). Slot 1 keeps [KEY_QUICK_MEMO_TEXT]. */
    const val QUICK_MEMO_SLOT_COUNT = 5
    const val KEY_QUICK_MEMO_PINNED_SLOT = "pref_quick_memo_pinned_slot"
    const val KEY_QUICK_MEMO_LAST_OPENED_SLOT = "pref_quick_memo_last_opened_slot"
    /** Pinned Quick Snippets for the `#`-hold Clips & Snippets wheel (JSON string array). */
    const val KEY_QUICK_SNIPPETS = "pref_quick_snippets"
    const val QUICK_SNIPPET_MAX_COUNT = 6
    const val QUICK_SNIPPET_MAX_CHARS = 240
    // Daily step counter bookkeeping (see [DailyStepState])
    const val KEY_STEP_DAY = "pref_step_day"
    const val KEY_STEP_BASELINE = "pref_step_baseline"
    const val KEY_STEP_CARRIED = "pref_step_carried"
    const val KEY_STEP_LAST_COUNTER = "pref_step_last_counter"
    const val KEY_HIDDEN_APPS = "hidden_apps"
    const val KEY_FAVORITE_ITEMS_ORDERED = "favorite_items_ordered_v1"
    const val KEY_CUSTOM_ICON_PREFIX = "custom_icon_"
    const val KEY_CUSTOM_FOLDER_ICON_PREFIX = "custom_folder_icon_"

    val isWallpaperOverlayEnabled: StateFlow<Boolean>
        get() = AppearancePrefs.isWallpaperOverlayEnabled

    val isSystemMonitorEnabled: StateFlow<Boolean>
        get() = AppearancePrefs.isSystemMonitorEnabled

    val isFolderScatterLayout: StateFlow<Boolean>
        get() = AppearancePrefs.isFolderScatterLayout

    val isDpadWrapAround: StateFlow<Boolean>
        get() = HardwareInputPrefs.isDpadWrapAround

    val isDpadBoundaryHaptics: StateFlow<Boolean>
        get() = HardwareInputPrefs.isDpadBoundaryHaptics

    val lidOpenAction: StateFlow<LidOpenAction>
        get() = HardwareInputPrefs.lidOpenAction

    val isLidCloseSnapSoundEnabled: StateFlow<Boolean>
        get() = SoundPrefs.isLidCloseSnapSoundEnabled

    val isSoftKeyNavEnabled: StateFlow<Boolean>
        get() = HardwareInputPrefs.isSoftKeyNavEnabled

    val keypadSoundMode: StateFlow<String>
        get() = SoundPrefs.keypadSoundMode

    val launcherAudioStream: StateFlow<String>
        get() = SoundPrefs.launcherAudioStream

    val launcherSoundVolume: StateFlow<Int>
        get() = SoundPrefs.launcherSoundVolume

    val isLidLedEnabled: StateFlow<Boolean>
        get() = LidLedPrefs.isLidLedEnabled

    val isLidLedChargingEnabled: StateFlow<Boolean>
        get() = LidLedPrefs.isLidLedChargingEnabled

    val appDrawerSortMode: StateFlow<AppDrawerSortMode>
        get() = AppearancePrefs.appDrawerSortMode

    val lidLedColor: StateFlow<String>
        get() = LidLedPrefs.lidLedColor

    val isMediaVolumeLockEnabled: StateFlow<Boolean>
        get() = SoundPrefs.isMediaVolumeLockEnabled

    val systemMonitorInterval: StateFlow<Int>
        get() = AppearancePrefs.systemMonitorInterval

    val customUserName: StateFlow<String>
        get() = AppearancePrefs.customUserName

    val themePreset: StateFlow<String>
        get() = AppearancePrefs.themePreset

    val themeFontFamily: StateFlow<String>
        get() = AppearancePrefs.themeFontFamily

    val themeBorderStyle: StateFlow<String>
        get() = AppearancePrefs.themeBorderStyle

    val isRootQuickSettingsEnabled: StateFlow<Boolean>
        get() = SystemAccessPrefs.isRootQuickSettingsEnabled

    val photoFrameUri: StateFlow<String?>
        get() = AppearancePrefs.photoFrameUri

    val photoFrameStyle: StateFlow<String>
        get() = AppearancePrefs.photoFrameStyle

    val photoFrameLabel: StateFlow<String>
        get() = AppearancePrefs.photoFrameLabel

    val isPhotoFrameEnabled: StateFlow<Boolean>
        get() = AppearancePrefs.isPhotoFrameEnabled

    /** Retro keitai "outer sub-display" status marquee on Home. */
    val isSubDisplayTickerEnabled: StateFlow<Boolean>
        get() = AppearancePrefs.isSubDisplayTickerEnabled

    /** true = dark sub-OLED look, false = pastel theme-matched look. */
    val isSubDisplayTickerDark: StateFlow<Boolean>
        get() = AppearancePrefs.isSubDisplayTickerDark

    /** Preset size of the mascot sitting on the Home favorites card. */
    val homeMascotSize: StateFlow<HomeMascotSize>
        get() = AppearancePrefs.homeMascotSize

    /** Quick Memo text (`#` hold on Home); shown in the sub-display ticker when non-blank. */
    val quickMemoText: StateFlow<String>
        get() = QuickMemoPrefs.quickMemoText

    /** All Quick Memo slots (index 0 = slot 1). */
    val quickMemoSlots: StateFlow<List<String>>
        get() = QuickMemoPrefs.slots

    /** Slot (1-based) pinned to the Home sub-display ticker. */
    val pinnedQuickMemoSlot: StateFlow<Int>
        get() = QuickMemoPrefs.pinnedSlot

    /** Slot (1-based) the Quick Memo dialog reopens on. */
    val lastOpenedQuickMemoSlot: StateFlow<Int>
        get() = QuickMemoPrefs.lastOpenedSlot

    /** Pinned Quick Snippets (Clips & Snippets wheel), at most [QUICK_SNIPPET_MAX_COUNT]. */
    val quickSnippets: StateFlow<List<String>>
        get() = QuickSnippetPrefs.snippets

    /** Saves the snippet list (blank / duplicate entries dropped, capped in count and length). */
    fun setQuickSnippets(context: Context, snippets: List<String>) =
        QuickSnippetPrefs.setSnippets(context, snippets)

    /** Per-app outer lid LED rules keyed by package name. */
    val lidAppLedRules: StateFlow<Map<String, LidAppLedRule>>
        get() = LidLedPrefs.appLedRules

    /** Whether root / LSPosed rows are shown in settings. Loaded in [init]. */
    val showRootOptions: StateFlow<Boolean>
        get() = SystemAccessPrefs.showRootOptions

    /** True once [init] has loaded every StateFlow; getters then serve the cached values. */
    internal var initialized = false
        private set

    fun init(context: Context) {
        if (!initialized) {
            reload(context)
            initialized = true
        }
        // Outside the guard so restore / reset-to-defaults (which call init again) refresh it
        SystemAccessPrefs.refreshShowRootOptions(context)
    }

    /**
     * Re-reads every cached StateFlow from SharedPreferences. Call after anything that rewrites
     * prefs behind the setters (backup restore, factory reset) so observers update immediately.
     */
    fun reload(context: Context) {
        val prefs = getPrefs(context)
        SystemAccessPrefs.reload(prefs)
        AppearancePrefs.reload(prefs)
        HardwareInputPrefs.reload(prefs)
        SoundPrefs.reload(prefs)
        LidLedPrefs.reload(prefs)
        QuickMemoPrefs.reload(prefs)
        QuickSnippetPrefs.reload(prefs)
        SystemAccessPrefs.refreshShowRootOptions(context)
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** `#` is the fixed, always-on Kaomoji Wheel trigger (hold). */
    fun matchesKaomojiTrigger(eventKeyCode: Int): Boolean =
        HardwareInputPrefs.matchesKaomojiTrigger(eventKeyCode)

    fun formatKeyName(keyCode: Int, scanCode: Int = 0): String =
        HardwareInputPrefs.formatKeyName(keyCode, scanCode)

    fun formatKeyBadge(keyCode: Int, scanCode: Int = 0): String =
        HardwareInputPrefs.formatKeyBadge(keyCode, scanCode)

    fun getKaomojiTriggerBadge(): String =
        HardwareInputPrefs.getKaomojiTriggerBadge()

    fun isWallpaperOverlayEnabled(context: Context): Boolean =
        AppearancePrefs.isWallpaperOverlayEnabled(context)

    fun setWallpaperOverlayEnabled(context: Context, enabled: Boolean) =
        AppearancePrefs.setWallpaperOverlayEnabled(context, enabled)

    fun isSystemMonitorEnabled(context: Context): Boolean =
        AppearancePrefs.isSystemMonitorEnabled(context)

    fun setSystemMonitorEnabled(context: Context, enabled: Boolean) =
        AppearancePrefs.setSystemMonitorEnabled(context, enabled)

    fun setPhotoFrameUri(context: Context, uri: String?) =
        AppearancePrefs.setPhotoFrameUri(context, uri)

    fun setPhotoFrameStyle(context: Context, style: String) =
        AppearancePrefs.setPhotoFrameStyle(context, style)

    fun setPhotoFrameLabel(context: Context, label: String) =
        AppearancePrefs.setPhotoFrameLabel(context, label)

    fun setPhotoFrameEnabled(context: Context, enabled: Boolean) =
        AppearancePrefs.setPhotoFrameEnabled(context, enabled)

    fun isSubDisplayTickerEnabled(context: Context): Boolean =
        AppearancePrefs.isSubDisplayTickerEnabled(context)

    fun setSubDisplayTickerEnabled(context: Context, enabled: Boolean) =
        AppearancePrefs.setSubDisplayTickerEnabled(context, enabled)

    fun isSubDisplayTickerDark(context: Context): Boolean =
        AppearancePrefs.isSubDisplayTickerDark(context)

    fun setSubDisplayTickerDark(context: Context, dark: Boolean) =
        AppearancePrefs.setSubDisplayTickerDark(context, dark)

    fun getHomeMascotSize(context: Context): HomeMascotSize =
        AppearancePrefs.getHomeMascotSize(context)

    fun setHomeMascotSize(context: Context, size: HomeMascotSize) =
        AppearancePrefs.setHomeMascotSize(context, size)

    /** Saves the memo text (trimmed of trailing blank lines, capped at [QUICK_MEMO_MAX_CHARS]). */
    fun setQuickMemoText(context: Context, text: String) =
        QuickMemoPrefs.setQuickMemoText(context, text)

    /** Clear button: empties the memo (which also removes it from the ticker). */
    fun clearQuickMemo(context: Context) =
        QuickMemoPrefs.clearQuickMemo(context)

    fun getQuickMemoSlotText(slot: Int): String =
        QuickMemoPrefs.slots.value.getOrElse(QuickMemoPrefs.clampSlot(slot) - 1) { "" }

    /** Saves one memo slot (1..[QUICK_MEMO_SLOT_COUNT]), trimmed and capped like the single memo. */
    fun setQuickMemoSlotText(context: Context, slot: Int, text: String) =
        QuickMemoPrefs.setSlotText(context, slot, text)

    fun setPinnedQuickMemoSlot(context: Context, slot: Int) =
        QuickMemoPrefs.setPinnedSlot(context, slot)

    fun setLastOpenedQuickMemoSlot(context: Context, slot: Int) =
        QuickMemoPrefs.setLastOpenedSlot(context, slot)

    /** Memo shown in the ticker: pinned slot, else first non-blank slot, else null. */
    fun tickerQuickMemo(slots: List<String>, pinnedSlot: Int): String? =
        QuickMemoPrefs.tickerMemo(slots, pinnedSlot)

    fun getDailyStepState(context: Context): DailyStepState? =
        HomeContentPrefs.getDailyStepState(context)

    fun setDailyStepState(context: Context, state: DailyStepState) =
        HomeContentPrefs.setDailyStepState(context, state)

    fun isFolderScatterLayout(context: Context): Boolean =
        AppearancePrefs.isFolderScatterLayout(context)

    fun setFolderScatterLayout(context: Context, enabled: Boolean) =
        AppearancePrefs.setFolderScatterLayout(context, enabled)

    fun getSystemMonitorInterval(context: Context): Int =
        AppearancePrefs.getSystemMonitorInterval(context)

    fun setSystemMonitorInterval(context: Context, seconds: Int) =
        AppearancePrefs.setSystemMonitorInterval(context, seconds)

    fun getCustomUserName(context: Context): String =
        AppearancePrefs.getCustomUserName(context)

    fun setCustomUserName(context: Context, name: String) =
        AppearancePrefs.setCustomUserName(context, name)

    fun getThemePreset(context: Context): String =
        AppearancePrefs.getThemePreset(context)

    fun setThemePreset(context: Context, preset: String) =
        AppearancePrefs.setThemePreset(context, preset)

    fun isDpadWrapAround(context: Context): Boolean =
        HardwareInputPrefs.isDpadWrapAround(context)

    fun setDpadWrapAround(context: Context, enabled: Boolean) =
        HardwareInputPrefs.setDpadWrapAround(context, enabled)

    fun isDpadBoundaryHaptics(context: Context): Boolean =
        HardwareInputPrefs.isDpadBoundaryHaptics(context)

    fun getLidOpenAction(context: Context): LidOpenAction =
        HardwareInputPrefs.getLidOpenAction(context)

    fun setLidOpenAction(context: Context, action: LidOpenAction) =
        HardwareInputPrefs.setLidOpenAction(context, action)

    fun getLastLaunchedPackage(context: Context): String? =
        HardwareInputPrefs.getLastLaunchedPackage(context)

    fun setLastLaunchedPackage(context: Context, packageName: String?) =
        HardwareInputPrefs.setLastLaunchedPackage(context, packageName)

    fun isLidCloseSnapSoundEnabled(context: Context): Boolean =
        SoundPrefs.isLidCloseSnapSoundEnabled(context)

    fun setLidCloseSnapSoundEnabled(context: Context, enabled: Boolean) =
        SoundPrefs.setLidCloseSnapSoundEnabled(context, enabled)

    fun setDpadBoundaryHaptics(context: Context, enabled: Boolean) =
        HardwareInputPrefs.setDpadBoundaryHaptics(context, enabled)

    fun getKeypadSoundMode(context: Context? = null): String =
        SoundPrefs.getKeypadSoundMode(context)

    fun setKeypadSoundMode(context: Context, mode: String) =
        SoundPrefs.setKeypadSoundMode(context, mode)

    fun getLauncherAudioStream(context: Context? = null): String =
        SoundPrefs.getLauncherAudioStream(context)

    fun setLauncherAudioStream(context: Context, stream: String) =
        SoundPrefs.setLauncherAudioStream(context, stream)

    fun getLauncherSoundVolume(context: Context? = null): Int =
        SoundPrefs.getLauncherSoundVolume(context)

    fun setLauncherSoundVolume(context: Context, volume: Int) =
        SoundPrefs.setLauncherSoundVolume(context, volume)

    fun isLidLedEnabled(context: Context): Boolean =
        LidLedPrefs.isLidLedEnabled(context)

    fun setLidLedEnabled(context: Context, enabled: Boolean) =
        LidLedPrefs.setLidLedEnabled(context, enabled)

    fun isLidLedChargingEnabled(context: Context): Boolean =
        LidLedPrefs.isLidLedChargingEnabled(context)

    fun setLidLedChargingEnabled(context: Context, enabled: Boolean) =
        LidLedPrefs.setLidLedChargingEnabled(context, enabled)

    fun getLidLedColor(context: Context): String =
        LidLedPrefs.getLidLedColor(context)

    fun getAppDrawerSortMode(context: Context? = null): AppDrawerSortMode =
        AppearancePrefs.getAppDrawerSortMode(context)

    fun setAppDrawerSortMode(context: Context, mode: AppDrawerSortMode) =
        AppearancePrefs.setAppDrawerSortMode(context, mode)

    fun setLidLedColor(context: Context, color: String) =
        LidLedPrefs.setLidLedColor(context, color)

    fun getLidLedNotificationAnimationId(context: Context): String =
        LidLedPrefs.getLidLedNotificationAnimationId(context)

    fun setLidLedNotificationAnimationId(context: Context, id: String) =
        LidLedPrefs.setLidLedNotificationAnimationId(context, id)

    fun getLidLedChargingAnimationId(context: Context): String =
        LidLedPrefs.getLidLedChargingAnimationId(context)

    fun setLidLedChargingAnimationId(context: Context, id: String) =
        LidLedPrefs.setLidLedChargingAnimationId(context, id)

    fun getLidAppLedRules(): Map<String, LidAppLedRule> = LidLedPrefs.appLedRules.value

    /** Saves [rule]; a [AppLedRuleMode.DEFAULT] rule removes the package's entry. */
    fun setLidAppLedRule(context: Context, rule: LidAppLedRule) =
        LidLedPrefs.setAppLedRule(context, rule)

    /** Replaces every per-app rule (backup restore). */
    fun setLidAppLedRules(context: Context, rules: Collection<LidAppLedRule>) =
        LidLedPrefs.setAppLedRules(context, rules)

    fun isMediaVolumeLockEnabled(context: Context): Boolean =
        SoundPrefs.isMediaVolumeLockEnabled(context)

    fun setMediaVolumeLockEnabled(context: Context, enabled: Boolean) =
        SoundPrefs.setMediaVolumeLockEnabled(context, enabled)

    fun getThemeFontFamily(context: Context? = null): String =
        AppearancePrefs.getThemeFontFamily(context)

    fun setThemeFontFamily(context: Context, family: String) =
        AppearancePrefs.setThemeFontFamily(context, family)

    fun getThemeBorderStyle(context: Context? = null): String =
        AppearancePrefs.getThemeBorderStyle(context)

    fun setThemeBorderStyle(context: Context, borderStyle: String) =
        AppearancePrefs.setThemeBorderStyle(context, borderStyle)

    fun getWallpaperUri(context: Context): String? =
        AppearancePrefs.getWallpaperUri(context)

    fun setWallpaperUri(context: Context, uri: String?) =
        AppearancePrefs.setWallpaperUri(context, uri)

    fun getHiddenPackages(context: Context): Set<String> =
        HomeContentPrefs.getHiddenPackages(context)

    fun setHiddenPackages(context: Context, packages: Set<String>) =
        HomeContentPrefs.setHiddenPackages(context, packages)

    fun addHiddenPackage(context: Context, packageName: String) =
        HomeContentPrefs.addHiddenPackage(context, packageName)

    fun removeHiddenPackage(context: Context, packageName: String) =
        HomeContentPrefs.removeHiddenPackage(context, packageName)

    fun setCustomAppIcon(context: Context, packageName: String, iconName: String?) =
        HomeContentPrefs.setCustomAppIcon(context, packageName, iconName)

    fun setCustomFolderIcon(context: Context, folderId: String, iconName: String?) =
        HomeContentPrefs.setCustomFolderIcon(context, folderId, iconName)

    fun saveFavoriteFolders(context: Context, folders: List<FavoriteFolder>) =
        HomeContentPrefs.saveFavoriteFolders(context, folders)

    fun loadFavoriteFolders(context: Context): List<FavoriteFolder> =
        HomeContentPrefs.loadFavoriteFolders(context)

    fun getFavoriteItemKeys(context: Context): List<String> =
        HomeContentPrefs.getFavoriteItemKeys(context)

    fun saveFavoriteItemKeys(context: Context, keys: List<String>) =
        HomeContentPrefs.saveFavoriteItemKeys(context, keys)

    fun getSpeedDialPackage(context: Context, digit: Int): String? =
        HardwareInputPrefs.getSpeedDialPackage(context, digit)

    fun setSpeedDialPackage(context: Context, digit: Int, packageName: String?) =
        HardwareInputPrefs.setSpeedDialPackage(context, digit, packageName)

    fun getAllSpeedDials(context: Context): Map<Int, String> =
        HardwareInputPrefs.getAllSpeedDials(context)

    fun isRootQuickSettingsEnabled(context: Context): Boolean =
        SystemAccessPrefs.isRootQuickSettingsEnabled(context)

    fun setRootQuickSettingsEnabled(context: Context, enabled: Boolean) =
        SystemAccessPrefs.setRootQuickSettingsEnabled(context, enabled)

    /** Root options start visible only when a su binary is present. */
    fun defaultShowRootOptions(suPresent: Boolean): Boolean =
        SystemAccessPrefs.defaultShowRootOptions(suPresent)

    fun isShowRootOptions(
        context: Context,
        suPresent: Boolean = one.crinklecod.mochibun.core.system.RootShell.isSuBinaryPresent()
    ): Boolean =
        SystemAccessPrefs.isShowRootOptions(context, suPresent)

    fun setShowRootOptions(context: Context, show: Boolean) =
        SystemAccessPrefs.setShowRootOptions(context, show)

    /**
     * Root's hall-switch reader or the LSPosed key hook already resets the UI on a real flip
     * close, so the screen-off reset defaults on only when neither is around.
     */
    fun defaultNonRootScreenOffReset(lidDetectionActive: Boolean): Boolean =
        SystemAccessPrefs.defaultNonRootScreenOffReset(lidDetectionActive)

    /** True when root (hall-switch reader) or the LSPosed key hook can see the flip close. */
    fun isLidDetectionActive(context: Context): Boolean =
        SystemAccessPrefs.isLidDetectionActive(context)

    /** [lidDetectionActive] overrides the device probe (tests); it is only consulted while unset. */
    fun isNonRootScreenOffResetEnabled(context: Context, lidDetectionActive: Boolean? = null): Boolean =
        SystemAccessPrefs.isNonRootScreenOffResetEnabled(context, lidDetectionActive)

    fun setNonRootScreenOffResetEnabled(context: Context, enabled: Boolean) =
        SystemAccessPrefs.setNonRootScreenOffResetEnabled(context, enabled)

    fun isKeyHookEnabled(context: Context): Boolean =
        SystemAccessPrefs.isKeyHookEnabled(context)

    fun setKeyHookEnabled(context: Context, enabled: Boolean) =
        SystemAccessPrefs.setKeyHookEnabled(context, enabled)

    fun isSystemUiHookEnabled(context: Context): Boolean =
        SystemAccessPrefs.isSystemUiHookEnabled(context)

    fun setSystemUiHookEnabled(context: Context, enabled: Boolean) =
        SystemAccessPrefs.setSystemUiHookEnabled(context, enabled)

    fun isCursorWatchdogEnabled(context: Context): Boolean =
        SystemAccessPrefs.isCursorWatchdogEnabled(context)

    /** How long a keypad key must be held in MochibunImeService to type its digit. */
    fun getImeHoldDelayMs(context: Context): Int =
        HardwareInputPrefs.getImeHoldDelayMs(context)

    fun setImeHoldDelayMs(context: Context, delayMs: Int) =
        HardwareInputPrefs.setImeHoldDelayMs(context, delayMs)

    /** Idle time after which MochibunImeService commits a pending multi-tap letter. */
    fun getImeIdleCommitMs(context: Context): Int =
        HardwareInputPrefs.getImeIdleCommitMs(context)

    /** Next auto-commit delay one [STEP_IME_IDLE_COMMIT_MS] notch in [direction] (-1 / +1), clamped. */
    fun stepImeIdleCommitMs(currentMs: Int, direction: Int): Int =
        HardwareInputPrefs.stepImeIdleCommitMs(currentMs, direction)

    fun setImeIdleCommitMs(context: Context, delayMs: Int) =
        HardwareInputPrefs.setImeIdleCommitMs(context, delayMs)

    /** Short vibration when a held key in MochibunImeService types its digit. */
    fun isImeHoldVibrationEnabled(context: Context): Boolean =
        HardwareInputPrefs.isImeHoldVibrationEnabled(context)

    fun isImeHoldForDigitEnabled(context: Context): Boolean =
        HardwareInputPrefs.isImeHoldForDigitEnabled(context)

    fun setImeHoldForDigitEnabled(context: Context, enabled: Boolean) =
        HardwareInputPrefs.setImeHoldForDigitEnabled(context, enabled)

    fun isImeAutoCommitEnabled(context: Context): Boolean =
        HardwareInputPrefs.isImeAutoCommitEnabled(context)

    fun setImeAutoCommitEnabled(context: Context, enabled: Boolean) =
        HardwareInputPrefs.setImeAutoCommitEnabled(context, enabled)

    fun setImeHoldVibrationEnabled(context: Context, enabled: Boolean) =
        HardwareInputPrefs.setImeHoldVibrationEnabled(context, enabled)

    fun isSoftKeyNavEnabled(context: Context): Boolean =
        HardwareInputPrefs.isSoftKeyNavEnabled(context)

    fun setSoftKeyNavEnabled(context: Context, enabled: Boolean) =
        HardwareInputPrefs.setSoftKeyNavEnabled(context, enabled)

    /**
     * Mirrors the Soft-Key Navigation pref into Settings.Global for MochibunKeyHook. Writes only
     * when the stored global differs. Tries a direct write first (works once WRITE_SECURE_SETTINGS
     * is granted), then falls back to root. Without either, the in-app handler still honours the pref.
     */
    fun syncSoftKeyNavToGlobal(context: Context) =
        HardwareInputPrefs.syncSoftKeyNavToGlobal(context)

    /**
     * Writes [value] to Settings.Global [name] for MochibunKeyHook, only when it differs from the
     * stored value ([defaultValue] when unset). Direct write first (needs WRITE_SECURE_SETTINGS),
     * then root. Without either the hook keeps reading [defaultValue].
     */
    internal fun mirrorIntToGlobal(context: Context, name: String, value: Int, defaultValue: Int) =
        HardwareInputPrefs.mirrorIntToGlobal(context, name, value, defaultValue)
}
