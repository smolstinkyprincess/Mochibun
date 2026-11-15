package one.crinklecod.mochibun

import kotlinx.coroutines.Dispatchers
import one.crinklecod.mochibun.data.repository.NotificationTracker
import one.crinklecod.mochibun.pet.ChargerPlugTracker
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.pet.PetState
import one.crinklecod.mochibun.pet.PetTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The treat-earning hooks: step counter readings, notification clear-all, and charger plug-ins,
 * including their daily caps. PetManager is not init()ed here, so nothing is written to disk.
 */
class PetTreatHooksTest {

    private val hour = 3_600_000L
    private val t0 = 1_800_000_000_000L
    private val clock = FakePetClock(now = t0, day = 20_000L, boot = t0 - 5 * hour)

    @Before
    fun setUp() {
        PetManager.resetForTests(clock, Dispatchers.Unconfined)
    }

    private val treats get() = PetManager.state.value.treats

    // ── Charger: only a real unplugged -> plugged change counts ─────────────

    @Test
    fun charger_firstReadingIsOnlyABaseline() {
        val unplugged = ChargerPlugTracker()
        assertNull(unplugged.isPlugged)
        unplugged.onBaseline(false)
        assertTrue(unplugged.onPowerConnected())

        val alreadyCharging = ChargerPlugTracker()
        alreadyCharging.onBaseline(true) // sticky broadcast at startup while plugged in
        assertFalse(alreadyCharging.onPowerConnected())

        // No baseline at all yet: still not a grant
        assertFalse(ChargerPlugTracker().onPowerConnected())
    }

    @Test
    fun charger_laterBatteryReadingsNeverChangeTheBaseline() {
        val tracker = ChargerPlugTracker()
        tracker.onBaseline(false)
        tracker.onBaseline(true) // a status update arriving before POWER_CONNECTED
        assertTrue(tracker.onPowerConnected())
    }

    @Test
    fun charger_repeatedConnectWithoutUnplugDoesNotCount() {
        val tracker = ChargerPlugTracker()
        tracker.onBaseline(false)
        assertTrue(tracker.onPowerConnected())
        assertFalse(tracker.onPowerConnected())
        tracker.onPowerDisconnected()
        assertTrue(tracker.onPowerConnected())
    }

    @Test
    fun charger_plugCyclesGrantUpToTheDailyCap() {
        val tracker = ChargerPlugTracker()
        tracker.onBaseline(false)
        val start = treats
        repeat(4) {
            if (tracker.onPowerConnected()) PetManager.onChargerConnected()
            tracker.onPowerDisconnected()
        }
        assertEquals(start + PetTuning.DAILY_CAP_CHARGER, treats)
    }

    // ── Steps ───────────────────────────────────────────────────────────────

    @Test
    fun steps_sensorReadingsEarnOneTreatPerThousand_upToTheDailyCap() {
        val start = treats
        PetManager.onStepCounterSensor(2_000f) // first reading: baseline only
        assertEquals(start, treats)

        PetManager.onStepCounterSensor(3_500f)
        assertEquals(start + 1, treats)
        assertEquals(3_000, PetManager.state.value.stepBaseline)

        PetManager.onStepCounterSensor(20_000f) // 17 more treats' worth, capped at 5 a day
        assertEquals(start + PetTuning.DAILY_CAP_STEPS, treats)
        assertEquals(20_000, PetManager.state.value.stepBaseline)
    }

    @Test
    fun steps_rebootIsDetectedFromTheBootTime() {
        PetManager.onStepCounterSensor(500f)
        val start = treats
        clock.boot = t0 - 10_000L // phone rebooted 10 s ago
        PetManager.onStepCounterSensor(4_000f)
        assertEquals(start, treats)
        assertEquals(4_000, PetManager.state.value.stepBaseline)
        assertEquals(clock.boot, PetManager.state.value.lastBootEpochMs)
    }

    @Test
    fun steps_badSensorValuesAreIgnored() {
        PetManager.onStepCounterSensor(-1f)
        PetManager.onStepCounterSensor(Float.NaN)
        assertEquals(-1, PetManager.state.value.stepBaseline)
    }

    // ── Notification clear-all ──────────────────────────────────────────────

    @Test
    fun clearAll_needsAtLeastOneNotification_andIsCappedDaily() {
        val start = treats
        PetManager.onNotifClearAll(0)
        assertEquals(start, treats)

        repeat(5) { PetManager.onNotifClearAll(2) }
        assertEquals(start + PetTuning.DAILY_CAP_NOTIF_CLEAR, treats)
    }

    @Test
    fun clearAll_onAnEmptyListEarnsNothing() {
        NotificationTracker.cancelAllNotifications() // empty in tests: nothing was cleared
        assertEquals(PetState().treats, treats)
        assertEquals(0, PetManager.state.value.dailyGrants.notifClearTreats)
    }
}
