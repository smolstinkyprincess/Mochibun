package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.core.shell.RootOperations
import one.crinklecod.mochibun.core.shell.RootStatus
import one.crinklecod.mochibun.core.system.RootShell
import java.io.File

/**
 * Syncs Mochibun's imported `.mochibundle` font (`custom_bundle_font.ttf`) system-wide via a safe
 * Magisk overlay module at `/data/adb/modules/mochibun_system_font/`.
 *
 * Safety invariants:
 * - Only prepends `mochibun_custom.ttf` entries at the top of `<family name="sans-serif">` in
 *   `/system/etc/fonts.xml`.
 * - Never touches `.ttc` collections (e.g., `NotoSansCJK-Regular.ttc`), `AndroidClock.ttf`, or
 *   other font families, preserving lockscreen clock and fallback glyph rendering.
 * - Cleans up any partial module directory if deployment fails so a broken `fonts.xml` is never
 *   left behind across a reboot.
 */
object SystemFontSyncManager {

    private const val TAG = "SystemFontSyncManager"

    const val MODULE_ID = "mochibun_system_font"
    const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    const val MODULE_SYSTEM_DIR = "$MODULE_DIR/system"
    const val MODULE_FONTS_DIR = "$MODULE_SYSTEM_DIR/fonts"
    const val MODULE_ETC_DIR = "$MODULE_SYSTEM_DIR/etc"
    const val CUSTOM_FONT_FILE_NAME = "mochibun_custom.ttf"
    const val TARGET_FONT_PATH = "$MODULE_FONTS_DIR/$CUSTOM_FONT_FILE_NAME"
    const val TARGET_FONTS_XML_PATH = "$MODULE_ETC_DIR/fonts.xml"
    const val MODULE_PROP_PATH = "$MODULE_DIR/module.prop"
    const val SYSTEM_FONTS_XML_PATH = "/system/etc/fonts.xml"
    const val MAGISK_MODULES_ROOT = "/data/adb/modules"

    private val SUPPORTED_WEIGHTS = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

    private val SANS_SERIF_FAMILY_OPEN_REGEX = Regex(
        """<family\b(?=[^>]*\bname\s*=\s*"sans-serif")[^>]*>"""
    )
    private val CLOSING_FAMILY_REGEX = Regex("""</family\s*>""")
    private val EXISTING_CUSTOM_FONT_LINE_REGEX = Regex(
        """(?m)^[ \t]*<font\b[^>]*>\s*${Regex.escape(CUSTOM_FONT_FILE_NAME)}\s*</font>[ \t]*\r?\n?"""
    )
    private val FONT_INDENT_REGEX = Regex("""(?m)^([ \t]*)<font\b""")

    /**
     * Checks whether the Magisk system font module directory exists on disk.
     * Skips invoking `su` if no su binary is present or root was already explicitly denied.
     */
    suspend fun isModuleInstalled(): Boolean = isModuleInstalled(RootShell)

    internal suspend fun isModuleInstalled(root: RootOperations): Boolean = withContext(Dispatchers.IO) {
        if (!root.isSuBinaryPresent() || root.isGranted() == false) {
            return@withContext false
        }
        val res = root.execute("[ -d ${shellQuote(MODULE_DIR)} ] && echo 'INSTALLED'")
        res.isSuccess && res.stdout.any { it.trim() == "INSTALLED" }
    }

    /**
     * Copies `custom_bundle_font.ttf` into `/data/adb/modules/mochibun_system_font/`, generates a
     * safely patched `fonts.xml` from `/system/etc/fonts.xml`, and writes `module.prop`.
     */
    suspend fun applyBundleFontSystemWide(context: Context): Result<Unit> =
        applyBundleFontSystemWide(context, RootShell)

    internal suspend fun applyBundleFontSystemWide(
        context: Context,
        root: RootOperations
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bundleFont = TypographyManager.bundleFontFile(context)
        if (!TypographyManager.isBundleableFontFile(bundleFont)) {
            return@withContext Result.failure(
                IllegalStateException("No valid bundle font found to apply system-wide")
            )
        }

        val rootError = ensureRootGranted(root)
        if (rootError != null) {
            return@withContext Result.failure(IllegalStateException(rootError))
        }

        val modulesRootCheck = root.execute("[ -d ${shellQuote(MAGISK_MODULES_ROOT)} ] && echo 'OK'")
        if (!modulesRootCheck.isSuccess || modulesRootCheck.stdout.none { it.trim() == "OK" }) {
            return@withContext Result.failure(
                IllegalStateException("Magisk modules directory ($MAGISK_MODULES_ROOT) not found")
            )
        }

        val readXmlResult = root.execute("cat ${shellQuote(SYSTEM_FONTS_XML_PATH)}")
        if (!readXmlResult.isSuccess || readXmlResult.stdout.isEmpty()) {
            val err = readXmlResult.errorString.ifBlank { "Exit code ${readXmlResult.exitCode}" }
            return@withContext Result.failure(
                IllegalStateException("Could not read $SYSTEM_FONTS_XML_PATH: $err")
            )
        }
        val originalXml = readXmlResult.stdout.joinToString("\n") + "\n"

        val patchedXml = buildPatchedFontsXml(originalXml).getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val stagingDir = File(context.cacheDir, "mochibun_system_font_stage")
        try {
            stagingDir.mkdirs()
            val stagedXml = File(stagingDir, "fonts.xml").apply {
                writeText(patchedXml, Charsets.UTF_8)
            }
            val stagedProp = File(stagingDir, "module.prop").apply {
                writeText(buildModuleProp(), Charsets.UTF_8)
            }

            val deployCmd = buildString {
                append("mkdir -p ")
                append(shellQuote(MODULE_FONTS_DIR)).append(' ').append(shellQuote(MODULE_ETC_DIR))
                append(" && rm -f ").append(shellQuote("$MODULE_DIR/disable"))
                append(" && cp ").append(shellQuote(bundleFont.absolutePath)).append(' ').append(shellQuote(TARGET_FONT_PATH))
                append(" && cp ").append(shellQuote(stagedXml.absolutePath)).append(' ').append(shellQuote(TARGET_FONTS_XML_PATH))
                append(" && cp ").append(shellQuote(stagedProp.absolutePath)).append(' ').append(shellQuote(MODULE_PROP_PATH))
                append(" && chmod 0755 ")
                append(shellQuote(MODULE_DIR)).append(' ')
                append(shellQuote(MODULE_SYSTEM_DIR)).append(' ')
                append(shellQuote(MODULE_FONTS_DIR)).append(' ')
                append(shellQuote(MODULE_ETC_DIR))
                append(" && chmod 0644 ")
                append(shellQuote(TARGET_FONT_PATH)).append(' ')
                append(shellQuote(TARGET_FONTS_XML_PATH)).append(' ')
                append(shellQuote(MODULE_PROP_PATH))
                append(" && [ -s ").append(shellQuote(TARGET_FONT_PATH))
                append(" ] && [ -s ").append(shellQuote(TARGET_FONTS_XML_PATH))
                append(" ] && [ -s ").append(shellQuote(MODULE_PROP_PATH)).append(" ]")
            }

            val deployResult = root.execute(deployCmd)
            if (!deployResult.isSuccess) {
                root.execute("rm -rf ${shellQuote(MODULE_DIR)}")
                val err = deployResult.errorString.ifBlank { deployResult.outputString }
                    .ifBlank { "Exit code ${deployResult.exitCode}" }
                Log.w(TAG, "Failed to deploy system font module: $err")
                return@withContext Result.failure(
                    IllegalStateException("Failed to install Magisk font module: $err")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { root.execute("rm -rf ${shellQuote(MODULE_DIR)}") }
            Log.w(TAG, "Exception while deploying system font module", e)
            Result.failure(e)
        } finally {
            runCatching { stagingDir.deleteRecursively() }
        }
    }

    /**
     * Removes `/data/adb/modules/mochibun_system_font` cleanly via `libsu`.
     */
    suspend fun removeSystemFontModule(): Result<Unit> = removeSystemFontModule(RootShell)

    internal suspend fun removeSystemFontModule(root: RootOperations): Result<Unit> =
        withContext(Dispatchers.IO) {
            val rootError = ensureRootGranted(root)
            if (rootError != null) {
                return@withContext Result.failure(IllegalStateException(rootError))
            }

            val res = root.execute("rm -rf ${shellQuote(MODULE_DIR)} && [ ! -e ${shellQuote(MODULE_DIR)} ]")
            if (res.isSuccess) {
                Result.success(Unit)
            } else {
                val err = res.errorString.ifBlank { res.outputString }
                    .ifBlank { "Exit code ${res.exitCode}" }
                Result.failure(IllegalStateException("Failed to remove Magisk font module: $err"))
            }
        }

    /**
     * Reboots the device via root shell so Magisk mounts or unmounts the system font module.
     */
    suspend fun rebootDevice(): Result<Unit> = rebootDevice(RootShell)

    internal suspend fun rebootDevice(root: RootOperations): Result<Unit> =
        withContext(Dispatchers.IO) {
            val rootError = ensureRootGranted(root)
            if (rootError != null) {
                return@withContext Result.failure(IllegalStateException(rootError))
            }
            val res = root.execute("svc power reboot || reboot")
            if (res.isSuccess) {
                Result.success(Unit)
            } else {
                val err = res.errorString.ifBlank { res.outputString }
                    .ifBlank { "Exit code ${res.exitCode}" }
                Result.failure(IllegalStateException("Reboot failed: $err"))
            }
        }

    /**
     * Safely patches `/system/etc/fonts.xml` content by prepending `mochibun_custom.ttf` weights
     * `100`–`900` (`style="normal"`) at the top of `<family name="sans-serif">`.
     * All other families (including `.ttc` collections and `AndroidClock.ttf`) remain untouched.
     */
    internal fun buildPatchedFontsXml(systemFontsXml: String): Result<String> {
        if (systemFontsXml.isBlank() ||
            !systemFontsXml.contains("<familyset") ||
            !systemFontsXml.contains("</familyset>")
        ) {
            return Result.failure(
                IllegalStateException("Invalid $SYSTEM_FONTS_XML_PATH: missing <familyset> root")
            )
        }

        val openMatch = SANS_SERIF_FAMILY_OPEN_REGEX.find(systemFontsXml)
            ?: return Result.failure(
                IllegalStateException("Could not find <family name=\"sans-serif\"> in $SYSTEM_FONTS_XML_PATH")
            )
        if (openMatch.value.trimEnd().endsWith("/>")) {
            return Result.failure(
                IllegalStateException("<family name=\"sans-serif\"> is unexpectedly self-closing")
            )
        }

        val bodyStart = openMatch.range.last + 1
        val closeMatch = CLOSING_FAMILY_REGEX.find(systemFontsXml, startIndex = bodyStart)
            ?: return Result.failure(
                IllegalStateException("Missing closing </family> for sans-serif in $SYSTEM_FONTS_XML_PATH")
            )

        val bodyEnd = closeMatch.range.first
        val originalBody = systemFontsXml.substring(bodyStart, bodyEnd)
        val cleanedBody = EXISTING_CUSTOM_FONT_LINE_REGEX.replace(originalBody, "")

        val indent = FONT_INDENT_REGEX.find(cleanedBody)?.groupValues?.get(1) ?: "        "
        val customFontEntries = SUPPORTED_WEIGHTS.joinToString(separator = "\n") { weight ->
            """$indent<font weight="$weight" style="normal">$CUSTOM_FONT_FILE_NAME</font>"""
        }

        val updatedBody = when {
            cleanedBody.startsWith("\r\n") || cleanedBody.startsWith("\n") ->
                "\n$customFontEntries$cleanedBody"
            else ->
                "\n$customFontEntries\n$cleanedBody"
        }

        val patched = buildString(systemFontsXml.length + customFontEntries.length + 16) {
            append(systemFontsXml, 0, bodyStart)
            append(updatedBody)
            append(systemFontsXml, bodyEnd, systemFontsXml.length)
        }
        return Result.success(patched)
    }

    internal fun buildModuleProp(): String = buildString {
        appendLine("id=$MODULE_ID")
        appendLine("name=Mochibun System Font")
        appendLine("version=1.0")
        appendLine("versionCode=1")
        appendLine("author=Mochibun")
        appendLine("description=System-wide custom font synced from Mochibun")
    }

    private suspend fun ensureRootGranted(root: RootOperations): String? {
        if (!root.isSuBinaryPresent()) {
            return "Root (su) binary not found on this device"
        }
        return when (val status = root.requestRoot()) {
            is RootStatus.Granted -> null
            is RootStatus.Denied -> status.message
            is RootStatus.Error -> status.message
            RootStatus.NotAvailable -> "Root access is not available"
            RootStatus.Unknown, RootStatus.Requesting -> "Root access could not be verified"
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
