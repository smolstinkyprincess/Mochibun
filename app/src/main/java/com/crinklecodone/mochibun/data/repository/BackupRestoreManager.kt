package com.crinklecodone.mochibun.data.repository

import android.content.Context
import com.crinklecodone.mochibun.BuildConfig
import com.crinklecodone.mochibun.data.model.ActionType
import com.crinklecodone.mochibun.data.model.ButtonActionConfig
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.prefs.ClickMethod
import com.crinklecodone.mochibun.data.prefs.CursorPreferences
import com.crinklecodone.mochibun.data.prefs.HotspotAlignment
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.service.MochibunAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
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

/**
 * Centralized backup and restore manager for Mochibun.
 * Exports and restores themes, layout, favorites, folders, hardware button remaps,
 * cursor physics, and launcher behavior in an open, portable JSON format.
 */
object BackupRestoreManager {

    const val CURRENT_BACKUP_VERSION = 1

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
        layoutObj.put("folder_grid_mode", PreferencesManager.isFolderGridMode(context))

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
        behaviorObj.put("t9_search_enabled", PreferencesManager.isT9SearchEnabled(context))
        behaviorObj.put("system_monitor_enabled", PreferencesManager.isSystemMonitorEnabled(context))
        behaviorObj.put("system_monitor_interval_sec", PreferencesManager.getSystemMonitorInterval(context))
        root.put("behavior", behaviorObj)

        // ── 5. Virtual Cursor Physics & Pointer ──────────────────────
        val cursorObj = JSONObject()
        cursorObj.put("toggle_keycode", CursorPreferences.getToggleKeyCode(context))
        cursorObj.put("move_speed", CursorPreferences.getMoveSpeed(context).toDouble())
        cursorObj.put("acceleration", CursorPreferences.getAcceleration(context).toDouble())
        cursorObj.put("tap_step_px", CursorPreferences.getTapStepPx(context))
        cursorObj.put("max_speed_px_per_sec", CursorPreferences.getMaxSpeed(context))
        cursorObj.put("accel_ramp_ms", CursorPreferences.getAccelRampMs(context))
        cursorObj.put("click_method", CursorPreferences.getClickMethod(context).name)
        cursorObj.put("hotspot_alignment", CursorPreferences.getHotspotAlignment(context).name)
        cursorObj.put("cursor_size_dp", CursorPreferences.getCursorSizeDp(context))
        cursorObj.put("gif_playback_speed", CursorPreferences.getGifPlaybackSpeed(context).toDouble())
        root.put("cursor", cursorObj)

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
     * Imports and applies a backup from an InputStream.
     */
    fun importBackup(context: Context, inputStream: InputStream): Result<BackupSummary> {
        return try {
            val jsonText = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            restoreBackupJson(context, jsonText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses and restores Mochibun configuration from JSON.
     */
    fun restoreBackupJson(context: Context, jsonString: String): Result<BackupSummary> {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", -1)
            if (version < 1 || version > CURRENT_BACKUP_VERSION) {
                return Result.failure(IllegalArgumentException("Unsupported backup version: $version"))
            }

            val timestamp = root.optLong("timestamp", System.currentTimeMillis())
            val appVersion = root.optString("app_version", "unknown")

            val launcherPrefs = PreferencesManager.getPrefs(context)
            val launcherEditor = launcherPrefs.edit()

            // ── 1. Restore Theme ─────────────────────────────────────────
            val themeObj = root.optJSONObject("theme")
            val themePreset = themeObj?.optString("preset", null)
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

                if (layoutObj.has("folder_grid_mode")) {
                    PreferencesManager.setFolderGridMode(context, layoutObj.optBoolean("folder_grid_mode", false))
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
                    }
                }

                if (remapsObj.has("use_root_recents_fallback")) {
                    remapEditor.putBoolean(
                        HardwareRemapManager.KEY_USE_ROOT_RECENTS_FALLBACK,
                        remapsObj.optBoolean("use_root_recents_fallback", false)
                    )
                }
                remapEditor.apply()
                MochibunAccessibilityService.reloadActiveConfig(context)
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
                if (behaviorObj.has("t9_search_enabled")) {
                    PreferencesManager.setT9SearchEnabled(context, behaviorObj.optBoolean("t9_search_enabled", true))
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

            // Refresh in-memory reactive state
            PreferencesManager.init(context)

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

        PreferencesManager.init(context)
        MochibunAccessibilityService.reloadActiveConfig(context)
    }
}
