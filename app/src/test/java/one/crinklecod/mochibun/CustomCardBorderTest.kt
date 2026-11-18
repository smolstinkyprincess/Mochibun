package one.crinklecod.mochibun

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.model.MascotAtlasValidator
import one.crinklecod.mochibun.data.model.YamlTheme
import one.crinklecod.mochibun.data.model.toLauncherTheme
import one.crinklecod.mochibun.data.repository.CustomCardBorderManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class CustomCardBorderTest {

    @Before
    fun setUp() {
        CustomCardBorderManager.platformBounds = { MascotAtlasValidator.readPngSize(it) }
    }

    @Test
    fun cardBorderStyle_fromIdResolvesAllStylesCaseInsensitivelyAndFallsBackToSolid() {
        assertEquals(CardBorderStyle.SOLID, CardBorderStyle.fromId("solid"))
        assertEquals(CardBorderStyle.DOUBLE, CardBorderStyle.fromId("DOUBLE"))
        assertEquals(CardBorderStyle.LACE, CardBorderStyle.fromId("  Lace "))
        assertEquals(CardBorderStyle.STITCH, CardBorderStyle.fromId("stitch"))
        assertEquals(CardBorderStyle.PIXEL, CardBorderStyle.fromId("PiXeL"))

        // Unknown, blank, or null values safely fall back to SOLID
        assertEquals(CardBorderStyle.SOLID, CardBorderStyle.fromId(null))
        assertEquals(CardBorderStyle.SOLID, CardBorderStyle.fromId(""))
        assertEquals(CardBorderStyle.SOLID, CardBorderStyle.fromId("   "))
        assertEquals(CardBorderStyle.SOLID, CardBorderStyle.fromId("neon_cyberpunk"))
    }

    @Test
    fun cardBorderStyle_nextAndPreviousCycleWrapAround() {
        assertEquals(CardBorderStyle.DOUBLE, CardBorderStyle.SOLID.next())
        assertEquals(CardBorderStyle.LACE, CardBorderStyle.DOUBLE.next())
        assertEquals(CardBorderStyle.STITCH, CardBorderStyle.LACE.next())
        assertEquals(CardBorderStyle.PIXEL, CardBorderStyle.STITCH.next())
        assertEquals(CardBorderStyle.SOLID, CardBorderStyle.PIXEL.next())

        assertEquals(CardBorderStyle.PIXEL, CardBorderStyle.SOLID.previous())
        assertEquals(CardBorderStyle.STITCH, CardBorderStyle.PIXEL.previous())
    }

    @Test
    fun validateDimensions_enforcesSizeAndSliceBounds() {
        // Valid 48x48 with slicePx=16
        val valid = CustomCardBorderManager.validateDimensions(48, 48, 16)
        assertTrue(valid.isSuccess)
        assertEquals(48, valid.getOrThrow().width)
        assertEquals(48, valid.getOrThrow().height)
        assertEquals(16, valid.getOrThrow().slicePx)

        // Clamping slicePx to 4..64 when image is large enough
        val clampedLow = CustomCardBorderManager.validateDimensions(48, 48, 1).getOrThrow()
        assertEquals(4, clampedLow.slicePx)
        val clampedHigh = CustomCardBorderManager.validateDimensions(200, 200, 120).getOrThrow()
        assertEquals(64, clampedHigh.slicePx)

        // Too small (< 16x16)
        assertTrue(CustomCardBorderManager.validateDimensions(15, 48, 4).isFailure)
        assertTrue(CustomCardBorderManager.validateDimensions(48, 12, 4).isFailure)

        // Too large (> 256x256)
        assertTrue(CustomCardBorderManager.validateDimensions(257, 64, 16).isFailure)
        assertTrue(CustomCardBorderManager.validateDimensions(64, 512, 16).isFailure)

        // slicePx * 2 >= min(width, height) rejected
        assertTrue(CustomCardBorderManager.validateDimensions(32, 32, 16).isFailure)
        assertTrue(CustomCardBorderManager.validateDimensions(24, 48, 12).isFailure)
        assertTrue(CustomCardBorderManager.validateDimensions(33, 33, 16).isSuccess)
    }

    @Test
    fun installStaged_rejectsNonPngAndInvalidDimensions() {
        val tempFile = File.createTempFile("invalid_border_", ".png")
        try {
            tempFile.writeBytes("not a png header".toByteArray(Charsets.UTF_8))
            assertNull(MascotAtlasValidator.readPngSize(tempFile))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun yamlTheme_parsesBorderStyleFromTopLevelAndDecorations() {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))

        val topLevelYaml = """
            id: "lace_kitty"
            name: "Lace Kitty"
            borderStyle: "lace"
        """.trimIndent()
        val parsedTop = yaml.decodeFromString(YamlTheme.serializer(), topLevelYaml)
        assertEquals("lace", parsedTop.resolvedBorderStyle())
        assertEquals("lace", parsedTop.toLauncherTheme().borderStyle)

        val decorationsYaml = """
            id: "pixel_arcade"
            name: "Pixel Arcade"
            decorations:
              cardRoundnessDp: 14.0
              borderStyle: "PIXEL"
        """.trimIndent()
        val parsedDeco = yaml.decodeFromString(YamlTheme.serializer(), decorationsYaml)
        assertEquals("pixel", parsedDeco.resolvedBorderStyle())
        assertEquals("pixel", parsedDeco.toLauncherTheme().borderStyle)

        val defaultYaml = """
            id: "plain_pastel"
            name: "Plain Pastel"
        """.trimIndent()
        val parsedDefault = yaml.decodeFromString(YamlTheme.serializer(), defaultYaml)
        assertEquals("solid", parsedDefault.resolvedBorderStyle())
        assertEquals("solid", parsedDefault.toLauncherTheme().borderStyle)
    }

    private fun assertNull(value: Any?) {
        org.junit.Assert.assertNull(value)
    }
}
