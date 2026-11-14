package one.crinklecod.mochibun.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.util.Log
import androidx.annotation.VisibleForTesting
import one.crinklecod.mochibun.data.model.LID_OFF_HEX
import one.crinklecod.mochibun.data.model.LidLedAnimation
import one.crinklecod.mochibun.data.model.LidLedStep
import one.crinklecod.mochibun.data.model.MochibunNotificationItem
import one.crinklecod.mochibun.data.model.isLidHexOff
import one.crinklecod.mochibun.data.model.normalizeLidHex
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.LidAnimationSlot
import one.crinklecod.mochibun.data.repository.LidLedAnimationRepository
import one.crinklecod.mochibun.data.repository.NotificationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile

/** What the closed lid is showing, highest priority first. */
enum class LidLedMode { CALL, NOTIFICATION, CHARGING, OFF }

/**
 * Exterior Clamshell Notification & Charging LED Manager for the Chinoe AT-M120 (Awinic AW22xxx driver).
 *
 * Coordinates external lid LED illumination, alternating shimmer animations, and custom 24-bit RGB colors:
 * - High-performance native register programming via `lidled` helper (`liblidled.so`).
 * - Direct register programming to `/sys/class/leds/aw22xxx_led/reg`.
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
 * when written through `reg`), so a shimmer is a bounded loop that runs inside the native helper daemon
 * using nanosleep (no per-frame work in this process) and always ends on a static frame. The chip holds
 * a static frame through SoC suspend with zero CPU:
 *  - Call: fast shimmer for up to [CALL_SHIMMER_CYCLES], then all windows solid.
 *  - Notification: shimmer on each new post, then a steady `1 0 1 0 1` glyph.
 *  - Charging: all windows solid in the user's color.
 *    `HLS_LsLedLights` in system_server writes `case_charge` on power events, which starts the
 *    kernel sequencer (stock green / rainbow). While the ladder owns the lid, `case_charge` is made
 *    read-only and `effect` is zeroed; [stopLeds] / OFF restore the original mode.
 */
object LidLedManager : ClamshellListener {

    private const val TAG = "LidLedManager"

    // Canonical sysfs paths exposed by the AT-M120 kernel driver
    /** AW22xxx outer-lid LED driver directory (AT-M120). Missing on other flip phones. */
    const val LED_SYSFS_DIR = "/sys/class/leds/aw22xxx_led"
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
    private const val NOTIFICATION_SHIMMER_CYCLES = 38 // ~60 s (38 * 1.6 s = 60.8 s)
    private const val CALL_FRAME_SECONDS = "0.4"
    private const val CALL_SHIMMER_CYCLES = 56 // ~45 s, longer than a typical ring
    const val SHIMMER_DEBOUNCE_MS = 1_500L

    private const val CASE_CHARGE_MODE_SAVE = "/data/local/tmp/lidled.case_charge.mode"

    // Step animations run inside one `lidled frames` daemon (like shimmer)
    private const val NOTIFICATION_STEP_TARGET_MS = 60_000L // match the classic ~60 s shimmer
    private const val MAX_STEP_LOOPS = 120 // same cap as lidled.c MAX_FRAME_LOOPS
    private const val TEST_PREVIEW_TARGET_MS = 5_000L


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationJob: Job? = null
    private var preferencesJob: Job? = null

    /**
     * Whether the AW22xxx outer LED exists: `true` = seen, `false` = root confirmed it is
     * missing (non-AT-M120 phone), `null` = unknown. SELinux can hide vendor sysfs from apps,
     * so only a root check that actually ran may answer `false`; unknown behaves like present.
     */
    private val _hasLedHardware = MutableStateFlow<Boolean?>(
        if (File(LED_SYSFS_DIR).exists()) true else null
    )
    val hasLedHardware: StateFlow<Boolean?> = _hasLedHardware.asStateFlow()

    private var appContext: Context? = null
    private var isInitialized = false
    private var shimmerWakeLock: PowerManager.WakeLock? = null
    private var helperBinaryPath: String? = null

    // Last program written by the ladder; manual writes (picker preview, tests) clear it
    private var appliedMode = LidLedMode.OFF
    private var appliedColorHex: String? = null
    private var lastShimmeredPostTime = 0L


    // Studio "Test Lid" runs with the flip open; the ladder must not cancel it mid-preview
    @Volatile
    private var testPreviewUntilMs = 0L
    private var testPreviewEndJob: Job? = null

    val testPreviewActive: Boolean get() = System.currentTimeMillis() < testPreviewUntilMs

    @Volatile
    private var shimmerStartTime = 0L
    @Volatile
    private var shimmerActiveUntilMs = 0L
    @Volatile
    private var shimmerPostTime = 0L

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
                    // HLS_LsLedLights reacts to the same event; force a fresh charging program
                    if (appliedMode == LidLedMode.CHARGING) invalidateApplied()
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
     * Installs or retrieves the native `lidled` binary from APK assets/lib folder.
     */
    internal fun ensureHelperInstalled(ctx: Context): File? {
        val devBin = File("/data/local/tmp/lidled")
        if (devBin.isFile && devBin.canExecute()) {
            helperBinaryPath = devBin.absolutePath
            return devBin
        }

        val dir = File(ctx.filesDir, "lidled").apply { mkdirs() }
        val stamp = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime
        } catch (_: Exception) {
            0L
        }
        val bin = File(dir, "lidled-$stamp")
        if (bin.isFile && bin.length() > 0 && bin.canExecute()) {
            helperBinaryPath = bin.absolutePath
            return bin
        }

        val info = ctx.applicationInfo
        val apks = listOf(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
        for (abi in Build.SUPPORTED_ABIS) {
            for (apk in apks) {
                try {
                    ZipFile(apk).use { zip ->
                        val entry = zip.getEntry("lib/$abi/liblidled.so") ?: return@use
                        val tmp = File(dir, "lidled.tmp")
                        zip.getInputStream(entry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        try {
                            Os.chmod(tmp.path, 0b111_101_101) // 0755
                        } catch (e: Exception) {
                            Log.w(TAG, "chmod failed on ${tmp.path}", e)
                        }
                        dir.listFiles { f -> f.name.startsWith("lidled-") }?.forEach { it.delete() }
                        if (tmp.renameTo(bin)) {
                            bin.setExecutable(true, false)
                            helperBinaryPath = bin.absolutePath
                            return bin
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading $apk for native lidled helper", e)
                }
            }
        }

        Log.w(TAG, "liblidled.so could not be found or extracted for ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        return null
    }

    /**
     * Resolves the path to the native helper binary.
     */
    fun getHelperPath(): String {
        helperBinaryPath?.let { return it }
        val ctx = appContext
        if (ctx != null) {
            ensureHelperInstalled(ctx)?.let { return it.absolutePath }
        }
        return "lidled"
    }

    @VisibleForTesting
    internal fun setMockHelperPath(path: String?) {
        helperBinaryPath = path
    }

    /**
     * Builds the command to cleanly shut down all lid channels and effects.
     */
    @VisibleForTesting
    internal fun buildOffCommand(helperPath: String = getHelperPath()): String =
        "'$helperPath' off"

    /**
     * Builds the command to illuminate all 5 physical exterior windows solid in custom RGB.
     */
    @VisibleForTesting
    internal fun buildSolidCommand(hexColor: String, helperPath: String = getHelperPath()): String =
        "'$helperPath' pattern 11111 $hexColor"

    /**
     * Builds the command to illuminate selected channels in custom RGB.
     */
    @VisibleForTesting
    internal fun buildPatternCommand(
        channelsMask: String,
        hexColor: String,
        helperPath: String = getHelperPath()
    ): String =
        "'$helperPath' pattern $channelsMask $hexColor"

    /**
     * Builds the command to execute a bounded alternating shimmer and settle on a final frame.
     */
    @VisibleForTesting
    internal fun buildShimmerCommand(
        hexColor: String,
        frameSeconds: String,
        cycles: Int,
        settleMode: String,
        helperPath: String = getHelperPath()
    ): String =
        "'$helperPath' shimmer $hexColor $frameSeconds $cycles $settleMode"

    /**
     * Halts the kernel sequencer and makes `case_charge` read-only so HLS_LsLedLights can't load
     * the stock green charging program over our frame.
     * The original mode is saved once so [buildReleaseChargeOverrideCommand] can restore it.
     */
    @VisibleForTesting
    internal fun buildSuppressStockChargeCommand(): String =
        "[ -f $CASE_CHARGE_MODE_SAVE ] || stat -c %a $LED_PATH_CHARGE > $CASE_CHARGE_MODE_SAVE 2>/dev/null; " +
            "chmod 0444 $LED_PATH_CHARGE 2>/dev/null; " +
            "echo 0 > $LED_PATH_EFFECT 2>/dev/null"


    /** Restores `case_charge` so the stock indicator works again when Mochibun isn't driving the lid. */
    @VisibleForTesting
    internal fun buildReleaseChargeOverrideCommand(): String =
        "if [ -f $CASE_CHARGE_MODE_SAVE ]; then chmod \$(cat $CASE_CHARGE_MODE_SAVE) $LED_PATH_CHARGE 2>/dev/null; " +
            "rm -f $CASE_CHARGE_MODE_SAVE; fi"

    /** Full teardown: zero the chip, stop the sequencer, release `case_charge`. */
    @VisibleForTesting
    internal fun buildTeardownCommand(helperPath: String = getHelperPath()): String =
        "${buildOffCommand(helperPath)}; echo 0 > $LED_PATH_EFFECT 2>/dev/null; " +
            buildReleaseChargeOverrideCommand()

    /** Charging program in one root shell submission: suppress stock, then hold all windows solid. */
    @VisibleForTesting
    internal fun buildChargingCommand(hexColor: String, helperPath: String = getHelperPath()): String =
        "${buildSuppressStockChargeCommand()}; ${buildSolidCommand(hexColor, helperPath)}"

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
        val hex = getHexColor(colorHex)
        releaseShimmerWakeLock()
        runCommand(buildSolidCommand(hex))
        Log.d(TAG, "Activated solid lid color ($hex)")
    }

    /**
     * Shuts off all lid channels cleanly without waking vendor rainbow workqueues.
     */
    @Synchronized
    fun stopLeds() {
        endTestPreview()
        invalidateApplied()
        notificationLedActive = false
        chargingLedActive = false
        releaseShimmerWakeLock()
        runCommand(buildTeardownCommand())
        Log.d(TAG, "Stopped all exterior LEDs cleanly via native helper")
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

        ensureHelperInstalled(app)
        ClamshellManager.addListener(this)
        probeLedHardware()

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
            combine(
                PreferencesManager.isLidLedEnabled,
                PreferencesManager.isLidLedChargingEnabled,
                PreferencesManager.lidLedColor
            ) { enabled, charging, color -> Triple(enabled, charging, color) }
                .collectLatest { updateLedState() }
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
    @VisibleForTesting
    internal fun shouldSkipShimmer(
        mode: LidLedMode,
        hex: String,
        newestPostTime: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (mode != LidLedMode.NOTIFICATION) return false
        if (mode != appliedMode || hex != appliedColorHex) return false
        val isShimmerActive = now < shimmerActiveUntilMs &&
            (newestPostTime <= shimmerPostTime || (now - shimmerStartTime < SHIMMER_DEBOUNCE_MS))
        val isNewPost = newestPostTime > lastShimmeredPostTime
        return !isNewPost || isShimmerActive
    }

    /**
     * Evaluates the priority ladder (lid closed, incoming call, notifications, charging) and writes
     * the matching program through the root shell. Does nothing when the outcome is unchanged,
     * except that a newly posted notification restarts the shimmer.
     */
    @Synchronized
    fun updateLedState() {
        val context = appContext ?: return
        if (testPreviewActive) return // re-evaluated when the preview ends
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

        if (shouldSkipShimmer(mode, hex, newestPostTime)) return

        val isNewPost = mode == LidLedMode.NOTIFICATION && newestPostTime > lastShimmeredPostTime
        if (mode == appliedMode && hex == appliedColorHex && !isNewPost) return

        applyMode(mode, hex, newestPostTime)
        if (mode == LidLedMode.NOTIFICATION) lastShimmeredPostTime = newestPostTime
    }

    private fun applyMode(mode: LidLedMode, hex: String, postTime: Long = 0L) {
        appliedMode = mode
        appliedColorHex = hex
        notificationLedActive = mode == LidLedMode.CALL || mode == LidLedMode.NOTIFICATION
        chargingLedActive = mode == LidLedMode.CHARGING
        when (mode) {
            LidLedMode.OFF -> {
                shimmerStartTime = 0L
                shimmerActiveUntilMs = 0L
                shimmerPostTime = 0L
                releaseShimmerWakeLock()
                runCommand(buildTeardownCommand())
            }
            LidLedMode.CHARGING -> {
                shimmerStartTime = 0L
                shimmerActiveUntilMs = 0L
                shimmerPostTime = 0L
                val custom = appContext?.let { ctx ->
                    runCatching {
                        LidLedAnimationRepository.getActiveAnimation(ctx, LidAnimationSlot.CHARGING)
                    }.getOrNull()
                }
                if (custom != null && custom.steps.isNotEmpty()) {
                    startChargingStepAnimation(custom)
                } else {
                    releaseShimmerWakeLock()
                    runCommand(buildChargingCommand(hex))
                }
            }
            LidLedMode.NOTIFICATION -> {
                // A selected step animation replaces the shimmer; none selected = classic behavior
                val custom = appContext?.let { ctx ->
                    runCatching {
                        LidLedAnimationRepository.getActiveAnimation(ctx, LidAnimationSlot.NOTIFICATION)
                    }.getOrNull()
                }
                if (custom != null && custom.steps.isNotEmpty()) {
                    startNotificationStepAnimation(custom, postTime)
                } else {
                    startShimmer(
                        hexColor = hex,
                        frameSeconds = NOTIFICATION_FRAME_SECONDS,
                        cycles = NOTIFICATION_SHIMMER_CYCLES,
                        settleMode = "10101",
                        postTime = postTime
                    )
                }
            }
            LidLedMode.CALL -> startShimmer(
                hexColor = hex,
                frameSeconds = CALL_FRAME_SECONDS,
                cycles = CALL_SHIMMER_CYCLES,
                settleMode = "solid",
                postTime = postTime
            )
        }
        Log.d(TAG, "Lid LED mode -> $mode ($hex)")
    }

    private fun startShimmer(
        hexColor: String,
        frameSeconds: String,
        cycles: Int,
        settleMode: String,
        postTime: Long = 0L
    ) {
        val now = System.currentTimeMillis()
        val durationMs = shimmerDurationMs(frameSeconds, cycles)
        shimmerStartTime = now
        shimmerActiveUntilMs = now + durationMs
        shimmerPostTime = postTime
        val shimmer = buildShimmerCommand(
            hexColor = hexColor,
            frameSeconds = frameSeconds,
            cycles = cycles,
            settleMode = settleMode
        )
        // Plugged in, HLS_LsLedLights would otherwise stomp on the alert with stock green
        val cmd = if (isChargingState) "${buildSuppressStockChargeCommand()}; $shimmer" else shimmer
        // Keep the SoC up just long enough for the loop to reach its static frame; with the flip
        // closed the display is off and the loop's nanosleep would otherwise stall mid-pattern
        holdShimmerWakeLock(durationMs + 1_000L)
        runCommand(cmd)
    }

    /** One step in the helper's own `pattern <mask> <hex>` vocabulary; null [hex] = all windows off. */
    internal data class StepFrame(val mask: String, val hex: String?)

    /**
     * The helper lights a frame in a single color, so a step becomes a 5-window mask (every
     * non-black window) plus its most common lit color (ties: leftmost window wins).
     */
    @VisibleForTesting
    internal fun stepFrame(step: LidLedStep): StepFrame {
        val windows = List(5) { i -> normalizeLidHex(step.windows.getOrNull(i) ?: "") }
        val lit = windows.filterNot(::isLidHexOff)
        if (lit.isEmpty()) return StepFrame("00000", null)
        val mask = windows.joinToString("") { if (isLidHexOff(it)) "0" else "1" }
        val color = lit.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        return StepFrame(mask, color)
    }

    /** `<mask>:<0xrrggbb>` argument for `lidled frames`; all-off steps are `00000:0x000000`. */
    @VisibleForTesting
    internal fun frameArg(frame: StepFrame): String = "${frame.mask}:${getHexColor(frame.hex ?: LID_OFF_HEX)}"

    /** Resting frame held after a notification / charging animation: the last step. */
    @VisibleForTesting
    internal fun settleFrame(animation: LidLedAnimation): StepFrame? =
        animation.steps.lastOrNull()?.let(::stepFrame)

    /**
     * `lidled frames <step_ms> <loops> <last|off> <mask:hex>...`: one helper daemon writes each
     * step once with the same `write_channel_pattern()` as `pattern`, then holds the resting
     * (last) frame or switches off. One writer per animation is what latches reliably on the
     * AT-M120; separate `pattern` calls per step drop frames and flicker (verified on device).
     * Any later helper call (teardown, pattern, shimmer) stops the daemon, so no extra cancel
     * logic is needed.
     */
    @VisibleForTesting
    internal fun buildFramesCommand(
        animation: LidLedAnimation,
        loops: Int,
        settle: Boolean,
        helperPath: String = getHelperPath()
    ): String =
        "'$helperPath' frames ${animation.stepMs} ${loops.coerceIn(1, MAX_STEP_LOOPS)} " +
            (if (settle) "last" else "off") + " " +
            animation.steps.joinToString(" ") { frameArg(stepFrame(it)) }

    /**
     * Full program: the existing [buildOffCommand] first (its chip reset clears any on-chip stock
     * program, e.g. the charging breath loaded at plug-in), then the frames daemon.
     */
    @VisibleForTesting
    internal fun buildStepAnimationCommand(
        animation: LidLedAnimation,
        loops: Int,
        settle: Boolean,
        helperPath: String = getHelperPath()
    ): String = "${buildOffCommand(helperPath)}; ${buildFramesCommand(animation, loops, settle, helperPath)}"

    @VisibleForTesting
    internal fun stepAnimationDurationMs(animation: LidLedAnimation, loops: Int): Long =
        loops * animation.durationMs

    /** Loops a notification / charging animation for about a minute, or once if `loop` is off. */
    @VisibleForTesting
    internal fun notificationStepLoops(animation: LidLedAnimation): Int = loopsFor(animation, NOTIFICATION_STEP_TARGET_MS)

    /** Studio "Test Lid": about 5 seconds of animation (at least one full pass). */
    @VisibleForTesting
    internal fun previewStepLoops(animation: LidLedAnimation): Int = loopsFor(animation.copy(loop = true), TEST_PREVIEW_TARGET_MS)

    private fun loopsFor(animation: LidLedAnimation, targetMs: Long): Int {
        if (!animation.loop) return 1
        val cycleMs = animation.durationMs.coerceAtLeast(1L)
        return ((targetMs + cycleMs - 1) / cycleMs).toInt().coerceIn(1, MAX_STEP_LOOPS)
    }

    /**
     * Plays a step animation on the lid right now (Studio "Test Lid"; the windows work with the
     * flip open), then switches it off. While it runs [updateLedState] is a no-op so power /
     * notification / preference events can't cancel it; the ladder re-applies when it ends.
     * [stopLeds] (lid opened / LEDs disabled) still cancels it.
     */
    @Synchronized
    fun playStepAnimation(animation: LidLedAnimation, loopCount: Int = previewStepLoops(animation)) {
        val clean = animation.sanitized()
        if (clean.steps.isEmpty()) return
        invalidateApplied()
        val loops = loopCount.coerceIn(1, MAX_STEP_LOOPS)
        val durationMs = stepAnimationDurationMs(clean, loops)
        holdShimmerWakeLock(durationMs + 1_000L)
        val program = buildStepAnimationCommand(clean, loops, settle = false)
        // Same as startShimmer: while plugged in, keep HLS_LsLedLights' stock charger program off the lid
        runCommand(if (isChargingState) "${buildSuppressStockChargeCommand()}; $program" else program)
        testPreviewUntilMs = System.currentTimeMillis() + durationMs
        testPreviewEndJob?.cancel()
        testPreviewEndJob = scope.launch {
            delay(durationMs)
            testPreviewUntilMs = 0L
            updateLedState()
        }
        Log.d(TAG, "Playing lid step animation '${clean.name}' x$loops")
    }

    private fun endTestPreview() {
        testPreviewUntilMs = 0L
        testPreviewEndJob?.cancel()
        testPreviewEndJob = null
    }

    private fun startChargingStepAnimation(animation: LidLedAnimation) {
        shimmerStartTime = 0L
        shimmerActiveUntilMs = 0L
        shimmerPostTime = 0L
        val loops = notificationStepLoops(animation)
        val program = buildStepAnimationCommand(animation, loops, settle = true)
        // Keep HLS_LsLedLights from loading the stock green charging program over the animation
        holdShimmerWakeLock(stepAnimationDurationMs(animation, loops) + 1_000L)
        runCommand("${buildSuppressStockChargeCommand()}; $program")
    }

    private fun startNotificationStepAnimation(animation: LidLedAnimation, postTime: Long) {
        val loops = notificationStepLoops(animation)
        val durationMs = stepAnimationDurationMs(animation, loops)
        val now = System.currentTimeMillis()
        shimmerStartTime = now
        shimmerActiveUntilMs = now + durationMs
        shimmerPostTime = postTime
        val program = buildStepAnimationCommand(animation, loops, settle = true)
        // Same as startShimmer: keep HLS_LsLedLights from stomping on the alert while plugged in
        val cmd = if (isChargingState) "${buildSuppressStockChargeCommand()}; $program" else program
        holdShimmerWakeLock(durationMs + 1_000L)
        runCommand(cmd)
    }

    private fun invalidateApplied() {
        appliedMode = LidLedMode.OFF
        appliedColorHex = null
        shimmerStartTime = 0L
        shimmerActiveUntilMs = 0L
        shimmerPostTime = 0L
    }

    @VisibleForTesting
    internal fun setMockShimmerState(startTime: Long, activeUntilMs: Long, postTime: Long) {
        shimmerStartTime = startTime
        shimmerActiveUntilMs = activeUntilMs
        shimmerPostTime = postTime
    }

    @VisibleForTesting
    internal fun setMockAppliedState(mode: LidLedMode, hex: String?, lastPostTime: Long) {
        appliedMode = mode
        appliedColorHex = hex
        lastShimmeredPostTime = lastPostTime
    }

    /** Asks root whether the LED node exists when the app itself can't see it. */
    private fun probeLedHardware() {
        if (_hasLedHardware.value != null || !RootShell.isRootAvailable()) return
        scope.launch {
            val result = RootShell.execute("[ -e $LED_SYSFS_DIR ] && echo yes || echo no", 5_000L)
            when (result.stdout.lastOrNull()?.trim()) {
                "yes" -> _hasLedHardware.value = true
                "no" -> {
                    _hasLedHardware.value = false
                    Log.i(TAG, "No AW22xxx lid LED on this device; outer LED features disabled")
                }
                // Root denied or timed out: stay unknown (same as before this check existed)
            }
        }
    }

    private fun runCommand(cmd: String) {
        // Unit tests have no context; never run LED programs in the host shell
        if (appContext == null && helperBinaryPath == null) return
        // Phones without the AW22xxx chip (e.g. Kyocera keitai) have nothing to drive
        if (_hasLedHardware.value == false) return
        try {
            RootShell.submit(cmd)
        } catch (e: Throwable) {
            Log.w(TAG, "Lid LED command execution failed: $cmd", e)
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
