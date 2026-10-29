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
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Exterior Clamshell Notification & Charging LED Manager for the Chinoe AT-M120 (Awinic AW22xxx driver).
 *
 * Coordinates external lid LED illumination, alternating shimmer animations, and custom 24-bit RGB colors:
 * - Direct zero-overhead hardware register execution via `/sys/class/leds/aw22xxx_led/reg`.
 * - Bypasses buggy vendor kernel workqueues (`brightness`, `effect`, `case_turnonoff`) that force the stock rainbow.
 * - Controls the 5 physical lid windows mapped to AW22127 channels:
 *     Window 1: Channel 0
 *     Window 2: Channel 1
 *     Window 3: Channel 3
 *     Window 4: Channel 4
 *     Window 5: Channel 6
 * - Alternating Keitai Shimmer pattern: `1 0 1 0 1` (Windows 1, 3, 5) <-> `0 1 0 1 0` (Windows 2, 4).
 * - Solid illumination for clamshell closed charging.
 * - Clean hardware shutoff without triggering vendor workqueue rainbow loops.
 */
object LidLedManager : ClamshellListener {

    private const val TAG = "LidLedManager"

    // Canonical sysfs paths exposed by the AT-M120 kernel driver
    const val LED_PATH_REG = "/sys/class/leds/aw22xxx_led/reg"
    const val LED_PATH_NOTIFICATION = "/sys/class/leds/aw22xxx_led/case_notification"
    const val LED_PATH_CHARGE = "/sys/class/leds/aw22xxx_led/case_charge"
    const val LED_PATH_MESSAGE = "/sys/class/leds/aw22xxx_led/case_message"
    const val LED_PATH_CALL = "/sys/class/leds/aw22xxx_led/case_call"
    const val LED_PATH_TURNOFF = "/sys/class/leds/aw22xxx_led/case_turnonoff"
    const val LED_PATH_FACTORY_TEST = "/sys/class/leds/aw22xxx_led/case_factory_test"
    const val LED_PATH_RGB = "/sys/class/leds/aw22xxx_led/rgb"
    const val LED_PATH_TASK0 = "/sys/class/leds/aw22xxx_led/task0"
    const val LED_PATH_EFFECT = "/sys/class/leds/aw22xxx_led/effect"
    const val LED_PATH_BRIGHTNESS = "/sys/class/leds/aw22xxx_led/brightness"

    // Channel mapping for the 5 physical exterior lid windows
    val CHANNELS_10101 = setOf(0, 3, 6) // Windows 1, 3, 5
    val CHANNELS_01010 = setOf(1, 4)    // Windows 2, 4
    val ALL_WINDOW_CHANNELS = setOf(0, 1, 3, 4, 6) // Windows 1, 2, 3, 4, 5

    data class LedPaletteColor(
        val id: String,
        val label: String,
        val hex: String,
        val colorValue: Long
    )

    val PRESET_COLORS = listOf(
        LedPaletteColor("pink", "Sakura Pink", "0xff1493", 0xFFFF1493),
        LedPaletteColor("baby_pink", "Baby Pink", "0xffb6c1", 0xFFFFB6C1),
        LedPaletteColor("peach", "Coral Peach", "0xff7f50", 0xFFFF7F50),
        LedPaletteColor("amber", "Sunset Amber", "0xff8c00", 0xFFFF8C00),
        LedPaletteColor("lemon", "Lemon Yellow", "0xfff44f", 0xFFFFF44F),
        LedPaletteColor("mint", "Pastel Mint", "0xb7f4d8", 0xFFB7F4D8),
        LedPaletteColor("green", "Keitai Green", "0x00ff7f", 0xFF00FF7F),
        LedPaletteColor("teal", "Ocean Teal", "0x00ced1", 0xFF00CED1),
        LedPaletteColor("sky", "Sky Blue", "0x00bfff", 0xFF00BFFF),
        LedPaletteColor("blue", "Ocean Blue", "0x1e90ff", 0xFF1E90FF),
        LedPaletteColor("purple", "Lavender Purple", "0x9370db", 0xFF9370DB),
        LedPaletteColor("violet", "Royal Violet", "0x8a2be2", 0xFF8A2BE2),
        LedPaletteColor("magenta", "Hot Magenta", "0xff00ff", 0xFFFF00FF),
        LedPaletteColor("candle", "Warm Candle", "0xffe4b5", 0xFFFFE4B5),
        LedPaletteColor("white", "Pure White", "0xffffff", 0xFFFFFFFF)
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null
    private var preferencesJob: Job? = null
    private var autoStopJob: Job? = null
    private var animationJob: Job? = null

    private var appContext: Context? = null
    private var isInitialized = false

    @Volatile
    var isTestRunning: Boolean = false
        private set

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
     * Maps user color keys or hex strings to 24-bit hex RGB values.
     */
    fun getHexColor(colorKey: String): String {
        val preset = PRESET_COLORS.firstOrNull { it.id.equals(colorKey, ignoreCase = true) }
        if (preset != null) return preset.hex

        val clean = colorKey.removePrefix("0x").removePrefix("#")
        if (clean.length == 6 && clean.toLongOrNull(16) != null) {
            return "0x${clean.lowercase()}"
        }
        return "0xff1493"
    }

    /**
     * Gets display label for a color key or custom hex.
     */
    fun getColorLabel(colorKey: String): String {
        val preset = PRESET_COLORS.firstOrNull { it.id.equals(colorKey, ignoreCase = true) }
        if (preset != null) return preset.label

        val clean = colorKey.removePrefix("0x").removePrefix("#")
        if (clean.length == 6 && clean.toLongOrNull(16) != null) {
            return "#${clean.uppercase()}"
        }
        return "Sakura Pink"
    }

    /**
     * Parses a hex color string into 8-bit R, G, B integers.
     */
    fun parseRgb(colorHex: String): Triple<Int, Int, Int> {
        val clean = colorHex.removePrefix("0x").removePrefix("#")
        val colorInt = clean.toIntOrNull(16) ?: 0xff1493
        val r = (colorInt shr 16) and 0xff
        val g = (colorInt shr 8) and 0xff
        val b = colorInt and 0xff
        return Triple(r, g, b)
    }

    /**
     * Generates a direct register programming script to light up specific channels in RGB.
     * All non-active channels are explicitly set to 0x00 (off).
     */
    @VisibleForTesting
    internal fun buildWindowPatternScript(activeChannels: Set<Int>, r: Int, g: Int, b: Int): String {
        return buildString {
            append("echo \"0xff 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x05 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x04 0x01\" > $LED_PATH_REG\n")
            append("echo \"0x02 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x02 0x01\" > $LED_PATH_REG\n")
            append("echo \"0x04 0x01\" > $LED_PATH_REG\n")
            append("echo \"0x0c 0x00\" > $LED_PATH_REG\n")
            append("echo \"0xff 0x01\" > $LED_PATH_REG\n")
            append("echo \"0x00 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x01 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x02 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x03 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x04 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x05 0x03\" > $LED_PATH_REG\n")
            append("echo \"0x06 0x09\" > $LED_PATH_REG\n")
            append("echo \"0x0b 0x00\" > $LED_PATH_REG\n")

            for (ch in 0..8) {
                val rReg = 0x10 + ch * 3
                val gReg = rReg + 1
                val bReg = rReg + 2
                if (ch in activeChannels) {
                    append("echo \"0x%02x 0x%02x\" > $LED_PATH_REG\n".format(rReg, r))
                    append("echo \"0x%02x 0x%02x\" > $LED_PATH_REG\n".format(gReg, g))
                    append("echo \"0x%02x 0x%02x\" > $LED_PATH_REG\n".format(bReg, b))
                } else {
                    append("echo \"0x%02x 0x00\" > $LED_PATH_REG\n".format(rReg))
                    append("echo \"0x%02x 0x00\" > $LED_PATH_REG\n".format(gReg))
                    append("echo \"0x%02x 0x00\" > $LED_PATH_REG\n".format(bReg))
                }
            }

            append("echo \"0x2b 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x2c 0xff\" > $LED_PATH_REG\n")
            append("echo \"0xff 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x05 0x02\" > $LED_PATH_REG\n")
            append("echo \"0x04 0x03\" > $LED_PATH_REG\n")
        }
    }

    /**
     * Builds the script to illuminate all 5 physical exterior windows solid in custom RGB.
     */
    @VisibleForTesting
    internal fun buildSolidScript(r: Int, g: Int, b: Int): String {
        return buildWindowPatternScript(ALL_WINDOW_CHANNELS, r, g, b)
    }

    /**
     * Builds the clean hardware shutoff script via register writes without waking vendor workqueues.
     */
    @VisibleForTesting
    internal fun buildOffScript(): String {
        return buildString {
            append("echo \"0xff 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x05 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x04 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x02 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x01 0x55\" > $LED_PATH_REG\n")
            append("echo \"0xff 0x01\" > $LED_PATH_REG\n")
            for (r in 0x10..0x2a) {
                append("echo \"0x%02x 0x00\" > $LED_PATH_REG\n".format(r))
            }
            append("echo \"0xff 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x05 0x00\" > $LED_PATH_REG\n")
            append("echo \"0x04 0x00\" > $LED_PATH_REG\n")
        }
    }

    /**
     * Sets all 5 exterior lid windows to steady solid illumination in the chosen color.
     */
    fun setSolidColor(colorHex: String) {
        animationJob?.cancel()
        animationJob = null
        val (r, g, b) = parseRgb(colorHex)
        val script = buildSolidScript(r, g, b)
        Shell.cmd(script).submit()
        Log.d(TAG, "Activated solid lid color ($colorHex)")
    }

    /**
     * Starts the alternating Keitai shimmer animation (10101 <-> 01010) in the chosen color.
     */
    fun startNotificationShimmer(colorHex: String, timeoutMs: Long? = 10_000L) {
        animationJob?.cancel()
        animationJob = null
        notificationLedActive = true
        val (r, g, b) = parseRgb(colorHex)
        val pat10101 = buildWindowPatternScript(CHANNELS_10101, r, g, b)
        val pat01010 = buildWindowPatternScript(CHANNELS_01010, r, g, b)

        animationJob = scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var step = 0
            while (notificationLedActive) {
                if (timeoutMs != null && timeoutMs > 0 && System.currentTimeMillis() - startTime >= timeoutMs) {
                    break
                }
                val cmd = if (step % 2 == 0) pat10101 else pat01010
                Shell.cmd(cmd).exec()
                delay(800L)
                step++
            }
            if (!isChargingState && !isTestRunning) {
                stopLeds()
            }
        }
        Log.d(TAG, "Started notification shimmer ($colorHex, timeoutMs=$timeoutMs)")
    }

    /**
     * Shuts off all lid channels cleanly without waking vendor rainbow workqueues.
     */
    @Synchronized
    fun stopLeds() {
        autoStopJob?.cancel()
        autoStopJob = null
        animationJob?.cancel()
        animationJob = null
        isTestRunning = false
        notificationLedActive = false
        chargingLedActive = false
        val offScript = buildOffScript()
        Shell.cmd(offScript).submit()
        Log.d(TAG, "Stopped all exterior LEDs cleanly via reg")
    }

    /**
     * Compatibility alias for stopLeds.
     */
    @Synchronized
    fun clearAllLeds() {
        stopLeds()
    }

    /**
     * Generic setLidColor method supporting solid and pulse modes.
     */
    fun setLidColor(
        colorHex: String,
        effectMode: Int = 2,
        durationMs: Long? = 4000L,
        isTest: Boolean = false
    ) {
        if (effectMode == 0) {
            stopLeds()
            return
        }
        if (isTest) {
            startPreviewTest(colorHex, delayMs = 0L, durationMs = durationMs ?: 8000L)
            return
        }
        if (effectMode == 1) {
            setSolidColor(colorHex)
        } else {
            startNotificationShimmer(colorHex, timeoutMs = durationMs)
        }
    }

    /**
     * Programs the lid LED shimmer with a countdown delay so the user can close the flip.
     */
    fun setLidColorWithDelay(
        colorHex: String = "0xff1493",
        effectMode: Int = 2,
        delayMs: Long = 2000L,
        durationMs: Long? = 8000L,
        isTest: Boolean = true
    ) {
        startPreviewTest(colorHex, delayMs = delayMs, durationMs = durationMs ?: 8000L)
    }

    /**
     * Interactive preview test for settings screen: countdown delay followed by alternating shimmer.
     */
    fun startPreviewTest(
        colorHex: String = "0xff1493",
        delayMs: Long = 2000L,
        durationMs: Long = 8000L
    ) {
        autoStopJob?.cancel()
        animationJob?.cancel()
        isTestRunning = true

        autoStopJob = scope.launch(Dispatchers.IO) {
            if (delayMs > 0) {
                delay(delayMs)
            }
            val (r, g, b) = parseRgb(colorHex)
            val pat10101 = buildWindowPatternScript(CHANNELS_10101, r, g, b)
            val pat01010 = buildWindowPatternScript(CHANNELS_01010, r, g, b)
            val endTime = System.currentTimeMillis() + durationMs
            var step = 0
            while (System.currentTimeMillis() < endTime && isTestRunning) {
                val cmd = if (step % 2 == 0) pat10101 else pat01010
                Shell.cmd(cmd).exec()
                delay(800L)
                step++
            }
            isTestRunning = false
            stopLeds()
        }
        Log.d(TAG, "Started preview test ($colorHex, delayMs=$delayMs, durationMs=$durationMs)")
    }

    /**
     * Applies user-selected palette color to exterior channels.
     */
    fun applyLidColor(colorKey: String) {
        val hex = getHexColor(colorKey)
        setSolidColor(hex)
    }

    /**
     * Triggers notification shimmer across exterior lid LEDs.
     */
    fun triggerNotificationPulse(autoStopTimeoutMs: Long = 10_000L) {
        val colorKey = appContext?.let { PreferencesManager.getLidLedColor(it) } ?: "pink"
        val hex = getHexColor(colorKey)
        startNotificationShimmer(hex, timeoutMs = autoStopTimeoutMs)
    }

    /**
     * Triggers notification shimmer after delay.
     */
    fun triggerNotificationWithDelay(delaySeconds: Int = 2, autoStopTimeoutMs: Long = 10_000L) {
        val colorKey = appContext?.let { PreferencesManager.getLidLedColor(it) } ?: "pink"
        val hex = getHexColor(colorKey)
        startPreviewTest(
            colorHex = hex,
            delayMs = delaySeconds * 1000L,
            durationMs = autoStopTimeoutMs
        )
    }

    /**
     * Factory sweep test routine (OEM hardware diagnostic).
     */
    fun triggerFactorySweep(autoStopTimeoutMs: Long = 5_000L) {
        Shell.cmd("echo 1 > $LED_PATH_FACTORY_TEST").submit()
        if (autoStopTimeoutMs > 0) {
            scope.launch {
                delay(autoStopTimeoutMs)
                stopLeds()
            }
        }
    }

    /**
     * Executes test patterns.
     */
    fun fireTestPattern(pattern: String = "notification", autoStopTimeoutMs: Long = 5_000L) {
        if (pattern == "factory_test") {
            triggerFactorySweep(autoStopTimeoutMs)
        } else {
            triggerNotificationPulse(autoStopTimeoutMs)
        }
    }

    /**
     * Initializes the Lid LED Manager with application context.
     * Idempotently registers once per process.
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

        isInitialized = true
        Log.d(TAG, "LidLedManager initialized (initial charging=$isChargingState)")
    }

    override fun onLidClosed() {
        if (isTestRunning) {
            Log.d(TAG, "Clamshell closed during active preview test; preserving test")
            return
        }
        Log.d(TAG, "Clamshell closed: updating lid LED states")
        updateLedState()
    }

    override fun onLidOpened() {
        if (isTestRunning) {
            Log.d(TAG, "Clamshell opened during active preview test; preserving test until timeout")
            return
        }
        Log.d(TAG, "Clamshell opened: turning off exterior LEDs")
        stopLeds()
    }

    /**
     * Evaluates current state (lid closed/open, notifications present, charging)
     * and sets the hardware LED driver state via privileged root shell daemon.
     */
    @Synchronized
    fun updateLedState() {
        if (isTestRunning) {
            Log.d(TAG, "updateLedState skipped: preview test is active")
            return
        }
        val context = appContext ?: return
        val isLidClosed = ClamshellManager.isLidClosed(context)
        val isEnabled = PreferencesManager.isLidLedEnabled(context)

        if (!isLidClosed || !isEnabled) {
            stopLeds()
            return
        }

        val hasNotifications = NotificationTracker.notifications.value.isNotEmpty()
        val shouldLightCharging = isChargingState && PreferencesManager.isLidLedChargingEnabled(context)

        val colorKey = PreferencesManager.getLidLedColor(context)
        val hex = getHexColor(colorKey)

        if (hasNotifications) {
            if (!notificationLedActive) {
                startNotificationShimmer(hex, timeoutMs = null)
            }
        } else if (shouldLightCharging) {
            notificationLedActive = false
            if (!chargingLedActive) {
                chargingLedActive = true
                setSolidColor(hex)
            }
        } else {
            stopLeds()
        }
    }

    @VisibleForTesting
    internal fun setMockChargingState(charging: Boolean) {
        isChargingState = charging
    }
}
