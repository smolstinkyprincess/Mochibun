package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.theme.MaruMinyaMFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Model representing a typography font family available in Mochibun.
 */
data class TypographyOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val sampleText: String,
    val fontFamily: FontFamily,
    val isExternal: Boolean = false,
    val file: File? = null
)

/**
 * Manages discovery, dynamic font loading, and selection for Mochibun typography.
 * Supports built-in Japanese Garakei/pixel styles and loads custom fonts from
 * `/sdcard/Android/data/one.crinklecod.mochibun/files/fonts/`.
 */
object TypographyManager {
    private const val TAG = "TypographyManager"
    const val FONTS_DIR_NAME = "fonts"
    private const val README_FILE_NAME = "README.txt"
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanLock = Any()

    val BuiltInTypographyOptions = listOf(
        TypographyOption(
            id = "default",
            name = "MaruMinyaM (まるみにゃむ)",
            subtitle = "Authentic 12px Keitai pixel font",
            sampleText = "あA 12:00",
            fontFamily = MaruMinyaMFontFamily
        ),
        TypographyOption(
            id = "clean_sans",
            name = "Clean Sans",
            subtitle = "Modern crisp system font",
            sampleText = "あA 12:00",
            fontFamily = FontFamily.Default
        ),
        TypographyOption(
            id = "pixel",
            name = "Keitai Dot (ガラケー)",
            subtitle = "Retro 8-bit / 10-bit feature-phone matrix",
            sampleText = "あA 12:00",
            fontFamily = FontFamily.Monospace
        ),
        TypographyOption(
            id = "gothic",
            name = "Keitai Gothic (ゴシック)",
            subtitle = "Classic Japanese 2000s mobile UI",
            sampleText = "あA 12:00",
            fontFamily = FontFamily.SansSerif
        ),
        TypographyOption(
            id = "serif",
            name = "Classic Mincho (明朝)",
            subtitle = "Nostalgic Keitai novel editorial serif",
            sampleText = "あA 12:00",
            fontFamily = FontFamily.Serif
        )
    )

    private val _options = MutableStateFlow<List<TypographyOption>>(BuiltInTypographyOptions)
    val options: StateFlow<List<TypographyOption>> = _options.asStateFlow()

    // ── Font imported from a .mochibundle ("font.ttf" / "font.otf") ──────────────────────────

    /** Font-style id of the font installed from a `.mochibundle`. */
    const val BUNDLE_FONT_ID = "bundle"
    const val BUNDLE_FONT_FILE_NAME = "custom_bundle_font.ttf"
    private const val BUNDLE_FONT_NAME_FILE_NAME = "custom_bundle_font.name"
    const val BUNDLE_FONT_FALLBACK_NAME = "Custom Bundle"
    /** Uncompressed cap for a bundled font; keeps glyph tables small on the ~1.8 GB MT6761. */
    const val MAX_BUNDLE_FONT_BYTES = 4L * 1024L * 1024L
    private const val MAX_FONT_DISPLAY_NAME_CHARS = 40

    /**
     * Creates the platform [Typeface]. Replaceable in JVM unit tests, where android.jar stubs
     * return null.
     */
    internal var typefaceLoader: (File) -> Typeface? = { Typeface.createFromFile(it) }

    /**
     * True when [file] is a font Android can actually render. Since API 26 a broken font file
     * does not throw: `Typeface.createFromFile` silently returns [Typeface.DEFAULT], so that
     * result counts as a failure too.
     */
    internal var fontFileValidator: (File) -> Boolean = { file ->
        val typeface = runCatching { typefaceLoader(file) }.getOrNull()
        typeface != null && typeface !== Typeface.DEFAULT
    }

    fun bundleFontFile(context: Context): File = File(context.filesDir, BUNDLE_FONT_FILE_NAME)

    /**
     * True for a TrueType/OpenType single-font header (`00 01 00 00`, `true`, or `OTTO`).
     * Collections (`ttcf`) are rejected because a bundle carries exactly one face.
     */
    fun isSfntHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        val b2 = header[2].toInt() and 0xFF
        val b3 = header[3].toInt() and 0xFF
        val trueType = b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00
        val tag = String(byteArrayOf(header[0], header[1], header[2], header[3]), Charsets.ISO_8859_1)
        return trueType || tag == "true" || tag == "OTTO"
    }

    /** Cheap size + header check (no Typeface creation) used for previews and exports. */
    fun isBundleableFontFile(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_BUNDLE_FONT_BYTES) return false
        val header = ByteArray(4)
        val read = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
        return read == 4 && isSfntHeader(header)
    }

    fun sanitizeFontDisplayName(raw: String?): String? =
        raw?.filterNot { it.isISOControl() }
            ?.trim()
            ?.take(MAX_FONT_DISPLAY_NAME_CHARS)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Display name stored alongside the bundle font, or null when none was given. */
    fun bundleFontDisplayName(context: Context): String? {
        val nameFile = File(context.filesDir, BUNDLE_FONT_NAME_FILE_NAME)
        if (!nameFile.isFile) return null
        return sanitizeFontDisplayName(runCatching { nameFile.readText() }.getOrNull())
    }

    fun hasBundleFont(context: Context): Boolean = isBundleableFontFile(bundleFontFile(context))

    /**
     * Validates [staged] (size, sfnt header, and a real non-default [Typeface]) and installs it
     * as [BUNDLE_FONT_FILE_NAME]. Call off the main thread.
     *
     * The new font is written to a temp file and renamed over the old one, never overwritten in
     * place: the active [Typeface] memory-maps the current file, and rewriting those bytes under
     * it would corrupt live glyph rendering. Rename swaps the directory entry while the old
     * mapping keeps its inode.
     */
    fun installBundleFont(context: Context, staged: File, displayName: String?): Result<Unit> {
        return try {
            if (!staged.isFile || staged.length() <= 0L) {
                return Result.failure(IllegalArgumentException("font file is empty"))
            }
            if (staged.length() > MAX_BUNDLE_FONT_BYTES) {
                return Result.failure(IllegalArgumentException("bigger than 4 MB"))
            }
            if (!isBundleableFontFile(staged)) {
                return Result.failure(IllegalArgumentException("not a TrueType/OpenType font"))
            }
            if (!fontFileValidator(staged)) {
                return Result.failure(IllegalArgumentException("Android couldn't load this font"))
            }

            val target = bundleFontFile(context)
            val temp = File(context.filesDir, "$BUNDLE_FONT_FILE_NAME.tmp")
            staged.copyTo(temp, overwrite = true)
            if (!temp.renameTo(target)) {
                temp.delete()
                return Result.failure(IllegalStateException("couldn't save font"))
            }

            val nameFile = File(context.filesDir, BUNDLE_FONT_NAME_FILE_NAME)
            val cleanName = sanitizeFontDisplayName(displayName)
            if (cleanName != null) nameFile.writeText(cleanName) else nameFile.delete()

            refreshBundleFontOption(context)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install bundle font", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes the bundle font. If it was the active style, falls back to the default font so
     * every screen (and the IME) re-renders with a font that still exists.
     */
    fun resetBundleFont(context: Context) {
        bundleFontFile(context).delete()
        File(context.filesDir, BUNDLE_FONT_NAME_FILE_NAME).delete()
        if (PreferencesManager.getThemeFontFamily(context) == BUNDLE_FONT_ID) {
            PreferencesManager.setThemeFontFamily(context, BuiltInTypographyOptions.first().id)
        }
        refreshBundleFontOption(context)
    }

    /** A font file that [ThemeBundleManager.exportBundle] can pack as `font.ttf`. */
    data class ExportableFont(val file: File, val displayName: String)

    /**
     * The active font if it is a bundle font or a drop-in `.ttf`/`.otf` within the 4 MB cap;
     * null for built-in styles. Reads files, so call off the main thread.
     */
    fun exportableActiveFont(context: Context): ExportableFont? {
        val activeId = PreferencesManager.getThemeFontFamily(context)
        return when {
            activeId == BUNDLE_FONT_ID -> {
                val file = bundleFontFile(context)
                if (isBundleableFontFile(file)) {
                    ExportableFont(file, bundleFontDisplayName(context) ?: BUNDLE_FONT_FALLBACK_NAME)
                } else null
            }
            activeId.startsWith(CUSTOM_ID_PREFIX) -> {
                // Only files the scanner already found; the id is never turned into a path.
                val option = _options.value.firstOrNull { it.id == activeId && it.isExternal }
                val file = option?.file
                if (file != null && isBundleableFontFile(file)) ExportableFont(file, option.name) else null
            }
            else -> null
        }
    }

    /**
     * In-memory check (safe in composition) for whether the export dialog should offer the
     * "Include Custom Font" checkbox. [exportableActiveFont] re-validates the file on export.
     */
    fun canExportFont(fontId: String, options: List<TypographyOption>): Boolean {
        val option = options.firstOrNull { it.id == fontId } ?: return false
        if (fontId == BUNDLE_FONT_ID) return true
        val ext = option.file?.extension?.lowercase()
        return option.isExternal && (ext == "ttf" || ext == "otf")
    }

    private const val CUSTOM_ID_PREFIX = "custom:"

    private fun buildBundleFontOption(context: Context): TypographyOption? {
        val file = bundleFontFile(context)
        if (!file.isFile) return null
        return try {
            val typeface = typefaceLoader(file) ?: return null
            val sizeKb = (file.length() / 1024).coerceAtLeast(1)
            TypographyOption(
                id = BUNDLE_FONT_ID,
                name = bundleFontDisplayName(context) ?: BUNDLE_FONT_FALLBACK_NAME,
                subtitle = "Custom Bundle font ($sizeKb KB)",
                sampleText = "あA 12:00",
                fontFamily = FontFamily(typeface),
                isExternal = false,
                file = file
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading bundle font", e)
            null
        }
    }

    /**
     * Swaps only the bundle entry in [options] (no rescan of external font folders). A new
     * [FontFamily] instance makes [MochibunTheme] and the IME recompose with the new glyphs.
     */
    private fun refreshBundleFontOption(context: Context) {
        synchronized(scanLock) {
            val withoutBundle = _options.value.filterNot { it.id == BUNDLE_FONT_ID }
            val bundleOption = buildBundleFontOption(context)
            _options.value = if (bundleOption == null) {
                withoutBundle
            } else {
                val insertAt = BuiltInTypographyOptions.size.coerceAtMost(withoutBundle.size)
                withoutBundle.take(insertAt) + bundleOption + withoutBundle.drop(insertAt)
            }
        }
    }

    fun getFontsDirectory(context: Context): File {
        val dir = context.getExternalFilesDir(FONTS_DIR_NAME) ?: File(context.filesDir, FONTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /** Schedules [scanFonts] on [Dispatchers.IO] so startup never blocks the main thread. */
    fun scanFontsInBackground(context: Context) {
        val appContext = context.applicationContext
        ioScope.launch {
            scanFonts(appContext)
        }
    }

    /** Suspends on [Dispatchers.IO] while scanning font directories. */
    suspend fun scanFontsAsync(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            scanFonts(appContext)
        }
    }

    fun scanFonts(context: Context) {
        synchronized(scanLock) {
            try {
                val primaryDir = getFontsDirectory(context)
                ensureReadmeExists(primaryDir)

                val directoriesToScan = mutableListOf(primaryDir)

                // Optional secondary external directories if accessible
                try {
                    val mochibunExternal = File(Environment.getExternalStorageDirectory(), "Mochibun/fonts")
                    if (mochibunExternal.exists() && mochibunExternal.isDirectory && mochibunExternal.canRead()) {
                        directoriesToScan.add(mochibunExternal)
                    }
                } catch (_: Exception) {}

                val customList = mutableListOf<TypographyOption>()
                val processedFileNames = mutableSetOf<String>()

                for (dir in directoriesToScan) {
                    val fontFiles = dir.listFiles { f ->
                        val ext = f.extension.lowercase()
                        ext in listOf("ttf", "otf", "ttc")
                    } ?: emptyArray()

                    for (file in fontFiles.sortedBy { it.name.lowercase() }) {
                        if (!processedFileNames.add(file.name.lowercase())) continue
                        try {
                            val typeface = typefaceLoader(file) ?: continue
                            val family = FontFamily(typeface)
                            val cleanName = file.nameWithoutExtension
                                .replace("_", " ")
                                .replace("-", " ")
                            val sizeKb = (file.length() / 1024).coerceAtLeast(1)
                            customList.add(
                                TypographyOption(
                                    id = "$CUSTOM_ID_PREFIX${file.name}",
                                    name = cleanName,
                                    subtitle = "Custom Drop-in ($sizeKb KB)",
                                    sampleText = "あA 12:00",
                                    fontFamily = family,
                                    isExternal = true,
                                    file = file
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed loading font: ${file.name}", e)
                        }
                    }
                }

                _options.value = BuiltInTypographyOptions +
                    listOfNotNull(buildBundleFontOption(context)) +
                    customList
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning custom fonts", e)
            }
        }
    }

    fun importFont(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver
            var fileName: String? = null
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                }
            }
            if (fileName.isNullOrBlank()) {
                fileName = "font_${System.currentTimeMillis()}.ttf"
            }

            val targetDir = getFontsDirectory(context)
            val destFile = File(targetDir, fileName!!)
            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            scanFonts(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import font: $uri", e)
            false
        }
    }

    private fun ensureReadmeExists(fontsDir: File) {
        val readme = File(fontsDir, README_FILE_NAME)
        if (!readme.exists()) {
            readme.writeText(
                """
                # Mochibun Custom Typography Guide
                Drop any Japanese Keitai (ガラケー), pixel, or custom TrueType / OpenType fonts (.ttf, .otf, .ttc) here!
                They will automatically appear in Mochibun's Settings Hub (Appearance -> Typography) and Theme Studio.

                Top Recommended Authentic Japanese Retro Keitai Fonts:
                1. PixelMplus (PixelMplus10 & PixelMplus12)
                   - Quintessential NTT DoCoMo FOMA / au KDDI feature-phone dot matrix UI.
                   - Download: https://itouhiro.hatenablog.com/entry/20130602/font

                2. Misaki Font (美咲フォント - Misaki Gothic & Misaki Mincho)
                   - Legendary 8x8 micro dot-matrix console & Tamagotchi aesthetic with full Kanji support.
                   - Download: https://littlelimit.net/misaki.htm

                3. Nu Kinako Mochi (nuきなこもち)
                   - Rounded dot caps mimicking 2000s Gal/Gyaru Decome (デコメール) phone screens.
                   - Download: https://marusexijaxs.web.fc2.com/

                4. Zpix / Little Limit
                   - Complete CJK pixel font with zero missing character boxes.
                   - Download: https://github.com/SolidZORO/zpix-pixel-font

                5. Chill Round Gothic (マルゴシック)
                   - Sharp AQUOS Keitai rounded mobile interface font.

                Instructions:
                - Copy your .ttf or .otf files into this folder.
                - Open Mochibun Settings -> Appearance -> Typography or Theme Studio to apply!
                """.trimIndent()
            )
        }
    }

    fun resolveFontFamily(id: String): FontFamily {
        return _options.value.firstOrNull { it.id == id }?.fontFamily
            ?: when (id) {
                "default", "maruminyam" -> MaruMinyaMFontFamily
                "clean_sans" -> FontFamily.Default
                "pixel" -> FontFamily.Monospace
                "gothic" -> FontFamily.SansSerif
                "serif" -> FontFamily.Serif
                else -> MaruMinyaMFontFamily
            }
    }
}
