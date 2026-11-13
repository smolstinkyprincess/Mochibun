package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.CursorServiceWatchdog
import com.crinklecodone.mochibun.core.system.WatchdogResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorServiceWatchdogTest {

    @Test
    fun testCursorServiceComponentConstant() {
        assertEquals(
            "com.crinklecodone.mochibun/com.crinklecodone.mochibun.service.VirtualCursorService",
            CursorServiceWatchdog.SERVICE_CURSOR
        )
    }

    @Test
    fun testWatchdogResultVariants() {
        val healthy = WatchdogResult.Healthy
        assertTrue(healthy is WatchdogResult)

        val healed = WatchdogResult.Healed("Service restored successfully")
        assertEquals("Service restored successfully", healed.message)

        val failed = WatchdogResult.Failed("Start refused")
        assertEquals("Start refused", failed.reason)

        val disabled = WatchdogResult.Disabled
        assertTrue(disabled is WatchdogResult)
    }
}
