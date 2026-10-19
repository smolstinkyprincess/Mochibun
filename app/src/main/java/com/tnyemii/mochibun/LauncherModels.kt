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

// clears cached icon map and app icons when packages change or icon pack changes <3
fun clearKawaiiIconCache() {
    IconPackManager.clearCache()
}

// invalidates cached app entries for a single package when its custom icon changes :3
fun invalidateCachedAppIcon(packageName: String) {
    IconPackManager.invalidateAppIcon(packageName)
}

// returns a single shared PackageContext for active icon pack or kawaii.icon.pack :3
fun getKawaiiIconPackContext(context: Context): Context? {
    return IconPackManager.getActiveParsedPackSync(context)?.iconPackContext
}

// resolves drawable resource ID inside active icon pack or kawaii.icon.pack :3
fun getKawaiiDrawableResId(iconPackContext: Context?, drawableName: String): Int {
    if (iconPackContext == null || drawableName.isEmpty()) return 0
    return try {
        iconPackContext.resources.getIdentifier(
            drawableName,
            "drawable",
            iconPackContext.packageName
        )
    } catch (_: Exception) {
        0
    }
}

// loads and caches a 96px ImageBitmap from active icon pack by drawable name <3
fun getCachedKawaiiImageBitmap(
    context: Context,
    drawableName: String?,
    targetSizePx: Int = 96
): ImageBitmap? {
    return IconPackManager.getCachedImageBitmap(context, drawableName, targetSizePx)
}

// loads or reuses a cached LauncherApp for a ResolveInfo and custom icon override :3
fun getOrLoadLauncherApp(
    context: Context,
    preferences: SharedPreferences,
    resolveInfo: ResolveInfo,
    kawaiiIconContext: Context? = null,
    kawaiiIconMap: Map<String, String> = emptyMap()
): LauncherApp {
    return IconPackManager.getOrLoadLauncherApp(context, preferences, resolveInfo)
}

// loads active icon pack appfilter mapping with thread-safe in-memory caching <3
fun loadKawaiiIconMap(
    context: Context
): Map<String, String> {
    return IconPackManager.getActiveParsedPackSync(context)?.componentMap ?: emptyMap()
}

// looks up icon for app component name :3
fun getKawaiiIcon(
    iconPackContext: Context?,
    iconMap: Map<String, String>,
    packageName: String,
    activityName: String
): Drawable? {
    val drawableName = iconMap["ComponentInfo{$packageName/$activityName}"]
        ?: iconMap["$packageName/$activityName"]
        ?: return null
    if (iconPackContext == null) return null
    val resId = getKawaiiDrawableResId(iconPackContext, drawableName)
    if (resId == 0) return null
    return try {
        iconPackContext.resources.getDrawable(resId, null)
    } catch (_: Exception) {
        null
    }
}

// loads list of all available icon names from active icon pack <3
fun loadAllKawaiiIconNames(context: Context): List<String> {
    return kotlinx.coroutines.runBlocking {
        IconPackManager.loadAllIconNames(context)
    }
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
