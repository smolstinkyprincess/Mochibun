package one.crinklecod.mochibun.pet

import kotlin.math.abs

/**
 * Mochibunny's pet rules: pure Kotlin with no Android imports, so every rule is JVM-testable.
 *
 * There are no timers. Decay is worked out lazily from timestamps whenever something reads or
 * changes her state. Every function here is pure: it returns a new [PetState] and never touches
 * the one it was given, so it is safe inside `MutableStateFlow.update { }` retries.
 *
 * `currentEpochDay` is the device's *local* calendar day (e.g. `LocalDate.now().toEpochDay()`),
 * not a UTC day, so daily treat caps reset at the user's midnight.
 */

/** Treats granted so far today, per source. Reset whenever the local day changes. */
data class DailyGrants(
    val epochDay: Long = 0L,
    val stepTreats: Int = 0,
    val notifClearTreats: Int = 0,
    val chargeTreats: Int = 0,
    val dailyCheckInTreats: Int = 0,
)

/** Where a treat came from. Each source has its own daily cap. */
enum class TreatSource { STEPS, NOTIF_CLEAR, CHARGER, DAILY_CHECKIN }

/** Outcome of trying to feed Mochibunny. */
enum class FeedResult { FED, NO_TREATS, ALREADY_FULL }

data class PetState(
    val fullness: Float = 0.8f,        // 0f..1f
    val joy: Float = 0.8f,             // 0f..1f
    val treats: Int = 2,               // 0..PetTuning.MAX_TREATS
    val lastUpdatedMs: Long = 0L,      // 0 = never updated (first run): nothing to decay yet
    val lastPetMs: Long = 0L,          // for the pet joy cooldown; 0 = never petted
    val stepBaseline: Int = -1,        // -1 = uninitialized (first read)
    val lastBootEpochMs: Long = 0L,    // detects reboots even if steps > baseline
    val dailyGrants: DailyGrants = DailyGrants(),
)

enum class PetMood { HAPPY, CONTENT, HUNGRY, SULKY, SLEEPY }

/** Every pet number lives here so tuning never means hunting through the rules. */
object PetTuning {
    const val FULLNESS_DECAY_PER_HOUR = 0.03f
    const val JOY_DECAY_PER_HOUR = 0.03f
    /** Decay never pushes a stat below this. She gets peckish and pouty, never "empty". */
    const val STAT_FLOOR = 0.15f
    /** Longest gap that counts toward decay (a week away decays the same as two days). */
    const val MS_PER_HOUR = 3_600_000L
    const val MAX_ELAPSED_MS = 48 * MS_PER_HOUR

    const val MAX_TREATS = 9
    const val FEED_FULLNESS = 0.30f
    /** Above this fullness, feeding is refused so treats aren't wasted on a full tummy. */
    const val FULL_THRESHOLD = 0.99f
    const val PET_JOY = 0.12f
    const val PET_COOLDOWN_MS = 5_000L
    const val CHARGER_JOY = 0.08f

    const val STEPS_PER_TREAT = 1_000
    /** Wall-clock corrections shift the computed boot time a little; only a bigger jump is a reboot. */
    const val REBOOT_TOLERANCE_MS = 60_000L

    const val DAILY_CAP_STEPS = 5
    const val DAILY_CAP_NOTIF_CLEAR = 3
    const val DAILY_CAP_CHARGER = 2
    const val DAILY_CAP_DAILY_CHECKIN = 1

    const val HUNGRY_BELOW = 0.30f
    const val SULKY_BELOW = 0.30f
    const val HAPPY_AT_LEAST = 0.70f
    /** Sleepy from 22:00 until 06:59 local time. */
    const val SLEEPY_FROM_HOUR = 22
    const val SLEEPY_UNTIL_HOUR = 6

    fun dailyCap(source: TreatSource): Int = when (source) {
        TreatSource.STEPS -> DAILY_CAP_STEPS
        TreatSource.NOTIF_CLEAR -> DAILY_CAP_NOTIF_CLEAR
        TreatSource.CHARGER -> DAILY_CAP_CHARGER
        TreatSource.DAILY_CHECKIN -> DAILY_CAP_DAILY_CHECKIN
    }
}

object PetLogic {

    /**
     * Applies decay for the time since [PetState.lastUpdatedMs] and always stamps
     * `lastUpdatedMs = nowMs`. Elapsed time is clamped to `0..48 h`, so a backwards clock jump or
     * a future timestamp decays nothing and is corrected to now instead of freezing her.
     * A never-updated state (`lastUpdatedMs == 0`) decays nothing either.
     */
    fun advance(s: PetState, nowMs: Long, currentEpochDay: Long): PetState {
        val hours = decayElapsedMs(s.lastUpdatedMs, nowMs) / PetTuning.MS_PER_HOUR.toFloat()
        return s.copy(
            fullness = decay(s.fullness, PetTuning.FULLNESS_DECAY_PER_HOUR * hours),
            joy = decay(s.joy, PetTuning.JOY_DECAY_PER_HOUR * hours),
            lastUpdatedMs = nowMs,
            dailyGrants = rollDay(s.dailyGrants, currentEpochDay),
        )
    }

    /**
     * Tries to spend one treat for +[PetTuning.FEED_FULLNESS] fullness.
     * - If already full (>= [PetTuning.FULL_THRESHOLD]), returns [FeedResult.ALREADY_FULL] without
     *   spending a treat.
     * - If out of treats, returns [FeedResult.NO_TREATS].
     */
    fun tryFeed(s: PetState, nowMs: Long, currentEpochDay: Long): Pair<PetState, FeedResult> {
        val a = advance(s, nowMs, currentEpochDay)
        if (a.fullness >= PetTuning.FULL_THRESHOLD) return a to FeedResult.ALREADY_FULL
        if (a.treats <= 0) return a to FeedResult.NO_TREATS
        val fed = a.copy(
            treats = a.treats - 1,
            fullness = (a.fullness + PetTuning.FEED_FULLNESS).coerceAtMost(1f),
        )
        return fed to FeedResult.FED
    }

    /** Convenience wrapper returning `true` only when a treat was actually eaten. */
    fun feed(s: PetState, nowMs: Long, currentEpochDay: Long): Pair<PetState, Boolean> {
        val (next, result) = tryFeed(s, nowMs, currentEpochDay)
        return next to (result == FeedResult.FED)
    }

    /**
     * Petting is free and the UI always plays the reaction, but joy only grows (and the cooldown
     * only restarts) once every [PetTuning.PET_COOLDOWN_MS]. A `lastPetMs` in the future (the
     * clock jumped backwards) counts as cooled down, so it can't block joy until the clock catches up.
     */
    fun pet(s: PetState, nowMs: Long, currentEpochDay: Long): PetState {
        val a = advance(s, nowMs, currentEpochDay)
        val cooledDown = a.lastPetMs <= 0L ||
            a.lastPetMs > nowMs ||
            nowMs - a.lastPetMs >= PetTuning.PET_COOLDOWN_MS
        if (!cooledDown) return a
        return a.copy(
            joy = (a.joy + PetTuning.PET_JOY).coerceAtMost(1f),
            lastPetMs = nowMs,
        )
    }

    /** Her single mood, in priority order: hungry > sulky > sleepy > happy > content. */
    fun mood(s: PetState, localHour: Int): PetMood = when {
        s.fullness < PetTuning.HUNGRY_BELOW -> PetMood.HUNGRY
        s.joy < PetTuning.SULKY_BELOW -> PetMood.SULKY
        isSleepyHour(localHour) -> PetMood.SLEEPY
        s.fullness >= PetTuning.HAPPY_AT_LEAST && s.joy >= PetTuning.HAPPY_AT_LEAST -> PetMood.HAPPY
        else -> PetMood.CONTENT
    }

    /**
     * Turns a cumulative-since-boot step counter reading into treats (1 per 1,000 steps).
     *
     * - First read or a reboot: re-baseline and grant nothing. A reboot is a boot time that moved
     *   by more than [PetTuning.REBOOT_TOLERANCE_MS], or a counter below the baseline.
     * - Otherwise the baseline moves forward by whole thousands only, so the remainder carries
     *   over (1,900 steps = 1 treat, 900 kept). It moves even when the daily cap or a full treat
     *   bag discards the treats, so a backlog can't pile up and dump all at once tomorrow.
     */
    fun onStepsRead(
        s: PetState, rawStepCounter: Int, bootEpochMs: Long,
        nowMs: Long, currentEpochDay: Long
    ): PetState {
        val a = advance(s, nowMs, currentEpochDay)
        if (rawStepCounter < 0) return a

        val rebooted = a.stepBaseline < 0 ||
            abs(bootEpochMs - a.lastBootEpochMs) > PetTuning.REBOOT_TOLERANCE_MS ||
            rawStepCounter < a.stepBaseline
        if (rebooted) {
            return a.copy(stepBaseline = rawStepCounter, lastBootEpochMs = bootEpochMs)
        }

        val earned = (rawStepCounter - a.stepBaseline) / PetTuning.STEPS_PER_TREAT
        if (earned <= 0) return a
        val moved = a.copy(stepBaseline = a.stepBaseline + earned * PetTuning.STEPS_PER_TREAT)
        return grantTreats(moved, earned, TreatSource.STEPS, nowMs, currentEpochDay)
    }

    /**
     * Grants up to [amount] treats from [source], clamped to that source's remaining daily cap
     * and to the room left in the bag ([PetTuning.MAX_TREATS]). The daily counter only grows by
     * what was actually granted. A charger grant that gives at least one treat also adds a little joy.
     */
    fun grantTreats(
        s: PetState, amount: Int, source: TreatSource,
        nowMs: Long, currentEpochDay: Long
    ): PetState {
        val a = advance(s, nowMs, currentEpochDay)
        if (amount <= 0) return a

        val grants = a.dailyGrants
        val usedToday = when (source) {
            TreatSource.STEPS -> grants.stepTreats
            TreatSource.NOTIF_CLEAR -> grants.notifClearTreats
            TreatSource.CHARGER -> grants.chargeTreats
            TreatSource.DAILY_CHECKIN -> grants.dailyCheckInTreats
        }
        val capLeft = (PetTuning.dailyCap(source) - usedToday).coerceAtLeast(0)
        val bagRoom = (PetTuning.MAX_TREATS - a.treats).coerceAtLeast(0)
        val granted = minOf(amount, capLeft, bagRoom)
        if (granted <= 0) return a

        val newGrants = when (source) {
            TreatSource.STEPS -> grants.copy(stepTreats = grants.stepTreats + granted)
            TreatSource.NOTIF_CLEAR -> grants.copy(notifClearTreats = grants.notifClearTreats + granted)
            TreatSource.CHARGER -> grants.copy(chargeTreats = grants.chargeTreats + granted)
            TreatSource.DAILY_CHECKIN -> grants.copy(dailyCheckInTreats = grants.dailyCheckInTreats + granted)
        }
        val newJoy = if (source == TreatSource.CHARGER) {
            (a.joy + PetTuning.CHARGER_JOY).coerceAtMost(1f)
        } else {
            a.joy
        }
        return a.copy(treats = a.treats + granted, joy = newJoy, dailyGrants = newGrants)
    }

    /** Formats a 0..1 stat as 4 monochrome hearts (`♥` / `♡`), matching the SystemUI battery meter. */
    fun statHearts(value: Float): String {
        val v = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
        val filled = when {
            v >= 0.875f -> 4
            v >= 0.625f -> 3
            v >= 0.375f -> 2
            v >= 0.125f -> 1
            else -> 0
        }
        return buildString(4) {
            repeat(filled) { append('♥') }
            repeat(4 - filled) { append('♡') }
        }
    }

    /**
     * Makes a loaded or restored state safe to use. Backups are user-supplied JSON, so anything
     * may be out of range: stats clamp to 0..1 (NaN falls back to the default), treats clamp to
     * 0..MAX_TREATS, future timestamps become [nowMs], negative timestamps become 0 (unset), and a
     * negative step baseline other than -1 becomes -1 (re-baseline on the next read).
     */
    fun sanitize(s: PetState, nowMs: Long): PetState {
        val defaults = PetState()
        return s.copy(
            fullness = clampStat(s.fullness, defaults.fullness),
            joy = clampStat(s.joy, defaults.joy),
            treats = s.treats.coerceIn(0, PetTuning.MAX_TREATS),
            lastUpdatedMs = sanitizeTimestamp(s.lastUpdatedMs, nowMs),
            lastPetMs = sanitizeTimestamp(s.lastPetMs, nowMs),
            stepBaseline = if (s.stepBaseline < 0) -1 else s.stepBaseline,
            lastBootEpochMs = s.lastBootEpochMs.coerceAtLeast(0L),
            dailyGrants = s.dailyGrants.copy(
                stepTreats = s.dailyGrants.stepTreats.coerceIn(0, PetTuning.DAILY_CAP_STEPS),
                notifClearTreats = s.dailyGrants.notifClearTreats.coerceIn(0, PetTuning.DAILY_CAP_NOTIF_CLEAR),
                chargeTreats = s.dailyGrants.chargeTreats.coerceIn(0, PetTuning.DAILY_CAP_CHARGER),
                dailyCheckInTreats = s.dailyGrants.dailyCheckInTreats.coerceIn(0, PetTuning.DAILY_CAP_DAILY_CHECKIN),
            ),
        )
    }

    /** Milliseconds that count toward decay: 0 when never updated, otherwise clamped to 0..48 h. */
    internal fun decayElapsedMs(lastUpdatedMs: Long, nowMs: Long): Long =
        if (lastUpdatedMs <= 0L) 0L else (nowMs - lastUpdatedMs).coerceIn(0L, PetTuning.MAX_ELAPSED_MS)

    private fun isSleepyHour(localHour: Int): Boolean =
        localHour >= PetTuning.SLEEPY_FROM_HOUR || localHour in 0..PetTuning.SLEEPY_UNTIL_HOUR

    /** Decay never pushes a stat below the floor, and never lifts one that is already under it. */
    private fun decay(value: Float, amount: Float): Float {
        if (value <= PetTuning.STAT_FLOOR) return value
        return (value - amount).coerceAtLeast(PetTuning.STAT_FLOOR)
    }

    private fun rollDay(grants: DailyGrants, currentEpochDay: Long): DailyGrants =
        if (grants.epochDay == currentEpochDay) grants else DailyGrants(epochDay = currentEpochDay)

    private fun clampStat(value: Float, fallback: Float): Float =
        if (value.isNaN()) fallback else value.coerceIn(0f, 1f)

    private fun sanitizeTimestamp(value: Long, nowMs: Long): Long = when {
        value < 0L -> 0L
        value > nowMs -> nowMs
        else -> value
    }
}
