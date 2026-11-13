package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.CursorServiceWatchdog
import org.junit.Assert.assertEquals
import org.junit.Test

class CursorServiceWatchdogTest {

    @Test
    fun testCursorServiceComponentConstant() {
        assertEquals(
            "com.crinklecodone.mochibun/com.crinklecodone.mochibun.service.VirtualCursorService",
            CursorServiceWatchdog.SERVICE_CURSOR
        )
    }
}
