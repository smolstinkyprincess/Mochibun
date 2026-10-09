package com.tnyemii.m120launcher

import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import org.xmlpull.v1.XmlPullParser

// holds installed app information and resolved drawable icon :3
data class LauncherApp(
    val resolveInfo: ResolveInfo,
    val name: String,
    val icon: Drawable
)

// loads kawaii icon pack appfilter xml mapping <3
fun loadKawaiiIconMap(
    context: Context
): Map<String, String> {
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

// loads list of all available icon names from kawaii icon pack <3
fun loadAllKawaiiIconNames(context: Context): List<String> {
    val iconNames = mutableSetOf<String>()

    val iconPackContext = try {
        context.createPackageContext(
            "kawaii.icon.pack",
            Context.CONTEXT_IGNORE_SECURITY
        )
    } catch (e: Exception) {
        return emptyList()
    }

    val appFilterId = iconPackContext.resources.getIdentifier(
        "appfilter",
        "xml",
        "kawaii.icon.pack"
    )

    if (appFilterId == 0) return emptyList()

    val parser = iconPackContext.resources.getXml(appFilterId)

    var eventType = parser.eventType

    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (
            eventType == XmlPullParser.START_TAG &&
            parser.name == "item"
        ) {
            val drawableName = parser.getAttributeValue(
                null,
                "drawable"
            )

            if (!drawableName.isNullOrEmpty()) {
                iconNames.add(drawableName)
            }
        }

        eventType = parser.next()
    }

    parser.close()

    return iconNames.toList()
}

// converts android drawable into bitmap for compose display :3
fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

    resolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
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

    return resolver.openInputStream(uri)?.use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream, null, options)
    }
}
