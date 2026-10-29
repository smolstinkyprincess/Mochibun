package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.keypad.LauncherKeypadState
import com.crinklecodone.mochibun.data.model.AppDrawerSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftKeyRemapUnitTest {

    private fun createTestState(
        anyModalOpen: Boolean = false,
        drawerOpen: Boolean = false,
        isHomeScreenActive: Boolean = false,
        currentPage: Int = 1
    ): LauncherKeypadState {
        return LauncherKeypadState(
            anyModalOpen = anyModalOpen,
            drawerOpen = drawerOpen,
            isHomeScreenActive = isHomeScreenActive,
            isPageTransitioning = false,
            pendingTargetPage = null,
            favoriteItemCount = 0,
            selectedFavoriteIndex = 0,
            savedFavoriteIndexOnHome = 0,
            homeFocusArea = 0,
            centerKeyState = 0,
            selectedDrawerIndex = 0,
            visibleApps = emptyList(),
            letterStarts = emptyMap(),
            currentPage = currentPage
        )
    }

    @Test
    fun testHomeScreenStateGating() {
        val state = createTestState(
            anyModalOpen = false,
            drawerOpen = false,
            isHomeScreenActive = true,
            currentPage = 1
        )
        assertTrue(state.isHomeScreenActive)
        assertFalse(state.isSidePageActive)
        assertFalse(state.isDrawerActive)
        assertFalse(state.isModalActive)
    }

    @Test
    fun testSidePagesStateGating() {
        // Page 0: Stats
        val statsState = createTestState(
            anyModalOpen = false,
            drawerOpen = false,
            isHomeScreenActive = false,
            currentPage = 0
        )
        assertFalse(statsState.isHomeScreenActive)
        assertTrue(statsState.isSidePageActive)
        assertFalse(statsState.isDrawerActive)
        assertFalse(statsState.isModalActive)

        // Page 2: Notifications
        val notifsState = createTestState(
            anyModalOpen = false,
            drawerOpen = false,
            isHomeScreenActive = false,
            currentPage = 2
        )
        assertFalse(notifsState.isHomeScreenActive)
        assertTrue(notifsState.isSidePageActive)
        assertFalse(notifsState.isDrawerActive)
        assertFalse(notifsState.isModalActive)
    }

    @Test
    fun testDrawerStateGating() {
        val drawerState = createTestState(
            anyModalOpen = false,
            drawerOpen = true,
            isHomeScreenActive = false,
            currentPage = 1
        )
        assertFalse(drawerState.isHomeScreenActive)
        assertFalse(drawerState.isSidePageActive)
        assertTrue(drawerState.isDrawerActive)
        assertFalse(drawerState.isModalActive)
    }

    @Test
    fun testModalStateGating() {
        val modalState = createTestState(
            anyModalOpen = true,
            drawerOpen = true,
            isHomeScreenActive = false,
            currentPage = 1
        )
        assertFalse(modalState.isHomeScreenActive)
        assertFalse(modalState.isSidePageActive)
        assertFalse(modalState.isDrawerActive)
        assertTrue(modalState.isModalActive)
    }

    @Test
    fun testAppDrawerSortCycle() {
        var mode = AppDrawerSortMode.ALPHABETICAL
        mode = mode.next()
        assertEquals(AppDrawerSortMode.NEWEST, mode)
        mode = mode.next()
        assertEquals(AppDrawerSortMode.MOST_USED, mode)
        mode = mode.next()
        assertEquals(AppDrawerSortMode.ALPHABETICAL, mode)
    }
}
