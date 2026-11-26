package one.crinklecod.mochibun

import one.crinklecod.mochibun.core.widget.CollagePreset
import one.crinklecod.mochibun.core.widget.DecoStyle
import one.crinklecod.mochibun.core.widget.StickerKind
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.WidgetRepository

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
            "bow" to WidgetPlacement(0, 10, 6, 4, 2f, DecoStyle.CORNER_BOW),
            "corners" to WidgetPlacement(0, 15, 6, 4, 3f, DecoStyle.CORNER_TAPE)
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
    fun `corner tape persists under its own id`() {
        val encoded = WidgetCanvasLayout.encode(mapOf("a" to WidgetPlacement(1, 2, 6, 4, 0f, DecoStyle.CORNER_TAPE)))
        assertEquals("a|1|2|6|4|0.0|corner_tape", encoded.lines().first())
        assertEquals(DecoStyle.CORNER_TAPE, WidgetCanvasLayout.decode(encoded).getValue("a").deco)
    }

    @Test
    fun `decos saved before corner tape existed keep their style`() {
        // Ids, not ordinals, are persisted, so inserting CORNER_TAPE mid-enum must not shift old saves
        val decoded = WidgetCanvasLayout.decode("w|0|0|6|4|0.0|washi\nb|0|5|6|4|1.0|bow\nh|0|10|6|4|2.0|heart")
        assertEquals(DecoStyle.WASHI_TAPE, decoded.getValue("w").deco)
        assertEquals(DecoStyle.CORNER_BOW, decoded.getValue("b").deco)
        assertEquals(DecoStyle.CORNER_HEART, decoded.getValue("h").deco)
    }

    @Test
    fun `deco cycle order puts corner tape between washi tape and the bow`() {
        assertEquals(
            listOf(DecoStyle.WASHI_TAPE, DecoStyle.CORNER_TAPE, DecoStyle.CORNER_BOW, DecoStyle.CORNER_HEART, DecoStyle.NONE),
            generateSequence(DecoStyle.NONE.next()) { it.next() }.take(5).toList()
        )
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

    // ── Standalone stickers ───────────────────────────────────────────

    private val S = WidgetCanvasLayout.STICKER_SIZE

    @Test
    fun `sticker keys carry a uid and kind and round-trip through persistence`() {
        val key = WidgetCanvasLayout.stickerKey(3, StickerKind.PASTEL_STAR)
        assertTrue(WidgetCanvasLayout.isStickerKey(key))
        assertEquals(3, WidgetCanvasLayout.stickerUidOf(key))
        assertEquals(StickerKind.PASTEL_STAR, WidgetCanvasLayout.stickerKindOf(key))
        assertFalse(WidgetCanvasLayout.isStickerKey(WidgetCanvasLayout.KEY_STICKY_NOTE))
        assertNull(WidgetCanvasLayout.stickerKindOf("sticker:4:glitter"))
        val layout = linkedMapOf(key to WidgetPlacement(10, 2, S, S, 5f))
        assertEquals(layout, WidgetCanvasLayout.decode(WidgetCanvasLayout.encode(layout)))
    }

    @Test
    fun `sticker kind ids are unique`() {
        assertEquals(StickerKind.entries.size, StickerKind.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `a new sticker fills an open corner beside a narrower card, on top of everything`() {
        val card = WidgetPlacement(0, 0, cols - 4, 10, 2f)
        val (layout, key) = WidgetCanvasLayout.addSticker(linkedMapOf("card" to card), StickerKind.PUFFY_BOW, cols)!!
        val sticker = layout.getValue(key)
        assertEquals(cols - S, sticker.gridX)
        assertEquals(0, sticker.gridY)
        assertEquals(S, sticker.gridW)
        assertEquals(S, sticker.gridH)
        assertFalse(sticker.overlaps(card))
        assertEquals(3f, sticker.zIndex)
    }

    @Test
    fun `with no free gap a sticker lands just below the cards`() {
        val card = WidgetPlacement(0, 0, cols, 10, 0f)
        val (layout, key) = WidgetCanvasLayout.addSticker(linkedMapOf("card" to card), StickerKind.PICTO_CAT, cols)!!
        assertEquals(10, layout.getValue(key).gridY)
    }

    @Test
    fun `stickers stop at the cap and get distinct keys`() {
        var layout: Map<String, WidgetPlacement> = linkedMapOf("card" to WidgetPlacement(0, 0, cols, 6, 0f))
        val keys = mutableSetOf<String>()
        repeat(WidgetCanvasLayout.MAX_STICKERS) {
            val (next, key) = WidgetCanvasLayout.addSticker(layout, StickerKind.PUFFY_HEART, cols)!!
            layout = next
            keys += key
        }
        assertEquals(WidgetCanvasLayout.MAX_STICKERS, keys.size)
        assertNull(WidgetCanvasLayout.addSticker(layout, StickerKind.PUFFY_HEART, cols))
        // Removing one frees a slot again
        val fewer = LinkedHashMap(layout).apply { remove(keys.first()) }
        assertNotNull(WidgetCanvasLayout.addSticker(fewer, StickerKind.PUFFY_HEART, cols))
    }

    @Test
    fun `stickers keep their size when moved and can tuck behind cards`() {
        val key = WidgetCanvasLayout.stickerKey(1, StickerKind.RIBBON)
        val sticker = WidgetPlacement(cols - S, 0, S, S, 1f)
        val moved = WidgetCanvasLayout.move(sticker, -1, 1, cols, key)!!
        assertEquals(S, moved.gridW)
        assertEquals(S, moved.gridH)
        assertNull(WidgetCanvasLayout.move(sticker, 1, 0, cols, key)) // already at the right edge
        // Without the key the card clamp would grow it to the card minimum
        assertEquals(S, WidgetCanvasLayout.clampFor(key, sticker, cols).gridW)

        val layout = linkedMapOf("card" to WidgetPlacement(cols - 8, 0, 8, 6, 0f), key to sticker)
        val behind = WidgetCanvasLayout.shiftLayer(layout, key, forward = false)!!
        assertTrue(behind.getValue(key).zIndex < behind.getValue("card").zIndex)
    }

    @Test
    fun `swapping a sticker keeps its spot, layer and order`() {
        val key = WidgetCanvasLayout.stickerKey(2, StickerKind.PUFFY_BOW)
        val layout = linkedMapOf("a" to WidgetPlacement(0, 0, 6, 4, 0f), key to WidgetPlacement(4, 1, S, S, 1f), "b" to WidgetPlacement(0, 5, 6, 4, 2f))
        val (swapped, newKey) = WidgetCanvasLayout.swapSticker(layout, key, StickerKind.PUFFY_HEART)!!
        assertEquals(WidgetCanvasLayout.stickerKey(2, StickerKind.PUFFY_HEART), newKey)
        assertEquals(listOf("a", newKey, "b"), swapped.keys.toList())
        assertEquals(layout.getValue(key), swapped.getValue(newKey))
        assertNull(WidgetCanvasLayout.swapSticker(layout, "a", StickerKind.PUFFY_HEART))
    }

    @Test
    fun `presets arrange cards only and keep stickers in place on top`() {
        val key = WidgetCanvasLayout.stickerKey(1, StickerKind.SPARKLES)
        val sticker = WidgetPlacement(2, 3, S, S, 0f)
        val layout = linkedMapOf(
            key to sticker,
            "a" to WidgetPlacement(0, 0, cols, 6, 1f),
            "b" to WidgetPlacement(0, 8, cols, 6, 2f)
        )
        CollagePreset.entries.forEach { preset ->
            val arranged = WidgetCanvasLayout.applyPreset(layout, layout.keys, preset, cols)
            val s = arranged.getValue(key)
            assertEquals("$preset", sticker.copy(zIndex = s.zIndex), s)
            assertTrue("$preset", s.zIndex > arranged.getValue("a").zIndex && s.zIndex > arranged.getValue("b").zIndex)
        }
    }

    // ── Custom preset slots ───────────────────────────────────────────

    @Test
    fun `a saved layout restores every card, deco and sticker`() {
        val star = WidgetCanvasLayout.stickerKey(1, StickerKind.PASTEL_STAR)
        val snapshot = linkedMapOf(
            "a" to WidgetPlacement(0, 0, 8, 6, 0f, DecoStyle.WASHI_TAPE),
            WidgetCanvasLayout.KEY_STICKY_NOTE to WidgetPlacement(8, 0, 10, 8, 1f, DecoStyle.CORNER_BOW),
            star to WidgetPlacement(15, 7, S, S, 2f)
        )
        // Since saving: cards moved, the star was removed and a heart added
        val heart = WidgetCanvasLayout.stickerKey(2, StickerKind.PUFFY_HEART)
        val current = linkedMapOf(
            "a" to WidgetPlacement(0, 20, cols, 6, 1f),
            WidgetCanvasLayout.KEY_STICKY_NOTE to WidgetPlacement(0, 0, cols, 8, 0f),
            heart to WidgetPlacement(0, 0, S, S, 2f)
        )
        val restored = WidgetCanvasLayout.applySnapshot(current, snapshot, cols)
        assertEquals(snapshot.getValue("a"), restored.getValue("a"))
        assertEquals(snapshot.getValue(WidgetCanvasLayout.KEY_STICKY_NOTE), restored.getValue(WidgetCanvasLayout.KEY_STICKY_NOTE))
        assertEquals(snapshot.getValue(star), restored.getValue(star))
        assertFalse(heart in restored)
    }

    @Test
    fun `cards added after saving move out from under the restored layout`() {
        val snapshot = linkedMapOf("a" to WidgetPlacement(0, 0, cols, 10, 0f))
        val current = linkedMapOf(
            "a" to WidgetPlacement(0, 30, cols, 10, 0f),
            "new" to WidgetPlacement(0, 2, cols, 6, 1f)
        )
        val restored = WidgetCanvasLayout.applySnapshot(current, snapshot, cols)
        val added = restored.getValue("new")
        assertFalse(added.overlaps(restored.getValue("a")))
        assertTrue(added.zIndex > restored.getValue("a").zIndex)
    }

    @Test
    fun `snapshot entries for removed widgets are ignored`() {
        val snapshot = linkedMapOf(
            "a" to WidgetPlacement(0, 0, 8, 6, 0f),
            WidgetCanvasLayout.appWidgetKey(42) to WidgetPlacement(0, 8, 8, 6, 1f)
        )
        val restored = WidgetCanvasLayout.applySnapshot(linkedMapOf("a" to WidgetPlacement(0, 10, 8, 6, 0f)), snapshot, cols)
        assertEquals(setOf("a"), restored.keys)
    }
}

/** Sticky Note and My Layout slots persisted through [WidgetRepository]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetCanvasPersistenceTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        PreferencesManager.getPrefs(context).edit().clear().commit()
        WidgetRepository.init(context)
    }

    @Test
    fun `sticky note toggle and text survive a reload`() {
        WidgetRepository.setStickyNoteEnabled(context, true)
        WidgetRepository.setStickyNoteText(context, "buy strawberry mochi ♡\n\n")
        WidgetRepository.init(context)
        assertTrue(WidgetRepository.stickyNoteEnabled.value)
        assertEquals("buy strawberry mochi ♡", WidgetRepository.stickyNoteText.value)

        WidgetRepository.setStickyNoteEnabled(context, false)
        WidgetRepository.init(context)
        assertFalse(WidgetRepository.stickyNoteEnabled.value)
        assertEquals("buy strawberry mochi ♡", WidgetRepository.stickyNoteText.value) // hiding keeps the note
    }

    @Test
    fun `sticky note text is capped like Quick Memo`() {
        WidgetRepository.setStickyNoteText(context, "a".repeat(500))
        assertEquals(WidgetRepository.STICKY_NOTE_MAX_CHARS, WidgetRepository.stickyNoteText.value.length)
    }

    @Test
    fun `sticky note mirrors the ticker's Quick Memo slot, else its own text`() {
        assertNull(WidgetRepository.stickyNoteSource(listOf("", "", ""), pinnedSlot = 1))
        assertEquals(2, WidgetRepository.stickyNoteSource(listOf("", "hi", "yo"), pinnedSlot = 1))
        assertEquals(3, WidgetRepository.stickyNoteSource(listOf("", "hi", "yo"), pinnedSlot = 3))
    }

    @Test
    fun `custom preset slots save and reload with stickers and deco`() {
        assertEquals(listOf(null, null), WidgetRepository.customPresets.value)
        val sticker = WidgetCanvasLayout.stickerKey(1, StickerKind.PICTO_HEART)
        val layout = linkedMapOf(
            WidgetCanvasLayout.KEY_STICKY_NOTE to WidgetPlacement(0, 0, 10, 8, 0f, DecoStyle.CORNER_TAPE),
            sticker to WidgetPlacement(12, 1, WidgetCanvasLayout.STICKER_SIZE, WidgetCanvasLayout.STICKER_SIZE, 1f)
        )
        WidgetRepository.saveCustomPreset(context, 2, layout)
        WidgetRepository.saveCustomPreset(context, 3, layout) // out of range: ignored
        WidgetRepository.init(context)
        assertNull(WidgetRepository.customPresets.value[0])
        assertEquals(layout, WidgetRepository.customPresets.value[1])
        assertEquals(2, WidgetRepository.customPresets.value.size)
    }
}
