package one.crinklecod.mochibun

import one.crinklecod.mochibun.ui.components.RoundRectWalker
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

/** Perimeter walker used to lay out the organza frill and pearl seam in KeitaiLaceContainer. */
class RoundRectWalkerTest {

    private val eps = 0.01f
    private val out = FloatArray(4)

    @Test
    fun lengthIsStraightEdgesPlusOneFullCircle() {
        // 100 x 60 box, inset 10 -> 80 x 40 rect, radius 10.
        val w = RoundRectWalker(inset = 10f, width = 100f, height = 60f, radius = 10f)
        val expected = 2 * 60f + 2 * 20f + (2 * PI * 10).toFloat()
        assertEquals(expected, w.length, eps)
    }

    @Test
    fun startsAtTopEdgeWithUpwardNormal() {
        val w = RoundRectWalker(inset = 10f, width = 100f, height = 60f, radius = 10f)
        w.at(0f, out)
        assertEquals(20f, out[0], eps)
        assertEquals(10f, out[1], eps)
        assertEquals(0f, out[2], eps)
        assertEquals(-1f, out[3], eps)
    }

    @Test
    fun everyPointLiesOnTheRectWithUnitNormal() {
        val w = RoundRectWalker(inset = 4f, width = 200f, height = 80f, radius = 16f)
        val steps = 400
        for (i in 0..steps) {
            w.at(w.length * i / steps, out)
            assertEquals("unit normal at step $i", 1f, hypot(out[2], out[3]), eps)
            // Points stay inside the inset rectangle bounds.
            assert(out[0] >= 4f - eps && out[0] <= 196f + eps) { "x out of bounds at $i" }
            assert(out[1] >= 4f - eps && out[1] <= 76f + eps) { "y out of bounds at $i" }
        }
    }

    @Test
    fun walkingTheFullLengthReturnsToStart() {
        val w = RoundRectWalker(inset = 0f, width = 50f, height = 50f, radius = 8f)
        w.at(w.length, out)
        assertEquals(8f, out[0], eps)
        assertEquals(0f, out[1], eps)
    }

    @Test
    fun oversizedRadiusIsClampedToAPill() {
        // 100 x 20 rect: radius can be at most 10 (a pill), so length = 2*80 + 2*PI*10.
        val w = RoundRectWalker(inset = 0f, width = 100f, height = 20f, radius = 50f)
        assertEquals(160f + (2 * PI * 10).toFloat(), w.length, eps)
    }
}
