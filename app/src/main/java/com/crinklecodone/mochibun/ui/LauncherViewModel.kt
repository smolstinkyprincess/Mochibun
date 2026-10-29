package com.crinklecodone.mochibun.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crinklecodone.mochibun.core.util.loadWallpaperBitmap
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.FavoriteSlotItem
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.data.model.AppDrawerSortMode
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.IconPackManager
import com.crinklecodone.mochibun.ui.screens.firstLetterOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ViewModel managing launcher state, data loading, preferences synchronization,
 * and mutations for apps, folders, favorites, and wallpapers.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    // ── Installed & Visible Apps ──────────────────────────────────────────────
    var apps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    var earlyFavoriteApps by mutableStateOf<List<LauncherApp>>(emptyList())
        private set

    var hiddenPackages by mutableStateOf<Set<String>>(emptySet())
        private set

    var appDrawerSortMode by mutableStateOf(PreferencesManager.getAppDrawerSortMode())
        private set

    private var launchCounts by mutableStateOf<Map<String, Int>>(emptyMap())

    val visibleApps by derivedStateOf {
        val filtered = if (hiddenPackages.isEmpty()) {
            apps
        } else {
            apps.filter { it.packageName !in hiddenPackages }
        }
        when (appDrawerSortMode) {
            AppDrawerSortMode.ALPHABETICAL -> filtered.sortedBy { it.lowercaseName }
            AppDrawerSortMode.NEWEST -> filtered.sortedWith(
                compareByDescending<LauncherApp> { it.installTime }.thenBy { it.lowercaseName }
            )
            AppDrawerSortMode.MOST_USED -> filtered.sortedWith(
                compareByDescending<LauncherApp> { launchCounts[it.packageName] ?: 0 }.thenBy { it.lowercaseName }
            )
        }
    }

    val hiddenApps by derivedStateOf {
        if (hiddenPackages.isEmpty()) {
            emptyList()
        } else {
            apps.filter { it.packageName in hiddenPackages }
                .distinctBy { it.packageName }
        }
    }

    val appsByPackage by derivedStateOf {
        buildMap(apps.size) {
            for (app in apps) {
                putIfAbsent(app.packageName, app)
            }
        }
    }

    val letterStarts by derivedStateOf {
        val map = linkedMapOf<Char, Int>()
        visibleApps.forEachIndexed { index, app ->
            map.getOrPut(firstLetterOf(app.name)) { index }
        }
        map
    }

    // ── Favorites & Folders ───────────────────────────────────────────────────
    var favoriteItemKeys by mutableStateOf<List<String>>(emptyList())
        private set

    var favoritePackages by mutableStateOf<List<String>>(emptyList())
        private set

    var favoriteFolders by mutableStateOf<List<FavoriteFolder>>(emptyList())
        private set

    val favoriteApps by derivedStateOf {
        if (apps.isEmpty()) {
            earlyFavoriteApps.filter { it.packageName !in hiddenPackages }
        } else {
            val earlyByPkg = earlyFavoriteApps.associateBy { it.packageName }
            favoritePackages
                .filter { it !in hiddenPackages }
                .mapNotNull { pkgName ->
                    appsByPackage[pkgName] ?: earlyByPkg[pkgName]
                }
        }
    }

    val favoriteItems by derivedStateOf {
        val appsMap = favoriteApps.associateBy { it.packageName }
        val foldersMap = favoriteFolders.associateBy { it.id }

        val items = mutableListOf<FavoriteSlotItem>()
        val seen = mutableSetOf<String>()

        for (key in favoriteItemKeys) {
            if (key.startsWith("folder:")) {
                val folderId = key.removePrefix("folder:")
                val folder = foldersMap[folderId]
                if (folder != null && seen.add(key)) {
                    items.add(FavoriteSlotItem.Folder(folder))
                }
            } else {
                val pkg = key.removePrefix("app:")
                val app = appsMap[pkg]
                if (app != null && pkg !in hiddenPackages && seen.add("app:$pkg")) {
                    items.add(FavoriteSlotItem.App(app))
                }
            }
        }

        // Safety fallback: ensure any folder not in keys is still accessible
        for (folder in favoriteFolders) {
            val fKey = "folder:${folder.id}"
            if (seen.add(fKey)) {
                items.add(FavoriteSlotItem.Folder(folder))
            }
        }

        // Safety fallback: ensure any favorite apps not in keys are still accessible
        for (app in favoriteApps) {
            val aKey = "app:${app.packageName}"
            if (app.packageName !in hiddenPackages && seen.add(aKey)) {
                items.add(FavoriteSlotItem.App(app))
            }
        }

        items
    }

    // ── Wallpaper State ───────────────────────────────────────────────────────
    var wallpaperUri by mutableStateOf<Uri?>(null)
        private set

    var wallpaperBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // ── Icon Pack & Refresh Keys ──────────────────────────────────────────────
    var iconRefreshKey by mutableIntStateOf(0)
        private set

    var folderIconRefreshKey by mutableIntStateOf(0)
        private set

    var kawaiiIconNames by mutableStateOf<List<String>>(emptyList())
        private set

    init {
        // Initialize preferences and cached states
        hiddenPackages = PreferencesManager.getHiddenPackages(context)
        favoriteFolders = PreferencesManager.loadFavoriteFolders(context)
        val loadedKeys = PreferencesManager.getFavoriteItemKeys(context)
        favoriteItemKeys = loadedKeys
        favoritePackages = loadedKeys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }

        PreferencesManager.getWallpaperUri(context)?.let {
            val parsedUri = Uri.parse(it)
            wallpaperUri = parsedUri
            loadWallpaper(parsedUri)
        }
    }

    // ── App Discovery & Two-Phase Loading ─────────────────────────────────────
    fun cycleAppDrawerSortMode(): AppDrawerSortMode {
        val nextMode = appDrawerSortMode.next()
        appDrawerSortMode = nextMode
        PreferencesManager.setAppDrawerSortMode(context, nextMode)
        return nextMode
    }

    fun recordAppLaunch(packageName: String) {
        val currentCount = launchCounts[packageName] ?: 0
        val newMap = launchCounts.toMutableMap()
        newMap[packageName] = currentCount + 1
        launchCounts = newMap
        val prefs = PreferencesManager.getPrefs(context)
        prefs.edit().putInt("app_launch_count_", currentCount + 1).apply()
    }

    private fun loadLaunchCounts() {
        val prefs = PreferencesManager.getPrefs(context)
        val map = mutableMapOf<String, Int>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("app_launch_count_") && value is Int) {
                val pkg = key.removePrefix("app_launch_count_")
                map[pkg] = value
            }
        }
        launchCounts = map
    }

    fun loadApps() {
        loadLaunchCounts()
        appDrawerSortMode = PreferencesManager.getAppDrawerSortMode(context)
        viewModelScope.launch {
            val prefs = PreferencesManager.getPrefs(context)

            // Fast favorites loader for instant home screen render
            if (apps.isEmpty() && favoritePackages.isNotEmpty()) {
                val fastFavs = withContext(Dispatchers.IO) {
                    IconPackManager.getActiveParsedPack(context)
                    favoritePackages
                        .filter { it !in hiddenPackages }
                        .mapNotNull { pkgName ->
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
                                ?: return@mapNotNull null
                            val resolveInfo = context.packageManager.resolveActivity(launchIntent, 0)
                                ?: return@mapNotNull null

                            IconPackManager.getOrLoadLauncherApp(
                                context = context,
                                preferences = prefs,
                                resolveInfo = resolveInfo
                            )
                        }
                }
                earlyFavoriteApps = fastFavs
            }

            // Full app drawer list query
            val loadedApps = withContext(Dispatchers.IO) {
                IconPackManager.getActiveParsedPack(context)
                context.packageManager
                    .queryIntentActivities(launcherIntent, 0)
                    .map { resolveInfo ->
                        IconPackManager.getOrLoadLauncherApp(
                            context = context,
                            preferences = prefs,
                            resolveInfo = resolveInfo
                        )
                    }
                    .sortedBy { it.lowercaseName }
            }
            apps = loadedApps
        }
    }

    fun loadKawaiiIconNames() {
        viewModelScope.launch {
            kawaiiIconNames = withContext(Dispatchers.IO) {
                IconPackManager.loadAllIconNames(context)
            }
        }
    }

    fun clearKawaiiIconNames() {
        kawaiiIconNames = emptyList()
    }

    fun onIconPackChanged() {
        apps = emptyList()
        earlyFavoriteApps = emptyList()
        iconRefreshKey++
        loadApps()
    }

    // ── Reordering Favorites ──────────────────────────────────────────────────
    fun moveFavoriteSlotItem(itemKey: String, direction: Int): Int? {
        val currentKeys = favoriteItems.map { it.key }
        val currentIndex = currentKeys.indexOf(itemKey)
        if (currentIndex != -1) {
            val targetIndex = currentIndex + direction
            if (targetIndex in currentKeys.indices) {
                val newKeys = currentKeys.toMutableList()
                val moved = newKeys.removeAt(currentIndex)
                newKeys.add(targetIndex, moved)

                favoriteItemKeys = newKeys
                val appPkgs = newKeys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }
                favoritePackages = appPkgs
                PreferencesManager.saveFavoriteItemKeys(context, newKeys)
                return targetIndex
            }
        }
        return null
    }

    fun toggleFavoriteApp(packageName: String) {
        val isFav = packageName in favoritePackages
        val newKeys = if (isFav) {
            favoriteItemKeys.filterNot { it == "app:$packageName" || it == packageName }
        } else {
            favoriteItemKeys + "app:$packageName"
        }
        favoriteItemKeys = newKeys
        favoritePackages = newKeys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }
        PreferencesManager.saveFavoriteItemKeys(context, newKeys)
    }

    // ── App & Folder Customization ────────────────────────────────────────────
    fun hideApp(packageName: String) {
        PreferencesManager.addHiddenPackage(context, packageName)
        hiddenPackages = PreferencesManager.getHiddenPackages(context)
    }

    fun unhideApp(packageName: String) {
        PreferencesManager.removeHiddenPackage(context, packageName)
        hiddenPackages = PreferencesManager.getHiddenPackages(context)
    }

    fun setCustomAppIcon(packageName: String, iconName: String) {
        PreferencesManager.setCustomAppIcon(context, packageName, iconName)
        IconPackManager.invalidateAppIcon(packageName)
        iconRefreshKey++
        loadApps()
    }

    fun resetCustomAppIcon(packageName: String) {
        PreferencesManager.setCustomAppIcon(context, packageName, null)
        IconPackManager.invalidateAppIcon(packageName)
        iconRefreshKey++
        loadApps()
    }

    fun setCustomFolderIcon(folderId: String, iconName: String) {
        PreferencesManager.setCustomFolderIcon(context, folderId, iconName)
        folderIconRefreshKey++
    }

    fun resetCustomFolderIcon(folderId: String) {
        PreferencesManager.setCustomFolderIcon(context, folderId, null)
        folderIconRefreshKey++
    }

    // ── Folder CRUD ───────────────────────────────────────────────────────────
    fun createFolder(folderName: String, packages: List<String>): String {
        val folder = FavoriteFolder(
            id = UUID.randomUUID().toString(),
            name = folderName,
            appPackages = packages.distinct()
        )
        val newFolders = favoriteFolders + folder
        favoriteFolders = newFolders
        PreferencesManager.saveFavoriteFolders(context, newFolders)

        val remainingKeys = favoriteItemKeys.filterNot { key ->
            !key.startsWith("folder:") && key.removePrefix("app:") in folder.appPackages
        } + "folder:${folder.id}"

        favoriteItemKeys = remainingKeys
        favoritePackages = remainingKeys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }
        PreferencesManager.saveFavoriteItemKeys(context, remainingKeys)
        return folder.id
    }

    fun renameFolder(folderId: String, newName: String) {
        val duplicateName = favoriteFolders.any {
            it.id != folderId && it.name.equals(newName, ignoreCase = true)
        }
        if (!duplicateName && newName.isNotBlank()) {
            val updated = favoriteFolders.map { folder ->
                if (folder.id == folderId) folder.copy(name = newName) else folder
            }
            favoriteFolders = updated
            PreferencesManager.saveFavoriteFolders(context, updated)
        }
    }

    fun removeAppFromFolder(folderId: String, packageName: String) {
        val updated = favoriteFolders.map { folder ->
            if (folder.id == folderId) {
                folder.copy(appPackages = folder.appPackages.filterNot { it == packageName })
            } else {
                folder
            }
        }
        favoriteFolders = updated
        PreferencesManager.saveFavoriteFolders(context, updated)
    }

    fun updateFolderApps(folderId: String, updatedPackages: List<String>) {
        val distinctPackages = updatedPackages.distinct()
        val updated = favoriteFolders.map { folder ->
            if (folder.id == folderId) {
                folder.copy(appPackages = distinctPackages)
            } else {
                folder
            }
        }
        favoriteFolders = updated
        PreferencesManager.saveFavoriteFolders(context, updated)

        val updatedKeys = favoriteItemKeys.filterNot { key ->
            !key.startsWith("folder:") && key.removePrefix("app:") in distinctPackages
        }
        if (updatedKeys != favoriteItemKeys) {
            favoriteItemKeys = updatedKeys
            favoritePackages = updatedKeys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }
            PreferencesManager.saveFavoriteItemKeys(context, updatedKeys)
        }
    }

    fun deleteFolder(folderId: String) {
        val newFolders = favoriteFolders.filterNot { it.id == folderId }
        favoriteFolders = newFolders
        PreferencesManager.setCustomFolderIcon(context, folderId, null)
        PreferencesManager.saveFavoriteFolders(context, newFolders)

        val newKeys = favoriteItemKeys.filterNot { it == "folder:$folderId" }
        favoriteItemKeys = newKeys
        favoritePackages = newKeys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }
        PreferencesManager.saveFavoriteItemKeys(context, newKeys)
    }

    // ── Wallpaper Management ──────────────────────────────────────────────────
    fun setWallpaper(uri: Uri?) {
        wallpaperUri = uri
        PreferencesManager.setWallpaperUri(context, uri?.toString())
        loadWallpaper(uri)
    }

    fun resetWallpaper() {
        wallpaperUri = null
        wallpaperBitmap = null
        PreferencesManager.setWallpaperUri(context, null)
    }

    fun reloadAllFromPreferences() {
        hiddenPackages = PreferencesManager.getHiddenPackages(context)
        favoriteFolders = PreferencesManager.loadFavoriteFolders(context)
        val loadedKeys = PreferencesManager.getFavoriteItemKeys(context)
        favoriteItemKeys = loadedKeys
        favoritePackages = loadedKeys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }

        val uri = PreferencesManager.getWallpaperUri(context)?.let(Uri::parse)
        wallpaperUri = uri
        if (uri != null) loadWallpaper(uri) else wallpaperBitmap = null
        iconRefreshKey++
        folderIconRefreshKey++
        apps = emptyList()
        earlyFavoriteApps = emptyList()
        loadApps()
    }

    private fun loadWallpaper(uri: Uri?) {
        if (uri == null) {
            wallpaperBitmap = null
            return
        }
        viewModelScope.launch {
            wallpaperBitmap = withContext(Dispatchers.IO) {
                loadWallpaperBitmap(context, uri)
            }
        }
    }
}
