/**
 * Third-party and custom icon pack resolver for Mochibun.
 * Parses icon pack themes (appfilter.xml, drawable.xml), caches resolved bitmaps
 * in a bounded LRU cache for the MT6761's ~1.8 GB RAM envelope, and handles asynchronous queries.
 */
package com.crinklecodone.mochibun.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.core.util.drawableToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isDefault: Boolean = false
)

data class ParsedIconPack(
    val packageName: String,
    val iconPackContext: Context,
    val componentMap: Map<String, String>,
    val packageMap: Map<String, String>,
    val allIconNames: List<String>
)

object IconPackManager {
    private const val TAG = "IconPackManager"
    private const val PREF_SELECTED_ICON_PACK = "selected_icon_pack"
    private const val KAWAII_ICON_PACK = "kawaii.icon.pack"

    private val ICON_PACK_INTENT_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME",
        "com.fede.launcher.THEME_ICONPACK"
    )

    private val lock = Any()

    @Volatile
    private var activeParsedPack: ParsedIconPack? = null

    @Volatile
    private var lastLoadedPackageName: String? = null

    // Bounded in-memory icon cache capped at 128 entries (~4-6 MB graphics RAM).
    // The Chinoe AT-M120 operates in a strict ~1.8 GB total RAM envelope. Unbounded bitmap
    // allocations trigger aggressive Low Memory Killer (LMK) trashing during fast drawer scrolls.
    private val imageBitmapCache = object : LruCache<String, ImageBitmap>(128) {}
    private val drawableResIdCache = ConcurrentHashMap<String, Int>()
    private val appEntryCache = ConcurrentHashMap<String, LauncherApp>()

    fun getSelectedIconPack(context: Context): String {
        val prefs = context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
        if (prefs.contains(PREF_SELECTED_ICON_PACK)) {
            return prefs.getString(PREF_SELECTED_ICON_PACK, "") ?: ""
        }

        // Backwards compatibility for existing Mochibun installations:
        // If kawaii.icon.pack is installed, default to it; otherwise use system default
        return if (isPackageInstalled(context, KAWAII_ICON_PACK)) {
            KAWAII_ICON_PACK
        } else {
            ""
        }
    }

    fun setSelectedIconPack(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_SELECTED_ICON_PACK, packageName).apply()
        clearCache()
    }

    fun clearCache() {
        synchronized(lock) {
            activeParsedPack = null
            lastLoadedPackageName = null
            imageBitmapCache.evictAll()
            drawableResIdCache.clear()
            appEntryCache.clear()
        }
    }

    fun invalidateAppIcon(packageName: String) {
        val prefix = "$packageName/"
        val keysToRemove = appEntryCache.keys.filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            appEntryCache.remove(key)
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    // Offloaded to Dispatchers.IO: Querying package manager activities and reading intent filters
    // takes 40-100ms on the MT6761 SoC; doing this on the main thread would cause dropped frames.
    suspend fun queryInstalledIconPacks(context: Context): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packs = LinkedHashMap<String, IconPackInfo>()

        // Default system icon entry
        val defaultAppIcon = context.applicationInfo.loadIcon(pm)
        packs[""] = IconPackInfo(
            packageName = "",
            label = "System Default",
            icon = defaultAppIcon,
            isDefault = true
        )

        for (action in ICON_PACK_INTENT_ACTIONS) {
            val intent = Intent(action)
            val resolveInfos = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Failed querying icon pack intent: $action", e)
                emptyList()
            }

            for (ri in resolveInfos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (!packs.containsKey(pkg)) {
                    try {
                        val label = ri.loadLabel(pm).toString()
                        val icon = ri.loadIcon(pm)
                        packs[pkg] = IconPackInfo(
                            packageName = pkg,
                            label = label,
                            icon = icon,
                            isDefault = false
                        )
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.w(TAG, "Failed loading icon pack info for $pkg", e)
                    }
                }
            }
        }

        // Also check explicitly for kawaii.icon.pack in case its manifest only had custom filters
        if (!packs.containsKey(KAWAII_ICON_PACK) && isPackageInstalled(context, KAWAII_ICON_PACK)) {
            try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(KAWAII_ICON_PACK, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(KAWAII_ICON_PACK, 0)
                }
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                packs[KAWAII_ICON_PACK] = IconPackInfo(
                    packageName = KAWAII_ICON_PACK,
                    label = label,
                    icon = icon,
                    isDefault = false
                )
            } catch (_: Exception) {}
        }

        packs.values.toList()
    }

    suspend fun getActiveParsedPack(context: Context): ParsedIconPack? = withContext(Dispatchers.IO) {
        val selectedPkg = getSelectedIconPack(context)
        if (selectedPkg.isEmpty()) return@withContext null

        if (activeParsedPack != null && lastLoadedPackageName == selectedPkg) {
            return@withContext activeParsedPack
        }

        synchronized(lock) {
            if (activeParsedPack != null && lastLoadedPackageName == selectedPkg) {
                return@synchronized activeParsedPack
            }
            val parsed = loadAndParseIconPack(context, selectedPkg)
            activeParsedPack = parsed
            lastLoadedPackageName = selectedPkg
            return@synchronized parsed
        }
    }

    fun getActiveParsedPackSync(context: Context): ParsedIconPack? {
        val selectedPkg = getSelectedIconPack(context)
        if (selectedPkg.isEmpty()) return null

        if (activeParsedPack != null && lastLoadedPackageName == selectedPkg) {
            return activeParsedPack
        }

        synchronized(lock) {
            if (activeParsedPack != null && lastLoadedPackageName == selectedPkg) {
                return activeParsedPack
            }
            val parsed = loadAndParseIconPack(context, selectedPkg)
            activeParsedPack = parsed
            lastLoadedPackageName = selectedPkg
            return parsed
        }
    }

    // XML parsing for appfilter.xml and drawable.xml can involve thousands of tag nodes.
    // Executed strictly off the main thread to prevent ANR dialogs during icon pack switching.
    private fun loadAndParseIconPack(context: Context, iconPackPackage: String): ParsedIconPack? {
        val appContext = context.applicationContext ?: context
        val iconPackContext = try {
            appContext.createPackageContext(iconPackPackage, Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Icon pack package not found: $iconPackPackage")
            return null
        }

        val componentMap = HashMap<String, String>(512)
        val packageMap = HashMap<String, String>(256)
        val iconNames = LinkedHashSet<String>(512)

        // 1. Try parsing appfilter.xml from res/xml or assets
        parseAppFilter(iconPackPackage, iconPackContext, componentMap, packageMap, iconNames)

        // 2. Try parsing drawable.xml from res/xml or assets to discover all icons for picker
        parseDrawableXml(iconPackPackage, iconPackContext, iconNames)

        val sortedIconNames = iconNames.filter { it.isNotEmpty() }.sorted()

        return ParsedIconPack(
            packageName = iconPackPackage,
            iconPackContext = iconPackContext,
            componentMap = componentMap,
            packageMap = packageMap,
            allIconNames = sortedIconNames
        )
    }

    private fun parseAppFilter(
        iconPackPackage: String,
        iconPackContext: Context,
        componentMap: MutableMap<String, String>,
        packageMap: MutableMap<String, String>,
        iconNames: MutableSet<String>
    ) {
        var parser: XmlPullParser? = null
        var inputStream: InputStream? = null

        val appFilterId = iconPackContext.resources.getIdentifier(
            "appfilter",
            "xml",
            iconPackPackage
        )

        try {
            if (appFilterId != 0) {
                parser = iconPackContext.resources.getXml(appFilterId)
            } else {
                try {
                    inputStream = iconPackContext.assets.open("appfilter.xml")
                    val pullParser = Xml.newPullParser()
                    pullParser.setInput(inputStream, "UTF-8")
                    parser = pullParser
                } catch (_: java.io.IOException) { }
            }

            if (parser == null) return

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")

                    if (!component.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                        componentMap[component] = drawable
                        iconNames.add(drawable)

                        // Normalize "ComponentInfo{pkg/cls}" into "pkg/cls"
                        val clean = component
                            .removePrefix("ComponentInfo{")
                            .removeSuffix("}")
                        if (clean.isNotEmpty()) {
                            componentMap[clean] = drawable

                            val slashIdx = clean.indexOf('/')
                            if (slashIdx > 0) {
                                val pkg = clean.substring(0, slashIdx)
                                if (!packageMap.containsKey(pkg)) {
                                    packageMap[pkg] = drawable
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing appfilter.xml from $iconPackPackage: ${e.message}")
        } finally {
            try {
                inputStream?.close()
            } catch (_: java.io.IOException) {}
        }
    }

    private fun parseDrawableXml(
        iconPackPackage: String,
        iconPackContext: Context,
        iconNames: MutableSet<String>
    ) {
        var parser: XmlPullParser? = null
        var inputStream: InputStream? = null

        val drawableId = iconPackContext.resources.getIdentifier(
            "drawable",
            "xml",
            iconPackPackage
        )

        try {
            if (drawableId != 0) {
                parser = iconPackContext.resources.getXml(drawableId)
            } else {
                try {
                    inputStream = iconPackContext.assets.open("drawable.xml")
                    val pullParser = Xml.newPullParser()
                    pullParser.setInput(inputStream, "UTF-8")
                    parser = pullParser
                } catch (_: java.io.IOException) {}
            }

            if (parser == null) return

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (!drawable.isNullOrEmpty()) {
                        iconNames.add(drawable)
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing drawable.xml from $iconPackPackage: ${e.message}")
        } finally {
            try {
                inputStream?.close()
            } catch (_: java.io.IOException) {}
        }
    }

    private fun getDrawableResId(iconPackContext: Context, packageName: String, drawableName: String): Int {
        if (drawableName.isEmpty()) return 0
        val key = "$packageName:$drawableName"
        drawableResIdCache[key]?.let { return it }

        val resId = iconPackContext.resources.getIdentifier(
            drawableName,
            "drawable",
            packageName
        )
        drawableResIdCache[key] = resId
        return resId
    }

    fun getAppIconDrawable(
        context: Context,
        resolveInfo: ResolveInfo,
        customIconName: String? = null
    ): Drawable {
        val pm = context.packageManager
        val pkgName = resolveInfo.activityInfo.packageName
        val activityName = resolveInfo.activityInfo.name
        val parsedPack = getActiveParsedPackSync(context)

        // 1. If user set a custom icon override for this app:
        if (!customIconName.isNullOrEmpty()) {
            val customDrawable = loadIconByName(context, customIconName, parsedPack)
            if (customDrawable != null) return customDrawable
        }

        // 2. If an active icon pack is enabled, resolve using the pack's mapping
        if (parsedPack != null) {
            val drawableName = parsedPack.componentMap["ComponentInfo{$pkgName/$activityName}"]
                ?: parsedPack.componentMap["$pkgName/$activityName"]
                ?: parsedPack.packageMap[pkgName]

            if (!drawableName.isNullOrEmpty()) {
                val resId = getDrawableResId(parsedPack.iconPackContext, parsedPack.packageName, drawableName)
                if (resId != 0) {
                    try {
                        val d = parsedPack.iconPackContext.resources.getDrawable(resId, null)
                        if (d != null) return d
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. Fallback to system default application icon
        return try {
            resolveInfo.loadIcon(pm)
        } catch (_: Exception) {
            context.applicationInfo.loadIcon(pm)
        }
    }

    fun getOrLoadLauncherApp(
        context: Context,
        preferences: SharedPreferences,
        resolveInfo: ResolveInfo
    ): LauncherApp {
        val pkgName = resolveInfo.activityInfo.packageName
        val activityName = resolveInfo.activityInfo.name
        val customIconName = preferences.getString("custom_icon_$pkgName", null)
        val selectedPack = getSelectedIconPack(context)
        val cacheKey = "$pkgName/$activityName#${customIconName.orEmpty()}#$selectedPack"

        appEntryCache[cacheKey]?.let { return it }

        // Remove any stale entry for the same component with an older custom icon or pack
        val componentPrefix = "$pkgName/$activityName#"
        for (existingKey in appEntryCache.keys) {
            if (existingKey.startsWith(componentPrefix) && existingKey != cacheKey) {
                appEntryCache.remove(existingKey)
            }
        }

        val pm = context.packageManager
        val drawable = getAppIconDrawable(context, resolveInfo, customIconName)
        val app = LauncherApp(
            resolveInfo = resolveInfo,
            name = resolveInfo.loadLabel(pm).toString(),
            iconBitmap = drawableToBitmap(drawable, 96)
        )
        appEntryCache[cacheKey] = app
        return app
    }

    private fun loadIconByName(
        context: Context,
        drawableName: String,
        parsedPack: ParsedIconPack?
    ): Drawable? {
        if (drawableName.isEmpty()) return null

        // Try currently active pack
        if (parsedPack != null) {
            val resId = getDrawableResId(parsedPack.iconPackContext, parsedPack.packageName, drawableName)
            if (resId != 0) {
                try {
                    val d = parsedPack.iconPackContext.resources.getDrawable(resId, null)
                    if (d != null) return d
                } catch (_: Exception) {}
            }
        }

        // Backwards compatibility fallback: check kawaii.icon.pack if active pack didn't have it
        if (parsedPack?.packageName != KAWAII_ICON_PACK && isPackageInstalled(context, KAWAII_ICON_PACK)) {
            try {
                val kawaiiContext = context.createPackageContext(KAWAII_ICON_PACK, Context.CONTEXT_IGNORE_SECURITY)
                val resId = getDrawableResId(kawaiiContext, KAWAII_ICON_PACK, drawableName)
                if (resId != 0) {
                    val d = kawaiiContext.resources.getDrawable(resId, null)
                    if (d != null) return d
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun getCachedImageBitmap(
        context: Context,
        drawableName: String?,
        targetSizePx: Int = 96
    ): ImageBitmap? {
        if (drawableName.isNullOrEmpty()) return null
        val selectedPack = getSelectedIconPack(context)
        val cacheKey = "$selectedPack#$drawableName@$targetSizePx"

        synchronized(imageBitmapCache) {
            imageBitmapCache.get(cacheKey)?.let { return it }
        }

        val parsedPack = getActiveParsedPackSync(context)
        val drawable = loadIconByName(context, drawableName, parsedPack) ?: return null

        return try {
            val imageBitmap = drawableToBitmap(drawable, targetSizePx).asImageBitmap()
            synchronized(imageBitmapCache) {
                imageBitmapCache.put(cacheKey, imageBitmap)
            }
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadAllIconNames(context: Context): List<String> = withContext(Dispatchers.IO) {
        val parsed = getActiveParsedPack(context)
        if (parsed != null && parsed.allIconNames.isNotEmpty()) {
            return@withContext parsed.allIconNames
        }

        // Fallback to kawaii.icon.pack if installed
        if (isPackageInstalled(context, KAWAII_ICON_PACK)) {
            val kawaiiParsed = loadAndParseIconPack(context, KAWAII_ICON_PACK)
            if (kawaiiParsed != null && kawaiiParsed.allIconNames.isNotEmpty()) {
                return@withContext kawaiiParsed.allIconNames
            }
        }

        emptyList()
    }
}
