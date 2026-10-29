package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.widget.NavDirection
import com.crinklecodone.mochibun.core.widget.TargetBounds
import com.crinklecodone.mochibun.core.widget.WidgetLayoutMath
import com.crinklecodone.mochibun.core.widget.WidgetTargetNavigator
import com.crinklecodone.mochibun.data.repository.WidgetRepository
import com.crinklecodone.mochibun.data.repository.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WidgetHostTest {

    // AT-M120: 480 px wide at hdpi (1.5x), card = 480 - 2 * 14dp padding = 438 px
    private val cardWidth = 438
    private val floor = 96 // 64dp
    private val maxHeight = 510 // 340dp

    // ── Sizing ────────────────────────────────────────────────────────

    @Test
    fun `widget narrower than the card is not scaled`() {
        val spec = WidgetLayoutMath.computeSize(cardWidth, 300, 165, 0, false, floor, maxHeight)
        assertEquals(1f, spec.scale, 0.0001f)
        assertEquals(cardWidth, spec.logicalWidthPx)
        assertEquals(165, spec.logicalHeightPx)
    }

    @Test
    fun `wide 1080p-style widget shrinks to fill the card exactly`() {
        // A 400dp-wide widget arrives as 600 px at hdpi, wider than the 438 px card
        val spec = WidgetLayoutMath.computeSize(cardWidth, 600, 180, 0, false, floor, maxHeight)
        assertEquals(cardWidth / 600f, spec.scale, 0.001f)
        assertEquals(600, spec.logicalWidthPx)
        assertTrue(abs(spec.displayWidthPx - cardWidth) <= 1)
        assertTrue(abs(spec.displayHeightPx - 180 * spec.scale) <= 1f)
    }

    @Test
    fun `horizontally resizable widget uses min resize width instead of scaling`() {
        val spec = WidgetLayoutMath.computeSize(cardWidth, 600, 180, 400, true, floor, maxHeight)
        assertEquals(1f, spec.scale, 0.0001f)
        assertEquals(cardWidth, spec.logicalWidthPx)
    }

    @Test
    fun `min resize width is ignored when the widget is not resizable`() {
        val spec = WidgetLayoutMath.computeSize(cardWidth, 600, 180, 400, false, floor, maxHeight)
        assertTrue(spec.scale < 1f)
    }

    @Test
    fun `zero height widget gets the floor height`() {
        val spec = WidgetLayoutMath.computeSize(cardWidth, 200, 0, 0, false, floor, maxHeight)
        assertEquals(floor, spec.logicalHeightPx)
    }

    @Test
    fun `very tall widget is capped to the max display height`() {
        val spec = WidgetLayoutMath.computeSize(cardWidth, 200, 900, 0, false, floor, maxHeight)
        assertTrue(spec.displayHeightPx <= maxHeight + 1)
        assertTrue(abs(spec.displayWidthPx - cardWidth) <= 1)
    }

    @Test
    fun `scale never drops below the readable minimum`() {
        val spec = WidgetLayoutMath.computeSize(cardWidth, 5000, 180, 0, false, floor, maxHeight)
        assertEquals(WidgetLayoutMath.MIN_SCALE, spec.scale, 0.0001f)
    }

    // ── D-pad navigation between widget controls ─────────────────────

    // Music widget style: [prev][play][next] on one row, [open app] full-width row below
    private val prev = TargetBounds(0, 0, 100, 60)
    private val play = TargetBounds(110, 0, 210, 60)
    private val next = TargetBounds(220, 0, 320, 60)
    private val openApp = TargetBounds(0, 80, 320, 140)
    private val controls = listOf(prev, play, next, openApp)

    @Test
    fun `start selection is the top-left control`() {
        assertEquals(2, WidgetTargetNavigator.findStart(listOf(openApp, next, prev, play)))
        assertEquals(-1, WidgetTargetNavigator.findStart(emptyList()))
    }

    @Test
    fun `right walks along the row then stops at the edge`() {
        assertEquals(1, WidgetTargetNavigator.findNext(controls, 0, NavDirection.RIGHT))
        assertEquals(2, WidgetTargetNavigator.findNext(controls, 1, NavDirection.RIGHT))
        assertEquals(-1, WidgetTargetNavigator.findNext(controls, 2, NavDirection.RIGHT))
    }

    @Test
    fun `right prefers the same row over a closer control below`() {
        // openApp's centre (160, 110) is right of prev's (50, 30) but on another row
        assertEquals(1, WidgetTargetNavigator.findNext(controls, 0, NavDirection.RIGHT))
    }

    @Test
    fun `down from the row reaches the full-width button and up returns to the row`() {
        assertEquals(3, WidgetTargetNavigator.findNext(controls, 1, NavDirection.DOWN))
        assertEquals(1, WidgetTargetNavigator.findNext(controls, 3, NavDirection.UP))
        assertEquals(-1, WidgetTargetNavigator.findNext(controls, 3, NavDirection.DOWN))
        assertEquals(-1, WidgetTargetNavigator.findNext(controls, 0, NavDirection.UP))
    }

    @Test
    fun `start skips a background container that wraps real buttons`() {
        val background = TargetBounds(0, 0, 320, 140)
        assertEquals(1, WidgetTargetNavigator.findStart(listOf(background, prev, play)))
        // A container on its own is still selectable
        assertEquals(0, WidgetTargetNavigator.findStart(listOf(background)))
    }

    @Test
    fun `same-size nested targets collapse to the innermost one`() {
        val root = TargetBounds(0, 0, 320, 140)
        val innerSameSize = TargetBounds(2, 1, 319, 140)
        val greet = TargetBounds(40, 100, 280, 130)
        assertEquals(listOf(1, 2), WidgetTargetNavigator.collapseDuplicates(listOf(root, innerSameSize, greet), 4))
        assertEquals(listOf(0, 1, 2), WidgetTargetNavigator.collapseDuplicates(controls.take(3), 4))
    }

    @Test
    fun `closest keeps the selection near where it was after a redraw`() {
        val moved = listOf(TargetBounds(0, 0, 100, 60), TargetBounds(115, 2, 215, 62))
        assertEquals(1, WidgetTargetNavigator.findClosest(moved, play))
    }

    // ── Persistence ───────────────────────────────────────────────────

    @Test
    fun `decodes legacy csv without providers`() {
        val slots = WidgetRepository.decodeSlots("12,7,,abc,12,-3,9", "")
        assertEquals(listOf(12, 7, 9), slots.map { it.appWidgetId })
        assertTrue(slots.all { it.provider == null })
    }

    @Test
    fun `providers round trip with ids`() {
        val slots = listOf(
            WidgetSlot(5, "com.example.clock/.ClockWidget"),
            WidgetSlot(8, null),
            WidgetSlot(3, "com.example.music/.MusicWidget")
        )
        val decoded = WidgetRepository.decodeSlots(
            WidgetRepository.encodeIds(slots),
            WidgetRepository.encodeProviders(slots)
        )
        assertEquals(slots, decoded)
    }

    @Test
    fun `move item swaps neighbours and rejects out of range`() {
        assertEquals(listOf(2, 1, 3), WidgetRepository.moveItem(listOf(1, 2, 3), 1, 0))
        assertEquals(listOf(1, 3, 2), WidgetRepository.moveItem(listOf(1, 2, 3), 1, 2))
        assertNull(WidgetRepository.moveItem(listOf(1, 2, 3), 0, -1))
        assertNull(WidgetRepository.moveItem(listOf(1, 2, 3), 2, 3))
    }

    @Test
    fun `reconcile drops uninstalled widgets and finds orphan ids`() {
        val saved = listOf(WidgetSlot(1), WidgetSlot(2), WidgetSlot(3))
        // System still knows 1 and 3; 2's provider was uninstalled. 7 is a crashed add, 9 is in flight.
        val plan = WidgetRepository.planReconcile(saved, setOf(1, 3, 7, 9), pendingId = 9)
        assertEquals(listOf(1, 3), plan.kept.map { it.appWidgetId })
        assertEquals(listOf(7), plan.orphans)
    }

    @Test
    fun `reconcile without a pending id deletes every unsaved host id`() {
        val plan = WidgetRepository.planReconcile(listOf(WidgetSlot(1)), setOf(1, 9), pendingId = null)
        assertEquals(listOf(9), plan.orphans)
    }
}
