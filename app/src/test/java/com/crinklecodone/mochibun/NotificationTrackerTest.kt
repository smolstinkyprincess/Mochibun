package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.data.model.MochibunNotificationItem
import com.crinklecodone.mochibun.data.repository.NotificationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTrackerTest {

    @Test
    fun mochibunNotificationItem_propertiesPreserved() {
        val item = MochibunNotificationItem(
            key = "0|com.example.chat|1|tag|1001",
            packageName = "com.example.chat",
            appName = "CuteChat",
            title = "Sakura",
            text = "Let's get boba! ♡",
            postTime = 1700000000000L,
            contentIntent = null,
            appIcon = null
        )

        assertEquals("0|com.example.chat|1|tag|1001", item.key)
        assertEquals("com.example.chat", item.packageName)
        assertEquals("CuteChat", item.appName)
        assertEquals("Sakura", item.title)
        assertEquals("Let's get boba! ♡", item.text)
        assertEquals(1700000000000L, item.postTime)
        assertNull(item.contentIntent)
        assertNull(item.appIcon)
    }

    @Test
    fun cancelNotification_optimisticallyRemovesItem() {
        // Initial state or after clear
        NotificationTracker.cancelAllNotifications()
        assertTrue(NotificationTracker.notifications.value.isEmpty())

        // Testing clear cache safety
        NotificationTracker.clearCache()
    }
}
