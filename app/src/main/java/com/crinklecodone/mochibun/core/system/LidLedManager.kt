package com.crinklecodone.mochibun.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.crinklecodone.mochibun.data.model.MochibunNotificationItem
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.NotificationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** What the closed lid is showing, highest priority first. */
enum class LidLedMode { CALL, NOTIFICATION, CHARGING, OFF }

/**
 * Exterior Clamshell Notification & Charging LED Manager for the Chinoe AT-M120 (Awinic AW22xxx driver).
 *
 * Coordinates external lid LED illumination, alternating shimmer animations, and custom 24-bit RGB colors:
 * - Direct register programming via `/sys/class/leds/aw22xxx_led/reg`.
 * - Bypasses the vendor `effect` / `task0` path: on this unit `task0` loads the `cfg[effect]` firmware
 *   blob with its colors baked in and ignores the `rgb` buffer (verified on device).
 * - Controls the 5 physical lid windows mapped to AW22127 channels:
 *     Window 1: Channel 0
 *     Window 2: Channel 1
 *     Window 3: Channel 3
 *     Window 4: Channel 4
 *     Window 5: Channel 6
 * - Alternating Keitai Shimmer pattern: `1 0 1 0 1` (Windows 1, 3, 5) <-> `0 1 0 1 0` (Windows 2, 4).
 * - Clean hardware shutoff without triggering vendor workqueue rainbow loops.
 *
 * Priority ladder while the flip is physically closed: incoming call > unread notification >
 * charging > off. The chip has no usable autonomous animation here (the breath program stays steady
 * when written through `reg`), so a shimmer is a bounded loop that runs inside the root shell (no
 * per-frame work in this process) and always ends on a static frame. The chip holds a static frame
 * through SoC suspend with zero CPU:
 *  - Call: fast shimmer for up to [CALL_SHIMMER_CYCLES], then all windows solid.
 *  - Notification: shimmer on each new post, then a steady `1 0 1 0 1` glyph.
 *  - Charging: all windows solid.
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
    const val CRITICAL_BATTERY_PCT = 5
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

    // Shimmer timing. One cycle shows both frames.
    private const val NOTIFICATION_FRAME_SECONDS = "0.8"
    private const val NOTIFICATION_SHIMMER_CYCLES = 6 // ~10 s, matching the old pulse timeout
    private const val CALL_FRAME_SECONDS = "0.4"
    private const val CALL_SHIMMER_CYCLES = 56 // ~45 s, longer than a typical ring

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null
    private var preferencesJob: Job? = null

    private var appContext: Context? = null
    private var isInitialized = false
    private var shimmerWakeLock: PowerManager.WakeLock? = null

    // Last program written by the ladder; manual writes (picker preview, tests) clear it
    private var appliedMode = LidLedMode.OFF
    private var appliedColorHex: String? = null
    private var lastShimmeredPostTime = 0L

    @Volatile
    var isChargingState: Boolean = false
        private set

    @Volatile
    var notificationLedActive: Boolean = false
        private set

    @Volatile
    var chargingLedActive: Boolean = false
        private set

    /** Below [CRITICAL_BATTERY_PCT] the ladder forces the lid dark to save the last few percent. */
    @Volatile
    var batteryPct: Int = 100
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
                    val pct = batteryPercent(intent)
                    val critical = pct?.let { it < CRITICAL_BATTERY_PCT }
                    val wasCritical = batteryPct < CRITICAL_BATTERY_PCT
                    if (pct != null) batteryPct = pct
                    if (isChargingState != charging || (critical != null && critical != wasCritical)) {
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
            append("echo 0 > $LED_PATH_EFFECT\n")
            append("echo 0 > $LED_PATH_BRIGHTNESS\n")
        }
    }

    /**
     * Builds a bounded shimmer that runs as a background job of the root shell: [cycles] rounds of
     * [frameA] / [frameB], then [settleScript]. The script is sent once, so a running shimmer costs
     * this process no per-frame allocations or root round trips. The job's PID goes to [pidFile] so
     * the next program can stop it.
     */
    @VisibleForTesting
    internal fun buildShimmerLoopScript(
        frameA: String,
        frameB: String,
        frameSeconds: String,
        cycles: Int,
        settleScript: String,
        pidFile: String
    ): String = buildString {
        append(buildStopLoopScript(pidFile))
        append("(\n")
        append("i=0\n")
        append("while [ \$i -lt $cycles ]; do\n")
        append(frameA)
        append("sleep $frameSeconds\n")
        append(frameB)
        append("sleep $frameSeconds\n")
        append("i=\$((i+1))\n")
        append("done\n")
        append(settleScript)
        append("rm -f '$pidFile'\n")
        append(") </dev/null >/dev/null 2>&1 &\n")
        append("echo \$! > '$pidFile'\n")
    }

    /** Stops a running root-side shimmer. Prefixed to every program so the newest write wins. */
    @VisibleForTesting
    internal fun buildStopLoopScript(pidFile: String): String =
        "p=\$(cat '$pidFile' 2>/dev/null); [ -n \"\$p\" ] && kill \$p 2>/dev/null; rm -f '$pidFile'\n"

    @VisibleForTesting
    internal fun shimmerDurationMs(frameSeconds: String, cycles: Int): Long =
        (frameSeconds.toDouble() * 2 * cycles * 1000).toLong()

    /** The priority ladder. Pure so the rules are unit-tested. */
    @VisibleForTesting
    internal fun resolveMode(
        lidClosed: Boolean,
        enabled: Boolean,
        hasIncomingCall: Boolean,
        hasNotifications: Boolean,
        chargingLight: Boolean,
        batteryPct: Int = 100
    ): LidLedMode = when {
        !lidClosed || !enabled || batteryPct < CRITICAL_BATTERY_PCT -> LidLedMode.OFF
        hasIncomingCall -> LidLedMode.CALL
        hasNotifications -> LidLedMode.NOTIFICATION
        chargingLight -> LidLedMode.CHARGING
        else -> LidLedMode.OFF
    }

    /**
     * Sets all 5 exterior lid windows to steady solid illumination in the chosen color.
     * Used for live preview by the color picker; the ladder re-applies on its next update.
     */
    @Synchronized
    fun setSolidColor(colorHex: String) {
        invalidateApplied()
        val (r, g, b) = parseRgb(colorHex)
        releaseShimmerWakeLock()
        runProgram(buildStopLoopScript(pidFilePath()) + buildSolidScript(r, g, b))
        Log.d(TAG, "Activated solid lid color ($colorHex)")
    }

    /**
     * Shuts off all lid channels cleanly without waking vendor rainbow workqueues.
     */
    @Synchronized
    fun stopLeds() {
        invalidateApplied()
        notificationLedActive = false
        chargingLedActive = false
        releaseShimmerWakeLock()
        runProgram(buildStopLoopScript(pidFilePath()) + buildOffScript())
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
            batteryStatus?.let(::batteryPercent)?.let { batteryPct = it }
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

    // Physical flip only: a display timeout with the flip open never reaches these
    override fun onLidClosed() {
        Log.d(TAG, "Clamshell closed: updating lid LED states")
        updateLedState()
    }

    override fun onLidOpened() {
        // The windows lose power when the flip opens; still clear the chip so it doesn't resume
        // a stale frame on the next close
        Log.d(TAG, "Clamshell opened: turning off exterior LEDs")
        stopLeds()
    }

    /**
     * Evaluates the priority ladder (lid closed, incoming call, notifications, charging) and writes
     * the matching program through the root shell. Does nothing when the outcome is unchanged,
     * except that a newly posted notification restarts the shimmer.
     */
    @Synchronized
    fun updateLedState() {
        val context = appContext ?: return
        val items = NotificationTracker.notifications.value
        val mode = resolveMode(
            lidClosed = ClamshellManager.isLidClosed.value,
            enabled = PreferencesManager.isLidLedEnabled(context),
            hasIncomingCall = items.any(MochibunNotificationItem::isIncomingCall),
            hasNotifications = items.isNotEmpty(),
            chargingLight = isChargingState && PreferencesManager.isLidLedChargingEnabled(context),
            batteryPct = batteryPct
        )
        val hex = getHexColor(PreferencesManager.getLidLedColor(context))
        val newestPostTime = items.maxOfOrNull { it.postTime } ?: 0L
        val isNewPost = mode == LidLedMode.NOTIFICATION && newestPostTime > lastShimmeredPostTime

        if (mode == appliedMode && hex == appliedColorHex && !isNewPost) return
        applyMode(mode, hex)
        if (mode == LidLedMode.NOTIFICATION) lastShimmeredPostTime = newestPostTime
    }

    private fun applyMode(mode: LidLedMode, hex: String) {
        appliedMode = mode
        appliedColorHex = hex
        notificationLedActive = mode == LidLedMode.CALL || mode == LidLedMode.NOTIFICATION
        chargingLedActive = mode == LidLedMode.CHARGING
        val (r, g, b) = parseRgb(hex)
        val stop = buildStopLoopScript(pidFilePath())
        when (mode) {
            LidLedMode.OFF -> {
                releaseShimmerWakeLock()
                runProgram(stop + buildOffScript())
            }
            LidLedMode.CHARGING -> {
                releaseShimmerWakeLock()
                runProgram(stop + buildSolidScript(r, g, b))
            }
            LidLedMode.NOTIFICATION -> startShimmer(
                r, g, b,
                frameSeconds = NOTIFICATION_FRAME_SECONDS,
                cycles = NOTIFICATION_SHIMMER_CYCLES,
                settleScript = buildWindowPatternScript(CHANNELS_10101, r, g, b)
            )
            LidLedMode.CALL -> startShimmer(
                r, g, b,
                frameSeconds = CALL_FRAME_SECONDS,
                cycles = CALL_SHIMMER_CYCLES,
                settleScript = buildSolidScript(r, g, b)
            )
        }
        Log.d(TAG, "Lid LED mode -> $mode ($hex)")
    }

    private fun startShimmer(r: Int, g: Int, b: Int, frameSeconds: String, cycles: Int, settleScript: String) {
        val script = buildShimmerLoopScript(
            frameA = buildWindowPatternScript(CHANNELS_10101, r, g, b),
            frameB = buildWindowPatternScript(CHANNELS_01010, r, g, b),
            frameSeconds = frameSeconds,
            cycles = cycles,
            settleScript = settleScript,
            pidFile = pidFilePath()
        )
        // Keep the SoC up just long enough for the loop to reach its static frame; with the flip
        // closed the display is off and the loop's `sleep` would otherwise stall mid-pattern
        holdShimmerWakeLock(shimmerDurationMs(frameSeconds, cycles) + 1_000L)
        runProgram(script)
    }

    private fun invalidateApplied() {
        appliedMode = LidLedMode.OFF
        appliedColorHex = null
    }

    private fun pidFilePath(): String =
        appContext?.cacheDir?.let { "${it.absolutePath}/lid_led.pid" } ?: "/data/local/tmp/mochibun_lid_led.pid"

    private fun runProgram(script: String) {
        // Unit tests have no context; never run LED programs in the host shell
        if (appContext == null) return
        try {
            RootShell.submit(script)
        } catch (e: Throwable) {
            Log.w(TAG, "Lid LED write failed", e)
        }
    }

    private fun holdShimmerWakeLock(timeoutMs: Long) {
        val context = appContext ?: return
        try {
            val lock = shimmerWakeLock ?: (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mochibun:LidShimmer")
                ?.also {
                    it.setReferenceCounted(false)
                    shimmerWakeLock = it
                }
            lock?.acquire(timeoutMs)
        } catch (e: Exception) {
            Log.w(TAG, "Shimmer wake lock unavailable", e)
        }
    }

    private fun releaseShimmerWakeLock() {
        try {
            shimmerWakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
    }

    private fun batteryPercent(intent: Intent): Int? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    @VisibleForTesting
    internal fun setMockBatteryPct(pct: Int) {
        batteryPct = pct
    }

    @VisibleForTesting
    internal fun setMockChargingState(charging: Boolean) {
        isChargingState = charging
    }
}
