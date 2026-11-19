package one.crinklecod.mochibun.ui.dialogs

import kotlin.math.abs

/**
 * Pure layout + D-pad math for the Favorite Folder sticker tray: up to [PAGE_SIZE] charms per
 * page, either scattered in a hand-placed "sticker book" constellation chosen by the page's item
 * count, or (scatter off) in a straight 3 × 2 grid. Both modes share the same D-pad rules.
 * Kept free of Compose so the focus contract is unit-tested in `FolderTrayNavigationTest`.
 *
 * Indices are global positions in the folder's app list; page `p` holds
 * `[p * PAGE_SIZE, min((p + 1) * PAGE_SIZE, total))`.
 */
internal object FolderTrayNav {
    const val PAGE_SIZE = 6

    /**
     * One charm slot. [x] is 0..1 across the horizontal room left after one charm width
     * (0 = flush left, 1 = flush right). [y] is the top edge in "row pitch" units, so 0.5 means
     * half a row lower; it also defines the D-pad row (slots with equal [y] share a row).
     * [tilt] is the sticker rotation in degrees.
     */
    data class Slot(val x: Float, val y: Float, val tilt: Float)

    /** A tiny decorative sparkle centred at ([x], [y]) in the same space, [sizeDp] across. */
    data class Motif(val x: Float, val y: Float, val sizeDp: Float)

    class Layout(val slots: List<Slot>, val motifs: List<Motif>) {
        /** Height of the constellation in row-pitch units, measured to the lowest slot's top. */
        val depth: Float = slots.maxOf { it.y }
    }

    // Slots are listed in reading order (y, then x): Left/Right walk the list, so the visual
    // order and the D-pad order can never disagree.
    private val LAYOUTS: Array<Layout> = arrayOf(
        // 1: centred charm
        Layout(
            slots = listOf(Slot(0.5f, 0f, 0f)),
            motifs = listOf(Motif(0.15f, -0.15f, 9f), Motif(0.85f, 0.2f, 7f), Motif(0.28f, 0.3f, 5f))
        ),
        // 2: diagonal pair
        Layout(
            slots = listOf(Slot(0.08f, 0f, -6f), Slot(0.92f, 0.7f, 6f)),
            motifs = listOf(Motif(0.82f, 0f, 8f), Motif(0.18f, 0.75f, 7f), Motif(0.5f, 0.35f, 5f))
        ),
        // 3: triangle cluster
        Layout(
            slots = listOf(Slot(0.5f, 0f, 0f), Slot(0.04f, 0.85f, -5f), Slot(0.96f, 0.85f, 5f)),
            motifs = listOf(Motif(0.1f, 0f, 8f), Motif(0.9f, 0.05f, 6f), Motif(0.5f, 0.95f, 6f))
        ),
        // 4: staggered 2 × 2, lower row nudged right so no corner sits empty
        Layout(
            slots = listOf(
                Slot(0f, 0f, -5f), Slot(0.56f, 0f, 5f),
                Slot(0.44f, 0.9f, 4f), Slot(1f, 0.9f, -4f)
            ),
            motifs = listOf(Motif(0.97f, 0.05f, 7f), Motif(0.03f, 0.95f, 7f))
        ),
        // 5: quincunx (dice five) with the centre jewel half a row down
        Layout(
            slots = listOf(
                Slot(0f, 0f, -5f), Slot(1f, 0f, 5f),
                Slot(0.5f, 0.75f, 0f),
                Slot(0f, 1.5f, 4f), Slot(1f, 1.5f, -4f)
            ),
            motifs = listOf(Motif(0.5f, 0f, 8f), Motif(0.5f, 1.5f, 7f), Motif(0.06f, 0.75f, 5f))
        ),
        // 6: staggered 3 × 2, lower row interleaved to the right
        Layout(
            slots = listOf(
                Slot(0f, 0f, -4f), Slot(0.4f, 0f, 4f), Slot(0.8f, 0f, -4f),
                Slot(0.2f, 0.9f, 4f), Slot(0.6f, 0.9f, -4f), Slot(1f, 0.9f, 4f)
            ),
            motifs = listOf(Motif(1f, 0.05f, 7f), Motif(0f, 0.95f, 7f))
        )
    )

    // Neat mode: a straight, untilted 3 × 2 grid, filled left to right like a normal grid.
    private const val GRID_COLUMNS = 3
    private val GRID_X = floatArrayOf(0.1f, 0.5f, 0.9f)
    private val GRID_LAYOUTS: Array<Layout> = Array(PAGE_SIZE) { i ->
        Layout(
            slots = List(i + 1) { n ->
                Slot(GRID_X[n % GRID_COLUMNS], (n / GRID_COLUMNS).toFloat(), 0f)
            },
            motifs = emptyList()
        )
    }

    /**
     * Slot table for a page holding [count] charms (clamped to 1..[PAGE_SIZE]): the sticker
     * constellation when [scatter] is on (`pref_folder_scatter_layout`), else the neat grid.
     */
    fun layoutFor(count: Int, scatter: Boolean = true): Layout {
        val i = count.coerceIn(1, PAGE_SIZE) - 1
        return if (scatter) LAYOUTS[i] else GRID_LAYOUTS[i]
    }

    /** Result of a vertical move: either a new charm index, or leaving the tray. */
    sealed interface Move {
        data class To(val index: Int) : Move
        /** Up from the top row: focus the folder title header. */
        data object Header : Move
        /** Down from the bottom row: focus the Close pill. */
        data object Close : Move
    }

    fun pageCount(total: Int): Int = if (total <= 0) 1 else (total + PAGE_SIZE - 1) / PAGE_SIZE

    fun pageOf(index: Int, total: Int): Int {
        if (total <= 0) return 0
        return index.coerceIn(0, total - 1) / PAGE_SIZE
    }

    fun pageStart(page: Int): Int = page * PAGE_SIZE

    fun pageLast(page: Int, total: Int): Int =
        (pageStart(page) + PAGE_SIZE - 1).coerceAtMost(total - 1)

    fun countOnPage(page: Int, total: Int): Int = pageLast(page, total) - pageStart(page) + 1

    /** Left/Right walk reading order, so the last charm of a page flips to the next page. */
    fun left(index: Int, total: Int): Int = if (index > 0) index - 1 else index

    fun right(index: Int, total: Int): Int = if (index + 1 < total) index + 1 else index

    /** Up moves to the horizontally nearest charm one row higher; from the top row, the header. */
    fun up(index: Int, total: Int, scatter: Boolean = true): Move =
        vertical(index, total, downward = false, scatter = scatter)

    /** Down moves to the horizontally nearest charm one row lower; from the bottom row, Close. */
    fun down(index: Int, total: Int, scatter: Boolean = true): Move =
        vertical(index, total, downward = true, scatter = scatter)

    /**
     * Up from Close lands in the bottom row of [page], on the charm nearest to [rememberedIndex]
     * (the charm focused before leaving the tray) when it is on this page.
     */
    fun bottomRowTarget(page: Int, rememberedIndex: Int, total: Int, scatter: Boolean = true): Int {
        val start = pageStart(page)
        val layout = layoutFor(countOnPage(page, total), scatter)
        val from = rememberedIndex - start
        val fromX = layout.slots.getOrNull(from)?.x ?: 0.5f
        val ordinal = if (from in layout.slots.indices) ordinalInRow(layout, from) else 0
        return start + nearestInRow(layout, layout.depth, fromX, ordinal)
    }

    /** Down from the header lands on the first (top-left) charm of the page currently shown. */
    fun headerDownTarget(page: Int, total: Int): Int =
        pageStart(page).coerceAtMost((total - 1).coerceAtLeast(0))

    private fun vertical(index: Int, total: Int, downward: Boolean, scatter: Boolean): Move {
        val page = pageOf(index, total)
        val start = pageStart(page)
        val layout = layoutFor(countOnPage(page, total), scatter)
        val currentSlot = index - start
        val current = layout.slots[currentSlot]
        var targetRow = Float.NaN
        for (slot in layout.slots) {
            val isCandidate = if (downward) slot.y > current.y else slot.y < current.y
            if (!isCandidate) continue
            val closer = if (downward) slot.y < targetRow else slot.y > targetRow
            if (targetRow.isNaN() || closer) targetRow = slot.y
        }
        if (targetRow.isNaN()) return if (downward) Move.Close else Move.Header
        val ordinal = ordinalInRow(layout, currentSlot)
        return Move.To(start + nearestInRow(layout, targetRow, current.x, ordinal))
    }

    /** Position of [slot] within its own row, counted from the left. */
    private fun ordinalInRow(layout: Layout, slot: Int): Int {
        val y = layout.slots[slot].y
        var ordinal = 0
        for (i in 0 until slot) if (layout.slots[i].y == y) ordinal++
        return ordinal
    }

    /**
     * Slot index (within the page) in row [rowY] closest to [fromX]. Staggered rows put a charm
     * exactly between two neighbours, so ties go to the one with the same position in its row
     * ([preferOrdinal]): Down then Up returns to the charm you started on.
     */
    private fun nearestInRow(layout: Layout, rowY: Float, fromX: Float, preferOrdinal: Int): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        var bestMatchesOrdinal = false
        var ordinal = 0
        layout.slots.forEachIndexed { i, slot ->
            if (slot.y != rowY) return@forEachIndexed
            val distance = abs(slot.x - fromX)
            val matches = ordinal == preferOrdinal
            ordinal++
            val tie = abs(distance - bestDistance) < 1e-4f
            if ((!tie && distance < bestDistance) || (tie && matches && !bestMatchesOrdinal)) {
                best = i
                bestDistance = distance
                bestMatchesOrdinal = matches
            }
        }
        return best
    }
}
