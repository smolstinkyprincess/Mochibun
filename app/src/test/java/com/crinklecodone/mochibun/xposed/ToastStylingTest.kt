package com.crinklecodone.mochibun.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Mochibun Stage E System Toast styling and Keitai heart framing.
 */
class ToastStylingTest {

    @Test
    fun testToastTextFormatterWrapping() {
        // Plain text should be wrapped with soft hearts
        assertEquals("♡ Copied to clipboard ♡", ToastTextFormatter.format("Copied to clipboard"))
        assertEquals("♡ M120 Launcher ♡", ToastTextFormatter.format("M120 Launcher"))
        assertEquals("♡ 12345 ♡", ToastTextFormatter.format("12345"))

        // Already framed or containing hearts should not be double wrapped
        assertEquals("♡ Already Framed ♡", ToastTextFormatter.format("♡ Already Framed ♡"))
        assertEquals("♥ Love ♡", ToastTextFormatter.format("♥ Love ♡"))
        assertEquals("♡", ToastTextFormatter.format("♡"))

        // Empty / blank / null strings
        assertEquals("", ToastTextFormatter.format(""))
        assertEquals("", ToastTextFormatter.format(null))
    }

    @Test
    fun testToastPaletteDefaults() {
        // Background container: #FFF9FB (Cream / Soft Surface)
        assertEquals(0xFFFFF9FB.toInt(), MochibunSystemUiHook.COLOR_TOAST_BG)

        // Border stroke: Deep Plum #4A2E56 at 30% alpha (0x4D4A2E56)
        assertEquals(0x4D4A2E56.toInt(), MochibunSystemUiHook.COLOR_TOAST_STROKE)

        // Text color: Deep Plum #4A2E56
        assertEquals(0xFF4A2E56.toInt(), MochibunSystemUiHook.COLOR_TOAST_TEXT)
    }

    @Test
    fun testActiveHookThemeToastStrokeDynamicCalculation() {
        // Reset to default theme
        ActiveHookTheme.updateColors(
            newPrimary = ActiveHookTheme.DEFAULT_PRIMARY,
            newContainer = ActiveHookTheme.DEFAULT_CONTAINER,
            newAccentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP,
            newTrack = ActiveHookTheme.DEFAULT_TRACK,
            newScrim = ActiveHookTheme.DEFAULT_SCRIM
        )

        // Verify default stroke is 30% alpha (0x4D) of DEFAULT_ACCENT_DEEP (#4A2E56)
        val expectedDefaultStroke = (0x4D shl 24) or (0x4A2E56)
        assertEquals(expectedDefaultStroke, ActiveHookTheme.toastStroke)

        // Update accent deep color (e.g. customized theme with brown/cocoa accent #3E2723)
        ActiveHookTheme.accentDeep = 0xFF3E2723.toInt()
        val expectedCustomStroke = (0x4D shl 24) or (0x3E2723)
        assertEquals(expectedCustomStroke, ActiveHookTheme.toastStroke)

        // Restore default
        ActiveHookTheme.accentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP
    }
}
