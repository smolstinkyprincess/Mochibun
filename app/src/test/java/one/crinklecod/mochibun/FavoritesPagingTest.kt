package one.crinklecod.mochibun

import one.crinklecod.mochibun.ui.screens.FAVORITES_PAGE_SIZE
import one.crinklecod.mochibun.ui.screens.favoritePageCount
import one.crinklecod.mochibun.ui.screens.favoritePageForIndex
import one.crinklecod.mochibun.ui.screens.firstFavoriteIndexOfPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** Paging math behind the 3-slot Favorites carousel on the Home card. */
class FavoritesPagingTest {

    @Test
    fun pageSizeIsThree() {
        assertEquals(3, FAVORITES_PAGE_SIZE)
    }

    @Test
    fun pageCountRoundsUpAndNeverDropsBelowOne() {
        assertEquals(1, favoritePageCount(0))
        assertEquals(1, favoritePageCount(1))
        assertEquals(1, favoritePageCount(3))
        assertEquals(2, favoritePageCount(4))
        assertEquals(2, favoritePageCount(6))
        assertEquals(3, favoritePageCount(7))
    }

    @Test
    fun dpadSelectionAcrossBoundaryFlipsPage() {
        // Right from the 3rd slot (index 2) lands on index 3 = page 1, and Left goes back.
        assertEquals(0, favoritePageForIndex(2, 6))
        assertEquals(1, favoritePageForIndex(3, 6))
        assertEquals(1, favoritePageForIndex(5, 6))
    }

    @Test
    fun pageForIndexIsClampedToExistingPages() {
        assertEquals(0, favoritePageForIndex(0, 0))
        assertEquals(1, favoritePageForIndex(99, 5)) // stale index after removals
        assertEquals(0, favoritePageForIndex(-1, 5))
    }

    @Test
    fun jumpingToPageSelectsItsFirstSlot() {
        assertEquals(0, firstFavoriteIndexOfPage(0, 7))
        assertEquals(3, firstFavoriteIndexOfPage(1, 7))
        assertEquals(6, firstFavoriteIndexOfPage(2, 7)) // short last page
        assertEquals(6, firstFavoriteIndexOfPage(5, 7)) // past the end clamps to last item
        assertEquals(0, firstFavoriteIndexOfPage(1, 0)) // empty list
    }
}
