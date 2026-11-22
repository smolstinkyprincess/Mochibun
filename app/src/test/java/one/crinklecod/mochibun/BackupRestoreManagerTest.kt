package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BackupRestoreManager
import kotlinx.coroutines.Dispatchers
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.pet.PetPrefsStore
import one.crinklecod.mochibun.pet.PetState
import one.crinklecod.mochibun.pet.PetTuning
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
class BackupRestoreManagerTest {

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
    fun folderLayoutKeysAreOptionalAndRestoreScatterToggle() {
        val context = RuntimeEnvironment.getApplication()

        // The fixture carries the retired "folder_grid_mode" key (old backups): still accepted.
        assertTrue(validBackupJson().getJSONObject("layout").has("folder_grid_mode"))

        // New backups drop it and carry "folder_scatter_layout" instead.
        val newStyle = validBackupJson().apply {
            getJSONObject("layout").remove("folder_grid_mode")
            getJSONObject("layout").put("folder_scatter_layout", false)
        }
        PreferencesManager.setFolderScatterLayout(context, true)
        BackupRestoreManager.restoreBackupJson(context, newStyle.toString()).getOrThrow()
        assertFalse(PreferencesManager.isFolderScatterLayout.value)

        // A malformed legacy value is still rejected rather than silently ignored.
        val badLegacy = validBackupJson().apply {
            getJSONObject("layout").put("folder_grid_mode", "yes")
        }
        assertFalse(BackupRestoreManager.previewBackupJson(badLegacy.toString()).isSuccess)
        PreferencesManager.setFolderScatterLayout(context, true)
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

    private fun resetPet(context: android.content.Context, clock: FakePetClock) {
        context.getSharedPreferences(PetPrefsStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        PetManager.resetForTests(clock, Dispatchers.Unconfined)
        PetManager.init(context)
    }

    @Test
    fun backupIncludesPet_andRestoreBringsHerBack() {
        val context = RuntimeEnvironment.getApplication()
        val clock = FakePetClock(now = 1_800_000_000_000L, day = 20_000L)
        resetPet(context, clock)
        PetManager.onNotifClearAll(1)
        PetManager.pet()
        val backedUp = PetManager.state.value

        val backup = JSONObject(BackupRestoreManager.createBackupJson(context))
        assertTrue(backup.has("pet"))
        assertEquals(backedUp.treats, backup.getJSONObject("pet").getInt("treats"))

        assertTrue(PetManager.feed())
        assertFalse(PetManager.feed()) // already full at 1.0 after the first feed
        BackupRestoreManager.restoreBackupJson(context, backup.toString()).getOrThrow()
        assertEquals(backedUp, PetManager.state.value)
    }

    @Test
    fun oldBackupWithoutPet_keepsCurrentPet() {
        val context = RuntimeEnvironment.getApplication()
        resetPet(context, FakePetClock(now = 1_800_000_000_000L, day = 20_000L))
        PetManager.onChargerConnected()
        val before = PetManager.state.value

        BackupRestoreManager.restoreBackupJson(context, validBackupJson().toString()).getOrThrow()
        assertEquals(before, PetManager.state.value)
    }

    @Test
    fun restoredPetIsSanitized_andMissingFieldsUseDefaults() {
        val context = RuntimeEnvironment.getApplication()
        val now = 1_800_000_000_000L
        resetPet(context, FakePetClock(now = now, day = 20_000L))
        val tampered = validBackupJson().put(
            "pet",
            JSONObject()
                .put("treats", 999)
                .put("fullness", "full")
                .put("last_updated_ms", now + 86_400_000L)
        )

        BackupRestoreManager.restoreBackupJson(context, tampered.toString()).getOrThrow()
        val s = PetManager.state.value
        assertEquals(PetTuning.MAX_TREATS, s.treats)
        assertEquals(PetState().fullness, s.fullness, 0f)
        assertEquals(PetState().joy, s.joy, 0f)
        assertEquals(now, s.lastUpdatedMs)
    }

    @Test
    fun t9PredictiveSettingsAndLearnedWordsRoundTripAndOldBackupsRestoreCleanly() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context)
        val learned = """{"en":{"hello":4,"world":2},"file:fr.tsv":{"bonjour":3}}"""
        PreferencesManager.setImePredictiveT9Enabled(context, false)
        PreferencesManager.setImeT9DictionaryLang(context, "file:fr.tsv")
        PreferencesManager.setImeT9LearnedWordsJson(context, learned)

        val backupJson = BackupRestoreManager.createBackupJson(context)
        val parsedBehavior = JSONObject(backupJson).getJSONObject("behavior")
        assertFalse(parsedBehavior.getBoolean("ime_predictive_t9_enabled"))
        assertEquals("file:fr.tsv", parsedBehavior.getString("ime_t9_dictionary_lang"))
        assertEquals(learned, parsedBehavior.getString("ime_t9_learned_words"))

        // Mutate before restore
        PreferencesManager.setImePredictiveT9Enabled(context, true)
        PreferencesManager.setImeT9DictionaryLang(context, "en")
        PreferencesManager.setImeT9LearnedWordsJson(context, "")

        BackupRestoreManager.restoreBackupJson(context, backupJson).getOrThrow()
        assertFalse(PreferencesManager.isImePredictiveT9Enabled(context))
        assertEquals("file:fr.tsv", PreferencesManager.getImeT9DictionaryLang(context))
        assertEquals(learned, PreferencesManager.getImeT9LearnedWordsJson(context))

        // Legacy backup without any of the three new T9 keys must still validate & restore cleanly
        // without clobbering current values.
        val legacy = validBackupJson()
        assertFalse(legacy.getJSONObject("behavior").has("ime_predictive_t9_enabled"))
        BackupRestoreManager.restoreBackupJson(context, legacy.toString()).getOrThrow()
        assertFalse(PreferencesManager.isImePredictiveT9Enabled(context))
        assertEquals("file:fr.tsv", PreferencesManager.getImeT9DictionaryLang(context))
        assertEquals(learned, PreferencesManager.getImeT9LearnedWordsJson(context))
    }

    @Test
    fun t9BehaviorFieldsEnforceValidationBounds() {
        fun withBehaviorField(key: String, value: Any): String = validBackupJson().apply {
            getJSONObject("behavior").put(key, value)
        }.toString()

        // Invalid types / path traversal / blank dictionary lang
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_predictive_t9_enabled", "yes")).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_dictionary_lang", "")).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_dictionary_lang", "../etc/passwd")).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_dictionary_lang", "a".repeat(256))).isSuccess)

        // Malformed learned words JSON
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_learned_words", "{not-json")).isSuccess)

        // Too many dictionaries (> 32)
        val tooManyDicts = JSONObject().apply {
            for (i in 1..33) put("d$i", JSONObject().put("word", 1))
        }.toString()
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_learned_words", tooManyDicts)).isSuccess)

        // Too many words (> 500) in one dictionary
        val tooManyWords = JSONObject().apply {
            val words = JSONObject()
            for (i in 1..501) words.put("w$i", 1)
            put("en", words)
        }.toString()
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_learned_words", tooManyWords)).isSuccess)

        // Count outside 1..100_000
        val badCountZero = JSONObject().put("en", JSONObject().put("hello", 0)).toString()
        val badCountHuge = JSONObject().put("en", JSONObject().put("hello", 100_001)).toString()
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_learned_words", badCountZero)).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_learned_words", badCountHuge)).isSuccess)

        // Word length > 64
        val longWord = JSONObject().put("en", JSONObject().put("a".repeat(65), 1)).toString()
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_learned_words", longWord)).isSuccess)
    }

    @Test
    fun userWordsAndLearnNewWordsBackupRoundTripValidationAndEpochBump() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context)
        val userWordsObj = JSONObject()
            .put("en", JSONObject().put("emii", 3).put("mochibun", 2))
            .put("file:fr.tsv", JSONObject().put("coucou", 4))
        PreferencesManager.setImeT9LearnNewWordsEnabled(context, false)
        PreferencesManager.setImeT9UserWordsJson(context, userWordsObj.toString())
        val epochBeforeBackup = PreferencesManager.getImeT9LearnedEpoch(context)

        val backupJson = BackupRestoreManager.createBackupJson(context)
        val parsedBehavior = JSONObject(backupJson).getJSONObject("behavior")
        assertFalse(parsedBehavior.getBoolean("ime_t9_learn_new_words"))
        assertFalse(parsedBehavior.has("ime_t9_learned_epoch"))
        val exportedUserWords = parsedBehavior.getJSONObject("ime_t9_user_words")
        assertEquals(3, exportedUserWords.getJSONObject("en").getInt("emii"))
        assertEquals(4, exportedUserWords.getJSONObject("file:fr.tsv").getInt("coucou"))

        // Mutate prefs before restore
        PreferencesManager.setImeT9LearnNewWordsEnabled(context, true)
        PreferencesManager.setImeT9UserWordsJson(context, "")

        BackupRestoreManager.restoreBackupJson(context, backupJson).getOrThrow()
        assertFalse(PreferencesManager.isImeT9LearnNewWordsEnabled(context))
        val restoredObj = JSONObject(PreferencesManager.getImeT9UserWordsJson(context))
        assertEquals(3, restoredObj.getJSONObject("en").getInt("emii"))
        assertEquals(2, restoredObj.getJSONObject("en").getInt("mochibun"))
        assertEquals(4, restoredObj.getJSONObject("file:fr.tsv").getInt("coucou"))
        assertEquals(epochBeforeBackup + 1, PreferencesManager.getImeT9LearnedEpoch(context))

        // Old backup without the new Step 5 keys must still restore cleanly
        val legacy = validBackupJson()
        assertFalse(legacy.getJSONObject("behavior").has("ime_t9_learn_new_words"))
        assertFalse(legacy.getJSONObject("behavior").has("ime_t9_user_words"))
        BackupRestoreManager.restoreBackupJson(context, legacy.toString()).getOrThrow()
        assertFalse(PreferencesManager.isImeT9LearnNewWordsEnabled(context))
        assertEquals(3, JSONObject(PreferencesManager.getImeT9UserWordsJson(context)).getJSONObject("en").getInt("emii"))

        // Validation bounds for ime_t9_learn_new_words & ime_t9_user_words
        fun withBehaviorField(key: String, value: Any): String = validBackupJson().apply {
            getJSONObject("behavior").put(key, value)
        }.toString()

        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_learn_new_words", "no")).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", "not-an-object")).isSuccess)

        val tooManyDicts = JSONObject().apply {
            for (i in 1..9) put("d$i", JSONObject().put("emii", 2))
        }
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", tooManyDicts)).isSuccess)

        val tooManyWords = JSONObject().apply {
            val words = JSONObject()
            for (i in 0 until 501) {
                words.put("w${'a' + (i / 26)}${'a' + (i % 26)}word", 2)
            }
            put("en", words)
        }
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", tooManyWords)).isSuccess)

        val badWordChars = JSONObject().put("en", JSONObject().put("user@host", 2))
        val badWordShort = JSONObject().put("en", JSONObject().put("ab", 2))
        val badWordLong = JSONObject().put("en", JSONObject().put("a".repeat(33), 2))
        val badCountZero = JSONObject().put("en", JSONObject().put("emii", 0))
        val badCountHuge = JSONObject().put("en", JSONObject().put("emii", 100_001))
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", badWordChars)).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", badWordShort)).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", badWordLong)).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", badCountZero)).isSuccess)
        assertFalse(BackupRestoreManager.previewBackupJson(withBehaviorField("ime_t9_user_words", badCountHuge)).isSuccess)
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
