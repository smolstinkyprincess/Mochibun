package com.crinklecodone.mochibun

import androidx.compose.ui.graphics.Color
import com.charleskorn.kaml.Yaml
import com.crinklecodone.mochibun.data.model.YamlTheme
import com.crinklecodone.mochibun.data.model.safeParseColorOrNull
import com.crinklecodone.mochibun.data.model.toLauncherPalette
import com.crinklecodone.mochibun.data.model.toLauncherTheme
import com.crinklecodone.mochibun.ui.components.mascotTintColor
import com.crinklecodone.mochibun.ui.theme.LavenderMistPalette
import com.crinklecodone.mochibun.ui.theme.MilkyPeachPalette
import com.crinklecodone.mochibun.ui.theme.MintyMatchaPalette
import com.crinklecodone.mochibun.ui.theme.SakuraDreamPalette
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YamlThemeMascotTintTest {

    @Test
    fun builtInPresets_haveExpectedMascotTintColors() {
        assertEquals(Color(0xFFFFEBF2), SakuraDreamPalette.mascotTint)
        assertEquals(Color(0xFFF3EDFF), LavenderMistPalette.mascotTint)
        assertEquals(Color(0xFFEDF8F1), MintyMatchaPalette.mascotTint)
        assertEquals(Color(0xFFFFF0E6), MilkyPeachPalette.mascotTint)
    }

    @Test
    fun safeParseColorOrNull_handlesValidAndInvalidHex() {
        assertEquals(Color(0xFFFFF0E6), safeParseColorOrNull("#FFF0E6"))
        assertEquals(Color(0xFFFFF0E6), safeParseColorOrNull("FFF0E6"))
        assertEquals(Color(0x80FFF0E6.toInt()), safeParseColorOrNull("#80FFF0E6"))
        assertNull(safeParseColorOrNull(null))
        assertNull(safeParseColorOrNull(""))
        assertNull(safeParseColorOrNull("none"))
        assertNull(safeParseColorOrNull("null"))
        assertNull(safeParseColorOrNull("not-a-color"))
    }

    @Test
    fun yamlTheme_withExplicitMascotTint_isParsedAndResolved() {
        val yaml = """
            id: "custom_marshmallow"
            name: "Toasted Marshmallow"
            colors:
              primary: "#FFAA77"
              mascotTint: "#FFF0E6"
        """.trimIndent()

        val parsed = Yaml.default.decodeFromString<YamlTheme>(yaml)
        val palette = parsed.toLauncherPalette()
        val theme = parsed.toLauncherTheme()

        assertNotNull(palette.mascotTint)
        assertEquals(Color(0xFFFFF0E6), palette.mascotTint)
        assertEquals(Color(0xFFFFF0E6), theme.mascotTintColor)
    }

    @Test
    fun yamlTheme_withoutMascotTint_defaultsToNullNaturalFur() {
        val yaml = """
            id: "custom_monochrome"
            name: "Monochrome Minimal"
            colors:
              primary: "#333333"
        """.trimIndent()

        val parsed = Yaml.default.decodeFromString<YamlTheme>(yaml)
        val palette = parsed.toLauncherPalette()
        val theme = parsed.toLauncherTheme()

        assertNull(palette.mascotTint)
        assertNull(theme.mascotTintColor)
    }
}
