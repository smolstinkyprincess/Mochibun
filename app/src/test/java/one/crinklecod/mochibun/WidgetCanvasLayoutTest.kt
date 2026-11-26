package one.crinklecod.mochibun

import one.crinklecod.mochibun.core.widget.CollagePreset
import one.crinklecod.mochibun.core.widget.DecoStyle
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout.GAP
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout.MAX_H
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout.MAX_ROWS
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout.MIN_H
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout.MIN_W
import one.crinklecod.mochibun.core.widget.WidgetPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCanvasLayoutTest {

    // AT-M120: 320dp wide minus 12dp each side -> 18 columns
    private val cols = WidgetCanvasLayout.columnsFor(320 - 24)

    @Test
    fun `AT-M120 canvas is 18 columns`() {
        assertEquals(18, cols)
    }

    // ── Clamping ──────────────────────────────────────────────────────

    @Test
    fun `clamp keeps placement inside the canvas`() {
        val p = WidgetCanvasLayout.clamp(WidgetPlacement(-4, -2, 40, 100), cols)
        assertEquals(WidgetPlacement(0, 0, cols, MAX_H), p)
    }

    @Test
    fun `clamp enforces minimum legible size`() {
        val p = WidgetCanvasLayout.clamp(WidgetPlacement(3, 3, 1, 1), cols)
        assertEquals(MIN_W, p.gridW)
        assertEquals(MIN_H, p.gridH)
    }

    @Test
    fun `clamp pulls a card back from the right edge after its width is fitted`() {
        val p = WidgetCanvasLayout.clamp(WidgetPlacement(16, 0, 10, 5), cols)
        assertEquals(cols - 10, p.gridX)
        assertEquals(10, p.gridW)
    }

    @Test
    fun `clamp refits a layout saved on a wider screen`() {
        val p = WidgetCanvasLayout.clamp(WidgetPlacement(20, 0, 30, 5), cols)
        assertEquals(0, p.gridX)
        assertEquals(cols, p.gridW)
    }

    @Test
    fun `clamp keeps cards above the canvas floor`() {
        val p = WidgetCanvasLayout.clamp(WidgetPlacement(0, MAX_ROWS + 10, 6, 8), cols)
        assertEquals(MAX_ROWS - 8, p.gridY)
    }

    // ── Move ──────────────────────────────────────────────────────────

    @Test
    fun `move shifts by one grid step`() {
        val start = WidgetPlacement(2, 2, 6, 4)
        assertEquals(WidgetPlacement(3, 2, 6, 4), WidgetCanvasLayout.move(start, 1, 0, cols))
        assertEquals(WidgetPlacement(2, 1, 6, 4), WidgetCanvasLayout.move(start, 0, -1, cols))
    }

    @Test
    fun `move returns null at the canvas edges`() {
        assertNull(WidgetCanvasLayout.move(WidgetPlacement(0, 0, 6, 4), -1, 0, cols))
        assertNull(WidgetCanvasLayout.move(WidgetPlacement(0, 0, 6, 4), 0, -1, cols))
        assertNull(WidgetCanvasLayout.move(WidgetPlacement(cols - 6, 0, 6, 4), 1, 0, cols))
        assertNull(WidgetCanvasLayout.move(WidgetPlacement(0, MAX_ROWS - 4, 6, 4), 0, 1, cols))
    }

    @Test
    fun `move keeps z index`() {
        val moved = WidgetCanvasLayout.move(WidgetPlacement(2, 2, 6, 4, 3f), 0, 1, cols)
        assertEquals(3f, moved!!.zIndex)
    }

    // ── Resize ────────────────────────────────────────────────────────

    @Test
    fun `resize grows and shrinks from the bottom-right corner`() {
        val start = WidgetPlacement(2, 2, 8, 6)
        assertEquals(WidgetPlacement(2, 2, 9, 6), WidgetCanvasLayout.resize(start, 1, 0, cols))
        assertEquals(WidgetPlacement(2, 2, 8, 5), WidgetCanvasLayout.resize(start, 0, -1, cols))
    }

    @Test
    fun `resize enforces min and max sizes`() {
        assertNull(WidgetCanvasLayout.resize(WidgetPlacement(0, 0, MIN_W, 6), -1, 0, cols))
        assertNull(WidgetCanvasLayout.resize(WidgetPlacement(0, 0, 8, MIN_H), 0, -1, cols))
        assertNull(WidgetCanvasLayout.resize(WidgetPlacement(0, 0, cols, 6), 1, 0, cols))
        assertNull(WidgetCanvasLayout.resize(WidgetPlacement(0, 0, 8, MAX_H), 0, 1, cols))
    }

    @Test
    fun `widening at the right edge slides the card left`() {
        val resized = WidgetCanvasLayout.resize(WidgetPlacement(cols - 8, 0, 8, 6), 1, 0, cols)
        assertEquals(WidgetPlacement(cols - 9, 0, 9, 6), resized)
    }

    @Test
    fun `resize respects a content floor for native cards`() {
        assertNull(WidgetCanvasLayout.resize(WidgetPlacement(0, 0, 8, 10), 0, -1, cols, minH = 10))
        assertNotNull(WidgetCanvasLayout.resize(WidgetPlacement(0, 0, 8, 10), 0, 1, cols, minH = 10))
    }

    // ── Layers ────────────────────────────────────────────────────────

    private fun stackOfThree(): Map<String, WidgetPlacement> = linkedMapOf(
        "a" to WidgetPlacement(0, 0, 10, 10, 0f),
        "b" to WidgetPlacement(4, 4, 10, 10, 1f),
        "c" to WidgetPlacement(6, 6, 10, 10, 2f)
    )

    @Test
    fun `bring forward swaps with the next overlapping card above`() {
        val updated = WidgetCanvasLayout.shiftLayer(stackOfThree(), "a", forward = true)!!
        assertEquals(1f, updated.getValue("a").zIndex)
        assertEquals(0f, updated.getValue("b").zIndex)
        assertEquals(2f, updated.getValue("c").zIndex)
    }

    @Test
    fun `send backward swaps with the next overlapping card below`() {
        val updated = WidgetCanvasLayout.shiftLayer(stackOfThree(), "c", forward = false)!!
        assertEquals(1f, updated.getValue("c").zIndex)
        assertEquals(2f, updated.getValue("b").zIndex)
    }

    @Test
    fun `layer shift stops at the top and bottom`() {
        assertNull(WidgetCanvasLayout.shiftLayer(stackOfThree(), "c", forward = true))
        assertNull(WidgetCanvasLayout.shiftLayer(stackOfThree(), "a", forward = false))
    }

    @Test
    fun `layer shift ignores cards that do not overlap`() {
        val layout = linkedMapOf(
            "a" to WidgetPlacement(0, 0, 6, 4, 0f),
            "far" to WidgetPlacement(0, 30, 6, 4, 1f)
        )
        assertNull(WidgetCanvasLayout.shiftLayer(layout, "a", forward = true))
    }

    @Test
    fun `layer shift skips hidden cards`() {
        val updated = WidgetCanvasLayout.shiftLayer(stackOfThree(), "a", forward = true, visibleKeys = listOf("a", "c"))!!
        assertEquals(2f, updated.getValue("a").zIndex)
        assertEquals(0f, updated.getValue("c").zIndex)
        assertEquals(1f, updated.getValue("b").zIndex)
    }

    @Test
    fun `tied z values get a definite order before swapping`() {
        val layout = linkedMapOf(
            "a" to WidgetPlacement(0, 0, 10, 10, 0f),
            "b" to WidgetPlacement(2, 2, 10, 10, 0f)
        )
        val normalized = WidgetCanvasLayout.normalizeZ(layout)
        assertEquals(0f, normalized.getValue("a").zIndex)
        assertEquals(1f, normalized.getValue("b").zIndex)
        val updated = WidgetCanvasLayout.shiftLayer(layout, "a", forward = true)!!
        assertTrue(updated.getValue("a").zIndex > updated.getValue("b").zIndex)
    }

    // ── Migration defaults ────────────────────────────────────────────

    @Test
    fun `empty saved layout migrates to the old top-to-bottom stack`() {
        val layout = WidgetCanvasLayout.withDefaults(
            emptyMap(),
            listOf("now_playing" to 10, "system_monitor" to 14, "app:7" to 6),
            cols
        )
        assertEquals(WidgetPlacement(0, 0, cols, 10, 0f), layout["now_playing"])
        assertEquals(WidgetPlacement(0, 10 + GAP, cols, 14, 1f), layout["system_monitor"])
        assertEquals(WidgetPlacement(0, 10 + GAP + 14 + GAP, cols, 6, 2f), layout["app:7"])
        assertEquals(listOf("now_playing", "system_monitor", "app:7"), layout.keys.toList())
    }

    @Test
    fun `migration keeps existing placements and returns the same map when complete`() {
        val existing = mapOf("a" to WidgetPlacement(3, 3, 8, 8, 5f))
        assertSame(existing, WidgetCanvasLayout.withDefaults(existing, listOf("a" to 4), cols))
    }

    @Test
    fun `new cards fill the first free gap and land on top`() {
        val existing = linkedMapOf(
            "a" to WidgetPlacement(0, 0, cols, 4, 0f),
            "b" to WidgetPlacement(0, 20, cols, 4, 1f)
        )
        val layout = WidgetCanvasLayout.withDefaults(existing, listOf("a" to 4, "b" to 4, "new" to 6), cols)
        assertEquals(WidgetPlacement(0, 4 + GAP, cols, 6, 2f), layout["new"])
        assertEquals(existing["a"], layout["a"])
        assertEquals(existing["b"], layout["b"])
    }

    @Test
    fun `new card goes below when the gap is too small`() {
        val existing = linkedMapOf(
            "a" to WidgetPlacement(0, 0, cols, 4, 0f),
            "b" to WidgetPlacement(0, 7, cols, 4, 1f)
        )
        val layout = WidgetCanvasLayout.withDefaults(existing, listOf("new" to 6), cols)
        assertEquals(11 + GAP, layout.getValue("new").gridY)
    }

    @Test
    fun `migration clamps default heights`() {
        val layout = WidgetCanvasLayout.withDefaults(emptyMap(), listOf("tiny" to 1, "huge" to 500), cols)
        assertEquals(MIN_H, layout.getValue("tiny").gridH)
        assertEquals(MAX_H, layout.getValue("huge").gridH)
    }

    // ── Presets ───────────────────────────────────────────────────────

    private fun threeCards(): Map<String, WidgetPlacement> = linkedMapOf(
        "a" to WidgetPlacement(0, 0, cols, 8, 0f),
        "b" to WidgetPlacement(0, 9, cols, 6, 1f),
        "c" to WidgetPlacement(0, 16, cols, 10, 2f)
    )

    @Test
    fun `every preset keeps visible cards inside the canvas`() {
        CollagePreset.entries.forEach { preset ->
            val layout = WidgetCanvasLayout.applyPreset(threeCards(), listOf("a", "b", "c"), preset, cols)
            layout.values.forEach { assertEquals("$preset", WidgetCanvasLayout.clamp(it, cols), it) }
        }
    }

    @Test
    fun `classic stack does not overlap`() {
        val layout = WidgetCanvasLayout.applyPreset(threeCards(), listOf("a", "b", "c"), CollagePreset.CLASSIC_STACK, cols)
        val cards = layout.values.toList()
        for (i in cards.indices) for (j in i + 1 until cards.size) assertFalse(cards[i].overlaps(cards[j]))
    }

    @Test
    fun `scrapbook staggers and layers later cards on top`() {
        val layout = WidgetCanvasLayout.applyPreset(threeCards(), listOf("a", "b", "c"), CollagePreset.SCRAPBOOK, cols)
        val a = layout.getValue("a")
        val b = layout.getValue("b")
        assertTrue(a.overlaps(b))
        assertTrue(b.zIndex > a.zIndex)
        assertTrue(a.gridX != b.gridX)
    }

    @Test
    fun `bento packs cards into two columns`() {
        val layout = WidgetCanvasLayout.applyPreset(threeCards(), listOf("a", "b", "c"), CollagePreset.BENTO, cols)
        assertEquals(0, layout.getValue("a").gridX)
        assertTrue(layout.getValue("b").gridX > 0)
        assertTrue(layout.getValue("a").gridW < cols)
        val cards = layout.values.toList()
        for (i in cards.indices) for (j in i + 1 until cards.size) assertFalse(cards[i].overlaps(cards[j]))
    }

    @Test
    fun `presets move hidden cards out from under the arranged ones`() {
        val layout = WidgetCanvasLayout.applyPreset(threeCards(), listOf("b", "c"), CollagePreset.CLASSIC_STACK, cols)
        val hidden = layout.getValue("a")
        assertFalse(hidden.overlaps(layout.getValue("b")))
        assertFalse(hidden.overlaps(layout.getValue("c")))
    }

    // ── Persistence ───────────────────────────────────────────────────

    @Test
    fun `encode and decode round-trip`() {
        val layout = linkedMapOf(
            "now_playing" to WidgetPlacement(1, 2, 12, 9, 0f),
            "app:42" to WidgetPlacement(0, 12, 18, 6, 1.5f)
        )
        assertEquals(layout, WidgetCanvasLayout.decode(WidgetCanvasLayout.encode(layout)))
    }

    @Test
    fun `decode skips malformed lines`() {
        val text = "a|1|2|3|4|0.0\nbroken\nb|x|2|3|4|0\nc|1|2|3|4|NaN\n|1|2|3|4|0\nd|0|0|6|4|2.0"
        assertEquals(setOf("a", "d"), WidgetCanvasLayout.decode(text).keys)
        assertTrue(WidgetCanvasLayout.decode("").isEmpty())
    }

    @Test
    fun `app widget keys round-trip`() {
        assertEquals(42, WidgetCanvasLayout.appWidgetIdOf(WidgetCanvasLayout.appWidgetKey(42)))
        assertNull(WidgetCanvasLayout.appWidgetIdOf(WidgetCanvasLayout.KEY_NOW_PLAYING))
    }

    // ── Deco ──────────────────────────────────────────────────────────

    @Test
    fun `deco round-trips and undecorated lines keep the original six-field format`() {
        val layout = linkedMapOf(
            "plain" to WidgetPlacement(0, 0, 6, 4, 0f),
            "taped" to WidgetPlacement(0, 5, 6, 4, 1f, DecoStyle.WASHI_TAPE),
            "bow" to WidgetPlacement(0, 10, 6, 4, 2f, DecoStyle.CORNER_BOW)
        )
        val encoded = WidgetCanvasLayout.encode(layout)
        assertEquals(6, encoded.lines().first().split('|').size)
        assertEquals(layout, WidgetCanvasLayout.decode(encoded))
    }

    @Test
    fun `layouts saved before deco existed decode as undecorated`() {
        val decoded = WidgetCanvasLayout.decode("a|0|0|6|4|0.0")
        assertEquals(DecoStyle.NONE, decoded.getValue("a").deco)
    }

    @Test
    fun `unknown deco ids fall back to none`() {
        assertEquals(DecoStyle.NONE, WidgetCanvasLayout.decode("a|0|0|6|4|0.0|glitter").getValue("a").deco)
    }

    @Test
    fun `deco cycles through every style and back to none`() {
        var style = DecoStyle.NONE
        val seen = mutableListOf<DecoStyle>()
        repeat(DecoStyle.entries.size) {
            style = style.next()
            seen += style
        }
        assertEquals(DecoStyle.NONE, style)
        assertEquals(DecoStyle.entries.toSet(), seen.toSet())
    }

    @Test
    fun `moving, resizing, layering and presets keep each card's deco`() {
        val taped = WidgetPlacement(2, 2, 8, 6, 0f, DecoStyle.WASHI_TAPE)
        assertEquals(DecoStyle.WASHI_TAPE, WidgetCanvasLayout.move(taped, 1, 0, cols)!!.deco)
        assertEquals(DecoStyle.WASHI_TAPE, WidgetCanvasLayout.resize(taped, 1, 1, cols)!!.deco)
        val layout = linkedMapOf("t" to taped, "o" to WidgetPlacement(4, 4, 8, 6, 1f, DecoStyle.CORNER_HEART))
        val layered = WidgetCanvasLayout.shiftLayer(layout, "t", forward = true)!!
        assertEquals(DecoStyle.WASHI_TAPE, layered.getValue("t").deco)
        CollagePreset.entries.forEach { preset ->
            val arranged = WidgetCanvasLayout.applyPreset(layout, layout.keys, preset, cols)
            assertEquals("$preset", DecoStyle.WASHI_TAPE, arranged.getValue("t").deco)
            assertEquals("$preset", DecoStyle.CORNER_HEART, arranged.getValue("o").deco)
        }
    }
}
