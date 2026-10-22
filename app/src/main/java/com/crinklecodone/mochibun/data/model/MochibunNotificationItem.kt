package com.crinklecodone.mochibun.data.model

import android.app.PendingIntent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
data class MochibunNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val appIcon: Drawable?
)
