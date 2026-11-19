package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.util.calculateInSampleSize
import one.crinklecod.mochibun.core.util.downsampleAndSaveImage
import one.crinklecod.mochibun.data.model.BundleCursorConfig
import one.crinklecod.mochibun.data.model.BundleGreetingConfig
import one.crinklecod.mochibun.data.model.BundlePaletteConfig
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.model.MascotAtlasValidator
import one.crinklecod.mochibun.data.model.MochibunBundleManifest
import one.crinklecod.mochibun.data.model.bundleJsonFormat
import one.crinklecod.mochibun.data.model.colorIntToHex
import one.crinklecod.mochibun.data.model.safeParseColorInt
import one.crinklecod.mochibun.data.prefs.ClickMethod
import one.crinklecod.mochibun.data.prefs.CursorPreferences
import one.crinklecod.mochibun.data.prefs.HotspotAlignment
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import kotlinx.serialization.encodeToString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Preview inspection metadata and thumbnails extracted from a `.mochibun` bundle.
 */
data class BundlePreviewData(
    val manifest: MochibunBundleManifest,
    val wallpaperPreview: Bitmap? = null,
    val hasCursorAsset: Boolean = false,
    /** A `mascot_atlas.png` within the 1.5 MB limit that is a PNG (fully validated on apply). */
    val hasMascotAsset: Boolean = false,
    /** A `card_border.png` within the 512 KB limit that is a PNG (fully validated on apply). */
    val hasCardBorderAsset: Boolean = false,
    val mascotPreview: Bitmap? = null,
    val cardBorderPreview: Bitmap? = null,
    /** A `font.ttf` / `font.otf` within 4 MB with a TrueType/OpenType header (Typeface-checked on apply). */
    val hasFontAsset: Boolean = false,
    /** Number of `sounds/<cue>.ogg` entries within 256 KB each with an `OggS` header. */
    val soundCueCount: Int = 0
) {
    val hasSoundPackAsset: Boolean
        get() = soundCueCount > 0

    val hasCustomMascot: Boolean
        get() = hasMascotAsset

    val hasCustomMascotDialogue: Boolean
        get() = (hasMascotAsset || manifest.hasCustomMascot) &&
            MascotAtlasValidator.sanitizeMascotDialogue(manifest.mascot?.dialogue) != null

    val hasCustomCardBorder: Boolean
        get() = hasCardBorderAsset

    val borderStyle: CardBorderStyle
        get() = CardBorderStyle.fromId(manifest.palette.borderStyle)
}

/**
 * Core engine for exporting and importing `.mochibun` Theme & Setup preset archives.
 * Streaming ZIP compression/decompression ensures strict compliance with low-memory (~1.8GB RAM) limits.
 */
object ThemeBundleManager {

    private const val TAG = "ThemeBundleManager"
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val WALLPAPER_ENTRY = "wallpaper.png"
    private const val CURSOR_GIF_ENTRY = "cursor.gif"
    private const val CURSOR_PNG_ENTRY = "cursor.png"
    /** Always this exact entry name; a manifest never names or locates the atlas. */
    const val MASCOT_ENTRY = "mascot_atlas.png"
    /** Always this exact entry name for custom 9-slice card border frames. */
    const val CARD_BORDER_ENTRY = "card_border.png"
    /** Optional bundled font; [FONT_OTF_ENTRY] is accepted on import, exports always write this. */
    const val FONT_ENTRY = "font.ttf"
    const val FONT_OTF_ENTRY = "font.otf"
    /** Sound cues live at `sounds/<cue>.ogg`, cue names from [SoundEffectManager.BundleCueFileNames]. */
    const val SOUNDS_ENTRY_PREFIX = "sounds/"

    private const val MAX_WALLPAPER_WIDTH = 480
    private const val MAX_WALLPAPER_HEIGHT = 800

    const val MAX_MANIFEST_BYTES = 512L * 1024L
    const val MAX_WALLPAPER_BYTES = 5L * 1024L * 1024L
    const val MAX_CURSOR_BYTES = 2L * 1024L * 1024L
    const val MAX_CARD_BORDER_BYTES = CustomCardBorderManager.MAX_BORDER_BYTES
    const val MAX_TOTAL_BUNDLE_BYTES = 8L * 1024L * 1024L
    const val MAX_FONT_BYTES = TypographyManager.MAX_BUNDLE_FONT_BYTES
    const val MAX_SOUND_BYTES = SoundEffectManager.MAX_BUNDLE_SOUND_BYTES

    /**
     * ZIP-slip guard: names that are absolute, contain `..` segments, backslashes, drive
     * letters or NULs are never treated as assets (they are drained and ignored). Assets are
     * matched by exact name and written to fixed app paths, so an entry name never becomes a
     * file path either way.
     */
    internal fun isUnsafeEntryName(name: String): Boolean =
        name.isEmpty() ||
            name.startsWith("/") ||
            name.contains('\\') ||
            name.contains(':') ||
            name.contains('\u0000') ||
            name.split('/').any { it == ".." }

    /** The sound cue for an exact `sounds/<cue>.ogg` entry, or null for anything else. */
    fun soundEffectForEntry(name: String): SoundEffect? {
        if (isUnsafeEntryName(name) || !name.startsWith(SOUNDS_ENTRY_PREFIX)) return null
        val fileName = name.removePrefix(SOUNDS_ENTRY_PREFIX)
        if (fileName.isEmpty() || fileName.contains('/')) return null
        return SoundEffectManager.effectForBundleCueFileName(fileName)
    }

    private val DISCARD_OUTPUT_STREAM = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }

    // Default pastel theme color fallbacks
    const val DEFAULT_FALLBACK_BG_INT = 0xFFFFF5F8.toInt()
    const val DEFAULT_FALLBACK_CARD_INT = 0xFFFCE4EC.toInt()
    const val DEFAULT_FALLBACK_GREETING_INT = 0xFFA04064.toInt()
    const val DEFAULT_FALLBACK_ACTION_INT = 0xFFA04064.toInt()

    fun generateBundleFileName(bundleName: String): String {
        val sanitized = bundleName.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_-]"), "_")
            .ifBlank { "mochibun_theme" }
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        return "${sanitized}_${dateFormat.format(Date())}.mochibundle"
    }

    /**
     * Exports active theme, visual tokens, cursor physics, and optional assets to a .mochibun ZIP archive.
     */
    fun exportBundle(
        context: Context,
        outputStream: OutputStream,
        bundleName: String,
        author: String = "Mochi User",
        includeWallpaper: Boolean = true,
        includeCustomCursor: Boolean = true,
        includeDisplayName: Boolean = true,
        includeCustomMascot: Boolean = true,
        includeCustomCardBorder: Boolean = true,
        includeFont: Boolean = false,
        includeSoundPack: Boolean = false
    ): Result<Unit> {
        return try {
            val launcherPrefs = PreferencesManager.getPrefs(context)
            val currentPreset = PreferencesManager.getThemePreset(context)

            // 1. Resolve active theme colors & border style
            val currentTheme = one.crinklecod.mochibun.ui.theme.loadSavedTheme(launcherPrefs)
            val bgColorHex = colorIntToHex(currentTheme.bgColor.toArgb())
            val cardColorHex = colorIntToHex(currentTheme.cardBgColor.toArgb())
            val greetingColorHex = colorIntToHex(currentTheme.greetingColor.toArgb())
            val actionColorHex = colorIntToHex(currentTheme.appActionColor.toArgb())
            val outlineColorHex = colorIntToHex(currentTheme.greetingOutlineColor.toArgb())
            val textColorHex = colorIntToHex(currentTheme.fgTextColor.toArgb())
            val fontFamilyId = PreferencesManager.getThemeFontFamily(context)
            val cardRoundness = currentTheme.cardRoundnessDp
            val borderStyleId = CardBorderStyle.normalizeId(currentTheme.borderStyle)

            // 2. Check wallpaper, cursor, mascot, and custom card border availability
            val activeWallpaperUriStr = PreferencesManager.getWallpaperUri(context)
            val activeWallpaperUri = if (includeWallpaper && !activeWallpaperUriStr.isNullOrBlank()) {
                Uri.parse(activeWallpaperUriStr)
            } else null

            val activeCursorUri = if (includeCustomCursor) {
                CursorPreferences.getCustomCursorUri(context)
            } else null

            val mascotConfig = if (includeCustomMascot) CustomMascotManager.activeConfig(context) else null

            val customBorderFile = CustomCardBorderManager.borderFile(context)
            val hasCustomBorderToBundle = includeCustomCardBorder &&
                (CustomCardBorderManager.isCustomBorderActive() || customBorderFile.isFile) &&
                customBorderFile.isFile
            val customBorderSlicePx = if (hasCustomBorderToBundle) {
                CustomCardBorderManager.activeSlicePx(context)
            } else {
                CardBorderStyle.DEFAULT_SLICE_PX
            }

            // Active bundle / drop-in font (always packed as font.ttf) and installed sound pack
            val fontToBundle = if (includeFont) TypographyManager.exportableActiveFont(context) else null
            val soundsToBundle = if (includeSoundPack) SoundEffectManager.bundleSoundFiles(context) else emptyMap()
            val soundPackName = if (soundsToBundle.isNotEmpty()) {
                SoundEffectManager.refreshBundleSoundPackState(context)
                SoundEffectManager.bundleSoundPackName.value
            } else null

            val manifest = MochibunBundleManifest(
                bundleVersion = 1,
                bundleName = bundleName.ifBlank { "Kawaii Setup" },
                author = author.ifBlank { "Mochi User" },
                createdAtEpochSec = System.currentTimeMillis() / 1000L,
                greeting = BundleGreetingConfig(
                    userName = if (includeDisplayName) PreferencesManager.getCustomUserName(context) else "Mochi User",
                    showSparkles = true
                ),
                palette = BundlePaletteConfig(
                    activePresetId = currentPreset,
                    isCustom = true,
                    bgColorHex = bgColorHex,
                    cardColorHex = cardColorHex,
                    greetingColorHex = greetingColorHex,
                    actionColorHex = actionColorHex,
                    cardRoundnessDp = cardRoundness,
                    borderStyle = borderStyleId,
                    hasCustomCardBorder = hasCustomBorderToBundle,
                    cardBorderSlicePx = customBorderSlicePx,
                    outlineColorHex = outlineColorHex,
                    textColorHex = textColorHex,
                    // A packed font installs as the "bundle" style on the receiving phone
                    fontFamilyId = if (fontToBundle != null) TypographyManager.BUNDLE_FONT_ID else fontFamilyId
                ),
                cursor = BundleCursorConfig(
                    baseVelocity = CursorPreferences.getMoveSpeed(context),
                    accelerationMulti = CursorPreferences.getAcceleration(context),
                    pointerSizeDp = CursorPreferences.getCursorSizeDp(context).toFloat(),
                    clickMethod = CursorPreferences.getClickMethod(context).name,
                    hotspot = CursorPreferences.getHotspotAlignment(context).name,
                    tapStepPx = CursorPreferences.getTapStepPx(context),
                    maxSpeedPxPerSec = CursorPreferences.getMaxSpeed(context),
                    accelRampMs = CursorPreferences.getAccelRampMs(context)
                ),
                hasWallpaper = activeWallpaperUri != null,
                hasCustomCursor = activeCursorUri != null,
                hasCustomMascot = mascotConfig != null,
                mascot = mascotConfig,
                hasFont = fontToBundle != null,
                fontDisplayName = fontToBundle?.displayName,
                hasSoundPack = soundsToBundle.isNotEmpty(),
                soundPackName = soundPackName
            )

            // 3. Stream directly to ZipOutputStream
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // Write manifest.json
                val manifestBytes = bundleJsonFormat.encodeToString(manifest).toByteArray(Charsets.UTF_8)
                zipOut.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zipOut.write(manifestBytes)
                zipOut.closeEntry()

                // Stream wallpaper without loading uncompressed full bitmap into memory
                if (activeWallpaperUri != null) {
                    try {
                        context.contentResolver.openInputStream(activeWallpaperUri)?.use { wallIn ->
                            zipOut.putNextEntry(ZipEntry(WALLPAPER_ENTRY))
                            wallIn.copyTo(zipOut, bufferSize = 8192)
                            zipOut.closeEntry()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to bundle wallpaper", e)
                    }
                }

                // Stream cursor asset
                if (activeCursorUri != null) {
                    try {
                        context.contentResolver.openInputStream(activeCursorUri)?.use { cursorIn ->
                            val entryName = if (activeCursorUri.toString().endsWith(".png", ignoreCase = true)) {
                                CURSOR_PNG_ENTRY
                            } else {
                                CURSOR_GIF_ENTRY
                            }
                            zipOut.putNextEntry(ZipEntry(entryName))
                            cursorIn.copyTo(zipOut, bufferSize = 8192)
                            zipOut.closeEntry()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to bundle cursor", e)
                    }
                }

                // Custom mascot atlas: always the fixed entry name, copied from the fixed file
                if (mascotConfig != null) {
                    try {
                        CustomMascotManager.atlasFile(context).inputStream().use { atlasIn ->
                            zipOut.putNextEntry(ZipEntry(MASCOT_ENTRY))
                            atlasIn.copyTo(zipOut, bufferSize = 8192)
                            zipOut.closeEntry()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to bundle mascot atlas", e)
                    }
                }

                // Custom 9-slice card border PNG: always the fixed entry name "card_border.png"
                if (hasCustomBorderToBundle) {
                    try {
                        customBorderFile.inputStream().use { borderIn ->
                            zipOut.putNextEntry(ZipEntry(CARD_BORDER_ENTRY))
                            borderIn.copyTo(zipOut, bufferSize = 8192)
                            zipOut.closeEntry()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to bundle custom card border", e)
                    }
                }

                if (fontToBundle != null) {
                    try {
                        fontToBundle.file.inputStream().use { fontIn ->
                            zipOut.putNextEntry(ZipEntry(FONT_ENTRY))
                            fontIn.copyTo(zipOut, bufferSize = 8192)
                            zipOut.closeEntry()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to bundle custom font", e)
                    }
                }

                soundsToBundle.forEach { (effect, file) ->
                    try {
                        file.inputStream().use { soundIn ->
                            zipOut.putNextEntry(
                                ZipEntry(SOUNDS_ENTRY_PREFIX + SoundEffectManager.bundleCueFileName(effect))
                            )
                            soundIn.copyTo(zipOut, bufferSize = 8192)
                            zipOut.closeEntry()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Failed to bundle sound ${effect.name}", e)
                    }
                }

                zipOut.finish()
                zipOut.flush()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Inspects a .mochibun bundle without saving files to disk, returning the manifest and asset thumbnails.
     */
    fun readBundleManifest(inputStream: InputStream): Result<BundlePreviewData> {
        return try {
            val bufferedIn = BufferedInputStream(inputStream)
            val totalBytes = AtomicLong()

            var manifest: MochibunBundleManifest? = null
            var wallpaperBitmap: Bitmap? = null
            var hasCursor = false
            var hasMascot = false
            var hasCardBorder = false
            var mascotBytes: ByteArray? = null
            var cardBorderBytes: ByteArray? = null
            var hasFont = false
            val soundCues = mutableSetOf<SoundEffect>()

            ZipInputStream(bufferedIn).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> {
                            val text = readBytesBounded(zipIn, MAX_MANIFEST_BYTES, totalBytes)
                                .toString(Charsets.UTF_8)
                            manifest = bundleJsonFormat.decodeFromString<MochibunBundleManifest>(text)
                        }
                        WALLPAPER_ENTRY -> {
                            val wallBytes = readBytesBounded(zipIn, MAX_WALLPAPER_BYTES, totalBytes)
                            val opts = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeByteArray(wallBytes, 0, wallBytes.size, opts)
                            val sample = calculateInSampleSize(opts, 120, 200)
                            val decodeOpts = BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPreferredConfig = Bitmap.Config.RGB_565 // Conserves graphics memory on MT6761
                            }
                            wallpaperBitmap = BitmapFactory.decodeByteArray(wallBytes, 0, wallBytes.size, decodeOpts)
                        }
                        CURSOR_GIF_ENTRY, CURSOR_PNG_ENTRY -> {
                            copyBounded(zipIn, DISCARD_OUTPUT_STREAM, MAX_CURSOR_BYTES, totalBytes)
                            hasCursor = true
                        }
                        MASCOT_ENTRY -> {
                            val captured = ByteArrayOutputStream()
                            val withinLimit = readMascotEntry(zipIn, captured, totalBytes)
                            if (withinLimit) {
                                val bytes = captured.toByteArray()
                                if (MascotAtlasValidator.readPngSize(bytes) != null) {
                                    hasMascot = true
                                    mascotBytes = bytes
                                }
                            }
                        }
                        CARD_BORDER_ENTRY -> {
                            val captured = ByteArrayOutputStream()
                            val withinLimit = readCardBorderEntry(zipIn, captured, totalBytes)
                            if (withinLimit) {
                                val bytes = captured.toByteArray()
                                val pngSize = MascotAtlasValidator.readPngSize(bytes)
                                if (pngSize != null && CustomCardBorderManager.validateDimensions(
                                        pngSize.first,
                                        pngSize.second,
                                        CardBorderStyle.MIN_SLICE_PX
                                    ).isSuccess
                                ) {
                                    hasCardBorder = true
                                    cardBorderBytes = bytes
                                }
                            }
                        }
                        FONT_ENTRY, FONT_OTF_ENTRY -> {
                            val header = readEntryHeader(zipIn, MAX_FONT_BYTES, totalBytes)
                            hasFont = header != null && TypographyManager.isSfntHeader(header)
                        }
                        else -> {
                            val soundEffect = soundEffectForEntry(entry.name)
                            if (soundEffect != null) {
                                val header = readEntryHeader(zipIn, MAX_SOUND_BYTES, totalBytes)
                                if (header != null && SoundEffectManager.isOggHeader(header)) {
                                    soundCues.add(soundEffect)
                                }
                            } else {
                                copyBounded(zipIn, DISCARD_OUTPUT_STREAM, MAX_TOTAL_BUNDLE_BYTES, totalBytes)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            val parsedManifest = manifest
            if (parsedManifest == null) {
                Result.failure(IllegalArgumentException("Invalid .mochibundle bundle: missing manifest.json"))
            } else {
                val mascotPreview = if (hasMascot) {
                    mascotBytes?.let { decodeMascotFirstFramePreview(it, parsedManifest) }
                } else null
                val cardBorderPreview = if (hasCardBorder) {
                    cardBorderBytes?.let { decodeBoundedThumbnail(it) }
                } else null
                Result.success(
                    BundlePreviewData(
                        manifest = parsedManifest,
                        wallpaperPreview = wallpaperBitmap,
                        hasCursorAsset = hasCursor,
                        hasMascotAsset = hasMascot,
                        hasCardBorderAsset = hasCardBorder,
                        mascotPreview = mascotPreview,
                        cardBorderPreview = cardBorderPreview,
                        hasFontAsset = hasFont,
                        soundCueCount = soundCues.size
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts and applies all settings, palette tokens, and assets from a .mochibun bundle.
     *
     * [onMascotSkipped] gets a short, friendly reason when the bundle has a mascot atlas or custom
     * card border that was not applied (too big or failed validation); the rest of the bundle still applies.
     */
    fun applyBundle(
        context: Context,
        inputStream: InputStream,
        clearUnbundledAssets: Boolean = false,
        onMascotSkipped: (String) -> Unit = {}
    ): Result<MochibunBundleManifest> {
        val stagedFiles = mutableListOf<File>()
        return try {
            var manifest: MochibunBundleManifest? = null
            var extractedWallpaperFile: File? = null
            var extractedCursorFile: File? = null
            var stagedWallpaperFile: File? = null
            var stagedCursorFile: File? = null
            var stagedMascotFile: File? = null
            var mascotWithinLimit = false
            var stagedCardBorderFile: File? = null
            var cardBorderWithinLimit = false
            var stagedFontFile: File? = null
            var fontWithinLimit = false
            val stagedSounds = mutableMapOf<SoundEffect, File>()
            var oversizedSoundCount = 0
            val totalBytes = AtomicLong()

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> {
                            val text = readBytesBounded(zipIn, MAX_MANIFEST_BYTES, totalBytes)
                                .toString(Charsets.UTF_8)
                            manifest = bundleJsonFormat.decodeFromString<MochibunBundleManifest>(text)
                        }
                        WALLPAPER_ENTRY -> {
                            val tempFile = File.createTempFile(
                                "theme_bundle_wallpaper_",
                                ".tmp",
                                context.cacheDir
                            )
                            stagedFiles.add(tempFile)
                            stagedWallpaperFile = tempFile
                            FileOutputStream(tempFile).use { out ->
                                copyBounded(zipIn, out, MAX_WALLPAPER_BYTES, totalBytes)
                            }
                            extractedWallpaperFile = File(context.filesDir, "bundle_wallpaper.png")
                        }
                        CURSOR_GIF_ENTRY -> {
                            val cursorFile = File.createTempFile(
                                "theme_bundle_cursor_",
                                ".gif",
                                context.cacheDir
                            )
                            stagedFiles.add(cursorFile)
                            stagedCursorFile = cursorFile
                            FileOutputStream(cursorFile).use { out ->
                                copyBounded(zipIn, out, MAX_CURSOR_BYTES, totalBytes)
                            }
                            extractedCursorFile = File(context.filesDir, "bundle_cursor.gif")
                        }
                        CURSOR_PNG_ENTRY -> {
                            val cursorFile = File.createTempFile(
                                "theme_bundle_cursor_",
                                ".png",
                                context.cacheDir
                            )
                            stagedFiles.add(cursorFile)
                            stagedCursorFile = cursorFile
                            FileOutputStream(cursorFile).use { out ->
                                copyBounded(zipIn, out, MAX_CURSOR_BYTES, totalBytes)
                            }
                            extractedCursorFile = File(context.filesDir, "bundle_cursor.png")
                        }
                        MASCOT_ENTRY -> {
                            val mascotFile = File.createTempFile("theme_bundle_mascot_", ".png", context.cacheDir)
                            stagedFiles.add(mascotFile)
                            stagedMascotFile = mascotFile
                            mascotWithinLimit = FileOutputStream(mascotFile).use { out ->
                                readMascotEntry(zipIn, out, totalBytes)
                            }
                        }
                        CARD_BORDER_ENTRY -> {
                            val borderFile = File.createTempFile("theme_bundle_border_", ".png", context.cacheDir)
                            stagedFiles.add(borderFile)
                            stagedCardBorderFile = borderFile
                            cardBorderWithinLimit = FileOutputStream(borderFile).use { out ->
                                readCardBorderEntry(zipIn, out, totalBytes)
                            }
                        }
                        FONT_ENTRY, FONT_OTF_ENTRY -> {
                            val fontFile = File.createTempFile("theme_bundle_font_", ".ttf", context.cacheDir)
                            stagedFiles.add(fontFile)
                            stagedFontFile = fontFile
                            fontWithinLimit = FileOutputStream(fontFile).use { out ->
                                readLimitedEntry(zipIn, out, MAX_FONT_BYTES, totalBytes)
                            }
                        }
                        else -> {
                            val soundEffect = soundEffectForEntry(entry.name)
                            if (soundEffect != null) {
                                val soundFile = File.createTempFile("theme_bundle_sound_", ".ogg", context.cacheDir)
                                stagedFiles.add(soundFile)
                                val withinLimit = FileOutputStream(soundFile).use { out ->
                                    readLimitedEntry(zipIn, out, MAX_SOUND_BYTES, totalBytes)
                                }
                                if (withinLimit) stagedSounds[soundEffect] = soundFile else oversizedSoundCount++
                            } else {
                                copyBounded(zipIn, DISCARD_OUTPUT_STREAM, MAX_TOTAL_BUNDLE_BYTES, totalBytes)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            val targetManifest = manifest ?: return Result.failure(
                IllegalArgumentException("Bundle is missing manifest.json")
            )

            extractedWallpaperFile?.let { destination ->
                val wallpaperTemp = checkNotNull(stagedWallpaperFile)
                try {
                    downsampleAndSaveImage(
                        wallpaperTemp,
                        destination,
                        MAX_WALLPAPER_WIDTH,
                        MAX_WALLPAPER_HEIGHT
                    )
                } catch (e: Exception) {
                    destination.delete()
                    throw e
                }
            }

            extractedCursorFile?.let { destination ->
                val cursorTemp = checkNotNull(stagedCursorFile)
                try {
                    cursorTemp.copyTo(destination, overwrite = true)
                } catch (e: Exception) {
                    destination.delete()
                    throw e
                }
            }

            // Bundled font: installed (and selected) only after Typeface validation; a bad font is
            // skipped and the rest of the bundle still applies.
            var installedBundleFont = false
            stagedFontFile?.let { staged ->
                if (!fontWithinLimit) {
                    onMascotSkipped("Custom font skipped: bigger than 4 MB")
                } else {
                    TypographyManager.installBundleFont(context, staged, targetManifest.fontDisplayName)
                        .onSuccess { installedBundleFont = true }
                        .onFailure { onMascotSkipped("Custom font skipped: ${it.message}") }
                }
            }

            // 1. Apply Palette & Colors
            val launcherPrefs = PreferencesManager.getPrefs(context)
            val editor = launcherPrefs.edit()

            PreferencesManager.setThemePreset(context, targetManifest.palette.activePresetId)

            val p = targetManifest.palette
            val bgInt = hexToColorInt(p.bgColorHex, DEFAULT_FALLBACK_BG_INT)
            val cardInt = hexToColorInt(p.cardColorHex, DEFAULT_FALLBACK_CARD_INT)
            val greetingInt = hexToColorInt(p.greetingColorHex, DEFAULT_FALLBACK_GREETING_INT)
            val actionInt = hexToColorInt(p.actionColorHex, DEFAULT_FALLBACK_ACTION_INT)
            val textInt = p.textColorHex?.let { safeParseColorInt(it, greetingInt) } ?: greetingInt
            val outlineInt = p.outlineColorHex?.let { safeParseColorInt(it, cardInt) } ?: cardInt
            val resolvedBorderStyle = CardBorderStyle.fromId(p.borderStyle)

            editor.putInt(PreferencesManager.KEY_THEME_BG_COLOR, bgInt)
            editor.putInt(PreferencesManager.KEY_THEME_FG_TEXT_COLOR, textInt)
            editor.putInt(PreferencesManager.KEY_THEME_CARD_BG_COLOR, cardInt)
            editor.putInt(PreferencesManager.KEY_THEME_CARD_TEXT_COLOR, textInt)
            editor.putInt(PreferencesManager.KEY_THEME_GREETING_COLOR, greetingInt)
            editor.putInt(PreferencesManager.KEY_THEME_GREETING_OUTLINE_COLOR, outlineInt)
            editor.putInt(PreferencesManager.KEY_THEME_GREETING_BG_COLOR, bgInt)
            editor.putInt(PreferencesManager.KEY_THEME_APP_ACTION_COLOR, actionInt)
            editor.putFloat(PreferencesManager.KEY_THEME_CARD_ROUNDNESS, p.cardRoundnessDp)
            editor.putString(PreferencesManager.KEY_THEME_BORDER_STYLE, resolvedBorderStyle.id)
            CustomCardBorderManager.setBorderStyle(context, resolvedBorderStyle)

            if (installedBundleFont) {
                PreferencesManager.setThemeFontFamily(context, TypographyManager.BUNDLE_FONT_ID)
            } else {
                // "bundle" without a font in this archive would select whatever font an earlier
                // bundle left behind, so it is ignored here.
                p.fontFamilyId
                    ?.takeIf { it.isNotBlank() && it != TypographyManager.BUNDLE_FONT_ID }
                    ?.let { requestedFontId ->
                        if (TypographyManager.options.value.any { it.id == requestedFontId }) {
                            PreferencesManager.setThemeFontFamily(context, requestedFontId)
                        }
                    }
            }

            // 2. Apply Greeting
            if (targetManifest.greeting.userName.isNotBlank() &&
                targetManifest.greeting.userName != "Mochi User"
            ) {
                PreferencesManager.setCustomUserName(context, targetManifest.greeting.userName)
            }

            // 3. Apply Cursor Physics & Key Controls
            val c = targetManifest.cursor
            CursorPreferences.setMoveSpeed(context, c.baseVelocity)
            CursorPreferences.setAcceleration(context, c.accelerationMulti)
            c.tapStepPx?.let { CursorPreferences.setTapStepPx(context, it) }
            c.maxSpeedPxPerSec?.let { CursorPreferences.setMaxSpeed(context, it) }
            c.accelRampMs?.let { CursorPreferences.setAccelRampMs(context, it) }
            CursorPreferences.setCursorSizeDp(context, c.pointerSizeDp.toInt())
            try {
                CursorPreferences.setClickMethod(context, ClickMethod.valueOf(c.clickMethod))
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Unknown click method: ${c.clickMethod}", e)
            }
            try {
                CursorPreferences.setHotspotAlignment(context, HotspotAlignment.valueOf(c.hotspot))
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Unknown hotspot alignment: ${c.hotspot}", e)
            }

            // 4. Set Assets if present (or clear unbundled ones if requested)
            if (extractedWallpaperFile != null && extractedWallpaperFile.exists()) {
                val wallUri = Uri.fromFile(extractedWallpaperFile)
                PreferencesManager.setWallpaperUri(context, wallUri.toString())
            } else if (clearUnbundledAssets) {
                PreferencesManager.setWallpaperUri(context, null)
                File(context.filesDir, "bundle_wallpaper.png").delete()
            }

            if (extractedCursorFile != null && extractedCursorFile.exists()) {
                val curUri = Uri.fromFile(extractedCursorFile)
                CursorPreferences.setCustomCursorUri(context, curUri)
            } else if (clearUnbundledAssets) {
                CursorPreferences.setCustomCursorUri(context, null)
                File(context.filesDir, "bundle_cursor.png").delete()
                File(context.filesDir, "bundle_cursor.gif").delete()
            }

            // Custom mascot: validated against the manifest's layout (or inferred). A bad or
            // oversized atlas is skipped and the current mascot is kept.
            val stagedMascot = stagedMascotFile
            if (stagedMascot != null) {
                if (!mascotWithinLimit) {
                    onMascotSkipped("Custom mascot skipped: bigger than 1.5 MB")
                } else {
                    CustomMascotManager.installStaged(context, stagedMascot, targetManifest.mascot)
                        .onFailure { onMascotSkipped("Custom mascot skipped: ${it.message}") }
                }
            } else if (clearUnbundledAssets) {
                CustomMascotManager.reset(context)
            }

            // Custom 9-slice card border:
            // - If the bundle includes a staged `card_border.png` (and hasn't explicitly disabled it
            //   while missing an entry), validate and install it; if invalid/oversized, clear any
            //   stale custom border and fall back to procedural `borderStyle`.
            // - If `hasCustomCardBorder` is false and no `card_border.png` entry is present, clear
            //   any previously active custom PNG so the bundle's procedural `borderStyle` shows.
            val stagedBorder = stagedCardBorderFile
            if (stagedBorder != null) {
                if (!cardBorderWithinLimit) {
                    CustomCardBorderManager.resetCustomBorder(context)
                    onMascotSkipped("Custom card border skipped: bigger than 512 KB")
                } else {
                    CustomCardBorderManager.installStaged(
                        context = context,
                        staged = stagedBorder,
                        requestedSlicePx = p.cardBorderSlicePx,
                        autoAdjustSliceForSmallImage = false
                    ).onFailure {
                        CustomCardBorderManager.resetCustomBorder(context)
                        onMascotSkipped("Custom card border skipped: ${it.message}")
                    }
                }
            } else if (!p.hasCustomCardBorder) {
                CustomCardBorderManager.resetCustomBorder(context)
            } else {
                // Manifest claimed hasCustomCardBorder = true, but card_border.png was missing
                CustomCardBorderManager.resetCustomBorder(context)
            }

            // Bundled sound pack: replaces any earlier pack. Selected as the keypad profile
            // unless keypad sounds are muted (importing a theme shouldn't unmute the phone).
            if (stagedSounds.isNotEmpty()) {
                SoundEffectManager.installBundleSounds(
                    context,
                    stagedSounds,
                    targetManifest.soundPackName ?: targetManifest.bundleName
                ).onSuccess {
                    if (PreferencesManager.getKeypadSoundMode(context) != "off") {
                        PreferencesManager.setKeypadSoundMode(context, SoundEffectManager.BUNDLE_THEME_ID)
                    }
                }.onFailure { onMascotSkipped("Custom sounds skipped: ${it.message}") }
            }
            if (oversizedSoundCount > 0) {
                onMascotSkipped("$oversizedSoundCount sound(s) skipped: bigger than 256 KB")
            }

            editor.apply()
            PreferencesManager.init(context)
            one.crinklecod.mochibun.ui.theme.SystemUiThemeBridge.broadcastSavedTheme(context)

            Result.success(targetManifest)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            stagedFiles.forEach { stagedFile ->
                if (stagedFile.exists() && !stagedFile.delete()) {
                    android.util.Log.w(TAG, "Failed to delete staged bundle file: ${stagedFile.name}")
                }
            }
        }
    }

    /**
     * Reads the mascot entry into [output] up to [MascotAtlasValidator.MAX_ATLAS_BYTES], counting
     * real bytes (never `ZipEntry.size`). Returns false when the entry is larger: the rest is then
     * drained under the whole-bundle limit, so only the mascot is rejected, not the bundle.
     */
    private fun readMascotEntry(input: InputStream, output: OutputStream, totalCounter: AtomicLong): Boolean =
        readLimitedEntry(input, output, MascotAtlasValidator.MAX_ATLAS_BYTES, totalCounter)

    /**
     * Streams an entry into [output] up to [maxBytes], counting real bytes (never trusting
     * `ZipEntry.size`). An oversized entry is drained under the whole-bundle limit and false is
     * returned, so only that asset is rejected, not the bundle.
     */
    private fun readLimitedEntry(
        input: InputStream,
        output: OutputStream,
        maxBytes: Long,
        totalCounter: AtomicLong
    ): Boolean {
        val counting = BundleByteCounter(input, totalCounter)
        val withinLimit = MascotAtlasValidator.copyAtMost(counting, output, maxBytes)
        if (!withinLimit) copyBounded(input, DISCARD_OUTPUT_STREAM, MAX_TOTAL_BUNDLE_BYTES, totalCounter)
        return withinLimit
    }

    /**
     * Preview helper: keeps only the first 16 bytes of an entry (for magic-number checks) while
     * counting the rest, so inspecting a 4 MB font never buffers it. Null when over [maxBytes].
     */
    private fun readEntryHeader(input: InputStream, maxBytes: Long, totalCounter: AtomicLong): ByteArray? {
        val header = HeaderCaptureOutputStream(16)
        return if (readLimitedEntry(input, header, maxBytes, totalCounter)) header.bytes() else null
    }

    /**
     * Reads `card_border.png` into [output] up to [MAX_CARD_BORDER_BYTES] (512 KB), counting real
     * bytes. If oversized, drains the entry under the whole-bundle limit and returns false so the
     * rest of the bundle still imports cleanly.
     */
    private fun readCardBorderEntry(input: InputStream, output: OutputStream, totalCounter: AtomicLong): Boolean =
        readLimitedEntry(input, output, MAX_CARD_BORDER_BYTES, totalCounter)

    private fun readBytesBounded(
        input: InputStream,
        maxEntryBytes: Long,
        totalCounter: AtomicLong
    ): ByteArray {
        val output = ByteArrayOutputStream()
        copyBounded(input, output, maxEntryBytes, totalCounter)
        return output.toByteArray()
    }

    private fun copyBounded(
        input: InputStream,
        output: OutputStream,
        maxEntryBytes: Long,
        totalCounter: AtomicLong
    ): Long {
        val buffer = ByteArray(8192)
        var entryBytes = 0L
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) return entryBytes

            val nextEntryBytes = entryBytes + bytesRead
            val nextTotalBytes = totalCounter.get() + bytesRead
            if (nextEntryBytes > maxEntryBytes || nextTotalBytes > MAX_TOTAL_BUNDLE_BYTES) {
                throw IllegalArgumentException("Theme bundle entry exceeds maximum allowed size")
            }

            output.write(buffer, 0, bytesRead)
            entryBytes = nextEntryBytes
            totalCounter.set(nextTotalBytes)
        }
    }

    fun hexToColorInt(hex: String, defaultColor: Int): Int =
        safeParseColorInt(hex, defaultColor)

    private fun decodeMascotFirstFramePreview(
        bytes: ByteArray,
        manifest: MochibunBundleManifest
    ): Bitmap? {
        return try {
            val (w, h) = MascotAtlasValidator.readPngSize(bytes) ?: return null
            val fw = (manifest.mascot?.frameWidth ?: (w / 4)).coerceIn(1, w)
            val fh = (manifest.mascot?.frameHeight ?: (h / 8)).coerceIn(1, h)
            val opts = BitmapFactory.Options().apply {
                outWidth = fw
                outHeight = fh
            }
            val sample = calculateInSampleSize(opts, 96, 96)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
            }
            val decoder = if (Build.VERSION.SDK_INT >= 31) {
                ByteArrayInputStream(bytes).use { BitmapRegionDecoder.newInstance(it) }
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            } ?: return null
            try {
                decoder.decodeRegion(Rect(0, 0, fw, fh), decodeOpts)
            } finally {
                decoder.recycle()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeBoundedThumbnail(bytes: ByteArray): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(boundsOpts, 96, 96)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }
}

/** Adds every byte read to the whole-bundle total and fails once the bundle is too big. */
private class BundleByteCounter(input: InputStream, private val total: AtomicLong) : FilterInputStream(input) {
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0 && total.addAndGet(n.toLong()) > ThemeBundleManager.MAX_TOTAL_BUNDLE_BYTES) {
            throw IllegalArgumentException("Theme bundle entry exceeds maximum allowed size")
        }
        return n
    }
}

/** Keeps the first [capacity] bytes written and discards the rest. */
private class HeaderCaptureOutputStream(capacity: Int) : OutputStream() {
    private val buffer = ByteArray(capacity)
    private var size = 0

    override fun write(value: Int) {
        if (size < buffer.size) buffer[size++] = value.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val n = minOf(len, buffer.size - size)
        if (n > 0) {
            System.arraycopy(b, off, buffer, size, n)
            size += n
        }
    }

    fun bytes(): ByteArray = buffer.copyOf(size)
}
