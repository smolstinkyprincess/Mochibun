package com.crinklecodone.mochibun.service

import android.service.notification.NotificationListenerService
import android.util.Log
import com.crinklecodone.mochibun.data.repository.MediaSessionTracker

/**
 * Lightweight NotificationListenerService enabling Mochibun to track active MediaSessions
 * across media apps on Android 12 (API 31) without overhead or battery drain.
 */
class MochibunNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MochibunNotification", "NotificationListenerService connected")
        MediaSessionTracker.onNotificationListenerServiceConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("MochibunNotification", "NotificationListenerService disconnected")
        MediaSessionTracker.onNotificationListenerServiceDisconnected()
    }
}
