package com.crinklecodone.mochibun

import android.content.res.Configuration
import com.crinklecodone.mochibun.core.system.ClamshellListener
import com.crinklecodone.mochibun.core.system.ClamshellManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClamshellManagerTest {

    @Test
    fun testClamshellListenerCallbacksAndStateFlow() {
        var lidClosedFired = false
        var lidOpenedFired = false

        val testListener = object : ClamshellListener {
            override fun onLidClosed() {
                lidClosedFired = true
            }

            override fun onLidOpened() {
                lidOpenedFired = true
            }
        }

        ClamshellManager.addListener(testListener)

        try {
            // Simulate clamshell close configuration
            val closedConfig = Configuration().apply {
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES
            }
            ClamshellManager.onConfigurationChanged(closedConfig)

            assertTrue(ClamshellManager.isLidClosed.value)
            assertTrue("onLidClosed should have been notified", lidClosedFired)

            // Simulate clamshell open configuration
            val openConfig = Configuration().apply {
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO
            }
            ClamshellManager.onConfigurationChanged(openConfig)

            assertFalse(ClamshellManager.isLidClosed.value)
            assertTrue("onLidOpened should have been notified", lidOpenedFired)
        } finally {
            ClamshellManager.removeListener(testListener)
        }
    }

    @Test
    fun testListenerRemovalStopsNotifications() {
        var eventCount = 0
        val testListener = object : ClamshellListener {
            override fun onLidClosed() { eventCount++ }
            override fun onLidOpened() { eventCount++ }
        }

        ClamshellManager.addListener(testListener)
        ClamshellManager.removeListener(testListener)

        val closedConfig = Configuration().apply {
            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES
        }
        ClamshellManager.onConfigurationChanged(closedConfig)

        assertEquals(0, eventCount)
    }
}
