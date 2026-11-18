package one.crinklecod.mochibun

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import one.crinklecod.mochibun.pet.DailyGrants
import one.crinklecod.mochibun.pet.FeedResult
import one.crinklecod.mochibun.pet.PetBackupCodec
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.pet.PetPrefsStore
import one.crinklecod.mochibun.pet.PetState
import one.crinklecod.mochibun.pet.PetTuning
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PetManagerTest {

    private val hour = 3_600_000L
    private val t0 = 1_800_000_000_000L
    private val clock = FakePetClock(now = t0, day = 20_000L)
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences(PetPrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        // Unconfined runs each write immediately on the calling thread, so tests can read prefs right away
        PetManager.resetForTests(clock, Dispatchers.Unconfined)
    }

    private fun saved(): PetState = PetPrefsStore.read(prefs)

    private fun seed(state: PetState) {
        PetPrefsStore.write(prefs, state)
        PetManager.init(context)
    }

    // ── Loading ─────────────────────────────────────────────────────────────

    @Test
    fun firstRun_usesDefaults() {
        PetManager.init(context)
        assertEquals(PetState(), PetManager.state.value)
    }

    @Test
    fun init_sanitizesSavedState() {
        seed(PetState(fullness = 3f, joy = -1f, treats = 50, lastUpdatedMs = t0 + 99 * hour, stepBaseline = -5))
        val s = PetManager.state.value
        assertEquals(1f, s.fullness, 0f)
        assertEquals(0f, s.joy, 0f)
        assertEquals(PetTuning.MAX_TREATS, s.treats)
        assertEquals(t0, s.lastUpdatedMs)
        assertEquals(-1, s.stepBaseline)
    }

    @Test
    fun savedState_survivesAProcessRestart() {
        PetManager.init(context)
        assertTrue(PetManager.feed())
        val before = PetManager.state.value

        PetManager.resetForTests(clock, Dispatchers.Unconfined) // "process death"
        PetManager.init(context)
        assertEquals(before, PetManager.state.value)
    }

    // ── Persistence rules ───────────────────────────────────────────────────

    @Test
    fun feed_persistsAndReportsTreats() {
        seed(PetState(treats = 1, lastUpdatedMs = t0))
        assertTrue(PetManager.feed())
        assertEquals(0, saved().treats)
        assertFalse(PetManager.feed())
        assertEquals(0, PetManager.state.value.treats)
    }

    @Test
    fun tryFeed_whenAlreadyFull_refusesWithoutSpendingOrWriting() {
        seed(PetState(fullness = 1f, treats = 2, lastUpdatedMs = t0))
        assertEquals(FeedResult.ALREADY_FULL, PetManager.tryFeed())
        assertEquals(2, PetManager.state.value.treats)
        assertEquals(2, saved().treats)
        assertEquals(t0, saved().lastUpdatedMs)
    }

    @Test
    fun refresh_decaysInMemoryButNeverWrites() {
        seed(PetState(fullness = 0.8f, treats = 1, lastUpdatedMs = t0))
        clock.now = t0 + 10 * hour
        PetManager.refresh()

        assertEquals(0.5f, PetManager.state.value.fullness, 1e-4f)
        assertEquals(t0 + 10 * hour, PetManager.state.value.lastUpdatedMs)
        assertEquals(0.8f, saved().fullness, 0f)
        assertEquals(t0, saved().lastUpdatedMs)
    }

    @Test
    fun savedState_staysConsistent_soReloadDecaysCorrectly() {
        seed(PetState(fullness = 0.8f, treats = 2, lastUpdatedMs = t0))
        clock.now = t0 + 2 * hour
        PetManager.refresh()               // not written
        clock.now = t0 + 4 * hour
        PetManager.onNotifClearAll(1)      // written: whole snapshot at t0 + 4 h
        assertEquals(t0 + 4 * hour, saved().lastUpdatedMs)
        assertEquals(0.68f, saved().fullness, 1e-4f)

        PetManager.resetForTests(clock, Dispatchers.Unconfined)
        clock.now = t0 + 6 * hour
        PetManager.init(context)
        PetManager.refresh()
        assertEquals(0.62f, PetManager.state.value.fullness, 1e-4f) // 6 h total, not double-counted
    }

    @Test
    fun petDuringCooldown_doesNotWrite() {
        seed(PetState(joy = 0.5f, lastUpdatedMs = t0))
        PetManager.pet()
        assertEquals(t0, saved().lastPetMs)

        clock.now = t0 + 1_000L
        PetManager.pet() // reaction only; no joy, no write
        assertEquals(t0, saved().lastPetMs)
        assertEquals(t0, saved().lastUpdatedMs)
    }

    @Test
    fun stepTicks_onlyWriteWhenSomethingRealChanges() {
        seed(PetState(treats = 0, lastUpdatedMs = t0))
        val boot = t0 - 3 * hour
        PetManager.onStepsRead(10_000, boot)
        assertEquals(10_000, saved().stepBaseline)

        clock.now = t0 + 60_000L
        PetManager.onStepsRead(10_500, boot) // no treat yet: not written
        assertEquals(t0, saved().lastUpdatedMs)

        clock.now = t0 + 120_000L
        PetManager.onStepsRead(11_200, boot)
        assertEquals(1, saved().treats)
        assertEquals(11_000, saved().stepBaseline)
        assertEquals(t0 + 120_000L, saved().lastUpdatedMs)
    }

    // ── Treat sources ───────────────────────────────────────────────────────

    @Test
    fun notifClearAll_needsAtLeastOneClearedNotification() {
        seed(PetState(treats = 0, lastUpdatedMs = t0))
        PetManager.onNotifClearAll(0)
        assertEquals(0, PetManager.state.value.treats)
        PetManager.onNotifClearAll(4)
        assertEquals(1, PetManager.state.value.treats) // one treat per clear-all, however many cleared
        assertEquals(1, saved().treats)
    }

    @Test
    fun charger_grantsATreatAndJoy_untilTheDailyCap() {
        seed(PetState(joy = 0.5f, treats = 0, lastUpdatedMs = t0, dailyGrants = DailyGrants(epochDay = clock.day)))
        repeat(4) { PetManager.onChargerConnected() }
        assertEquals(PetTuning.DAILY_CAP_CHARGER, PetManager.state.value.treats)
        assertEquals(0.66f, PetManager.state.value.joy, 1e-4f)
    }

    @Test
    fun dailyCheckIn_grantsOncePerDay_andPendingEarnedTracksGrants() {
        seed(PetState(treats = 1, lastUpdatedMs = t0, dailyGrants = DailyGrants(epochDay = clock.day)))
        assertEquals(1, PetManager.claimDailyCheckIn())
        assertEquals(0, PetManager.claimDailyCheckIn())
        assertEquals(2, PetManager.state.value.treats)
        assertEquals(1, PetManager.consumePendingEarnedTreats())
        assertEquals(0, PetManager.consumePendingEarnedTreats())

        PetManager.onChargerConnected()
        PetManager.onNotifClearAll(1)
        assertEquals(2, PetManager.consumePendingEarnedTreats())

        clock.day += 1
        assertEquals(1, PetManager.claimDailyCheckIn())
        assertEquals(5, PetManager.state.value.treats)
    }

    // ── Concurrency ─────────────────────────────────────────────────────────

    private fun runConcurrently(threads: Int, action: () -> Unit) {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.execute {
                start.await()
                try { action() } finally { done.countDown() }
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun concurrentFeeds_neverSpendTheSameTreatTwice() {
        seed(PetState(fullness = 0.2f, treats = PetTuning.MAX_TREATS, lastUpdatedMs = t0))
        val fedCount = AtomicInteger()
        runConcurrently(16) { if (PetManager.feed()) fedCount.incrementAndGet() }

        // 0.2 -> 0.5 -> 0.8 -> 1.0 takes 3 feeds; the remaining 13 calls see ALREADY_FULL
        assertEquals(3, fedCount.get())
        assertEquals(PetTuning.MAX_TREATS - 3, PetManager.state.value.treats)
    }

    @Test
    fun concurrentGrants_respectTheDailyCapExactly() {
        seed(PetState(treats = 0, lastUpdatedMs = t0, dailyGrants = DailyGrants(epochDay = clock.day)))
        runConcurrently(12) { PetManager.onNotifClearAll(1) }

        assertEquals(PetTuning.DAILY_CAP_NOTIF_CLEAR, PetManager.state.value.treats)
        assertEquals(PetTuning.DAILY_CAP_NOTIF_CLEAR, PetManager.state.value.dailyGrants.notifClearTreats)
    }

    // ── Restore & backup codec ──────────────────────────────────────────────

    @Test
    fun restore_sanitizesAndPersists() {
        PetManager.init(context)
        PetManager.restore(context, PetState(fullness = 7f, treats = -2, lastPetMs = t0 + hour, stepBaseline = -3))
        val s = PetManager.state.value
        assertEquals(1f, s.fullness, 0f)
        assertEquals(0, s.treats)
        assertEquals(t0, s.lastPetMs)
        assertEquals(-1, s.stepBaseline)
        assertEquals(s, saved())
    }

    @Test
    fun backupCodec_roundTrips() {
        val s = PetState(
            fullness = 0.42f, joy = 0.9f, treats = 7, lastUpdatedMs = t0, lastPetMs = t0 - 5_000L,
            stepBaseline = 12_345, lastBootEpochMs = t0 - hour,
            dailyGrants = DailyGrants(
                epochDay = 20_000L,
                stepTreats = 2,
                notifClearTreats = 1,
                chargeTreats = 1,
                dailyCheckInTreats = 1,
            ),
        )
        val json = JSONObject(PetBackupCodec.toJson(s).toString())
        assertEquals(0.42, json.getDouble("fullness"), 0.0) // stored readably, not 0.41999998...
        assertEquals(s, PetBackupCodec.fromJson(json))
    }

    @Test
    fun backupCodec_missingOrBadFieldsFallBackToDefaults() {
        val d = PetState()
        assertEquals(d, PetBackupCodec.fromJson(JSONObject()))

        val partial = PetBackupCodec.fromJson(
            JSONObject().put("treats", 5).put("fullness", "lots").put("daily_grants", "nope")
        )
        assertEquals(5, partial.treats)
        assertEquals(d.fullness, partial.fullness, 0f)
        assertEquals(d.joy, partial.joy, 0f)
        assertEquals(d.dailyGrants, partial.dailyGrants)
    }
}
