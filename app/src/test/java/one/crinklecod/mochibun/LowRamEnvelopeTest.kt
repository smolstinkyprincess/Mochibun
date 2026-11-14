package one.crinklecod.mochibun

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LowRamEnvelopeTest {
    @Test
    fun oneGbPhonesAreTiny() {
        assertTrue(isOneGbClassDevice(950L * 1024 * 1024))
    }

    @Test
    fun atM120KeepsFullBudget() {
        // AT-M120 reports ~1.8 GB of usable RAM
        assertFalse(isOneGbClassDevice(1_850L * 1024 * 1024))
    }

    @Test
    fun unknownRamIsNotTiny() {
        assertFalse(isOneGbClassDevice(0L))
    }
}
