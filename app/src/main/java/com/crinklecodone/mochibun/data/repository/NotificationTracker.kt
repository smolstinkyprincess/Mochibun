package com.crinklecodone.mochibun.data.repository

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import com.crinklecodone.mochibun.data.model.MochibunNotificationItem
import com.crinklecodone.mochibun.service.MochibunNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Lightweight reactive notification tracker and state holder for Mochibun.
 * Hooks into [MochibunNotificationListenerService] and exposes filtered notifications
 * to Compose while respecting low-RAM device constraints.
 *
 * Updates are incremental: each callback carries the one [StatusBarNotification] that changed plus
 * the listener's [RankingMap], so after the initial sync no event pulls the full active list over
 * Binder (with every large icon and RemoteViews parceled along). All updates go through one ordered
 * channel with a single consumer, so a late update can never overwrite a newer list, and a burst is
 * folded into one published list.
 */
object NotificationTracker {
    private const val TAG = "NotificationTracker"

    private val _notifications = MutableStateFlow<List<MochibunNotificationItem>>(emptyList())
    val notifications: StateFlow<List<MochibunNotificationItem>> = _notifications.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private var activeServiceRef: WeakReference<MochibunNotificationListenerService>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Low-overhead memory cache for app metadata to avoid repeated IPC lookups
    private val appMetadataCache = LruCache<String, Pair<String, Drawable?>>(40)

    private sealed interface Update {
        class Posted(val context: Context, val sbn: StatusBarNotification, val rankedKeys: Array<String>?) : Update
        class Removed(val key: String, val rankedKeys: Array<String>?) : Update
        class Ranked(val rankedKeys: Array<String>) : Update
        class Resync(val service: MochibunNotificationListenerService) : Update
        data object Clear : Update
    }

    private val updates = Channel<Update>(Channel.UNLIMITED)

    init {
        scope.launch {
            val state = NotificationListState()
            for (first in updates) {
                var update: Update? = first
                while (update != null) {
                    try {
                        apply(state, update)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to apply notification update", e)
                    }
                    update = updates.tryReceive().getOrNull()
                }
                _notifications.value = state.snapshot()
            }
        }
    }

    private fun apply(state: NotificationListState, update: Update) {
        when (update) {
            is Update.Posted -> {
                val item = parseNotification(update.context, update.sbn)
                // A repost can turn a shown notification into filtered noise (e.g. it went ongoing)
                if (item != null) state.upsert(item) else state.remove(update.sbn.key)
                update.rankedKeys?.let(state::setRanking)
            }
            is Update.Removed -> {
                state.remove(update.key)
                update.rankedKeys?.let(state::setRanking)
            }
            is Update.Ranked -> state.setRanking(update.rankedKeys)
            is Update.Resync -> {
                val service = update.service
                val items = (service.activeNotifications ?: emptyArray())
                    .mapNotNull { parseNotification(service, it) }
                state.replaceAll(items)
                service.currentRanking?.orderedKeys?.let(state::setRanking)
            }
            Update.Clear -> state.clear()
        }
    }

    fun onNotificationListenerConnected(service: MochibunNotificationListenerService) {
        Log.d(TAG, "NotificationListener connected")
        activeServiceRef = WeakReference(service)
        _isServiceConnected.value = true
        // The only full sweep: on (re)connection, when the posted/removed stream may have gaps
        updates.trySend(Update.Resync(service))
    }

    fun onNotificationListenerDisconnected() {
        Log.d(TAG, "NotificationListener disconnected")
        activeServiceRef = null
        _isServiceConnected.value = false
    }

    fun onNotificationPosted(context: Context, sbn: StatusBarNotification, rankingMap: RankingMap?) {
        updates.trySend(Update.Posted(context.applicationContext, sbn, rankingMap?.orderedKeys))
    }

    fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        updates.trySend(Update.Removed(sbn.key, rankingMap?.orderedKeys))
    }

    fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        val keys = rankingMap?.orderedKeys ?: return
        updates.trySend(Update.Ranked(keys))
    }

    private fun parseNotification(
        context: Context,
        sbn: StatusBarNotification
    ): MochibunNotificationItem? {
        val pkg = sbn.packageName

        // 1. Skip Mochibun's own package
        if (pkg == context.packageName || pkg == "com.crinklecodone.mochibun") {
            return null
        }

        // 2. Filter persistent background noise: exclude ongoing system notifications unless priority messaging
        val isOngoing = sbn.isOngoing
        val extras = sbn.notification.extras
        val category = sbn.notification.category
        val isPriorityMessaging = category == Notification.CATEGORY_MESSAGE ||
                category == Notification.CATEGORY_CALL ||
                extras?.containsKey(Notification.EXTRA_MESSAGING_PERSON) == true ||
                extras?.containsKey("android.messagingStyleUser") == true

        if (isOngoing && !isPriorityMessaging) {
            return null
        }

        // 3. Extract title and text
        val title = (extras?.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: "").toString().trim()

        val text = (extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            ?: "").toString().trim()

        val ticker = sbn.notification.tickerText?.toString()?.trim().orEmpty()

        if (title.isEmpty() && text.isEmpty() && ticker.isEmpty()) {
            return null
        }

        val (appName, appIcon) = getAppMetadata(context.packageManager, pkg)
        val finalTitle = when {
            title.isNotEmpty() -> title
            text.isNotEmpty() -> appName
            else -> ticker
        }

        return MochibunNotificationItem(
            key = sbn.key,
            packageName = pkg,
            appName = appName.ifBlank { pkg },
            title = finalTitle.ifBlank { appName },
            text = text,
            postTime = sbn.postTime,
            contentIntent = sbn.notification.contentIntent,
            appIcon = appIcon,
            isIncomingCall = isIncomingCall(sbn.notification)
        )
    }

    /**
     * Ringing calls use CallStyle's incoming type on newer dialers, or carry a full-screen intent
     * (the in-call notification for an answered call doesn't).
     */
    private fun isIncomingCall(notification: Notification): Boolean {
        if (notification.category != Notification.CATEGORY_CALL) return false
        val callType = notification.extras?.getInt(Notification.EXTRA_CALL_TYPE, 0) ?: 0
        return callType == Notification.CallStyle.CALL_TYPE_INCOMING || notification.fullScreenIntent != null
    }

    private fun getAppMetadata(pm: PackageManager, packageName: String): Pair<String, Drawable?> {
        appMetadataCache.get(packageName)?.let { return it }
        val (name, icon) = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            val drawable = pm.getApplicationIcon(appInfo)
            Pair(label, drawable)
        } catch (_: Exception) {
            Pair(packageName, null)
        }
        val result = Pair(name, icon)
        appMetadataCache.put(packageName, result)
        return result
    }

    fun cancelNotification(key: String) {
        val service = activeServiceRef?.get()
        if (service != null) {
            try {
                service.cancelNotification(key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel notification: $key", e)
            }
        }
        updates.trySend(Update.Removed(key, rankedKeys = null))
    }

    fun cancelAllNotifications() {
        val service = activeServiceRef?.get()
        if (service != null) {
            try {
                service.cancelAllNotifications()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel all notifications", e)
            }
        }
        updates.trySend(Update.Clear)
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val myComponent = ComponentName(context, MochibunNotificationListenerService::class.java).flattenToString()
        return enabledListeners.contains(myComponent) || enabledListeners.contains(context.packageName)
    }

    fun clearCache() {
        appMetadataCache.evictAll()
    }
}

/**
 * The tracker's list, keyed by [StatusBarNotification.key] and ordered by the listener's ranking
 * (the system's importance-then-recency order). Keys the ranking doesn't know yet sort after ranked
 * ones, newest first. Confined to the tracker's single consumer coroutine.
 */
@VisibleForTesting
internal class NotificationListState {
    private val items = HashMap<String, MochibunNotificationItem>()
    private var rankByKey: Map<String, Int> = emptyMap()

    fun upsert(item: MochibunNotificationItem) {
        items[item.key] = item
    }

    fun remove(key: String) {
        items.remove(key)
    }

    fun replaceAll(newItems: List<MochibunNotificationItem>) {
        items.clear()
        for (item in newItems) items[item.key] = item
    }

    fun clear() {
        items.clear()
    }

    fun setRanking(orderedKeys: Array<String>) {
        val ranks = HashMap<String, Int>(orderedKeys.size * 2)
        orderedKeys.forEachIndexed { index, key -> ranks[key] = index }
        rankByKey = ranks
    }

    fun snapshot(): List<MochibunNotificationItem> =
        items.values.sortedWith(
            compareBy<MochibunNotificationItem> { rankByKey[it.key] ?: Int.MAX_VALUE }
                .thenByDescending { it.postTime }
        )
}
