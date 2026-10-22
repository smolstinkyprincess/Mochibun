package com.crinklecodone.mochibun.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.android.quickstep.RecentsActivity
import com.crinklecodone.mochibun.core.system.ClamshellListener
import com.crinklecodone.mochibun.core.system.ClamshellManager
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.data.model.ActionType
import com.crinklecodone.mochibun.data.model.ButtonActionConfig
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MochibunAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MochibunAccessibility"
        const val ACTION_RELOAD_CONFIG="com.crinklecodone.mochibun.RELOAD_CONFIG"
        const val PREFS_NAME = "hardware_remap_preferences"

        @Volatile
        var isServiceRunning = false
            private set

        private var instance: MochibunAccessibilityService? = null

        fun reloadActiveConfig(context: Context) {
            instance?.loadConfiguration()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    // In-memory mapping of Android KeyCode -> Action Configuration
    private val activeMappings = mutableMapOf<Int, ButtonActionConfig>()

    // State tracking for double-tap and long-press per keycode
    private var lastTapTime = 0L
    private var lastTapKeyCode = -1
    private var pendingSinglePressRunnable: Runnable? = null
    private var pendingLongPressRunnable: Runnable? = null
    private var longPressFired = false

    private var isTorchOn = false

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadConfiguration()
    }

    private val clamshellListener = object : ClamshellListener {
        override fun onLidClosed() {
            clearPendingHandlers()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true

        ClamshellManager.init(this)
        ClamshellManager.addListener(clamshellListener)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        loadConfiguration()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ClamshellManager.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        ClamshellManager.removeListener(clamshellListener)
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        clearPendingHandlers()
        serviceScope.cancel()
        isServiceRunning = false
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not observing UI events to maintain lightweight zero-overhead performance
    }

    override fun onInterrupt() {
        clearPendingHandlers()
    }

    fun loadConfiguration() {
        activeMappings.clear()
        for (key in PhysicalKey.entries) {
            val config = HardwareRemapManager.getButtonActionConfig(this, key)
            if (config.hasAnyAction()) {
                activeMappings[key.defaultKeyCode] = config
                if (key == PhysicalKey.KEY_ENTER) {
                    activeMappings[KeyEvent.KEYCODE_DPAD_CENTER] = config
                }
            }
        }
        // Always ensure APP_SWITCH has a valid default mapping if not present
        if (!activeMappings.containsKey(KeyEvent.KEYCODE_APP_SWITCH)) {
            val appSwitchConfig = HardwareRemapManager.getButtonActionConfig(this, PhysicalKey.APP_SWITCH)
            activeMappings[KeyEvent.KEYCODE_APP_SWITCH] = appSwitchConfig
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Suppress hardware button shortcuts while closed in pocket to prevent accidental triggering
        if (ClamshellManager.isLidClosed(this)) {
            clearPendingHandlers()
            return false
        }

        // Fast lookup from in-memory cache without blocking UI-thread disk I/O
        var config = activeMappings[event.keyCode]
        if (config == null && event.scanCode > 0) {
            val key = PhysicalKey.fromScancode(event.scanCode)
            if (key != null) {
                config = activeMappings[key.defaultKeyCode]
            }
        }
        if (config == null || !config.hasAnyAction()) return false

        Log.d("MochibunA11y", "Intercepted keycode ${event.keyCode}, action: ${event.action}")

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    longPressFired = false
                    if (config.longPressAction !is ActionType.None) {
                        pendingLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        val runnable = Runnable {
                            longPressFired = true
                            executeAction(config.longPressAction)
                        }
                        pendingLongPressRunnable = runnable
                        mainHandler.postDelayed(runnable, 500L)
                    }
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                // Cancel scheduled long-press
                pendingLongPressRunnable?.let {
                    mainHandler.removeCallbacks(it)
                    pendingLongPressRunnable = null
                }

                // If long-press already triggered, do not trigger tap actions
                if (longPressFired) {
                    longPressFired = false
                    return true
                }

                val now = SystemClock.uptimeMillis()

                if (config.doublePressAction !is ActionType.None) {
                    if (lastTapKeyCode == event.keyCode && (now - lastTapTime) < 350L) {
                        // Double-press detected! Cancel single-press and fire double-press
                        pendingSinglePressRunnable?.let {
                            mainHandler.removeCallbacks(it)
                            pendingSinglePressRunnable = null
                        }
                        lastTapTime = 0L
                        lastTapKeyCode = -1
                        executeAction(config.doublePressAction)
                    } else {
                        // First tap of potential double-press
                        lastTapTime = now
                        lastTapKeyCode = event.keyCode
                        pendingSinglePressRunnable?.let { mainHandler.removeCallbacks(it) }

                        val runnable = Runnable {
                            lastTapTime = 0L
                            lastTapKeyCode = -1
                            if (config.singlePressAction !is ActionType.None) {
                                executeAction(config.singlePressAction)
                            }
                        }
                        pendingSinglePressRunnable = runnable
                        mainHandler.postDelayed(runnable, 350L)
                    }
                } else {
                    // No double-press configured; fire single-press immediately
                    if (config.singlePressAction !is ActionType.None) {
                        executeAction(config.singlePressAction)
                    }
                }
                return true
            }
        }

        return false
    }

    private fun executeAction(action: ActionType) {
        when (action) {
            is ActionType.None -> {}
            is ActionType.LaunchApp -> {
                try {
                    packageManager.getLaunchIntentForPackage(action.packageName)?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch app ${action.packageName}", e)
                }
            }
            is ActionType.GlobalBack -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            is ActionType.GlobalRecents -> {
                handleRecentsAction()
            }
            is ActionType.OpenRecentsSystem -> {
                executeRootRecents()
            }
            is ActionType.GlobalNotifications -> {
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            }
            is ActionType.ToggleFlashlight -> {
                toggleTorch()
            }
        }
    }

    /** Toggles the device flashlight for hardware remap shortcut actions. */
    private fun toggleTorch() {
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            Log.e(TAG, "Failed toggling torch", e)
        }
    }

    private fun clearPendingHandlers() {
        pendingSinglePressRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingLongPressRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingSinglePressRunnable = null
        pendingLongPressRunnable = null
        longPressFired = false
        lastTapTime = 0L
        lastTapKeyCode = -1
    }

    private fun handleRecentsAction() {
        val useFallback = HardwareRemapManager.isRootRecentsFallbackEnabled(this)
        if (useFallback) {
            Log.d("MochibunA11y", "Recents action: attempting root-shell fallback")
            executeRootRecents()
        } else {
            Log.d("MochibunA11y", "Recents action: using native implementation")
            executeNativeRecents()
        }
    }

    private fun executeNativeRecents() {
        if (RecentsActivity.shouldThrottleLaunch()) {
            Log.d("MochibunA11y", "Throttling duplicate native Recents launch")
            return
        }
        try {
            val intent = Intent(this, RecentsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            Log.d("MochibunA11y", "Successfully started native RecentsActivity")
        } catch (e: Exception) {
            Log.e("MochibunA11y", "Failed to launch native RecentsActivity, falling back to global action", e)
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    private fun executeRootRecents() {
        if (RecentsActivity.shouldThrottleLaunch()) {
            Log.d("MochibunA11y", "Throttling duplicate root Recents launch")
            return
        }
        serviceScope.launch {
            var succeeded = false
            if (RootShell.isSuBinaryPresent()) {
                val result = RootShell.execute("am start -n com.crinklecodone.mochibun/com.android.quickstep.RecentsActivity")
                val hasError = !result.isSuccess ||
                        result.stdout.any { it.startsWith("Error:") } ||
                        result.stderr.any { it.startsWith("Error:") }
                if (!hasError) {
                    succeeded = true
                    Log.d("MochibunA11y", "Successfully started RecentsActivity via root shell")
                } else {
                    val errDetails = (result.stderr + result.stdout).joinToString("; ").ifBlank { "Exit code ${result.exitCode}" }
                    Log.w("MochibunA11y", "Failed to launch RecentsActivity via root shell: $errDetails")
                }
            } else {
                Log.w("MochibunA11y", "su binary not available for root recents")
            }
            if (!succeeded) {
                withContext(Dispatchers.Main) {
                    executeNativeRecents()
                }
            }
        }
    }
}
