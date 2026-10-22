package com.crinklecodone.mochibun.data.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FavoriteFolder(
    val id: String,
    val name: String,
    val appPackages: List<String>
)

