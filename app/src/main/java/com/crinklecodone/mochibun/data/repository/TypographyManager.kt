package com.crinklecodone.mochibun.data.repository

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * `/sdcard/Android/data/com.crinklecodone.mochibun/files/fonts/`.
 */
object TypographyManager {
    private const val TAG = "TypographyManager"
    const val FONTS_DIR_NAME = "fonts"
    private const val README_FILE_NAME = "README.txt"

    val BuiltInTypographyOptions = listOf(
        TypographyOption(
            id = "default",
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

    fun getFontsDirectory(context: Context): File {
        val dir = context.getExternalFilesDir(FONTS_DIR_NAME) ?: File(context.filesDir, FONTS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun scanFonts(context: Context) {
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
                        val typeface = Typeface.createFromFile(file)
                        val family = FontFamily(typeface)
                        val cleanName = file.nameWithoutExtension
                            .replace("_", " ")
                            .replace("-", " ")
                        val sizeKb = (file.length() / 1024).coerceAtLeast(1)
                        customList.add(
                            TypographyOption(
                                id = "custom:${file.name}",
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

            _options.value = BuiltInTypographyOptions + customList
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning custom fonts", e)
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
                "pixel" -> FontFamily.Monospace
                "gothic" -> FontFamily.SansSerif
                "serif" -> FontFamily.Serif
                else -> FontFamily.Default
            }
    }
}
