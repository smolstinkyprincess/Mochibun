package com.crinklecodone.mochibun.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.crinklecodone.mochibun.data.repository.MediaSessionTracker
import com.crinklecodone.mochibun.data.repository.NotificationTracker

/**
 * Lightweight NotificationListenerService enabling Mochibun to track active MediaSessions
 * and active user notifications across Android 12 (API 31) without overhead or battery drain.
 *
 * Lid LEDs follow [NotificationTracker.notifications] through LidLedManager's priority ladder, so
 * nothing here drives them directly.
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
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn ?: return
        NotificationTracker.onNotificationPosted(this, sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn ?: return
        NotificationTracker.onNotificationRemoved(sbn, rankingMap)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        NotificationTracker.onNotificationRankingUpdate(rankingMap)
    }

    companion object {
        private const val TAG = "MochibunNotification"
    }
}
