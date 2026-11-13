package com.crinklecodone.mochibun

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.crinklecodone.mochibun.ui.theme.ColorContrast
import com.crinklecodone.mochibun.ui.theme.LauncherTheme
import com.crinklecodone.mochibun.ui.theme.LavenderMistPalette
import com.crinklecodone.mochibun.ui.theme.MidnightMochiPalette
import com.crinklecodone.mochibun.ui.theme.MidnightMochiTheme
import com.crinklecodone.mochibun.ui.theme.MilkyPeachPalette
import com.crinklecodone.mochibun.ui.theme.MintyMatchaPalette
import com.crinklecodone.mochibun.ui.theme.SakuraDreamPalette
import com.crinklecodone.mochibun.ui.theme.SystemUiThemeBridge
import com.crinklecodone.mochibun.ui.theme.palette
import com.crinklecodone.mochibun.ui.theme.unfocusedCardBgColor
import com.crinklecodone.mochibun.xposed.ActiveHookTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the dedicated Midnight Mochi dark pastel palette, its preset wiring, and that the
 * SystemUI hook stays legible when it is broadcast.
 */
class MidnightMochiPaletteTest {

    private fun ratio(fg: Color, bg: Color) = ColorContrast.ratio(fg.toArgb(), bg.toArgb())

    private fun applyToHook(colors: SystemUiThemeBridge.SystemUiColors) {
        ActiveHookTheme.updateColors(
            newPrimary = colors.primary,
            newContainer = colors.container,
            newAccentDeep = colors.accentDeep,
            newTrack = colors.track,
            newScrim = colors.scrim
        )
    }

    @After
    fun resetHookTheme() {
        ActiveHookTheme.updateColors(
            newPrimary = ActiveHookTheme.DEFAULT_PRIMARY,
            newContainer = ActiveHookTheme.DEFAULT_CONTAINER,
            newAccentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP,
            newTrack = ActiveHookTheme.DEFAULT_TRACK,
            newScrim = ActiveHookTheme.DEFAULT_SCRIM
        )
    }

    @Test
    fun paletteTokens() {
        val p = MidnightMochiPalette
        assertEquals("midnight_mochi", p.id)
        assertEquals(Color(0xFF1A1226), p.background)
        assertEquals(Color(0xFF281D38), p.container)
        assertEquals(Color(0xFF332547), p.unfocusedCardBg)
        assertEquals(Color(0xFFD9B4F8), p.primary)
        assertEquals(Color(0xFFF3D9FF), p.deepAccent)
        assertEquals(Color(0xFF5A437A), p.outline)
        assertEquals(Color(0xFFF9F2FF), p.textColor)
        assertEquals(Color(0xFFC8B6DF), MidnightMochiTheme.cardTextColor)
    }

    @Test
    fun presetResolvesToDedicatedPaletteInsteadOfSakura() {
        assertSame(MidnightMochiPalette, MidnightMochiTheme.palette)
        assertSame(MidnightMochiPalette, LauncherTheme(name = "midnight_mochi").palette)
        assertEquals(MidnightMochiPalette.background, MidnightMochiTheme.bgColor)
        assertEquals(MidnightMochiPalette.unfocusedCardBg, MidnightMochiTheme.unfocusedCardBgColor)
    }

    @Test
    fun launcherTextIsLegibleOnDarkSurfaces() {
        val p = MidnightMochiPalette
        val surfaces = listOf(p.background, p.container, p.unfocusedCardBg, p.focusedCardContainer)
        for (surface in surfaces) {
            assertTrue(ratio(p.textColor, surface) >= 4.5)
            assertTrue(ratio(p.deepAccent, surface) >= 4.5)
            assertTrue(ratio(MidnightMochiTheme.cardTextColor, surface) >= 4.5)
        }
    }

    @Test
    fun systemUiInksStayLegibleWithMidnightMochi() {
        val colors = SystemUiThemeBridge.systemUiColors(MidnightMochiPalette)
        assertEquals(0xEB1A1226.toInt(), colors.scrim)
        applyToHook(colors)

        // Active QS tile / ringer pill / notification actions: dark ink on the light primary fill
        assertEquals(colors.container, ActiveHookTheme.onPrimary)
        assertTrue(ColorContrast.ratio(ActiveHookTheme.onPrimary, ActiveHookTheme.primary) >= 4.5)
        // Volume slider icon on the track fill
        assertTrue(ColorContrast.ratio(ActiveHookTheme.onTrack, ActiveHookTheme.track) >= 4.5)
        // Inactive QS tiles, notification cards, volume dialog: light ink on the dark container
        assertTrue(ColorContrast.ratio(ActiveHookTheme.accentDeep, ActiveHookTheme.container) >= 4.5)
        assertTrue(ColorContrast.ratio(ActiveHookTheme.accentDeep, colors.scrim) >= 4.5)
        // Status bar: the "dark" tone is still the darker one, so icons never invert
        assertEquals(colors.container, ActiveHookTheme.statusIconDarkTone)
        assertEquals(colors.accentDeep, ActiveHookTheme.statusIconLightTone)
    }

    @Test
    fun lightPalettesKeepAccentDeepInk() {
        for (light in listOf(SakuraDreamPalette, LavenderMistPalette, MintyMatchaPalette, MilkyPeachPalette)) {
            val colors = SystemUiThemeBridge.systemUiColors(light)
            applyToHook(colors)
            assertEquals(light.id, colors.accentDeep, ActiveHookTheme.onPrimary)
            assertEquals(light.id, colors.accentDeep, ActiveHookTheme.statusIconDarkTone)
            assertEquals(light.id, colors.container, ActiveHookTheme.statusIconLightTone)
        }
    }
}
