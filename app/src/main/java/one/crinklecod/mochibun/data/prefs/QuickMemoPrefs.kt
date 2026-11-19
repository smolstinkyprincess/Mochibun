package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_QUICK_MEMO_TEXT
import one.crinklecod.mochibun.data.prefs.PreferencesManager.QUICK_MEMO_MAX_CHARS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quick Memo text shown on Home and in the sub-display ticker.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object QuickMemoPrefs {
    private val _quickMemoText = MutableStateFlow("")
    /** Quick Memo text (`#` hold on Home); shown in the sub-display ticker when non-blank. */
    val quickMemoText: StateFlow<String> = _quickMemoText.asStateFlow()

    fun reload(prefs: SharedPreferences) {
        _quickMemoText.value = prefs.getString(KEY_QUICK_MEMO_TEXT, "") ?: ""
    }

    /** Saves the memo text (trimmed of trailing blank lines, capped at [QUICK_MEMO_MAX_CHARS]). */
    fun setQuickMemoText(context: Context, text: String) {
        val clean = text.take(QUICK_MEMO_MAX_CHARS).trimEnd()
        getPrefs(context).edit().putString(KEY_QUICK_MEMO_TEXT, clean).apply()
        _quickMemoText.value = clean
    }

    /** Clear button: empties the memo (which also removes it from the ticker). */
    fun clearQuickMemo(context: Context) {
        setQuickMemoText(context, "")
    }
}
