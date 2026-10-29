package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.util.CursorPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.hypot

class CursorPhysicsTest {

    private companion object {
        const val FRAME_60HZ = 16_666_667L
        const val FRAME_30HZ = 33_333_333L
    }

    private lateinit var physics: CursorPhysics
    private var nowNanos = 0L

    @Before
    fun setUp() {
        physics = CursorPhysics(screenWidth = 480f, screenHeight = 800f)
        physics.stepPx = 3f
        physics.maxSpeed = 650f
        physics.accelRate = CursorPhysics.accelRateForRampMs(750)
        physics.setPosition(240f, 400f)
        nowNanos = 1_000_000_000L
    }

    /** Runs [count] VSYNC frames [frameNanos] apart. */
    private fun runFrames(count: Int, frameNanos: Long = FRAME_60HZ) {
        repeat(count) {
            nowNanos += frameNanos
            physics.tick(nowNanos)
        }
    }

    @Test
    fun `tap yields a single crisp micro-step`() {
        physics.pressDirection(1, 0)
        assertEquals("Press should nudge by stepPx at once", 243f, physics.posX, 0.001f)

        // A 100 ms tap stays inside the 120 ms tap window: no further motion
        runFrames(6)
        assertEquals(243f, physics.posX, 0.001f)
        assertEquals(0f, physics.velocity, 0.001f)

        physics.releaseDirection(1, 0)
        assertEquals(243f, physics.posX, 0.001f)
        assertEquals(400f, physics.posY, 0.001f)
    }

    @Test
    fun `repeated press of an already held key does not step again`() {
        physics.pressDirection(0, -1)
        physics.pressDirection(0, -1)
        assertEquals(397f, physics.posY, 0.001f)
    }

    @Test
    fun `sustained hold ramps smoothly from base speed up to max speed`() {
        physics.setPosition(240f, 0f)
        physics.pressDirection(0, 1)

        // Just past the tap window the cursor moves near base speed (stepPx per 60 Hz frame)
        runFrames(8)
        assertTrue(
            "Speed just after the tap window should be near base, got ${physics.velocity}",
            physics.velocity >= physics.baseSpeed && physics.velocity < physics.baseSpeed + 40f
        )

        var previous = physics.velocity
        repeat(180) { // ~3 s
            nowNanos += FRAME_60HZ
            physics.tick(nowNanos)
            assertTrue("Velocity must never drop while holding", physics.velocity >= previous)
            assertTrue("Velocity must never exceed maxSpeed", physics.velocity <= physics.maxSpeed)
            previous = physics.velocity
        }
        assertEquals(physics.maxSpeed, physics.velocity, 0.5f)
    }

    @Test
    fun `movement is independent of frame rate`() {
        physics.setPosition(100f, 0f)
        physics.pressDirection(0, 1)
        runFrames(1 + 60, FRAME_60HZ)
        val at60Hz = physics.posY

        physics.releaseAll()
        physics.setPosition(100f, 0f)
        physics.pressDirection(0, 1)
        runFrames(1, FRAME_60HZ)
        runFrames(30, FRAME_30HZ)
        val at30Hz = physics.posY

        assertTrue("Hold should have travelled far, got $at60Hz", at60Hz > 300f)
        assertEquals(at60Hz, at30Hz, 0.05f)
    }

    @Test
    fun `cursor coordinates are strictly clamped within display viewport`() {
        physics.setPosition(478f, 798f)
        physics.pressDirection(1, 0)
        physics.pressDirection(0, 1)
        assertEquals(480f, physics.posX, 0.001f)
        assertEquals(800f, physics.posY, 0.001f)

        repeat(120) {
            nowNanos += FRAME_60HZ
            physics.tick(nowNanos)
            assertTrue(physics.posX in 0f..480f)
            assertTrue(physics.posY in 0f..800f)
        }
        assertEquals(480f, physics.posX, 0.001f)
        assertEquals(800f, physics.posY, 0.001f)

        physics.releaseAll()
        physics.setPosition(2f, 2f)
        physics.pressDirection(-1, 0)
        physics.pressDirection(0, -1)
        repeat(120) {
            nowNanos += FRAME_60HZ
            physics.tick(nowNanos)
            assertTrue(physics.posX in 0f..480f)
            assertTrue(physics.posY in 0f..800f)
        }
        assertEquals(0f, physics.posX, 0.001f)
        assertEquals(0f, physics.posY, 0.001f)
    }

    @Test
    fun `releasing keys settles velocity to zero with a dead stop`() {
        physics.setPosition(240f, 0f)
        physics.pressDirection(0, 1)
        runFrames(60)
        assertTrue(physics.velocity > physics.baseSpeed)

        physics.releaseDirection(0, 1)
        assertFalse(physics.hasActiveInput)
        assertEquals(0f, physics.velocity, 0.0f)

        val xBefore = physics.posX
        val yBefore = physics.posY
        nowNanos += FRAME_60HZ
        assertFalse(physics.tick(nowNanos))
        assertEquals(xBefore, physics.posX, 0.0f)
        assertEquals(yBefore, physics.posY, 0.0f)

        // The next press starts a fresh gesture: tap step again, no leftover speed
        physics.pressDirection(0, 1)
        assertEquals(yBefore + 3f, physics.posY, 0.001f)
        assertEquals(0f, physics.velocity, 0.0f)
    }

    @Test
    fun `diagonal movement vector is normalized to cardinal magnitude`() {
        physics.pressDirection(1, 0)
        runFrames(30)
        val cardinal = physics.posX - 243f

        physics.releaseAll()
        physics.setPosition(240f, 400f)
        physics.pressDirection(1, 0)
        physics.pressDirection(0, 1)
        runFrames(30)
        val dx = physics.posX - 243f
        val dy = physics.posY - 403f

        assertEquals(dx, dy, 0.001f)
        assertEquals(cardinal, hypot(dx, dy), 0.01f)
    }

    @Test
    fun `opposite keys cancel out`() {
        physics.pressDirection(-1, 0)
        physics.pressDirection(1, 0)
        runFrames(60)
        assertEquals(240f, physics.posX, 0.001f)
        assertEquals(400f, physics.posY, 0.001f)
    }
}
