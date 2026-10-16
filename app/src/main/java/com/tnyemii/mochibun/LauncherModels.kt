package com.tnyemii.mochibun

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap

// holds installed app information and resolved bitmap icon :3
@Immutable
data class LauncherApp(
    val resolveInfo: ResolveInfo,
    val name: String,
    val iconBitmap: Bitmap
) {
    val packageName: String = resolveInfo.activityInfo.packageName
    val activityName: String = resolveInfo.activityInfo.name
    val componentKey: String = "$packageName/$activityName"
    val favoriteKey: String = "app:$packageName"
    val lowercaseName: String = name.lowercase()

    // cached Compose ImageBitmap initialized on background loader thread <3
    val imageBitmap: ImageBitmap = iconBitmap.asImageBitmap()
}

private val iconCacheLock = Any()

@Volatile
private var cachedIconMap: Map<String, String>? = null

@Volatile
private var cachedIconPackContext: Context? = null

@Volatile
private var iconPackContextInitialized = false

private val cachedDrawableResIds = ConcurrentHashMap<String, Int>()
private val cachedAppEntries = ConcurrentHashMap<String, LauncherApp>()
private val cachedKawaiiImageBitmaps = object : LruCache<String, ImageBitmap>(64) {}

// clears cached icon map and app icons when packages change <3
fun clearKawaiiIconCache() {
    synchronized(iconCacheLock) {
        cachedIconMap = null
        cachedIconPackContext = null
        iconPackContextInitialized = false
        cachedDrawableResIds.clear()
        cachedAppEntries.clear()
        cachedKawaiiImageBitmaps.evictAll()
    }
}

// invalidates cached app entries for a single package when its custom icon changes :3
fun invalidateCachedAppIcon(packageName: String) {
    val prefix = "$packageName/"
    val keysToRemove = cachedAppEntries.keys.filter { it.startsWith(prefix) }
    for (key in keysToRemove) {
        cachedAppEntries.remove(key)
    }
}

// returns a single shared PackageContext for kawaii.icon.pack to avoid leaking ContextImpls <3
fun getKawaiiIconPackContext(context: Context): Context? {
    if (iconPackContextInitialized) return cachedIconPackContext
    synchronized(iconCacheLock) {
        if (iconPackContextInitialized) return cachedIconPackContext
        val appContext = context.applicationContext ?: context
        cachedIconPackContext = try {
            appContext.createPackageContext("kawaii.icon.pack", Context.CONTEXT_IGNORE_SECURITY)
        } catch (_: Exception) {
            null
        }
        iconPackContextInitialized = true
        return cachedIconPackContext
    }
}

// resolves and caches drawable resource ID inside kawaii.icon.pack :3
fun getKawaiiDrawableResId(iconPackContext: Context?, drawableName: String): Int {
    if (iconPackContext == null || drawableName.isEmpty()) return 0
    cachedDrawableResIds[drawableName]?.let { return it }
    val resId = iconPackContext.resources.getIdentifier(
        drawableName,
        "drawable",
        "kawaii.icon.pack"
    )
    cachedDrawableResIds[drawableName] = resId
    return resId
}

// loads and caches a 96px ImageBitmap from kawaii.icon.pack by drawable name <3
fun getCachedKawaiiImageBitmap(
    context: Context,
    drawableName: String?,
    targetSizePx: Int = 96
): ImageBitmap? {
    if (drawableName.isNullOrEmpty()) return null
    val cacheKey = "$drawableName@$targetSizePx"
    synchronized(cachedKawaiiImageBitmaps) {
        cachedKawaiiImageBitmaps.get(cacheKey)?.let { return it }
    }
    val iconPackContext = getKawaiiIconPackContext(context) ?: return null
    val resId = getKawaiiDrawableResId(iconPackContext, drawableName)
    if (resId == 0) return null
    return try {
        val drawable = iconPackContext.resources.getDrawable(resId, null) ?: return null
        val imageBitmap = drawableToBitmap(drawable, targetSizePx).asImageBitmap()
        synchronized(cachedKawaiiImageBitmaps) {
            cachedKawaiiImageBitmaps.put(cacheKey, imageBitmap)
        }
        imageBitmap
    } catch (_: Exception) {
        null
    }
}

// loads or reuses a cached LauncherApp for a ResolveInfo and custom icon override :3
fun getOrLoadLauncherApp(
    context: Context,
    preferences: SharedPreferences,
    resolveInfo: ResolveInfo,
    kawaiiIconContext: Context? = getKawaiiIconPackContext(context),
    kawaiiIconMap: Map<String, String> = loadKawaiiIconMap(context)
): LauncherApp {
    val pkgName = resolveInfo.activityInfo.packageName
    val activityName = resolveInfo.activityInfo.name
    val customIconName = preferences.getString("custom_icon_$pkgName", null)
    val cacheKey = "$pkgName/$activityName#${customIconName.orEmpty()}"

    cachedAppEntries[cacheKey]?.let { return it }

    // Remove any stale entry for the same component with an older custom icon
    val componentPrefix = "$pkgName/$activityName#"
    for (existingKey in cachedAppEntries.keys) {
        if (existingKey.startsWith(componentPrefix) && existingKey != cacheKey) {
            cachedAppEntries.remove(existingKey)
        }
    }

    val pm = context.packageManager
    val drawable = if (customIconName != null && kawaiiIconContext != null) {
        val resourceId = getKawaiiDrawableResId(kawaiiIconContext, customIconName)
        if (resourceId != 0) {
            try {
                kawaiiIconContext.resources.getDrawable(resourceId, null)
            } catch (_: Exception) {
                null
            } ?: getKawaiiIcon(kawaiiIconContext, kawaiiIconMap, pkgName, activityName)
            ?: resolveInfo.loadIcon(pm)
        } else {
            getKawaiiIcon(kawaiiIconContext, kawaiiIconMap, pkgName, activityName)
                ?: resolveInfo.loadIcon(pm)
        }
    } else {
        getKawaiiIcon(kawaiiIconContext, kawaiiIconMap, pkgName, activityName)
            ?: resolveInfo.loadIcon(pm)
    }

    val app = LauncherApp(
        resolveInfo = resolveInfo,
        name = resolveInfo.loadLabel(pm).toString(),
        iconBitmap = drawableToBitmap(drawable, 96)
    )
    cachedAppEntries[cacheKey] = app
    return app
}

// loads kawaii icon pack appfilter xml mapping with thread-safe in-memory caching <3
fun loadKawaiiIconMap(
    context: Context
): Map<String, String> {
    cachedIconMap?.let { return it }

    synchronized(iconCacheLock) {
        cachedIconMap?.let { return it }

        val iconPackContext = getKawaiiIconPackContext(context) ?: return emptyMap()
        val appFilterId = iconPackContext.resources.getIdentifier(
            "appfilter",
            "xml",
            "kawaii.icon.pack"
        )

        if (appFilterId == 0) {
            cachedIconMap = emptyMap()
            return emptyMap()
        }

        val iconMap = HashMap<String, String>(512)
        try {
            val parser = iconPackContext.resources.getXml(appFilterId)
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")

                    if (component != null && drawable != null) {
                        iconMap[component] = drawable
                    }
                }

                eventType = parser.next()
            }

            parser.close()
        } catch (_: Exception) {}

        cachedIconMap = iconMap
        return iconMap
    }
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
    val resourceId = getKawaiiDrawableResId(iconPackContext, drawableName)
    if (resourceId == 0) { return null }

    return try {
        iconPackContext.resources.getDrawable(resourceId, null)
    } catch (_: Exception) {
        null
    }
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
    bitmap.prepareToDraw()

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
    }?.also { it.prepareToDraw() }
}
