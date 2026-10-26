package com.crinklecodone.mochibun.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import com.crinklecodone.mochibun.service.MochibunAccessibilityService
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.data.model.ActionType
import com.crinklecodone.mochibun.data.model.BASELINE_MTK_KPD_MAPPINGS
import com.crinklecodone.mochibun.data.model.ButtonActionConfig
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.model.StaticModuleStatus
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
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
     * Reads configured High-Level Action for a physical key.
     * PhysicalKey.APP_SWITCH defaults to ActionType.GlobalRecents.
     */
    fun getButtonActionConfig(context: Context, key: PhysicalKey): ButtonActionConfig {
        val prefs = getPrefs(context)
        val defaultSingle = when (key) {
            PhysicalKey.F1 -> ActionType.OpenWidgetsPage
            PhysicalKey.CAMERA -> ActionType.OpenNotificationsPage
            PhysicalKey.APP_SWITCH -> ActionType.GlobalRecents
            else -> ActionType.None
        }
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
     * Normalizes raw Android keycodes (e.g. numpad digits or AT-M120 side key scanCode 62).
     */
    fun normalizeKeyCode(keyCode: Int, scanCode: Int): Int = when {
        keyCode == KeyEvent.KEYCODE_UNKNOWN && scanCode == 62 -> KeyEvent.KEYCODE_F4
        keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            KeyEvent.KEYCODE_0 + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)
        else -> keyCode
    }

    /**
     * Returns the Speed Dial digit (2..9) if the keyCode corresponds to numeric keys 2–9.
     */
    fun getSpeedDialDigit(keyCode: Int, scanCode: Int = 0): Int? {
        val norm = normalizeKeyCode(keyCode, scanCode)
        return if (norm in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9) {
            norm - KeyEvent.KEYCODE_0
        } else {
            null
        }
    }

    /**
     * Returns the default [ButtonActionConfig] for any physical key on the device.
     */
    fun getDefaultActionConfigForKey(keyCode: Int, scanCode: Int): ButtonActionConfig {
        val norm = normalizeKeyCode(keyCode, scanCode)
        val physicalKey = PhysicalKey.fromKeyCode(norm)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null
        return when {
            physicalKey == PhysicalKey.F1 -> ButtonActionConfig(singlePressAction = ActionType.OpenWidgetsPage)
            physicalKey == PhysicalKey.CAMERA -> ButtonActionConfig(singlePressAction = ActionType.OpenNotificationsPage)
            physicalKey == PhysicalKey.APP_SWITCH -> ButtonActionConfig(singlePressAction = ActionType.GlobalRecents)
            norm == KeyEvent.KEYCODE_0 -> ButtonActionConfig(longPressAction = ActionType.ToggleFlashlight)
            else -> ButtonActionConfig()
        }
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    /**
     * Reads the configured [ButtonActionConfig] for ANY physical button (including [PhysicalKey]s,
     * number pad keys 0–9 / Speed Dial, or arbitrary hardware keycodes/scancodes).
     */
    fun getButtonActionConfigForKey(context: Context, keyCode: Int, scanCode: Int): ButtonActionConfig {
        val norm = normalizeKeyCode(keyCode, scanCode)
        val physicalKey = PhysicalKey.fromKeyCode(norm)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null
        if (physicalKey != null) {
            return getButtonActionConfig(context, physicalKey)
        }

        val prefs = getPrefs(context)
        val suffix = if (norm > 0) "kc_$norm" else "sc_$scanCode"
        val single = ActionType.deserialize(prefs.getString("action_single_$suffix", null))
        val double = ActionType.deserialize(prefs.getString("action_double_$suffix", null))

        val longRaw = prefs.getString("action_long_$suffix", null)
        val long = if (longRaw != null) {
            ActionType.deserialize(longRaw)
        } else {
            val digit = getSpeedDialDigit(norm, scanCode)
            if (digit != null) {
                val speedDialPkg = PreferencesManager.getSpeedDialPackage(context, digit)
                if (!speedDialPkg.isNullOrBlank()) {
                    ActionType.LaunchApp(speedDialPkg, resolveAppLabel(context, speedDialPkg))
                } else {
                    ActionType.None
                }
            } else if (norm == KeyEvent.KEYCODE_0) {
                ActionType.ToggleFlashlight
            } else {
                ActionType.None
            }
        }
        return ButtonActionConfig(single, double, long)
    }

    /**
     * Saves the [ButtonActionConfig] for ANY physical button and syncs Speed Dial preferences
     * when configuring numeric keys 2–9.
     */
    fun saveButtonActionConfigForKey(
        context: Context,
        keyCode: Int,
        scanCode: Int,
        config: ButtonActionConfig
    ) {
        val norm = normalizeKeyCode(keyCode, scanCode)
        val physicalKey = PhysicalKey.fromKeyCode(norm)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null
        if (physicalKey != null) {
            saveButtonActionConfig(context, physicalKey, config)
            return
        }

        val suffix = if (norm > 0) "kc_$norm" else "sc_$scanCode"
        getPrefs(context).edit()
            .putString("action_single_$suffix", config.singlePressAction.serialize())
            .putString("action_double_$suffix", config.doublePressAction.serialize())
            .putString("action_long_$suffix", config.longPressAction.serialize())
            .putInt("scan_$suffix", scanCode)
            .apply()

        val digit = getSpeedDialDigit(norm, scanCode)
        if (digit != null) {
            val launchPkg = (config.longPressAction as? ActionType.LaunchApp)?.packageName
            PreferencesManager.setSpeedDialPackage(context, digit, launchPkg)
        }

        MochibunAccessibilityService.reloadActiveConfig(context)
    }

    /**
     * Resets a specific physical button back to its default action configuration.
     */
    fun resetButtonActionConfigForKey(context: Context, keyCode: Int, scanCode: Int): ButtonActionConfig {
        val norm = normalizeKeyCode(keyCode, scanCode)
        val defaultConfig = getDefaultActionConfigForKey(norm, scanCode)
        val physicalKey = PhysicalKey.fromKeyCode(norm)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null

        if (physicalKey != null) {
            getPrefs(context).edit()
                .remove("action_single_${physicalKey.scancode}")
                .remove("action_double_${physicalKey.scancode}")
                .remove("action_long_${physicalKey.scancode}")
                .apply()
        } else {
            val suffix = if (norm > 0) "kc_$norm" else "sc_$scanCode"
            getPrefs(context).edit()
                .remove("action_single_$suffix")
                .remove("action_double_$suffix")
                .remove("action_long_$suffix")
                .remove("scan_$suffix")
                .apply()

            val digit = getSpeedDialDigit(norm, scanCode)
            if (digit != null) {
                PreferencesManager.setSpeedDialPackage(context, digit, null)
            }
        }

        MochibunAccessibilityService.reloadActiveConfig(context)
        return defaultConfig
    }

    /**
     * Resets all custom button actions and Speed Dial shortcuts back to system defaults.
     */
    fun resetAllButtonActionConfigs(context: Context) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        for (k in prefs.all.keys) {
            if (k.startsWith("action_single_") || k.startsWith("action_double_") || k.startsWith("action_long_") || k.startsWith("scan_")) {
                editor.remove(k)
            }
        }
        editor.apply()
        for (digit in 2..9) {
            PreferencesManager.setSpeedDialPackage(context, digit, null)
        }
        MochibunAccessibilityService.reloadActiveConfig(context)
    }

    /**
     * Returns all non-PhysicalKey custom action configurations keyed by Android keyCode.
     */
    fun getAllArbitraryKeyActionConfigs(context: Context): Map<Int, ButtonActionConfig> {
        val result = mutableMapOf<Int, ButtonActionConfig>()
        val prefs = getPrefs(context)
        val seenKeyCodes = mutableSetOf<Int>()

        for (prefKey in prefs.all.keys) {
            if (prefKey.startsWith("action_single_kc_") ||
                prefKey.startsWith("action_double_kc_") ||
                prefKey.startsWith("action_long_kc_")
            ) {
                val kc = prefKey.substringAfter("_kc_").toIntOrNull()
                if (kc != null) seenKeyCodes.add(kc)
            }
        }
        for (digit in 2..9) {
            if (!PreferencesManager.getSpeedDialPackage(context, digit).isNullOrBlank()) {
                seenKeyCodes.add(KeyEvent.KEYCODE_0 + digit)
            }
        }
        for (kc in seenKeyCodes) {
            val scan = prefs.getInt("scan_kc_$kc", 0)
            val cfg = getButtonActionConfigForKey(context, kc, scan)
            if (cfg.hasAnyAction()) {
                result[kc] = cfg
            }
        }
        return result
    }

    /**
     * Returns compact badge labels for all physical keys that currently have non-default actions.
     */
    fun getConfiguredKeyBadges(context: Context): List<String> {
        val badges = mutableListOf<String>()
        for (key in PhysicalKey.entries) {
            val cfg = getButtonActionConfig(context, key)
            val def = getDefaultActionConfigForKey(key.defaultKeyCode, key.scancode)
            if (cfg != def && cfg.hasAnyAction()) {
                badges.add(PreferencesManager.formatKaomojiKeyBadge(key.defaultKeyCode, key.scancode))
            }
        }
        val arbitrary = getAllArbitraryKeyActionConfigs(context)
        for ((kc, cfg) in arbitrary.entries.sortedBy { it.key }) {
            val def = getDefaultActionConfigForKey(kc, 0)
            if (cfg != def && cfg.hasAnyAction()) {
                badges.add(PreferencesManager.formatKaomojiKeyBadge(kc, 0))
            }
        }
        return badges
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
