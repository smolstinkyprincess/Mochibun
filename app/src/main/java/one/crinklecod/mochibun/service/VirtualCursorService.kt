/**
 * System-wide virtual mouse cursor foreground service for the Chinoe AT-M120 (Android 12).
 * Driven via system-level broadcast events that are routed through the launcher’s neutral
 * action constants, translating discrete D-pad presses into continuous, fluid
 * hardware-accelerated cursor motion.
 */
package one.crinklecod.mochibun.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import one.crinklecod.mochibun.R
import one.crinklecod.mochibun.core.system.ClamshellListener
import one.crinklecod.mochibun.core.system.ClamshellManager
import one.crinklecod.mochibun.core.system.RootTouchInjector
import one.crinklecod.mochibun.core.util.CursorPhysics
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.prefs.CursorPreferences
import one.crinklecod.mochibun.ui.overlay.CursorOverlayView
import kotlin.math.abs

class VirtualCursorService : Service(), Choreographer.FrameCallback {

    companion object {
        private const val TAG = "VirtualCursorService"
        private const val SCROLL_SWIPE_DURATION_MS = 200L
        private const val SCROLL_DEBOUNCE_MS = 220L

        private const val NOTIFICATION_CHANNEL_ID = "virtual_cursor_service"
        private const val NOTIFICATION_ID = 4801

        const val ACTION_TOGGLE_CURSOR = MochibunSystemActions.ACTION_TOGGLE_CURSOR
        const val ACTION_CURSOR_KEY = MochibunSystemActions.ACTION_CURSOR_KEY
        private const val ACTION_CURSOR_MODE_CHANGED = MochibunSystemActions.ACTION_CURSOR_MODE_CHANGED
        private const val ACTION_REQUEST_CURSOR_MODE = MochibunSystemActions.ACTION_REQUEST_CURSOR_MODE

        const val EXTRA_KEY_EVENT = MochibunSystemActions.EXTRA_KEY_EVENT
        const val EXTRA_KEY_CODE = MochibunSystemActions.EXTRA_KEY_CODE
        const val EXTRA_ACTION = MochibunSystemActions.EXTRA_ACTION
        const val EXTRA_REPEAT_COUNT = MochibunSystemActions.EXTRA_REPEAT_COUNT
        const val EXTRA_CURSOR_ACTIVE = MochibunSystemActions.EXTRA_CURSOR_ACTIVE

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var isCursorVisible = false
            private set

        private var instance: VirtualCursorService? = null

        /** Running instance, for in-module tests. */
        internal val runningInstance: VirtualCursorService? get() = instance

        /**
         * Toggles the cursor. The service only runs while the cursor is visible, so when it is
         * not running, toggling means starting it with the cursor shown.
         */
        fun toggleCursor(context: Context) {
            val service = instance
            if (service != null) {
                service.toggleCursorVisibility()
            } else {
                start(context, showCursor = true)
            }
        }

        /**
         * Feeds a cursor key from the optional non-root [MochibunAccessibilityService] (same path
         * the LSPosed hook's ACTION_CURSOR_KEY broadcast takes). Main thread only.
         */
        fun dispatchCursorKey(keyCode: Int, action: Int, repeatCount: Int): Boolean {
            val service = instance ?: return false
            service.handleCursorKey(keyCode, action, repeatCount)
            return true
        }

        fun hideCursor() {
            val service = instance ?: return
            if (!isCursorVisible) return
            service.mainHandler.post { service.handleLidClosed() }
        }

        /**
         * Starts the service with the cursor shown. A start that would leave the cursor hidden
         * stops the service again immediately (no idle foreground notification).
         */
        fun start(context: Context, showCursor: Boolean = true) {
            context.startForegroundService(showIntent(context, showCursor))
        }

        internal fun showIntent(context: Context, showCursor: Boolean = true): Intent =
            Intent(context, VirtualCursorService::class.java)
                .setAction(ACTION_TOGGLE_CURSOR)
                .putExtra(EXTRA_CURSOR_ACTIVE, showCursor)

        /** True when the service should stay alive: it only runs while the cursor is visible. */
        internal fun shouldKeepRunning(cursorVisible: Boolean): Boolean = cursorVisible

        fun stop(context: Context) {
            val intent = Intent(context, VirtualCursorService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: CursorOverlayView? = null
    private var isOverlayAttached = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    // Configurable settings cache
    private var toggleKeyCode = CursorPreferences.DEFAULT_TOGGLE_KEYCODE
    private var scrollStepPx = CursorPreferences.DEFAULT_SCROLL_STEP_PX

    // Zero-allocation continuous physics engine
    private val physics = CursorPhysics()
    private var isMovementTickerActive = false

    // Scroll cadence debounce
    private var lastScrollTimeMs = 0L

    // Preallocated so the toggle key doesn't allocate a lambda per press
    private val toggleRunnable = Runnable { toggleCursorVisibility() }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadConfig()
    }

    private val clamshellListener = object : ClamshellListener {
        override fun onDisplaySleep() {
            handleLidClosed()
        }

        override fun onDisplayWake() {
            handleLidOpened()
        }
    }

    private val cursorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                ACTION_TOGGLE_CURSOR -> {
                    val active = if (intent.hasExtra(EXTRA_CURSOR_ACTIVE)) {
                        intent.getBooleanExtra(EXTRA_CURSOR_ACTIVE, !isCursorVisible)
                    } else {
                        !isCursorVisible
                    }
                    setCursorVisibility(active)
                }
                ACTION_REQUEST_CURSOR_MODE -> broadcastCursorModeChanged(isCursorVisible)
                ACTION_CURSOR_KEY -> {
                    @Suppress("DEPRECATION")
                    val keyEvent = intent.getParcelableExtra<KeyEvent>(EXTRA_KEY_EVENT)
                    val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, keyEvent?.keyCode ?: KeyEvent.KEYCODE_UNKNOWN)
                    val action = intent.getIntExtra(EXTRA_ACTION, keyEvent?.action ?: KeyEvent.ACTION_DOWN)
                    val repeatCount = intent.getIntExtra(EXTRA_REPEAT_COUNT, keyEvent?.repeatCount ?: 0)
                    handleCursorKey(keyCode, action, repeatCount)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning = true

        RootTouchInjector.attach(this)

        ClamshellManager.init(this)
        ClamshellManager.addListener(clamshellListener)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(480f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(800f)

        physics.setBounds(width, height)
        physics.setPosition((width / 2f).coerceIn(0f, 480f), (height / 2f).coerceIn(0f, 800f))

        prefs = CursorPreferences.getPrefs(this)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        reloadConfig()

        overlayView = CursorOverlayView(this).apply {
            visibility = View.GONE
            windowLayoutParams.apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_CURSOR)
            addAction(ACTION_CURSOR_KEY)
            addAction(ACTION_REQUEST_CURSOR_MODE)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            cursorReceiver,
            filter,
            MochibunSystemActions.SYSTEM_SERVER_BROADCAST_PERMISSION,
            null,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )

        Log.d(TAG, "VirtualCursorService started as foreground Service on display ${physics.screenWidth}x${physics.screenHeight}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires startForeground() even if we stop right away.
        startInForeground()
        when (intent?.action) {
            ACTION_TOGGLE_CURSOR -> {
                val active = if (intent.hasExtra(EXTRA_CURSOR_ACTIVE)) {
                    intent.getBooleanExtra(EXTRA_CURSOR_ACTIVE, !isCursorVisible)
                } else {
                    !isCursorVisible
                }
                setCursorVisibility(active)
            }
            ACTION_CURSOR_KEY -> {
                @Suppress("DEPRECATION")
                val keyEvent = intent.getParcelableExtra<KeyEvent>(EXTRA_KEY_EVENT)
                val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, keyEvent?.keyCode ?: KeyEvent.KEYCODE_UNKNOWN)
                val action = intent.getIntExtra(EXTRA_ACTION, keyEvent?.action ?: KeyEvent.ACTION_DOWN)
                val repeatCount = intent.getIntExtra(EXTRA_REPEAT_COUNT, keyEvent?.repeatCount ?: 0)
                handleCursorKey(keyCode, action, repeatCount)
            }
        }
        stopIfHidden()
        return START_NOT_STICKY
    }

    /** Removes the notification and stops the service whenever the cursor is off. */
    private fun stopIfHidden() {
        if (shouldKeepRunning(isCursorVisible)) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(480f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(800f)
        physics.setBounds(width, height)
    }

    private fun startInForeground() {
        try {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Virtual Cursor",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mochibun Virtual Cursor Overlay Service"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)

            val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Mochibun Virtual Cursor")
                .setContentText("Virtual mouse cursor active")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VirtualCursorService in foreground: ${e.message}")
        }
    }

    private fun handleLidClosed() {
        Log.d(TAG, "Display asleep: suspending cursor ticker and auto-dismissing overlay")
        stopMovementTicker()
        overlayView?.pauseAnimation()
        if (isCursorVisible) {
            isCursorVisible = false
            overlayView?.visibility = View.GONE
            RootTouchInjector.cancelTouch()
            RootTouchInjector.release()
            broadcastCursorModeChanged(false)
        }
        stopIfHidden()
    }

    private fun handleLidOpened() {
        Log.d(TAG, "Display awake: refreshing display bounds")
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(480f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(800f)
        physics.setBounds(width, height)
    }

    override fun onDestroy() {
        val cursorWasVisible = isCursorVisible
        isServiceRunning = false
        isCursorVisible = false
        if (cursorWasVisible) broadcastCursorModeChanged(false)
        instance = null
        mainHandler.removeCallbacks(toggleRunnable)

        try {
            unregisterReceiver(cursorReceiver)
        } catch (_: Exception) {}

        ClamshellManager.removeListener(clamshellListener)
        stopMovementTicker()

        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        }

        overlayView?.let { view ->
            if (isOverlayAttached) {
                try {
                    windowManager.removeView(view)
                } catch (_: IllegalArgumentException) {}
                isOverlayAttached = false
            }
            view.release()
        }
        overlayView = null

        RootTouchInjector.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun reloadConfig() {
        toggleKeyCode = CursorPreferences.getToggleKeyCode(this)
        physics.stepPx = CursorPreferences.getTapStepPx(this).toFloat()
        physics.maxSpeed = CursorPreferences.getMaxSpeed(this).toFloat()
        physics.accelRate = CursorPhysics.accelRateForRampMs(CursorPreferences.getAccelRampMs(this))
        scrollStepPx = CursorPreferences.getScrollStepPx(this)
        if (isCursorVisible) {
            RootTouchInjector.attach(applicationContext)
        }
        overlayView?.reloadConfiguration()
    }

    fun setCursorVisibility(visible: Boolean) {
        if (isCursorVisible == visible) return
        isCursorVisible = visible
        val view = overlayView ?: run {
            isCursorVisible = false
            broadcastCursorModeChanged(false)
            stopIfHidden()
            return
        }

        if (isCursorVisible) {
            RootTouchInjector.attach(applicationContext)
            //Cancel any pending/stuck touch
            RootTouchInjector.cancelTouch()
            if (!isOverlayAttached) {
                try {
                    windowManager.addView(view, view.windowLayoutParams)
                    isOverlayAttached = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to attach cursor overlay window: ${e.message}")
                    isCursorVisible = false
                    broadcastCursorModeChanged(false)
                    stopIfHidden()
                    return
                }
            }
            view.visibility = View.VISIBLE
            view.updatePosition(physics.posX, physics.posY)
            view.resumeAnimation()
            Log.d(TAG, "Virtual cursor activated at (${physics.posX}, ${physics.posY})")
        } else {
            stopMovementTicker()
            view.pauseAnimation()
            view.visibility = View.GONE
            RootTouchInjector.cancelTouch()
            RootTouchInjector.release()
            Log.d(TAG, "Virtual cursor hidden")
        }
        broadcastCursorModeChanged(isCursorVisible)
        stopIfHidden()
    }

    private fun broadcastCursorModeChanged(active: Boolean) {
        val intent = Intent(ACTION_CURSOR_MODE_CHANGED)
            .setPackage("android")
            .putExtra(EXTRA_CURSOR_ACTIVE, active)
        try {
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synchronize cursor mode with system_server", e)
        }
    }

    fun toggleCursorVisibility() {
        setCursorVisibility(!isCursorVisible)
    }

    private fun handleCursorKey(keyCode: Int, action: Int, repeatCount: Int) {
        val isToggle = (keyCode == toggleKeyCode) || (keyCode == KeyEvent.KEYCODE_F4)

        if (isToggle) {
            if ((action == KeyEvent.ACTION_DOWN) && (repeatCount == 0)) {
                toggleCursorVisibility()
            }
            return
        }

        if (!isCursorVisible) return

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> handleDirectionKey(action, repeatCount, dx = 0, dy = -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> handleDirectionKey(action, repeatCount, dx = 0, dy = 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> handleDirectionKey(action, repeatCount, dx = -1, dy = 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> handleDirectionKey(action, repeatCount, dx = 1, dy = 0)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    performTapDispatch()
                }
            }
            // Scroll keys drag opposite to scroll direction
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_NUMPAD_2 -> {
                if (action == KeyEvent.ACTION_DOWN) handleScrollKey(dragX = 0, dragY = 1) // scroll up
            }
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_NUMPAD_8 -> {
                if (action == KeyEvent.ACTION_DOWN) handleScrollKey(dragX = 0, dragY = -1) // scroll down
            }
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_NUMPAD_4 -> {
                if (action == KeyEvent.ACTION_DOWN) handleScrollKey(dragX = 1, dragY = 0) // scroll left
            }
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_NUMPAD_6 -> {
                if (action == KeyEvent.ACTION_DOWN) handleScrollKey(dragX = -1, dragY = 0) // scroll right
            }
        }
    }

    private fun handleScrollKey(dragX: Int, dragY: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScrollTimeMs >= SCROLL_DEBOUNCE_MS) {
            lastScrollTimeMs = now
            performScrollDispatch(dragX, dragY)
        }
    }

    private fun handleDirectionKey(action: Int, repeatCount: Int, dx: Int, dy: Int) {
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount != 0) return
                physics.pressDirection(dx, dy)
                overlayView?.updatePosition(physics.posX, physics.posY)
                startMovementTickerIfNeeded()
            }
            KeyEvent.ACTION_UP -> {
                physics.releaseDirection(dx, dy)
                if (!physics.hasActiveInput) {
                    stopMovementTicker()
                }
            }
        }
    }

    private fun startMovementTickerIfNeeded() {
        if (!isMovementTickerActive && isCursorVisible) {
            isMovementTickerActive = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopMovementTicker() {
        physics.releaseAll()
        if (isMovementTickerActive) {
            isMovementTickerActive = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    /**
     * VSYNC-driven frame callback, posted only while a direction key is held, so the cursor
     * costs no CPU when idle. Zero allocations: physics math plus a translation update.
     */
    override fun doFrame(frameTimeNanos: Long) {
        if (!isMovementTickerActive || !isCursorVisible) {
            isMovementTickerActive = false
            return
        }

        if (physics.hasActiveInput) {
            if (physics.tick(frameTimeNanos)) {
                overlayView?.updatePosition(physics.posX, physics.posY)
            }
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            stopMovementTicker()
        }
    }

    /**
     * Dispatches a tap at current cursor coordinates using RootTouchInjector (evinput/native tap).
     */
    private fun performTapDispatch() {
        val view = overlayView ?: return
        view.showClickFeedback()

        val tapX = physics.posX.coerceIn(0f, (physics.screenWidth - 1f).coerceAtLeast(1f))
        val tapY = physics.posY.coerceIn(0f, (physics.screenHeight - 1f).coerceAtLeast(1f))

        Log.d(TAG, "Dispatching tap at ($tapX, $tapY)")
        RootTouchInjector.tapAsync(tapX.toInt(), tapY.toInt())
    }

    /**
     * Dispatches a one-step scroll swipe from the current pointer coordinates.
     * The finger drags in direction ([dragX], [dragY]), which scrolls the content the opposite way:
     * - Key 2 (0, +1): downward drag to scroll UP.
     * - Key 8 (0, -1): upward drag to scroll DOWN.
     */
    private fun performScrollDispatch(dragX: Int, dragY: Int) {
        if (overlayView == null) return

        val maxX = (physics.screenWidth - 1f).coerceAtLeast(1f)
        val maxY = (physics.screenHeight - 1f).coerceAtLeast(1f)

        var startX = physics.posX.coerceIn(0f, maxX)
        var startY = physics.posY.coerceIn(0f, maxY)
        val distance = scrollStepPx.toFloat().coerceIn(
            CursorPreferences.MIN_SCROLL_STEP_PX.toFloat(),
            CursorPreferences.MAX_SCROLL_STEP_PX.toFloat()
        )

        val endX = (startX + dragX * distance).coerceIn(0f, maxX)
        val endY = (startY + dragY * distance).coerceIn(0f, maxY)

        if (dragX != 0 && abs(endX - startX) < 16f) {
            startX = (endX - dragX * distance).coerceIn(0f, maxX)
        }
        if (dragY != 0 && abs(endY - startY) < 16f) {
            startY = (endY - dragY * distance).coerceIn(0f, maxY)
        }

        if (abs(endX - startX) + abs(endY - startY) < 5f) return

        val x1Int = startX.toInt()
        val y1Int = startY.toInt()
        val x2Int = endX.toInt()
        val y2Int = endY.toInt()

        Log.d(TAG, "Dispatching scroll swipe: ($x1Int, $y1Int) -> ($x2Int, $y2Int) over $SCROLL_SWIPE_DURATION_MS")
        RootTouchInjector.swipeAsync(x1Int, y1Int, x2Int, y2Int, SCROLL_SWIPE_DURATION_MS)
    }
}
