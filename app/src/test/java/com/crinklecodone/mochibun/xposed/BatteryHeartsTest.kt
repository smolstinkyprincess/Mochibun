package com.crinklecodone.mochibun.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryHeartsTest {

    @Test
    fun testBatteryHeartsCalculation() {
        // Spec requirements:
        // filled = ceil(level / 25.0) clamped to 0..4
        // 76-100 = ♥♥♥♥
        // 51-75 = ♥♥♥♡
        // 26-50 = ♥♥♡♡
        // 1-25 = ♥♡♡♡
        // 0 = ♡♡♡♡
        // Charging: append " ✦"

        assertEquals("♡♡♡♡", BatteryHeartFormatter.format(0, false))
        assertEquals("♡♡♡♡ ✦", BatteryHeartFormatter.format(0, true))

        assertEquals("♥♡♡♡", BatteryHeartFormatter.format(1, false))
        assertEquals("♥♡♡♡", BatteryHeartFormatter.format(15, false))
        assertEquals("♥♡♡♡ ✦", BatteryHeartFormatter.format(15, true))
        assertEquals("♥♡♡♡", BatteryHeartFormatter.format(25, false))

        assertEquals("♥♥♡♡", BatteryHeartFormatter.format(26, false))
        assertEquals("♥♥♡♡", BatteryHeartFormatter.format(50, false))
        assertEquals("♥♥♡♡ ✦", BatteryHeartFormatter.format(50, true))

        assertEquals("♥♥♥♡", BatteryHeartFormatter.format(51, false))
        assertEquals("♥♥♥♡", BatteryHeartFormatter.format(75, false))
        assertEquals("♥♥♥♡ ✦", BatteryHeartFormatter.format(75, true))

        assertEquals("♥♥♥♥", BatteryHeartFormatter.format(76, false))
        assertEquals("♥♥♥♥", BatteryHeartFormatter.format(100, false))
        assertEquals("♥♥♥♥ ✦", BatteryHeartFormatter.format(100, true))

        // Clamping edge cases
        assertEquals("♡♡♡♡", BatteryHeartFormatter.format(-5, false))
        assertEquals("♥♥♥♥", BatteryHeartFormatter.format(120, false))
    }
}
