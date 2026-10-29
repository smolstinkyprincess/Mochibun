package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.core.system.ClamshellListener
import com.crinklecodone.mochibun.core.system.ClamshellManager
import com.crinklecodone.mochibun.core.system.ClamshellState
import com.crinklecodone.mochibun.core.system.HallSwitchReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClamshellManagerTest {

    private val events = mutableListOf<String>()

    private val recorder = object : ClamshellListener {
        override fun onLidClosed() { events += "lidClosed" }
        override fun onLidOpened() { events += "lidOpened" }
        override fun onDisplaySleep() { events += "sleep" }
        override fun onDisplayWake() { events += "wake" }
    }

    @Before
    fun setUp() {
        ClamshellManager.resetForTest()
        ClamshellManager.addListener(recorder)
    }

    @After
    fun tearDown() {
        ClamshellManager.removeListener(recorder)
        ClamshellManager.resetForTest()
    }

    @Test
    fun screenTimeoutWithFlipOpenNeverClosesTheLid() {
        ClamshellManager.onDisplayPowerChanged(awake = false)

        assertEquals(ClamshellState.OPEN_DOZING, ClamshellManager.state.value)
        assertFalse(ClamshellManager.isLidClosed.value)
        assertEquals(listOf("sleep"), events)

        ClamshellManager.onDisplayPowerChanged(awake = true)
        assertEquals(ClamshellState.OPEN_AWAKE, ClamshellManager.state.value)
        assertEquals(listOf("sleep", "wake"), events)
    }

    @Test
    fun closingTheFlipFiresLidClosedOnceThenSleeps() {
        ClamshellManager.onHallChanged(closed = true)
        assertEquals(ClamshellState.CLOSED_ACTIVE, ClamshellManager.state.value)

        ClamshellManager.onDisplayPowerChanged(awake = false)
        assertEquals(ClamshellState.CLOSED_SLEEP, ClamshellManager.state.value)
        assertTrue(ClamshellManager.isLidClosed.value)
        assertEquals(listOf("lidClosed", "sleep"), events)
    }

    @Test
    fun closingAfterADisplayTimeoutStillClosesTheLid() {
        ClamshellManager.onDisplayPowerChanged(awake = false)
        ClamshellManager.onHallChanged(closed = true)

        assertEquals(ClamshellState.CLOSED_SLEEP, ClamshellManager.state.value)
        assertEquals(listOf("sleep", "lidClosed"), events)
    }

    @Test
    fun openingTheFlipFiresLidOpenedAndWake() {
        ClamshellManager.onHallChanged(closed = true)
        ClamshellManager.onDisplayPowerChanged(awake = false)
        events.clear()

        ClamshellManager.onHallChanged(closed = false)
        ClamshellManager.onDisplayPowerChanged(awake = true)

        assertEquals(ClamshellState.OPEN_AWAKE, ClamshellManager.state.value)
        assertEquals(listOf("lidOpened", "wake"), events)
    }

    @Test
    fun powerKeyWithFlipClosedStaysClosed() {
        ClamshellManager.onHallChanged(closed = true)
        ClamshellManager.onDisplayPowerChanged(awake = false)
        events.clear()

        ClamshellManager.onDisplayPowerChanged(awake = true)

        assertEquals(ClamshellState.CLOSED_ACTIVE, ClamshellManager.state.value)
        assertTrue(ClamshellManager.isLidClosed.value)
        assertEquals(listOf("wake"), events)
    }

    @Test
    fun repeatedInputsDoNotRefireCallbacks() {
        ClamshellManager.onHallChanged(closed = true)
        ClamshellManager.onHallChanged(closed = true)
        ClamshellManager.onDisplayPowerChanged(awake = true)

        assertEquals(listOf("lidClosed"), events)
    }

    @Test
    fun stateChangeReportsPreviousAndCurrent() {
        val transitions = mutableListOf<Pair<ClamshellState, ClamshellState>>()
        val listener = object : ClamshellListener {
            override fun onStateChanged(previous: ClamshellState, current: ClamshellState) {
                transitions += previous to current
            }
        }
        ClamshellManager.addListener(listener)
        try {
            ClamshellManager.onDisplayPowerChanged(awake = false)
            ClamshellManager.onHallChanged(closed = true)
        } finally {
            ClamshellManager.removeListener(listener)
        }

        assertEquals(
            listOf(
                ClamshellState.OPEN_AWAKE to ClamshellState.OPEN_DOZING,
                ClamshellState.OPEN_DOZING to ClamshellState.CLOSED_SLEEP
            ),
            transitions
        )
    }

    @Test
    fun listenerRemovalStopsNotifications() {
        ClamshellManager.removeListener(recorder)
        ClamshellManager.onHallChanged(closed = true)
        assertTrue(events.isEmpty())
    }

    @Test
    fun stateMapping() {
        assertEquals(ClamshellState.OPEN_AWAKE, ClamshellState.of(isClosed = false, isAwake = true))
        assertEquals(ClamshellState.OPEN_DOZING, ClamshellState.of(isClosed = false, isAwake = false))
        assertEquals(ClamshellState.CLOSED_ACTIVE, ClamshellState.of(isClosed = true, isAwake = true))
        assertEquals(ClamshellState.CLOSED_SLEEP, ClamshellState.of(isClosed = true, isAwake = false))
    }

    @Test
    fun parsesHallKeysFromGetevent() {
        // Lines as printed by `getevent -ql /dev/input/event0` on the AT-M120
        assertEquals(true, HallSwitchReader.parseHallLine("EV_KEY       KEY_SLEEP            DOWN"))
        assertEquals(false, HallSwitchReader.parseHallLine("EV_KEY       KEY_WAKEUP           DOWN"))
        assertNull(HallSwitchReader.parseHallLine("EV_KEY       KEY_SLEEP            UP"))
        assertNull(HallSwitchReader.parseHallLine("EV_KEY       KEY_5                DOWN"))
        assertNull(HallSwitchReader.parseHallLine("EV_SYN       SYN_REPORT           00000000"))
    }
}
