package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BackupRestoreManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class BackupRestoreTest {

    @Test
    fun testGenerateBackupFileNameFormat() {
        val fileName = BackupRestoreManager.generateBackupFileName()
        assertTrue("Filename should start with mochibun_backup_", fileName.startsWith("mochibun_backup_"))
        assertTrue("Filename should end with .json", fileName.endsWith(".json"))
        assertEquals(36, fileName.length)
    }

    @Test
    fun testCurrentBackupVersion() {
        assertEquals(1, BackupRestoreManager.CURRENT_BACKUP_VERSION)
    }

    @Test
    fun validBackupPreviewIsAccurateAndRestoreApplies() {
        val context = RuntimeEnvironment.getApplication()
        val backupJson = validBackupJson()

        val preview = BackupRestoreManager.previewBackupJson(backupJson.toString()).getOrThrow()
        assertEquals(1_700_000_000_000L, preview.timestamp)
        assertEquals("1.4.0", preview.appVersion)
        assertEquals(2, preview.favoriteCount)
        assertEquals(1, preview.folderCount)
        assertEquals(2, preview.remapCount)
        assertEquals("Lavender", preview.themeName)

        val restored = BackupRestoreManager.restoreBackupJson(context, backupJson.toString()).getOrThrow()
        assertEquals(preview.timestamp, restored.timestamp)
        assertEquals(preview.appVersion, restored.appVersion)
        assertEquals(preview.favoriteCount, restored.favoriteCount)
        assertEquals(preview.folderCount, restored.folderCount)
        assertEquals(preview.remapCount, restored.remapCount)
        assertEquals(listOf("com.example.one", "com.example.two"), PreferencesManager.getFavoriteItemKeys(context))
    }

    @Test
    fun malformedAndIncompleteBackupsFailWithoutChangingFavorites() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.saveFavoriteItemKeys(context, listOf("com.existing.favorite"))

        val malformedResult = BackupRestoreManager.restoreBackupJson(context, "{not-json")
        assertFalse(malformedResult.isSuccess)
        assertEquals(listOf("com.existing.favorite"), PreferencesManager.getFavoriteItemKeys(context))

        val incomplete = validBackupJson().apply { remove("cursor") }
        val incompleteResult = BackupRestoreManager.restoreBackupJson(context, incomplete.toString())
        assertFalse(incompleteResult.isSuccess)
        assertNotNull(incompleteResult.exceptionOrNull()?.message)
        assertEquals(listOf("com.existing.favorite"), PreferencesManager.getFavoriteItemKeys(context))
        assertFalse(BackupRestoreManager.previewBackupJson(incomplete.toString()).isSuccess)
    }

    @Test
    fun reloadRefreshesCachedFlowsFromSharedPreferences() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context)
        PreferencesManager.getPrefs(context).edit()
            .putString(PreferencesManager.KEY_CUSTOM_USER_NAME, "Mochi")
            .putString(PreferencesManager.KEY_THEME_FONT_FAMILY, "reload_font")
            .commit()
        assertFalse(PreferencesManager.themeFontFamily.value == "reload_font")

        PreferencesManager.reload(context)

        assertEquals("Mochi", PreferencesManager.customUserName.value)
        assertEquals("reload_font", PreferencesManager.themeFontFamily.value)
    }

    @Test
    fun restoreReloadsCachedFlows() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context)
        // Written behind the setters, so only a reload at the end of restore picks it up.
        PreferencesManager.getPrefs(context).edit()
            .putString(PreferencesManager.KEY_THEME_FONT_FAMILY, "restored_font")
            .commit()

        BackupRestoreManager.restoreBackupJson(context, validBackupJson().toString()).getOrThrow()

        assertEquals("restored_font", PreferencesManager.themeFontFamily.value)
    }

    @Test
    fun resetToDefaultsReloadsCachedFlows() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context)
        PreferencesManager.setCustomUserName(context, "Mochi")
        PreferencesManager.setDpadWrapAround(context, !PreferencesManager.DEFAULT_DPAD_WRAP_AROUND)

        BackupRestoreManager.resetToDefaults(context)

        assertEquals(PreferencesManager.DEFAULT_CUSTOM_USER_NAME, PreferencesManager.customUserName.value)
        assertEquals(PreferencesManager.DEFAULT_DPAD_WRAP_AROUND, PreferencesManager.isDpadWrapAround.value)
    }

    private fun validBackupJson(): JSONObject = JSONObject()
        .put("version", BackupRestoreManager.CURRENT_BACKUP_VERSION)
        .put("timestamp", 1_700_000_000_000L)
        .put("app_version", "1.4.0")
        .put(
            "theme",
            JSONObject()
                .put("preset", "Lavender")
                .put("custom_user_name", "")
                .put("wallpaper_overlay_enabled", true)
                .put("colors", JSONObject())
        )
        .put(
            "layout",
            JSONObject()
                .put("favorite_items_ordered", JSONArray(listOf("com.example.one", "com.example.two")))
                .put(
                    "folders",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "folder-1")
                            .put("name", "Favorites")
                            .put("apps", JSONArray(listOf("com.example.one")))
                    )
                )
                .put("custom_app_icons", JSONObject())
                .put("custom_folder_icons", JSONObject())
                .put("hidden_apps", JSONArray())
                .put("folder_grid_mode", false)
                .put("speed_dials", JSONObject())
        )
        .put(
            "hardware_remaps",
            JSONObject()
                .put(
                    "button_actions",
                    JSONObject().put(
                        "59",
                        JSONObject()
                            .put("single", "OPEN_WIDGETS_PAGE")
                            .put("double", "NONE")
                            .put("long", "NONE")
                    )
                )
                .put("static_mappings", JSONObject().put("59", "F1"))
                .put("use_root_recents_fallback", false)
        )
        .put(
            "behavior",
            JSONObject()
                .put("dpad_wrap_around", true)
                .put("dpad_boundary_haptics", true)
                .put("launcher_audio_stream", "STREAM_MEDIA")
                .put("launcher_sound_volume", 80)
                .put("keypad_sound_mode", "DEFAULT")
                .put("media_volume_lock_enabled", false)
                .put("lid_close_snap_sound_enabled", true)
                .put("kaomoji_wheel_enabled", true)
                .put("t9_search_enabled", true)
                .put("system_monitor_enabled", true)
                .put("system_monitor_interval_sec", 2)
                .put("ime_hold_delay_ms", 500)
                .put("ime_hold_for_digit_enabled", false)
                .put("ime_hold_vibration", true)
                .put("ime_idle_commit_ms", 2000)
                .put("ime_auto_commit_enabled", true)
        )
        .put(
            "cursor",
            JSONObject()
                .put("toggle_keycode", 124)
                .put("move_speed", 10.0)
                .put("acceleration", 1.08)
                .put("tap_step_px", 3)
                .put("max_speed_px_per_sec", 650)
                .put("accel_ramp_ms", 750)
                .put("click_method", "ACCESSIBILITY_GESTURE")
                .put("hotspot_alignment", "CENTER")
                .put("cursor_size_dp", 32)
                .put("gif_playback_speed", 0.5)
        )
}
