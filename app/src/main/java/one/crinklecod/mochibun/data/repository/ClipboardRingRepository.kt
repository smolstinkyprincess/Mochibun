package one.crinklecod.mochibun.data.repository

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import one.crinklecod.mochibun.data.prefs.PreferencesManager

/** One petal of the Clips & Snippets wheel. [pinned] = a saved Quick Snippet (shown with ★). */
data class ClipRingEntry(val text: String, val pinned: Boolean)

/**
 * Bounded most-recent-first list of copied text. Pure Kotlin so the rules are unit-testable:
 * entries are trimmed and capped at [maxChars], blank or sensitive clips are skipped, and
 * re-copying a text moves it to the front instead of adding a duplicate.
 */
class ClipboardRing(
    private val maxItems: Int = ClipboardRingRepository.MAX_RECENT_CLIPS,
    private val maxChars: Int = ClipboardRingRepository.MAX_CLIP_CHARS
) {
    private val entries = ArrayList<String>(maxItems)

    val items: List<String>
        @Synchronized get() = entries.toList()

    /** Adds [raw] to the front. Returns true if the ring changed. */
    @Synchronized
    fun offer(raw: CharSequence?, sensitive: Boolean = false): Boolean {
        if (sensitive || raw == null) return false
        val text = raw.toString().trim().take(maxChars).trimEnd()
        if (text.isEmpty()) return false
        if (entries.firstOrNull() == text) return false
        entries.remove(text)
        entries.add(0, text)
        while (entries.size > maxItems) entries.removeAt(entries.lastIndex)
        return true
    }

    @Synchronized
    fun clear() = entries.clear()
}

/**
 * Recent clipboard text (in memory only, never persisted or backed up) plus the pinned Quick
 * Snippets from [PreferencesManager], merged into the petals of the `#`-hold wheel.
 *
 * The listener lives in [one.crinklecod.mochibun.service.MochibunImeService]: Android 10+ only
 * lets the focused app or the default keyboard read the clipboard, so the IME is the one place
 * that sees copies made in other apps. MainActivity also syncs on resume for when another
 * keyboard is active.
 */
object ClipboardRingRepository {
    const val MAX_RECENT_CLIPS = 8
    const val MAX_CLIP_CHARS = 240
    /** Petals on the wheel (pinned snippets first, then recent clips). */
    const val MAX_RING_PETALS = 8

    /** Key behind [ClipDescription.EXTRA_IS_SENSITIVE] (API 33). Password managers set it on older releases too. */
    private const val EXTRA_IS_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

    private val ring = ClipboardRing()
    private val _recentClips = MutableStateFlow<List<String>>(emptyList())
    val recentClips: StateFlow<List<String>> = _recentClips.asStateFlow()

    // ClipDescription timestamp of the last clip we looked at, so a resume with an unchanged
    // clipboard never reads clip content again (each read can show Android 12's paste toast).
    @Volatile
    private var lastSeenTimestamp = -1L

    private var registeredManager: ClipboardManager? = null
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        registeredManager?.let { capture(it) }
    }

    /** Starts watching the clipboard (call from the IME's onCreate). */
    fun register(context: Context) {
        if (registeredManager != null) return
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        registeredManager = manager
        runCatching { manager.addPrimaryClipChangedListener(listener) }
        capture(manager)
    }

    fun unregister() {
        val manager = registeredManager ?: return
        runCatching { manager.removePrimaryClipChangedListener(listener) }
        registeredManager = null
    }

    /** Picks up the current primary clip if it changed since we last looked. */
    fun syncFromClipboard(context: Context) {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        capture(manager)
    }

    private fun capture(manager: ClipboardManager) {
        runCatching {
            val description = manager.primaryClipDescription ?: return
            val stamp = description.timestamp
            if (stamp != 0L && stamp == lastSeenTimestamp) return
            lastSeenTimestamp = stamp
            // Checked before reading the content, so a password is never even fetched
            if (isSensitive(description)) return
            if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
                !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
            ) return
            val clip = manager.primaryClip ?: return
            if (clip.itemCount == 0) return
            // getText only: coerceToText could open content URIs (disk / provider I/O)
            offer(clip.getItemAt(0).text)
        }
    }

    /** Adds text to the ring (also the path for tests and future in-app copies). */
    fun offer(text: CharSequence?, sensitive: Boolean = false) {
        if (ring.offer(text, sensitive)) _recentClips.value = ring.items
    }

    fun isSensitive(description: ClipDescription?): Boolean {
        val extras = description?.extras ?: return false
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            EXTRA_IS_SENSITIVE_KEY
        }
        return extras.getBoolean(key, false)
    }

    /** Pinned snippets first, then recent clips not already pinned, up to [MAX_RING_PETALS]. */
    fun mergeRing(pinned: List<String>, recent: List<String>): List<ClipRingEntry> {
        val out = ArrayList<ClipRingEntry>(MAX_RING_PETALS)
        val seen = HashSet<String>()
        for (text in pinned) {
            if (out.size >= MAX_RING_PETALS) break
            if (text.isNotBlank() && seen.add(text)) out.add(ClipRingEntry(text, pinned = true))
        }
        for (text in recent) {
            if (out.size >= MAX_RING_PETALS) break
            if (text.isNotBlank() && seen.add(text)) out.add(ClipRingEntry(text, pinned = false))
        }
        return out
    }

    fun ringEntries(): List<ClipRingEntry> =
        mergeRing(PreferencesManager.quickSnippets.value, _recentClips.value)

    /** Test hook: forgets recent clips. */
    internal fun clearForTest() {
        clearForTestKeepingStamp()
        lastSeenTimestamp = -1L
    }

    /** Test hook: forgets recent clips but remembers which clip was last read. */
    internal fun clearForTestKeepingStamp() {
        ring.clear()
        _recentClips.value = emptyList()
    }
}
