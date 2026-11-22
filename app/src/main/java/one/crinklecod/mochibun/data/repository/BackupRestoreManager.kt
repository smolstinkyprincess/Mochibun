package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.net.Uri
import one.crinklecod.mochibun.BuildConfig
import one.crinklecod.mochibun.data.ime.LearnedWordExtractor
import one.crinklecod.mochibun.data.ime.UserWordStats
import one.crinklecod.mochibun.data.ime.UserWordStore
import one.crinklecod.mochibun.data.model.ActionType
import one.crinklecod.mochibun.data.model.AppDrawerSortMode
import one.crinklecod.mochibun.data.model.AppLedRuleMode
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.model.LidAppLedRule
import one.crinklecod.mochibun.data.model.LidAppLedRuleCodec
import one.crinklecod.mochibun.data.model.CONTROLLED_STATIC_KEYCODES
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.model.PhysicalKey
import one.crinklecod.mochibun.data.prefs.ClickMethod
import one.crinklecod.mochibun.data.prefs.CursorPreferences
import one.crinklecod.mochibun.data.prefs.HomeMascotSize
import one.crinklecod.mochibun.data.prefs.HotspotAlignment
import one.crinklecod.mochibun.data.prefs.LidOpenAction
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.pet.PetBackupCodec
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.ui.components.PhotoFrameStyle
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Summary details of a restored backup, used for user confirmations and audits.
 */
data class BackupSummary(
    val timestamp: Long,
    val appVersion: String,
    val themePreset: String?,
    val folderCount: Int,
    val favoriteCount: Int,
    val hiddenAppCount: Int,
    val remapCount: Int
)

data class BackupPreviewSummary(
    val timestamp: Long,
    val appVersion: String,
    val favoriteCount: Int,
    val folderCount: Int,
    val remapCount: Int,
    val themeName: String?
)

/**
 * Centralized backup and restore manager for Mochibun.
 * Exports and restores themes, layout, favorites, folders, hardware button remaps,
 * cursor physics, and launcher behavior in an open, portable JSON format.
 */
object BackupRestoreManager {

    const val CURRENT_BACKUP_VERSION = 1
    const val KEY_QUICK_MEMO = "quick_memo"
    const val KEY_LID_LED_APP_RULES = "lid_led_app_rules"
    const val KEY_QUICK_SNIPPETS = "quick_snippets"
    private const val MAX_FAVORITES = 200
    private const val MAX_FOLDERS = 100
    private const val MAX_APPS_PER_FOLDER = 200
    private const val MAX_ICON_ENTRIES = 200
    private const val MAX_REMAP_ENTRIES = 32
    private const val MAX_T9_LEARNED_DICTS = 32
    private const val MAX_T9_USER_WORD_DICTS = 8
    private const val MAX_T9_LEARNED_WORDS_JSON_CHARS = 256 * 1024
    private const val MAX_BACKUP_SIZE_BYTES = 2 * 1024 * 1024

    private data class ValidatedBackup(
        val root: JSONObject,
        val preview: BackupPreviewSummary
    )

    fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "mochibun_backup_${dateFormat.format(Date())}.json"
    }

    /**
     * Serializes all Mochibun configuration to a structured JSON string.
     */
    fun createBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("version", CURRENT_BACKUP_VERSION)
        root.put("timestamp", System.currentTimeMillis())
        root.put("app_version", BuildConfig.VERSION_NAME)

        // ── 1. Theme & Look ──────────────────────────────────────────
        val launcherPrefs = PreferencesManager.getPrefs(context)
        val themeObj = JSONObject()
        themeObj.put("preset", PreferencesManager.getThemePreset(context))
        themeObj.put("custom_user_name", PreferencesManager.getCustomUserName(context))
        themeObj.put("wallpaper_overlay_enabled", PreferencesManager.isWallpaperOverlayEnabled(context))
        themeObj.put("home_mascot_size", PreferencesManager.getHomeMascotSize(context).id)
        themeObj.put("theme_font_family", PreferencesManager.getThemeFontFamily(context))
        themeObj.put("theme_border_style", PreferencesManager.getThemeBorderStyle(context))
        themeObj.put("sub_display_ticker_enabled", PreferencesManager.isSubDisplayTickerEnabled(context))
        themeObj.put("sub_display_ticker_dark", PreferencesManager.isSubDisplayTickerDark(context))
        themeObj.put(
            "photo_frame_enabled",
            launcherPrefs.getBoolean(
                PreferencesManager.KEY_PHOTO_FRAME_ENABLED,
                PreferencesManager.DEFAULT_PHOTO_FRAME_ENABLED
            )
        )
        themeObj.put(
            "photo_frame_style",
            launcherPrefs.getString(
                PreferencesManager.KEY_PHOTO_FRAME_STYLE,
                PreferencesManager.DEFAULT_PHOTO_FRAME_STYLE
            ) ?: PreferencesManager.DEFAULT_PHOTO_FRAME_STYLE
        )
        themeObj.put(
            "photo_frame_label",
            launcherPrefs.getString(
                PreferencesManager.KEY_PHOTO_FRAME_LABEL,
                PreferencesManager.DEFAULT_PHOTO_FRAME_LABEL
            ) ?: PreferencesManager.DEFAULT_PHOTO_FRAME_LABEL
        )

        val colorsObj = JSONObject()
        PreferencesManager.THEME_COLOR_KEYS.forEach { key ->
            if (launcherPrefs.contains(key)) {
                colorsObj.put(key, launcherPrefs.getInt(key, 0))
            }
        }
        if (launcherPrefs.contains(PreferencesManager.KEY_THEME_CARD_ROUNDNESS)) {
            colorsObj.put(
                PreferencesManager.KEY_THEME_CARD_ROUNDNESS,
                launcherPrefs.getFloat(PreferencesManager.KEY_THEME_CARD_ROUNDNESS, 24f).toDouble()
            )
        }
        themeObj.put("colors", colorsObj)
        root.put("theme", themeObj)

        // ── 2. Layout, Favorites & Folders ───────────────────────────
        val layoutObj = JSONObject()
        val favItemKeys = PreferencesManager.getFavoriteItemKeys(context)
        layoutObj.put("favorite_items_ordered", JSONArray(favItemKeys))

        val folders = PreferencesManager.loadFavoriteFolders(context)
        val foldersArray = JSONArray()
        folders.forEach { folder ->
            foldersArray.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("apps", JSONArray(folder.appPackages))
            )
        }
        layoutObj.put("folders", foldersArray)

        // Custom App Icons & Folder Icons
        val appIconsObj = JSONObject()
        val folderIconsObj = JSONObject()
        for ((key, value) in launcherPrefs.all) {
            if (key.startsWith(PreferencesManager.KEY_CUSTOM_ICON_PREFIX) && value is String) {
                val pkg = key.removePrefix(PreferencesManager.KEY_CUSTOM_ICON_PREFIX)
                appIconsObj.put(pkg, value)
            } else if (key.startsWith(PreferencesManager.KEY_CUSTOM_FOLDER_ICON_PREFIX) && value is String) {
                val folderId = key.removePrefix(PreferencesManager.KEY_CUSTOM_FOLDER_ICON_PREFIX)
                folderIconsObj.put(folderId, value)
            }
        }
        layoutObj.put("custom_app_icons", appIconsObj)
        layoutObj.put("custom_folder_icons", folderIconsObj)

        layoutObj.put("hidden_apps", JSONArray(PreferencesManager.getHiddenPackages(context)))
        layoutObj.put("folder_scatter_layout", PreferencesManager.isFolderScatterLayout(context))
        layoutObj.put("app_drawer_sort_mode", PreferencesManager.getAppDrawerSortMode(context).name)

        val speedDials = PreferencesManager.getAllSpeedDials(context)
        val speedDialsObj = JSONObject()
        speedDials.forEach { (digit, pkg) ->
            speedDialsObj.put(digit.toString(), pkg)
        }
        layoutObj.put("speed_dials", speedDialsObj)

        val selectedIconPack = launcherPrefs.getString("selected_icon_pack", "")
        if (!selectedIconPack.isNullOrBlank()) {
            layoutObj.put("selected_icon_pack", selectedIconPack)
        }
        root.put("layout", layoutObj)

        // ── 3. Hardware Key Remaps ───────────────────────────────────
        val remapsObj = JSONObject()
        val buttonActionsObj = JSONObject()
        for (key in PhysicalKey.entries) {
            val config = HardwareRemapManager.getButtonActionConfig(context, key)
            if (config.singlePressAction != ActionType.None ||
                config.doublePressAction != ActionType.None ||
                config.longPressAction != ActionType.None
            ) {
                buttonActionsObj.put(
                    key.scancode.toString(),
                    JSONObject()
                        .put("single", config.singlePressAction.serialize())
                        .put("double", config.doublePressAction.serialize())
                        .put("long", config.longPressAction.serialize())
                )
            }
        }
        remapsObj.put("button_actions", buttonActionsObj)

        val staticMappings = HardwareRemapManager.getConfiguredStaticMappings(context)
        val staticMappingsObj = JSONObject()
        staticMappings.forEach { (scancode, target) ->
            staticMappingsObj.put(scancode.toString(), target)
        }
        remapsObj.put("static_mappings", staticMappingsObj)
        remapsObj.put("use_root_recents_fallback", HardwareRemapManager.isRootRecentsFallbackEnabled(context))
        root.put("hardware_remaps", remapsObj)

        // ── 4. Launcher Behavior ─────────────────────────────────────
        val behaviorObj = JSONObject()
        behaviorObj.put("dpad_wrap_around", PreferencesManager.isDpadWrapAround(context))
        behaviorObj.put("dpad_boundary_haptics", PreferencesManager.isDpadBoundaryHaptics(context))
        behaviorObj.put("launcher_audio_stream", PreferencesManager.getLauncherAudioStream(context))
        behaviorObj.put("launcher_sound_volume", PreferencesManager.getLauncherSoundVolume(context))
        behaviorObj.put("keypad_sound_mode", PreferencesManager.getKeypadSoundMode(context))
        behaviorObj.put("media_volume_lock_enabled", PreferencesManager.isMediaVolumeLockEnabled(context))
        behaviorObj.put("lid_close_snap_sound_enabled", PreferencesManager.isLidCloseSnapSoundEnabled(context))
        behaviorObj.put("soft_key_nav_enabled", PreferencesManager.isSoftKeyNavEnabled(context))
        behaviorObj.put("system_monitor_enabled", PreferencesManager.isSystemMonitorEnabled(context))
        behaviorObj.put("system_monitor_interval_sec", PreferencesManager.getSystemMonitorInterval(context))
        behaviorObj.put("ime_hold_delay_ms", PreferencesManager.getImeHoldDelayMs(context))
        behaviorObj.put("ime_hold_for_digit_enabled", PreferencesManager.isImeHoldForDigitEnabled(context))
        behaviorObj.put("ime_hold_vibration", PreferencesManager.isImeHoldVibrationEnabled(context))
        behaviorObj.put("ime_idle_commit_ms", PreferencesManager.getImeIdleCommitMs(context))
        behaviorObj.put("ime_auto_commit_enabled", PreferencesManager.isImeAutoCommitEnabled(context))
        behaviorObj.put("ime_predictive_t9_enabled", PreferencesManager.isImePredictiveT9Enabled(context))
        behaviorObj.put("ime_t9_dictionary_lang", PreferencesManager.getImeT9DictionaryLang(context))
        behaviorObj.put("ime_t9_learned_words", PreferencesManager.getImeT9LearnedWordsJson(context))
        behaviorObj.put("ime_t9_learn_new_words", PreferencesManager.isImeT9LearnNewWordsEnabled(context))
        val userWordsJson = PreferencesManager.getImeT9UserWordsJson(context)
        val userWordsObj = if (userWordsJson.isNotBlank()) {
            runCatching { JSONObject(userWordsJson) }.getOrElse { JSONObject() }
        } else {
            JSONObject()
        }
        behaviorObj.put("ime_t9_user_words", userWordsObj)
        behaviorObj.put("show_root_options", PreferencesManager.isShowRootOptions(context))
        behaviorObj.put("non_root_screen_off_reset", PreferencesManager.isNonRootScreenOffResetEnabled(context))
        behaviorObj.put("lid_open_action", PreferencesManager.getLidOpenAction(context).id)
        behaviorObj.put("lid_led_enabled", PreferencesManager.isLidLedEnabled(context))
        behaviorObj.put("lid_led_charging", PreferencesManager.isLidLedChargingEnabled(context))
        behaviorObj.put("lid_led_color", PreferencesManager.getLidLedColor(context))
        behaviorObj.put("lid_led_notification_animation_id", PreferencesManager.getLidLedNotificationAnimationId(context))
        behaviorObj.put("lid_led_charging_animation_id", PreferencesManager.getLidLedChargingAnimationId(context))
        root.put("behavior", behaviorObj)

        // ── 5. Virtual Cursor Physics & Pointer ──────────────────────
        val cursorObj = JSONObject()
        cursorObj.put("toggle_keycode", CursorPreferences.getToggleKeyCode(context))
        cursorObj.put("move_speed", CursorPreferences.getMoveSpeed(context).toDouble())
        cursorObj.put("acceleration", CursorPreferences.getAcceleration(context).toDouble())
        cursorObj.put("tap_step_px", CursorPreferences.getTapStepPx(context))
        cursorObj.put("max_speed_px_per_sec", CursorPreferences.getMaxSpeed(context))
        cursorObj.put("accel_ramp_ms", CursorPreferences.getAccelRampMs(context))
        cursorObj.put("scroll_step_px", CursorPreferences.getScrollStepPx(context))
        cursorObj.put("click_method", CursorPreferences.getClickMethod(context).name)
        cursorObj.put("hotspot_alignment", CursorPreferences.getHotspotAlignment(context).name)
        cursorObj.put("cursor_size_dp", CursorPreferences.getCursorSizeDp(context))
        cursorObj.put("gif_playback_speed", CursorPreferences.getGifPlaybackSpeed(context).toDouble())
        root.put("cursor", cursorObj)

        // ── 6. Quick Memo slots (optional on restore: older backups have none) ──
        val memoObj = JSONObject()
        val slotsArray = JSONArray()
        PreferencesManager.quickMemoSlots.value.forEach { slotsArray.put(it) }
        memoObj.put("slots", slotsArray)
        memoObj.put("pinned_slot", PreferencesManager.pinnedQuickMemoSlot.value)
        memoObj.put("last_opened_slot", PreferencesManager.lastOpenedQuickMemoSlot.value)
        root.put(KEY_QUICK_MEMO, memoObj)

        // ── 6b. Pinned Quick Snippets (optional on restore). Recent clipboard text is never exported. ──
        root.put(KEY_QUICK_SNIPPETS, JSONArray(PreferencesManager.quickSnippets.value))

        // ── 7. Per-app lid LED rules (optional on restore) ──
        root.put(
            KEY_LID_LED_APP_RULES,
            JSONArray(LidAppLedRuleCodec.encode(PreferencesManager.getLidAppLedRules().values))
        )

        // ── 8. Mochibunny pet (optional on restore: older backups have no "pet") ──
        root.put(PetBackupCodec.KEY, PetBackupCodec.toJson(PetManager.stateForBackup(context)))

        return root.toString(2)
    }

    /**
     * Exports backup data to an OutputStream.
     */
    fun exportBackup(context: Context, outputStream: OutputStream) {
        val json = createBackupJson(context)
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json)
            writer.flush()
        }
    }

    /**
     * Reads and validates a backup selected through the Storage Access Framework.
     * This method never changes preferences or files.
     */
    fun previewBackup(context: Context, uri: Uri): Result<BackupPreviewSummary> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("The selected backup could not be opened.")
            val jsonText = readBackupText(inputStream)
            Result.success(parseAndValidateBackup(jsonText).preview)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validates JSON backup content without touching application state.
     */
    fun previewBackupJson(jsonString: String): Result<BackupPreviewSummary> {
        return try {
            Result.success(parseAndValidateBackup(jsonString).preview)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseAndValidateBackup(jsonString: String): ValidatedBackup {
        require(jsonString.isNotBlank()) { "The backup file is empty." }
        val root = JSONObject(jsonString)
        val version = root.requiredInt("version")
        require(version in 1..CURRENT_BACKUP_VERSION) {
            "Unsupported backup version: $version"
        }
        val timestamp = root.requiredLong("timestamp")
        require(timestamp > 0) { "Backup timestamp is invalid." }
        val appVersion = root.requiredString("app_version", maxLength = 64)

        val theme = root.requiredObject("theme")
        val themeName = theme.optString("preset").takeIf { it.isNotBlank() && it != "null" }
        if (theme.has("preset")) {
            val preset = theme.get("preset")
            require(preset == JSONObject.NULL || preset is String) { "Theme preset is invalid." }
        }
        theme.requiredString("custom_user_name", maxLength = 128, allowBlank = true)
        theme.requiredBoolean("wallpaper_overlay_enabled")
        if (theme.has("home_mascot_size")) {
            theme.requiredString("home_mascot_size", maxLength = 32)
        }
        if (theme.has("theme_font_family")) {
            val fontFamily = theme.requiredString("theme_font_family", maxLength = 128)
            require(!fontFamily.contains('/') && !fontFamily.contains('\\') && fontFamily != "..") {
                "Theme font family is invalid."
            }
        }
        if (theme.has("theme_border_style")) {
            val borderStyle = theme.requiredString("theme_border_style", maxLength = 32)
            require(
                CardBorderStyle.entries.any {
                    it.id.equals(borderStyle.trim(), ignoreCase = true) ||
                        it.name.equals(borderStyle.trim(), ignoreCase = true)
                }
            ) { "Theme border style is invalid." }
        }
        if (theme.has("sub_display_ticker_enabled")) {
            theme.requiredBoolean("sub_display_ticker_enabled")
        }
        if (theme.has("sub_display_ticker_dark")) {
            theme.requiredBoolean("sub_display_ticker_dark")
        }
        if (theme.has("photo_frame_enabled")) {
            theme.requiredBoolean("photo_frame_enabled")
        }
        if (theme.has("photo_frame_style")) {
            val frameStyle = theme.requiredString("photo_frame_style", maxLength = 32)
            require(PhotoFrameStyle.entries.any { it.id == frameStyle }) {
                "Photo frame style is invalid."
            }
        }
        if (theme.has("photo_frame_label")) {
            theme.requiredString("photo_frame_label", maxLength = 64, allowBlank = true)
        }
        val colors = theme.requiredObject("colors")
        colors.keys().forEach { key ->
            val value = colors.get(key)
            if (key == PreferencesManager.KEY_THEME_CARD_ROUNDNESS) {
                require(value is Number && value.toDouble().isFinite() && value.toDouble() in 0.0..100.0) {
                    "Theme card roundness is invalid."
                }
            } else {
                require(value is Number && value.toDouble().isFinite() &&
                    value.toDouble() >= Int.MIN_VALUE && value.toDouble() <= Int.MAX_VALUE
                ) { "Theme color '$key' is invalid." }
            }
        }

        val layout = root.requiredObject("layout")
        val favorites = layout.requiredArray("favorite_items_ordered")
        require(favorites.length() <= MAX_FAVORITES) {
            "Backup contains too many favorites (maximum $MAX_FAVORITES)."
        }
        val favoriteKeys = mutableSetOf<String>()
        for (index in 0 until favorites.length()) {
            val key = favorites.requiredString(index, "favorite")
            require(favoriteKeys.add(key)) { "Backup contains duplicate favorites." }
        }

        val folders = layout.requiredArray("folders")
        require(folders.length() <= MAX_FOLDERS) {
            "Backup contains too many folders (maximum $MAX_FOLDERS)."
        }
        val folderIds = mutableSetOf<String>()
        for (index in 0 until folders.length()) {
            val folder = folders.optJSONObject(index)
                ?: throw IllegalArgumentException("Folder ${index + 1} is invalid.")
            val id = folder.requiredString("id", maxLength = 128)
            folder.requiredString("name", maxLength = 128)
            require(folderIds.add(id)) { "Backup contains duplicate folder IDs." }
            val apps = folder.requiredArray("apps")
            require(apps.length() <= MAX_APPS_PER_FOLDER) {
                "Folder contains too many apps (maximum $MAX_APPS_PER_FOLDER)."
            }
            for (appIndex in 0 until apps.length()) {
                apps.requiredString(appIndex, "folder app")
            }
        }

        validateStringObject(layout.requiredObject("custom_app_icons"), "custom app icons")
        validateStringObject(layout.requiredObject("custom_folder_icons"), "custom folder icons")
        require(layout.requiredArray("hidden_apps").length() <= MAX_FAVORITES) {
            "Backup contains too many hidden apps."
        }
        val hiddenApps = layout.getJSONArray("hidden_apps")
        for (index in 0 until hiddenApps.length()) hiddenApps.requiredString(index, "hidden app")
        // Legacy: backups from before the 2×2 Folder Grid row was removed still carry this key.
        // It is validated (so a malformed file is still rejected) but no longer applied.
        if (layout.has("folder_grid_mode")) layout.requiredBoolean("folder_grid_mode")
        // Optional: backups made before the sticker-tray toggle existed don't carry it.
        if (layout.has("folder_scatter_layout")) layout.requiredBoolean("folder_scatter_layout")
        if (layout.has("app_drawer_sort_mode")) {
            val sortMode = layout.requiredString("app_drawer_sort_mode", maxLength = 32)
            require(AppDrawerSortMode.entries.any { it.name == sortMode }) {
                "App drawer sort mode '$sortMode' is not supported."
            }
        }
        val speedDials = layout.requiredObject("speed_dials")
        require(speedDials.length() <= 8) { "Backup contains too many speed dials." }
        speedDials.keys().forEach { digit ->
            require(digit.toIntOrNull() in 2..9) { "Speed dial '$digit' is invalid." }
            speedDials.requiredString(digit, maxLength = 255)
        }
        if (layout.has("selected_icon_pack")) {
            layout.requiredString("selected_icon_pack", maxLength = 255)
        }

        val remaps = root.requiredObject("hardware_remaps")
        val buttonActions = remaps.requiredObject("button_actions")
        require(buttonActions.length() <= PhysicalKey.entries.size) {
            "Backup contains too many hardware button remaps."
        }
        buttonActions.keys().forEach { scanCodeText ->
            val scanCode = scanCodeText.toIntOrNull()
            require(scanCode != null && PhysicalKey.fromScancode(scanCode) != null) {
                "Hardware remap scancode '$scanCodeText' is not supported."
            }
            val actions = buttonActions.requiredObject(scanCodeText)
            listOf("single", "double", "long").forEach { name ->
                val action = actions.requiredString(name, maxLength = 512)
                require(ActionType.deserialize(action).serialize() == action) {
                    "Hardware remap action '$name' is not supported."
                }
            }
        }
        val staticMappings = remaps.requiredObject("static_mappings")
        require(staticMappings.length() <= MAX_REMAP_ENTRIES) {
            "Backup contains too many static hardware remaps."
        }
        staticMappings.keys().forEach { scanCodeText ->
            val scanCode = scanCodeText.toIntOrNull()
            val physicalKey = scanCode?.let { PhysicalKey.fromScancode(it) }
            require(physicalKey != null && physicalKey.isStaticRemapSafe) {
                "Static remap scancode '$scanCodeText' is not supported."
            }
            val target = staticMappings.requiredString(scanCodeText, maxLength = 64)
            require(target in CONTROLLED_STATIC_KEYCODES) {
                "Static remap target '$target' is not supported."
            }
        }
        remaps.requiredBoolean("use_root_recents_fallback")

        validateBehavior(root.requiredObject("behavior"))
        validateCursor(root.requiredObject("cursor"))
        root.optJSONObject(KEY_QUICK_MEMO)?.let(::validateQuickMemo)
        if (root.has(KEY_QUICK_SNIPPETS)) validateQuickSnippets(root.requiredArray(KEY_QUICK_SNIPPETS))
        if (root.has(KEY_LID_LED_APP_RULES)) validateLidAppLedRules(root.requiredArray(KEY_LID_LED_APP_RULES))

        val preview = BackupPreviewSummary(
            timestamp = timestamp,
            appVersion = appVersion,
            favoriteCount = favorites.length(),
            folderCount = folders.length(),
            remapCount = buttonActions.length() + staticMappings.length(),
            themeName = themeName
        )
        return ValidatedBackup(root, preview)
    }

    private fun readBackupText(inputStream: InputStream): String {
        return inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var totalBytes = 0
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                totalBytes += bytesRead
                require(totalBytes <= MAX_BACKUP_SIZE_BYTES) {
                    "Backup file is too large (maximum ${MAX_BACKUP_SIZE_BYTES / 1024} KB)."
                }
                output.write(buffer, 0, bytesRead)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun validateStringObject(value: JSONObject, fieldName: String) {
        require(value.length() <= MAX_ICON_ENTRIES) { "Backup contains too many $fieldName entries." }
        value.keys().forEach { key ->
            require(key.isNotBlank() && key.length <= 255) { "A $fieldName key is invalid." }
            value.requiredString(key, maxLength = 4096)
        }
    }

    private fun validateBehavior(value: JSONObject) {
        listOf(
            "dpad_wrap_around", "dpad_boundary_haptics", "media_volume_lock_enabled",
            "lid_close_snap_sound_enabled",
            "system_monitor_enabled", "ime_hold_for_digit_enabled", "ime_hold_vibration",
            "ime_auto_commit_enabled"
        ).forEach { value.requiredBoolean(it) }
        value.requiredString("launcher_audio_stream", maxLength = 64)
        value.requiredIntInRange("launcher_sound_volume", 0..100)
        value.requiredString("keypad_sound_mode", maxLength = 64)
        value.requiredIntInRange("system_monitor_interval_sec", 1..60)
        value.requiredIntInRange("ime_hold_delay_ms", 100..5000)
        value.requiredIntInRange("ime_idle_commit_ms", 100..10000)
        // Optional: legacy keys no longer exported (features now always on) and keys added in
        // later versions. Validate the type only when present, so old and new backups both restore.
        listOf(
            "kaomoji_wheel_enabled", "t9_search_enabled",
            "show_root_options", "non_root_screen_off_reset",
            "ime_predictive_t9_enabled", "ime_t9_learn_new_words",
            "lid_led_enabled", "lid_led_charging"
        ).forEach {
            if (value.has(it)) value.requiredBoolean(it)
        }
        if (value.has("lid_open_action")) {
            val action = value.requiredString("lid_open_action", maxLength = 32)
            require(LidOpenAction.entries.any { it.id == action }) {
                "Backup field 'lid_open_action' is invalid."
            }
        }
        if (value.has("lid_led_color")) {
            value.requiredString("lid_led_color", maxLength = 32)
        }
        listOf("lid_led_notification_animation_id", "lid_led_charging_animation_id").forEach { key ->
            if (value.has(key)) {
                val animId = value.requiredString(key, maxLength = 128, allowBlank = true)
                require(!animId.contains('/') && !animId.contains('\\') && animId != "..") {
                    "Backup field '$key' is invalid."
                }
            }
        }
        if (value.has("ime_t9_dictionary_lang")) {
            val lang = value.requiredString("ime_t9_dictionary_lang", maxLength = 255)
            require(!lang.contains('/') && !lang.contains('\\') && lang != "..") {
                "Backup field 'ime_t9_dictionary_lang' is invalid."
            }
        }
        if (value.has("ime_t9_learned_words")) {
            val learnedJson = value.requiredString(
                "ime_t9_learned_words",
                maxLength = MAX_T9_LEARNED_WORDS_JSON_CHARS,
                allowBlank = true
            )
            if (learnedJson.isNotBlank()) {
                validateLearnedWordsJson(learnedJson)
            }
        }
        if (value.has("ime_t9_user_words")) {
            validateUserWordsObject(value.requiredObject("ime_t9_user_words"))
        }
    }

    private fun validateUserWordsObject(root: JSONObject) {
        require(root.length() <= MAX_T9_USER_WORD_DICTS) {
            "Backup field 'ime_t9_user_words' contains too many dictionaries."
        }
        val dictKeys = root.keys()
        while (dictKeys.hasNext()) {
            val dictId = dictKeys.next()
            require(dictId.isNotBlank() && dictId.length <= 128) {
                "Backup field 'ime_t9_user_words' has an invalid dictionary id."
            }
            val wordsObj = root.requiredObject(dictId)
            require(wordsObj.length() <= UserWordStore.MAX_WORDS_PER_DICT) {
                "Backup field 'ime_t9_user_words' exceeds ${UserWordStore.MAX_WORDS_PER_DICT} words for '$dictId'."
            }
            val wordKeys = wordsObj.keys()
            while (wordKeys.hasNext()) {
                val word = wordKeys.next()
                require(LearnedWordExtractor.isValidLearnedWord(word)) {
                    "Backup field 'ime_t9_user_words' has an invalid word entry."
                }
                wordsObj.requiredIntInRange(word, 1..UserWordStore.MAX_COUNT)
            }
        }
    }

    private fun validateLearnedWordsJson(jsonString: String) {
        val root = try {
            JSONObject(jsonString)
        } catch (_: Exception) {
            throw IllegalArgumentException("Backup field 'ime_t9_learned_words' is not valid JSON.")
        }
        require(root.length() <= MAX_T9_LEARNED_DICTS) {
            "Backup field 'ime_t9_learned_words' contains too many dictionaries."
        }
        val dictKeys = root.keys()
        while (dictKeys.hasNext()) {
            val dictId = dictKeys.next()
            require(dictId.isNotBlank() && dictId.length <= 128) {
                "Backup field 'ime_t9_learned_words' has an invalid dictionary id."
            }
            val wordsObj = root.optJSONObject(dictId)
                ?: throw IllegalArgumentException("Backup field 'ime_t9_learned_words' entry '$dictId' is invalid.")
            require(wordsObj.length() <= UserWordStats.MAX_WORDS_PER_DICT) {
                "Backup field 'ime_t9_learned_words' exceeds ${UserWordStats.MAX_WORDS_PER_DICT} words for '$dictId'."
            }
            val wordKeys = wordsObj.keys()
            while (wordKeys.hasNext()) {
                val word = wordKeys.next()
                require(word.isNotBlank() && word.length <= UserWordStats.MAX_WORD_LENGTH) {
                    "Backup field 'ime_t9_learned_words' has an invalid word entry."
                }
                wordsObj.requiredIntInRange(word, 1..UserWordStats.MAX_COUNT)
            }
        }
    }

    private fun validateQuickMemo(value: JSONObject) {
        if (value.has("slots")) {
            val slots = value.requiredArray("slots")
            require(slots.length() <= PreferencesManager.QUICK_MEMO_SLOT_COUNT) { "Backup has too many Quick Memo slots." }
            for (i in 0 until slots.length()) {
                val text = slots.get(i)
                require(text is String && text.length <= 4 * PreferencesManager.QUICK_MEMO_MAX_CHARS) {
                    "Quick Memo slot ${i + 1} is invalid."
                }
            }
        }
        if (value.has("pinned_slot")) value.requiredIntInRange("pinned_slot", 1..PreferencesManager.QUICK_MEMO_SLOT_COUNT)
        if (value.has("last_opened_slot")) value.requiredIntInRange("last_opened_slot", 1..PreferencesManager.QUICK_MEMO_SLOT_COUNT)
    }

    private fun validateQuickSnippets(value: JSONArray) {
        require(value.length() <= PreferencesManager.QUICK_SNIPPET_MAX_COUNT) { "Backup has too many Quick Snippets." }
        for (i in 0 until value.length()) {
            val text = value.get(i)
            require(text is String && text.length <= 4 * PreferencesManager.QUICK_SNIPPET_MAX_CHARS) {
                "Quick Snippet ${i + 1} is invalid."
            }
        }
    }

    private fun validateLidAppLedRules(value: JSONArray) {
        require(value.length() <= LidAppLedRule.MAX_RULES) { "Backup has too many per-app LED rules." }
        for (i in 0 until value.length()) {
            val rule = value.optJSONObject(i) ?: throw IllegalArgumentException("Per-app LED rule $i is invalid.")
            rule.requiredString("packageName", maxLength = LidAppLedRule.MAX_PACKAGE_LENGTH)
            val mode = rule.requiredString("mode", maxLength = 32)
            require(AppLedRuleMode.entries.any { it.name == mode }) { "Per-app LED rule mode '$mode' is not supported." }
        }
    }

    private fun validateCursor(value: JSONObject) {
        value.requiredIntInRange("toggle_keycode", 0..1000)
        value.requiredFiniteNumber("move_speed", 0.1..100.0)
        value.requiredFiniteNumber("acceleration", 1.0..5.0)
        value.requiredIntInRange("tap_step_px", 1..1000)
        value.requiredIntInRange("max_speed_px_per_sec", 1..10000)
        value.requiredIntInRange("accel_ramp_ms", 1..10000)
        if (value.has("scroll_step_px")) {
            value.requiredIntInRange(
                "scroll_step_px",
                CursorPreferences.MIN_SCROLL_STEP_PX..CursorPreferences.MAX_SCROLL_STEP_PX
            )
        }
        require(value.requiredString("click_method", maxLength = 32) in ClickMethod.entries.map { it.name }) {
            "Cursor click method is invalid."
        }
        require(value.requiredString("hotspot_alignment", maxLength = 32) in HotspotAlignment.entries.map { it.name }) {
            "Cursor hotspot alignment is invalid."
        }
        value.requiredIntInRange("cursor_size_dp", 1..256)
        value.requiredFiniteNumber("gif_playback_speed", 0.1..4.0)
    }

    private fun JSONObject.requiredObject(name: String): JSONObject =
        optJSONObject(name) ?: throw IllegalArgumentException("Backup field '$name' is missing or invalid.")

    private fun JSONObject.requiredArray(name: String): JSONArray =
        optJSONArray(name) ?: throw IllegalArgumentException("Backup field '$name' is missing or invalid.")

    private fun JSONObject.requiredString(
        name: String,
        maxLength: Int = 255,
        allowBlank: Boolean = false
    ): String {
        val value = opt(name)
        require(value is String && value.length <= maxLength && (allowBlank || value.isNotBlank())) {
            "Backup field '$name' is missing or invalid."
        }
        return value
    }

    private fun JSONArray.requiredString(index: Int, fieldName: String): String {
        val value = opt(index)
        require(value is String && value.isNotBlank() && value.length <= 255) {
            "Backup $fieldName ${index + 1} is missing or invalid."
        }
        return value
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        val value = opt(name)
        require(value is Boolean) { "Backup field '$name' is missing or invalid." }
        return value
    }

    private fun JSONObject.requiredInt(name: String): Int {
        val value = opt(name)
        require(value is Number && value.toDouble().isFinite() &&
            value.toDouble() >= Int.MIN_VALUE && value.toDouble() <= Int.MAX_VALUE &&
            value.toDouble() % 1.0 == 0.0
        ) { "Backup field '$name' is missing or invalid." }
        return value.toInt()
    }

    private fun JSONObject.requiredLong(name: String): Long {
        val value = opt(name)
        require(value is Number && value.toDouble().isFinite() &&
            value.toDouble() > 0.0 && value.toDouble() <= Long.MAX_VALUE.toDouble() &&
            value.toDouble() % 1.0 == 0.0
        ) { "Backup field '$name' is missing or invalid." }
        return value.toLong()
    }

    private fun JSONObject.requiredIntInRange(name: String, range: IntRange): Int {
        val value = requiredInt(name)
        require(value in range) { "Backup field '$name' is outside the supported range." }
        return value
    }

    private fun JSONObject.requiredFiniteNumber(name: String, range: ClosedFloatingPointRange<Double>): Double {
        val value = opt(name)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() in range) {
            "Backup field '$name' is outside the supported range."
        }
        return value.toDouble()
    }

    /**
     * Imports and applies a backup from an InputStream.
     */
    fun importBackup(context: Context, inputStream: InputStream): Result<BackupSummary> {
        return try {
            val jsonText = readBackupText(inputStream)
            restoreBackupJson(context, jsonText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses and restores Mochibun configuration from JSON.
     */
    fun restoreBackupJson(context: Context, jsonString: String): Result<BackupSummary> {
        val validated = try {
            parseAndValidateBackup(jsonString)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            val root = validated.root

            val timestamp = validated.preview.timestamp
            val appVersion = validated.preview.appVersion

            val launcherPrefs = PreferencesManager.getPrefs(context)
            val launcherEditor = launcherPrefs.edit()

            // ── 1. Restore Theme ─────────────────────────────────────────
            val themeObj = root.optJSONObject("theme")
            val themePreset = themeObj?.optString("preset")?.takeUnless { it == "null" }
            if (!themePreset.isNullOrBlank()) {
                PreferencesManager.setThemePreset(context, themePreset)
            }
            if (themeObj?.has("custom_user_name") == true) {
                PreferencesManager.setCustomUserName(context, themeObj.optString("custom_user_name", ""))
            }
            if (themeObj?.has("wallpaper_overlay_enabled") == true) {
                PreferencesManager.setWallpaperOverlayEnabled(
                    context,
                    themeObj.optBoolean("wallpaper_overlay_enabled", true)
                )
            }
            if (themeObj?.has("home_mascot_size") == true) {
                PreferencesManager.setHomeMascotSize(
                    context,
                    HomeMascotSize.fromId(themeObj.optString("home_mascot_size"))
                )
            }
            if (themeObj?.has("theme_font_family") == true) {
                PreferencesManager.setThemeFontFamily(
                    context,
                    themeObj.optString("theme_font_family", PreferencesManager.DEFAULT_THEME_FONT_FAMILY)
                )
            }
            if (themeObj?.has("theme_border_style") == true) {
                PreferencesManager.setThemeBorderStyle(
                    context,
                    themeObj.optString("theme_border_style", PreferencesManager.DEFAULT_THEME_BORDER_STYLE)
                )
            }
            if (themeObj?.has("sub_display_ticker_enabled") == true) {
                PreferencesManager.setSubDisplayTickerEnabled(
                    context,
                    themeObj.optBoolean("sub_display_ticker_enabled", PreferencesManager.DEFAULT_SUB_DISPLAY_TICKER_ENABLED)
                )
            }
            if (themeObj?.has("sub_display_ticker_dark") == true) {
                PreferencesManager.setSubDisplayTickerDark(
                    context,
                    themeObj.optBoolean("sub_display_ticker_dark", PreferencesManager.DEFAULT_SUB_DISPLAY_TICKER_DARK)
                )
            }
            if (themeObj?.has("photo_frame_enabled") == true) {
                PreferencesManager.setPhotoFrameEnabled(
                    context,
                    themeObj.optBoolean("photo_frame_enabled", PreferencesManager.DEFAULT_PHOTO_FRAME_ENABLED)
                )
            }
            if (themeObj?.has("photo_frame_style") == true) {
                PreferencesManager.setPhotoFrameStyle(
                    context,
                    PhotoFrameStyle.fromId(
                        themeObj.optString("photo_frame_style", PreferencesManager.DEFAULT_PHOTO_FRAME_STYLE)
                    ).id
                )
            }
            if (themeObj?.has("photo_frame_label") == true) {
                PreferencesManager.setPhotoFrameLabel(
                    context,
                    themeObj.optString("photo_frame_label", PreferencesManager.DEFAULT_PHOTO_FRAME_LABEL)
                )
            }

            val colorsObj = themeObj?.optJSONObject("colors")
            if (colorsObj != null) {
                PreferencesManager.THEME_COLOR_KEYS.forEach { key ->
                    if (colorsObj.has(key)) {
                        launcherEditor.putInt(key, colorsObj.getInt(key))
                    }
                }
                if (colorsObj.has(PreferencesManager.KEY_THEME_CARD_ROUNDNESS)) {
                    launcherEditor.putFloat(
                        PreferencesManager.KEY_THEME_CARD_ROUNDNESS,
                        colorsObj.getDouble(PreferencesManager.KEY_THEME_CARD_ROUNDNESS).toFloat()
                    )
                }
            }

            // ── 2. Restore Layout, Favorites & Folders ───────────────────
            val layoutObj = root.optJSONObject("layout")
            var folderCount = 0
            var favoriteCount = 0
            var hiddenCount = 0

            if (layoutObj != null) {
                // Favorite item keys
                val favItemsArray = layoutObj.optJSONArray("favorite_items_ordered")
                if (favItemsArray != null) {
                    val keys = buildList {
                        for (i in 0 until favItemsArray.length()) {
                            val k = favItemsArray.optString(i)
                            if (k.isNotBlank()) add(k)
                        }
                    }
                    favoriteCount = keys.size
                    PreferencesManager.saveFavoriteItemKeys(context, keys)
                }

                // Folders
                val foldersArray = layoutObj.optJSONArray("folders")
                if (foldersArray != null) {
                    val restoredFolders = buildList {
                        for (i in 0 until foldersArray.length()) {
                            val fObj = foldersArray.optJSONObject(i) ?: continue
                            val id = fObj.optString("id")
                            val name = fObj.optString("name")
                            val appsArray = fObj.optJSONArray("apps") ?: JSONArray()
                            val apps = buildList {
                                for (j in 0 until appsArray.length()) {
                                    val pkg = appsArray.optString(j)
                                    if (pkg.isNotBlank()) add(pkg)
                                }
                            }
                            if (id.isNotBlank() && name.isNotBlank()) {
                                add(FavoriteFolder(id = id, name = name, appPackages = apps))
                            }
                        }
                    }
                    folderCount = restoredFolders.size
                    PreferencesManager.saveFavoriteFolders(context, restoredFolders)
                }

                // Clear prior custom icons and restore from backup
                val keysToRemove = launcherPrefs.all.keys.filter {
                    it.startsWith(PreferencesManager.KEY_CUSTOM_ICON_PREFIX) ||
                            it.startsWith(PreferencesManager.KEY_CUSTOM_FOLDER_ICON_PREFIX)
                }
                keysToRemove.forEach { launcherEditor.remove(it) }

                val appIconsObj = layoutObj.optJSONObject("custom_app_icons")
                appIconsObj?.keys()?.forEach { pkg ->
                    launcherEditor.putString("${PreferencesManager.KEY_CUSTOM_ICON_PREFIX}$pkg", appIconsObj.getString(pkg))
                }

                val folderIconsObj = layoutObj.optJSONObject("custom_folder_icons")
                folderIconsObj?.keys()?.forEach { folderId ->
                    launcherEditor.putString("${PreferencesManager.KEY_CUSTOM_FOLDER_ICON_PREFIX}$folderId", folderIconsObj.getString(folderId))
                }

                // Hidden Apps
                val hiddenArray = layoutObj.optJSONArray("hidden_apps")
                if (hiddenArray != null) {
                    val hiddenSet = buildSet {
                        for (i in 0 until hiddenArray.length()) {
                            val pkg = hiddenArray.optString(i)
                            if (pkg.isNotBlank()) add(pkg)
                        }
                    }
                    hiddenCount = hiddenSet.size
                    PreferencesManager.setHiddenPackages(context, hiddenSet)
                }

                if (layoutObj.has("folder_scatter_layout")) {
                    PreferencesManager.setFolderScatterLayout(
                        context,
                        layoutObj.optBoolean("folder_scatter_layout", PreferencesManager.DEFAULT_FOLDER_SCATTER_LAYOUT)
                    )
                }
                if (layoutObj.has("app_drawer_sort_mode")) {
                    val sortModeName = layoutObj.optString("app_drawer_sort_mode", PreferencesManager.DEFAULT_APP_DRAWER_SORT_MODE)
                    val sortMode = AppDrawerSortMode.entries.firstOrNull { it.name == sortModeName }
                        ?: AppDrawerSortMode.ALPHABETICAL
                    PreferencesManager.setAppDrawerSortMode(context, sortMode)
                }

                // Speed dials
                val speedDialsObj = layoutObj.optJSONObject("speed_dials")
                for (digit in 2..9) {
                    PreferencesManager.setSpeedDialPackage(context, digit, null)
                }
                speedDialsObj?.keys()?.forEach { digitStr ->
                    val digit = digitStr.toIntOrNull()
                    if (digit != null && digit in 2..9) {
                        PreferencesManager.setSpeedDialPackage(context, digit, speedDialsObj.getString(digitStr))
                    }
                }

                if (layoutObj.has("selected_icon_pack")) {
                    launcherEditor.putString("selected_icon_pack", layoutObj.optString("selected_icon_pack", ""))
                }
            }
            launcherEditor.apply()

            // ── 3. Restore Hardware Remaps ───────────────────────────────
            var remapCount = 0
            val remapsObj = root.optJSONObject("hardware_remaps")
            if (remapsObj != null) {
                val remapPrefs = HardwareRemapManager.getPrefs(context)
                val remapEditor = remapPrefs.edit()

                // Clear previous remaps
                remapPrefs.all.keys.forEach { remapEditor.remove(it) }

                val buttonActionsObj = remapsObj.optJSONObject("button_actions")
                if (buttonActionsObj != null) {
                    buttonActionsObj.keys().forEach { scancodeStr ->
                        val scancode = scancodeStr.toIntOrNull()
                        val cfgObj = buttonActionsObj.optJSONObject(scancodeStr)
                        if (scancode != null && cfgObj != null) {
                            val single = cfgObj.optString("single", "NONE")
                            val double = cfgObj.optString("double", "NONE")
                            val long = cfgObj.optString("long", "NONE")
                            remapEditor.putString("action_single_$scancode", single)
                            remapEditor.putString("action_double_$scancode", double)
                            remapEditor.putString("action_long_$scancode", long)
                            remapCount++
                        }
                    }
                }

                val staticMappingsObj = remapsObj.optJSONObject("static_mappings")
                staticMappingsObj?.keys()?.forEach { scancodeStr ->
                    val scancode = scancodeStr.toIntOrNull()
                    val target = staticMappingsObj.optString(scancodeStr, "")
                    if (scancode != null && target.isNotBlank()) {
                        remapEditor.putString("static_remap_$scancode", target)
                        remapCount++
                    }
                }

                if (remapsObj.has("use_root_recents_fallback")) {
                    remapEditor.putBoolean(
                        HardwareRemapManager.KEY_USE_ROOT_RECENTS_FALLBACK,
                        remapsObj.optBoolean("use_root_recents_fallback", false)
                    )
                }
                remapEditor.apply()
                HardwareRemapManager.reloadActiveConfig(context)
            }

            // ── 4. Restore Behavior ──────────────────────────────────────
            val behaviorObj = root.optJSONObject("behavior")
            if (behaviorObj != null) {
                if (behaviorObj.has("dpad_wrap_around")) {
                    PreferencesManager.setDpadWrapAround(context, behaviorObj.optBoolean("dpad_wrap_around", true))
                }
                if (behaviorObj.has("dpad_boundary_haptics")) {
                    PreferencesManager.setDpadBoundaryHaptics(context, behaviorObj.optBoolean("dpad_boundary_haptics", true))
                }
                if (behaviorObj.has("launcher_audio_stream")) {
                    PreferencesManager.setLauncherAudioStream(context, behaviorObj.optString("launcher_audio_stream", PreferencesManager.DEFAULT_LAUNCHER_AUDIO_STREAM))
                }
                if (behaviorObj.has("launcher_sound_volume")) {
                    PreferencesManager.setLauncherSoundVolume(context, behaviorObj.optInt("launcher_sound_volume", PreferencesManager.DEFAULT_LAUNCHER_SOUND_VOLUME))
                }
                if (behaviorObj.has("keypad_sound_mode")) {
                    PreferencesManager.setKeypadSoundMode(context, behaviorObj.optString("keypad_sound_mode", PreferencesManager.DEFAULT_KEYPAD_SOUND_MODE))
                }
                if (behaviorObj.has("media_volume_lock_enabled")) {
                    PreferencesManager.setMediaVolumeLockEnabled(context, behaviorObj.optBoolean("media_volume_lock_enabled", PreferencesManager.DEFAULT_MEDIA_VOLUME_LOCK_ENABLED))
                }
                if (behaviorObj.has("lid_close_snap_sound_enabled")) {
                    PreferencesManager.setLidCloseSnapSoundEnabled(context, behaviorObj.optBoolean("lid_close_snap_sound_enabled", PreferencesManager.DEFAULT_LID_CLOSE_SNAP_SOUND))
                }
                // Older backups may still carry "kaomoji_wheel_enabled" / "t9_search_enabled"; both
                // features are now always on, so those keys are ignored.
                if (behaviorObj.has("soft_key_nav_enabled")) {
                    PreferencesManager.setSoftKeyNavEnabled(
                        context,
                        behaviorObj.optBoolean("soft_key_nav_enabled", PreferencesManager.DEFAULT_SOFT_KEY_NAV_ENABLED)
                    )
                }
                if (behaviorObj.has("ime_hold_delay_ms")) {
                    PreferencesManager.setImeHoldDelayMs(
                        context,
                        behaviorObj.optInt("ime_hold_delay_ms", PreferencesManager.DEFAULT_IME_HOLD_DELAY_MS)
                    )
                }
                if (behaviorObj.has("ime_hold_for_digit_enabled")) {
                    PreferencesManager.setImeHoldForDigitEnabled(
                        context,
                        behaviorObj.optBoolean("ime_hold_for_digit_enabled", PreferencesManager.DEFAULT_IME_HOLD_FOR_DIGIT_ENABLED)
                    )
                }
                if (behaviorObj.has("ime_idle_commit_ms")) {
                    PreferencesManager.setImeIdleCommitMs(
                        context,
                        behaviorObj.optInt("ime_idle_commit_ms", PreferencesManager.DEFAULT_IME_IDLE_COMMIT_MS)
                    )
                }
                if (behaviorObj.has("ime_auto_commit_enabled")) {
                    PreferencesManager.setImeAutoCommitEnabled(
                        context,
                        behaviorObj.optBoolean("ime_auto_commit_enabled", PreferencesManager.DEFAULT_IME_AUTO_COMMIT_ENABLED)
                    )
                }
                if (behaviorObj.has("ime_hold_vibration")) {
                    PreferencesManager.setImeHoldVibrationEnabled(
                        context,
                        behaviorObj.optBoolean("ime_hold_vibration", PreferencesManager.DEFAULT_IME_HOLD_VIBRATION)
                    )
                }
                if (behaviorObj.has("ime_predictive_t9_enabled")) {
                    PreferencesManager.setImePredictiveT9Enabled(
                        context,
                        behaviorObj.optBoolean("ime_predictive_t9_enabled", PreferencesManager.DEFAULT_IME_PREDICTIVE_T9_ENABLED)
                    )
                }
                if (behaviorObj.has("ime_t9_dictionary_lang")) {
                    PreferencesManager.setImeT9DictionaryLang(
                        context,
                        behaviorObj.optString("ime_t9_dictionary_lang", PreferencesManager.DEFAULT_IME_T9_DICTIONARY_LANG)
                    )
                }
                var learnedRestored = false
                if (behaviorObj.has("ime_t9_learned_words")) {
                    PreferencesManager.setImeT9LearnedWordsJson(
                        context,
                        behaviorObj.optString("ime_t9_learned_words", PreferencesManager.DEFAULT_IME_T9_LEARNED_WORDS)
                    )
                    learnedRestored = true
                }
                if (behaviorObj.has("ime_t9_learn_new_words")) {
                    PreferencesManager.setImeT9LearnNewWordsEnabled(
                        context,
                        behaviorObj.optBoolean("ime_t9_learn_new_words", PreferencesManager.DEFAULT_IME_T9_LEARN_NEW_WORDS)
                    )
                }
                if (behaviorObj.has("ime_t9_user_words")) {
                    val normalizedStore = UserWordStore().apply {
                        loadFromJsonString(behaviorObj.optJSONObject("ime_t9_user_words")?.toString())
                    }
                    PreferencesManager.setImeT9UserWordsJson(context, normalizedStore.toJsonString())
                    learnedRestored = true
                }
                if (learnedRestored) {
                    PreferencesManager.bumpImeT9LearnedEpoch(context)
                }
                // Non-Root Mode switches (v2.1.0); absent from older backups
                if (behaviorObj.has("show_root_options")) {
                    PreferencesManager.setShowRootOptions(
                        context,
                        behaviorObj.optBoolean("show_root_options", false)
                    )
                }
                if (behaviorObj.has("non_root_screen_off_reset")) {
                    PreferencesManager.setNonRootScreenOffResetEnabled(
                        context,
                        behaviorObj.optBoolean("non_root_screen_off_reset", true)
                    )
                }
                if (behaviorObj.has("lid_open_action")) {
                    PreferencesManager.setLidOpenAction(
                        context,
                        LidOpenAction.fromId(behaviorObj.optString("lid_open_action", PreferencesManager.DEFAULT_LID_OPEN_ACTION))
                    )
                }
                if (behaviorObj.has("lid_led_enabled")) {
                    PreferencesManager.setLidLedEnabled(
                        context,
                        behaviorObj.optBoolean("lid_led_enabled", PreferencesManager.DEFAULT_LID_LED_ENABLED)
                    )
                }
                if (behaviorObj.has("lid_led_charging")) {
                    PreferencesManager.setLidLedChargingEnabled(
                        context,
                        behaviorObj.optBoolean("lid_led_charging", PreferencesManager.DEFAULT_LID_LED_CHARGING)
                    )
                }
                if (behaviorObj.has("lid_led_color")) {
                    PreferencesManager.setLidLedColor(
                        context,
                        behaviorObj.optString("lid_led_color", PreferencesManager.DEFAULT_LID_LED_COLOR)
                    )
                }
                if (behaviorObj.has("lid_led_notification_animation_id")) {
                    PreferencesManager.setLidLedNotificationAnimationId(
                        context,
                        behaviorObj.optString("lid_led_notification_animation_id", "")
                    )
                }
                if (behaviorObj.has("lid_led_charging_animation_id")) {
                    PreferencesManager.setLidLedChargingAnimationId(
                        context,
                        behaviorObj.optString("lid_led_charging_animation_id", "")
                    )
                }
                if (behaviorObj.has("system_monitor_enabled")) {
                    PreferencesManager.setSystemMonitorEnabled(context, behaviorObj.optBoolean("system_monitor_enabled", true))
                }
                if (behaviorObj.has("system_monitor_interval_sec")) {
                    PreferencesManager.setSystemMonitorInterval(context, behaviorObj.optInt("system_monitor_interval_sec", 2))
                }
            }

            // ── 5. Restore Virtual Cursor ────────────────────────────────
            val cursorObj = root.optJSONObject("cursor")
            if (cursorObj != null) {
                if (cursorObj.has("toggle_keycode")) {
                    CursorPreferences.setToggleKeyCode(context, cursorObj.optInt("toggle_keycode", CursorPreferences.DEFAULT_TOGGLE_KEYCODE))
                }
                if (cursorObj.has("move_speed")) {
                    CursorPreferences.setMoveSpeed(context, cursorObj.optDouble("move_speed", 10.0).toFloat())
                }
                if (cursorObj.has("acceleration")) {
                    CursorPreferences.setAcceleration(context, cursorObj.optDouble("acceleration", 1.08).toFloat())
                }
                if (cursorObj.has("tap_step_px")) {
                    CursorPreferences.setTapStepPx(context, cursorObj.optInt("tap_step_px", CursorPreferences.DEFAULT_TAP_STEP_PX))
                }
                if (cursorObj.has("max_speed_px_per_sec")) {
                    CursorPreferences.setMaxSpeed(context, cursorObj.optInt("max_speed_px_per_sec", CursorPreferences.DEFAULT_MAX_SPEED))
                }
                if (cursorObj.has("accel_ramp_ms")) {
                    CursorPreferences.setAccelRampMs(context, cursorObj.optInt("accel_ramp_ms", CursorPreferences.DEFAULT_ACCEL_RAMP_MS))
                }
                if (cursorObj.has("scroll_step_px")) {
                    CursorPreferences.setScrollStepPx(context, cursorObj.optInt("scroll_step_px", CursorPreferences.DEFAULT_SCROLL_STEP_PX))
                }
                if (cursorObj.has("click_method")) {
                    try {
                        val method = ClickMethod.valueOf(cursorObj.getString("click_method"))
                        CursorPreferences.setClickMethod(context, method)
                    } catch (_: Exception) {}
                }
                if (cursorObj.has("hotspot_alignment")) {
                    try {
                        val alignment = HotspotAlignment.valueOf(cursorObj.getString("hotspot_alignment"))
                        CursorPreferences.setHotspotAlignment(context, alignment)
                    } catch (_: Exception) {}
                }
                if (cursorObj.has("cursor_size_dp")) {
                    CursorPreferences.setCursorSizeDp(context, cursorObj.optInt("cursor_size_dp", CursorPreferences.DEFAULT_CURSOR_SIZE_DP))
                }
                if (cursorObj.has("gif_playback_speed")) {
                    CursorPreferences.setGifPlaybackSpeed(context, cursorObj.optDouble("gif_playback_speed", 0.5).toFloat())
                }
            }

            // ── 6. Restore Quick Memo slots ──────────────────────────────
            root.optJSONObject(KEY_QUICK_MEMO)?.let { memoObj ->
                memoObj.optJSONArray("slots")?.let { slots ->
                    for (slot in 1..PreferencesManager.QUICK_MEMO_SLOT_COUNT) {
                        PreferencesManager.setQuickMemoSlotText(context, slot, slots.optString(slot - 1, ""))
                    }
                }
                if (memoObj.has("pinned_slot")) {
                    PreferencesManager.setPinnedQuickMemoSlot(context, memoObj.optInt("pinned_slot", 1))
                }
                if (memoObj.has("last_opened_slot")) {
                    PreferencesManager.setLastOpenedQuickMemoSlot(context, memoObj.optInt("last_opened_slot", 1))
                }
            }

            // ── 6b. Restore pinned Quick Snippets ─────────────────────────
            root.optJSONArray(KEY_QUICK_SNIPPETS)?.let { array ->
                PreferencesManager.setQuickSnippets(context, List(array.length()) { array.optString(it, "") })
            }

            // ── 7. Restore per-app lid LED rules ─────────────────────────
            root.optJSONArray(KEY_LID_LED_APP_RULES)?.let { rulesArray ->
                PreferencesManager.setLidAppLedRules(
                    context,
                    LidAppLedRuleCodec.decode(rulesArray.toString()).values
                )
            }

            // ── 8. Restore Mochibunny pet ────────────────────────────────
            // Optional: backups made before the pet existed simply keep her current state.
            // Missing fields fall back to defaults; PetManager.restore sanitizes everything.
            root.optJSONObject(PetBackupCodec.KEY)?.let { petObj ->
                PetManager.restore(context, PetBackupCodec.fromJson(petObj))
            }

            // Refresh in-memory reactive state
            PreferencesManager.reload(context)
            one.crinklecod.mochibun.ui.theme.SystemUiThemeBridge.broadcastSavedTheme(context)

            Result.success(
                BackupSummary(
                    timestamp = timestamp,
                    appVersion = appVersion,
                    themePreset = themePreset,
                    folderCount = folderCount,
                    favoriteCount = favoriteCount,
                    hiddenAppCount = hiddenCount,
                    remapCount = remapCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resets all Mochibun launcher configurations, remaps, and cursor preferences to factory defaults.
     */
    fun resetToDefaults(context: Context) {
        PreferencesManager.getPrefs(context).edit().clear().apply()
        HardwareRemapManager.getPrefs(context).edit().clear().apply()
        CursorPreferences.getPrefs(context).edit().clear().apply()

        PreferencesManager.reload(context)
        HardwareRemapManager.reloadActiveConfig(context)
        one.crinklecod.mochibun.ui.theme.SystemUiThemeBridge.broadcastSavedTheme(context)
    }
}
