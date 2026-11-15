package one.crinklecod.mochibun.pet

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Wall clock + local calendar for the pet. Swappable so tests can control time. */
interface PetClock {
    fun nowMs(): Long

    /** The device's local calendar day for [nowMs] (not a UTC day). */
    fun epochDay(nowMs: Long): Long

    /** The local hour of day (0..23) for [nowMs]. */
    fun localHour(nowMs: Long): Int

    /** When the current boot started, on the wall clock (shifts slightly if the clock is corrected). */
    fun bootEpochMs(nowMs: Long): Long

    object System : PetClock {
        override fun nowMs(): Long = java.lang.System.currentTimeMillis()
        override fun epochDay(nowMs: Long): Long =
            Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        override fun localHour(nowMs: Long): Int =
            Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
        override fun bootEpochMs(nowMs: Long): Long = nowMs - SystemClock.elapsedRealtime()
    }
}

/**
 * Thin singleton around [PetLogic]: holds Mochibunny's live [state] and persists it.
 *
 * - No timers or background work: decay is applied whenever something calls in.
 * - Every mutation is atomic against [_state] (`update` / compare-and-set), so sensor, broadcast
 *   and UI events can't overwrite each other. The lambdas are pure because `update` may re-run them.
 * - Writes happen on one serial IO worker, after the state has changed, and always save the
 *   latest whole state. [refresh] (decay only) never writes: decay is recomputed from the saved
 *   `lastUpdatedMs`, so skipping those writes loses nothing.
 */
object PetManager {

    private val _state = MutableStateFlow(PetState())
    val state: StateFlow<PetState> = _state.asStateFlow()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    internal var clock: PetClock = PetClock.System

    /** Serial so writes never interleave. Tests swap in `Dispatchers.Unconfined`. */
    internal var writeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val scope = CoroutineScope(SupervisorJob())

    /** Set by [init], then only touched on [writeDispatcher], which runs one write at a time. */
    @Volatile
    private var lastWrittenKey: PersistKey? = null

    /** Loads the saved state (sanitized). Safe to call more than once; only the first call loads. */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PetPrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
            val loaded = PetLogic.sanitize(PetPrefsStore.read(p), clock.nowMs())
            _state.value = loaded
            lastWrittenKey = persistKey(loaded)
            prefs = p
        }
    }

    /** Applies decay up to now (e.g. when Home resumes). In memory only: never writes prefs. */
    fun refresh() {
        val now = clock.nowMs()
        val day = clock.epochDay(now)
        _state.update { PetLogic.advance(it, now, day) }
    }

    /** Her mood right now, decayed to the current time without changing the stored state. */
    fun currentMood(): PetMood {
        val now = clock.nowMs()
        return PetLogic.mood(PetLogic.advance(_state.value, now, clock.epochDay(now)), clock.localHour(now))
    }

    /**
     * Spends a treat if she has one. Returns `true` when she was fed, `false` when there were no
     * treats (the UI shows the "no treats yet" bubble from this, outside any state lambda).
     */
    fun feed(): Boolean {
        val now = clock.nowMs()
        val day = clock.epochDay(now)
        // A compare-and-set loop instead of update {}: update can't hand back the fed flag safely.
        while (true) {
            val current = _state.value
            val (next, fed) = PetLogic.feed(current, now, day)
            if (_state.compareAndSet(current, next)) {
                if (fed) persist()
                return fed
            }
        }
    }

    /** Petting is free; joy only grows once per cooldown (see [PetLogic.pet]). */
    fun pet() {
        val now = clock.nowMs()
        val day = clock.epochDay(now)
        _state.update { PetLogic.pet(it, now, day) }
        persist()
    }

    /** A step-counter reading: cumulative steps since boot, and when this boot started (wall clock). */
    fun onStepsRead(rawStepCounter: Int, bootEpochMs: Long) {
        val now = clock.nowMs()
        val day = clock.epochDay(now)
        _state.update { PetLogic.onStepsRead(it, rawStepCounter, bootEpochMs, now, day) }
        persist()
    }

    /**
     * A raw TYPE_STEP_COUNTER value (cumulative steps since boot). Works out the boot time itself,
     * so a reboot is noticed even when the new counter is already above the old baseline.
     */
    fun onStepCounterSensor(rawSteps: Float) {
        if (!(rawSteps >= 0f)) return // negative or NaN: ignore
        val raw = rawSteps.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        onStepsRead(raw, clock.bootEpochMs(clock.nowMs()))
    }

    /** Clearing at least one notification earns a treat (capped daily). Clearing nothing earns nothing. */
    fun onNotifClearAll(clearedCount: Int) {
        if (clearedCount < 1) return
        grant(TreatSource.NOTIF_CLEAR)
    }

    /** Call only on a real not-charging -> charging transition, never for a first or sticky reading. */
    fun onChargerConnected() {
        grant(TreatSource.CHARGER)
    }

    /** Replaces her state from a backup. The values are user-supplied, so they are sanitized first. */
    fun restore(context: Context, restored: PetState) {
        init(context)
        _state.value = PetLogic.sanitize(restored, clock.nowMs())
        persist(force = true)
    }

    /** The state to put in a backup: live state once initialized, otherwise what is saved on disk. */
    fun stateForBackup(context: Context): PetState {
        init(context)
        return _state.value
    }

    private fun grant(source: TreatSource) {
        val now = clock.nowMs()
        val day = clock.epochDay(now)
        _state.update { PetLogic.grantTreats(it, 1, source, now, day) }
        persist()
    }

    /**
     * Saves the latest whole state on the serial IO worker. Skipped when nothing but decay changed
     * since the last write (same [PersistKey]), so step-sensor ticks and cooldown pets don't hit disk.
     */
    private fun persist(force: Boolean = false) {
        val p = prefs ?: return
        scope.launch(writeDispatcher) {
            val snapshot = _state.value
            val key = persistKey(snapshot)
            if (!force && key == lastWrittenKey) return@launch
            PetPrefsStore.write(p, snapshot)
            lastWrittenKey = key
        }
    }

    /**
     * The fields that change only through a real event (feed, pet, grants, steps). Decay alone
     * changes `fullness` / `joy` / `lastUpdatedMs` and none of these.
     */
    internal data class PersistKey(
        val treats: Int,
        val lastPetMs: Long,
        val stepBaseline: Int,
        val lastBootEpochMs: Long,
        val dailyGrants: DailyGrants,
    )

    internal fun persistKey(s: PetState) =
        PersistKey(s.treats, s.lastPetMs, s.stepBaseline, s.lastBootEpochMs, s.dailyGrants)

    /** Test hook: forget the loaded prefs and in-memory state. */
    internal fun resetForTests(clock: PetClock, writeDispatcher: CoroutineDispatcher) {
        synchronized(this) {
            prefs = null
            lastWrittenKey = null
            _state.value = PetState()
            this.clock = clock
            this.writeDispatcher = writeDispatcher
        }
    }
}
