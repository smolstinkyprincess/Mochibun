package com.crinklecodone.mochibun.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.crinklecodone.mochibun.core.system.LidLedManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.MediaSessionTracker
import com.crinklecodone.mochibun.data.repository.NotificationTracker

/**
 * Lightweight NotificationListenerService enabling Mochibun to track active MediaSessions
 * and active user notifications across Android 12 (API 31) without overhead or battery drain.
 */
class MochibunNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected")
        MediaSessionTracker.onNotificationListenerServiceConnected(this)
        NotificationTracker.onNotificationListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListenerService disconnected")
        NotificationTracker.onNotificationListenerDisconnected()
        LidLedManager.stopLeds()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            NotificationTracker.onNotificationPosted(it)
            if (it.packageName != packageName && PreferencesManager.isLidLedEnabled(this)) {
                LidLedManager.triggerNotificationPulse()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            NotificationTracker.onNotificationRemoved(it)
            val remaining = try {
                activeNotifications?.filter { n -> n.packageName != packageName && !n.isOngoing }
            } catch (_: Exception) {
                null
            }
            if (remaining.isNullOrEmpty()) {
                LidLedManager.stopLeds()
            }
        }
    }

    companion object {
        private const val TAG = "MochibunNotification"
    }
}
