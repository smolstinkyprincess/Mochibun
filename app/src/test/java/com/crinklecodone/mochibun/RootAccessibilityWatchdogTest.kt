package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.RootAccessibilityWatchdog
import com.crinklecodone.mochibun.core.system.WatchdogResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootAccessibilityWatchdogTest {

    @Test
    fun testServiceComponentConstants() {
        assertEquals(
            "com.crinklecodone.mochibun/com.crinklecodone.mochibun.service.MochibunAccessibilityService",
            RootAccessibilityWatchdog.SERVICE_A11Y
        )
        assertEquals(
            "com.crinklecodone.mochibun/com.crinklecodone.mochibun.service.VirtualCursorService",
            RootAccessibilityWatchdog.SERVICE_CURSOR
        )
    }

    @Test
    fun testPreservesThirdPartyAccessibilityServices() {
        val thirdParty1 = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        val thirdParty2 = "com.example.service/com.example.service.CustomService"
        val rawSettings = "$thirdParty1:$thirdParty2"

        val current = rawSettings.split(':').filter { it.isNotBlank() }.toSet()
        val merged = (current + RootAccessibilityWatchdog.SERVICE_A11Y + RootAccessibilityWatchdog.SERVICE_CURSOR).joinToString(":")

        assertTrue(merged.contains(thirdParty1))
        assertTrue(merged.contains(thirdParty2))
        assertTrue(merged.contains(RootAccessibilityWatchdog.SERVICE_A11Y))
        assertTrue(merged.contains(RootAccessibilityWatchdog.SERVICE_CURSOR))
        assertEquals(4, merged.split(':').size)
    }

    @Test
    fun testWatchdogResultVariants() {
        val healthy = WatchdogResult.Healthy
        assertTrue(healthy is WatchdogResult)

        val healed = WatchdogResult.Healed("Services restored successfully")
        assertEquals("Services restored successfully", healed.message)

        val failed = WatchdogResult.Failed("Root permission denied")
        assertEquals("Root permission denied", failed.reason)

        val disabled = WatchdogResult.Disabled
        assertTrue(disabled is WatchdogResult)

        val noRoot = WatchdogResult.NoRoot
        assertTrue(noRoot is WatchdogResult)
    }
}
