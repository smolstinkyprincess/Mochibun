package com.crinklecodone.mochibun.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class FavoriteFolder(
    val id: String,
    val name: String,
    val appPackages: List<String>
)

