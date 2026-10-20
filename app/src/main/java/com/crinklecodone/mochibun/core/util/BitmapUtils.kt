package com.crinklecodone.mochibun.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.BufferedInputStream

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

// loads and samples wallpaper bitmap using memory efficient rgb_565 config
fun loadWallpaperBitmap(
    context: Context,
    uri: Uri
): Bitmap? {
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

    return resolver.openInputStream(uri)?.use { rawStream ->
        BufferedInputStream(rawStream).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }?.also { it.prepareToDraw() }
}