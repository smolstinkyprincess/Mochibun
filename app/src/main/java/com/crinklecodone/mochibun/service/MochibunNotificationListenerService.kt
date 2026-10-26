package com.crinklecodone.mochibun.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.crinklecodone.mochibun.data.repository.MediaSessionTracker
import com.crinklecodone.mochibun.data.repository.NotificationTracker

/**
 * Lightweight NotificationListenerService enabling Mochibun to track active MediaSessions
 * and active user notifications across Android 12 (API 31) without overhead or battery drain.
 */
class MochibunNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MochibunNotification", "NotificationListenerService connected")
        MediaSessionTracker.onNotificationListenerServiceConnected(this)
        NotificationTracker.onNotificationListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("MochibunNotification", "NotificationListenerService disconnected")
        NotificationTracker.onNotificationListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { NotificationTracker.onNotificationPosted(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let { NotificationTracker.onNotificationRemoved(it) }
    }
}
