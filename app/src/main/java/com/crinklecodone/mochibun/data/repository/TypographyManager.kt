package com.crinklecodone.mochibun.data.repository

import android.content.Context
import android.graphics.Typeface
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
    private const val FONTS_DIR_NAME = "fonts"
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

    fun scanFonts(context: Context) {
        try {
            val fontsDir = context.getExternalFilesDir(FONTS_DIR_NAME) ?: File(context.filesDir, FONTS_DIR_NAME)
            if (!fontsDir.exists()) {
                fontsDir.mkdirs()
            }

            val readme = File(fontsDir, README_FILE_NAME)
            if (!readme.exists()) {
                readme.writeText(
                    """
                    # Mochibun Custom Typography
                    Place Japanese pixel/bitmap fonts (.ttf, .otf, or .ttc) here!
                    Recommended retro Keitai fonts:
                    - PixelMplus10-Regular.ttf (or PixelMplus12)
                    - misaki_gothic.ttf (Misaki 8x8 bitmap)
                    - ChillRoundGothic.ttf
                    Fonts placed in this folder are automatically recognized by Mochibun's Theme Studio.
                    """.trimIndent()
                )
            }

            val customList = mutableListOf<TypographyOption>()
            val fontFiles = fontsDir.listFiles { f ->
                val ext = f.extension.lowercase()
                ext in listOf("ttf", "otf", "ttc")
            } ?: emptyArray()

            for (file in fontFiles) {
                try {
                    val typeface = Typeface.createFromFile(file)
                    val family = FontFamily(typeface)
                    val cleanName = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
                    customList.add(
                        TypographyOption(
                            id = "custom:${file.name}",
                            name = cleanName,
                            subtitle = "Custom Font (${file.length() / 1024} KB)",
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

            _options.value = BuiltInTypographyOptions + customList
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning custom fonts", e)
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
