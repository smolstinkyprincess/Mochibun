package com.crinklecodone.mochibun.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import com.crinklecodone.mochibun.core.util.calculateInSampleSize
import com.crinklecodone.mochibun.core.util.downsampleAndSaveImage
import com.crinklecodone.mochibun.data.model.BundleCursorConfig
import com.crinklecodone.mochibun.data.model.BundleGreetingConfig
import com.crinklecodone.mochibun.data.model.BundlePaletteConfig
import com.crinklecodone.mochibun.data.model.MochibunBundleManifest
import com.crinklecodone.mochibun.data.model.bundleJsonFormat
import com.crinklecodone.mochibun.data.model.colorIntToHex
import com.crinklecodone.mochibun.data.model.safeParseColorInt
import com.crinklecodone.mochibun.data.prefs.ClickMethod
import com.crinklecodone.mochibun.data.prefs.CursorPreferences
import com.crinklecodone.mochibun.data.prefs.HotspotAlignment
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import kotlinx.serialization.encodeToString
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Preview inspection metadata and thumbnails extracted from a `.mochibun` bundle.
 */
data class BundlePreviewData(
    val manifest: MochibunBundleManifest,
    val wallpaperPreview: Bitmap? = null,
    val hasCursorAsset: Boolean = false
)

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

    private const val MAX_WALLPAPER_WIDTH = 480
    private const val MAX_WALLPAPER_HEIGHT = 800
    private const val MAX_PREVIEW_WALLPAPER_BYTES = 5 * 1024 * 1024 // 5MB guard against MT6761 memory spike

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
        return "${sanitized}_${dateFormat.format(Date())}.mochibun"
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
        includeDisplayName: Boolean = true
    ): Result<Unit> {
        return try {
            val launcherPrefs = PreferencesManager.getPrefs(context)
            val currentPreset = PreferencesManager.getThemePreset(context)

            // 1. Resolve active theme colors
            val currentTheme = com.crinklecodone.mochibun.ui.theme.loadSavedTheme(launcherPrefs)
            val bgColorHex = colorIntToHex(currentTheme.bgColor.toArgb())
            val cardColorHex = colorIntToHex(currentTheme.cardBgColor.toArgb())
            val greetingColorHex = colorIntToHex(currentTheme.greetingColor.toArgb())
            val actionColorHex = colorIntToHex(currentTheme.appActionColor.toArgb())
            val cardRoundness = currentTheme.cardRoundnessDp

            // 2. Check wallpaper & cursor availability
            val activeWallpaperUriStr = PreferencesManager.getWallpaperUri(context)
            val activeWallpaperUri = if (includeWallpaper && !activeWallpaperUriStr.isNullOrBlank()) {
                Uri.parse(activeWallpaperUriStr)
            } else null

            val activeCursorUri = if (includeCustomCursor) {
                CursorPreferences.getCustomCursorUri(context)
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
                    cardRoundnessDp = cardRoundness
                ),
                cursor = BundleCursorConfig(
                    baseVelocity = CursorPreferences.getMoveSpeed(context),
                    accelerationMulti = CursorPreferences.getAcceleration(context),
                    pointerSizeDp = CursorPreferences.getCursorSizeDp(context).toFloat(),
                    clickMethod = CursorPreferences.getClickMethod(context).name,
                    hotspot = CursorPreferences.getHotspotAlignment(context).name
                ),
                hasWallpaper = activeWallpaperUri != null,
                hasCustomCursor = activeCursorUri != null
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

            var manifest: MochibunBundleManifest? = null
            var wallpaperBitmap: Bitmap? = null
            var hasCursor = false

            ZipInputStream(bufferedIn).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> {
                            val text = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            manifest = bundleJsonFormat.decodeFromString<MochibunBundleManifest>(text)
                        }
                        WALLPAPER_ENTRY -> {
                            // Extract downsampled thumbnail preview with strict memory bounds
                            val bytesOut = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            var bytesReadTotal = 0
                            var read = zipIn.read(buffer)
                            while (read != -1 && bytesReadTotal < MAX_PREVIEW_WALLPAPER_BYTES) {
                                bytesOut.write(buffer, 0, read)
                                bytesReadTotal += read
                                read = zipIn.read(buffer)
                            }
                            val wallBytes = bytesOut.toByteArray()
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
                            hasCursor = true
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (manifest == null) {
                Result.failure(IllegalArgumentException("Invalid .mochibun bundle: missing manifest.json"))
            } else {
                Result.success(
                    BundlePreviewData(
                        manifest = manifest!!,
                        wallpaperPreview = wallpaperBitmap,
                        hasCursorAsset = hasCursor
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts and applies all settings, palette tokens, and assets from a .mochibun bundle.
     */
    fun applyBundle(context: Context, inputStream: InputStream): Result<MochibunBundleManifest> {
        return try {
            var manifest: MochibunBundleManifest? = null
            var extractedWallpaperFile: File? = null
            var extractedCursorFile: File? = null

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        MANIFEST_ENTRY -> {
                            val text = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            manifest = bundleJsonFormat.decodeFromString<MochibunBundleManifest>(text)
                        }
                        WALLPAPER_ENTRY -> {
                            // Bitmap dimension guard: ensure incoming wallpaper is downsampled to AT-M120 screen
                            val tempFile = File(context.cacheDir, "temp_wall_import.png")
                            try {
                                FileOutputStream(tempFile).use { out ->
                                    zipIn.copyTo(out, bufferSize = 8192)
                                }
                                val destFile = File(context.filesDir, "bundle_wallpaper.png")
                                downsampleAndSaveImage(tempFile, destFile, MAX_WALLPAPER_WIDTH, MAX_WALLPAPER_HEIGHT)
                                extractedWallpaperFile = destFile
                            } finally {
                                if (tempFile.exists()) {
                                    tempFile.delete()
                                }
                            }
                        }
                        CURSOR_GIF_ENTRY -> {
                            val cursorFile = File(context.filesDir, "bundle_cursor.gif")
                            FileOutputStream(cursorFile).use { out ->
                                zipIn.copyTo(out, bufferSize = 8192)
                            }
                            extractedCursorFile = cursorFile
                        }
                        CURSOR_PNG_ENTRY -> {
                            val cursorFile = File(context.filesDir, "bundle_cursor.png")
                            FileOutputStream(cursorFile).use { out ->
                                zipIn.copyTo(out, bufferSize = 8192)
                            }
                            extractedCursorFile = cursorFile
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            val targetManifest = manifest ?: return Result.failure(
                IllegalArgumentException("Bundle is missing manifest.json")
            )

            // 1. Apply Palette & Colors
            val launcherPrefs = PreferencesManager.getPrefs(context)
            val editor = launcherPrefs.edit()

            PreferencesManager.setThemePreset(context, targetManifest.palette.activePresetId)

            val p = targetManifest.palette
            val bgInt = hexToColorInt(p.bgColorHex, DEFAULT_FALLBACK_BG_INT)
            val cardInt = hexToColorInt(p.cardColorHex, DEFAULT_FALLBACK_CARD_INT)
            val greetingInt = hexToColorInt(p.greetingColorHex, DEFAULT_FALLBACK_GREETING_INT)
            val actionInt = hexToColorInt(p.actionColorHex, DEFAULT_FALLBACK_ACTION_INT)

            editor.putInt(PreferencesManager.KEY_THEME_BG_COLOR, bgInt)
            editor.putInt(PreferencesManager.KEY_THEME_FG_TEXT_COLOR, greetingInt)
            editor.putInt(PreferencesManager.KEY_THEME_CARD_BG_COLOR, cardInt)
            editor.putInt(PreferencesManager.KEY_THEME_CARD_TEXT_COLOR, greetingInt)
            editor.putInt(PreferencesManager.KEY_THEME_GREETING_COLOR, greetingInt)
            editor.putInt(PreferencesManager.KEY_THEME_GREETING_OUTLINE_COLOR, cardInt)
            editor.putInt(PreferencesManager.KEY_THEME_GREETING_BG_COLOR, bgInt)
            editor.putInt(PreferencesManager.KEY_THEME_APP_ACTION_COLOR, actionInt)
            editor.putFloat(PreferencesManager.KEY_THEME_CARD_ROUNDNESS, p.cardRoundnessDp)

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

            // 4. Set Assets if present
            if (extractedWallpaperFile != null && extractedWallpaperFile.exists()) {
                val wallUri = Uri.fromFile(extractedWallpaperFile)
                PreferencesManager.setWallpaperUri(context, wallUri.toString())
            }

            if (extractedCursorFile != null && extractedCursorFile.exists()) {
                val curUri = Uri.fromFile(extractedCursorFile)
                CursorPreferences.setCustomCursorUri(context, curUri)
            }

            editor.apply()
            PreferencesManager.init(context)

            Result.success(targetManifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hexToColorInt(hex: String, defaultColor: Int): Int =
        safeParseColorInt(hex, defaultColor)
}
