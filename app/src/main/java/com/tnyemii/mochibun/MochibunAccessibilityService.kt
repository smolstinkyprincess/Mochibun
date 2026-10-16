package com.tnyemii.mochibun

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class MochibunAccessibilityService : AccessibilityService() {

    companion object {
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        loadConfiguration()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        clearPendingHandlers()
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
            val single = ActionType.deserialize(prefs.getString("action_single_${key.scancode}", null))
            val double = ActionType.deserialize(prefs.getString("action_double_${key.scancode}", null))
            val long = ActionType.deserialize(prefs.getString("action_long_${key.scancode}", null))

            val config = ButtonActionConfig(single, double, long)
            if (config.hasAnyAction()) {
                activeMappings[key.defaultKeyCode] = config
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Fast lookup with fresh reload fallback if needed
        var config = activeMappings[event.keyCode]
        if (config == null) {
            loadConfiguration()
            config = activeMappings[event.keyCode]
        }
        if (config == null || !config.hasAnyAction()) return false

        android.util.Log.d("MochibunA11y", "Intercepted keycode ${event.keyCode}, action: ${event.action}")

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
                    e.printStackTrace()
                }
            }
            is ActionType.OpenAiChat -> {
                try {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        this.action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("OPEN_PAGE", 2)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            is ActionType.GlobalBack -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            is ActionType.GlobalRecents -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
            is ActionType.GlobalNotifications -> {
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            }
            is ActionType.ToggleFlashlight -> {
                toggleTorch()
            }
        }
    }

    private fun toggleTorch() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            e.printStackTrace()
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
}
