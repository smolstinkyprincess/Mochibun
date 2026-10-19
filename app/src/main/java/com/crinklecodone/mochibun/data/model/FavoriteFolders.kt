package com.crinklecodone.mochibun.data.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FavoriteFolder(
    val id: String,
    val name: String,
    val appPackages: List<String>
)

sealed class FavoriteItem {
    data class App(val packageName: String) : FavoriteItem()
    data class Folder(val folderId: String) : FavoriteItem()
}
