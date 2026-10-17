package com.tnyemii.mochibun

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object HardwareRemapManager {

    const val PREFS_NAME = "hardware_remap_preferences"

    const val MODULE_ID = "mochibun_keys"
    const val MODULE_DIR = "/data/adb/modules/mochibun_keys"
    const val MODULE_DISABLE_FILE = "$MODULE_DIR/disable"
    const val MODULE_KL_DIR = "$MODULE_DIR/system/usr/keylayout"
    const val TARGET_KL_VENDOR = "$MODULE_KL_DIR/Vendor_2454_Product_6500.kl"
    const val TARGET_KL_NAME = "$MODULE_KL_DIR/mtk-kpd.kl"

    // Live system mount check
    const val LIVE_KL_VENDOR = "/system/usr/keylayout/Vendor_2454_Product_6500.kl"
    const val LIVE_KL_NAME = "/system/usr/keylayout/mtk-kpd.kl"

    const val KEY_USE_ROOT_RECENTS_FALLBACK = "use_root_recents_fallback"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Reads whether the optional root-shell Recents fallback is enabled.
     * Defaults to false (OFF).
     */
    fun isRootRecentsFallbackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_USE_ROOT_RECENTS_FALLBACK, false)
    }

    /**
     * Persists the root-shell Recents fallback setting and reloads active configuration.
     */
    fun setRootRecentsFallbackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_USE_ROOT_RECENTS_FALLBACK, enabled).apply()
        MochibunAccessibilityService.reloadActiveConfig(context)
    }

    /**
     * Checks whether MochibunAccessibilityService is enabled in Android Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (MochibunAccessibilityService.isServiceRunning) return true
        val expected = ComponentName(context, MochibunAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks whether KeyMapper's accessibility service is currently enabled.
     */
    fun isKeyMapperActive(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("io.github.sds100.keymapper")
    }

    /**
     * Reads configured High-Level Action for a physical key.
     * PhysicalKey.APP_SWITCH defaults to ActionType.GlobalRecents.
     */
    fun getButtonActionConfig(context: Context, key: PhysicalKey): ButtonActionConfig {
        val prefs = getPrefs(context)
        val defaultSingle = if (key == PhysicalKey.APP_SWITCH) ActionType.GlobalRecents else ActionType.None
        val singleRaw = prefs.getString("action_single_${key.scancode}", null)
        val single = if (singleRaw == null) defaultSingle else ActionType.deserialize(singleRaw)
        val double = ActionType.deserialize(prefs.getString("action_double_${key.scancode}", null))
        val long = ActionType.deserialize(prefs.getString("action_long_${key.scancode}", null))
        return ButtonActionConfig(single, double, long)
    }

    /**
     * Saves High-Level Action configuration for a physical key.
     * Instantly refreshes in-memory mapping in MochibunAccessibilityService.
     */
    fun saveButtonActionConfig(context: Context, key: PhysicalKey, config: ButtonActionConfig) {
        getPrefs(context).edit()
            .putString("action_single_${key.scancode}", config.singlePressAction.serialize())
            .putString("action_double_${key.scancode}", config.doublePressAction.serialize())
            .putString("action_long_${key.scancode}", config.longPressAction.serialize())
            .apply()

        MochibunAccessibilityService.reloadActiveConfig(context)
    }

    /**
     * Reads configured static remappings from preferences.
     * Returns a map of scancode -> target KeyCode name (e.g. 169 -> "VOLUME_MUTE").
     */
    fun getConfiguredStaticMappings(context: Context): Map<Int, String> {
        val prefs = getPrefs(context)
        val mappings = mutableMapOf<Int, String>()
        for (key in PhysicalKey.entries) {
            if (!key.isStaticRemapSafe) continue
            val target = prefs.getString("static_remap_${key.scancode}", null)
            val defaultKey = BASELINE_MTK_KPD_MAPPINGS[key.scancode]
            if (!target.isNullOrBlank() && target != defaultKey) {
                mappings[key.scancode] = target
            }
        }
        return mappings
    }

    /**
     * Saves configured static remapping for a key in preferences.
     */
    fun saveConfiguredStaticMapping(context: Context, key: PhysicalKey, targetKeyCode: String?) {
        val editor = getPrefs(context).edit()
        if (targetKeyCode == null || targetKeyCode == BASELINE_MTK_KPD_MAPPINGS[key.scancode]) {
            editor.remove("static_remap_${key.scancode}")
        } else {
            editor.putString("static_remap_${key.scancode}", targetKeyCode)
        }
        editor.apply()
    }

    /**
     * Validates and generates key layout content.
     * Enforces that ALL baseline keys exist and critical keys are never modified.
     */
    fun generateKeyLayoutContent(customMappings: Map<Int, String>): String {
        val builder = StringBuilder()
        builder.append("# Generated by Mochibun Hardware Remapper for AT-M120 (mtk-kpd)\n")
        builder.append("# Vendor 2454 Product 6500 Version 0010\n\n")

        val sortedScancodes = BASELINE_MTK_KPD_MAPPINGS.keys.sorted()
        for (scancode in sortedScancodes) {
            val defaultKey = BASELINE_MTK_KPD_MAPPINGS[scancode] ?: continue
            val mappedKey = customMappings[scancode] ?: defaultKey
            builder.append("key ").append(scancode).append("     ").append(mappedKey).append("\n")
        }

        val content = builder.toString()
        if (!validateKeyLayoutContent(content)) {
            throw IllegalStateException("Generated key layout failed validation checks")
        }
        return content
    }

    /**
     * Validates syntax and safety of key layout content.
     */
    fun validateKeyLayoutContent(content: String): Boolean {
        val lines = content.lines()
        val criticalScancodes = setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 28, 103, 105, 106, 108, 158, 172)
        val foundScancodes = mutableSetOf<Int>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val tokens = line.split("\\s+".toRegex())
            if (tokens.size < 3 || tokens[0] != "key") return false

            val scancode = tokens[1].toIntOrNull() ?: return false
            val keyCodeName = tokens[2]

            // Ensure valid identifier
            if (!keyCodeName.matches("^[A-Z0-9_]+$".toRegex())) return false

            // Ensure critical keys were not accidentally remapped
            if (criticalScancodes.contains(scancode)) {
                val expected = BASELINE_MTK_KPD_MAPPINGS[scancode]
                if (keyCodeName != expected) return false
            }

            foundScancodes.add(scancode)
        }

        // Ensure all critical scancodes are present
        return foundScancodes.containsAll(criticalScancodes)
    }

    /**
     * Staging and deployment of Magisk module with complete validation and atomic execution.
     * Runs off the main thread.
     */
    suspend fun stageAndApplyStaticMapping(
        context: Context,
        customMappings: Map<Int, String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!RootShell.isSuBinaryPresent()) {
                return@withContext Result.failure(Exception("Root (su) binary is not present on this device"))
            }

            val klContent = generateKeyLayoutContent(customMappings)

            // Write to staging files in app cache first
            val stageFile = File(context.cacheDir, "Vendor_2454_Product_6500.kl")
            stageFile.writeText(klContent)
            val stagePath = stageFile.absolutePath

            val propFile = File(context.cacheDir, "module.prop")
            propFile.writeText(
                "id=$MODULE_ID\n" +
                "name=Mochibun Hardware Key Remapper\n" +
                "version=1.0\n" +
                "versionCode=1\n" +
                "author=Mochibun\n" +
                "description=Device-specific key layout override for Chinoe AT-M120 (mtk-kpd)\n"
            )
            val stagePropPath = propFile.absolutePath

            // Build atomic shell commands for root execution
            val script = buildString {
                append("mkdir -p $MODULE_KL_DIR && ")
                append("cp $stagePropPath $MODULE_DIR/module.prop && ")
                append("cp $stagePath $TARGET_KL_VENDOR && ")
                append("cp $stagePath $TARGET_KL_NAME && ")
                append("chmod 644 $MODULE_DIR/module.prop && ")
                append("chmod 644 $TARGET_KL_VENDOR && ")
                append("chmod 644 $TARGET_KL_NAME && ")
                append("chown -R 0:0 $MODULE_DIR && ")
                append("rm -f $MODULE_DISABLE_FILE")
            }

            val execResult = RootShell.execute(script)
            if (!execResult.isSuccess) {
                val err = execResult.stderr.joinToString(" ").ifBlank {
                    execResult.stdout.joinToString(" ").ifBlank { "Exit code ${execResult.exitCode}" }
                }
                return@withContext Result.failure(Exception("Failed to install Magisk module: $err"))
            }

            Result.success("Key layout module successfully installed and staged. Reboot phone to activate.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Safely disables the Magisk module by creating the standard /disable file.
     * Restores default Android keylayout on next reboot.
     */
    suspend fun disableModule(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = RootShell.execute("touch $MODULE_DISABLE_FILE")
            if (res.isSuccess) {
                Result.success("Module disabled. Original key layout will be active upon reboot.")
            } else {
                Result.failure(Exception(res.stderr.joinToString(" ")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Completely removes the Magisk module from /data/adb/modules/mochibun_keys.
     */
    suspend fun removeModule(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = RootShell.execute("rm -rf $MODULE_DIR")
            if (res.isSuccess) {
                Result.success("Module removed. Original default key layout active.")
            } else {
                Result.failure(Exception(res.stderr.joinToString(" ")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reconciles the actual state of the Magisk module by inspecting filesystem evidence.
     */
    suspend fun reconcileModuleStatus(): StaticModuleStatus = withContext(Dispatchers.IO) {
        try {
            // Check if disable file exists
            val disableCheck = RootShell.execute("[ -f $MODULE_DISABLE_FILE ] && echo 'DISABLED'")
            if (disableCheck.stdout.any { it.contains("DISABLED") }) {
                return@withContext StaticModuleStatus.Disabled
            }

            // Check if module directory exists
            val moduleCheck = RootShell.execute("[ -f $TARGET_KL_VENDOR ] && echo 'STAGED'")
            val isStaged = moduleCheck.stdout.any { it.contains("STAGED") }
            if (!isStaged) {
                return@withContext StaticModuleStatus.NotInstalled
            }

            // Check if live system has the file mounted
            val liveCheck = RootShell.execute("[ -f $LIVE_KL_VENDOR ] && echo 'ACTIVE'")
            val isActive = liveCheck.stdout.any { it.contains("ACTIVE") }

            // Read the deployed mappings from the module file
            val catResult = RootShell.execute("cat $TARGET_KL_VENDOR")
            val deployed = parseMappingsFromKl(catResult.stdout)

            if (isActive) {
                StaticModuleStatus.Active(deployed)
            } else {
                StaticModuleStatus.StagedPendingReboot(deployed)
            }
        } catch (e: Exception) {
            StaticModuleStatus.Error(e.message ?: "Failed to reconcile status")
        }
    }

    private fun parseMappingsFromKl(lines: List<String>): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("key ")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val scancode = parts[1].toIntOrNull()
                    val keyName = parts[2]
                    if (scancode != null) {
                        result[scancode] = keyName
                    }
                }
            }
        }
        return result
    }
}
