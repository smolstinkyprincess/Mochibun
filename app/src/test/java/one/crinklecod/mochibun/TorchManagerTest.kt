package one.crinklecod.mochibun

import one.crinklecod.mochibun.core.system.TorchManager
import org.junit.Assert.assertFalse
import org.junit.Test

class TorchManagerTest {

    @Test
    fun testTorchInitialStateIsOff() {
        assertFalse("Torch initial state should be off", TorchManager.isTorchOn)
    }
}
