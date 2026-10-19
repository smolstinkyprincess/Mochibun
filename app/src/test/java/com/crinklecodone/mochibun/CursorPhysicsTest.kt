package com.tnyemii.mochibun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.hypot

class CursorPhysicsTest {

    private lateinit var physics: CursorPhysics

    @Before
    fun setUp() {
        physics = CursorPhysics(screenWidth = 480f, screenHeight = 800f)
        physics.setPosition(240f, 400f)
    }

    @Test
    fun `initial tap moves small distance for pixel-art alignment`() {
        val baseSpeed = 10f // Default setting
        physics.resetVelocity(baseSpeed)
        physics.moveRight = true

        val nanosPerFrame = 16_666_666L // 1/60th second
        val updated = physics.tick(nanosPerFrame, baseSpeed, 1.08f)

        assertTrue(updated)
        // At 10 baseSpeed, baseVelocity is 120 px/s -> ~2.0 px per frame
        val deltaX = physics.posX - 240f
        assertTrue("Expected small initial step of ~2px, got $deltaX", deltaX in 1.8f..2.2f)
        assertEquals(400f, physics.posY, 0.001f)
    }

    @Test
    fun `holding directional key ramps velocity smoothly towards maxSpeed`() {
        val baseSpeed = 10f
        val accelerationRate = 1.08f
        physics.resetVelocity(baseSpeed)
        physics.moveDown = true

        var currentNanos = 16_666_666L
        val initialSpeed = physics.currentSpeed

        // Simulate holding key for 30 consecutive frames (~0.5s)
        for (i in 1..30) {
            currentNanos += 16_666_666L
            physics.tick(currentNanos, baseSpeed, accelerationRate)
        }

        assertTrue(
            "Current speed (${physics.currentSpeed}) should be significantly higher than initial speed ($initialSpeed)",
            physics.currentSpeed > initialSpeed * 3f
        )
        val maxSpeed = physics.calculateMaxSpeed(baseSpeed)
        assertTrue(
            "Speed should not exceed maxSpeed ($maxSpeed), got ${physics.currentSpeed}",
            physics.currentSpeed <= maxSpeed
        )
    }

    @Test
    fun `releasing keys halts movement instantly with dead stop`() {
        val baseSpeed = 10f
        physics.resetVelocity(baseSpeed)
        physics.moveUp = true

        physics.tick(16_666_666L, baseSpeed, 1.08f)
        assertTrue(physics.hasActiveInput)

        // User releases D-pad
        physics.moveUp = false
        assertFalse(physics.hasActiveInput)

        val posXBefore = physics.posX
        val posYBefore = physics.posY

        // Subsequent tick should return false and not displace the cursor
        val updated = physics.tick(33_333_333L, baseSpeed, 1.08f)
        assertFalse(updated)
        assertEquals(posXBefore, physics.posX, 0.001f)
        assertEquals(posYBefore, physics.posY, 0.001f)
    }

    @Test
    fun `diagonal movement vector is normalized to cardinal magnitude`() {
        val baseSpeed = 10f
        physics.resetVelocity(baseSpeed)
        physics.moveRight = true
        physics.moveDown = true

        val nanosPerFrame = 16_666_666L
        physics.tick(nanosPerFrame, baseSpeed, 1.00f) // acceleration 1.0 to isolate magnitude

        val dx = physics.posX - 240f
        val dy = physics.posY - 400f
        val totalDistance = hypot(dx, dy)

        // At 120 px/s for 1/60s, total distance traveled diagonally should be ~2.0 px
        assertEquals(2.0f, totalDistance, 0.05f)
        // dx and dy should be equal
        assertEquals(dx, dy, 0.001f)
    }

    @Test
    fun `cursor coordinates are strictly clamped within display viewport`() {
        val baseSpeed = 50f
        physics.setPosition(475f, 795f)
        physics.moveRight = true
        physics.moveDown = true

        var currentNanos = 16_666_666L
        for (i in 1..20) {
            currentNanos += 16_666_666L
            physics.tick(currentNanos, baseSpeed, 1.2f)
        }

        assertEquals(480f, physics.posX, 0.001f)
        assertEquals(800f, physics.posY, 0.001f)

        // Test upper-left clamping
        physics.setPosition(5f, 5f)
        physics.moveRight = false
        physics.moveDown = false
        physics.moveLeft = true
        physics.moveUp = true

        for (i in 1..20) {
            currentNanos += 16_666_666L
            physics.tick(currentNanos, baseSpeed, 1.2f)
        }

        assertEquals(0f, physics.posX, 0.001f)
        assertEquals(0f, physics.posY, 0.001f)
    }
}
