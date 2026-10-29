package com.crinklecodone.mochibun.core.system

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.service.MochibunAccessibilityService
import com.crinklecodone.mochibun.service.VirtualCursorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class WatchdogResult {
    object Healthy : WatchdogResult()
    data class Healed(val message: String) : WatchdogResult()
    data class Failed(val reason: String) : WatchdogResult()
    object Disabled : WatchdogResult()
    object NoRoot : WatchdogResult()
}

/**
 * Privileged watchdog coordinator that monitors Mochibun's Accessibility Services.
 *
 * On aggressive low-RAM / Android Go OEM firmware (e.g. MediaTek MT6761 on the AT-M120),
 * background services can occasionally be terminated or disabled after long sleep periods.
 *
 * When enabled, the watchdog detects inactive services and uses Magisk Root privileges
 * to silently re-register and re-bind both [MochibunAccessibilityService] and [VirtualCursorService]
 * without requiring manual user intervention in Android System Settings.
 */
object RootAccessibilityWatchdog : ClamshellListener {

    private const val TAG = "RootA11yWatchdog"

    const val SERVICE_A11Y = "com.crinklecodone.mochibun/com.crinklecodone.mochibun.service.MochibunAccessibilityService"
    const val SERVICE_CURSOR = "com.crinklecodone.mochibun/com.crinklecodone.mochibun.service.VirtualCursorService"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastCheckTimeMs = 0L
    private const val MIN_CHECK_INTERVAL_MS = 10_000L // 10-second debounce between auto checks

    private var appContext: Context? = null
    private var isInitialized = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (isInitialized) return
        isInitialized = true
        ClamshellManager.addListener(this)
        checkAsync(context, force = false)
    }

    override fun onDisplayWake() {
        appContext?.let { checkAsync(it, force = false) }
    }

    /**
     * Dispatches an asynchronous health check on [Dispatchers.IO].
     */
    fun checkAsync(context: Context, force: Boolean = false, onComplete: ((WatchdogResult) -> Unit)? = null) {
        scope.launch {
            val result = checkAndHeal(context, force)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(result)
            }
        }
    }

    /**
     * Evaluates whether both services are running and present in [Settings.Secure].
     * If degraded and root is available, applies self-healing commands.
     */
    suspend fun checkAndHeal(context: Context, force: Boolean = false): WatchdogResult = withContext(Dispatchers.IO) {
        if (!PreferencesManager.isRootA11yWatchdogEnabled(context)) {
            return@withContext WatchdogResult.Disabled
        }

        val now = System.currentTimeMillis()
        if (!force && (now - lastCheckTimeMs < MIN_CHECK_INTERVAL_MS)) {
            return@withContext WatchdogResult.Healthy
        }
        lastCheckTimeMs = now

        val remapRunning = MochibunAccessibilityService.isServiceRunning
        val cursorRunning = VirtualCursorService.isServiceRunning

        val contentResolver = context.contentResolver
        val enabledServices = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        } catch (_: Exception) {
            ""
        }
        val isA11yGloballyEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }

        val currentServices = enabledServices.split(':').filter { it.isNotBlank() }.toSet()
        val hasRemapInSettings = currentServices.contains(SERVICE_A11Y)
        val hasCursorInSettings = currentServices.contains(SERVICE_CURSOR)

        // If both services are running in memory, listed in Settings.Secure, and accessibility is globally enabled:
        if (remapRunning && cursorRunning && hasRemapInSettings && hasCursorInSettings && isA11yGloballyEnabled) {
            return@withContext WatchdogResult.Healthy
        }

        if (!RootShell.isSuBinaryPresent()) {
            Log.w(TAG, "Accessibility service missing or dropped, but su binary not present. Cannot auto-heal.")
            return@withContext WatchdogResult.NoRoot
        }

        Log.i(TAG, "Watchdog detected inactive service. Healing... (remapRunning=$remapRunning, cursorRunning=$cursorRunning, inSettings=$hasRemapInSettings/$hasCursorInSettings)")

        try {
            val mergedServices = (currentServices + SERVICE_A11Y + SERVICE_CURSOR).joinToString(":")
            val grantCmd = "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null"

            val needKick = (hasRemapInSettings && !remapRunning) || (hasCursorInSettings && !cursorRunning)
            val cmds = mutableListOf<String>()
            cmds.add(grantCmd)

            if (needKick) {
                val intermediate = currentServices.filter { it != SERVICE_A11Y && it != SERVICE_CURSOR }.joinToString(":")
                cmds.add("settings put secure enabled_accessibility_services '$intermediate'")
                cmds.add("sleep 0.1")
            }

            cmds.add("settings put secure enabled_accessibility_services '$mergedServices'")
            cmds.add("settings put secure accessibility_enabled 1")

            val result = RootShell.execute(cmds.joinToString(" && "))
            if (result.isSuccess) {
                Log.i(TAG, "Successfully auto-healed Mochibun accessibility services via Root.")
                WatchdogResult.Healed("Services restored successfully")
            } else {
                val err = result.stderr.joinToString(" ").ifBlank { "Exit code ${result.exitCode}" }
                Log.e(TAG, "Root execution failed while healing accessibility services: $err")
                WatchdogResult.Failed(err)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while healing accessibility services", e)
            WatchdogResult.Failed(e.message ?: "Unknown error")
        }
    }
}
