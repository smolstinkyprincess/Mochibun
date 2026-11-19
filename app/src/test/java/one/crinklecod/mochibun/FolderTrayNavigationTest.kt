package one.crinklecod.mochibun

import one.crinklecod.mochibun.ui.dialogs.FolderTrayNav
import one.crinklecod.mochibun.ui.dialogs.FolderTrayNav.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layout + D-pad contract of the Favorite Folder sticker tray (≤ 6 scattered charms a page). */
class FolderTrayNavigationTest {

    @Test
    fun pagesHoldSixCharms() {
        assertEquals(6, FolderTrayNav.PAGE_SIZE)
        assertEquals(1, FolderTrayNav.pageCount(0))
        assertEquals(1, FolderTrayNav.pageCount(6))
        assertEquals(2, FolderTrayNav.pageCount(7))
        assertEquals(1, FolderTrayNav.pageOf(6, 8))
        assertEquals(2, FolderTrayNav.countOnPage(1, 8))
        assertEquals(0, FolderTrayNav.pageOf(99, 0))
    }

    @Test
    fun everyLayoutHasOneSlotPerCharmInReadingOrder() {
        for (count in 1..6) {
            val layout = FolderTrayNav.layoutFor(count)
            assertEquals(count, layout.slots.size)
            assertTrue("2-3 motifs for $count", layout.motifs.size in 2..3)
            layout.slots.zipWithNext().forEach { (a, b) ->
                assertTrue("reading order for $count", a.y < b.y || (a.y == b.y && a.x < b.x))
            }
            layout.slots.forEach { assertTrue(it.x in 0f..1f && it.tilt in -6f..6f) }
        }
    }

    @Test
    fun layoutTiltsMatchTheStickerSpec() {
        fun tilts(n: Int) = FolderTrayNav.layoutFor(n).slots.map { it.tilt }
        assertEquals(listOf(0f), tilts(1))
        assertEquals(listOf(-6f, 6f), tilts(2))
        assertEquals(listOf(0f, -5f, 5f), tilts(3))
        assertEquals(listOf(-5f, 5f, 4f, -4f), tilts(4))
        assertEquals(listOf(-5f, 5f, 0f, 4f, -4f), tilts(5))
        assertEquals(listOf(-4f, 4f, -4f, 4f, -4f, 4f), tilts(6))
    }

    @Test
    fun leftRightWalkReadingOrderAndFlipPages() {
        assertEquals(6, FolderTrayNav.right(5, 8)) // last charm of page 0 -> page 1
        assertEquals(5, FolderTrayNav.left(6, 8)) // and back
        assertEquals(0, FolderTrayNav.left(0, 8))
        assertEquals(7, FolderTrayNav.right(7, 8))
    }

    @Test
    fun singleRowLayoutsLeaveTheTrayVertically() {
        assertEquals(Move.Header, FolderTrayNav.up(0, 1))
        assertEquals(Move.Close, FolderTrayNav.down(0, 1))
    }

    @Test
    fun diagonalPairAndTriangle() {
        assertEquals(Move.To(1), FolderTrayNav.down(0, 2))
        assertEquals(Move.To(0), FolderTrayNav.up(1, 2))
        // Triangle: top-centre goes down to the left foot (tie -> left); feet go back up to it.
        assertEquals(Move.To(1), FolderTrayNav.down(0, 3))
        assertEquals(Move.To(0), FolderTrayNav.up(2, 3))
        assertEquals(Move.Close, FolderTrayNav.down(2, 3))
    }

    @Test
    fun staggeredFourMovesToNearestCharm() {
        // Top (0, 0.56), bottom (0.44, 1).
        assertEquals(Move.To(2), FolderTrayNav.down(0, 4))
        assertEquals(Move.To(2), FolderTrayNav.down(1, 4)) // 0.56 is nearer 0.44 than 1
        assertEquals(Move.To(1), FolderTrayNav.up(3, 4))
        assertEquals(Move.To(1), FolderTrayNav.up(2, 4)) // 0.44 is nearer 0.56 than 0
    }

    @Test
    fun quincunxPassesThroughTheCentreJewel() {
        assertEquals(Move.To(2), FolderTrayNav.down(0, 5))
        assertEquals(Move.To(2), FolderTrayNav.down(1, 5))
        assertEquals(Move.To(3), FolderTrayNav.down(2, 5))
        assertEquals(Move.To(2), FolderTrayNav.up(4, 5))
        assertEquals(Move.Header, FolderTrayNav.up(1, 5))
        assertEquals(Move.Close, FolderTrayNav.down(4, 5))
    }

    @Test
    fun staggeredSixAndSecondPage() {
        // Every top charm sits exactly between two lower ones: ties keep the in-row position,
        // so Down then Up round-trips.
        for (top in 0..2) {
            assertEquals(Move.To(top + 3), FolderTrayNav.down(top, 6))
            assertEquals(Move.To(top), FolderTrayNav.up(top + 3, 6))
        }
        // Page 1 of 8 apps holds 2 -> diagonal pair layout.
        assertEquals(Move.To(7), FolderTrayNav.down(6, 8))
        assertEquals(Move.Header, FolderTrayNav.up(6, 8))
    }

    @Test
    fun upFromCloseReturnsToBottomRowNearestRememberedCharm() {
        assertEquals(3, FolderTrayNav.bottomRowTarget(page = 0, rememberedIndex = 0, total = 6))
        assertEquals(5, FolderTrayNav.bottomRowTarget(page = 0, rememberedIndex = 2, total = 6))
        assertEquals(4, FolderTrayNav.bottomRowTarget(page = 0, rememberedIndex = 1, total = 5))
        assertEquals(0, FolderTrayNav.bottomRowTarget(page = 0, rememberedIndex = 0, total = 1))
        assertEquals(7, FolderTrayNav.bottomRowTarget(page = 1, rememberedIndex = 6, total = 8))
    }

    @Test
    fun scatterAndNeatGridModesShareSlotCountsButDifferInShape() {
        for (count in 1..6) {
            val scatter = FolderTrayNav.layoutFor(count, scatter = true)
            val grid = FolderTrayNav.layoutFor(count, scatter = false)
            assertEquals(count, scatter.slots.size)
            assertEquals(count, grid.slots.size)
            // Neat grid: no tilt, no sparkles, fixed 3 columns filled left to right, rows 0 and 1.
            assertTrue(grid.slots.all { it.tilt == 0f })
            assertTrue(grid.motifs.isEmpty())
            grid.slots.forEachIndexed { i, slot ->
                assertEquals(listOf(0.1f, 0.5f, 0.9f)[i % 3], slot.x)
                assertEquals((i / 3).toFloat(), slot.y)
            }
        }
        // Scatter stays the default.
        assertEquals(FolderTrayNav.layoutFor(5, scatter = true), FolderTrayNav.layoutFor(5))
        assertTrue(FolderTrayNav.layoutFor(5).slots.any { it.tilt != 0f })
    }

    @Test
    fun neatGridNavigatesStraightColumns() {
        // 6 apps: columns line up, Down/Up round-trip, edges leave to Header / Close.
        for (col in 0..2) {
            assertEquals(Move.To(col + 3), FolderTrayNav.down(col, 6, scatter = false))
            assertEquals(Move.To(col), FolderTrayNav.up(col + 3, 6, scatter = false))
            assertEquals(Move.Header, FolderTrayNav.up(col, 6, scatter = false))
            assertEquals(Move.Close, FolderTrayNav.down(col + 3, 6, scatter = false))
        }
        // 4 apps: lone charm in row 2 sits under column 0, so Down from column 2 lands on it.
        assertEquals(Move.To(3), FolderTrayNav.down(2, 4, scatter = false))
        assertEquals(Move.To(0), FolderTrayNav.up(3, 4, scatter = false))
        // A single row of 3 goes straight to Close; the quincunx would have had a middle row.
        assertEquals(Move.Close, FolderTrayNav.down(0, 3, scatter = false))
        assertEquals(Move.To(4), FolderTrayNav.down(2, 5, scatter = false))
        assertEquals(Move.To(2), FolderTrayNav.down(1, 5, scatter = true))
        // Up from Close keeps the column; Left/Right (reading order) are the same in both modes.
        assertEquals(5, FolderTrayNav.bottomRowTarget(0, rememberedIndex = 2, total = 6, scatter = false))
        assertEquals(7, FolderTrayNav.right(6, 8))
    }

    @Test
    fun downFromHeaderLandsOnFirstCharmOfShownPage() {
        assertEquals(0, FolderTrayNav.headerDownTarget(0, 5))
        assertEquals(6, FolderTrayNav.headerDownTarget(1, 8))
    }
}
