package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_QUICK_SNIPPETS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.QUICK_SNIPPET_MAX_CHARS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.QUICK_SNIPPET_MAX_COUNT
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import org.json.JSONArray

/**
 * Pinned Quick Snippets (up to [QUICK_SNIPPET_MAX_COUNT]) shown first on the Clips & Snippets
 * wheel. Stored as one JSON array string so add / delete never leaves gaps in numbered keys.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 */
internal object QuickSnippetPrefs {
    private val _snippets = MutableStateFlow<List<String>>(emptyList())
    val snippets: StateFlow<List<String>> = _snippets.asStateFlow()

    fun clean(text: String): String = text.trim().take(QUICK_SNIPPET_MAX_CHARS).trimEnd()

    /** Drops blanks and duplicates, caps length and count. */
    fun sanitize(list: List<String>): List<String> =
        list.asSequence()
            .map(::clean)
            .filter { it.isNotEmpty() }
            .distinct()
            .take(QUICK_SNIPPET_MAX_COUNT)
            .toList()

    fun decode(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            sanitize(List(array.length()) { array.optString(it, "") })
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<String>): String = JSONArray(list).toString()

    fun reload(prefs: SharedPreferences) {
        _snippets.value = decode(prefs.getString(KEY_QUICK_SNIPPETS, null))
    }

    fun setSnippets(context: Context, list: List<String>) {
        val value = sanitize(list)
        getPrefs(context).edit().putString(KEY_QUICK_SNIPPETS, encode(value)).apply()
        _snippets.value = value
    }
}
