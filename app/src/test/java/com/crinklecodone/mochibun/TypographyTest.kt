package com.crinklecodone.mochibun

import androidx.compose.ui.text.font.FontFamily
import com.crinklecodone.mochibun.data.repository.TypographyManager
import com.crinklecodone.mochibun.ui.theme.MaruMinyaMFontFamily
import com.crinklecodone.mochibun.ui.theme.getLauncherTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTest {

    @Test
    fun testBuiltInTypographyOptionsIntegrity() {
        val options = TypographyManager.BuiltInTypographyOptions
        assertEquals(5, options.size)

        val ids = options.map { it.id }
        assertTrue(ids.contains("default"))
        assertTrue(ids.contains("clean_sans"))
        assertTrue(ids.contains("pixel"))
        assertTrue(ids.contains("gothic"))
        assertTrue(ids.contains("serif"))
    }

    @Test
    fun testResolveFontFamily() {
        assertEquals(MaruMinyaMFontFamily, TypographyManager.resolveFontFamily("default"))
        assertEquals(FontFamily.Default, TypographyManager.resolveFontFamily("clean_sans"))
        assertEquals(FontFamily.Monospace, TypographyManager.resolveFontFamily("pixel"))
        assertEquals(FontFamily.SansSerif, TypographyManager.resolveFontFamily("gothic"))
        assertEquals(FontFamily.Serif, TypographyManager.resolveFontFamily("serif"))
        // Unknown defaults to MaruMinyaMFontFamily
        assertEquals(MaruMinyaMFontFamily, TypographyManager.resolveFontFamily("unknown_custom_id"))
    }

    @Test
    fun testLauncherTypographyGeneration() {
        val defaultTypography = getLauncherTypography(FontFamily.Default)
        assertNotNull(defaultTypography.titleLarge)
        assertEquals(FontFamily.Default, defaultTypography.titleLarge.fontFamily)

        val pixelTypography = getLauncherTypography(FontFamily.Monospace)
        assertNotNull(pixelTypography.titleLarge)
        assertEquals(FontFamily.Monospace, pixelTypography.titleLarge.fontFamily)
    }
}
