package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.LidLedManager
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
    fun testBuildWindowPatternScriptSetsActiveChannelsAndZerosOthers() {
        val script = LidLedManager.buildWindowPatternScript(
            activeChannels = setOf(0, 3, 6),
            r = 0xff, g = 0x14, b = 0x93
        )
        // Ch 0 active: 0x10=0xff, 0x11=0x14, 0x12=0x93
        assertTrue(script.contains("echo \"0x10 0xff\" > /sys/class/leds/aw22xxx_led/reg"))
        assertTrue(script.contains("echo \"0x11 0x14\" > /sys/class/leds/aw22xxx_led/reg"))
        assertTrue(script.contains("echo \"0x12 0x93\" > /sys/class/leds/aw22xxx_led/reg"))

        // Ch 1 inactive: 0x13=0x00, 0x14=0x00, 0x15=0x00
        assertTrue(script.contains("echo \"0x13 0x00\" > /sys/class/leds/aw22xxx_led/reg"))
        assertTrue(script.contains("echo \"0x14 0x00\" > /sys/class/leds/aw22xxx_led/reg"))
        assertTrue(script.contains("echo \"0x15 0x00\" > /sys/class/leds/aw22xxx_led/reg"))

        // Latch at end
        assertTrue(script.contains("echo \"0x05 0x02\" > /sys/class/leds/aw22xxx_led/reg"))
        assertTrue(script.contains("echo \"0x04 0x03\" > /sys/class/leds/aw22xxx_led/reg"))
    }

    @Test
    fun testBuildSolidScriptContainsAllFiveWindowChannels() {
        val script = LidLedManager.buildSolidScript(r = 0xb7, g = 0xf4, b = 0xd8)
        // Check all 5 active windows (0, 1, 3, 4, 6)
        val activeWindowRegs = listOf(0x10, 0x13, 0x19, 0x1c, 0x22)
        for (reg in activeWindowRegs) {
            assertTrue("Should contain red component for reg 0x%02x".format(reg),
                script.contains("echo \"0x%02x 0xb7\" > /sys/class/leds/aw22xxx_led/reg".format(reg)))
        }
    }

    @Test
    fun testBuildOffScriptZerosAllRegistersAndDisarmsChip() {
        val script = LidLedManager.buildOffScript()
        assertTrue(script.contains("echo \"0x05 0x00\" > /sys/class/leds/aw22xxx_led/reg"))
        assertTrue(script.contains("echo \"0x04 0x00\" > /sys/class/leds/aw22xxx_led/reg"))
        assertTrue(script.contains("echo \"0x01 0x55\" > /sys/class/leds/aw22xxx_led/reg"))
        for (reg in 0x10..0x2a) {
            assertTrue("Should zero PWM register 0x%02x".format(reg),
                script.contains("echo \"0x%02x 0x00\" > /sys/class/leds/aw22xxx_led/reg".format(reg)))
        }
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
        assertEquals("0xb7f4d8", LidLedManager.getHexColor("mint"))
        // Fallback
        assertEquals("0xff1493", LidLedManager.getHexColor("unknown"))
    }
}
