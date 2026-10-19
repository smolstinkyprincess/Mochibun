package com.crinklecodone.mochibun

import android.content.Context

data class FavoriteFolder(
    val id: String,
    val name: String,
    val appPackages: List<String>
)

sealed class FavoriteItem {
    data class App(val packageName: String) : FavoriteItem()
    data class Folder(val folderId: String) : FavoriteItem()
}

private const val FAVORITE_FOLDERS_KEY = "favorite_folders_v1"

fun loadFavoriteFolders(context: Context): List<FavoriteFolder> {
    val preferences = context.getSharedPreferences(
        "launcher_preferences",
        Context.MODE_PRIVATE
    )

    return try {
        val saved = preferences.getString(FAVORITE_FOLDERS_KEY, null)
            ?: return emptyList()

        val json = org.json.JSONArray(saved)
        buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                val packages = item.optJSONArray("apps") ?: org.json.JSONArray()

                if (id.isBlank() || name.isBlank()) continue

                val appPackages = buildList {
                    for (j in 0 until packages.length()) {
                        val pkg = packages.optString(j)
                        if (pkg.isNotBlank() && pkg !in this) add(pkg)
                    }
                }

                add(
                    FavoriteFolder(
                        id = id,
                        name = name,
                        appPackages = appPackages
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveFavoriteFolders(
    context: Context,
    folders: List<FavoriteFolder>
) {
    val json = org.json.JSONArray()

    folders.forEach { folder ->
        json.put(
            org.json.JSONObject()
                .put("id", folder.id)
                .put("name", folder.name)
                .put("apps", org.json.JSONArray(folder.appPackages))
        )
    }

    context.getSharedPreferences(
        "launcher_preferences",
        Context.MODE_PRIVATE
    ).edit()
        .putString(FAVORITE_FOLDERS_KEY, json.toString())
        .apply()
}