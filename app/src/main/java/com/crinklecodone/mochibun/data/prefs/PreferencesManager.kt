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
    const val KEY_FAVORITE_FOLDERS = "favorite_folders_v1"

    private val _isPixelSprayEnabled = MutableStateFlow(DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED)
    val isPixelSprayEnabled: StateFlow<Boolean> = _isPixelSprayEnabled.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val prefs = getPrefs(context)
            _isPixelSprayEnabled.value = prefs.getBoolean(
                KEY_PIXELSPRAY_WALLPAPER_ENABLED,
                DEFAULT_PIXELSPRAY_WALLPAPER_ENABLED
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
}
