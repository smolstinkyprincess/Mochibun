package com.crinklecodone.mochibun.data.model

import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

@Immutable
data class LauncherApp(
    val resolveInfo: ResolveInfo,
    val name: String,
    val iconBitmap: Bitmap
) {
    val packageName: String = resolveInfo.activityInfo.packageName
    val activityName: String = resolveInfo.activityInfo.name
    val componentKey: String = "$packageName/$activityName"
    val favoriteKey: String = "app:$packageName"
    val lowercaseName: String = name.lowercase()
    val imageBitmap: ImageBitmap = iconBitmap.asImageBitmap()
}





