package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.LidLedManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LidLedManagerTest {

    @Test
    fun testSysfsPathsAreCanonicalAw22xxxPaths() {
        assertEquals("/sys/class/leds/aw22xxx_led/case_notification", LidLedManager.LED_PATH_NOTIFICATION)
        assertEquals("/sys/class/leds/aw22xxx_led/case_charge", LidLedManager.LED_PATH_CHARGE)
        assertEquals("/sys/class/leds/aw22xxx_led/case_message", LidLedManager.LED_PATH_MESSAGE)
        assertEquals("/sys/class/leds/aw22xxx_led/case_call", LidLedManager.LED_PATH_CALL)
        assertEquals("/sys/class/leds/aw22xxx_led/case_turnonoff", LidLedManager.LED_PATH_TURNOFF)
        assertEquals("/sys/class/leds/aw22xxx_led/rgb", LidLedManager.LED_PATH_RGB)
        assertEquals("/sys/class/leds/aw22xxx_led/task0", LidLedManager.LED_PATH_TASK0)
    }

    @Test
    fun testClearAllLedsResetsActiveFlags() {
        LidLedManager.clearAllLeds()
        assertFalse("Notification LED active flag should be false after clearAllLeds", LidLedManager.notificationLedActive)
        assertFalse("Charging LED active flag should be false after clearAllLeds", LidLedManager.chargingLedActive)
    }

    @Test
    fun testOnLidOpenedClearsLeds() {
        LidLedManager.onLidOpened()
        assertFalse(LidLedManager.notificationLedActive)
        assertFalse(LidLedManager.chargingLedActive)
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
        // Fallback
        assertEquals("0xff1493", LidLedManager.getHexColor("unknown"))
    }
}
