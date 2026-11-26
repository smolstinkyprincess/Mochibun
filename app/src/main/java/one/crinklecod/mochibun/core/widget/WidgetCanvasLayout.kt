package one.crinklecod.mochibun.core.widget

import kotlin.math.max
import kotlin.math.min

/**
 * Where one card sits on the freeform Widgets page, in 16dp grid steps.
 *
 * A coarse grid (instead of raw dp) keeps D-pad nudges crisp: one press = one visible step,
 * and saved layouts stay readable integers. Cards with a higher [zIndex] draw on top.
 */
data class WidgetPlacement(
    val gridX: Int,
    val gridY: Int,
    val gridW: Int,
    val gridH: Int,
    val zIndex: Float = 0f,
    val deco: DecoStyle = DecoStyle.NONE
) {
    val right: Int get() = gridX + gridW
    val bottom: Int get() = gridY + gridH

    fun overlaps(other: WidgetPlacement): Boolean =
        gridX < other.right && other.gridX < right && gridY < other.bottom && other.gridY < bottom

    fun toBounds(): TargetBounds = TargetBounds(gridX, gridY, right, bottom)
}

/**
 * Optional scrapbook decoration taped or pinned onto a card. Anything but [NONE] also swaps the
 * card's custom 9-slice frame for a plain stitched border, so the tape never fights the frame.
 */
enum class DecoStyle(val id: String, val label: String) {
    NONE("none", "None"),
    WASHI_TAPE("washi", "Washi tape"),
    CORNER_TAPE("corner_tape", "Corner tape"),
    CORNER_BOW("bow", "Corner bow"),
    CORNER_HEART("heart", "Corner heart");

    fun next(): DecoStyle = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): DecoStyle = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/**
 * Standalone scrapbook stickers placed straight onto the canvas. Five are crisp procedural
 * die-cut vectors; the rest are 1-bit Docomo pictograms ([docomoIndex]) drawn integer-scaled
 * with a white die-cut backing. [id] is stored in the sticker's canvas key, so it must not change.
 */
enum class StickerKind(val id: String, val label: String, val docomoIndex: Int? = null) {
    PUFFY_BOW("bow", "Puffy Bow"),
    PUFFY_HEART("heart", "Puffy Heart"),
    SPARKLES("sparkles", "Sparkle Cluster"),
    PASTEL_STAR("star", "Pastel Star"),
    RIBBON("ribbon", "Ribbon"),
    PICTO_HEART("p_heart", "Pixel Heart", docomoIndex = 0),
    PICTO_NOTE("p_note", "Pixel Note", docomoIndex = 9),
    PICTO_SUN("p_sun", "Pixel Sun", docomoIndex = 14),
    PICTO_CAT("p_cat", "Pixel Cat", docomoIndex = 131),
    PICTO_GIFT("p_gift", "Pixel Gift", docomoIndex = 105);

    fun next(): StickerKind = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): StickerKind? = entries.firstOrNull { it.id == id }
    }
}

/** One-press auto-arrangements for the visible cards, meant as a starting point for D-pad tweaks. */
enum class CollagePreset(val label: String) {
    CLASSIC_STACK("Stack"),
    SCRAPBOOK("Scrapbook"),
    BENTO("Bento")
}

/**
 * Pure layout math for the freeform Widgets canvas (unit tested in `WidgetCanvasLayoutTest`).
 *
 * Layout maps are keyed by a stable card key: [KEY_NOW_PLAYING], [KEY_SYSTEM_MONITOR],
 * [KEY_PHOTO_FRAME], [KEY_STICKY_NOTE], [appWidgetKey] for hosted Android widgets, or
 * [stickerKey] for standalone stickers (fixed [STICKER_SIZE] squares, at most [MAX_STICKERS]).
 */
object WidgetCanvasLayout {
    const val STEP_DP = 16

    /** 6 steps = 96dp: narrower than this and card text stops being legible. */
    const val MIN_W = 6
    const val MIN_H = 3
    const val MAX_H = 40

    /** How far down the canvas a card may go (2560dp, a bit over 5 screens). */
    const val MAX_ROWS = 160

    /** Empty row between stacked cards (16dp, close to the old 14dp list spacing). */
    const val GAP = 1

    const val KEY_NOW_PLAYING = "now_playing"
    const val KEY_SYSTEM_MONITOR = "system_monitor"
    const val KEY_PHOTO_FRAME = "photo_frame"
    const val KEY_STICKY_NOTE = "sticky_note"
    private const val APP_WIDGET_PREFIX = "app:"
    private const val STICKER_PREFIX = "sticker:"

    /** Stickers are fixed 3x3-step (48dp) squares: small enough to tuck into a card's corner. */
    const val STICKER_SIZE = 3

    /** Keeps the canvas light (each sticker is one small static draw) and D-pad traversal short. */
    const val MAX_STICKERS = 8

    fun appWidgetKey(appWidgetId: Int): String = "$APP_WIDGET_PREFIX$appWidgetId"

    fun appWidgetIdOf(key: String): Int? =
        if (key.startsWith(APP_WIDGET_PREFIX)) key.substring(APP_WIDGET_PREFIX.length).toIntOrNull() else null

    fun isStickerKey(key: String): Boolean = key.startsWith(STICKER_PREFIX)

    /** `sticker:<uid>:<kind>`: the uid keeps two stickers of the same kind apart. */
    fun stickerKey(uid: Int, kind: StickerKind): String = "$STICKER_PREFIX$uid:${kind.id}"

    fun stickerUidOf(key: String): Int? =
        if (isStickerKey(key)) key.removePrefix(STICKER_PREFIX).substringBefore(':').toIntOrNull() else null

    fun stickerKindOf(key: String): StickerKind? =
        if (isStickerKey(key)) StickerKind.fromId(key.removePrefix(STICKER_PREFIX).substringAfter(':', "")) else null

    fun stickerKeys(layout: Map<String, WidgetPlacement>): List<String> = layout.keys.filter(::isStickerKey)

    fun columnsFor(widthDp: Int): Int = (widthDp / STEP_DP).coerceAtLeast(MIN_W)

    /** Rows needed to show [heightPx] at a [stepPx] grid step, within the allowed height range. */
    fun rowsFor(heightPx: Int, stepPx: Int): Int =
        ((heightPx + stepPx - 1) / stepPx.coerceAtLeast(1)).coerceIn(MIN_H, MAX_H)

    /** Pulls [p] back inside a [cols]-wide canvas with legal sizes. Width is fitted before position. */
    fun clamp(p: WidgetPlacement, cols: Int, minW: Int = MIN_W, minH: Int = MIN_H): WidgetPlacement {
        val w = p.gridW.coerceIn(min(minW, cols), cols)
        val h = p.gridH.coerceIn(minH, MAX_H)
        return p.copy(
            gridX = p.gridX.coerceIn(0, cols - w),
            gridY = p.gridY.coerceIn(0, MAX_ROWS - h),
            gridW = w,
            gridH = h
        )
    }

    /** [clamp] for the card stored under [key]: stickers keep their fixed small square. */
    fun clampFor(key: String?, p: WidgetPlacement, cols: Int): WidgetPlacement =
        if (key != null && isStickerKey(key)) {
            clamp(p.copy(gridW = STICKER_SIZE, gridH = STICKER_SIZE), cols, minW = 1, minH = 1)
        } else {
            clamp(p, cols)
        }

    /**
     * Shifts [p] by whole steps. Returns null when the canvas edge stops it (so the caller can buzz).
     * [key] lets a sticker keep its small size instead of being grown to the card minimum.
     */
    fun move(p: WidgetPlacement, dx: Int, dy: Int, cols: Int, key: String? = null): WidgetPlacement? {
        val moved = clampFor(key, p.copy(gridX = p.gridX + dx, gridY = p.gridY + dy), cols)
        return moved.takeIf { it != clampFor(key, p, cols) }
    }

    /**
     * Grows or shrinks [p] from its bottom-right corner. A card already touching the right edge
     * slides left as it widens, so "wider" always works until it spans the full canvas.
     * [minH] lets a card that can't squeeze its content any smaller refuse to shrink further.
     * Returns null when nothing changed.
     */
    fun resize(p: WidgetPlacement, dw: Int, dh: Int, cols: Int, minH: Int = MIN_H): WidgetPlacement? {
        val base = clamp(p, cols)
        val w = (base.gridW + dw).coerceIn(min(MIN_W, cols), cols)
        val h = (base.gridH + dh).coerceIn(minH.coerceIn(MIN_H, MAX_H), MAX_H)
        val resized = clamp(base.copy(gridX = min(base.gridX, cols - w), gridW = w, gridH = h), cols)
        return resized.takeIf { it != base }
    }

    /**
     * Rewrites z values to 0, 1, 2… in current draw order, so ties (fresh migrations) get a
     * definite order. Ties keep [layout]'s iteration order, which is insertion order.
     */
    fun normalizeZ(layout: Map<String, WidgetPlacement>): Map<String, WidgetPlacement> {
        val ordered = layout.entries.sortedBy { it.value.zIndex } // stable sort keeps ties in order
        val out = LinkedHashMap<String, WidgetPlacement>(layout.size)
        layout.keys.forEach { key -> out[key] = layout.getValue(key) }
        ordered.forEachIndexed { rank, entry -> out[entry.key] = entry.value.copy(zIndex = rank.toFloat()) }
        return out
    }

    /**
     * Swaps [key] one layer toward the viewer (`forward`) or away from it, past the nearest card
     * it actually overlaps. Swapping with a card elsewhere on the page would change nothing
     * visible, so only overlapping cards count. [visibleKeys] limits the candidates to cards
     * on screen. Returns null when there's nothing to pass.
     */
    fun shiftLayer(
        layout: Map<String, WidgetPlacement>,
        key: String,
        forward: Boolean,
        visibleKeys: Collection<String> = layout.keys
    ): Map<String, WidgetPlacement>? {
        if (key !in layout) return null
        val normalized = normalizeZ(layout)
        val self = normalized.getValue(key)
        val neighbour = normalized.entries
            .filter { it.key != key && it.key in visibleKeys && it.value.overlaps(self) }
            .filter { if (forward) it.value.zIndex > self.zIndex else it.value.zIndex < self.zIndex }
            .let { candidates ->
                if (forward) candidates.minByOrNull { it.value.zIndex } else candidates.maxByOrNull { it.value.zIndex }
            } ?: return null
        val out = LinkedHashMap(normalized)
        out[key] = self.copy(zIndex = neighbour.value.zIndex)
        out[neighbour.key] = neighbour.value.copy(zIndex = self.zIndex)
        return out
    }

    fun topZ(layout: Map<String, WidgetPlacement>): Float = layout.values.maxOfOrNull { it.zIndex } ?: -1f

    /**
     * The first row where a full-width card of [h] rows fits without touching any placed card
     * (keeping a [GAP] row clear above and below). Scans top-down, so the first card on an empty
     * canvas lands at row 0 and later ones stack underneath: the old list layout.
     */
    fun firstFreeRow(placed: Collection<WidgetPlacement>, h: Int, cols: Int): Int {
        val lastStart = MAX_ROWS - h
        var y = 0
        while (y <= lastStart) {
            val probe = WidgetPlacement(0, y - GAP, cols, h + 2 * GAP)
            val blocker = placed.filter { it.overlaps(probe) }.maxByOrNull { it.bottom } ?: return y
            y = max(y + 1, blocker.bottom + GAP)
        }
        return (placed.maxOfOrNull { it.bottom + GAP } ?: 0).coerceAtMost(lastStart)
    }

    /**
     * Migration / first-appearance placement. Every key in [needed] (card key to default height
     * in rows, in classic stack order) without a saved placement is stacked full-width into the
     * first free gap. Existing placements are kept exactly. An upgrade with no saved layout
     * therefore gets the same top-to-bottom stack the old list showed.
     */
    fun withDefaults(
        existing: Map<String, WidgetPlacement>,
        needed: List<Pair<String, Int>>,
        cols: Int
    ): Map<String, WidgetPlacement> {
        if (needed.all { it.first in existing }) return existing
        val out = LinkedHashMap(existing)
        var z = topZ(existing)
        needed.forEach { (key, rows) ->
            if (key in out) return@forEach
            val h = rows.coerceIn(MIN_H, MAX_H)
            z += 1f
            out[key] = WidgetPlacement(0, firstFreeRow(out.values, h, cols), cols, h, z)
        }
        return out
    }

    /** Reading order: top-to-bottom, then left-to-right. */
    fun readingOrder(layout: Map<String, WidgetPlacement>, keys: Collection<String>): List<String> =
        keys.filter { it in layout }.sortedWith(
            compareBy<String>({ layout.getValue(it).gridY }, { layout.getValue(it).gridX })
        )

    /**
     * Re-arranges the [visibleKeys] cards (taken in reading order) into [preset]. Heights are
     * kept so each card still shows its content. Hidden cards (Now Playing with nothing
     * playing, say) keep their saved spot unless the preset covers it, in which case they move
     * to the first free gap below.
     */
    fun applyPreset(
        layout: Map<String, WidgetPlacement>,
        visibleKeys: Collection<String>,
        preset: CollagePreset,
        cols: Int
    ): Map<String, WidgetPlacement> {
        // Stickers are scrapbook trim, not cards: presets leave them where they are (on top, below)
        val order = readingOrder(layout, visibleKeys.filterNot(::isStickerKey))
        val arranged = LinkedHashMap<String, WidgetPlacement>()
        when (preset) {
            CollagePreset.CLASSIC_STACK -> {
                var y = 0
                order.forEachIndexed { i, key ->
                    val h = clamp(layout.getValue(key), cols).gridH
                    arranged[key] = layout.getValue(key).copy(gridX = 0, gridY = y, gridW = cols, gridH = h, zIndex = i.toFloat())
                    y += h + GAP
                }
            }
            CollagePreset.SCRAPBOOK -> {
                // Staggered cards, each tucked a third of the way under the one below it:
                // later cards sit on top, like photos dropped into an album.
                val w = max(min(MIN_W, cols), (cols * 3 + 3) / 4)
                var y = 0
                order.forEachIndexed { i, key ->
                    val h = clamp(layout.getValue(key), cols).gridH
                    val x = if (i % 2 == 0) 0 else cols - w
                    arranged[key] = layout.getValue(key).copy(gridX = x, gridY = y, gridW = w, gridH = h, zIndex = i.toFloat())
                    y += max(MIN_H, h - h / 3)
                }
            }
            CollagePreset.BENTO -> {
                // Two-column masonry: each card drops into whichever column is shorter
                val w = max(min(MIN_W, cols), (cols - GAP) / 2)
                val columnX = intArrayOf(0, cols - w)
                val columnY = intArrayOf(0, 0)
                order.forEachIndexed { i, key ->
                    val h = clamp(layout.getValue(key), cols).gridH
                    val c = if (columnY[1] < columnY[0]) 1 else 0
                    arranged[key] = layout.getValue(key).copy(gridX = columnX[c], gridY = columnY[c], gridW = w, gridH = h, zIndex = i.toFloat())
                    columnY[c] += h + GAP
                }
            }
        }
        val out = LinkedHashMap<String, WidgetPlacement>()
        layout.forEach { (key, p) -> out[key] = arranged[key] ?: p }
        // Hidden cards go above the arranged ones in z, and out from under them
        var z = arranged.size.toFloat()
        layout.keys.filter { it !in arranged && !isStickerKey(it) }.forEach { key ->
            val p = clamp(layout.getValue(key), cols)
            val blocked = arranged.values.any { it.overlaps(p) }
            val y = if (blocked) firstFreeRow(out.filterKeys { it != key && !isStickerKey(it) }.values, p.gridH, cols) else p.gridY
            out[key] = p.copy(gridX = if (blocked) 0 else p.gridX, gridY = y, zIndex = z++)
        }
        stickersOnTop(layout, out, z)
        return out
    }

    /** Re-stacks [layout]'s stickers above everything else in [out], keeping their own order. */
    private fun stickersOnTop(layout: Map<String, WidgetPlacement>, out: MutableMap<String, WidgetPlacement>, firstZ: Float) {
        var z = firstZ
        layout.entries.filter { isStickerKey(it.key) }.sortedBy { it.value.zIndex }.forEach { (key, p) ->
            out[key] = p.copy(zIndex = z++)
        }
    }

    // ── Standalone stickers ──

    /**
     * The first free [STICKER_SIZE] square among [occupied] (the cards on screen), scanning
     * rows top-down and each row right-to-left, so a new sticker lands in an open corner beside
     * a card instead of under the page. Falls back to the right edge just below everything.
     */
    fun freeStickerSpot(occupied: Collection<WidgetPlacement>, cols: Int): Pair<Int, Int> {
        val s = STICKER_SIZE
        val bottom = occupied.maxOfOrNull { it.bottom } ?: 0
        for (y in 0..(bottom - s)) {
            for (x in (cols - s) downTo 0) {
                val probe = WidgetPlacement(x, y, s, s)
                if (occupied.none { it.overlaps(probe) }) return x to y
            }
        }
        return (cols - s).coerceAtLeast(0) to bottom.coerceAtMost(MAX_ROWS - s)
    }

    /**
     * Adds a [kind] sticker on top of every card, in the first free spot among [occupied].
     * Returns the new layout and the sticker's key, or null once [MAX_STICKERS] are placed.
     */
    fun addSticker(
        layout: Map<String, WidgetPlacement>,
        kind: StickerKind,
        cols: Int,
        occupied: Collection<WidgetPlacement> = layout.values
    ): Pair<Map<String, WidgetPlacement>, String>? {
        if (stickerKeys(layout).size >= MAX_STICKERS) return null
        val uid = (layout.keys.mapNotNull(::stickerUidOf).maxOrNull() ?: 0) + 1
        val key = stickerKey(uid, kind)
        val (x, y) = freeStickerSpot(occupied, cols)
        val out = LinkedHashMap(layout)
        out[key] = WidgetPlacement(x, y, STICKER_SIZE, STICKER_SIZE, topZ(layout) + 1f)
        return out to key
    }

    /** Swaps the sticker under [key] for [kind] in place (same spot, layer and list position). */
    fun swapSticker(
        layout: Map<String, WidgetPlacement>,
        key: String,
        kind: StickerKind
    ): Pair<Map<String, WidgetPlacement>, String>? {
        val uid = stickerUidOf(key) ?: return null
        if (key !in layout) return null
        val newKey = stickerKey(uid, kind)
        val out = LinkedHashMap<String, WidgetPlacement>(layout.size)
        layout.forEach { (k, p) -> out[if (k == key) newKey else k] = p }
        return out to newKey
    }

    // ── Custom preset slots ──

    /**
     * Restores a saved "My Layout" [snapshot] onto the current [layout]:
     * - cards in both take their saved spot, size, layer and deco;
     * - stickers are restored exactly as saved (current stickers not in the snapshot go);
     * - cards added since the save (and missing from it) keep their spot, moved below the
     *   restored cards if they'd sit underneath one, and stack on top;
     * - snapshot entries for cards that no longer exist (a removed widget) are ignored.
     */
    fun applySnapshot(
        layout: Map<String, WidgetPlacement>,
        snapshot: Map<String, WidgetPlacement>,
        cols: Int
    ): Map<String, WidgetPlacement> {
        val out = LinkedHashMap<String, WidgetPlacement>()
        layout.forEach { (key, p) ->
            if (isStickerKey(key)) return@forEach
            snapshot[key]?.let { out[key] = clampFor(key, it, cols) } ?: run { out[key] = p }
        }
        snapshot.forEach { (key, p) ->
            if (isStickerKey(key) && stickerKindOf(key) != null && stickerKeys(out).size < MAX_STICKERS) {
                out[key] = clampFor(key, p, cols)
            }
        }
        val restored = out.filterKeys { it in snapshot }
        var z = topZ(restored) + 1f
        layout.keys.filter { !isStickerKey(it) && it !in snapshot }.forEach { key ->
            val p = clamp(layout.getValue(key), cols)
            val blocked = restored.values.any { it.overlaps(p) }
            val y = if (blocked) firstFreeRow(out.filterKeys { it != key && !isStickerKey(it) }.values, p.gridH, cols) else p.gridY
            out[key] = p.copy(gridX = if (blocked) 0 else p.gridX, gridY = y, zIndex = z++)
        }
        return out
    }

    // ── Persistence format: one `key|x|y|w|h|z[|deco]` line per card ──
    // The deco field is only written when set, so undecorated layouts stay in the original format.

    fun encode(layout: Map<String, WidgetPlacement>): String = layout.entries.joinToString("\n") { (key, p) ->
        val base = "$key|${p.gridX}|${p.gridY}|${p.gridW}|${p.gridH}|${p.zIndex}"
        if (p.deco == DecoStyle.NONE) base else "$base|${p.deco.id}"
    }

    /** Skips malformed lines instead of failing, so one bad entry can't wipe the whole page. */
    fun decode(text: String): Map<String, WidgetPlacement> {
        val out = LinkedHashMap<String, WidgetPlacement>()
        text.lineSequence().forEach { line ->
            val parts = line.trim().split('|')
            if (parts.size !in 6..7 || parts[0].isBlank()) return@forEach
            val x = parts[1].toIntOrNull() ?: return@forEach
            val y = parts[2].toIntOrNull() ?: return@forEach
            val w = parts[3].toIntOrNull() ?: return@forEach
            val h = parts[4].toIntOrNull() ?: return@forEach
            val z = parts[5].toFloatOrNull()?.takeIf { it.isFinite() } ?: return@forEach
            out[parts[0]] = WidgetPlacement(x, y, w, h, z, DecoStyle.fromId(parts.getOrNull(6)))
        }
        return out
    }
}
