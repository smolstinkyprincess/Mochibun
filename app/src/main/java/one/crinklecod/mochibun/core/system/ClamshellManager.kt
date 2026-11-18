/**
 * Centralized Clamshell / Flip Lid Lifecycle Manager for the ALT Mive Style Folder (AT-M120).
 * Tracks the physical flip (hall switch) separately from display power, so a screen timeout with
 * the flip open is never mistaken for the lid closing.
 */
package one.crinklecod.mochibun.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/** Physical flip position crossed with display power. */
enum class ClamshellState(val isClosed: Boolean, val isAwake: Boolean) {
    OPEN_AWAKE(isClosed = false, isAwake = true),
    OPEN_DOZING(isClosed = false, isAwake = false),
    CLOSED_ACTIVE(isClosed = true, isAwake = true),
    CLOSED_SLEEP(isClosed = true, isAwake = false);

    companion object {
        fun of(isClosed: Boolean, isAwake: Boolean): ClamshellState = when {
            isClosed && isAwake -> CLOSED_ACTIVE
            isClosed -> CLOSED_SLEEP
            isAwake -> OPEN_AWAKE
            else -> OPEN_DOZING
        }
    }
}

/** All callbacks run on the main thread. */
interface ClamshellListener {
    /** The flip was physically shut. A display timeout or Power key never fires this. */
    fun onLidClosed() {}

    /** The flip was physically opened. */
    fun onLidOpened() {}

    /** The display went to sleep for any reason: lid close, timeout, or Power key. */
    fun onDisplaySleep() {}

    /** The display woke for any reason. */
    fun onDisplayWake() {}

    fun onStateChanged(previous: ClamshellState, current: ClamshellState) {}
}

/**
 * Lid sources on the AT-M120, verified on device:
 *  - The hall IRQ is reported by the mtk-kpd keypad driver as KEY_SLEEP (closed) and KEY_WAKEUP
 *    (opened). There is no SW_LID switch and no /sys/class/switch node.
 *  - The window policy consumes both keys before apps or accessibility filters see them, and the
 *    vendor mirror property `sys.hls.hall.state` is system_prop, which untrusted apps can't read.
 *  - `Configuration.hardKeyboardHidden` reports YES with the flip open, so it is not a lid signal.
 *
 * So with root, [HallSwitchReader] streams the keypad events and seeds the initial state from the
 * property. Without root the flip is assumed open: a screen-off becomes OPEN_DOZING, never CLOSED.
 */
object ClamshellManager {
    private const val TAG = "ClamshellManager"

    /** Vendor mirror of the hall switch: "1" = closed, "0" = open. Readable only as root. */
    internal const val PROP_HALL_STATE = "sys.hls.hall.state"

    // Covers delivering a hall event from the reader thread through to the LED write while the SoC
    // would otherwise drop back into suspend
    private const val HALL_EVENT_WAKE_LOCK_MS = 3_000L
    private const val HINGE_CLOSED_MAX_DEGREES = 15f
    private const val HINGE_OPEN_MIN_DEGREES = 30f

    private val _state = MutableStateFlow(ClamshellState.OPEN_AWAKE)
    val state: StateFlow<ClamshellState> = _state.asStateFlow()

    /** True only while the flip is physically shut. */
    private val _isLidClosed = MutableStateFlow(false)
    val isLidClosed: StateFlow<Boolean> = _isLidClosed.asStateFlow()

    /** Process-wide close counter so MainActivity never misses a close that happened before it existed. */
    private val _lidCloseSequence = MutableStateFlow(0)
    val lidCloseSequence: StateFlow<Int> = _lidCloseSequence.asStateFlow()

    /** Process-wide wake-while-open counter for D-pad focus recovery. */
    private val _openAwakeSequence = MutableStateFlow(0)
    val openAwakeSequence: StateFlow<Int> = _openAwakeSequence.asStateFlow()

    /**
     * Incremented when the phone wakes after a close:
     * - Hardware lid close -> OPEN_AWAKE (rooted M120 hall switch or Sensor.TYPE_HINGE_ANGLE)
     * - Non-root / generic fallback: ACTION_SCREEN_OFF -> ACTION_SCREEN_ON when no hardware lid source is active.
     */
    private val _pendingOpenActionSequence = MutableStateFlow(0)
    val pendingOpenActionSequence: StateFlow<Int> = _pendingOpenActionSequence.asStateFlow()

    @Volatile
    var lastPendingOpenActionUptimeMs: Long = 0L
        private set

    @VisibleForTesting
    internal var uptimeMillisProvider: () -> Long = {
        try {
            SystemClock.uptimeMillis()
        } catch (_: Throwable) {
            0L
        }
    }

    private val listeners = CopyOnWriteArrayList<ClamshellListener>()
    private var isReceiverRegistered = false
    private var appContext: Context? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var hallReader: HallSwitchReader? = null
    private var hallWakeLock: PowerManager.WakeLock? = null
    private var hingeSensorActive = false
    private var hingeSensorListener: SensorEventListener? = null

    // Inputs to the state machine. Written on the main thread only.
    private var hallClosed = false
    private var displayAwake = true
    private var closedSinceOpen = false
    private var fallbackScreenOffSinceOpen = false

    @VisibleForTesting
    internal var hardwareLidSourceOverride: Boolean? = null

    /** True when a real physical lid sensor/stream is driving transitions (not just screen power). */
    fun hasHardwareLidSource(): Boolean = hardwareLidSourceOverride
        ?: (hallReader?.isStreaming == true || hingeSensorActive || RootShell.isGranted() == true)

    @VisibleForTesting
    internal fun hingeAngleToClosed(angleDegrees: Float): Boolean? = when {
        angleDegrees <= HINGE_CLOSED_MAX_DEGREES -> true
        angleDegrees >= HINGE_OPEN_MIN_DEGREES -> false
        else -> null
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val awake = when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> false
                Intent.ACTION_SCREEN_ON -> true
                else -> return
            }
            onDisplayPowerChanged(awake, allowScreenOffFallback = !hasHardwareLidSource())

            // Without a live event stream, re-read the switch as root. A screen-off may be the lid
            // closing, so wait briefly for the vendor property to catch up before trusting "open".
            if (hallReader?.isStreaming != true && RootShell.isGranted() == true) {
                val pending = goAsync()
                Thread({
                    val closed = HallSwitchReader.readHallProperty(expectClosed = !awake)
                    mainHandler.post {
                        if (closed != null) onHallChanged(closed)
                        pending.finish()
                    }
                }, "mochibun-hall-sync").start()
            }
        }
    }

    /**
     * Registers the screen receiver and starts the root hall reader (or hinge sensor fallback).
     * Safe to call multiple times; idempotently registers once per process.
     */
    fun init(context: Context) {
        if (isReceiverRegistered) {
            ensureHallReaderRunning()
            return
        }
        val app = context.applicationContext
        appContext = app
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            app.registerReceiver(screenStateReceiver, filter)
            isReceiverRegistered = true
            val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            displayAwake = powerManager?.isInteractive ?: true
            applyState()
            Log.d(TAG, "ClamshellManager initialized: ${_state.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen state receiver: ${e.message}")
        }

        startHallReader()
        initHingeSensorFallback(app)
    }

    /** Retries starting [HallSwitchReader] if root became available after early boot. */
    fun ensureHallReaderRunning() {
        if (!isReceiverRegistered) return
        val reader = hallReader
        if (reader?.isStreaming == true || reader?.isRunning == true) return
        if (!RootShell.isSuBinaryPresent()) return
        if (RootShell.isGranted() == false) return
        startHallReader()
    }

    private fun startHallReader() {
        hallReader = HallSwitchReader { closed ->
            acquireHallWakeLock()
            mainHandler.post { onHallChanged(closed) }
        }.also { it.start() }
    }

    private fun initHingeSensorFallback(app: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (hingeSensorListener != null) return
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val hingeSensor = sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (hallReader?.isStreaming == true) return
                val angle = event?.values?.firstOrNull() ?: return
                hingeAngleToClosed(angle)?.let { closed ->
                    onHallChanged(closed)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sm.registerListener(listener, hingeSensor, SensorManager.SENSOR_DELAY_NORMAL, mainHandler)) {
            hingeSensorListener = listener
            hingeSensorActive = true
            Log.d(TAG, "Hinge angle sensor fallback registered")
        }
    }

    /** True when the flip is shut or the display is off. Use for "nobody is looking" decisions. */
    fun isClosedOrAsleep(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = powerManager?.isInteractive ?: true
        return !isInteractive || _state.value.isClosed
    }

    fun addListener(listener: ClamshellListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: ClamshellListener) {
        listeners.remove(listener)
    }

    @VisibleForTesting
    internal fun onHallChanged(closed: Boolean) {
        hallClosed = closed
        applyState()
    }

    @VisibleForTesting
    internal fun onDisplayPowerChanged(awake: Boolean, allowScreenOffFallback: Boolean = false) {
        displayAwake = awake
        applyState()
        if (allowScreenOffFallback) {
            if (!awake) {
                fallbackScreenOffSinceOpen = true
            } else if (fallbackScreenOffSinceOpen) {
                fallbackScreenOffSinceOpen = false
                lastPendingOpenActionUptimeMs = uptimeMillisProvider()
                _pendingOpenActionSequence.value++
            }
        }
    }

    private fun applyState() {
        val previous = _state.value
        val current = ClamshellState.of(hallClosed, displayAwake)
        _isLidClosed.value = current.isClosed
        if (previous == current) return
        _state.value = current
        Log.d(TAG, "Clamshell transition: $previous -> $current")

        if (current.isClosed && !previous.isClosed) {
            closedSinceOpen = true
            fallbackScreenOffSinceOpen = false
            _lidCloseSequence.value++
        }
        if (current == ClamshellState.OPEN_AWAKE) {
            _openAwakeSequence.value++
            if (closedSinceOpen) {
                closedSinceOpen = false
                fallbackScreenOffSinceOpen = false
                lastPendingOpenActionUptimeMs = uptimeMillisProvider()
                _pendingOpenActionSequence.value++
            }
        }

        val lidChanged = previous.isClosed != current.isClosed
        val displayChanged = previous.isAwake != current.isAwake
        listeners.forEach { listener ->
            if (lidChanged) {
                if (current.isClosed) listener.onLidClosed() else listener.onLidOpened()
            }
            if (displayChanged) {
                if (current.isAwake) listener.onDisplayWake() else listener.onDisplaySleep()
            }
            listener.onStateChanged(previous, current)
        }
    }

    private fun acquireHallWakeLock() {
        val context = appContext ?: return
        try {
            val lock = hallWakeLock ?: (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mochibun:HallEvent")
                ?.also {
                    it.setReferenceCounted(false)
                    hallWakeLock = it
                }
            lock?.acquire(HALL_EVENT_WAKE_LOCK_MS)
        } catch (e: Exception) {
            Log.w(TAG, "Hall wake lock unavailable", e)
        }
    }

    @VisibleForTesting
    internal fun resetForTest() {
        hallClosed = false
        displayAwake = true
        closedSinceOpen = false
        fallbackScreenOffSinceOpen = false
        hardwareLidSourceOverride = null
        hingeSensorActive = false
        lastPendingOpenActionUptimeMs = 0L
        _lidCloseSequence.value = 0
        _openAwakeSequence.value = 0
        _pendingOpenActionSequence.value = 0
        _state.value = ClamshellState.OPEN_AWAKE
        _isLidClosed.value = false
    }
}
