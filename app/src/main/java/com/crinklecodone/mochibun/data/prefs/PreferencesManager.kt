package com.crinklecodone.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.emptyList

/**
 * Centralized preferences manager for Mochibun launcher settings.
 * Exposes reactive StateFlows for Compose consumption without requiring launcher restarts.
 */
object PreferencesManager {
    const val PREFS_NAME = "launcher_preferences"
    const val KEY_PIXELSPRAY_WALLPAPER_ENABLED = "pref_pixelspray_wallpaper_enabled"
    const val DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED = true
    const val KEY_SYSTEM_MONITOR_ENABLED = "pref_system_monitor_enabled"
    const val DEFAULT_SYSTEM_MONITOR_ENABLED = true
    const val KEY_FAVORITE_FOLDERS = "favorite_folders_v1"

    const val KEY_FOLDER_GRID_MODE = "pref_folder_grid_mode"
    const val DEFAULT_FOLDER_GRID_MODE = false // Compact List by default

    const val KEY_T9_SEARCH_ENABLED = "pref_t9_search_enabled"
    const val DEFAULT_T9_SEARCH_ENABLED = true

    const val KEY_SYSTEM_MONITOR_INTERVAL = "pref_system_monitor_interval_sec"
    const val DEFAULT_SYSTEM_MONITOR_INTERVAL = 2

    const val KEY_CUSTOM_USER_NAME = "custom_user_name"
    const val DEFAULT_CUSTOM_USER_NAME = ""

    const val KEY_THEME_PRESET = "theme_preset"
    const val DEFAULT_THEME_PRESET = "sakura_dream"

    const val KEY_DPAD_WRAP_AROUND = "pref_dpad_wrap_around"
    const val DEFAULT_DPAD_WRAP_AROUND = true

    const val KEY_DPAD_BOUNDARY_HAPTICS = "pref_dpad_boundary_haptics"
    const val DEFAULT_DPAD_BOUNDARY_HAPTICS = true

    const val KEY_SPEED_DIAL_PREFIX = "pref_speed_dial_"

    const val KEY_WALLPAPER_URI = "wallpaper_uri"
    const val KEY_HIDDEN_APPS = "hidden_apps"
    const val KEY_FAVORITE_ITEMS_ORDERED = "favorite_items_ordered_v1"
    const val KEY_FAVORITE_PACKAGES_ORDERED = "favorite_packages_ordered"
    const val KEY_CUSTOM_ICON_PREFIX = "custom_icon_"
    const val KEY_CUSTOM_FOLDER_ICON_PREFIX = "custom_folder_icon_"

    private val _isPixelSprayEnabled = MutableStateFlow(DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED)
    val isPixelSprayEnabled: StateFlow<Boolean> = _isPixelSprayEnabled.asStateFlow()

    private val _isSystemMonitorEnabled = MutableStateFlow(DEFAULT_SYSTEM_MONITOR_ENABLED)
    val isSystemMonitorEnabled: StateFlow<Boolean> = _isSystemMonitorEnabled.asStateFlow()

    private val _isFolderGridMode = MutableStateFlow(DEFAULT_FOLDER_GRID_MODE)
    val isFolderGridMode: StateFlow<Boolean> = _isFolderGridMode.asStateFlow()

    private val _isT9SearchEnabled = MutableStateFlow(DEFAULT_T9_SEARCH_ENABLED)
    val isT9SearchEnabled: StateFlow<Boolean> = _isT9SearchEnabled.asStateFlow()

    private val _isDpadWrapAround = MutableStateFlow(DEFAULT_DPAD_WRAP_AROUND)
    val isDpadWrapAround: StateFlow<Boolean> = _isDpadWrapAround.asStateFlow()

    private val _isDpadBoundaryHaptics = MutableStateFlow(DEFAULT_DPAD_BOUNDARY_HAPTICS)
    val isDpadBoundaryHaptics: StateFlow<Boolean> = _isDpadBoundaryHaptics.asStateFlow()

    private val _systemMonitorInterval = MutableStateFlow(DEFAULT_SYSTEM_MONITOR_INTERVAL)
    val systemMonitorInterval: StateFlow<Int> = _systemMonitorInterval.asStateFlow()

    private val _customUserName = MutableStateFlow(DEFAULT_CUSTOM_USER_NAME)
    val customUserName: StateFlow<String> = _customUserName.asStateFlow()

    private val _themePreset = MutableStateFlow(DEFAULT_THEME_PRESET)
    val themePreset: StateFlow<String> = _themePreset.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val prefs = getPrefs(context)
            _isPixelSprayEnabled.value = prefs.getBoolean(
                KEY_PIXELSPRAY_WALLPAPER_ENABLED,
                DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED
            )
            _isSystemMonitorEnabled.value = prefs.getBoolean(
                KEY_SYSTEM_MONITOR_ENABLED,
                DEFAULT_SYSTEM_MONITOR_ENABLED
            )
            _isFolderGridMode.value = prefs.getBoolean(
                KEY_FOLDER_GRID_MODE,
                DEFAULT_FOLDER_GRID_MODE
            )
            _isT9SearchEnabled.value = prefs.getBoolean(
                KEY_T9_SEARCH_ENABLED,
                DEFAULT_T9_SEARCH_ENABLED
            )
            _systemMonitorInterval.value = prefs.getInt(
                KEY_SYSTEM_MONITOR_INTERVAL,
                DEFAULT_SYSTEM_MONITOR_INTERVAL
            )
            _customUserName.value = prefs.getString(
                KEY_CUSTOM_USER_NAME,
                DEFAULT_CUSTOM_USER_NAME
            ) ?: DEFAULT_CUSTOM_USER_NAME
            _themePreset.value = prefs.getString(
                KEY_THEME_PRESET,
                DEFAULT_THEME_PRESET
            ) ?: DEFAULT_THEME_PRESET
            _isDpadWrapAround.value = prefs.getBoolean(
                KEY_DPAD_WRAP_AROUND,
                DEFAULT_DPAD_WRAP_AROUND
            )
            _isDpadBoundaryHaptics.value = prefs.getBoolean(
                KEY_DPAD_BOUNDARY_HAPTICS,
                DEFAULT_DPAD_BOUNDARY_HAPTICS
            )
            initialized = true
        }
    }

    fun loadFavoriteFolders(context: Context): List<FavoriteFolder> {
        val preferences = getPrefs(context)
        val saved = preferences.getString(KEY_FAVORITE_FOLDERS, null) ?: return emptyList()
        return try {
            val json = JSONArray(saved)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    val packages = item.optJSONArray("apps") ?: JSONArray()
                    if (id.isBlank() || name.isBlank()) continue
                    val appPackages = buildList {
                        for (j in 0 until packages.length()) {
                            val pkg = packages.optString(j)
                            if (pkg.isNotBlank() && pkg !in this) add(pkg)
                        }
                    }
                    add(FavoriteFolder(id = id, name = name, appPackages = appPackages))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFavoriteFolders(context: Context, folders: List<FavoriteFolder>) {
        val json = JSONArray()
        folders.forEach { folder ->
            json.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("apps", JSONArray(folder.appPackages))
            )
        }
        getPrefs(context).edit()
            .putString(KEY_FAVORITE_FOLDERS, json.toString())
            .apply()
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPixelSprayWallpaperEnabled(context: Context): Boolean {
        init(context)
        return _isPixelSprayEnabled.value
    }

    fun setPixelSprayWallpaperEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PIXELSPRAY_WALLPAPER_ENABLED, enabled).apply()
        _isPixelSprayEnabled.value = enabled
    }

    fun isSystemMonitorEnabled(context: Context): Boolean {
        init(context)
        return _isSystemMonitorEnabled.value
    }

    fun setSystemMonitorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SYSTEM_MONITOR_ENABLED, enabled).apply()
        _isSystemMonitorEnabled.value = enabled
    }

    fun isFolderGridMode(context: Context): Boolean {
        init(context)
        return _isFolderGridMode.value
    }

    fun setFolderGridMode(context: Context, isGrid: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLDER_GRID_MODE, isGrid).apply()
        _isFolderGridMode.value = isGrid
    }

    fun isT9SearchEnabled(context: Context): Boolean {
        init(context)
        return _isT9SearchEnabled.value
    }

    fun setT9SearchEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_T9_SEARCH_ENABLED, enabled).apply()
        _isT9SearchEnabled.value = enabled
    }

    fun getSystemMonitorInterval(context: Context): Int {
        init(context)
        return _systemMonitorInterval.value
    }

    fun setSystemMonitorInterval(context: Context, intervalSec: Int) {
        getPrefs(context).edit().putInt(KEY_SYSTEM_MONITOR_INTERVAL, intervalSec).apply()
        _systemMonitorInterval.value = intervalSec
    }

    fun getCustomUserName(context: Context): String {
        init(context)
        return _customUserName.value
    }

    fun setCustomUserName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_USER_NAME, name).apply()
        _customUserName.value = name
    }

    fun getThemePreset(context: Context): String {
        init(context)
        return _themePreset.value
    }

    fun setThemePreset(context: Context, presetId: String) {
        getPrefs(context).edit().putString(KEY_THEME_PRESET, presetId).apply()
        _themePreset.value = presetId
    }

    fun isDpadWrapAround(context: Context): Boolean {
        init(context)
        return _isDpadWrapAround.value
    }

    fun setDpadWrapAround(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_WRAP_AROUND, enabled).apply()
        _isDpadWrapAround.value = enabled
    }

    fun isDpadBoundaryHaptics(context: Context): Boolean {
        init(context)
        return _isDpadBoundaryHaptics.value
    }

    fun setDpadBoundaryHaptics(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_BOUNDARY_HAPTICS, enabled).apply()
        _isDpadBoundaryHaptics.value = enabled
    }

    fun getSpeedDialPackage(context: Context, digit: Int): String? {
        if (digit !in 2..9) return null
        return getPrefs(context).getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
    }

    fun setSpeedDialPackage(context: Context, digit: Int, packageName: String?) {
        if (digit !in 2..9) return
        val editor = getPrefs(context).edit()
        if (packageName.isNullOrBlank()) {
            editor.remove("$KEY_SPEED_DIAL_PREFIX$digit")
        } else {
            editor.putString("$KEY_SPEED_DIAL_PREFIX$digit", packageName)
        }
        editor.apply()
    }

    fun getAllSpeedDials(context: Context): Map<Int, String> {
        val prefs = getPrefs(context)
        return buildMap {
            for (digit in 2..9) {
                val pkg = prefs.getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
                if (!pkg.isNullOrBlank()) {
                    put(digit, pkg)
                }
            }
        }
    }

    fun getWallpaperUri(context: Context): String? {
        return getPrefs(context).getString(KEY_WALLPAPER_URI, null)
    }

    fun setWallpaperUri(context: Context, uri: String?) {
        val editor = getPrefs(context).edit()
        if (uri == null) {
            editor.remove(KEY_WALLPAPER_URI)
        } else {
            editor.putString(KEY_WALLPAPER_URI, uri)
        }
        editor.apply()
    }

    fun getHiddenPackages(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_HIDDEN_APPS, emptySet())?.toSet() ?: emptySet()
    }

    fun setHiddenPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_APPS, packages).apply()
    }

    fun addHiddenPackage(context: Context, packageName: String) {
        val current = getHiddenPackages(context).toMutableSet()
        if (current.add(packageName)) {
            setHiddenPackages(context, current)
        }
    }

    fun removeHiddenPackage(context: Context, packageName: String) {
        val current = getHiddenPackages(context).toMutableSet()
        if (current.remove(packageName)) {
            setHiddenPackages(context, current)
        }
    }

    fun getFavoriteItemKeys(context: Context): List<String> {
        val prefs = getPrefs(context)
        val saved = prefs.getString(KEY_FAVORITE_ITEMS_ORDERED, null)
        if (saved != null) {
            return saved.split("|").filter { it.isNotEmpty() }
        }
        val legacyPackages = prefs.getString(KEY_FAVORITE_PACKAGES_ORDERED, "")
            ?.split("|")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val legacyFolders = loadFavoriteFolders(context)
        return legacyPackages.map { "app:$it" } + legacyFolders.map { "folder:${it.id}" }
    }

    fun saveFavoriteItemKeys(context: Context, keys: List<String>) {
        val appPkgs = keys.filterNot { it.startsWith("folder:") }.map { it.removePrefix("app:") }
        getPrefs(context).edit()
            .putString(KEY_FAVORITE_ITEMS_ORDERED, keys.joinToString("|"))
            .putString(KEY_FAVORITE_PACKAGES_ORDERED, appPkgs.joinToString("|"))
            .apply()
    }

    fun getCustomAppIcon(context: Context, packageName: String): String? {
        return getPrefs(context).getString("$KEY_CUSTOM_ICON_PREFIX$packageName", null)
    }

    fun setCustomAppIcon(context: Context, packageName: String, iconName: String?) {
        val editor = getPrefs(context).edit()
        if (iconName == null) {
            editor.remove("$KEY_CUSTOM_ICON_PREFIX$packageName")
        } else {
            editor.putString("$KEY_CUSTOM_ICON_PREFIX$packageName", iconName)
        }
        editor.apply()
    }

    fun getCustomFolderIcon(context: Context, folderId: String): String? {
        return getPrefs(context).getString("$KEY_CUSTOM_FOLDER_ICON_PREFIX$folderId", null)
    }

    fun setCustomFolderIcon(context: Context, folderId: String, iconName: String?) {
        val editor = getPrefs(context).edit()
        if (iconName == null) {
            editor.remove("$KEY_CUSTOM_FOLDER_ICON_PREFIX$folderId")
        } else {
            editor.putString("$KEY_CUSTOM_FOLDER_ICON_PREFIX$folderId", iconName)
        }
        editor.apply()
    }
}
