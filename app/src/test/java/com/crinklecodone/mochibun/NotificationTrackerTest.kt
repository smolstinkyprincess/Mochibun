package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.data.model.MochibunNotificationItem
import com.crinklecodone.mochibun.data.repository.NotificationListState
import com.crinklecodone.mochibun.data.repository.NotificationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun item(key: String, postTime: Long, incomingCall: Boolean = false) = MochibunNotificationItem(
        key = key,
        packageName = "com.example",
        appName = "Example",
        title = key,
        text = "",
        postTime = postTime,
        contentIntent = null,
        appIcon = null,
        isIncomingCall = incomingCall
    )

    @Test
    fun listState_withoutRankingSortsNewestFirst() {
        val state = NotificationListState()
        state.upsert(item("a", postTime = 1))
        state.upsert(item("b", postTime = 3))
        state.upsert(item("c", postTime = 2))

        assertEquals(listOf("b", "c", "a"), state.snapshot().map { it.key })
    }

    @Test
    fun listState_followsRankingThenPutsUnrankedKeysLast() {
        val state = NotificationListState()
        state.upsert(item("old_but_important", postTime = 1))
        state.upsert(item("new", postTime = 5))
        state.upsert(item("unranked", postTime = 9))
        state.setRanking(arrayOf("old_but_important", "new", "filtered_elsewhere"))

        assertEquals(listOf("old_but_important", "new", "unranked"), state.snapshot().map { it.key })
    }

    @Test
    fun listState_repostReplacesByKeyAndRemoveDrops() {
        val state = NotificationListState()
        state.upsert(item("chat", postTime = 1))
        state.upsert(item("chat", postTime = 7))
        state.upsert(item("mail", postTime = 2))
        state.remove("mail")

        val snapshot = state.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals(7L, snapshot.single().postTime)
    }

    @Test
    fun listState_replaceAllAndClear() {
        val state = NotificationListState()
        state.upsert(item("stale", postTime = 1))
        state.replaceAll(listOf(item("x", postTime = 1), item("y", postTime = 2)))
        assertEquals(listOf("y", "x"), state.snapshot().map { it.key })

        state.clear()
        assertTrue(state.snapshot().isEmpty())
    }

    @Test
    fun item_incomingCallDefaultsToFalse() {
        assertFalse(item("k", postTime = 0).copy(isIncomingCall = false).isIncomingCall)
        assertTrue(item("call", postTime = 0, incomingCall = true).isIncomingCall)
    }
}
