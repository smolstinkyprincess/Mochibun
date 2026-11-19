package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_QUICK_MEMO_LAST_OPENED_SLOT
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_QUICK_MEMO_PINNED_SLOT
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_QUICK_MEMO_TEXT
import one.crinklecod.mochibun.data.prefs.PreferencesManager.QUICK_MEMO_MAX_CHARS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.QUICK_MEMO_SLOT_COUNT
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quick Memo slots (1..[QUICK_MEMO_SLOT_COUNT]) shown on Home and in the sub-display ticker.
 *
 * Slot 1 is stored under the original single-memo key ([KEY_QUICK_MEMO_TEXT]), so a memo written
 * before slots existed is read back as slot 1 with no copy step, and a downgrade still finds it.
 * Slots 2..5 use `pref_quick_memo_text_<n>`.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object QuickMemoPrefs {
    private val _slots = MutableStateFlow(List(QUICK_MEMO_SLOT_COUNT) { "" })
    /** All memo slots, index 0 = slot 1. */
    val slots: StateFlow<List<String>> = _slots.asStateFlow()

    private val _quickMemoText = MutableStateFlow("")
    /** Slot 1 text: the pre-slot single-memo API, kept for existing callers. */
    val quickMemoText: StateFlow<String> = _quickMemoText.asStateFlow()

    private val _pinnedSlot = MutableStateFlow(1)
    /** Slot (1-based) shown in the Home sub-display ticker. */
    val pinnedSlot: StateFlow<Int> = _pinnedSlot.asStateFlow()

    private val _lastOpenedSlot = MutableStateFlow(1)
    /** Slot (1-based) the memo dialog reopens on. */
    val lastOpenedSlot: StateFlow<Int> = _lastOpenedSlot.asStateFlow()

    fun slotKey(slot: Int): String =
        if (slot == 1) KEY_QUICK_MEMO_TEXT else "${KEY_QUICK_MEMO_TEXT}_$slot"

    fun clampSlot(slot: Int): Int = slot.coerceIn(1, QUICK_MEMO_SLOT_COUNT)

    fun clean(text: String): String = text.take(QUICK_MEMO_MAX_CHARS).trimEnd()

    fun reload(prefs: SharedPreferences) {
        val loaded = List(QUICK_MEMO_SLOT_COUNT) { i -> prefs.getString(slotKey(i + 1), "") ?: "" }
        _slots.value = loaded
        _quickMemoText.value = loaded[0]
        _pinnedSlot.value = clampSlot(prefs.getInt(KEY_QUICK_MEMO_PINNED_SLOT, 1))
        _lastOpenedSlot.value = clampSlot(prefs.getInt(KEY_QUICK_MEMO_LAST_OPENED_SLOT, 1))
    }

    /** Saves one slot (trimmed of trailing blank lines, capped at [QUICK_MEMO_MAX_CHARS]). */
    fun setSlotText(context: Context, slot: Int, text: String) {
        val index = clampSlot(slot) - 1
        val value = clean(text)
        getPrefs(context).edit().putString(slotKey(index + 1), value).apply()
        _slots.value = _slots.value.toMutableList().also { it[index] = value }
        if (index == 0) _quickMemoText.value = value
    }

    fun setPinnedSlot(context: Context, slot: Int) {
        val value = clampSlot(slot)
        getPrefs(context).edit().putInt(KEY_QUICK_MEMO_PINNED_SLOT, value).apply()
        _pinnedSlot.value = value
    }

    fun setLastOpenedSlot(context: Context, slot: Int) {
        val value = clampSlot(slot)
        getPrefs(context).edit().putInt(KEY_QUICK_MEMO_LAST_OPENED_SLOT, value).apply()
        _lastOpenedSlot.value = value
    }

    /** Pre-slot API: writes slot 1. */
    fun setQuickMemoText(context: Context, text: String) = setSlotText(context, 1, text)

    /** Pre-slot Clear: empties slot 1 (which also removes it from the ticker). */
    fun clearQuickMemo(context: Context) {
        setQuickMemoText(context, "")
    }

    /**
     * Ticker text: the pinned slot, else the first non-blank slot, else null (no memo segment).
     */
    fun tickerMemo(slots: List<String>, pinnedSlot: Int): String? {
        slots.getOrNull(pinnedSlot - 1)?.takeIf { it.isNotBlank() }?.let { return it }
        return slots.firstOrNull { it.isNotBlank() }
    }
}
