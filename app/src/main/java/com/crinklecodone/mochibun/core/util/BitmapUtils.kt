package com.crinklecodone.mochibun.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

fun drawableToBitmap(
    drawable: Drawable,
    targetSizePx: Int = 96
): Bitmap {
    val intrinsicWidth = drawable.intrinsicWidth.coerceAtLeast(1)
    val intrinsicHeight = drawable.intrinsicHeight.coerceAtLeast(1)

    val width: Int
    val height: Int
    if (intrinsicWidth > targetSizePx || intrinsicHeight > targetSizePx) {
        if (intrinsicWidth >= intrinsicHeight) {
            width = targetSizePx
            height = (intrinsicHeight * targetSizePx) / intrinsicWidth
        } else {
            height = targetSizePx
            width = (intrinsicWidth * targetSizePx) / intrinsicHeight
        }
    } else {
        width = intrinsicWidth
        height = intrinsicHeight
    }

    val bitmap = Bitmap.createBitmap(
        width.coerceAtLeast(1),
        height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)

    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bitmap.prepareToDraw()

    return bitmap
}

/**
 * Re-encodes a fully opaque icon as RGB_565, halving its pixel memory. Icons with any transparency
 * (adaptive-icon corners, most icon pack art) keep ARGB_8888, so nothing gains a black background.
 */
fun Bitmap.compactIfOpaque(): Bitmap {
    if (config != Bitmap.Config.ARGB_8888) return this
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    if (!isFullyOpaque(pixels)) return this
    val compact = copy(Bitmap.Config.RGB_565, false) ?: return this
    recycle()
    return compact
}

internal fun isFullyOpaque(pixels: IntArray): Boolean {
    if (pixels.isEmpty()) return false
    for (pixel in pixels) {
        if (pixel ushr 24 != 0xFF) return false
    }
    return true
}

/**
 * Calculates a power-of-two sample size so that decoded dimensions fit within [reqWidth] x [reqHeight].
 */
fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int = 480,
    reqHeight: Int = 800
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * Downsamples an image file using RGB_565 and writes it to [destFile] to conserve graphics RAM on MT6761.
 */
fun downsampleAndSaveImage(
    srcFile: File,
    destFile: File,
    reqWidth: Int = 480,
    reqHeight: Int = 800
) {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(srcFile.absolutePath, boundsOpts)

    val sample = calculateInSampleSize(boundsOpts, reqWidth, reqHeight)
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = BitmapFactory.decodeFile(srcFile.absolutePath, decodeOpts) ?: return

    FileOutputStream(destFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        out.flush()
    }
    bitmap.recycle()
}

// loads and samples wallpaper bitmap using memory efficient rgb_565 config
fun loadWallpaperBitmap(
    context: Context,
    uri: Uri
): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        resolver.openInputStream(uri)?.use { rawStream ->
            BufferedInputStream(rawStream).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > 480 ||
            bounds.outHeight / sampleSize > 800
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        resolver.openInputStream(uri)?.use { rawStream ->
            BufferedInputStream(rawStream).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }?.also { it.prepareToDraw() }
    } catch (e: Exception) {
        Log.e("BitmapUtils", "Failed to decode wallpaper from $uri: ${e.message}")
        null
    }
}
