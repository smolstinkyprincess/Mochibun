package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.data.model.BundleCursorConfig
import com.crinklecodone.mochibun.data.model.BundleGreetingConfig
import com.crinklecodone.mochibun.data.model.BundlePaletteConfig
import com.crinklecodone.mochibun.data.model.MochibunBundleManifest
import com.crinklecodone.mochibun.data.model.bundleJsonFormat
import com.crinklecodone.mochibun.data.repository.ThemeBundleManager
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThemeBundleTest {

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
}
