package one.crinklecod.mochibun

import one.crinklecod.mochibun.pet.DailyGrants
import one.crinklecod.mochibun.pet.PetLogic
import one.crinklecod.mochibun.pet.PetMood
import one.crinklecod.mochibun.pet.PetState
import one.crinklecod.mochibun.pet.PetTuning
import one.crinklecod.mochibun.pet.TreatSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PetLogicTest {

    private val hour = 3_600_000L
    private val t0 = 1_800_000_000_000L          // an ordinary wall-clock time
    private val day = LocalDate.of(2026, 11, 15).toEpochDay()
    private val boot = t0 - 5 * hour
    private val eps = 1e-4f

    /** A state that was last updated at [t0], on [day]. */
    private fun base(
        fullness: Float = 0.8f,
        joy: Float = 0.8f,
        treats: Int = 2,
        grants: DailyGrants = DailyGrants(epochDay = day),
    ) = PetState(
        fullness = fullness,
        joy = joy,
        treats = treats,
        lastUpdatedMs = t0,
        dailyGrants = grants,
    )

    // ── Decay ────────────────────────────────────────────────────────────────

    @Test
    fun decay_isLinearPerHour() {
        val s = PetLogic.advance(base(), t0 + 10 * hour, day)
        assertEquals(0.5f, s.fullness, eps)
        assertEquals(0.5f, s.joy, eps)
        assertEquals(t0 + 10 * hour, s.lastUpdatedMs)
    }

    @Test
    fun decay_stopsAtFloor() {
        val s = PetLogic.advance(base(), t0 + 30 * hour, day)
        assertEquals(PetTuning.STAT_FLOOR, s.fullness, 0f)
        assertEquals(PetTuning.STAT_FLOOR, s.joy, 0f)
    }

    @Test
    fun decay_neverLiftsAStatAlreadyBelowFloor() {
        val s = PetLogic.advance(base(fullness = 0.1f, joy = 0.05f), t0 + 2 * hour, day)
        assertEquals(0.1f, s.fullness, 0f)
        assertEquals(0.05f, s.joy, 0f)
    }

    @Test
    fun decay_elapsedIsClampedTo48Hours() {
        assertEquals(48 * hour, PetLogic.decayElapsedMs(t0, t0 + 100 * hour))
        assertEquals(5 * hour, PetLogic.decayElapsedMs(t0, t0 + 5 * hour))
        val s = PetLogic.advance(base(fullness = 1f, joy = 1f), t0 + 100 * hour, day)
        assertEquals(PetTuning.STAT_FLOOR, s.fullness, 0f)
        assertEquals(t0 + 100 * hour, s.lastUpdatedMs)
    }

    @Test
    fun decay_negativeElapsed_decaysNothing() {
        // Clock jumped back 5 hours
        val now = t0 - 5 * hour
        assertEquals(0L, PetLogic.decayElapsedMs(t0, now))
        val s = PetLogic.advance(base(), now, day)
        assertEquals(0.8f, s.fullness, 0f)
        assertEquals(0.8f, s.joy, 0f)
        assertEquals(now, s.lastUpdatedMs)
    }

    @Test
    fun decay_futureLastUpdated_isCorrectedToNow_andDecayResumes() {
        val future = base().copy(lastUpdatedMs = t0 + 24 * hour)
        val corrected = PetLogic.advance(future, t0, day)
        assertEquals(0.8f, corrected.fullness, 0f)
        assertEquals(t0, corrected.lastUpdatedMs)

        // Not frozen: two hours later she decays two hours' worth
        val later = PetLogic.advance(corrected, t0 + 2 * hour, day)
        assertEquals(0.74f, later.fullness, eps)
    }

    @Test
    fun decay_neverUpdatedState_decaysNothing() {
        val s = PetLogic.advance(PetState(), t0, day)
        assertEquals(0.8f, s.fullness, 0f)
        assertEquals(0.8f, s.joy, 0f)
        assertEquals(t0, s.lastUpdatedMs)
    }

    // ── Sanitize ─────────────────────────────────────────────────────────────

    @Test
    fun sanitize_clampsBadRestoredValues() {
        val bad = PetState(
            fullness = 5f,
            joy = -2f,
            treats = 99,
            lastUpdatedMs = t0 + 100 * hour,
            lastPetMs = t0 + 1,
            stepBaseline = -7,
            lastBootEpochMs = -50L,
            dailyGrants = DailyGrants(epochDay = day, stepTreats = 40, notifClearTreats = -1, chargeTreats = 9),
        )
        val s = PetLogic.sanitize(bad, t0)
        assertEquals(1f, s.fullness, 0f)
        assertEquals(0f, s.joy, 0f)
        assertEquals(PetTuning.MAX_TREATS, s.treats)
        assertEquals(t0, s.lastUpdatedMs)
        assertEquals(t0, s.lastPetMs)
        assertEquals(-1, s.stepBaseline)
        assertEquals(0L, s.lastBootEpochMs)
        assertEquals(PetTuning.DAILY_CAP_STEPS, s.dailyGrants.stepTreats)
        assertEquals(0, s.dailyGrants.notifClearTreats)
        assertEquals(PetTuning.DAILY_CAP_CHARGER, s.dailyGrants.chargeTreats)
    }

    @Test
    fun sanitize_nanStatsFallBackToDefaults_andNegativeValuesAreFixed() {
        val s = PetLogic.sanitize(
            PetState(fullness = Float.NaN, joy = Float.NaN, treats = -3, lastUpdatedMs = -1L, lastPetMs = -9L),
            t0
        )
        assertEquals(PetState().fullness, s.fullness, 0f)
        assertEquals(PetState().joy, s.joy, 0f)
        assertEquals(0, s.treats)
        assertEquals(0L, s.lastUpdatedMs)
        assertEquals(0L, s.lastPetMs)
    }

    @Test
    fun sanitize_keepsValidState() {
        val good = base().copy(lastPetMs = t0 - hour, stepBaseline = 1234, lastBootEpochMs = boot)
        assertEquals(good, PetLogic.sanitize(good, t0))
        assertEquals(-1, PetLogic.sanitize(good.copy(stepBaseline = -1), t0).stepBaseline)
    }

    // ── Feed & Pet ───────────────────────────────────────────────────────────

    @Test
    fun feed_withNoTreats_returnsFalseAndOnlyAdvances() {
        val (s, fed) = PetLogic.feed(base(treats = 0), t0 + hour, day)
        assertFalse(fed)
        assertEquals(0, s.treats)
        assertEquals(0.77f, s.fullness, eps) // one hour of decay, nothing else
        assertEquals(t0 + hour, s.lastUpdatedMs)
    }

    @Test
    fun feed_spendsATreatAndAddsFullness_cappedAtOne() {
        val (s, fed) = PetLogic.feed(base(fullness = 0.4f, treats = 3), t0, day)
        assertTrue(fed)
        assertEquals(2, s.treats)
        assertEquals(0.7f, s.fullness, eps)

        val (full, fedAgain) = PetLogic.feed(base(fullness = 0.9f, treats = 1), t0, day)
        assertTrue(fedAgain)
        assertEquals(1f, full.fullness, 0f)
        assertEquals(0, full.treats)
    }

    @Test
    fun pet_respectsFiveSecondCooldown() {
        val first = PetLogic.pet(base(joy = 0.5f), t0, day)
        assertEquals(0.62f, first.joy, eps)
        assertEquals(t0, first.lastPetMs)

        val tooSoon = PetLogic.pet(first, t0 + 4_999L, day)
        assertEquals(first.joy, tooSoon.joy, eps)
        assertEquals(t0, tooSoon.lastPetMs)

        val again = PetLogic.pet(tooSoon, t0 + 5_000L, day)
        assertEquals(0.74f, again.joy, 1e-3f) // tiny decay over 5 s
        assertEquals(t0 + 5_000L, again.lastPetMs)
    }

    @Test
    fun pet_joyIsCappedAtOne_andFutureLastPetCountsAsCooledDown() {
        assertEquals(1f, PetLogic.pet(base(joy = 0.95f), t0, day).joy, 0f)

        val future = base(joy = 0.5f).copy(lastPetMs = t0 + hour)
        val s = PetLogic.pet(future, t0, day)
        assertEquals(0.62f, s.joy, eps)
        assertEquals(t0, s.lastPetMs)
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    @Test
    fun steps_firstReadOnlySetsBaseline() {
        val s = PetLogic.onStepsRead(base(), 10_000, boot, t0, day)
        assertEquals(10_000, s.stepBaseline)
        assertEquals(boot, s.lastBootEpochMs)
        assertEquals(2, s.treats)
        assertEquals(0, s.dailyGrants.stepTreats)
    }

    @Test
    fun steps_keepTheRemainder() {
        val baselined = PetLogic.onStepsRead(base(), 10_000, boot, t0, day)
        val s1 = PetLogic.onStepsRead(baselined, 11_900, boot, t0, day)
        assertEquals(3, s1.treats)
        assertEquals(11_000, s1.stepBaseline) // 900 steps carried toward the next treat
        assertEquals(1, s1.dailyGrants.stepTreats)

        val s2 = PetLogic.onStepsRead(s1, 12_100, boot, t0, day)
        assertEquals(4, s2.treats)
        assertEquals(12_000, s2.stepBaseline)

        val s3 = PetLogic.onStepsRead(s2, 12_999, boot, t0, day)
        assertEquals(s2.treats, s3.treats)
        assertEquals(12_000, s3.stepBaseline)
    }

    @Test
    fun steps_baselineStillAdvancesWhenDailyCapIsHit() {
        val capped = base(grants = DailyGrants(epochDay = day, stepTreats = PetTuning.DAILY_CAP_STEPS))
            .copy(stepBaseline = 1_000, lastBootEpochMs = boot)
        val s = PetLogic.onStepsRead(capped, 4_500, boot, t0, day)
        assertEquals(2, s.treats)
        assertEquals(4_000, s.stepBaseline)
        assertEquals(PetTuning.DAILY_CAP_STEPS, s.dailyGrants.stepTreats)
    }

    @Test
    fun steps_baselineStillAdvancesWhenTreatBagIsFull() {
        val full = base(treats = PetTuning.MAX_TREATS).copy(stepBaseline = 0, lastBootEpochMs = boot)
        val s = PetLogic.onStepsRead(full, 2_000, boot, t0, day)
        assertEquals(PetTuning.MAX_TREATS, s.treats)
        assertEquals(2_000, s.stepBaseline)
        assertEquals(0, s.dailyGrants.stepTreats)
    }

    @Test
    fun steps_rebootDetectedFromBootTimeEvenWhenCounterIsHigher() {
        val before = base().copy(stepBaseline = 500, lastBootEpochMs = boot)
        val newBoot = boot + 2 * hour
        val s = PetLogic.onStepsRead(before, 5_000, newBoot, t0, day)
        assertEquals(5_000, s.stepBaseline)
        assertEquals(newBoot, s.lastBootEpochMs)
        assertEquals(2, s.treats) // nothing granted for the post-reboot counter
    }

    @Test
    fun steps_rebootDetectedWhenCounterDropsBelowBaseline() {
        val before = base().copy(stepBaseline = 8_000, lastBootEpochMs = boot)
        val s = PetLogic.onStepsRead(before, 300, boot, t0, day)
        assertEquals(300, s.stepBaseline)
        assertEquals(2, s.treats)
    }

    @Test
    fun steps_smallBootTimeDriftIsNotAReboot() {
        val before = base().copy(stepBaseline = 500, lastBootEpochMs = boot)
        val s = PetLogic.onStepsRead(before, 2_600, boot + 30_000L, t0, day)
        assertEquals(4, s.treats)
        assertEquals(2_500, s.stepBaseline)
        assertEquals(boot, s.lastBootEpochMs)
    }

    // ── Grants & daily caps ─────────────────────────────────────────────────

    @Test
    fun grants_areCappedAtMaxTreats_andCounterOnlyCountsGranted() {
        val s = PetLogic.grantTreats(base(treats = 8), 3, TreatSource.NOTIF_CLEAR, t0, day)
        assertEquals(PetTuning.MAX_TREATS, s.treats)
        assertEquals(1, s.dailyGrants.notifClearTreats)
    }

    @Test
    fun grants_dailyCapsClampPartially() {
        var s = base(treats = 0)
        s = PetLogic.grantTreats(s, 2, TreatSource.NOTIF_CLEAR, t0, day)
        assertEquals(2, s.treats)
        s = PetLogic.grantTreats(s, 2, TreatSource.NOTIF_CLEAR, t0, day)
        assertEquals(3, s.treats) // only 1 of 2 fits under the cap of 3
        assertEquals(3, s.dailyGrants.notifClearTreats)
        s = PetLogic.grantTreats(s, 1, TreatSource.NOTIF_CLEAR, t0, day)
        assertEquals(3, s.treats)

        // Caps are per source
        s = PetLogic.grantTreats(s, 5, TreatSource.CHARGER, t0, day)
        assertEquals(5, s.treats)
        assertEquals(PetTuning.DAILY_CAP_CHARGER, s.dailyGrants.chargeTreats)
        s = PetLogic.grantTreats(s, 9, TreatSource.STEPS, t0, day)
        assertEquals(PetTuning.MAX_TREATS, s.treats) // 4 steps treats: bag room wins over cap 5
        assertEquals(4, s.dailyGrants.stepTreats)
    }

    @Test
    fun grants_nonPositiveAmountDoesNothing() {
        val s = PetLogic.grantTreats(base(), 0, TreatSource.NOTIF_CLEAR, t0, day)
        assertEquals(2, s.treats)
        assertEquals(0, s.dailyGrants.notifClearTreats)
        assertEquals(2, PetLogic.grantTreats(base(), -4, TreatSource.CHARGER, t0, day).treats)
    }

    @Test
    fun charger_joyBumpOnlyWhenATreatIsGranted() {
        val granted = PetLogic.grantTreats(base(joy = 0.5f), 1, TreatSource.CHARGER, t0, day)
        assertEquals(0.58f, granted.joy, eps)

        val capHit = base(joy = 0.5f, grants = DailyGrants(epochDay = day, chargeTreats = PetTuning.DAILY_CAP_CHARGER))
        assertEquals(0.5f, PetLogic.grantTreats(capHit, 1, TreatSource.CHARGER, t0, day).joy, 0f)

        val bagFull = base(joy = 0.5f, treats = PetTuning.MAX_TREATS)
        assertEquals(0.5f, PetLogic.grantTreats(bagFull, 1, TreatSource.CHARGER, t0, day).joy, 0f)

        // Other sources never bump joy
        assertEquals(0.5f, PetLogic.grantTreats(base(joy = 0.5f), 1, TreatSource.NOTIF_CLEAR, t0, day).joy, 0f)
    }

    @Test
    fun dayRollover_acrossYearBoundary_resetsCaps() {
        val newYearsEve = LocalDate.of(2026, 12, 31).toEpochDay()
        val newYearsDay = LocalDate.of(2027, 1, 1).toEpochDay()
        val spent = base(
            treats = 0,
            grants = DailyGrants(
                epochDay = newYearsEve,
                stepTreats = PetTuning.DAILY_CAP_STEPS,
                notifClearTreats = PetTuning.DAILY_CAP_NOTIF_CLEAR,
                chargeTreats = PetTuning.DAILY_CAP_CHARGER,
            )
        )
        // Still Dec 31: nothing left
        assertEquals(0, PetLogic.grantTreats(spent, 1, TreatSource.NOTIF_CLEAR, t0, newYearsEve).treats)

        val s = PetLogic.grantTreats(spent, 1, TreatSource.NOTIF_CLEAR, t0 + hour, newYearsDay)
        assertEquals(1, s.treats)
        assertEquals(DailyGrants(epochDay = newYearsDay, notifClearTreats = 1), s.dailyGrants)
    }

    @Test
    fun dayRollover_appliesToSteps() {
        val yesterday = day - 1
        val spent = base(grants = DailyGrants(epochDay = yesterday, stepTreats = PetTuning.DAILY_CAP_STEPS))
            .copy(stepBaseline = 0, lastBootEpochMs = boot)
        val s = PetLogic.onStepsRead(spent, 1_000, boot, t0, day)
        assertEquals(3, s.treats)
        assertEquals(DailyGrants(epochDay = day, stepTreats = 1), s.dailyGrants)
    }

    // ── Mood ────────────────────────────────────────────────────────────────

    @Test
    fun mood_followsPriorityOrder() {
        val noon = 12
        // Hungry beats everything
        assertEquals(PetMood.HUNGRY, PetLogic.mood(PetState(fullness = 0.2f, joy = 0.1f), 2))
        // Sulky beats sleepy
        assertEquals(PetMood.SULKY, PetLogic.mood(PetState(fullness = 0.9f, joy = 0.2f), 2))
        // Sleepy beats happy
        assertEquals(PetMood.SLEEPY, PetLogic.mood(PetState(fullness = 0.9f, joy = 0.9f), 23))
        assertEquals(PetMood.HAPPY, PetLogic.mood(PetState(fullness = 0.9f, joy = 0.9f), noon))
        assertEquals(PetMood.CONTENT, PetLogic.mood(PetState(fullness = 0.5f, joy = 0.9f), noon))
    }

    @Test
    fun mood_thresholdsAndSleepyHours() {
        assertEquals(PetMood.CONTENT, PetLogic.mood(PetState(fullness = 0.30f, joy = 0.30f), 12))
        assertEquals(PetMood.HAPPY, PetLogic.mood(PetState(fullness = 0.70f, joy = 0.70f), 12))
        val happy = PetState(fullness = 0.9f, joy = 0.9f)
        for (h in listOf(22, 23, 0, 3, 6)) assertEquals("hour $h", PetMood.SLEEPY, PetLogic.mood(happy, h))
        for (h in listOf(7, 12, 21)) assertEquals("hour $h", PetMood.HAPPY, PetLogic.mood(happy, h))
    }
}
