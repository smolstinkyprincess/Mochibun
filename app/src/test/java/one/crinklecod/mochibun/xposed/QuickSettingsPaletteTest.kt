package one.crinklecod.mochibun.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the Mochibun Stage D Quick Settings pastel palette definitions.
 */
class QuickSettingsPaletteTest {

    @Test
    fun testQuickSettingsBackdropColor() {
        // Frosted ivory / translucent lavender: #F6F0FC at ~92% opacity (ARGB: 0xEB, 0xF6, 0xF0, 0xFC)
        val expected = (0xEB shl 24) or (0xF6 shl 16) or (0xF0 shl 8) or 0xFC
        assertEquals(expected, MochibunSystemUiHook.COLOR_QS_BACKDROP_TINT)
    }

    @Test
    fun testQuickSettingsTileStrokeColor() {
        // Deep Plum #4A2E56 at 30% alpha (0x4D4A2E56)
        val expected = (0x4D shl 24) or (0x4A shl 16) or (0x2E shl 8) or 0x56
        assertEquals(expected, MochibunSystemUiHook.COLOR_QS_TILE_STROKE)
    }

    @Test
    fun testQuickSettingsTileFillColors() {
        // Active (State 2): Sakura Pink #FFB7D5
        assertEquals(0xFFFFB7D5.toInt(), MochibunSystemUiHook.COLOR_QS_TILE_ACTIVE_BG)

        // Inactive (State 1): Cream / Soft Surface #FFF9FB
        assertEquals(0xFFFFF9FB.toInt(), MochibunSystemUiHook.COLOR_QS_TILE_INACTIVE_BG)

        // Unavailable (State 0): Muted Soft Lilac #EADDF8 at 40% alpha (0x66EADDF8)
        assertEquals(0x66EADDF8.toInt(), MochibunSystemUiHook.COLOR_QS_TILE_UNAVAILABLE_BG)
    }

    @Test
    fun testQuickSettingsTileForegroundColors() {
        // Active (State 2): Deep Plum #4A2E56 at 100%
        assertEquals(0xFF4A2E56.toInt(), MochibunSystemUiHook.COLOR_QS_TILE_ACTIVE_FG)

        // Inactive (State 1): Deep Plum #4A2E56 at 60% alpha (0x994A2E56)
        assertEquals(0x994A2E56.toInt(), MochibunSystemUiHook.COLOR_QS_TILE_INACTIVE_FG)

        // Unavailable (State 0): Deep Plum #4A2E56 at 30% alpha (0x4D4A2E56)
        assertEquals(0x4D4A2E56.toInt(), MochibunSystemUiHook.COLOR_QS_TILE_UNAVAILABLE_FG)
    }

    @Test
    fun testBrightnessSliderColors() {
        // Active progress: Sakura Pink #FFB7D5
        assertEquals(0xFFFFB7D5.toInt(), MochibunSystemUiHook.COLOR_BRIGHTNESS_PROGRESS)

        // Inactive background track: Soft Lilac #EADDF8
        assertEquals(0xFFEADDF8.toInt(), MochibunSystemUiHook.COLOR_BRIGHTNESS_TRACK)

        // Sun icon: Deep Plum #4A2E56
        assertEquals(0xFF4A2E56.toInt(), MochibunSystemUiHook.COLOR_BRIGHTNESS_ICON)
    }

    @Test
    fun testFooterActionButtonsPalette() {
        // Background fill: Soft Cream #FFF9FB
        assertEquals(0xFFFFF9FB.toInt(), MochibunSystemUiHook.COLOR_QS_FOOTER_BTN_BG)

        // Border stroke: Deep Plum #4A2E56 at 30% alpha (0x4D4A2E56)
        assertEquals(0x4D4A2E56.toInt(), MochibunSystemUiHook.COLOR_QS_FOOTER_BTN_STROKE)

        // Icon tint: Deep Plum #4A2E56
        assertEquals(0xFF4A2E56.toInt(), MochibunSystemUiHook.COLOR_QS_FOOTER_BTN_ICON)
    }
}
