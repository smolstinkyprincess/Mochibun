package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.LidLedManager
import com.crinklecodone.mochibun.core.system.LidLedMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LidLedManagerTest {

    @Test
    fun testSysfsPathsAreCanonicalAw22xxxPaths() {
        assertEquals("/sys/class/leds/aw22xxx_led/reg", LidLedManager.LED_PATH_REG)
        assertEquals("/sys/class/leds/aw22xxx_led/case_notification", LidLedManager.LED_PATH_NOTIFICATION)
        assertEquals("/sys/class/leds/aw22xxx_led/case_charge", LidLedManager.LED_PATH_CHARGE)
        assertEquals("/sys/class/leds/aw22xxx_led/case_message", LidLedManager.LED_PATH_MESSAGE)
        assertEquals("/sys/class/leds/aw22xxx_led/case_call", LidLedManager.LED_PATH_CALL)
        assertEquals("/sys/class/leds/aw22xxx_led/case_turnonoff", LidLedManager.LED_PATH_TURNOFF)
        assertEquals("/sys/class/leds/aw22xxx_led/case_factory_test", LidLedManager.LED_PATH_FACTORY_TEST)
        assertEquals("/sys/class/leds/aw22xxx_led/rgb", LidLedManager.LED_PATH_RGB)
        assertEquals("/sys/class/leds/aw22xxx_led/task0", LidLedManager.LED_PATH_TASK0)
        assertEquals("/sys/class/leds/aw22xxx_led/effect", LidLedManager.LED_PATH_EFFECT)
        assertEquals("/sys/class/leds/aw22xxx_led/brightness", LidLedManager.LED_PATH_BRIGHTNESS)
    }

    @Test
    fun testPhysicalWindowChannelMappings() {
        assertEquals(setOf(0, 3, 6), LidLedManager.CHANNELS_10101)
        assertEquals(setOf(1, 4), LidLedManager.CHANNELS_01010)
        assertEquals(setOf(0, 1, 3, 4, 6), LidLedManager.ALL_WINDOW_CHANNELS)
    }

    @Test
    fun testParseRgb() {
        val (r1, g1, b1) = LidLedManager.parseRgb("0xff1493")
        assertEquals(0xff, r1)
        assertEquals(0x14, g1)
        assertEquals(0x93, b1)

        val (r2, g2, b2) = LidLedManager.parseRgb("#00ff7f")
        assertEquals(0x00, r2)
        assertEquals(0xff, g2)
        assertEquals(0x7f, b2)
    }

    @Test
    fun testBuildOffCommand() {
        val cmd = LidLedManager.buildOffCommand("/mock/lidled")
        assertEquals("'/mock/lidled' off", cmd)
    }

    @Test
    fun testBuildSolidCommand() {
        val cmd = LidLedManager.buildSolidCommand("0xff1493", "/mock/lidled")
        assertEquals("'/mock/lidled' pattern 11111 0xff1493", cmd)
    }

    @Test
    fun testBuildPatternCommand() {
        val cmd10101 = LidLedManager.buildPatternCommand("10101", "0xff1493", "/mock/lidled")
        assertEquals("'/mock/lidled' pattern 10101 0xff1493", cmd10101)

        val cmd01010 = LidLedManager.buildPatternCommand("01010", "0x00ced1", "/mock/lidled")
        assertEquals("'/mock/lidled' pattern 01010 0x00ced1", cmd01010)
    }

    @Test
    fun testBuildShimmerCommand() {
        val notifCmd = LidLedManager.buildShimmerCommand(
            hexColor = "0xff1493",
            frameSeconds = "0.8",
            cycles = 6,
            settleMode = "10101",
            helperPath = "/mock/lidled"
        )
        assertEquals("'/mock/lidled' shimmer 0xff1493 0.8 6 10101", notifCmd)

        val callCmd = LidLedManager.buildShimmerCommand(
            hexColor = "0xff1493",
            frameSeconds = "0.4",
            cycles = 56,
            settleMode = "solid",
            helperPath = "/mock/lidled"
        )
        assertEquals("'/mock/lidled' shimmer 0xff1493 0.4 56 solid", callCmd)
    }

    @Test
    fun testClearAllLedsResetsActiveFlags() {
        LidLedManager.clearAllLeds()
        assertFalse("Notification LED active flag should be false after clearAllLeds", LidLedManager.notificationLedActive)
        assertFalse("Charging LED active flag should be false after clearAllLeds", LidLedManager.chargingLedActive)
    }

    @Test
    fun testStopLedsResetsActiveFlags() {
        LidLedManager.stopLeds()
        assertFalse("Notification LED active flag should be false after stopLeds", LidLedManager.notificationLedActive)
        assertFalse("Charging LED active flag should be false after stopLeds", LidLedManager.chargingLedActive)
    }

    @Test
    fun testLadderIsOffWhenFlipOpenOrDisabled() {
        // An open flip (including a display timeout with the flip open) never lights the lid
        assertEquals(LidLedMode.OFF, LidLedManager.resolveMode(
            lidClosed = false, enabled = true, hasIncomingCall = true, hasNotifications = true, chargingLight = true))
        assertEquals(LidLedMode.OFF, LidLedManager.resolveMode(
            lidClosed = true, enabled = false, hasIncomingCall = true, hasNotifications = true, chargingLight = true))
        assertEquals(LidLedMode.OFF, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = true, chargingLight = true, batteryPct = 4))
    }

    @Test
    fun testCriticalBatteryTurnsOffLeds() {
        assertEquals(LidLedMode.OFF, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = true, chargingLight = false, batteryPct = 4))
        assertEquals(LidLedMode.OFF, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = false, chargingLight = true, batteryPct = 4))
        assertEquals(LidLedMode.NOTIFICATION, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = true, chargingLight = true, batteryPct = 5))
        assertEquals(LidLedMode.CHARGING, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = false, chargingLight = true, batteryPct = 5))
    }

    @Test
    fun testLadderPriorityCallThenNotificationThenCharging() {
        assertEquals(LidLedMode.CALL, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = true, hasNotifications = true, chargingLight = true))
        assertEquals(LidLedMode.NOTIFICATION, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = true, chargingLight = true))
        assertEquals(LidLedMode.CHARGING, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = false, chargingLight = true))
        assertEquals(LidLedMode.OFF, LidLedManager.resolveMode(
            lidClosed = true, enabled = true, hasIncomingCall = false, hasNotifications = false, chargingLight = false))
    }

    @Test
    fun testShimmerDuration() {
        assertEquals(9_600L, LidLedManager.shimmerDurationMs("0.8", 6))
        assertEquals(44_800L, LidLedManager.shimmerDurationMs("0.4", 56))
    }

    @Test
    fun testMockChargingState() {
        LidLedManager.setMockChargingState(true)
        assertEquals(true, LidLedManager.isChargingState)
        LidLedManager.setMockChargingState(false)
        assertEquals(false, LidLedManager.isChargingState)
    }

    @Test
    fun testGetHexColorMappings() {
        assertEquals("0xff1493", LidLedManager.getHexColor("pink"))
        assertEquals("0x9370db", LidLedManager.getHexColor("purple"))
        assertEquals("0xffffff", LidLedManager.getHexColor("white"))
        assertEquals("0x00ff7f", LidLedManager.getHexColor("green"))
        assertEquals("0xb7f4d8", LidLedManager.getHexColor("mint"))
        // Fallback
        assertEquals("0xff1493", LidLedManager.getHexColor("unknown"))
    }

    @Test
    fun testShimmerActiveGuardSkipsRetriggerForSameNotification() {
        val colorHex = "0xff1493"
        val postTime = 1000L
        val startTime = 5000L
        val activeUntil = startTime + 9600L

        LidLedManager.setMockAppliedState(LidLedMode.NOTIFICATION, colorHex, postTime)
        LidLedManager.setMockShimmerState(startTime, activeUntil, postTime)

        // Same postTime during active shimmer: must skip
        assertTrue(
            LidLedManager.shouldSkipShimmer(
                mode = LidLedMode.NOTIFICATION,
                hex = colorHex,
                newestPostTime = postTime,
                now = startTime + 200L
            )
        )
    }

    @Test
    fun testShimmerDebounceWindowSkipsRetriggerDuringScreenOffCascade() {
        val colorHex = "0xff1493"
        val initialPostTime = 1000L
        val startTime = 5000L
        val activeUntil = startTime + 9600L

        LidLedManager.setMockAppliedState(LidLedMode.NOTIFICATION, colorHex, initialPostTime)
        LidLedManager.setMockShimmerState(startTime, activeUntil, initialPostTime)

        // Screen-off cascade ~200ms later with slightly bumped postTime: must skip within debounce window
        val bumpedPostTime = 1050L
        assertTrue(
            LidLedManager.shouldSkipShimmer(
                mode = LidLedMode.NOTIFICATION,
                hex = colorHex,
                newestPostTime = bumpedPostTime,
                now = startTime + 200L
            )
        )
    }

    @Test
    fun testShimmerAllowsNewNotificationAfterDebounce() {
        val colorHex = "0xff1493"
        val initialPostTime = 1000L
        val startTime = 5000L
        val activeUntil = startTime + 9600L

        LidLedManager.setMockAppliedState(LidLedMode.NOTIFICATION, colorHex, initialPostTime)
        LidLedManager.setMockShimmerState(startTime, activeUntil, initialPostTime)

        // Truly new notification arriving after debounce window: must NOT skip
        val newPostTime = 2000L
        assertFalse(
            LidLedManager.shouldSkipShimmer(
                mode = LidLedMode.NOTIFICATION,
                hex = colorHex,
                newestPostTime = newPostTime,
                now = startTime + 2500L
            )
        )
    }
}
