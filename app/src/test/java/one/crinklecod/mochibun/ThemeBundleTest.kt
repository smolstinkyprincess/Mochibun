package one.crinklecod.mochibun

import android.content.ContextWrapper
import android.content.SharedPreferences
import one.crinklecod.mochibun.data.model.BundleCursorConfig
import one.crinklecod.mochibun.data.model.BundleMascotConfig
import one.crinklecod.mochibun.data.model.BundleMascotSlot
import one.crinklecod.mochibun.data.model.MascotAtlasValidator
import one.crinklecod.mochibun.data.repository.CustomMascotManager
import one.crinklecod.mochibun.ui.components.MascotAnimSlot
import one.crinklecod.mochibun.ui.components.MascotSlotLayout
import one.crinklecod.mochibun.ui.components.MascotSpriteSource
import one.crinklecod.mochibun.ui.components.MascotSpriteStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import one.crinklecod.mochibun.data.model.BundleGreetingConfig
import one.crinklecod.mochibun.data.model.BundlePaletteConfig
import one.crinklecod.mochibun.data.model.MochibunBundleManifest
import one.crinklecod.mochibun.data.model.bundleJsonFormat
import one.crinklecod.mochibun.data.repository.ThemeBundleManager
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeBundleTest {

    @Before
    fun resetMascot() {
        MascotSpriteStore.setSource(MascotSpriteSource.BuiltIn)
        // Plain JVM has no real BitmapFactory: read the PNG header instead
        CustomMascotManager.platformBounds = { MascotAtlasValidator.readPngSize(it) }
    }

    // ── Custom mascot atlas (mascot_atlas.png) ──────────────────────────────

    private fun manifestBytes(manifest: MochibunBundleManifest = validManifest()) =
        bundleJsonFormat.encodeToString(manifest).toByteArray(Charsets.UTF_8)

    private val customSource get() = MascotSpriteStore.source.value as? MascotSpriteSource.Custom

    @Test
    fun bundleWithMascot_previewsAndInstallsIt() {
        val png = fakePng(192, 384)
        val bundle = createZip("manifest.json" to manifestBytes(), "mascot_atlas.png" to png)

        assertTrue(ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow().hasMascotAsset)

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
        val bundle = createZip("manifest.json" to manifestBytes(), "mascot_atlas.png" to tooBig)

        assertFalse(ThemeBundleManager.readBundleManifest(ByteArrayInputStream(bundle)).getOrThrow().hasMascotAsset)
        withTestContext { context ->
            var skipped: String? = null
            val applied = ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)) { skipped = it }
            assertTrue("Bundle still applies", applied.isSuccess)
            assertTrue(skipped!!.contains("1.5 MB"))
            assertEquals(MascotSpriteSource.BuiltIn, MascotSpriteStore.source.value)
            assertFalse(CustomMascotManager.atlasFile(context).exists())
            assertTrue("Staging files cleaned", context.cacheDir.listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun invalidMascotDimensions_areSkipped() {
        for (png in listOf(fakePng(200, 380), fakePng(16 * 17, 16 * 8), "not a png".toByteArray())) {
            val bundle = createZip("manifest.json" to manifestBytes(), "mascot_atlas.png" to png)
            withTestContext { context ->
                var skipped: String? = null
                assertTrue(ThemeBundleManager.applyBundle(context, ByteArrayInputStream(bundle)) { skipped = it }.isSuccess)
                assertTrue(skipped != null)
                assertEquals(MascotSpriteSource.BuiltIn, MascotSpriteStore.source.value)
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

        MascotSpriteStore.setSource(MascotSpriteSource.BuiltIn)
        withTestContext { target ->
            ThemeBundleManager.applyBundle(target, ByteArrayInputStream(exported)).getOrThrow()
            assertArrayEquals(png, CustomMascotManager.atlasFile(target).readBytes())
            assertEquals(installedLayout, customSource!!.layout)
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
        assertTrue("Filename must end with .mochibun", fileName.endsWith(".mochibun"))
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
        try {
            block(BundleTestContext(root, testSharedPreferences()))
        } finally {
            root.deleteRecursively()
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
