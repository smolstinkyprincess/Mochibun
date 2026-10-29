package com.crinklecodone.mochibun

import android.content.ComponentCallbacks2
import com.crinklecodone.mochibun.core.system.MemoryTrimTier
import com.crinklecodone.mochibun.core.system.memoryTrimTier
import com.crinklecodone.mochibun.core.util.isFullyOpaque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTrimTest {

    @Test
    fun runningPressureBelowCriticalKeepsEverything() {
        assertEquals(MemoryTrimTier.NONE, memoryTrimTier(launcherVisible = true, level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        assertEquals(MemoryTrimTier.NONE, memoryTrimTier(launcherVisible = true, level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    }

    @Test
    fun uiHiddenOnlyDropsPickerThumbnails() {
        // Fires on every app launch from the launcher, so it must never release the drawer
        assertEquals(MemoryTrimTier.PICKER, memoryTrimTier(launcherVisible = true, level = ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertEquals(MemoryTrimTier.PICKER, memoryTrimTier(launcherVisible = true, level = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
    }

    @Test
    fun backgroundLevelsDropMetadata() {
        assertEquals(MemoryTrimTier.BACKGROUND, memoryTrimTier(launcherVisible = true, level = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertEquals(MemoryTrimTier.BACKGROUND, memoryTrimTier(launcherVisible = true, level = ComponentCallbacks2.TRIM_MEMORY_MODERATE))
    }

    @Test
    fun criticalReleasesTheDrawerOnlyWhenTheLauncherIsHidden() {
        // The a11y-bound process never sees background levels; RUNNING_CRITICAL is its real signal
        assertEquals(MemoryTrimTier.RELEASE_DRAWER,
            memoryTrimTier(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, launcherVisible = false))
        assertEquals(MemoryTrimTier.PICKER,
            memoryTrimTier(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, launcherVisible = true))
        assertEquals(MemoryTrimTier.NONE,
            memoryTrimTier(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, launcherVisible = false))
    }

    @Test
    fun completeReleasesTheDrawer() {
        assertEquals(MemoryTrimTier.RELEASE_DRAWER, memoryTrimTier(launcherVisible = true, level = ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }

    @Test
    fun opacityCheckRejectsAnyTransparentPixel() {
        assertTrue(isFullyOpaque(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF123456.toInt())))
        assertFalse(isFullyOpaque(intArrayOf(0xFF000000.toInt(), 0xFE000000.toInt())))
        assertFalse(isFullyOpaque(intArrayOf(0x00000000)))
        assertFalse(isFullyOpaque(IntArray(0)))
    }
}
