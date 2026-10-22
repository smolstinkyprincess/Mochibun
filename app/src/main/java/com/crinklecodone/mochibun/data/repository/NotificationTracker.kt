package com.crinklecodone.mochibun.data.repository

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import com.crinklecodone.mochibun.data.model.MochibunNotificationItem
import com.crinklecodone.mochibun.service.MochibunNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Lightweight reactive notification tracker and state holder for Mochibun.
 * Hooks into [MochibunNotificationListenerService] and exposes filtered notifications
 * to Compose while respecting low-RAM device constraints.
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

    fun onNotificationListenerConnected(service: MochibunNotificationListenerService) {
        Log.d(TAG, "NotificationListener connected")
        activeServiceRef = WeakReference(service)
        _isServiceConnected.value = true
        syncActiveNotifications(service)
    }

    fun onNotificationListenerDisconnected() {
        Log.d(TAG, "NotificationListener disconnected")
        activeServiceRef = null
        _isServiceConnected.value = false
    }

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val service = activeServiceRef?.get() ?: return
        syncActiveNotifications(service)
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val service = activeServiceRef?.get()
        if (service != null) {
            syncActiveNotifications(service)
        } else {
            _notifications.value = _notifications.value.filter { it.key != sbn.key }
        }
    }

    fun syncActiveNotifications(service: MochibunNotificationListenerService) {
        scope.launch {
            try {
                val activeSbns = service.activeNotifications ?: emptyArray()
                val items = activeSbns.mapNotNull { sbn ->
                    parseNotification(service, sbn)
                }.sortedByDescending { it.postTime }

                _notifications.value = items
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query active notifications", e)
            }
        }
    }

    fun refresh(context: Context) {
        val service = activeServiceRef?.get()
        if (service != null) {
            syncActiveNotifications(service)
        }
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
            appIcon = appIcon
        )
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
        _notifications.value = _notifications.value.filter { it.key != key }
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
        _notifications.value = emptyList()
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
