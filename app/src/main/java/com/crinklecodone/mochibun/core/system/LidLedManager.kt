package com.crinklecodone.mochibun.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.NotificationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Exterior Clamshell Notification & Charging LED Manager for the Chinoe AT-M120 (Awinic AW22xxx driver).
 *
 * Coordinates external lid LED illumination and customizable RGB colors when the flip folder is closed:
 * - Pulses exterior breathing LED on incoming unread notifications in Sakura Pink, Lavender Purple, etc.
 * - Lights exterior charging indicator when connected to AC/USB power.
 * - Immediately turns off all exterior illumination when the clamshell opens to conserve battery.
 */
object LidLedManager : ClamshellListener {

    private const val TAG = "LidLedManager"

    // Canonical sysfs paths exposed by the AT-M120 kernel driver
    const val LED_PATH_NOTIFICATION = "/sys/class/leds/aw22xxx_led/case_notification"
    const val LED_PATH_CHARGE = "/sys/class/leds/aw22xxx_led/case_charge"
    const val LED_PATH_MESSAGE = "/sys/class/leds/aw22xxx_led/case_message"
    const val LED_PATH_CALL = "/sys/class/leds/aw22xxx_led/case_call"
    const val LED_PATH_TURNOFF = "/sys/class/leds/aw22xxx_led/case_turnonoff"
    const val LED_PATH_RGB = "/sys/class/leds/aw22xxx_led/rgb"
    const val LED_PATH_TASK0 = "/sys/class/leds/aw22xxx_led/task0"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null
    private var preferencesJob: Job? = null
    private var colorJob: Job? = null

    private var appContext: Context? = null
    private var isInitialized = false

    @Volatile
    var isChargingState: Boolean = false
        private set

    @Volatile
    var notificationLedActive: Boolean = false
        private set

    @Volatile
    var chargingLedActive: Boolean = false
        private set

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isChargingState = true
                    updateLedState()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isChargingState = false
                    updateLedState()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    if (isChargingState != charging) {
                        isChargingState = charging
                        updateLedState()
                    }
                }
            }
        }
    }

    /**
     * Maps user color keys to 24-bit hex RGB values for AW22xxx registers.
     */
    fun getHexColor(colorKey: String): String {
        return when (colorKey) {
            "purple" -> "0x9370db" // Lavender Purple
            "white" -> "0xffffff"  // Pure White
            "green" -> "0x00ff7f"  // Retro Keitai Green
            else -> "0xff1493"     // Sakura Pink (Default)
        }
    }

    /**
     * Programs the 9 RGB channels on the AW22xxx controller and commits via task0.
     */
    fun applyLidColor(colorKey: String) {
        val hex = getHexColor(colorKey)
        val cmd = buildString {
            for (i in 0..8) {
                append("echo \"$i $hex\" > $LED_PATH_RGB; ")
            }
            append("echo 1 > $LED_PATH_TASK0")
        }
        RootShell.submit(cmd)
        Log.d(TAG, "Applied lid LED color: $colorKey ($hex)")
    }

    /**
     * Initializes the Lid LED Manager with application context.
     * Safe to call multiple times; idempotently registers once per process.
     */
    fun init(context: Context) {
        if (isInitialized) return
        val app = context.applicationContext
        appContext = app

        ClamshellManager.addListener(this)

        // Query initial charging state
        try {
            val batteryStatus = app.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isChargingState = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading initial battery status", e)
        }

        // Register dynamic power receiver
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            app.registerReceiver(powerReceiver, powerFilter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering power receiver", e)
        }

        // Listen for notification changes
        notificationJob?.cancel()
        notificationJob = scope.launch {
            NotificationTracker.notifications.collectLatest {
                updateLedState()
            }
        }

        // Listen for preference changes
        preferencesJob?.cancel()
        preferencesJob = scope.launch {
            PreferencesManager.isLidLedEnabled.collectLatest {
                updateLedState()
            }
        }

        // Listen for color changes
        colorJob?.cancel()
        colorJob = scope.launch {
            PreferencesManager.lidLedColor.collectLatest { color ->
                applyLidColor(color)
            }
        }

        // Apply baseline color on startup
        applyLidColor(PreferencesManager.getLidLedColor(app))

        isInitialized = true
        Log.d(TAG, "LidLedManager initialized (initial charging=$isChargingState)")
    }

    override fun onLidClosed() {
        Log.d(TAG, "Clamshell closed: updating lid LED states")
        updateLedState()
    }

    override fun onLidOpened() {
        Log.d(TAG, "Clamshell opened: turning off exterior LEDs")
        clearAllLeds()
    }

    /**
     * Evaluates current state (lid closed/open, notifications present, charging)
     * and sets the hardware LED driver state via privileged root shell daemon.
     */
    @Synchronized
    fun updateLedState() {
        val context = appContext ?: return
        val isLidClosed = ClamshellManager.isLidClosed(context)
        val isEnabled = PreferencesManager.isLidLedEnabled(context)

        if (!isLidClosed || !isEnabled) {
            clearAllLeds()
            return
        }

        val hasNotifications = NotificationTracker.notifications.value.isNotEmpty()
        val shouldLightNotification = hasNotifications
        val shouldLightCharging = isChargingState && PreferencesManager.isLidLedChargingEnabled(context)

        // Make sure chosen color is committed
        val colorKey = PreferencesManager.getLidLedColor(context)
        applyLidColor(colorKey)

        // Update notification LED state
        if (shouldLightNotification != notificationLedActive) {
            notificationLedActive = shouldLightNotification
            val value = if (shouldLightNotification) "1" else "0"
            RootShell.submit("echo $value > $LED_PATH_NOTIFICATION")
            Log.d(TAG, "Notification LED set to $value")
        }

        // Update charging LED state
        if (shouldLightCharging != chargingLedActive) {
            chargingLedActive = shouldLightCharging
            val value = if (shouldLightCharging) "1" else "0"
            RootShell.submit("echo $value > $LED_PATH_CHARGE")
            Log.d(TAG, "Charging LED set to $value")
        }
    }

    /**
     * Shuts down all exterior LEDs immediately.
     */
    @Synchronized
    fun clearAllLeds() {
        if (notificationLedActive || chargingLedActive) {
            notificationLedActive = false
            chargingLedActive = false
            RootShell.submit("echo 0 > $LED_PATH_NOTIFICATION; echo 0 > $LED_PATH_CHARGE; echo 0 > $LED_PATH_TURNOFF")
            Log.d(TAG, "Cleared all exterior lid LEDs")
        }
    }

    @VisibleForTesting
    internal fun setMockChargingState(charging: Boolean) {
        isChargingState = charging
    }
}
