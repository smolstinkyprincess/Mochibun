/**
 * System-wide virtual mouse cursor service for the Chinoe AT-M120 (Android 12).
 * Intercepts physical keypad and OEM side-key events to drive an on-screen pointer overlay,
 * translating discrete D-pad presses into continuous, fluid hardware-accelerated cursor motion.
 */
package com.crinklecodone.mochibun.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.crinklecodone.mochibun.core.system.ClamshellListener
import com.crinklecodone.mochibun.core.system.ClamshellManager
import com.crinklecodone.mochibun.core.system.RootTouchInjector
import com.crinklecodone.mochibun.core.util.CursorPhysics
import com.crinklecodone.mochibun.data.prefs.ClickMethod
import com.crinklecodone.mochibun.data.prefs.CursorPreferences
import com.crinklecodone.mochibun.ui.overlay.CursorOverlayView
import kotlin.math.abs

class VirtualCursorService : AccessibilityService(), Choreographer.FrameCallback {

    companion object {
        private const val TAG = "VirtualCursorService"
        private const val SCROLL_SWIPE_DURATION_MS = 200L
        private const val SCROLL_DEBOUNCE_MS = 220L

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var isCursorVisible = false
            private set

        private var instance: VirtualCursorService? = null

        fun toggleCursor() {
            instance?.toggleCursorVisibility()
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
    private var clickMethod = ClickMethod.ACCESSIBILITY_GESTURE

    // Zero-allocation continuous physics engine
    private val physics = CursorPhysics()
    private var isMovementTickerActive = false

    // Key event deduplication & scroll cadence debounce
    private var isToggleKeyDown = false
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true

        ClamshellManager.init(this)
        ClamshellManager.addListener(clamshellListener)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(480f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(800f)

        physics.setBounds(width, height)
        physics.setPosition(width / 2f, height / 2f)

        prefs = CursorPreferences.getPrefs(this)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        reloadConfig()

        overlayView = CursorOverlayView(this).apply {
            visibility = View.GONE
        }

        Log.d(TAG, "VirtualCursorService connected on display ${physics.screenWidth}x${physics.screenHeight}")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(480f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(800f)
        physics.setBounds(width, height)
    }

    private fun handleLidClosed() {
        Log.d(TAG, "Display asleep: suspending cursor ticker and auto-dismissing overlay")
        stopMovementTicker()
        overlayView?.pauseAnimation()
        if (isCursorVisible) {
            isCursorVisible = false
            overlayView?.visibility = View.GONE
        }
    }

    private fun handleLidOpened() {
        Log.d(TAG, "Display awake: refreshing display bounds")
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(480f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(800f)
        physics.setBounds(width, height)
    }

    override fun onDestroy() {
        isServiceRunning = false
        isCursorVisible = false
        instance = null
        InputArbiter.cursorToggleKeyCode = KeyEvent.KEYCODE_UNKNOWN
        mainHandler.removeCallbacks(toggleRunnable)

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

        RootTouchInjector.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero-overhead: We deliberately ignore accessibility event streams.
        // On the low-end MediaTek MT6761, parsing window content changes or text events
        // would burn precious CPU cycles and cause noticeable cursor jank.
    }

    override fun onInterrupt() {
        stopMovementTicker()
        overlayView?.pauseAnimation()
    }

    private fun reloadConfig() {
        toggleKeyCode = CursorPreferences.getToggleKeyCode(this)
        physics.stepPx = CursorPreferences.getTapStepPx(this).toFloat()
        physics.maxSpeed = CursorPreferences.getMaxSpeed(this).toFloat()
        physics.accelRate = CursorPhysics.accelRateForRampMs(CursorPreferences.getAccelRampMs(this))
        scrollStepPx = CursorPreferences.getScrollStepPx(this)
        clickMethod = CursorPreferences.getClickMethod(this)
        InputArbiter.cursorToggleKeyCode = toggleKeyCode
        overlayView?.reloadConfiguration()
    }

    fun toggleCursorVisibility() {
        isCursorVisible = !isCursorVisible
        val view = overlayView ?: return

        if (isCursorVisible) {
            if (!isOverlayAttached) {
                try {
                    windowManager.addView(view, view.windowLayoutParams)
                    isOverlayAttached = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to attach cursor overlay window: ${e.message}")
                    isCursorVisible = false
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
            Log.d(TAG, "Virtual cursor hidden")
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val keyCode = event.keyCode

        if (InputArbiter.isNewPress(event)) {
            // Same rule MochibunAccessibilityService applies, so only one of us claims the press
            if (!InputArbiter.cursorTakesPress(event)) {
                if (InputArbiter.ownerOf(keyCode) == InputLayer.CURSOR_OVERLAY) {
                    InputArbiter.setOwner(keyCode, InputLayer.NONE)
                }
                return false
            }
            InputArbiter.setOwner(keyCode, InputLayer.CURSOR_OVERLAY)
        } else if (InputArbiter.ownerOf(keyCode) != InputLayer.CURSOR_OVERLAY) {
            // Repeat or release of a press someone else took (or that predates the cursor)
            return false
        }

        // From here on the press is ours: consume every event of it, even if the cursor was
        // hidden mid-press, so a stray release never leaks to the app underneath.
        handleOwnedKey(event)
        return true
    }

    private fun handleOwnedKey(event: KeyEvent) {
        val keyCode = event.keyCode
        val action = event.action

        // The AT-M120 physical side key maps to KEYCODE_F4 (raw scan code 62).
        // We deduplicate repeat events so holding the key doesn't strobe the cursor on and off.
        val isToggle = keyCode == toggleKeyCode ||
                (event.scanCode == 62 && toggleKeyCode == KeyEvent.KEYCODE_F4)

        if (isToggle) {
            when (action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!isToggleKeyDown && event.repeatCount == 0) {
                        isToggleKeyDown = true
                        mainHandler.post(toggleRunnable)
                    }
                }
                KeyEvent.ACTION_UP -> isToggleKeyDown = false
            }
            return
        }

        // A press that started while visible: still swallow its tail after the cursor hides
        if (!isCursorVisible) return

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> handleDirectionKey(event, dx = 0, dy = -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> handleDirectionKey(event, dx = 0, dy = 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> handleDirectionKey(event, dx = -1, dy = 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> handleDirectionKey(event, dx = 1, dy = 0)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    performTapDispatch()
                }
            }
            // Scroll keys drag the finger opposite to the scroll direction, like a touch swipe
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

    private fun handleDirectionKey(event: KeyEvent, dx: Int, dy: Int) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Auto-repeats are still consumed (the press is ours) but add no motion:
                // the frame loop already drives a held key.
                if (event.repeatCount != 0) return
                physics.pressDirection(dx, dy)
                // Show the tap micro-step right away instead of waiting for the next frame
                overlayView?.updatePosition(physics.posX, physics.posY)
                startMovementTickerIfNeeded()
            }
            KeyEvent.ACTION_UP -> {
                physics.releaseDirection(dx, dy)

                // Dead-stop immediately when all directional keys are released
                if (!physics.hasActiveInput) {
                    stopMovementTicker()
                }
            }
        }
    }

    private fun startMovementTickerIfNeeded() {
        if (!isMovementTickerActive && isCursorVisible) {
            isMovementTickerActive = true
            // Lock directly to display VSYNC frames
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
     * Dispatches a tap at current cursor coordinates.
     *
     * We use AccessibilityService.dispatchGesture() as the primary low-latency, unprivileged path.
     * However, certain overlays, system permission dialogs, or secure windows cancel or reject
     * accessibility gestures. If dispatch fails or gets cancelled, we gracefully fall back to
     * RootTouchInjector (interactive 'su' session running 'input tap') so clicks never silently fail.
     */
    private fun performTapDispatch() {
        val view = overlayView ?: return
        view.showClickFeedback()

        val tapX = view.getHotspotX().coerceIn(0f, physics.screenWidth - 1f)
        val tapY = view.getHotspotY().coerceIn(0f, physics.screenHeight - 1f)

        if (clickMethod == ClickMethod.ROOT_INPUT_TAP) {
            RootTouchInjector.tapAsync(tapX.toInt(), tapY.toInt())
            return
        }

        // Accessibility Gesture dispatch (primary path: fast, no root required)
        val path = Path().apply {
            moveTo(tapX, tapY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Accessibility gesture cancelled at ($tapX, $tapY), falling back to root tap")
                RootTouchInjector.tapAsync(tapX.toInt(), tapY.toInt())
            }
        }, null)

        if (!dispatched) {
            Log.w(TAG, "dispatchGesture() returned false at ($tapX, $tapY), falling back to root tap")
            RootTouchInjector.tapAsync(tapX.toInt(), tapY.toInt())
        }
    }

    /**
     * Dispatches a one-step scroll swipe from the current pointer coordinates. The finger drags in
     * direction ([dragX], [dragY]), one of them ±1, which scrolls the content the opposite way:
     *
     * - Key 2 (0, +1): downward drag to scroll UP.      Key 8 (0, -1): upward drag to scroll DOWN.
     * - Key 4 (+1, 0): rightward drag to scroll LEFT.   Key 6 (-1, 0): leftward drag to scroll RIGHT.
     *
     * Clamps start and end coordinates to screen bounds (0f..480f, 0f..800f) so swipes remain on-screen.
     */
    private fun performScrollDispatch(dragX: Int, dragY: Int) {
        val view = overlayView ?: return

        val maxX = (physics.screenWidth - 1f).coerceAtLeast(1f)
        val maxY = (physics.screenHeight - 1f).coerceAtLeast(1f)

        var startX = view.getHotspotX().coerceIn(0f, maxX)
        var startY = view.getHotspotY().coerceIn(0f, maxY)
        val distance = scrollStepPx.toFloat().coerceIn(
            CursorPreferences.MIN_SCROLL_STEP_PX.toFloat(),
            CursorPreferences.MAX_SCROLL_STEP_PX.toFloat()
        )

        val endX = (startX + dragX * distance).coerceIn(0f, maxX)
        val endY = (startY + dragY * distance).coerceIn(0f, maxY)

        // If cursor is parked against the screen edge in the swipe direction,
        // shift the stroke start inward so the swipe still has travel distance.
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

        if (clickMethod == ClickMethod.ROOT_INPUT_TAP) {
            RootTouchInjector.swipeAsync(x1Int, y1Int, x2Int, y2Int, SCROLL_SWIPE_DURATION_MS)
            return
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SCROLL_SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Accessibility scroll gesture cancelled at ($startX, $startY)->($endX, $endY), falling back to root swipe")
                RootTouchInjector.swipeAsync(x1Int, y1Int, x2Int, y2Int, SCROLL_SWIPE_DURATION_MS)
            }
        }, null)

        if (!dispatched) {
            Log.w(TAG, "dispatchGesture() scroll returned false at ($startX, $startY)->($endX, $endY), falling back to root swipe")
            RootTouchInjector.swipeAsync(x1Int, y1Int, x2Int, y2Int, SCROLL_SWIPE_DURATION_MS)
        }
    }
}
