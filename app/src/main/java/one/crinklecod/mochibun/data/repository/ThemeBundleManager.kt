package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.toArgb
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
    val cardBorderPreview: Bitmap? = null
) {
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

    private const val MAX_WALLPAPER_WIDTH = 480
    private const val MAX_WALLPAPER_HEIGHT = 800

    const val MAX_MANIFEST_BYTES = 512L * 1024L
    const val MAX_WALLPAPER_BYTES = 5L * 1024L * 1024L
    const val MAX_CURSOR_BYTES = 2L * 1024L * 1024L
    const val MAX_CARD_BORDER_BYTES = CustomCardBorderManager.MAX_BORDER_BYTES
    const val MAX_TOTAL_BUNDLE_BYTES = 8L * 1024L * 1024L

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
        includeCustomCardBorder: Boolean = true
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
                    fontFamilyId = fontFamilyId
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
                mascot = mascotConfig
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
                        else -> copyBounded(
                            zipIn,
                            DISCARD_OUTPUT_STREAM,
                            MAX_TOTAL_BUNDLE_BYTES,
                            totalBytes
                        )
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
                        cardBorderPreview = cardBorderPreview
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
                        else -> copyBounded(
                            zipIn,
                            DISCARD_OUTPUT_STREAM,
                            MAX_TOTAL_BUNDLE_BYTES,
                            totalBytes
                        )
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

            p.fontFamilyId?.takeIf { it.isNotBlank() }?.let { requestedFontId ->
                if (TypographyManager.options.value.any { it.id == requestedFontId }) {
                    PreferencesManager.setThemeFontFamily(context, requestedFontId)
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
    private fun readMascotEntry(input: InputStream, output: OutputStream, totalCounter: AtomicLong): Boolean {
        val counting = BundleByteCounter(input, totalCounter)
        val withinLimit = MascotAtlasValidator.copyAtMost(counting, output, MascotAtlasValidator.MAX_ATLAS_BYTES)
        if (!withinLimit) copyBounded(input, DISCARD_OUTPUT_STREAM, MAX_TOTAL_BUNDLE_BYTES, totalCounter)
        return withinLimit
    }

    /**
     * Reads `card_border.png` into [output] up to [MAX_CARD_BORDER_BYTES] (512 KB), counting real
     * bytes. If oversized, drains the entry under the whole-bundle limit and returns false so the
     * rest of the bundle still imports cleanly.
     */
    private fun readCardBorderEntry(input: InputStream, output: OutputStream, totalCounter: AtomicLong): Boolean {
        val counting = BundleByteCounter(input, totalCounter)
        val withinLimit = MascotAtlasValidator.copyAtMost(counting, output, MAX_CARD_BORDER_BYTES)
        if (!withinLimit) copyBounded(input, DISCARD_OUTPUT_STREAM, MAX_TOTAL_BUNDLE_BYTES, totalCounter)
        return withinLimit
    }

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
