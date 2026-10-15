package com.tnyemii.mochibun

import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream

// holds installed app information and resolved bitmap icon :3
data class LauncherApp(
    val resolveInfo: ResolveInfo,
    val name: String,
    val iconBitmap: Bitmap
) {
    // cached Compose ImageBitmap avoids rewrapping on every recomposition/scroll frame <3
    val imageBitmap: ImageBitmap by lazy(LazyThreadSafetyMode.NONE) {
        iconBitmap.asImageBitmap()
    }
}

@Volatile
private var cachedIconMap: Map<String, String>? = null

// clears cached icon map when packages change <3
fun clearKawaiiIconCache() {
    cachedIconMap = null
}

// loads kawaii icon pack appfilter xml mapping with in-memory caching <3
fun loadKawaiiIconMap(
    context: Context
): Map<String, String> {
    cachedIconMap?.let { return it }

    val iconMap = mutableMapOf<String, String>()

    val iconPackContext = try {
        context.createPackageContext("kawaii.icon.pack", Context.CONTEXT_IGNORE_SECURITY)
    } catch (e: Exception) { return emptyMap() }

    val appFilterId = iconPackContext.resources.getIdentifier(
        "appfilter",
        "xml",
        "kawaii.icon.pack"
    )

    if (appFilterId == 0) { return emptyMap() }

    try {
        val parser = iconPackContext.resources.getXml(appFilterId)
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")

                if (component != null && drawable != null) { iconMap[component] = drawable }
            }

            eventType = parser.next()
        }

        parser.close()
    } catch (_: Exception) {}

    cachedIconMap = iconMap
    return iconMap
}

// looks up custom kawaii icon for app component name :3
fun getKawaiiIcon(
    iconPackContext: Context?,
    iconMap: Map<String, String>,
    packageName: String,
    activityName: String
): Drawable? {
    if (iconPackContext == null) { return null }

    val drawableName = iconMap["ComponentInfo{$packageName/$activityName}"] ?: return null

    val resourceId = iconPackContext.resources.getIdentifier(
        drawableName,
        "drawable",
        "kawaii.icon.pack"
    )

    if (resourceId == 0) { return null }

    return iconPackContext.resources.getDrawable(resourceId, null)
}

// loads list of all available icon names from kawaii icon pack reuse cached map <3
fun loadAllKawaiiIconNames(context: Context): List<String> {
    return loadKawaiiIconMap(context).values.filter { it.isNotEmpty() }.distinct().sorted()
}

// converts android drawable into bitmap capped at targetSizePx for compose display :3
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

    return bitmap
}

// loads and samples wallpaper bitmap using memory efficient rgb_565 config <3
fun loadWallpaperBitmap(
    context: Context,
    uri: Uri
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }

    resolver.openInputStream(uri)?.use { rawStream ->
        BufferedInputStream(rawStream).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
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

    val options = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    return resolver.openInputStream(uri)?.use { rawStream ->
        BufferedInputStream(rawStream).use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, options)
        }
    }
}
