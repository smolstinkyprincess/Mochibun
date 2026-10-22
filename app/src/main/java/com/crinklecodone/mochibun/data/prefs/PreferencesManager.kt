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

    const val KEY_THEME_BG_COLOR = "theme_bg_color"
    const val KEY_THEME_FG_TEXT_COLOR = "theme_fg_text_color"
    const val KEY_THEME_CARD_BG_COLOR = "theme_card_bg_color"
    const val KEY_THEME_CARD_TEXT_COLOR = "theme_card_text_color"
    const val KEY_THEME_GREETING_COLOR = "theme_greeting_color"
    const val KEY_THEME_GREETING_OUTLINE_COLOR = "theme_greeting_outline_color"
    const val KEY_THEME_GREETING_BG_COLOR = "theme_greeting_bg_color"
    const val KEY_THEME_APP_ACTION_COLOR = "theme_app_action_color"
    const val KEY_THEME_CARD_ROUNDNESS = "theme_card_roundness"

    val THEME_COLOR_KEYS = listOf(
        KEY_THEME_BG_COLOR,
        KEY_THEME_FG_TEXT_COLOR,
        KEY_THEME_CARD_BG_COLOR,
        KEY_THEME_CARD_TEXT_COLOR,
        KEY_THEME_GREETING_COLOR,
        KEY_THEME_GREETING_OUTLINE_COLOR,
        KEY_THEME_GREETING_BG_COLOR,
        KEY_THEME_APP_ACTION_COLOR
    )

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
            try {
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
            } catch (_: Exception) {
            }
            initialized = true
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPixelSprayWallpaperEnabled(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(
                KEY_PIXELSPRAY_WALLPAPER_ENABLED,
                DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED
            )
        } catch (_: Exception) {
            DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED
        }
    }

    fun setPixelSprayWallpaperEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PIXELSPRAY_WALLPAPER_ENABLED, enabled).apply()
        _isPixelSprayEnabled.value = enabled
    }

    fun isSystemMonitorEnabled(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(
                KEY_SYSTEM_MONITOR_ENABLED,
                DEFAULT_SYSTEM_MONITOR_ENABLED
            )
        } catch (_: Exception) {
            DEFAULT_SYSTEM_MONITOR_ENABLED
        }
    }

    fun setSystemMonitorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SYSTEM_MONITOR_ENABLED, enabled).apply()
        _isSystemMonitorEnabled.value = enabled
    }

    fun isFolderGridMode(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(
                KEY_FOLDER_GRID_MODE,
                DEFAULT_FOLDER_GRID_MODE
            )
        } catch (_: Exception) {
            DEFAULT_FOLDER_GRID_MODE
        }
    }

    fun setFolderGridMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FOLDER_GRID_MODE, enabled).apply()
        _isFolderGridMode.value = enabled
    }

    fun isT9SearchEnabled(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(
                KEY_T9_SEARCH_ENABLED,
                DEFAULT_T9_SEARCH_ENABLED
            )
        } catch (_: Exception) {
            DEFAULT_T9_SEARCH_ENABLED
        }
    }

    fun setT9SearchEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_T9_SEARCH_ENABLED, enabled).apply()
        _isT9SearchEnabled.value = enabled
    }

    fun getSystemMonitorInterval(context: Context): Int {
        return try {
            getPrefs(context).getInt(
                KEY_SYSTEM_MONITOR_INTERVAL,
                DEFAULT_SYSTEM_MONITOR_INTERVAL
            )
        } catch (_: Exception) {
            DEFAULT_SYSTEM_MONITOR_INTERVAL
        }
    }

    fun setSystemMonitorInterval(context: Context, seconds: Int) {
        getPrefs(context).edit().putInt(KEY_SYSTEM_MONITOR_INTERVAL, seconds).apply()
        _systemMonitorInterval.value = seconds
    }

    fun getCustomUserName(context: Context): String {
        return try {
            getPrefs(context).getString(
                KEY_CUSTOM_USER_NAME,
                DEFAULT_CUSTOM_USER_NAME
            ) ?: DEFAULT_CUSTOM_USER_NAME
        } catch (_: Exception) {
            DEFAULT_CUSTOM_USER_NAME
        }
    }

    fun setCustomUserName(context: Context, name: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_USER_NAME, name).apply()
        _customUserName.value = name
    }

    fun getThemePreset(context: Context): String {
        return try {
            getPrefs(context).getString(
                KEY_THEME_PRESET,
                DEFAULT_THEME_PRESET
            ) ?: DEFAULT_THEME_PRESET
        } catch (_: Exception) {
            DEFAULT_THEME_PRESET
        }
    }

    fun setThemePreset(context: Context, preset: String) {
        getPrefs(context).edit().putString(KEY_THEME_PRESET, preset).apply()
        _themePreset.value = preset
    }

    fun isDpadWrapAround(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(
                KEY_DPAD_WRAP_AROUND,
                DEFAULT_DPAD_WRAP_AROUND
            )
        } catch (_: Exception) {
            DEFAULT_DPAD_WRAP_AROUND
        }
    }

    fun setDpadWrapAround(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_WRAP_AROUND, enabled).apply()
        _isDpadWrapAround.value = enabled
    }

    fun isDpadBoundaryHaptics(context: Context): Boolean {
        return try {
            getPrefs(context).getBoolean(
                KEY_DPAD_BOUNDARY_HAPTICS,
                DEFAULT_DPAD_BOUNDARY_HAPTICS
            )
        } catch (_: Exception) {
            DEFAULT_DPAD_BOUNDARY_HAPTICS
        }
    }

    fun setDpadBoundaryHaptics(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_BOUNDARY_HAPTICS, enabled).apply()
        _isDpadBoundaryHaptics.value = enabled
    }

    fun getWallpaperUri(context: Context): String? {
        return try {
            getPrefs(context).getString(KEY_WALLPAPER_URI, null)
        } catch (_: Exception) {
            null
        }
    }

    fun setWallpaperUri(context: Context, uri: String?) {
        if (uri == null) {
            getPrefs(context).edit().remove(KEY_WALLPAPER_URI).apply()
        } else {
            getPrefs(context).edit().putString(KEY_WALLPAPER_URI, uri).apply()
        }
    }

    fun getHiddenPackages(context: Context): Set<String> {
        val prefs = getPrefs(context)
        return try {
            val rawObj = prefs.all[KEY_HIDDEN_APPS]
            when (rawObj) {
                is Set<*> -> {
                    val set = rawObj.filterIsInstance<String>().toSet()
                    setHiddenPackages(context, set)
                    set
                }
                is String -> {
                    if (rawObj.isBlank()) emptySet() else rawObj.split(",").toSet()
                }
                else -> emptySet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setHiddenPackages(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putString(KEY_HIDDEN_APPS, packages.joinToString(",")).apply()
    }

    fun addHiddenPackage(context: Context, packageName: String) {
        val current = getHiddenPackages(context).toMutableSet()
        current.add(packageName)
        setHiddenPackages(context, current)
    }

    fun removeHiddenPackage(context: Context, packageName: String) {
        val current = getHiddenPackages(context).toMutableSet()
        current.remove(packageName)
        setHiddenPackages(context, current)
    }

    fun setCustomAppIcon(context: Context, packageName: String, iconName: String?) {
        val key = "$KEY_CUSTOM_ICON_PREFIX$packageName"
        val editor = getPrefs(context).edit()
        if (iconName != null) {
            editor.putString(key, iconName).apply()
        } else {
            editor.remove(key).apply()
        }
    }

    fun setCustomFolderIcon(context: Context, folderId: String, iconName: String?) {
        val key = "$KEY_CUSTOM_FOLDER_ICON_PREFIX$folderId"
        val editor = getPrefs(context).edit()
        if (iconName != null) {
            editor.putString(key, iconName).apply()
        } else {
            editor.remove(key).apply()
        }
    }

    fun saveFavoriteFolders(context: Context, folders: List<FavoriteFolder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            val obj = JSONObject()
            obj.put("id", folder.id)
            obj.put("name", folder.name)
            obj.put("apps", JSONArray(folder.appPackages))
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_FAVORITE_FOLDERS, array.toString()).apply()
    }

    fun loadFavoriteFolders(context: Context): List<FavoriteFolder> {
        val raw = try {
            getPrefs(context).getString(KEY_FAVORITE_FOLDERS, null)
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<FavoriteFolder>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val appsArray = obj.getJSONArray("apps")
                val apps = mutableListOf<String>()
                for (j in 0 until appsArray.length()) {
                    apps.add(appsArray.getString(j))
                }
                list.add(FavoriteFolder(id = id, name = name, appPackages = apps))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getFavoriteItemKeys(context: Context): List<String> {
        val raw = try {
            getPrefs(context).getString(KEY_FAVORITE_ITEMS_ORDERED, "") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotEmpty() }
    }

    fun saveFavoriteItemKeys(context: Context, keys: List<String>) {
        getPrefs(context).edit()
            .putString(KEY_FAVORITE_ITEMS_ORDERED, keys.joinToString("|"))
            .apply()
    }

    fun getSpeedDialPackage(context: Context, digit: Int): String? {
        if (digit !in 2..9) return null
        return try {
            getPrefs(context).getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
        } catch (_: Exception) {
            null
        }
    }

    fun setSpeedDialPackage(context: Context, digit: Int, packageName: String?) {
        if (digit !in 2..9) return
        val prefs = getPrefs(context)
        if (packageName == null) {
            prefs.edit().remove("$KEY_SPEED_DIAL_PREFIX$digit").apply()
        } else {
            prefs.edit().putString("$KEY_SPEED_DIAL_PREFIX$digit", packageName).apply()
        }
    }

    fun getAllSpeedDials(context: Context): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        val prefs = getPrefs(context)
        for (digit in 2..9) {
            val pkg = try {
                prefs.getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
            } catch (_: Exception) {
                null
            }
            if (pkg != null) {
                map[digit] = pkg
            }
        }
        return map
    }
}
