package one.crinklecod.mochibun

import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.model.BundleCursorConfig
import one.crinklecod.mochibun.data.model.BundleGreetingConfig
import one.crinklecod.mochibun.data.model.BundleMascotConfig
import one.crinklecod.mochibun.data.model.BundleMascotDialogue
import one.crinklecod.mochibun.data.model.BundleMascotSlot
import one.crinklecod.mochibun.data.model.BundlePaletteConfig
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.model.MascotAtlasValidator
import one.crinklecod.mochibun.data.model.MochibunBundleManifest
import one.crinklecod.mochibun.data.model.bundleJsonFormat
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.CustomCardBorderManager
import one.crinklecod.mochibun.data.repository.CustomCardBorderSource
import one.crinklecod.mochibun.data.repository.CustomMascotManager
import one.crinklecod.mochibun.data.repository.ThemeBundleManager
import one.crinklecod.mochibun.data.repository.TypographyManager
import one.crinklecod.mochibun.pet.PetMood
import one.crinklecod.mochibun.ui.components.MascotAnimSlot
import one.crinklecod.mochibun.ui.components.MascotReactionKind
import one.crinklecod.mochibun.ui.components.MascotSlotLayout
import one.crinklecod.mochibun.ui.components.MascotSpriteSource
import one.crinklecod.mochibun.ui.components.MascotSpriteStore
import one.crinklecod.mochibun.ui.components.PET_CONTENT_LINES
import one.crinklecod.mochibun.ui.components.PET_HUNGRY_LINES
import one.crinklecod.mochibun.ui.components.PET_NO_TREATS_LINES
import one.crinklecod.mochibun.ui.components.PET_SLEEPY_LINES
import one.crinklecod.mochibun.ui.components.PET_SULKY_LINES
import one.crinklecod.mochibun.ui.components.petMoodLines
import one.crinklecod.mochibun.ui.components.selectReactionLine
import one.crinklecod.mochibun.ui.components.selectSpeechBubbleDialogue
import one.crinklecod.mochibun.ui.dialogs.exportDialogRows
import one.crinklecod.mochibun.ui.dialogs.previewFontBadgeText
import one.crinklecod.mochibun.ui.dialogs.previewMascotBadgeText
import one.crinklecod.mochibun.ui.dialogs.previewSoundPackBadgeText
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ThemeBundleManagerTest {

    private lateinit var originalFontValidator: (File) -> Boolean

    @Before
    fun resetMascot() {
        MascotSpriteStore.setSource(MascotSpriteSource.BuiltIn)
        // Plain JVM has no real BitmapFactory: read the PNG header instead
        CustomMascotManager.platformBounds = { MascotAtlasValidator.readPngSize(it) }
        CustomCardBorderManager.platformBounds = { MascotAtlasValidator.readPngSize(it) }
        originalFontValidator = TypographyManager.fontFileValidator
        TypographyManager.fontFileValidator = { true }
    }

    @After
    fun restoreTypographyValidator() {
        TypographyManager.fontFileValidator = originalFontValidator
    }

    // ── Custom mascot atlas (mascot_atlas.png) ──────────────────────────────

    private fun manifestBytes(manifest: MochibunBundleManifest = validManifest()) =
        bundleJsonFormat.encodeToString(manifest).toByteArray(Charsets.UTF_8)

    private val customSource get() = MascotSpriteStore.source.value as? MascotSpriteSource.Custom

    @Test
    fun bundleWithMascot_previewsAndInstallsIt() {
        val png = fakePng(192, 384)
        val bundle = createZip("manifest.json" to manifestBytes(), "mascot_atlas.png" to png)

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow()
        assertTrue(preview.hasMascotAsset)
        assertFalse(preview.hasCustomMascotDialogue)
        assertEquals("🐰 Custom mascot included", previewMascotBadgeText(preview))

        withTestContext { context ->
            var skipped: String? = null
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)) { skipped = it }
            assertTrue(applied.isSuccess)
            assertNull(skipped)
            val file = CustomMascotManager.atlasFile(context)
            assertArrayEquals(png, file.readBytes())
            val source = customSource!!
            assertEquals(file, source.file)
            assertEquals(48, source.layout.frameWidth) // inferred: 384 / 8
            assertEquals(MascotSlotLayout(6, 4), source.layout.slots[MascotAnimSlot.EAT])
            assertNull(CustomMascotManager.activeDialogue())
            assertTrue("Staging files cleaned", context.cacheDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun bundleWithMascot_usesTheManifestLayout() {
        val manifest = validManifest().copy(
            hasCustomMascot = true,
            mascot = BundleMascotConfig(
                frameWidth = 32, frameHeight = 48,
                slots = mapOf("idle" to BundleMascotSlot(0, 6), "sparkle" to BundleMascotSlot(9, 9))
            )
        )
        val bundle = createZip("manifest.json" to manifestBytes(manifest), "mascot_atlas.png" to fakePng(192, 384))
        withTestContext { context ->
            assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)).isSuccess)
            val layout = customSource!!.layout
            assertEquals(32, layout.frameWidth)
            assertEquals(mapOf(MascotAnimSlot.IDLE to MascotSlotLayout(0, 6)), layout.slots) // unknown key ignored
        }
    }

    @Test
    fun bundleWithoutMascot_leavesTheCurrentMascotAlone() {
        val bundle = createZip("manifest.json" to manifestBytes())
        assertFalse(ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow().hasMascotAsset)

        withTestContext { context ->
            assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)).isSuccess)
            assertEquals(MascotSpriteSource.BuiltIn, MascotSpriteStore.source.value)
            assertFalse(CustomMascotManager.atlasFile(context).exists())

            // An already-installed custom mascot also survives a bundle without one
            val withMascot = createZip("manifest.json" to manifestBytes(), "mascot_atlas.png" to fakePng(192, 384))
            ThemeBundleManager.applyBundle(context, ByteArrayInputStream(withMascot)).getOrThrow()
            val installed = customSource
            ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)).getOrThrow()
            assertEquals(installed, MascotSpriteStore.source.value)
        }
    }

    @Test
    fun oversizeMascot_isSkipped_butTheRestOfTheBundleApplies() {
        val tooBig = fakePng(192, 384, totalBytes = MascotAtlasValidator.MAX_ATLAS_BYTES.toInt() + 1)
        val manifest = validManifest().copy(
            hasCustomMascot = true,
            mascot = BundleMascotConfig(
                frameWidth = 48,
                frameHeight = 48,
                dialogue = BundleMascotDialogue(happy = listOf("should be skipped!"))
            )
        )
        val bundle = createZip("manifest.json" to manifestBytes(manifest), "mascot_atlas.png" to tooBig)

        assertFalse(ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow().hasMascotAsset)
        withTestContext { context ->
            var skipped: String? = null
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)) { skipped = it }
            assertTrue("Bundle still applies", applied.isSuccess)
            assertTrue(skipped!!.contains("1.5 MB"))
            assertEquals(MascotSpriteSource.BuiltIn, MascotSpriteStore.source.value)
            assertNull("Skipped mascot must not apply custom dialogue", CustomMascotManager.activeDialogue())
            assertFalse(CustomMascotManager.atlasFile(context).exists())
            assertTrue("Staging files cleaned", context.cacheDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun invalidMascotDimensions_areSkipped() {
        for (png in listOf(fakePng(200, 380), fakePng(16 * 17, 16 * 8), "not a png".toByteArray())) {
            val manifest = validManifest().copy(
                hasCustomMascot = true,
                mascot = BundleMascotConfig(
                    frameWidth = 48,
                    frameHeight = 48,
                    dialogue = BundleMascotDialogue(happy = listOf("invalid atlas line"))
                )
            )
            val bundle = createZip("manifest.json" to manifestBytes(manifest), "mascot_atlas.png" to png)
            withTestContext { context ->
                var skipped: String? = null
                assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)) { skipped = it }.isSuccess)
                assertTrue(skipped != null)
                assertEquals(MascotSpriteSource.BuiltIn, MascotSpriteStore.source.value)
                assertNull(CustomMascotManager.activeDialogue())
                assertFalse(CustomMascotManager.atlasFile(context).exists())
            }
        }
    }

    @Test
    fun mascotIsOnlyEverReadFromTheExactEntryName() {
        // A manifest can't point at a file: extra keys are ignored, and look-alike entries are never used
        val manifestJson = String(manifestBytes(), Charsets.UTF_8).trimEnd().removeSuffix("}") +
            ",\"mascotFile\": \"../evil.png\", \"atlasPath\": \"/data/evil.png\"}"
        val bundle = createZip(
            "manifest.json" to manifestJson.toByteArray(Charsets.UTF_8),
            "../evil.png" to fakePng(192, 384),
            "mascot/mascot_atlas.png" to fakePng(192, 384),
            "MASCOT_ATLAS.PNG" to fakePng(192, 384)
        )
        assertFalse(ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow().hasMascotAsset)
        withTestContext { context ->
            assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)).isSuccess)
            assertEquals(MascotSpriteSource.BuiltIn, MascotSpriteStore.source.value)
            assertFalse(CustomMascotManager.atlasFile(context).exists())
            assertFalse(java.io.File(context.filesDir.parentFile, "evil.png").exists())
            assertTrue(context.filesDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun customMascot_roundTripsThroughExportAndImport() {
        val png = fakePng(192, 384)
        var exported = ByteArray(0)
        withTestContext { source ->
            val staged = java.io.File(source.cacheDir, "pick.png").apply { writeBytes(png) }
            CustomMascotManager.installStaged(source, staged, config = null).getOrThrow()
            val out = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(source, out, bundleName = "Mascot Trip").getOrThrow()
            exported = out.toByteArray()

            val noMascot = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(source, noMascot, bundleName = "No Mascot", includeCustomMascot = false).getOrThrow()
            val plain = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(noMascot.toByteArray())).getOrThrow()
            assertFalse(plain.hasMascotAsset)
            assertFalse(plain.manifest.hasCustomMascot)
            assertNull(plain.manifest.mascot)
        }
        val installedLayout = customSource!!.layout

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(exported)).getOrThrow()
        assertTrue(preview.hasMascotAsset)
        assertTrue(preview.manifest.hasCustomMascot)
        assertEquals(48, preview.manifest.mascot!!.frameWidth)
        assertNull(preview.manifest.mascot!!.dialogue)

        MascotSpriteStore.setSource(MascotSpriteSource.BuiltIn)
        withTestContext { target ->
            ThemeBundleManager.applyBundle(target, ByteArrayInputStream(exported)).getOrThrow()
            assertArrayEquals(png, CustomMascotManager.atlasFile(target).readBytes())
            assertEquals(installedLayout, customSource!!.layout)
            assertNull(CustomMascotManager.activeDialogue())
        }
    }

    @Test
    fun customMascotDialogue_partialPoolsFallBack_roundTripsAndClearsOnReset() {
        val png = fakePng(192, 384)
        val rawDialogue = BundleMascotDialogue(
            happy = listOf("  kero kero! 🐸  ", "lily pad bliss ♡", "\n\t "),
            fed = listOf("chomp! 🪰", "yummy bug~")
        )
        val expectedDialogue = BundleMascotDialogue(
            happy = listOf("kero kero! 🐸", "lily pad bliss ♡"),
            fed = listOf("chomp! 🪰", "yummy bug~")
        )
        val initialManifest = validManifest().copy(
            hasCustomMascot = true,
            mascot = BundleMascotConfig(
                name = "Kerobun",
                frameWidth = 48,
                frameHeight = 48,
                dialogue = rawDialogue
            )
        )
        val initialBundle = createZip(
            "manifest.json" to manifestBytes(initialManifest),
            "mascot_atlas.png" to png
        )

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(initialBundle)).getOrThrow()
        assertTrue(preview.hasMascotAsset)
        assertTrue(preview.hasCustomMascotDialogue)
        assertEquals("🐰 Custom mascot + dialogue included", previewMascotBadgeText(preview))

        var reExported = ByteArray(0)
        withTestContext { source ->
            ThemeBundleManager.applyBundle(source, ByteArrayInputStream(initialBundle)).getOrThrow()
            assertEquals(expectedDialogue, CustomMascotManager.activeDialogue())

            // Partial dialogue fallback: happy & fed use custom lines; other moods use built-in pools
            var prevHappy: String? = null
            repeat(15) {
                val line = selectSpeechBubbleDialogue(
                    previousLine = prevHappy,
                    hour = 12,
                    isLowBattery = false,
                    isMusicPlaying = false,
                    petMood = PetMood.HAPPY
                )
                assertTrue(line in expectedDialogue.happy)
                assertNotEquals(prevHappy, line)
                prevHappy = line
            }
            var prevFed: String? = null
            repeat(15) {
                val line = selectReactionLine(MascotReactionKind.FED, prevFed) { "" }
                assertTrue(line in expectedDialogue.fed)
                assertNotEquals(prevFed, line)
                prevFed = line
            }
            assertEquals(PET_CONTENT_LINES, petMoodLines(PetMood.CONTENT))
            assertEquals(PET_HUNGRY_LINES, petMoodLines(PetMood.HUNGRY))
            assertEquals(PET_SULKY_LINES, petMoodLines(PetMood.SULKY))
            assertEquals(PET_SLEEPY_LINES, petMoodLines(PetMood.SLEEPY))
            assertTrue(selectReactionLine(MascotReactionKind.NO_TREATS, null) { "" } in PET_NO_TREATS_LINES)

            val out = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(source, out, bundleName = "Kerobun Pack").getOrThrow()
            reExported = out.toByteArray()

            // Resetting to default Mochibunny clears custom dialogue and restores default pools
            CustomMascotManager.reset(source)
            assertNull(CustomMascotManager.activeDialogue())
            assertNull(CustomMascotManager.activeConfig(source))
            val defaultHappy = selectSpeechBubbleDialogue(
                hour = 12,
                isLowBattery = false,
                isMusicPlaying = false,
                petMood = PetMood.HAPPY
            )
            assertFalse(defaultHappy in expectedDialogue.happy)
        }

        // Round-trip import into a fresh target context preserves sanitized dialogue and startup restore
        withTestContext { target ->
            ThemeBundleManager.applyBundle(target, ByteArrayInputStream(reExported)).getOrThrow()
            assertEquals(expectedDialogue, CustomMascotManager.activeDialogue())

            MascotSpriteStore.setSource(MascotSpriteSource.BuiltIn)
            assertNull(CustomMascotManager.activeDialogue())
            CustomMascotManager.restore(target)
            assertEquals(expectedDialogue, CustomMascotManager.activeDialogue())

            // Importing a standalone PNG without dialogue clears previous custom dialogue
            CustomMascotManager.importPng(target, ByteArrayInputStream(png)).getOrThrow()
            assertNull(CustomMascotManager.activeDialogue())
        }
    }

    // ── Custom card border (card_border.png + procedural borderStyle) ─────────

    @Test
    fun bundleWithCustomCardBorder_previewsInstallsAndRoundTrips() {
        val borderPng = fakePng(48, 48)
        val manifest = validManifest().copy(
            palette = validManifest().palette.copy(
                borderStyle = "lace",
                hasCustomCardBorder = true,
                cardBorderSlicePx = 14
            )
        )
        val bundle = createZip(
            "manifest.json" to manifestBytes(manifest),
            "card_border.png" to borderPng
        )

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow()
        assertTrue(preview.hasCardBorderAsset)
        assertEquals("lace", preview.manifest.palette.borderStyle)
        assertTrue(preview.manifest.palette.hasCustomCardBorder)
        assertEquals(14, preview.manifest.palette.cardBorderSlicePx)

        var exportedWithBorder = ByteArray(0)
        var exportedWithoutBorder = ByteArray(0)
        withTestContext { source ->
            var skipped: String? = null
            val applied = ThemeBundleManager.applyBundle(source, ByteArrayInputStream(bundle)) { skipped = it }
            assertTrue(applied.isSuccess)
            assertNull(skipped)
            assertTrue(CustomCardBorderManager.isCustomBorderActive())
            assertEquals(CardBorderStyle.LACE, CustomCardBorderManager.currentBorderStyle(source))
            assertArrayEquals(borderPng, CustomCardBorderManager.borderFile(source).readBytes())
            val customSource = CustomCardBorderManager.source.value as CustomCardBorderSource.Custom
            assertEquals(48, customSource.spec.width)
            assertEquals(48, customSource.spec.height)
            assertEquals(14, customSource.spec.slicePx)

            val outWith = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(
                source,
                outWith,
                bundleName = "Lace & Frame",
                includeCustomCardBorder = true
            ).getOrThrow()
            exportedWithBorder = outWith.toByteArray()

            val outWithout = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(
                source,
                outWithout,
                bundleName = "Lace Only",
                includeCustomCardBorder = false
            ).getOrThrow()
            exportedWithoutBorder = outWithout.toByteArray()
        }

        // Verify export with custom card border included
        val previewWith = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(exportedWithBorder)).getOrThrow()
        assertTrue(previewWith.hasCardBorderAsset)
        assertTrue(previewWith.manifest.palette.hasCustomCardBorder)
        assertEquals(14, previewWith.manifest.palette.cardBorderSlicePx)
        assertEquals("lace", previewWith.manifest.palette.borderStyle)

        // Verify export with custom card border excluded
        val previewWithout = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(exportedWithoutBorder)).getOrThrow()
        assertFalse(previewWithout.hasCardBorderAsset)
        assertFalse(previewWithout.manifest.palette.hasCustomCardBorder)
        assertEquals("lace", previewWithout.manifest.palette.borderStyle)

        // Applying exportedWithoutBorder to a context that had a custom border resets the custom border PNG
        withTestContext { target ->
            ThemeBundleManager.applyBundle(target, ByteArrayInputStream(exportedWithBorder)).getOrThrow()
            assertTrue(CustomCardBorderManager.isCustomBorderActive())
            assertTrue(CustomCardBorderManager.borderFile(target).exists())

            ThemeBundleManager.applyBundle(target, ByteArrayInputStream(exportedWithoutBorder)).getOrThrow()
            assertFalse("Bundle without custom border must clear previous custom border", CustomCardBorderManager.isCustomBorderActive())
            assertFalse(CustomCardBorderManager.borderFile(target).exists())
            assertEquals(CardBorderStyle.LACE, CustomCardBorderManager.currentBorderStyle(target))
        }
    }

    @Test
    fun oversizedOrInvalidCardBorder_isSkipped_andResetsExistingCustomBorder() {
        val validBorderPng = fakePng(48, 48)
        val tooBigBorderPng = fakePng(48, 48, totalBytes = ThemeBundleManager.MAX_CARD_BORDER_BYTES.toInt() + 1)
        val manifest = validManifest().copy(
            palette = validManifest().palette.copy(
                borderStyle = "stitch",
                hasCustomCardBorder = true,
                cardBorderSlicePx = 16
            )
        )
        val bundle = createZip(
            "manifest.json" to manifestBytes(manifest),
            "card_border.png" to tooBigBorderPng
        )

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow()
        assertFalse("Oversized card_border.png must not be marked valid in preview", preview.hasCardBorderAsset)

        withTestContext { context ->
            // Pre-install a valid custom border to verify it is cleaned up when a bundle's border is skipped
            CustomCardBorderManager.installFromBytes(context, validBorderPng, 16).getOrThrow()
            assertTrue(CustomCardBorderManager.isCustomBorderActive())

            var skipped: String? = null
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)) { skipped = it }
            assertTrue("Bundle still applies when card_border.png is oversized", applied.isSuccess)
            assertTrue(skipped!!.contains("512 KB"))
            assertFalse(CustomCardBorderManager.isCustomBorderActive())
            assertFalse(CustomCardBorderManager.borderFile(context).exists())
            assertEquals(CardBorderStyle.STITCH, CustomCardBorderManager.currentBorderStyle(context))
        }
    }

    // ── Bundled fonts (font.ttf / font.otf) & sound packs (sounds/*.ogg) ───

    @Test
    fun manifestFontAndSoundFields_defaultForLegacyAndRoundTrip() {
        val legacyJson = """
            {
              "bundleVersion": 1,
              "bundleName": "Legacy Pack",
              "author": "Mochi User",
              "createdAtEpochSec": 1729600000,
              "greeting": {"userName": "Mochi User", "showSparkles": true},
              "palette": {
                "activePresetId": "sakura_dream",
                "isCustom": false,
                "bgColorHex": "#FFF5F8",
                "cardColorHex": "#FCE4EC",
                "greetingColorHex": "#A04064",
                "actionColorHex": "#A04064",
                "cardRoundnessDp": 20.0
              },
              "cursor": {
                "baseVelocity": 10.0,
                "accelerationMulti": 1.05,
                "pointerSizeDp": 28.0,
                "clickMethod": "ACCESSIBILITY_GESTURE",
                "hotspot": "CENTER"
              },
              "hasWallpaper": false,
              "hasCustomCursor": false
            }
        """.trimIndent()
        val legacy = bundleJsonFormat.decodeFromString<MochibunBundleManifest>(legacyJson)
        assertFalse(legacy.hasFont)
        assertNull(legacy.fontDisplayName)
        assertFalse(legacy.hasSoundPack)
        assertNull(legacy.soundPackName)

        val modern = validManifest().copy(
            hasFont = true,
            fontDisplayName = "PixelMplus10",
            hasSoundPack = true,
            soundPackName = "Crystal Bell"
        )
        val decoded = bundleJsonFormat.decodeFromString<MochibunBundleManifest>(
            bundleJsonFormat.encodeToString(modern)
        )
        assertTrue(decoded.hasFont)
        assertEquals("PixelMplus10", decoded.fontDisplayName)
        assertTrue(decoded.hasSoundPack)
        assertEquals("Crystal Bell", decoded.soundPackName)
    }

    @Test
    fun bundledFontAndSoundPack_previewInstallAndRoundTripThroughExport() {
        val ttfBytes = fakeTtf(128)
        val navOgg = fakeOgg(96)
        val clickOgg = fakeOgg(112)
        val manifest = validManifest().copy(
            bundleName = "Sakura Studio",
            hasFont = true,
            fontDisplayName = "PixelMplus10",
            hasSoundPack = true,
            soundPackName = "Sakura Chimes"
        )
        val bundle = createZip(
            "manifest.json" to manifestBytes(manifest),
            "font.ttf" to ttfBytes,
            "sounds/nav.ogg" to navOgg,
            "sounds/click.ogg" to clickOgg
        )

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow()
        assertTrue(preview.hasFontAsset)
        assertTrue(preview.hasSoundPackAsset)
        assertEquals(2, preview.soundCueCount)
        assertEquals("🔤 Custom Font: PixelMplus10", previewFontBadgeText(preview))
        assertEquals("🎵 Custom Sound Pack: Sakura Chimes (2/8 sounds)", previewSoundPackBadgeText(preview))
        assertEquals(
            listOf("name", "wallpaper", "cursor", "mascot", "card_border", "font", "sounds", "display_name", "cancel", "create"),
            exportDialogRows(hasCustomFont = true, hasCustomSoundPack = true)
        )
        assertEquals(
            listOf("name", "wallpaper", "cursor", "mascot", "card_border", "display_name", "cancel", "create"),
            exportDialogRows(hasCustomFont = false, hasCustomSoundPack = false)
        )

        var exportedWithAssets = ByteArray(0)
        var exportedWithoutAssets = ByteArray(0)
        withTestContext { source ->
            var skipped: String? = null
            val applied = ThemeBundleManager.applyBundle(source, ByteArrayInputStream(bundle)) { skipped = it }
            assertTrue(applied.isSuccess)
            assertNull(skipped)

            assertArrayEquals(ttfBytes, TypographyManager.bundleFontFile(source).readBytes())
            assertEquals("PixelMplus10", TypographyManager.bundleFontDisplayName(source))
            assertEquals(TypographyManager.BUNDLE_FONT_ID, PreferencesManager.getThemeFontFamily(source))

            val installedSounds = SoundEffectManager.bundleSoundFiles(source)
            assertEquals(setOf(SoundEffect.NAV, SoundEffect.CLICK), installedSounds.keys)
            assertArrayEquals(navOgg, installedSounds[SoundEffect.NAV]!!.readBytes())
            assertArrayEquals(clickOgg, installedSounds[SoundEffect.CLICK]!!.readBytes())
            assertEquals("Sakura Chimes", SoundEffectManager.bundleSoundPackName.value)
            assertEquals(SoundEffectManager.BUNDLE_THEME_ID, PreferencesManager.getKeypadSoundMode(source))
            assertTrue("Staging files cleaned", source.cacheDir.listFiles().isNullOrEmpty())

            val outWith = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(
                source,
                outWith,
                bundleName = "Sakura Re-export",
                includeFont = true,
                includeSoundPack = true
            ).getOrThrow()
            exportedWithAssets = outWith.toByteArray()

            val outWithout = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(
                source,
                outWithout,
                bundleName = "Sakura Plain",
                includeFont = false,
                includeSoundPack = false
            ).getOrThrow()
            exportedWithoutAssets = outWithout.toByteArray()

            // Reset helpers clear bundle font & sounds and revert font choice to default
            TypographyManager.resetBundleFont(source)
            assertFalse(TypographyManager.bundleFontFile(source).exists())
            assertEquals("default", PreferencesManager.getThemeFontFamily(source))

            SoundEffectManager.resetBundleSounds(source)
            assertTrue(SoundEffectManager.bundleSoundFiles(source).isEmpty())
            assertNull(SoundEffectManager.bundleSoundPackName.value)
        }

        val entriesWith = zipEntryNames(exportedWithAssets)
        assertTrue(ThemeBundleManager.FONT_ENTRY in entriesWith)
        assertTrue("sounds/nav.ogg" in entriesWith)
        assertTrue("sounds/click.ogg" in entriesWith)

        val entriesWithout = zipEntryNames(exportedWithoutAssets)
        assertFalse(ThemeBundleManager.FONT_ENTRY in entriesWithout)
        assertFalse(entriesWithout.any { it.startsWith(ThemeBundleManager.SOUNDS_ENTRY_PREFIX) })

        val previewWithout = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(exportedWithoutAssets)).getOrThrow()
        assertFalse(previewWithout.hasFontAsset)
        assertFalse(previewWithout.hasSoundPackAsset)
        assertFalse(previewWithout.manifest.hasFont)
        assertFalse(previewWithout.manifest.hasSoundPack)
        assertNull(previewWithout.manifest.fontDisplayName)
        assertNull(previewWithout.manifest.soundPackName)

        withTestContext { target ->
            ThemeBundleManager.applyBundle(target, ByteArrayInputStream(exportedWithAssets)).getOrThrow()
            assertArrayEquals(ttfBytes, TypographyManager.bundleFontFile(target).readBytes())
            assertEquals("PixelMplus10", TypographyManager.bundleFontDisplayName(target))
            assertEquals(TypographyManager.BUNDLE_FONT_ID, PreferencesManager.getThemeFontFamily(target))

            val targetSounds = SoundEffectManager.bundleSoundFiles(target)
            assertEquals(setOf(SoundEffect.NAV, SoundEffect.CLICK), targetSounds.keys)
            assertArrayEquals(navOgg, targetSounds[SoundEffect.NAV]!!.readBytes())
            assertArrayEquals(clickOgg, targetSounds[SoundEffect.CLICK]!!.readBytes())
            assertEquals("Sakura Chimes", SoundEffectManager.bundleSoundPackName.value)
            assertEquals(SoundEffectManager.BUNDLE_THEME_ID, PreferencesManager.getKeypadSoundMode(target))
        }
    }

    @Test
    fun otfFontImportsAndReExportsAsFontTtf_andMutedSoundsStayMuted() {
        val otfBytes = fakeOtf(128)
        val snapOgg = fakeOgg(80)
        val manifest = validManifest().copy(bundleName = "OTF & Muted")
        val bundle = createZip(
            "manifest.json" to manifestBytes(manifest),
            "font.otf" to otfBytes,
            "sounds/snap.ogg" to snapOgg
        )

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow()
        assertTrue(preview.hasFontAsset)
        assertEquals(1, preview.soundCueCount)
        assertEquals("🔤 Custom Font", previewFontBadgeText(preview))
        assertEquals("🎵 Custom Sound Pack (1/8 sound)", previewSoundPackBadgeText(preview))

        withTestContext { context ->
            PreferencesManager.setKeypadSoundMode(context, "off")
            ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)).getOrThrow()

            assertArrayEquals(otfBytes, TypographyManager.bundleFontFile(context).readBytes())
            assertEquals(TypographyManager.BUNDLE_FONT_ID, PreferencesManager.getThemeFontFamily(context))
            // Sound pack installs cleanly without unmuting a muted keypad profile
            assertEquals(setOf(SoundEffect.SNAP), SoundEffectManager.bundleSoundFiles(context).keys)
            assertEquals("OTF & Muted", SoundEffectManager.bundleSoundPackName.value)
            assertEquals("off", PreferencesManager.getKeypadSoundMode(context))

            val out = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(
                context,
                out,
                bundleName = "OTF Re-export",
                includeFont = true,
                includeSoundPack = true
            ).getOrThrow()
            val exportedEntries = zipEntryNames(out.toByteArray())
            assertTrue(ThemeBundleManager.FONT_ENTRY in exportedEntries)
            assertFalse(ThemeBundleManager.FONT_OTF_ENTRY in exportedEntries)
            assertTrue("sounds/snap.ogg" in exportedEntries)
        }
    }

    @Test
    fun fontAndSoundGuardrails_rejectOversizedInvalidHeadersAndZipTraversal() {
        val oversizedFont = fakeTtf(ThemeBundleManager.MAX_FONT_BYTES.toInt() + 1)
        val oversizedOgg = fakeOgg(ThemeBundleManager.MAX_SOUND_BYTES.toInt() + 1)
        val validOgg = fakeOgg(96)

        // 1. Oversized font + oversized sound cue + valid sound cue: bad assets skipped, rest applies
        val mixedBundle = createZip(
            "manifest.json" to manifestBytes(),
            "font.ttf" to oversizedFont,
            "sounds/nav.ogg" to oversizedOgg,
            "sounds/click.ogg" to validOgg
        )
        val mixedPreview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(mixedBundle)).getOrThrow()
        assertFalse("Oversized font must not be marked valid in preview", mixedPreview.hasFontAsset)
        assertEquals("Only the within-limit sound cue should count", 1, mixedPreview.soundCueCount)

        withTestContext { context ->
            val warnings = mutableListOf<String>()
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(mixedBundle)) { warnings.add(it) }
            assertTrue("Bundle still applies when oversized font/sound are skipped", applied.isSuccess)
            assertTrue(warnings.any { it.contains("4 MB") })
            assertTrue(warnings.any { it.contains("256 KB") })
            assertFalse(TypographyManager.bundleFontFile(context).exists())
            assertNotEquals(TypographyManager.BUNDLE_FONT_ID, PreferencesManager.getThemeFontFamily(context))
            assertEquals(setOf(SoundEffect.CLICK), SoundEffectManager.bundleSoundFiles(context).keys)
        }

        // 2. Invalid font header (ttcf collection) and invalid sound header (non-OggS)
        val badHeaderBundle = createZip(
            "manifest.json" to manifestBytes(),
            "font.ttf" to ("ttcf" + "0".repeat(60)).toByteArray(Charsets.ISO_8859_1),
            "sounds/nav.ogg" to ("RIFF" + "0".repeat(60)).toByteArray(Charsets.ISO_8859_1)
        )
        val badPreview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(badHeaderBundle)).getOrThrow()
        assertFalse(badPreview.hasFontAsset)
        assertEquals(0, badPreview.soundCueCount)

        withTestContext { context ->
            val warnings = mutableListOf<String>()
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(badHeaderBundle)) { warnings.add(it) }
            assertTrue(applied.isSuccess)
            assertTrue(warnings.any { it.contains("TrueType/OpenType") })
            assertTrue(warnings.any { it.contains("Custom sounds skipped") })
            assertFalse(TypographyManager.bundleFontFile(context).exists())
            assertTrue(SoundEffectManager.bundleSoundFiles(context).isEmpty())
        }

        // 3. Platform Typeface rejection (e.g., corrupted SFNT tables despite valid header)
        TypographyManager.fontFileValidator = { false }
        val unloadableFontBundle = createZip(
            "manifest.json" to manifestBytes(),
            "font.ttf" to fakeTtf(128)
        )
        withTestContext { context ->
            val warnings = mutableListOf<String>()
            assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(unloadableFontBundle)) { warnings.add(it) }.isSuccess)
            assertTrue(warnings.any { it.contains("couldn't load this font") })
            assertFalse(TypographyManager.bundleFontFile(context).exists())
            assertNotEquals(TypographyManager.BUNDLE_FONT_ID, PreferencesManager.getThemeFontFamily(context))
        }
        TypographyManager.fontFileValidator = { true }

        // 4. ZIP path traversal & disallowed entry paths are ignored safely
        val traversalNames = listOf(
            "sounds/../evil.ogg",
            "sounds/../nav.ogg",
            "/sounds/nav.ogg",
            "sounds\\nav.ogg",
            "sounds/sub/nav.ogg",
            "sounds/unknown.ogg",
            "sounds/nav.wav",
            "../font.ttf",
            "fonts/font.ttf"
        )
        for (badName in traversalNames) {
            assertNull("Entry '$badName' must be rejected", ThemeBundleManager.soundEffectForEntry(badName))
        }
        val traversalBundle = createZip(
            "manifest.json" to manifestBytes(),
            "sounds/../evil.ogg" to validOgg,
            "/sounds/nav.ogg" to validOgg,
            "sounds\\click.ogg" to validOgg,
            "sounds/sub/select.ogg" to validOgg,
            "sounds/unknown.ogg" to validOgg,
            "../font.ttf" to fakeTtf(64),
            "fonts/font.ttf" to fakeTtf(64)
        )
        val traversalPreview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(traversalBundle)).getOrThrow()
        assertFalse(traversalPreview.hasFontAsset)
        assertEquals(0, traversalPreview.soundCueCount)
        withTestContext { context ->
            assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(traversalBundle)).isSuccess)
            assertFalse(File(context.filesDir.parentFile, "evil.ogg").exists())
            assertFalse(File(context.filesDir.parentFile, "font.ttf").exists())
            assertTrue(context.filesDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun soundEffectManager_cueMappingAndBundleFallbackBehavior() {
        // Exact cue mapping & case insensitivity (all 8 cues including feed & pet)
        assertEquals(8, SoundEffectManager.BundleCueFileNames.size)
        assertTrue("feed.ogg" in SoundEffectManager.BundleCueFileNames)
        assertTrue("pet.ogg" in SoundEffectManager.BundleCueFileNames)
        for (effect in SoundEffect.entries) {
            val fileName = SoundEffectManager.bundleCueFileName(effect)
            assertEquals("${effect.name.lowercase()}.ogg", fileName)
            assertEquals(effect, SoundEffectManager.effectForBundleCueFileName(fileName))
            assertEquals(effect, SoundEffectManager.effectForBundleCueFileName(fileName.uppercase()))
            assertEquals(effect, ThemeBundleManager.soundEffectForEntry("sounds/$fileName"))
        }
        assertNull(SoundEffectManager.effectForBundleCueFileName("unknown.ogg"))
        assertNull(SoundEffectManager.effectForBundleCueFileName("nav.wav"))
        assertNull(SoundEffectManager.effectForBundleCueFileName(""))

        // Partial bundle directory: valid .ogg cues resolve to files, missing/corrupt cues resolve to null (fallback = "kawaii")
        assertEquals("kawaii", SoundEffectManager.BUNDLE_FALLBACK_THEME)
        val tempRoot = Files.createTempDirectory("mochibun-cue-resolve").toFile()
        try {
            File(tempRoot, "nav.ogg").writeBytes(fakeOgg(64))
            File(tempRoot, "feed.ogg").writeBytes(fakeOgg(72))
            File(tempRoot, "click.ogg").writeBytes("NOT_OGG".toByteArray())
            File(tempRoot, "select.ogg").writeBytes(fakeOgg(SoundEffectManager.MAX_BUNDLE_SOUND_BYTES.toInt() + 1))

            val resolved = SoundEffectManager.resolveBundleCueSources(tempRoot)
            assertEquals(File(tempRoot, "nav.ogg"), resolved[SoundEffect.NAV])
            assertEquals(File(tempRoot, "feed.ogg"), resolved[SoundEffect.FEED])
            assertNull("Non-OggS cue must fall back to built-in", resolved[SoundEffect.CLICK])
            assertNull("Oversized cue must fall back to built-in", resolved[SoundEffect.SELECT])
            assertNull("Missing cue must fall back to built-in", resolved[SoundEffect.BACK])
            assertNull("Missing cue must fall back to built-in", resolved[SoundEffect.BOUNDARY])
            assertNull("Missing cue must fall back to built-in", resolved[SoundEffect.SNAP])
            assertNull("Missing cue must fall back to built-in", resolved[SoundEffect.PET])
        } finally {
            tempRoot.deleteRecursively()
        }

        // Theme cycling & naming with/without bundle pack
        assertEquals("off", SoundEffectManager.nextThemeId("water", +1, includeBundle = false))
        assertEquals(SoundEffectManager.BUNDLE_THEME_ID, SoundEffectManager.nextThemeId("water", +1, includeBundle = true))
        assertEquals("off", SoundEffectManager.nextThemeId(SoundEffectManager.BUNDLE_THEME_ID, +1, includeBundle = true))
        assertEquals("water", SoundEffectManager.nextThemeId(SoundEffectManager.BUNDLE_THEME_ID, -1, includeBundle = true))
        assertEquals("kawaii", SoundEffectManager.nextThemeId("unknown_theme", +1, includeBundle = false))

        assertEquals("Bell Chime", SoundEffectManager.themeName(SoundEffectManager.BUNDLE_THEME_ID, "Bell Chime"))
        assertEquals("Custom Bundle", SoundEffectManager.themeName(SoundEffectManager.BUNDLE_THEME_ID, null))
        assertEquals("Custom Bundle", SoundEffectManager.themeName(SoundEffectManager.BUNDLE_THEME_ID, "   "))
    }

    @Test
    fun feedAndPetSoundCues_previewInstallAndRoundTrip() {
        val cuePayloads = SoundEffect.entries.associateWith { effect ->
            fakeOgg(72 + effect.ordinal * 8)
        }
        val manifest = validManifest().copy(
            bundleName = "Pet Snack Audio",
            hasSoundPack = true,
            soundPackName = "Bunny Chirps"
        )
        val zipEntries = buildList {
            add("manifest.json" to manifestBytes(manifest))
            cuePayloads.forEach { (effect, bytes) ->
                add("sounds/${SoundEffectManager.bundleCueFileName(effect)}" to bytes)
            }
        }.toTypedArray()
        val bundle = createZip(*zipEntries)

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow()
        assertTrue(preview.hasSoundPackAsset)
        assertEquals(8, preview.soundCueCount)
        assertEquals("🎵 Custom Sound Pack: Bunny Chirps (8/8 sounds)", previewSoundPackBadgeText(preview))

        withTestContext { source ->
            ThemeBundleManager.applyBundle(source, ByteArrayInputStream(bundle)).getOrThrow()
            val installed = SoundEffectManager.bundleSoundFiles(source)
            assertEquals(SoundEffect.entries.toSet(), installed.keys)
            assertArrayEquals(cuePayloads[SoundEffect.FEED]!!, installed[SoundEffect.FEED]!!.readBytes())
            assertArrayEquals(cuePayloads[SoundEffect.PET]!!, installed[SoundEffect.PET]!!.readBytes())
            assertEquals("Bunny Chirps", SoundEffectManager.bundleSoundPackName.value)

            val out = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(
                source,
                out,
                bundleName = "Pet Snack Re-export",
                includeSoundPack = true
            ).getOrThrow()
            val reExportedEntries = zipEntryNames(out.toByteArray())
            assertTrue("sounds/feed.ogg" in reExportedEntries)
            assertTrue("sounds/pet.ogg" in reExportedEntries)
            assertEquals(8, reExportedEntries.count { it.startsWith("sounds/") })
        }
    }

    @Test
    fun testManifestSerializationRoundtrip() {
        val original = MochibunBundleManifest(
            bundleVersion = 1,
            bundleName = "Pastel Sakura",
            author = "Emii",
            createdAtEpochSec = 1729600000L,
            greeting = BundleGreetingConfig(
                userName = "Mochibun",
                showSparkles = true
            ),
            palette = BundlePaletteConfig(
                activePresetId = "sakura_dream",
                isCustom = true,
                bgColorHex = "#FFF5F8",
                cardColorHex = "#FCE4EC",
                greetingColorHex = "#A04064",
                actionColorHex = "#A04064",
                cardRoundnessDp = 24.0f
            ),
            cursor = BundleCursorConfig(
                baseVelocity = 12.0f,
                accelerationMulti = 1.08f,
                pointerSizeDp = 32.0f,
                clickMethod = "ACCESSIBILITY_GESTURE",
                hotspot = "TOP_LEFT"
            ),
            hasWallpaper = true,
            hasCustomCursor = false
        )

        val json = bundleJsonFormat.encodeToString(original)
        assertTrue("JSON must contain manifest fields", json.contains("Pastel Sakura"))
        assertTrue("JSON must contain palette preset", json.contains("sakura_dream"))

        val decoded = bundleJsonFormat.decodeFromString<MochibunBundleManifest>(json)
        assertEquals(original.bundleName, decoded.bundleName)
        assertEquals(original.author, decoded.author)
        assertEquals(original.createdAtEpochSec, decoded.createdAtEpochSec)
        assertEquals(original.greeting.userName, decoded.greeting.userName)
        assertEquals(original.palette.bgColorHex, decoded.palette.bgColorHex)
        assertEquals(original.cursor.baseVelocity, decoded.cursor.baseVelocity, 0.001f)
        assertTrue(decoded.hasWallpaper)
        assertFalse(decoded.hasCustomCursor)
    }

    @Test
    fun testStreamZipUnpackValidation() {
        val manifest = MochibunBundleManifest(
            bundleVersion = 1,
            bundleName = "Matcha Latte",
            author = "Barista",
            createdAtEpochSec = 1729600000L,
            greeting = BundleGreetingConfig(userName = "Matcha", showSparkles = true),
            palette = BundlePaletteConfig(
                activePresetId = "minty_matcha",
                isCustom = false,
                bgColorHex = "#F1F8F1",
                cardColorHex = "#E8F5E9",
                greetingColorHex = "#2E7D32",
                actionColorHex = "#2E7D32",
                cardRoundnessDp = 20.0f
            ),
            cursor = BundleCursorConfig(
                baseVelocity = 10.0f,
                accelerationMulti = 1.05f,
                pointerSizeDp = 28.0f,
                clickMethod = "ACCESSIBILITY_GESTURE",
                hotspot = "CENTER"
            ),
            hasWallpaper = false,
            hasCustomCursor = true
        )

        val zipBytesOut = ByteArrayOutputStream()
        ZipOutputStream(zipBytesOut).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(bundleJsonFormat.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("cursor.gif"))
            zipOut.write(byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) // GIF89a header
            zipOut.closeEntry()

            zipOut.finish()
        }

        val result = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(zipBytesOut.toByteArray()))
        assertTrue("Bundle inspection should succeed", result.isSuccess)

        val preview = result.getOrThrow()
        assertEquals("Matcha Latte", preview.manifest.bundleName)
        assertEquals("Barista", preview.manifest.author)
        assertTrue("Cursor asset should be detected", preview.hasCursorAsset)
    }

    @Test
    fun oversizedManifestIsRejectedByPreviewAndApply() {
        val oversizedManifest = ByteArray(ThemeBundleManager.MAX_MANIFEST_BYTES.toInt() + 1)
        val bundle = createZip("manifest.json" to oversizedManifest)

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle))
        assertTrue("Oversized manifest must fail preview", preview.isFailure)
        assertEquals(
            "Theme bundle entry exceeds maximum allowed size",
            preview.exceptionOrNull()?.message
        )

        withTestContext { context ->
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle))
            assertTrue("Oversized manifest must fail apply", applied.isFailure)
            assertEquals(
                "Theme bundle entry exceeds maximum allowed size",
                applied.exceptionOrNull()?.message
            )
            assertTrue("Failed import must not leave files", context.filesDir.listFiles().isNullOrEmpty())
            assertTrue("Failed import must clean staging files", context.cacheDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun oversizedWallpaperOrCursorIsRejectedWithoutLeavingFiles() {
        val manifest = bundleJsonFormat.encodeToString(validManifest()).toByteArray(Charsets.UTF_8)
        val oversizedAssets = listOf(
            "wallpaper.png" to ByteArray(ThemeBundleManager.MAX_WALLPAPER_BYTES.toInt() + 1),
            "cursor.png" to ByteArray(ThemeBundleManager.MAX_CURSOR_BYTES.toInt() + 1)
        )

        oversizedAssets.forEach { (entryName, assetBytes) ->
            val bundle = createZip("manifest.json" to manifest, entryName to assetBytes)
            withTestContext { context ->
                val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle))
                assertTrue("$entryName must be rejected", applied.isFailure)
                assertEquals(
                    "Theme bundle entry exceeds maximum allowed size",
                    applied.exceptionOrNull()?.message
                )
                assertTrue("$entryName must not leave partial files", context.filesDir.listFiles().isNullOrEmpty())
                assertTrue("$entryName must clean staging files", context.cacheDir.listFiles().isNullOrEmpty())
            }
        }
    }

    @Test
    fun bundleExceedingAggregateLimitIsRejected() {
        val manifest = bundleJsonFormat.encodeToString(validManifest()).toByteArray(Charsets.UTF_8)
        val bundle = createZip(
            "manifest.json" to manifest,
            "asset-one.bin" to ByteArray(4 * 1024 * 1024),
            "asset-two.bin" to ByteArray(4 * 1024 * 1024)
        )

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle))
        assertTrue("Aggregate bundle limit must apply during preview", preview.isFailure)
        assertEquals(
            "Theme bundle entry exceeds maximum allowed size",
            preview.exceptionOrNull()?.message
        )

        withTestContext { context ->
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle))
            assertTrue("Aggregate bundle limit must apply during extraction", applied.isFailure)
            assertEquals(
                "Theme bundle entry exceeds maximum allowed size",
                applied.exceptionOrNull()?.message
            )
            assertTrue("Failed import must not leave files", context.filesDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun validBundleContinuesToPreviewAndApply() {
        val manifest = validManifest()
        val bundle = createZip(
            "manifest.json" to bundleJsonFormat.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        )

        val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle))
        assertTrue("Valid bundle preview should succeed", preview.isSuccess)
        assertEquals(manifest.bundleName, preview.getOrThrow().manifest.bundleName)

        withTestContext { context ->
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle))
            assertTrue("Valid bundle apply should succeed", applied.isSuccess)
            assertEquals(manifest.bundleName, applied.getOrThrow().bundleName)
            assertTrue("Valid bundle apply should clean staging files", context.cacheDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun testMissingManifestZipFailsValidation() {
        val zipBytesOut = ByteArrayOutputStream()
        ZipOutputStream(zipBytesOut).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("unrelated.txt"))
            zipOut.write("Just some text".toByteArray())
            zipOut.closeEntry()
            zipOut.finish()
        }

        val result = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(zipBytesOut.toByteArray()))
        assertTrue("Bundle without manifest.json must fail inspection", result.isFailure)
    }

    @Test
    fun testGenerateBundleFileNameFormat() {
        val fileName = ThemeBundleManager.generateBundleFileName("Pastel Dreams!")
        assertTrue("Filename should start with sanitized slug", fileName.startsWith("pastel_dreams__"))
        assertTrue("Filename must end with .mochibundle", fileName.endsWith(".mochibundle"))
    }

    @Test
    fun testCursorKineticFieldsAreOptional() {
        // Cursor block as written by versions before the kinetic cursor settings
        val legacyJson = """
            {"baseVelocity": 10.0, "accelerationMulti": 1.08, "pointerSizeDp": 32.0,
             "clickMethod": "ACCESSIBILITY_GESTURE", "hotspot": "TOP_LEFT"}
        """.trimIndent()
        val legacy = bundleJsonFormat.decodeFromString<BundleCursorConfig>(legacyJson)
        assertEquals(null, legacy.tapStepPx)
        assertEquals(null, legacy.maxSpeedPxPerSec)
        assertEquals(null, legacy.accelRampMs)

        val current = legacy.copy(tapStepPx = 2, maxSpeedPxPerSec = 800, accelRampMs = 500)
        val decoded = bundleJsonFormat.decodeFromString<BundleCursorConfig>(
            bundleJsonFormat.encodeToString(current)
        )
        assertEquals(current, decoded)
    }

    @Test
    fun paletteExtraTokens_roundTripAndRemainOptionalForLegacyBundles() {
        val legacyPaletteJson = """
            {"activePresetId": "sakura_dream", "isCustom": false, "bgColorHex": "#FFF5F8",
             "cardColorHex": "#FCE4EC", "greetingColorHex": "#A04064", "actionColorHex": "#A04064",
             "cardRoundnessDp": 24.0}
        """.trimIndent()
        val legacyPalette = bundleJsonFormat.decodeFromString<BundlePaletteConfig>(legacyPaletteJson)
        assertNull(legacyPalette.outlineColorHex)
        assertNull(legacyPalette.textColorHex)
        assertNull(legacyPalette.fontFamilyId)

        withTestContext { context ->
            val manifest = validManifest().copy(
                palette = validManifest().palette.copy(
                    activePresetId = "midnight_mochi",
                    outlineColorHex = "#FF112233",
                    textColorHex = "#FF445566",
                    fontFamilyId = "pixel"
                )
            )
            val bundle = createZip("manifest.json" to manifestBytes(manifest))
            assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)).isSuccess)

            val prefs = one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs(context)
            assertEquals(0xFF112233.toInt(), prefs.getInt(one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_THEME_GREETING_OUTLINE_COLOR, 0))
            assertEquals(0xFF445566.toInt(), prefs.getInt(one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_THEME_FG_TEXT_COLOR, 0))
            assertEquals("pixel", one.crinklecod.mochibun.data.prefs.PreferencesManager.getThemeFontFamily(context))

            // Export round-trip preserves those tokens
            val exported = ByteArrayOutputStream()
            ThemeBundleManager.exportBundle(context, exported, bundleName = "Token Trip").getOrThrow()
            val preview = ThemeBundleManager.readBundleManifest(ByteArrayInputStream(exported.toByteArray())).getOrThrow()
            assertEquals("#FF112233", preview.manifest.palette.outlineColorHex)
            assertEquals("#FF445566", preview.manifest.palette.textColorHex)
            assertEquals("pixel", preview.manifest.palette.fontFamilyId)

            // Unknown font IDs are ignored instead of overwriting the valid font
            val unknownFontManifest = manifest.copy(
                palette = manifest.palette.copy(fontFamilyId = "nonexistent_font_xyz")
            )
            val unknownBundle = createZip("manifest.json" to manifestBytes(unknownFontManifest))
            assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(unknownBundle)).isSuccess)
            assertEquals("pixel", one.crinklecod.mochibun.data.prefs.PreferencesManager.getThemeFontFamily(context))
        }
    }

    @Test
    fun clearUnbundledAssets_resetsPreviousWallpaperCursorAndMascotWhenEnabled() {
        val minimalBundle = createZip("manifest.json" to manifestBytes())
        val mascotPng = fakePng(192, 384)
        val mascotBundle = createZip("manifest.json" to manifestBytes(), "mascot_atlas.png" to mascotPng)

        withTestContext { context ->
            // Seed wallpaper, cursor, and custom mascot
            one.crinklecod.mochibun.data.prefs.PreferencesManager.setWallpaperUri(context, "file:///tmp/wall.png")
            val cursorPrefs = context.getSharedPreferences(
                one.crinklecod.mochibun.data.prefs.CursorPreferences.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
            cursorPrefs.edit().putString("cursor_custom_uri", "file:///tmp/cur.png").apply()
            val staged = File(context.cacheDir, "seed.png").apply { writeBytes(mascotPng) }
            CustomMascotManager.installStaged(context, staged, config = null).getOrThrow()
            staged.delete()

            // Default (clearUnbundledAssets = false) preserves all three
            ThemeBundleManager.applyBundle(context, ByteArrayInputStream(minimalBundle), clearUnbundledAssets = false).getOrThrow()
            assertEquals("file:///tmp/wall.png", one.crinklecod.mochibun.data.prefs.PreferencesManager.getWallpaperUri(context))
            assertEquals("file:///tmp/cur.png", cursorPrefs.getString("cursor_custom_uri", null))
            assertTrue(CustomMascotManager.isActive())

            // Applying a bundle with mascot_atlas.png and clearUnbundledAssets = true keeps the new mascot & clears wallpaper/cursor
            ThemeBundleManager.applyBundle(context, ByteArrayInputStream(mascotBundle), clearUnbundledAssets = true).getOrThrow()
            assertNull(one.crinklecod.mochibun.data.prefs.PreferencesManager.getWallpaperUri(context))
            assertNull(cursorPrefs.getString("cursor_custom_uri", null))
            assertTrue(CustomMascotManager.isActive())

            // Applying a minimal bundle with clearUnbundledAssets = true resets the custom mascot too
            ThemeBundleManager.applyBundle(context, ByteArrayInputStream(minimalBundle), clearUnbundledAssets = true).getOrThrow()
            assertEquals(MascotSpriteSource.BuiltIn, MascotSpriteStore.source.value)
            assertFalse(CustomMascotManager.atlasFile(context).exists())
        }
    }

    private fun validManifest() = MochibunBundleManifest(
        bundleVersion = 1,
        bundleName = "Test Theme",
        author = "Mochibun",
        createdAtEpochSec = 1729600000L,
        greeting = BundleGreetingConfig(userName = "Mochi User", showSparkles = true),
        palette = BundlePaletteConfig(
            activePresetId = "sakura_dream",
            isCustom = false,
            bgColorHex = "#FFF5F8",
            cardColorHex = "#FCE4EC",
            greetingColorHex = "#A04064",
            actionColorHex = "#A04064",
            cardRoundnessDp = 20.0f
        ),
        cursor = BundleCursorConfig(
            baseVelocity = 10.0f,
            accelerationMulti = 1.05f,
            pointerSizeDp = 28.0f,
            clickMethod = "ACCESSIBILITY_GESTURE",
            hotspot = "CENTER"
        ),
        hasWallpaper = false,
        hasCustomCursor = false
    )

    private fun fakeTtf(totalBytes: Int = 64): ByteArray = ByteArray(maxOf(4, totalBytes)).apply {
        this[0] = 0x00
        this[1] = 0x01
        this[2] = 0x00
        this[3] = 0x00
    }

    private fun fakeOtf(totalBytes: Int = 64): ByteArray = ByteArray(maxOf(4, totalBytes)).apply {
        this[0] = 'O'.code.toByte()
        this[1] = 'T'.code.toByte()
        this[2] = 'T'.code.toByte()
        this[3] = 'O'.code.toByte()
    }

    private fun fakeOgg(totalBytes: Int = 64): ByteArray = ByteArray(maxOf(4, totalBytes)).apply {
        this[0] = 'O'.code.toByte()
        this[1] = 'g'.code.toByte()
        this[2] = 'g'.code.toByte()
        this[3] = 'S'.code.toByte()
    }

    private fun zipEntryNames(bytes: ByteArray): Set<String> = buildSet {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                add(entry.name)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun createZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun withTestContext(block: (BundleTestContext) -> Unit) {
        val root = Files.createTempDirectory("mochibun-bundle-test").toFile()
        val context = BundleTestContext(root, testSharedPreferences())
        try {
            PreferencesManager.reload(context)
            SoundEffectManager.refreshBundleSoundPackState(context)
            block(context)
        } finally {
            root.deleteRecursively()
            SoundEffectManager.refreshBundleSoundPackState(context)
        }
    }

    private fun testSharedPreferences(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            val arguments = args ?: emptyArray()
            when (method.name) {
                "putBoolean", "putFloat", "putInt", "putLong", "putString", "putStringSet" -> {
                    val key = arguments[0] as String
                    val value = arguments[1]
                    if (value == null) values.remove(key) else values[key] = value
                    proxy
                }
                "remove" -> {
                    values.remove(arguments[0] as String)
                    proxy
                }
                "clear" -> {
                    values.clear()
                    proxy
                }
                "commit" -> true
                "apply" -> null
                else -> null
            }
        } as SharedPreferences.Editor

        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            val arguments = args ?: emptyArray()
            when (method.name) {
                "getAll" -> HashMap(values)
                "getBoolean", "getFloat", "getInt", "getLong", "getString", "getStringSet" ->
                    values[arguments[0] as String] ?: arguments[1]
                "contains" -> values.containsKey(arguments[0] as String)
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
    }

    private class BundleTestContext(root: File, private val preferences: SharedPreferences) :
        ContextWrapper(null) {
        private val files = File(root, "files").apply { mkdirs() }
        private val cache = File(root, "cache").apply { mkdirs() }

        override fun getFilesDir(): File = files
        override fun getCacheDir(): File = cache
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        override fun sendBroadcast(intent: android.content.Intent) = Unit
    }
}
