package com.crinklecodone.mochibun.data.repository

import android.content.Context
import android.util.Log
import com.charleskorn.kaml.Yaml
import com.crinklecodone.mochibun.data.model.YamlTheme
import com.crinklecodone.mochibun.data.model.toLauncherPalette
import com.crinklecodone.mochibun.data.model.toLauncherTheme
import com.crinklecodone.mochibun.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Model representing any theme available in the launcher (built-in preset or external YAML theme).
 */
data class ThemeOption(
    val id: String,
    val name: String,
    val author: String = "Mochibun",
    val version: String = "1.0",
    val isExternal: Boolean = false,
    val palette: MochibunThemePalette,
    val theme: LauncherTheme,
    val file: File? = null
)

/**
 * Manages discovery, sample generation, and asynchronous parsing of external YAML themes.
 * Themes are loaded from `/sdcard/Android/data/com.crinklecodone.mochibun/files/themes/`.
 */
object ExternalThemeManager {
    private const val TAG = "ExternalThemeManager"
    private const val THEMES_DIR_NAME = "themes"
    private const val SAMPLE_FILE_NAME = "sample_theme.yaml"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val BuiltInThemeOptions = listOf(
        ThemeOption(
            id = SakuraDreamPalette.id,
            name = SakuraDreamPalette.name,
            author = "Mochibun",
            palette = SakuraDreamPalette,
            theme = SakuraDreamTheme
        ),
        ThemeOption(
            id = LavenderMistPalette.id,
            name = LavenderMistPalette.name,
            author = "Mochibun",
            palette = LavenderMistPalette,
            theme = LavenderMistTheme
        ),
        ThemeOption(
            id = MintyMatchaPalette.id,
            name = MintyMatchaPalette.name,
            author = "Mochibun",
            palette = MintyMatchaPalette,
            theme = MintyMatchaTheme
        ),
        ThemeOption(
            id = MilkyPeachPalette.id,
            name = MilkyPeachPalette.name,
            author = "Mochibun",
            palette = MilkyPeachPalette,
            theme = MilkyPeachTheme
        )
    )

    private val _themes = MutableStateFlow<List<ThemeOption>>(BuiltInThemeOptions)
    val themes: StateFlow<List<ThemeOption>> = _themes.asStateFlow()

    private const val SAMPLE_THEME_YAML = """# Mochibun Custom Theme Template
# Place .yaml / .yml files in:
# /sdcard/Android/data/com.crinklecodone.mochibun/files/themes/

id: "sample_strawberry"
name: "Strawberry Milk"
author: "Mochibun Team"
version: "1.0"

colors:
  primary: "#F48FB1"
  container: "#FCE4EC"
  cardBorder: "#F06292"
  accentDeep: "#880E4F"
  background: "#FFF0F5"
  outline: "#F8BBD0"
  selectionGlow: "#FF80AB"
  textColor: "#4A154B"
  squirclePrimary: "#F48FB1"
  squircleSecondary: "#F8BBD0"
  cloudBack: "#FDE8F0"
  cloudMid: "#FAD2E1"
  cloudFront: "#FFF5F8"
  catOutline: "#880E4F"
  catBlush: "#FF4081"
  mascotTint: "#FFEBF2"

decorations:
  greetingText: "— ♡ Sweet Strawberries ♡ —"
  glowExpansionDp: 4.0
  cardRoundnessDp: 24.0
"""

    /**
     * Ensures the themes directory exists in the app's external files directory.
     */
    fun getThemesDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val themesDir = File(baseDir, THEMES_DIR_NAME)
        if (!themesDir.exists()) {
            themesDir.mkdirs()
        }
        return themesDir
    }

    /**
     * Initializes external theme scanning asynchronously.
     */
    fun init(context: Context) {
        scope.launch {
            scanThemes(context)
        }
    }

    /**
     * Scans and parses all YAML theme files on [Dispatchers.IO].
     */
    suspend fun scanThemes(context: Context): List<ThemeOption> = withContext(Dispatchers.IO) {
        val themesDir = getThemesDirectory(context)
        createSampleThemeIfEmpty(themesDir)

        val yamlFiles = themesDir.listFiles { file ->
            file.isFile && (file.extension.equals("yaml", ignoreCase = true) || file.extension.equals("yml", ignoreCase = true))
        }?.toList().orEmpty()

        val parsedThemes = yamlFiles.mapNotNull { file ->
            try {
                val content = file.readText()
                val yamlTheme = Yaml.default.decodeFromString<YamlTheme>(content)
                val effectiveId = yamlTheme.id.ifBlank { file.nameWithoutExtension }
                val palette = yamlTheme.toLauncherPalette()
                val theme = yamlTheme.toLauncherTheme()

                ThemeOption(
                    id = effectiveId,
                    name = yamlTheme.name.ifBlank { file.nameWithoutExtension },
                    author = yamlTheme.author,
                    version = yamlTheme.version,
                    isExternal = true,
                    palette = palette.copy(id = effectiveId),
                    theme = theme.copy(name = effectiveId),
                    file = file
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse YAML theme from ${file.name}", e)
                null
            }
        }

        val allThemes = BuiltInThemeOptions + parsedThemes
        _themes.value = allThemes
        allThemes
    }

    /**
     * Creates a default sample template file if the themes directory is currently empty.
     */
    private fun createSampleThemeIfEmpty(themesDir: File) {
        val files = themesDir.listFiles { file ->
            file.isFile && (file.extension.equals("yaml", ignoreCase = true) || file.extension.equals("yml", ignoreCase = true))
        }
        if (files.isNullOrEmpty()) {
            val sampleFile = File(themesDir, SAMPLE_FILE_NAME)
            try {
                sampleFile.writeText(SAMPLE_THEME_YAML)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create sample_theme.yaml", e)
            }
        }
    }

    /**
     * Look up a theme option by its unique ID.
     */
    fun findTheme(id: String): ThemeOption? {
        return _themes.value.firstOrNull { it.id == id }
    }

    /**
     * Look up a theme palette by ID (built-in or external).
     */
    fun findPalette(id: String): MochibunThemePalette? {
        return findTheme(id)?.palette
    }
}
