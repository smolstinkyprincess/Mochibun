package one.crinklecod.mochibun.data.prefs

import android.content.Context
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_FAVORITE_FOLDERS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_FAVORITE_ITEMS_ORDERED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_HIDDEN_APPS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_STEP_BASELINE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_STEP_CARRIED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_STEP_DAY
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_STEP_LAST_COUNTER
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_CUSTOM_FOLDER_ICON_PREFIX
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_CUSTOM_ICON_PREFIX
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.emptyList

/**
 * Hidden apps, custom app/folder icons, favorite folders & ordering, and daily step bookkeeping.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object HomeContentPrefs {
    fun getDailyStepState(context: Context): DailyStepState? {
        val prefs = getPrefs(context)
        val day = prefs.getInt(KEY_STEP_DAY, -1)
        if (day < 0) return null
        return DailyStepState(
            day = day,
            baseline = prefs.getLong(KEY_STEP_BASELINE, 0L),
            carried = prefs.getLong(KEY_STEP_CARRIED, 0L),
            lastCounter = prefs.getLong(KEY_STEP_LAST_COUNTER, 0L)
        )
    }

    fun setDailyStepState(context: Context, state: DailyStepState) {
        getPrefs(context).edit()
            .putInt(KEY_STEP_DAY, state.day)
            .putLong(KEY_STEP_BASELINE, state.baseline)
            .putLong(KEY_STEP_CARRIED, state.carried)
            .putLong(KEY_STEP_LAST_COUNTER, state.lastCounter)
            .apply()
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
        val raw = getPrefs(context).getString(KEY_FAVORITE_FOLDERS, null) ?: return emptyList()
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
        val raw = getPrefs(context).getString(KEY_FAVORITE_ITEMS_ORDERED, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotEmpty() }
    }

    fun saveFavoriteItemKeys(context: Context, keys: List<String>) {
        getPrefs(context).edit()
            .putString(KEY_FAVORITE_ITEMS_ORDERED, keys.joinToString("|"))
            .apply()
    }
}
