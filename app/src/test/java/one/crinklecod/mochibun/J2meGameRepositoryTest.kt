package one.crinklecod.mochibun

import android.graphics.Bitmap
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.J2meGameRepository
import org.robolectric.RuntimeEnvironment
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class J2meGameRepositoryTest {

    // Minimal 1x1 PNG header + chunks for archive icon extraction verification
    private val samplePngBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
        0x42, 0x60, 0x82.toByte()
    )

    @Test
    fun parseManifestPrefersMidletNameAndMidletIcon() {
        val manifest = """
            Manifest-Version: 1.0
            MIDlet-Name: Diamond Rush
            MIDlet-Icon: /icons/diamond.png
            MIDlet-1: Ignored Name, /ignored.png, com.example.Main
        """.trimIndent()

        val meta = J2meGameRepository.parseManifest(
            ByteArrayInputStream(manifest.toByteArray(Charsets.UTF_8)),
            "diamond_rush.jar"
        )

        assertEquals("Diamond Rush", meta.name)
        assertEquals("icons/diamond.png", meta.iconPath)
    }

    @Test
    fun parseManifestFallsBackToMidlet1NameAndIcon() {
        val manifest = """
            Manifest-Version: 1.0
            MIDlet-1: Bounce Tales, /res/icon.png, BounceMIDlet
        """.trimIndent()

        val meta = J2meGameRepository.parseManifest(
            ByteArrayInputStream(manifest.toByteArray(Charsets.UTF_8)),
            "bounce.JAR"
        )

        assertEquals("Bounce Tales", meta.name)
        assertEquals("res/icon.png", meta.iconPath)
    }

    @Test
    fun parseManifestUnfolds72ByteContinuationLines() {
        val manifest = "Manifest-Version: 1.0\r\n" +
            "MIDlet-Name: Castlevania Aria of Sorrow\r\n" +
            "  Mobile Edition\r\n" +
            "MIDlet-Icon: /assets/very/long/\r\n" +
            " path/to/game_icon.png\r\n"

        val meta = J2meGameRepository.parseManifest(
            ByteArrayInputStream(manifest.toByteArray(Charsets.UTF_8)),
            "castlevania.jar"
        )

        assertEquals("Castlevania Aria of Sorrow Mobile Edition", meta.name)
        assertEquals("assets/very/long/path/to/game_icon.png", meta.iconPath)
    }

    @Test
    fun parseManifestFallsBackToFilenameWhenMetadataMissing() {
        val manifest = "Manifest-Version: 1.0\nCreated-By: Mochibun Test\n"
        val meta = J2meGameRepository.parseManifest(
            ByteArrayInputStream(manifest.toByteArray(Charsets.UTF_8)),
            "Kirby_Mini.JAR"
        )

        assertEquals("Kirby_Mini", meta.name)
        assertNull(meta.iconPath)
    }

    @Test
    fun parseJarStreamExtractsManifestAndPngWhenManifestFirst() {
        val manifestBytes = """
            Manifest-Version: 1.0
            MIDlet-Name: Tamagotchi Angel
            MIDlet-Icon: /res/tama.png
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val jarBytes = buildJarBytes(
            "META-INF/MANIFEST.MF" to manifestBytes,
            "res/tama.png" to samplePngBytes
        )

        val parsed = J2meGameRepository.parseJarStream(
            ByteArrayInputStream(jarBytes),
            "tamagotchi.jar"
        )

        assertEquals("Tamagotchi Angel", parsed.metadata.name)
        assertEquals("res/tama.png", parsed.metadata.iconPath)
        assertNotNull(parsed.iconBytes)
        assertArrayEquals(samplePngBytes, parsed.iconBytes)
        assertFalse(parsed.needsSecondPass)
    }

    @Test
    fun parseJarStreamExtractsPngWhenIconAppearsBeforeManifest() {
        val manifestBytes = """
            Manifest-Version: 1.0
            MIDlet-1: Nintendogs Mobile, /i.png, DogsMIDlet
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val jarBytes = buildJarBytes(
            "i.png" to samplePngBytes,
            "META-INF/MANIFEST.MF" to manifestBytes
        )

        val parsed = J2meGameRepository.parseJarStream(
            ByteArrayInputStream(jarBytes),
            "dogs.jar"
        )

        assertEquals("Nintendogs Mobile", parsed.metadata.name)
        assertEquals("i.png", parsed.metadata.iconPath)
        assertNotNull(parsed.iconBytes)
        assertArrayEquals(samplePngBytes, parsed.iconBytes)
    }

    @Test
    fun launcherAppJ2meKeyAvoidsDelimiterCollisions() {
        val docUri = "content://com.android.externalstorage.documents/tree/primary%3AJ2ME/document/primary%3AJ2ME%2FGame%2C%20v1%7C2.jar"
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val app = LauncherApp(
            resolveInfo = null,
            name = "Game, v1|2",
            iconBitmap = bmp,
            installTime = 1234L,
            j2meDocumentUri = docUri
        )

        assertTrue(app.isJ2meGame)
        assertTrue(app.packageName.startsWith("j2me://"))
        assertFalse(app.packageName.contains(","))
        assertFalse(app.packageName.contains("|"))
        assertEquals("app:${app.packageName}", app.favoriteKey)
    }

    @Test
    fun mergeConfigTemplateCopiesControlsAndDisplayWhilePreservingPerGameFields() {
        val targetJson = JSONObject().apply {
            put("detectedScreenWidth", 176)
            put("detectedScreenHeight", 208)
            put("CompatibilityProfile", "nokia_s60")
            put("compatibilityReasons", JSONArray(listOf("detected_resolution")))
            put("SystemProperties", "microedition.locale: en")
            put("KeyMappings", JSONObject().put("19", "UP"))
            put("ShowKeyboard", true)
            put("ScreenScaleToFit", false)
            put("ScreenKeepAspectRatio", false)
            put("ScreenGravity", 1)
        }.toString()

        val templateJson = JSONObject().apply {
            put("detectedScreenWidth", 240)
            put("detectedScreenHeight", 320)
            put("CompatibilityProfile", "reference_profile")
            put("compatibilityReasons", JSONArray(listOf("manual_override")))
            put("SystemProperties", "microedition.locale: ja")
            put("ControlMode", 2)
            put("KeyMappings", JSONObject().put("19", "SOFT_LEFT").put("20", "FIRE"))
            put("ShowKeyboard", false)
            put("ScreenScaleToFit", true)
            put("ScreenKeepAspectRatio", true)
            put("ScreenScaleType", 1)
            put("ScreenFilter", true)
            put("FsrUpscaling", false)
        }.toString()

        val mergedText = J2meGameRepository.mergeConfigTemplate(targetJson, templateJson)
        val merged = JSONObject(mergedText)

        // Copied control and display keys from template
        assertEquals(2, merged.getInt("ControlMode"))
        assertEquals("SOFT_LEFT", merged.getJSONObject("KeyMappings").getString("19"))
        assertEquals("FIRE", merged.getJSONObject("KeyMappings").getString("20"))
        assertFalse(merged.getBoolean("ShowKeyboard"))
        assertTrue(merged.getBoolean("ScreenScaleToFit"))
        assertTrue(merged.getBoolean("ScreenKeepAspectRatio"))
        assertEquals(1, merged.getInt("ScreenScaleType"))
        assertTrue(merged.getBoolean("ScreenFilter"))
        assertFalse(merged.getBoolean("FsrUpscaling"))

        // Target-specific fields preserved untouched
        assertEquals(176, merged.getInt("detectedScreenWidth"))
        assertEquals(208, merged.getInt("detectedScreenHeight"))
        assertEquals("nokia_s60", merged.getString("CompatibilityProfile"))
        assertEquals("detected_resolution", merged.getJSONArray("compatibilityReasons").getString(0))
        assertEquals("microedition.locale: en", merged.getString("SystemProperties"))
        // Keys absent from template remain as target had them
        assertEquals(1, merged.getInt("ScreenGravity"))
    }

    @Test
    fun autoSyncOnLaunchPreferenceDefaultsOffAndPersists() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.getPrefs(context).edit().clear().commit()
        PreferencesManager.reload(context)

        assertFalse(PreferencesManager.isJ2meAutoSyncOnLaunchEnabled(context))
        assertFalse(PreferencesManager.isJ2meAutoSyncOnLaunchEnabled.value)

        PreferencesManager.setJ2meAutoSyncOnLaunchEnabled(context, true)
        assertTrue(PreferencesManager.isJ2meAutoSyncOnLaunchEnabled(context))
        assertTrue(PreferencesManager.isJ2meAutoSyncOnLaunchEnabled.value)

        PreferencesManager.setJ2meAutoSyncOnLaunchEnabled(context, false)
        assertFalse(PreferencesManager.isJ2meAutoSyncOnLaunchEnabled(context))
    }

    private fun buildJarBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
