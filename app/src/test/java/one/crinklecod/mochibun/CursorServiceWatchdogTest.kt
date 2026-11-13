package one.crinklecod.mochibun

import one.crinklecod.mochibun.core.system.CursorServiceWatchdog
import org.junit.Assert.assertEquals
import org.junit.Test

class CursorServiceWatchdogTest {

    @Test
    fun testCursorServiceComponentConstant() {
        assertEquals(
            "one.crinklecod.mochibun/one.crinklecod.mochibun.service.VirtualCursorService",
            CursorServiceWatchdog.SERVICE_CURSOR
        )
    }
}
