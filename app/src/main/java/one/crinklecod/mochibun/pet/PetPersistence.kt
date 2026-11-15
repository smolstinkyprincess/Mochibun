package one.crinklecod.mochibun.pet

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Reads and writes [PetState] in its own small SharedPreferences file. Every write stores the whole
 * state from one snapshot, so `fullness` / `joy` always match the `lastUpdatedMs` they were decayed to.
 */
internal object PetPrefsStore {
    const val PREFS_NAME = "mochibunny_pet"

    private const val KEY_FULLNESS = "fullness"
    private const val KEY_JOY = "joy"
    private const val KEY_TREATS = "treats"
    private const val KEY_LAST_UPDATED_MS = "last_updated_ms"
    private const val KEY_LAST_PET_MS = "last_pet_ms"
    private const val KEY_STEP_BASELINE = "step_baseline"
    private const val KEY_LAST_BOOT_EPOCH_MS = "last_boot_epoch_ms"
    private const val KEY_GRANTS_EPOCH_DAY = "grants_epoch_day"
    private const val KEY_GRANTS_STEPS = "grants_steps"
    private const val KEY_GRANTS_NOTIF_CLEAR = "grants_notif_clear"
    private const val KEY_GRANTS_CHARGER = "grants_charger"

    /** Missing keys (first run) fall back to [PetState] defaults. Callers should [PetLogic.sanitize]. */
    fun read(prefs: SharedPreferences): PetState {
        val d = PetState()
        return PetState(
            fullness = prefs.getFloat(KEY_FULLNESS, d.fullness),
            joy = prefs.getFloat(KEY_JOY, d.joy),
            treats = prefs.getInt(KEY_TREATS, d.treats),
            lastUpdatedMs = prefs.getLong(KEY_LAST_UPDATED_MS, d.lastUpdatedMs),
            lastPetMs = prefs.getLong(KEY_LAST_PET_MS, d.lastPetMs),
            stepBaseline = prefs.getInt(KEY_STEP_BASELINE, d.stepBaseline),
            lastBootEpochMs = prefs.getLong(KEY_LAST_BOOT_EPOCH_MS, d.lastBootEpochMs),
            dailyGrants = DailyGrants(
                epochDay = prefs.getLong(KEY_GRANTS_EPOCH_DAY, d.dailyGrants.epochDay),
                stepTreats = prefs.getInt(KEY_GRANTS_STEPS, d.dailyGrants.stepTreats),
                notifClearTreats = prefs.getInt(KEY_GRANTS_NOTIF_CLEAR, d.dailyGrants.notifClearTreats),
                chargeTreats = prefs.getInt(KEY_GRANTS_CHARGER, d.dailyGrants.chargeTreats),
            ),
        )
    }

    fun write(prefs: SharedPreferences, s: PetState) {
        prefs.edit()
            .putFloat(KEY_FULLNESS, s.fullness)
            .putFloat(KEY_JOY, s.joy)
            .putInt(KEY_TREATS, s.treats)
            .putLong(KEY_LAST_UPDATED_MS, s.lastUpdatedMs)
            .putLong(KEY_LAST_PET_MS, s.lastPetMs)
            .putInt(KEY_STEP_BASELINE, s.stepBaseline)
            .putLong(KEY_LAST_BOOT_EPOCH_MS, s.lastBootEpochMs)
            .putLong(KEY_GRANTS_EPOCH_DAY, s.dailyGrants.epochDay)
            .putInt(KEY_GRANTS_STEPS, s.dailyGrants.stepTreats)
            .putInt(KEY_GRANTS_NOTIF_CLEAR, s.dailyGrants.notifClearTreats)
            .putInt(KEY_GRANTS_CHARGER, s.dailyGrants.chargeTreats)
            .apply()
    }
}

/**
 * The `"pet"` object in the JSON backup. Backups are user-supplied, so [fromJson] never throws:
 * missing or non-numeric fields fall back to [PetState] defaults, and the caller sanitizes the result.
 */
internal object PetBackupCodec {
    const val KEY = "pet"

    fun toJson(s: PetState): JSONObject = JSONObject().apply {
        // Float -> String -> Double keeps "0.8" readable instead of 0.800000011920929
        put("fullness", s.fullness.toString().toDouble())
        put("joy", s.joy.toString().toDouble())
        put("treats", s.treats)
        put("last_updated_ms", s.lastUpdatedMs)
        put("last_pet_ms", s.lastPetMs)
        put("step_baseline", s.stepBaseline)
        put("last_boot_epoch_ms", s.lastBootEpochMs)
        put("daily_grants", JSONObject().apply {
            put("epoch_day", s.dailyGrants.epochDay)
            put("step_treats", s.dailyGrants.stepTreats)
            put("notif_clear_treats", s.dailyGrants.notifClearTreats)
            put("charge_treats", s.dailyGrants.chargeTreats)
        })
    }

    fun fromJson(o: JSONObject): PetState {
        val d = PetState()
        val g = o.optJSONObject("daily_grants")
        val dg = d.dailyGrants
        return PetState(
            fullness = o.optDouble("fullness", d.fullness.toDouble()).toFloat(),
            joy = o.optDouble("joy", d.joy.toDouble()).toFloat(),
            treats = o.optInt("treats", d.treats),
            lastUpdatedMs = o.optLong("last_updated_ms", d.lastUpdatedMs),
            lastPetMs = o.optLong("last_pet_ms", d.lastPetMs),
            stepBaseline = o.optInt("step_baseline", d.stepBaseline),
            lastBootEpochMs = o.optLong("last_boot_epoch_ms", d.lastBootEpochMs),
            dailyGrants = DailyGrants(
                epochDay = g?.optLong("epoch_day", dg.epochDay) ?: dg.epochDay,
                stepTreats = g?.optInt("step_treats", dg.stepTreats) ?: dg.stepTreats,
                notifClearTreats = g?.optInt("notif_clear_treats", dg.notifClearTreats) ?: dg.notifClearTreats,
                chargeTreats = g?.optInt("charge_treats", dg.chargeTreats) ?: dg.chargeTreats,
            ),
        )
    }
}
