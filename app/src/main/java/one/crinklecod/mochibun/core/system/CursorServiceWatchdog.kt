package one.crinklecod.mochibun.core.system

import android.content.Context
import android.util.Log
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.service.VirtualCursorService
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
}

/**
 * Keeps the [VirtualCursorService] foreground service alive.
 *
 * On aggressive low-RAM OEM firmware (e.g. MediaTek MT6761 on the AT-M120), background services
 * can occasionally be terminated after long sleep periods. When enabled, the watchdog checks on
 * launch, on every launcher resume and on display wake, and restarts the service if it is gone.
 * No root is needed: restarting our own service is a plain startForegroundService call.
 */
object CursorServiceWatchdog : ClamshellListener {

    private const val TAG = "CursorServiceWatchdog"

    const val SERVICE_CURSOR = "one.crinklecod.mochibun/one.crinklecod.mochibun.service.VirtualCursorService"

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
     * Returns [WatchdogResult.Healthy] when [VirtualCursorService] is running; otherwise restarts it.
     */
    suspend fun checkAndHeal(context: Context, force: Boolean = false): WatchdogResult = withContext(Dispatchers.IO) {
        if (!PreferencesManager.isCursorWatchdogEnabled(context)) {
            return@withContext WatchdogResult.Disabled
        }

        val now = System.currentTimeMillis()
        if (!force && (now - lastCheckTimeMs < MIN_CHECK_INTERVAL_MS)) {
            return@withContext WatchdogResult.Healthy
        }
        lastCheckTimeMs = now

        val cursorRunning = VirtualCursorService.isServiceRunning
        if (cursorRunning) {
            return@withContext WatchdogResult.Healthy
        }

        Log.i(TAG, "Watchdog detected inactive VirtualCursorService. Restarting...")
        try {
            VirtualCursorService.start(context)
            WatchdogResult.Healed("VirtualCursorService restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VirtualCursorService", e)
            WatchdogResult.Failed(e.message ?: "Unknown error")
        }
    }
}
