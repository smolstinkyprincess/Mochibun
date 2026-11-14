package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.android.quickstep.RecentsActivity
import one.crinklecod.mochibun.service.MochibunAccessibilityService
import one.crinklecod.mochibun.BuildConfig
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.core.system.TorchManager
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.model.ActionType
import one.crinklecod.mochibun.data.model.BASELINE_MTK_KPD_MAPPINGS
import one.crinklecod.mochibun.data.model.ButtonActionConfig
import one.crinklecod.mochibun.data.model.PhysicalKey
import one.crinklecod.mochibun.data.model.StaticModuleStatus
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object HardwareRemapManager {

    private const val TAG = "HardwareRemapManager"
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

    /**
     * Real-time hardware key capture callback.
     * When active (e.g. inside Keypad Remapper dialog or Kaomoji Trigger key picker),
     * this listener intercepts raw KeyEvent before window dispatch.
     * Returning `true` consumes the event.
     */
    @Volatile
    var keyCaptureListener: ((KeyEvent) -> Boolean)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory mapping of Android KeyCode -> Action Configuration
    private val activeMappings = mutableMapOf<Int, ButtonActionConfig>()

    // State tracking for double-tap and long-press per keycode
    private var lastTapTime = 0L
    private var lastTapKeyCode = -1
    private var pendingSinglePressRunnable: Runnable? = null
    private var pendingLongPressRunnable: Runnable? = null
    private var longPressFired = false

    private var isInitialized = false

    private val hardwareKeyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            if (intent.action == MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER) {
                @Suppress("DEPRECATION")
                val rawEvent = intent.getParcelableExtra<KeyEvent>(MochibunSystemActions.EXTRA_KEY_EVENT)
                val event = rawEvent ?: run {
                    val keyCode = intent.getIntExtra(MochibunSystemActions.EXTRA_KEY_CODE, KeyEvent.KEYCODE_UNKNOWN)
                    val action = intent.getIntExtra(MochibunSystemActions.EXTRA_ACTION, KeyEvent.ACTION_DOWN)
                    val repeatCount = intent.getIntExtra(MochibunSystemActions.EXTRA_REPEAT_COUNT, 0)
                    KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, keyCode, repeatCount)
                }
                onKeyEvent(context, event)
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val appContext = context.applicationContext
        reloadActiveConfig(appContext)
        getPrefs(appContext).registerOnSharedPreferenceChangeListener { _, _ ->
            reloadActiveConfig(appContext)
        }
        val filter = android.content.IntentFilter(MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER)
        androidx.core.content.ContextCompat.registerReceiver(
            appContext,
            hardwareKeyReceiver,
            filter,
            MochibunSystemActions.SYSTEM_SERVER_BROADCAST_PERMISSION,
            null,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun reloadActiveConfig(context: Context) {
        activeMappings.clear()
        for (key in PhysicalKey.entries) {
            val config = getButtonActionConfig(context, key)
            if (config.hasAnyAction()) {
                activeMappings[key.defaultKeyCode] = config
                if (key == PhysicalKey.KEY_ENTER) {
                    activeMappings[KeyEvent.KEYCODE_DPAD_CENTER] = config
                }
            }
        }
        for ((kc, config) in getAllArbitraryKeyActionConfigs(context)) {
            val isNumericDigit = kc in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9
            val hasTapOverride = config.singlePressAction !is ActionType.None || config.doublePressAction !is ActionType.None
            if (!isNumericDigit || hasTapOverride) {
                activeMappings[kc] = config
            }
        }
        // Always ensure APP_SWITCH has a valid default mapping if not present
        if (!activeMappings.containsKey(KeyEvent.KEYCODE_APP_SWITCH)) {
            val appSwitchConfig = getButtonActionConfig(context, PhysicalKey.APP_SWITCH)
            activeMappings[KeyEvent.KEYCODE_APP_SWITCH] = appSwitchConfig
        }
    }

    /**
     * Dispatches intercepted hardware key events from system hook or UI.
     * Handles single-tap, double-tap, and 500 ms long-press gestures.
     * Returns true if consumed.
     */
    fun onKeyEvent(context: Context, event: KeyEvent): Boolean {
        keyCaptureListener?.let { listener ->
            if (listener(event)) return true
        }

        val normalizedCode = normalizeKeyCode(event.keyCode, event.scanCode)
        var config = activeMappings[normalizedCode] ?: activeMappings[event.keyCode]
        if (config == null && event.scanCode > 0) {
            val key = PhysicalKey.fromScancode(event.scanCode)
            if (key != null) {
                config = activeMappings[key.defaultKeyCode]
            }
        }

        if (config == null || !config.hasAnyAction()) {
            return false
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "HardwareRemapManager onKeyEvent: code=${event.keyCode} (norm=$normalizedCode), action=${event.action}")
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    longPressFired = false
                    if (config.longPressAction !is ActionType.None) {
                        pendingLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        val runnable = Runnable {
                            longPressFired = true
                            executeAction(context, config.longPressAction)
                        }
                        pendingLongPressRunnable = runnable
                        mainHandler.postDelayed(runnable, 500L)
                    }
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                pendingLongPressRunnable?.let {
                    mainHandler.removeCallbacks(it)
                    pendingLongPressRunnable = null
                }

                if (longPressFired) {
                    longPressFired = false
                    return true
                }

                val now = SystemClock.uptimeMillis()

                if (config.doublePressAction !is ActionType.None) {
                    if (lastTapKeyCode == event.keyCode && (now - lastTapTime) < 350L) {
                        pendingSinglePressRunnable?.let {
                            mainHandler.removeCallbacks(it)
                            pendingSinglePressRunnable = null
                        }
                        lastTapTime = 0L
                        lastTapKeyCode = -1
                        executeAction(context, config.doublePressAction)
                    } else {
                        lastTapTime = now
                        lastTapKeyCode = event.keyCode
                        pendingSinglePressRunnable?.let { mainHandler.removeCallbacks(it) }

                        val runnable = Runnable {
                            lastTapTime = 0L
                            lastTapKeyCode = -1
                            if (config.singlePressAction !is ActionType.None) {
                                executeAction(context, config.singlePressAction)
                            }
                        }
                        pendingSinglePressRunnable = runnable
                        mainHandler.postDelayed(runnable, 350L)
                    }
                } else {
                    if (config.singlePressAction !is ActionType.None) {
                        executeAction(context, config.singlePressAction)
                    }
                }
                return true
            }
        }

        return false
    }

    /**
     * Executes the target action directly from the manager.
     */
    fun executeAction(context: Context, action: ActionType) {
        when (action) {
            is ActionType.None -> {}
            is ActionType.LaunchApp -> {
                try {
                    context.packageManager.getLaunchIntentForPackage(action.packageName)?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch app ${action.packageName}", e)
                }
            }
            is ActionType.OpenWidgetsPage -> {
                try {
                    val intent = Intent(context, one.crinklecod.mochibun.ui.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("OPEN_PAGE", 0)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open widgets page", e)
                }
            }
            is ActionType.OpenNotificationsPage -> {
                try {
                    val intent = Intent(context, one.crinklecod.mochibun.ui.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("OPEN_PAGE", 2)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open notifications page", e)
                }
            }
            is ActionType.GlobalBack -> {
                // Without root, the optional accessibility service can press Back for us
                val handled = !RootShell.isSuBinaryPresent() && MochibunAccessibilityService.goBack()
                if (!handled) {
                    // Sent via root touch injector if available
                    one.crinklecod.mochibun.core.system.RootTouchInjector.keyAsync(KeyEvent.KEYCODE_BACK)
                }
            }
            is ActionType.GlobalRecents -> {
                handleRecentsAction(context)
            }
            is ActionType.OpenRecentsSystem -> {
                executeRootRecents(context)
            }
            is ActionType.GlobalNotifications -> {
                // Status bar expansion; the optional accessibility service covers ROMs that refuse it
                val expanded = try {
                    val sbs = context.getSystemService("statusbar")
                    val method = sbs?.javaClass?.getMethod("expandNotificationsPanel")
                    method?.invoke(sbs)
                    method != null
                } catch (_: Exception) {
                    false
                }
                if (!expanded) MochibunAccessibilityService.openNotifications()
            }
            is ActionType.ToggleFlashlight -> {
                TorchManager.toggleTorch(context)
            }
        }
    }

    private fun handleRecentsAction(context: Context) {
        val useFallback = isRootRecentsFallbackEnabled(context)
        if (useFallback) {
            Log.d(TAG, "Recents action: attempting root-shell fallback")
            executeRootRecents(context)
        } else {
            Log.d(TAG, "Recents action: using native implementation")
            executeNativeRecents(context)
        }
    }

    private fun executeNativeRecents(context: Context) {
        if (RecentsActivity.shouldThrottleLaunch()) {
            Log.d(TAG, "Throttling duplicate native Recents launch")
            return
        }
        try {
            val intent = Intent(context, RecentsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            Log.d(TAG, "Successfully started native RecentsActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch native RecentsActivity", e)
            // Last resort without root: the system Recents via the optional accessibility service
            MochibunAccessibilityService.openRecents()
        }
    }

    private fun executeRootRecents(context: Context) {
        if (RecentsActivity.shouldThrottleLaunch()) {
            Log.d(TAG, "Throttling duplicate root Recents launch")
            return
        }
        scope.launch {
            var succeeded = false
            if (RootShell.isSuBinaryPresent()) {
                val result = RootShell.execute("am start -n one.crinklecod.mochibun/com.android.quickstep.RecentsActivity")
                val hasError = !result.isSuccess ||
                        result.stdout.any { it.startsWith("Error:") } ||
                        result.stderr.any { it.startsWith("Error:") }
                if (!hasError) {
                    succeeded = true
                    Log.d(TAG, "Successfully started RecentsActivity via root shell")
                } else {
                    val errDetails = (result.stderr + result.stdout).joinToString("; ").ifBlank { "Exit code ${result.exitCode}" }
                    Log.w(TAG, "Failed to launch RecentsActivity via root shell: $errDetails")
                }
            } else {
                Log.w(TAG, "su binary not available for root recents")
            }
            if (!succeeded) {
                withContext(Dispatchers.Main) {
                    executeNativeRecents(context)
                }
            }
        }
    }

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
        reloadActiveConfig(context)
    }

    /**
     * Reads configured High-Level Action for a physical key.
     * PhysicalKey.APP_SWITCH defaults to ActionType.GlobalRecents.
     */
    fun getButtonActionConfig(context: Context, key: PhysicalKey): ButtonActionConfig {
        val prefs = getPrefs(context)
        val defaultSingle = when (key) {
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
     */
    fun saveButtonActionConfig(context: Context, key: PhysicalKey, config: ButtonActionConfig) {
        getPrefs(context).edit()
            .putString("action_single_${key.scancode}", config.singlePressAction.serialize())
            .putString("action_double_${key.scancode}", config.doublePressAction.serialize())
            .putString("action_long_${key.scancode}", config.longPressAction.serialize())
            .apply()

        reloadActiveConfig(context)
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
     * Reads the configured [ButtonActionConfig] for ANY physical button.
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

        reloadActiveConfig(context)
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

        reloadActiveConfig(context)
        return defaultConfig
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
                badges.add(PreferencesManager.formatKeyBadge(key.defaultKeyCode, key.scancode))
            }
        }
        val arbitrary = getAllArbitraryKeyActionConfigs(context)
        for ((kc, cfg) in arbitrary.entries.sortedBy { it.key }) {
            val def = getDefaultActionConfigForKey(kc, 0)
            if (cfg != def && cfg.hasAnyAction()) {
                badges.add(PreferencesManager.formatKeyBadge(kc, 0))
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
            val disableCheck = RootShell.execute("[ -f $MODULE_DISABLE_FILE ] && echo 'DISABLED'")
            if (disableCheck.stdout.any { it.contains("DISABLED") }) {
                return@withContext StaticModuleStatus.Disabled
            }

            val moduleCheck = RootShell.execute("[ -f $TARGET_KL_VENDOR ] && echo 'STAGED'")
            val isStaged = moduleCheck.stdout.any { it.contains("STAGED") }
            if (!isStaged) {
                return@withContext StaticModuleStatus.NotInstalled
            }

            val liveCheck = RootShell.execute("[ -f $LIVE_KL_VENDOR ] && echo 'ACTIVE'")
            val isActive = liveCheck.stdout.any { it.contains("ACTIVE") }

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
