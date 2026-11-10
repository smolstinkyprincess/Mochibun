package com.crinklecodone.mochibun.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the in-memory ActiveHookTheme state and dynamic color updates.
 */
class ActiveHookThemeTest {

    @Test
    fun testActiveHookThemeDefaults() {
        assertEquals(0xFFFFB7D5.toInt(), ActiveHookTheme.DEFAULT_PRIMARY)
        assertEquals(0xFFFFF9FB.toInt(), ActiveHookTheme.DEFAULT_CONTAINER)
        assertEquals(0xFF4A2E56.toInt(), ActiveHookTheme.DEFAULT_ACCENT_DEEP)
        assertEquals(0xFFEADDF8.toInt(), ActiveHookTheme.DEFAULT_TRACK)
        assertEquals(0xEBF6F0FC.toInt(), ActiveHookTheme.DEFAULT_SCRIM)
        assertEquals(0xFFF06292.toInt(), ActiveHookTheme.DEFAULT_LOW_BATTERY)

        assertEquals(ActiveHookTheme.DEFAULT_PRIMARY, ActiveHookTheme.primary)
        assertEquals(ActiveHookTheme.DEFAULT_CONTAINER, ActiveHookTheme.container)
        assertEquals(ActiveHookTheme.DEFAULT_ACCENT_DEEP, ActiveHookTheme.accentDeep)
        assertEquals(ActiveHookTheme.DEFAULT_TRACK, ActiveHookTheme.track)
        assertEquals(ActiveHookTheme.DEFAULT_SCRIM, ActiveHookTheme.scrim)
        assertEquals(ActiveHookTheme.DEFAULT_LOW_BATTERY, ActiveHookTheme.lowBattery)
    }

    @Test
    fun testActiveHookThemeUpdateAndAlphaDerivations() {
        val testPrimary = 0xFFC8A8E9.toInt()      // Lavender Mist Primary
        val testContainer = 0xFFEDE7F6.toInt()    // Lavender Mist Container
        val testAccentDeep = 0xFF5E35B1.toInt()   // Lavender Mist Deep Accent
        val testTrack = 0xFFB39DDB.toInt()        // Lavender Mist Secondary (Track)
        val testScrim = 0xEBF8F6FD.toInt()        // Lavender Mist Background @ 92% alpha

        ActiveHookTheme.updateColors(
            newPrimary = testPrimary,
            newContainer = testContainer,
            newAccentDeep = testAccentDeep,
            newTrack = testTrack,
            newScrim = testScrim,
            newLowBattery = ActiveHookTheme.DEFAULT_LOW_BATTERY
        )

        assertEquals(testPrimary, ActiveHookTheme.primary)
        assertEquals(testContainer, ActiveHookTheme.container)
        assertEquals(testAccentDeep, ActiveHookTheme.accentDeep)
        assertEquals(testTrack, ActiveHookTheme.track)
        assertEquals(testScrim, ActiveHookTheme.scrim)

        // Pre-cached ColorStateLists (checked if non-null in JVM host environment)
        ActiveHookTheme.primaryColorStateList?.let {
            assertEquals(testPrimary, it.defaultColor)
        }
        ActiveHookTheme.trackColorStateList?.let {
            assertEquals(testTrack, it.defaultColor)
        }
        ActiveHookTheme.accentDeepColorStateList?.let {
            assertEquals(testAccentDeep, it.defaultColor)
        }

        // Alpha derivations
        val expectedVolumeStroke = (0x66 shl 24) or (testAccentDeep and 0x00FFFFFF)
        assertEquals(expectedVolumeStroke, ActiveHookTheme.volumeStroke)

        val expectedQsTileStroke = (0x4D shl 24) or (testAccentDeep and 0x00FFFFFF)
        assertEquals(expectedQsTileStroke, ActiveHookTheme.qsTileStroke)

        val expectedUnavailableBg = (0x66 shl 24) or (testTrack and 0x00FFFFFF)
        assertEquals(expectedUnavailableBg, ActiveHookTheme.qsTileUnavailableBg)

        val expectedInactiveFg = (0x99 shl 24) or (testAccentDeep and 0x00FFFFFF)
        assertEquals(expectedInactiveFg, ActiveHookTheme.qsTileInactiveFg)

        val expectedUnavailableFg = (0x4D shl 24) or (testAccentDeep and 0x00FFFFFF)
        assertEquals(expectedUnavailableFg, ActiveHookTheme.qsTileUnavailableFg)

        // Reset back to defaults for clean test state
        ActiveHookTheme.updateColors(
            newPrimary = ActiveHookTheme.DEFAULT_PRIMARY,
            newContainer = ActiveHookTheme.DEFAULT_CONTAINER,
            newAccentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP,
            newTrack = ActiveHookTheme.DEFAULT_TRACK,
            newScrim = ActiveHookTheme.DEFAULT_SCRIM,
            newLowBattery = ActiveHookTheme.DEFAULT_LOW_BATTERY
        )
    }
}
