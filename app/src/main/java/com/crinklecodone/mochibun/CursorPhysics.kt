package com.crinklecodone.mochibun

import kotlin.math.pow

/**
 * High-performance, zero-allocation continuous physics engine for virtual mouse cursor movement.
 * Replaces discrete step jumps with a fluid delta-time velocity loop tailored for the
 * Chinoe AT-M120 (480x800 display, MediaTek MT6761 quad-core processor).
 */
class CursorPhysics(
    var screenWidth: Float = 480f,
    var screenHeight: Float = 800f
) {
    var posX: Float = 240f
        private set
    var posY: Float = 400f
        private set

    var currentSpeed: Float = 120f
        private set

    private var lastFrameTimeNanos: Long = 0L

    // Continuous directional key states
    var moveUp: Boolean = false
    var moveDown: Boolean = false
    var moveLeft: Boolean = false
    var moveRight: Boolean = false

    val hasActiveInput: Boolean
        get() = moveUp || moveDown || moveLeft || moveRight

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

    fun resetVelocity(baseSpeed: Float) {
        lastFrameTimeNanos = 0L
        currentSpeed = calculateBaseVelocity(baseSpeed)
    }

    fun calculateBaseVelocity(baseSpeed: Float): Float {
        // Base velocity in px/s: Default baseSpeed = 10 -> 120 px/s (~2 px/frame at 60fps for single taps)
        return (baseSpeed * 12f).coerceAtLeast(10f)
    }

    fun calculateMaxSpeed(baseSpeed: Float): Float {
        // Upper bound for accelerated gliding across 480x800 viewport
        return (baseSpeed * 65f).coerceIn(200f, 1800f)
    }

    /**
     * Integrates cursor motion over elapsed delta-time between Choreographer VSYNC frames.
     * Uses zero object allocations for smooth 60 FPS performance on the MT6761.
     *
     * @return true if coordinates were updated, false if idle
     */
    fun tick(
        frameTimeNanos: Long,
        baseSpeed: Float,
        accelerationRate: Float
    ): Boolean {
        var dirX = 0f
        var dirY = 0f
        if (moveLeft) dirX -= 1f
        if (moveRight) dirX += 1f
        if (moveUp) dirY -= 1f
        if (moveDown) dirY += 1f

        if (dirX == 0f && dirY == 0f) {
            resetVelocity(baseSpeed)
            return false
        }

        // Frame-accurate delta time calculation (capped to prevent teleportation on lag spikes)
        val dt = if (lastFrameTimeNanos > 0L) {
            ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        } else {
            1f / 60f
        }
        lastFrameTimeNanos = frameTimeNanos

        // Normalize direction vector (constant for diagonal movements avoids runtime sqrt/hypot)
        val normX: Float
        val normY: Float
        if (dirX != 0f && dirY != 0f) {
            normX = dirX * 0.70710677f
            normY = dirY * 0.70710677f
        } else {
            normX = dirX
            normY = dirY
        }

        // Frame-rate independent exponential acceleration curve up to max speed
        val maxSpeed = calculateMaxSpeed(baseSpeed)
        val factor = accelerationRate.toDouble().pow((dt * 60.0)).toFloat()
        currentSpeed = (currentSpeed * factor).coerceAtMost(maxSpeed)

        // Integrate position and strictly clamp within viewport
        val step = currentSpeed * dt
        posX = (posX + normX * step).coerceIn(0f, screenWidth)
        posY = (posY + normY * step).coerceIn(0f, screenHeight)

        return true
    }
}
