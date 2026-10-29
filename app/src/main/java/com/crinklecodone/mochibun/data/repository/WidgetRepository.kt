package com.crinklecodone.mochibun.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One hosted Android app widget on the Widgets page, in display order. */
data class WidgetSlot(
    val appWidgetId: Int,
    /** Flattened provider ComponentName, kept so an unavailable widget can still be named. */
    val provider: String? = null
)

/**
 * Persists the Widgets page's hosted app widgets in `launcher_preferences`.
 *
 * Widget IDs belong to this device's AppWidgetService, so they're deliberately left out of
 * JSON backups ([BackupRestoreManager] never reads these keys).
 */
object WidgetRepository {
    private const val TAG = "WidgetRepository"

    /** Ordered, comma-separated widget IDs. Same key and format as older Mochibun builds. */
    const val KEY_WIDGET_IDS = "widget_ids"

    /** Newline-separated `id|provider` pairs. */
    const val KEY_WIDGET_PROVIDERS = "widget_providers"

    /** A widget ID that's mid-way through bind / configure, so cleanup doesn't delete it. */
    const val KEY_PENDING_WIDGET_ID = "widget_pending_id"

    private val _slots = MutableStateFlow<List<WidgetSlot>>(emptyList())
    val slots: StateFlow<List<WidgetSlot>> = _slots.asStateFlow()

    @Volatile
    private var loaded = false

    fun init(context: Context) {
        val prefs = prefs(context)
        _slots.value = decodeSlots(
            prefs.getString(KEY_WIDGET_IDS, "").orEmpty(),
            prefs.getString(KEY_WIDGET_PROVIDERS, "").orEmpty()
        )
        loaded = true
    }

    private fun ensureLoaded(context: Context) {
        if (!loaded) init(context)
    }

    fun ids(): List<Int> = _slots.value.map { it.appWidgetId }

    fun add(context: Context, appWidgetId: Int, provider: String?) {
        ensureLoaded(context)
        val current = _slots.value
        if (current.any { it.appWidgetId == appWidgetId }) return
        save(context, current + WidgetSlot(appWidgetId, provider))
    }

    fun remove(context: Context, appWidgetId: Int) {
        ensureLoaded(context)
        val current = _slots.value
        val updated = current.filter { it.appWidgetId != appWidgetId }
        if (updated.size != current.size) save(context, updated)
    }

    /** Moves a widget [delta] slots (-1 = up, +1 = down). Returns false at either end. */
    fun move(context: Context, appWidgetId: Int, delta: Int): Boolean {
        ensureLoaded(context)
        val current = _slots.value
        val from = current.indexOfFirst { it.appWidgetId == appWidgetId }
        val updated = moveItem(current, from, from + delta) ?: return false
        save(context, updated)
        return true
    }

    fun pendingWidgetId(context: Context): Int? {
        val id = prefs(context).getInt(KEY_PENDING_WIDGET_ID, -1)
        return id.takeIf { it > 0 }
    }

    fun setPendingWidgetId(context: Context, appWidgetId: Int?) {
        prefs(context).edit().apply {
            if (appWidgetId == null) remove(KEY_PENDING_WIDGET_ID) else putInt(KEY_PENDING_WIDGET_ID, appWidgetId)
        }.apply()
    }

    /**
     * Brings saved slots and the IDs the system holds for our host back in sync:
     * - IDs the host owns but Mochibun never saved (left over from a crash or a cancelled add)
     *   are deleted so they don't pile up in AppWidgetService.
     * - Saved IDs the system no longer knows (the provider app was uninstalled) are dropped.
     *
     * [keepPending] keeps the in-flight ID while an add flow may still be running.
     * Returns the IDs that should be deleted from the host.
     */
    fun reconcile(context: Context, hostIds: IntArray, keepPending: Boolean): List<Int> {
        ensureLoaded(context)
        val pending = pendingWidgetId(context)
        if (!keepPending && pending != null) setPendingWidgetId(context, null)
        val plan = planReconcile(
            saved = _slots.value,
            hostIds = hostIds.toSet(),
            pendingId = if (keepPending) pending else null
        )
        if (plan.kept.size != _slots.value.size) {
            Log.i(TAG, "Dropping ${_slots.value.size - plan.kept.size} widget(s) whose provider is gone")
            save(context, plan.kept)
        }
        return plan.orphans
    }

    private fun save(context: Context, slots: List<WidgetSlot>) {
        _slots.value = slots
        prefs(context).edit()
            .putString(KEY_WIDGET_IDS, encodeIds(slots))
            .putString(KEY_WIDGET_PROVIDERS, encodeProviders(slots))
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PreferencesManager.PREFS_NAME, Context.MODE_PRIVATE)

    // ── Pure helpers (unit tested) ────────────────────────────────────

    data class ReconcilePlan(val kept: List<WidgetSlot>, val orphans: List<Int>)

    fun planReconcile(saved: List<WidgetSlot>, hostIds: Set<Int>, pendingId: Int?): ReconcilePlan {
        val savedIds = saved.mapTo(HashSet()) { it.appWidgetId }
        val kept = saved.filter { it.appWidgetId in hostIds }
        val orphans = hostIds.filter { it !in savedIds && it != pendingId }.sorted()
        return ReconcilePlan(kept, orphans)
    }

    fun encodeIds(slots: List<WidgetSlot>): String = slots.joinToString(",") { it.appWidgetId.toString() }

    fun encodeProviders(slots: List<WidgetSlot>): String = slots
        .filter { !it.provider.isNullOrBlank() }
        .joinToString("\n") { "${it.appWidgetId}|${it.provider}" }

    fun decodeSlots(idsCsv: String, providersText: String): List<WidgetSlot> {
        val providers = HashMap<Int, String>()
        providersText.lineSequence().forEach { line ->
            val sep = line.indexOf('|')
            if (sep > 0) {
                val id = line.substring(0, sep).trim().toIntOrNull()
                val provider = line.substring(sep + 1).trim()
                if (id != null && provider.isNotEmpty()) providers[id] = provider
            }
        }
        val seen = HashSet<Int>()
        return idsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 && seen.add(it) }
            .map { WidgetSlot(it, providers[it]) }
    }

    /** Returns a copy of [list] with the item at [from] moved to [to], or null if either is out of range. */
    fun <T> moveItem(list: List<T>, from: Int, to: Int): List<T>? {
        if (from !in list.indices || to !in list.indices || from == to) return null
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}
