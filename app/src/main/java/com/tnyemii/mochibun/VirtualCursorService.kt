/**
 * System-wide virtual mouse cursor service for the Chinoe AT-M120 (Android 12).
 * Intercepts physical keypad and OEM side-key events to drive an on-screen pointer overlay,
 * translating discrete D-pad presses into continuous, fluid hardware-accelerated cursor motion.
 */
package com.tnyemii.mochibun

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

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

    // Zero-allocation continuous physics engine
    private val physics = CursorPhysics()
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
        // Zero-overhead: We deliberately ignore accessibility event streams.
        // On the low-end MediaTek MT6761, parsing window content changes or text events
        // would burn precious CPU cycles and cause noticeable cursor jank.
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
            view.updatePosition(physics.posX, physics.posY)
            Log.d(TAG, "Virtual cursor activated at (${physics.posX}, ${physics.posY})")
        } else {
            stopMovementTicker()
            view.visibility = View.GONE
            Log.d(TAG, "Virtual cursor hidden")
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

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

        // When virtual cursor is off, pass all hardware keys through untouched to the active app
        if (!isCursorVisible) {
            return false
        }

        // When cursor is visible, hijack D-pad directional navigation and click keys
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                handleDirectionKey(action, up = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                handleDirectionKey(action, down = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                handleDirectionKey(action, left = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handleDirectionKey(action, right = true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (action == KeyEvent.ACTION_DOWN) {
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
        action: Int,
        up: Boolean = false,
        down: Boolean = false,
        left: Boolean = false,
        right: Boolean = false
    ) {
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (up) physics.moveUp = true
                if (down) physics.moveDown = true
                if (left) physics.moveLeft = true
                if (right) physics.moveRight = true
                startMovementTickerIfNeeded()
            }
            KeyEvent.ACTION_UP -> {
                if (up) physics.moveUp = false
                if (down) physics.moveDown = false
                if (left) physics.moveLeft = false
                if (right) physics.moveRight = false

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
            physics.resetVelocity(baseSpeed)
            // Lock directly to display VSYNC frames
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopMovementTicker() {
        physics.moveUp = false
        physics.moveDown = false
        physics.moveLeft = false
        physics.moveRight = false
        physics.resetVelocity(baseSpeed)
        if (isMovementTickerActive) {
            isMovementTickerActive = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    /**
     * VSYNC-driven frame callback. Continuous physics velocity integration
     * with delta time, zero-allocation calculations, and GPU canvas translation.
     */
    override fun doFrame(frameTimeNanos: Long) {
        if (!isMovementTickerActive || !isCursorVisible) {
            isMovementTickerActive = false
            return
        }

        if (physics.hasActiveInput) {
            physics.tick(frameTimeNanos, baseSpeed, accelerationRate)
            overlayView?.updatePosition(physics.posX, physics.posY)
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
}
