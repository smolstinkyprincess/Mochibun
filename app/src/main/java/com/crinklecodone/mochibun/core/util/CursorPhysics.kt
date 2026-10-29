package com.crinklecodone.mochibun.core.util

import kotlin.math.exp

/**
 * Zero-allocation kinetic physics for the D-pad virtual cursor on the Chinoe AT-M120
 * (480x800 display, MediaTek MT6761). Driven by Choreographer VSYNC frames.
 *
 * Two cadences:
 *  - Tap: a new press nudges the cursor by exactly [stepPx] right away. If the key is let go
 *    within [TAP_WINDOW_SEC] that nudge is the whole movement, so tiny targets are easy to hit.
 *  - Hold: once a key is held past the tap window, speed ramps from the base speed
 *    ([stepPx] per 60 Hz frame) towards [maxSpeed]:
 *        v(t) = vBase + (vMax - vBase) * (1 - e^(-k * t))
 *    where t is the time since the hold began and k = [accelRate].
 *
 * Distance per frame is the exact integral of v(t) over that frame, so the path is the same at
 * 60 Hz, 30 Hz or with a dropped frame. Positions are floats, so slow movement keeps its
 * sub-pixel progress between frames.
 *
 * Releasing every direction is a dead stop (velocity 0, no glide) so the cursor never
 * overshoots the button it was aimed at.
 */
class CursorPhysics(
    var screenWidth: Float = 480f,
    var screenHeight: Float = 800f
) {
    companion object {
        /** Presses shorter than this only produce the single micro-step. */
        const val TAP_WINDOW_SEC = 0.12f

        private const val DIAGONAL = 0.70710677f // 1 / sqrt(2)
        private const val NOMINAL_FRAME_SEC = 1f / 60f
        private const val MAX_FRAME_SEC = 0.05f // caps lag spikes so the cursor never teleports

        /**
         * Converts "time to reach full speed" into the curve's k. After 3/k seconds only
         * e^-3 (about 5%) of the gap to [maxSpeed] is left, which feels like "full speed".
         */
        fun accelRateForRampMs(rampMs: Int): Float = 3000f / rampMs.coerceAtLeast(1)
    }

    var posX: Float = 240f
        private set
    var posY: Float = 400f
        private set

    /** Current hold speed in px/s. 0 while idle or still inside the tap window. */
    var velocity: Float = 0f
        private set

    // Tuning, set by VirtualCursorService from CursorPreferences
    var stepPx: Float = 3f
    var maxSpeed: Float = 650f
    var accelRate: Float = 4f

    // Directional key states
    var moveUp: Boolean = false
        private set
    var moveDown: Boolean = false
        private set
    var moveLeft: Boolean = false
        private set
    var moveRight: Boolean = false
        private set

    // Time since the first direction of the current gesture went down
    private var holdTimeSec: Float = 0f
    private var lastFrameTimeNanos: Long = 0L

    val hasActiveInput: Boolean
        get() = moveUp || moveDown || moveLeft || moveRight

    /** Hold speed at the very start of the ramp: one [stepPx] per 60 Hz frame. */
    val baseSpeed: Float
        get() = stepPx * 60f

    fun setPosition(x: Float, y: Float) {
        posX = x.coerceIn(0f, screenWidth)
        posY = y.coerceIn(0f, screenHeight)
    }

    fun setBounds(width: Float, height: Float) {
        screenWidth = width.coerceAtLeast(1f)
        screenHeight = height.coerceAtLeast(1f)
        posX = posX.coerceIn(0f, screenWidth)
        posY = posY.coerceIn(0f, screenHeight)
    }

    /**
     * A direction key went down (call once per press, not for auto-repeats).
     * [dx] / [dy] are -1, 0 or 1. Inside the tap window the cursor jumps one [stepPx] at once.
     */
    fun pressDirection(dx: Int, dy: Int) {
        val alreadyHeld = (dx < 0 && moveLeft) || (dx > 0 && moveRight) ||
                (dy < 0 && moveUp) || (dy > 0 && moveDown)
        if (dx < 0) moveLeft = true
        if (dx > 0) moveRight = true
        if (dy < 0) moveUp = true
        if (dy > 0) moveDown = true

        // Once the hold ramp is running, a newly added direction just bends the path
        if (!alreadyHeld && holdTimeSec < TAP_WINDOW_SEC) {
            posX = (posX + dx * stepPx).coerceIn(0f, screenWidth)
            posY = (posY + dy * stepPx).coerceIn(0f, screenHeight)
        }
    }

    /** A direction key went up. Releasing the last one stops the cursor dead. */
    fun releaseDirection(dx: Int, dy: Int) {
        if (dx < 0) moveLeft = false
        if (dx > 0) moveRight = false
        if (dy < 0) moveUp = false
        if (dy > 0) moveDown = false
        if (!hasActiveInput) releaseAll()
    }

    /** Clears every direction and settles velocity to zero. */
    fun releaseAll() {
        moveUp = false
        moveDown = false
        moveLeft = false
        moveRight = false
        velocity = 0f
        holdTimeSec = 0f
        lastFrameTimeNanos = 0L
    }

    /**
     * Advances the hold motion to the VSYNC time [frameTimeNanos]. No allocations.
     *
     * @return true if the position changed this frame
     */
    fun tick(frameTimeNanos: Long): Boolean {
        if (!hasActiveInput) {
            releaseAll()
            return false
        }

        val dt = if (lastFrameTimeNanos > 0L) {
            ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_SEC)
        } else {
            NOMINAL_FRAME_SEC
        }
        lastFrameTimeNanos = frameTimeNanos
        holdTimeSec += dt

        // Ramp time at the end of this frame, and the part of this frame spent past the tap window
        val rampEnd = holdTimeSec - TAP_WINDOW_SEC
        if (rampEnd <= 0f) return false
        val rampStart = (rampEnd - dt).coerceAtLeast(0f)
        val moveDt = rampEnd - rampStart

        val vBase = baseSpeed
        val vMax = maxSpeed.coerceAtLeast(vBase)
        val k = accelRate.coerceAtLeast(0.01f)
        val decayStart = exp(-k * rampStart)
        val decayEnd = exp(-k * rampEnd)
        velocity = vBase + (vMax - vBase) * (1f - decayEnd)

        var dirX = 0f
        var dirY = 0f
        if (moveLeft) dirX -= 1f
        if (moveRight) dirX += 1f
        if (moveUp) dirY -= 1f
        if (moveDown) dirY += 1f
        if (dirX == 0f && dirY == 0f) return false // opposite keys cancel out
        if (dirX != 0f && dirY != 0f) {
            dirX *= DIAGONAL
            dirY *= DIAGONAL
        }

        // Exact integral of v(t) from rampStart to rampEnd
        val distance = vBase * moveDt + (vMax - vBase) * (moveDt - (decayStart - decayEnd) / k)

        val oldX = posX
        val oldY = posY
        posX = (posX + dirX * distance).coerceIn(0f, screenWidth)
        posY = (posY + dirY * distance).coerceIn(0f, screenHeight)
        return posX != oldX || posY != oldY
    }
}
