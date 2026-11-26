package one.crinklecod.mochibun.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import one.crinklecod.mochibun.core.widget.WidgetCanvasLayout
import one.crinklecod.mochibun.core.widget.WidgetPlacement
import one.crinklecod.mochibun.data.prefs.PreferencesManager
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

    /**
     * Freeform canvas placements for every Widgets-page card (native cards and hosted widgets),
     * in [WidgetCanvasLayout.encode] format. Kept out of JSON backups like the widget IDs above:
     * hosted-widget entries are keyed by device-local IDs.
     */
    const val KEY_WIDGET_CANVAS = "widget_canvas_layout"

    /** Now Playing's compact MiniDisc / pocket-deck skin instead of the full two-band card. */
    const val KEY_NOW_PLAYING_MINI = "widget_now_playing_mini"

    private val _nowPlayingMini = MutableStateFlow(false)
    val nowPlayingMini: StateFlow<Boolean> = _nowPlayingMini.asStateFlow()

    private val _slots = MutableStateFlow<List<WidgetSlot>>(emptyList())
    val slots: StateFlow<List<WidgetSlot>> = _slots.asStateFlow()

    private val _placements = MutableStateFlow<Map<String, WidgetPlacement>>(emptyMap())
    val placements: StateFlow<Map<String, WidgetPlacement>> = _placements.asStateFlow()

    @Volatile
    private var loaded = false

    fun init(context: Context) {
        val prefs = prefs(context)
        _slots.value = decodeSlots(
            prefs.getString(KEY_WIDGET_IDS, "").orEmpty(),
            prefs.getString(KEY_WIDGET_PROVIDERS, "").orEmpty()
        )
        _placements.value = WidgetCanvasLayout.decode(prefs.getString(KEY_WIDGET_CANVAS, "").orEmpty())
        _nowPlayingMini.value = prefs.getBoolean(KEY_NOW_PLAYING_MINI, false)
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
        dropPlacements(context, listOf(appWidgetId))
    }

    /** Saves the canvas layout (no-op when unchanged, so callers can commit freely). */
    fun setPlacements(context: Context, layout: Map<String, WidgetPlacement>) {
        ensureLoaded(context)
        if (layout == _placements.value) return
        _placements.value = layout
        prefs(context).edit().putString(KEY_WIDGET_CANVAS, WidgetCanvasLayout.encode(layout)).apply()
    }

    fun setNowPlayingMini(context: Context, mini: Boolean) {
        ensureLoaded(context)
        if (_nowPlayingMini.value == mini) return
        _nowPlayingMini.value = mini
        prefs(context).edit().putBoolean(KEY_NOW_PLAYING_MINI, mini).apply()
    }

    private fun dropPlacements(context: Context, appWidgetIds: Collection<Int>) {
        val keys = appWidgetIds.mapTo(HashSet()) { WidgetCanvasLayout.appWidgetKey(it) }
        val current = _placements.value
        if (current.keys.none { it in keys }) return
        setPlacements(context, current.filterKeys { it !in keys })
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
            val keptIds = plan.kept.mapTo(HashSet()) { it.appWidgetId }
            val dropped = _slots.value.map { it.appWidgetId }.filter { it !in keptIds }
            save(context, plan.kept)
            dropPlacements(context, dropped)
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
