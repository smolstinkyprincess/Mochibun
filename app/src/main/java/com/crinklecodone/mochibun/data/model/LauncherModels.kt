package com.crinklecodone.mochibun.data.model

import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

enum class AppDrawerSortMode(val label: String, val icon: String, val toastName: String) {
    ALPHABETICAL("A-Z", "🔤", "Alphabetical (A-Z)"),
    NEWEST("Newest", "✨", "Recently Installed"),
    MOST_USED("Used", "🔥", "Most Used");

    fun next(): AppDrawerSortMode {
        val entries = values()
        return entries[(ordinal + 1) % entries.size]
    }
}

@Immutable
data class LauncherApp(
    val resolveInfo: ResolveInfo,
    val name: String,
    val iconBitmap: Bitmap,
    val installTime: Long = 0L
) {
    val packageName: String = resolveInfo.activityInfo.packageName
    val activityName: String = resolveInfo.activityInfo.name
    val componentKey: String = "$packageName/$activityName"
    val favoriteKey: String = "app:$packageName"
    val lowercaseName: String = name.lowercase()
    val imageBitmap: ImageBitmap = iconBitmap.asImageBitmap()
}
