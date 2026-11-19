package one.crinklecod.mochibun

import kotlinx.coroutines.runBlocking
import one.crinklecod.mochibun.core.shell.RootOperations
import one.crinklecod.mochibun.core.shell.RootResult
import one.crinklecod.mochibun.core.shell.RootStatus
import one.crinklecod.mochibun.data.repository.SystemFontSyncManager
import one.crinklecod.mochibun.data.repository.TypographyManager
import one.crinklecod.mochibun.ui.screens.settings.appearanceRowIds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SystemFontSyncManagerTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private class FakeRoot(
        var suPresent: Boolean = true,
        var granted: Boolean? = true,
        var rootStatus: RootStatus = RootStatus.Granted("uid=0(root)"),
        var handler: (String) -> RootResult = { RootResult(exitCode = 0) }
    ) : RootOperations {
        val commands = mutableListOf<String>()

        override fun isSuBinaryPresent(): Boolean = suPresent
        override fun isGranted(): Boolean? = granted
        override fun warmUp() {}
        override fun acquireBlocking(): Boolean = suPresent && rootStatus is RootStatus.Granted
        override suspend fun requestRoot(): RootStatus = rootStatus
        override suspend fun execute(command: String, timeoutMs: Long): RootResult {
            commands += command
            return handler(command)
        }
        override fun executeBlocking(vararg commands: String): RootResult {
            this.commands += commands
            return handler(commands.joinToString(" && "))
        }
        override fun submit(command: String, onResult: ((RootResult) -> Unit)?) {
            commands += command
            onResult?.invoke(handler(command))
        }
    }

    @Before
    fun cleanUpBefore() {
        TypographyManager.bundleFontFile(context).delete()
        File(context.cacheDir, "mochibun_system_font_stage").deleteRecursively()
    }

    @After
    fun cleanUpAfter() {
        TypographyManager.bundleFontFile(context).delete()
        File(context.cacheDir, "mochibun_system_font_stage").deleteRecursively()
    }

    @Test
    fun buildPatchedFontsXml_prependsWeights100To900AtTopOfSansSerifAndLeavesTtcAndClockUntouched() {
        val sampleXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <familyset version="23">
                <family name="sans-serif">
                    <font weight="100" style="normal">Roboto-Thin.ttf</font>
                    <font weight="400" style="normal">Roboto-Regular.ttf</font>
                    <font weight="700" style="normal">Roboto-Bold.ttf</font>
                </family>
                <family name="sans-serif-condensed">
                    <font weight="400" style="normal">RobotoCondensed-Regular.ttf</font>
                </family>
                <family name="clock">
                    <font weight="400" style="normal">AndroidClock.ttf</font>
                </family>
                <family lang="ja">
                    <font weight="400" style="normal" index="0">NotoSansCJK-Regular.ttc</font>
                </family>
            </familyset>
        """.trimIndent() + "\n"

        val patched = SystemFontSyncManager.buildPatchedFontsXml(sampleXml).getOrThrow()

        for (weight in listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)) {
            assertTrue(
                "Missing weight $weight",
                patched.contains("""<font weight="$weight" style="normal">mochibun_custom.ttf</font>""")
            )
        }

        val custom400Idx = patched.indexOf("""<font weight="400" style="normal">mochibun_custom.ttf</font>""")
        val robotoThinIdx = patched.indexOf("Roboto-Thin.ttf")
        assertTrue(custom400Idx in 1 until robotoThinIdx)

        // Ensure CJK .ttc, AndroidClock.ttf, and sans-serif-condensed were not modified
        val condensedSection = patched.substringAfter("""<family name="sans-serif-condensed">""")
        assertFalse(condensedSection.contains("mochibun_custom.ttf"))
        assertTrue(condensedSection.contains("""<font weight="400" style="normal">AndroidClock.ttf</font>"""))
        assertTrue(condensedSection.contains("""<font weight="400" style="normal" index="0">NotoSansCJK-Regular.ttc</font>"""))
    }

    @Test
    fun buildPatchedFontsXml_isIdempotentWhenAlreadyPatched() {
        val sampleXml = """
            <familyset version="23">
                <family name="sans-serif">
                    <font weight="400" style="normal">Roboto-Regular.ttf</font>
                </family>
            </familyset>
        """.trimIndent() + "\n"

        val first = SystemFontSyncManager.buildPatchedFontsXml(sampleXml).getOrThrow()
        val second = SystemFontSyncManager.buildPatchedFontsXml(first).getOrThrow()

        assertEquals(first, second)
        val occurrences = Regex("mochibun_custom\\.ttf").findAll(second).count()
        assertEquals(9, occurrences)
    }

    @Test
    fun buildPatchedFontsXml_failsSafelyOnMissingSansSerifOrInvalidXml() {
        assertTrue(SystemFontSyncManager.buildPatchedFontsXml("").isFailure)
        assertTrue(SystemFontSyncManager.buildPatchedFontsXml("<notxml/>").isFailure)
        assertTrue(
            SystemFontSyncManager.buildPatchedFontsXml(
                """
                <familyset version="23">
                    <family name="sans-serif-condensed">
                        <font weight="400" style="normal">RobotoCondensed-Regular.ttf</font>
                    </family>
                </familyset>
                """.trimIndent()
            ).isFailure
        )
        assertTrue(
            SystemFontSyncManager.buildPatchedFontsXml(
                """
                <familyset version="23">
                    <family name="sans-serif">
                        <font weight="400" style="normal">Roboto-Regular.ttf</font>
                </familyset>
                """.trimIndent()
            ).isFailure
        )
    }

    @Test
    fun buildModuleProp_containsExpectedMagiskFields() {
        val prop = SystemFontSyncManager.buildModuleProp()
        assertTrue(prop.contains("id=mochibun_system_font"))
        assertTrue(prop.contains("name=Mochibun System Font"))
        assertTrue(prop.contains("version=1.0"))
        assertTrue(prop.contains("versionCode=1"))
        assertTrue(prop.contains("author=Mochibun"))
        assertTrue(prop.contains("description=System-wide custom font synced from Mochibun"))
    }

    @Test
    fun applyBundleFontSystemWide_deploysModuleAndCleansStagingDir() = runBlocking {
        val fontFile = TypographyManager.bundleFontFile(context)
        fontFile.writeBytes(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x0A))

        var capturedStagedXml = ""
        var capturedStagedProp = ""
        val root = FakeRoot { cmd ->
            when {
                cmd.startsWith("[ -d '/data/adb/modules' ]") -> RootResult(0, stdout = listOf("OK"))
                cmd.startsWith("cat '/system/etc/fonts.xml'") -> RootResult(
                    0,
                    stdout = listOf(
                        "<familyset version=\"23\">",
                        "    <family name=\"sans-serif\">",
                        "        <font weight=\"400\" style=\"normal\">Roboto-Regular.ttf</font>",
                        "    </family>",
                        "    <family name=\"clock\">",
                        "        <font weight=\"400\" style=\"normal\">AndroidClock.ttf</font>",
                        "    </family>",
                        "</familyset>"
                    )
                )
                cmd.startsWith("mkdir -p ") -> {
                    val stageDir = File(context.cacheDir, "mochibun_system_font_stage")
                    capturedStagedXml = File(stageDir, "fonts.xml").readText()
                    capturedStagedProp = File(stageDir, "module.prop").readText()
                    RootResult(0)
                }
                else -> RootResult(0)
            }
        }

        val result = SystemFontSyncManager.applyBundleFontSystemWide(context, root)

        assertTrue("Expected deploy success, got: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(capturedStagedXml.contains("mochibun_custom.ttf"))
        assertTrue(capturedStagedXml.contains("AndroidClock.ttf"))
        assertTrue(capturedStagedProp.contains("id=mochibun_system_font"))
        val deployCmd = root.commands.first { it.startsWith("mkdir -p ") }
        assertTrue(deployCmd.contains("chmod 0755"))
        assertTrue(deployCmd.contains("chmod 0644"))
        assertTrue(deployCmd.contains(SystemFontSyncManager.TARGET_FONT_PATH))
        assertTrue(deployCmd.contains(SystemFontSyncManager.TARGET_FONTS_XML_PATH))
        assertTrue(deployCmd.contains(SystemFontSyncManager.MODULE_PROP_PATH))
        assertFalse(File(context.cacheDir, "mochibun_system_font_stage").exists())
    }

    @Test
    fun applyBundleFontSystemWide_rollsBackModuleDirectoryOnFailure() = runBlocking {
        TypographyManager.bundleFontFile(context).writeBytes(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x0A))

        val root = FakeRoot { cmd ->
            when {
                cmd.startsWith("[ -d '/data/adb/modules' ]") -> RootResult(0, stdout = listOf("OK"))
                cmd.startsWith("cat '/system/etc/fonts.xml'") -> RootResult(
                    0,
                    stdout = listOf(
                        "<familyset version=\"23\">",
                        "    <family name=\"sans-serif\">",
                        "        <font weight=\"400\" style=\"normal\">Roboto-Regular.ttf</font>",
                        "    </family>",
                        "</familyset>"
                    )
                )
                cmd.startsWith("mkdir -p ") -> RootResult.failure("cp: permission denied", exitCode = 1)
                else -> RootResult(0)
            }
        }

        val result = SystemFontSyncManager.applyBundleFontSystemWide(context, root)

        assertTrue(result.isFailure)
        assertTrue(root.commands.any { it == "rm -rf '${SystemFontSyncManager.MODULE_DIR}'" })
    }

    @Test
    fun removeSystemFontModule_executesCleanRemoval() = runBlocking {
        val root = FakeRoot()
        val result = SystemFontSyncManager.removeSystemFontModule(root)
        assertTrue(result.isSuccess)
        assertTrue(root.commands.any { it.contains("rm -rf '${SystemFontSyncManager.MODULE_DIR}'") })
    }

    @Test
    fun appearanceRowIds_onlyShowsSystemFontRowsWhenBothBundleFontAndRootOptionsEnabled() {
        val noFont = appearanceRowIds(
            hasBundleFont = false,
            showRootOptions = true,
            isSystemFontInstalled = true,
            hasCustomWallpaper = false,
            hasCustomMascot = false,
            hasCustomBorder = false,
            isSystemMonitorEnabled = false,
            isSubDisplayTickerEnabled = false
        )
        assertFalse(noFont.contains("apply_system_font"))
        assertFalse(noFont.contains("reset_system_font"))

        val noRoot = appearanceRowIds(
            hasBundleFont = true,
            showRootOptions = false,
            isSystemFontInstalled = true,
            hasCustomWallpaper = false,
            hasCustomMascot = false,
            hasCustomBorder = false,
            isSystemMonitorEnabled = false,
            isSubDisplayTickerEnabled = false
        )
        assertFalse(noRoot.contains("apply_system_font"))
        assertFalse(noRoot.contains("reset_system_font"))

        val rootNotInstalled = appearanceRowIds(
            hasBundleFont = true,
            showRootOptions = true,
            isSystemFontInstalled = false,
            hasCustomWallpaper = false,
            hasCustomMascot = false,
            hasCustomBorder = false,
            isSystemMonitorEnabled = false,
            isSubDisplayTickerEnabled = false
        )
        assertTrue(rootNotInstalled.contains("apply_system_font"))
        assertFalse(rootNotInstalled.contains("reset_system_font"))

        val rootInstalled = appearanceRowIds(
            hasBundleFont = true,
            showRootOptions = true,
            isSystemFontInstalled = true,
            hasCustomWallpaper = false,
            hasCustomMascot = false,
            hasCustomBorder = false,
            isSystemMonitorEnabled = false,
            isSubDisplayTickerEnabled = false
        )
        assertTrue(rootInstalled.contains("apply_system_font"))
        assertTrue(rootInstalled.contains("reset_system_font"))
    }
}
