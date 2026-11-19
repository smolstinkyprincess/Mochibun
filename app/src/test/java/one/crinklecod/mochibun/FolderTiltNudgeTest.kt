package one.crinklecod.mochibun

import one.crinklecod.mochibun.ui.dialogs.TiltNudgeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoothing / neutral-angle / clamping contract of the folder tray's sticker parallax. */
class FolderTiltNudgeTest {

    @Test
    fun firstReadingIsNeutralAndStillPhoneProducesNoUpdates() {
        val filter = TiltNudgeFilter()
        assertFalse(filter.update(1.2f, 8.5f)) // held at an angle: that's "rest"
        repeat(50) { assertFalse(filter.update(1.2f, 8.5f)) }
        assertEquals(0f, filter.tiltX, 0f)
        assertEquals(0f, filter.tiltY, 0f)
    }

    @Test
    fun handTremorIsSwallowed() {
        val filter = TiltNudgeFilter()
        filter.update(0f, 9f)
        var updates = 0
        repeat(100) { i ->
            val jitter = if (i % 2 == 0) 0.05f else -0.05f
            if (filter.update(jitter, 9f + jitter)) updates++
        }
        assertEquals(0, updates)
    }

    @Test
    fun sustainedTiltIsSmoothedTowardTargetAndClamped() {
        val filter = TiltNudgeFilter(alpha = 0.15f, fullScale = 3.5f)
        filter.update(0f, 9f)
        filter.update(1.75f, 9f) // half of full scale
        // One step of smoothing only moves 15% of the way.
        assertEquals(0.075f, filter.tiltX, 1e-4f)
        repeat(100) { filter.update(1.75f, 9f) }
        assertEquals(0.5f, filter.tiltX, 0.02f)

        repeat(100) { filter.update(-20f, 0f) } // way past full scale
        // Clamped to -1; the deadband (0.02) stops updates just short of the limit.
        assertEquals(-1f, filter.tiltX, 0.02f)
        assertEquals(-1f, filter.tiltY, 0.02f)
        repeat(100) { filter.update(-200f, -200f) }
        assertTrue(filter.tiltX >= -1f && filter.tiltY >= -1f)
    }

    @Test
    fun resetRecapturesNeutral() {
        val filter = TiltNudgeFilter()
        filter.update(0f, 9f)
        repeat(100) { filter.update(3f, 9f) }
        assertTrue(filter.tiltX > 0.5f)
        filter.reset()
        assertEquals(0f, filter.tiltX, 0f)
        assertFalse(filter.update(3f, 9f)) // new rest position
        repeat(20) { assertFalse(filter.update(3f, 9f)) }
    }

    @Test
    fun depthVariesPerSlot() {
        assertEquals(0.7f, TiltNudgeFilter.depthFor(0), 1e-6f)
        assertEquals(0.85f, TiltNudgeFilter.depthFor(1), 1e-6f)
        assertEquals(1f, TiltNudgeFilter.depthFor(2), 1e-6f)
        assertEquals(0.7f, TiltNudgeFilter.depthFor(3), 1e-6f)
    }
}
