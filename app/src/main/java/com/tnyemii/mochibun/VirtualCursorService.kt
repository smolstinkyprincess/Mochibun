package com.tnyemii.mochibun

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.hypot

/**
 * System-wide virtual mouse cursor service for Android 12 (API 31) and Chinoe AT-M120.
 * Intercepts physical hardware side-key (F4) to toggle the mouse, and redirects D-pad
 * navigation to smooth, accelerated on-screen cursor movement and touch injection.
 */
class VirtualCursorService : AccessibilityService(), Choreographer.FrameCallback {

    companion object {
        private const val TAG = "VirtualCursorService"

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var isCursorVisible = false
            private set

        private var instance: VirtualCursorService? = null

        fun reloadActiveConfig(context: Context) {
            instance?.reloadConfig()
        }

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
    private var baseSpeed = CursorPreferences.DEFAULT_MOVE_SPEED
    private var accelerationRate = CursorPreferences.DEFAULT_ACCELERATION
    private var clickMethod = ClickMethod.ACCESSIBILITY_GESTURE

    // Display bounds (AT-M120 default: 480 x 800)
    private var screenWidth = 480f
    private var screenHeight = 800f

    // Current cursor coordinates
    private var cursorX = 240f
    private var cursorY = 400f

    // Movement state
    private var moveUp = false
    private var moveDown = false
    private var moveLeft = false
    private var moveRight = false
    private var currentSpeed = CursorPreferences.DEFAULT_MOVE_SPEED
    private var isMovementTickerActive = false

    // Key event deduplication
    private var isToggleKeyDown = false

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadConfig()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels.toFloat().coerceAtLeast(480f)
        screenHeight = metrics.heightPixels.toFloat().coerceAtLeast(800f)

        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f

        prefs = CursorPreferences.getPrefs(this)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        reloadConfig()

        overlayView = CursorOverlayView(this).apply {
            visibility = View.GONE
        }

        Log.d(TAG, "VirtualCursorService connected on display ${screenWidth}x${screenHeight}")
    }

    override fun onDestroy() {
        isServiceRunning = false
        isCursorVisible = false
        instance = null

        stopMovementTicker()

        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)

        overlayView?.let { view ->
            if (isOverlayAttached) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
                isOverlayAttached = false
            }
            view.release()
        }
        overlayView = null

        RootTouchInjector.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero-overhead: No event processing needed for mouse movement
    }

    override fun onInterrupt() {
        stopMovementTicker()
    }

    private fun reloadConfig() {
        toggleKeyCode = CursorPreferences.getToggleKeyCode(this)
        baseSpeed = CursorPreferences.getMoveSpeed(this)
        accelerationRate = CursorPreferences.getAcceleration(this)
        clickMethod = CursorPreferences.getClickMethod(this)
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
            view.updatePosition(cursorX, cursorY, windowManager)
            Log.d(TAG, "Virtual cursor activated at ($cursorX, $cursorY)")
        } else {
            stopMovementTicker()
            view.visibility = View.GONE
            Log.d(TAG, "Virtual cursor hidden")
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // 1. Toggle key check (F4 or configured keycode) with ACTION_DOWN / ACTION_UP deduplication
        val isToggle = keyCode == toggleKeyCode ||
                (event.scanCode == 62 && toggleKeyCode == KeyEvent.KEYCODE_F4)

        if (isToggle) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!isToggleKeyDown && event.repeatCount == 0) {
                        isToggleKeyDown = true
                        mainHandler.post { toggleCursorVisibility() }
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    isToggleKeyDown = false
                    return true
                }
            }
        }

        // 2. If cursor is not visible, pass all hardware keys normally
        if (!isCursorVisible) {
            return false
        }

        // 3. Intercept D-pad navigation & Center click while cursor is visible
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                handleDirectionKey(KeyEvent.ACTION_DOWN == event.action, up = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                handleDirectionKey(KeyEvent.ACTION_DOWN == event.action, down = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                handleDirectionKey(KeyEvent.ACTION_DOWN == event.action, left = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handleDirectionKey(KeyEvent.ACTION_DOWN == event.action, right = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        performTapDispatch()
                    }
                }
                return true
            }
        }

        // Allow system back or other non-dpad keys to pass through
        return false
    }

    private fun handleDirectionKey(
        isDown: Boolean,
        up: Boolean = false,
        down: Boolean = false,
        left: Boolean = false,
        right: Boolean = false
    ) {
        if (up) moveUp = isDown
        if (down) moveDown = isDown
        if (left) moveLeft = isDown
        if (right) moveRight = isDown

        val hasDirection = moveUp || moveDown || moveLeft || moveRight
        if (hasDirection) {
            if (!isMovementTickerActive) {
                currentSpeed = baseSpeed
                isMovementTickerActive = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        } else {
            stopMovementTicker()
        }
    }

    private fun stopMovementTicker() {
        moveUp = false
        moveDown = false
        moveLeft = false
        moveRight = false
        currentSpeed = baseSpeed
        if (isMovementTickerActive) {
            isMovementTickerActive = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isMovementTickerActive || !isCursorVisible) {
            isMovementTickerActive = false
            return
        }

        var dirX = 0f
        var dirY = 0f
        if (moveLeft) dirX -= 1f
        if (moveRight) dirX += 1f
        if (moveUp) dirY -= 1f
        if (moveDown) dirY += 1f

        if (dirX != 0f || dirY != 0f) {
            val length = hypot(dirX, dirY)
            val normX = dirX / length
            val normY = dirY / length

            // Accelerate smoothly while holding down the physical key
            currentSpeed = (currentSpeed * accelerationRate).coerceAtMost(baseSpeed * 3.2f)

            cursorX = (cursorX + normX * currentSpeed).coerceIn(0f, screenWidth)
            cursorY = (cursorY + normY * currentSpeed).coerceIn(0f, screenHeight)

            overlayView?.updatePosition(cursorX, cursorY, windowManager)
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            stopMovementTicker()
        }
    }

    /**
     * Dispatches a tap at current cursor coordinates via AccessibilityService.dispatchGesture(),
     * falling back seamlessly to RootTouchInjector if dispatch fails or Root mode is selected.
     */
    private fun performTapDispatch() {
        val view = overlayView ?: return
        view.showClickFeedback()

        val tapX = view.getHotspotX().coerceIn(0f, screenWidth - 1f)
        val tapY = view.getHotspotY().coerceIn(0f, screenHeight - 1f)

        if (clickMethod == ClickMethod.ROOT_INPUT_TAP) {
            RootTouchInjector.tapAsync(tapX.toInt(), tapY.toInt())
            return
        }

        // Accessibility Gesture dispatch (primary path)
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
}
