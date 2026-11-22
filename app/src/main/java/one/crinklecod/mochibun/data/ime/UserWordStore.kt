package one.crinklecod.mochibun.data.ime

import org.json.JSONObject
import java.util.Locale

internal enum class LearnedEpochAction {
    KEEP_IN_MEMORY,
    RELOAD_FROM_PREFS
}

/**
 * Compares the epoch loaded into memory against the current epoch stored in preferences. Any
 * mismatch means another component (e.g. Clear Learned Words or Backup Restore) mutated prefs, so
 * the in-memory state (including unsaved edits) must be discarded and reloaded.
 */
internal fun decideLearnedEpochAction(loadedEpoch: Int, prefEpoch: Int): LearnedEpochAction =
    if (loadedEpoch == prefEpoch) LearnedEpochAction.KEEP_IN_MEMORY else LearnedEpochAction.RELOAD_FROM_PREFS

/**
 * Stores per-dictionary learned new words (`word -> sighting count`).
 *
 * Keeps insertion order per dictionary so that when [MAX_WORDS_PER_DICT] is reached, eviction
 * removes the lowest-count entry and breaks ties by removing the oldest entry first. Words become
 * eligible for T9 suggestions once their count reaches [SUGGESTION_THRESHOLD] (`>= 2`).
 */
class UserWordStore {
    private val lock = Any()
    private val byDict = LinkedHashMap<String, LinkedHashMap<String, Int>>()

    @Volatile
    var dirty: Boolean = false
        private set

    @Volatile
    internal var version: Int = 0
        private set

    fun recordSighting(dictId: String, word: String): Int {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (!LearnedWordExtractor.isValidLearnedWord(clean)) return 0
        synchronized(lock) {
            val map = byDict.getOrPut(dictId) { LinkedHashMap() }
            val existing = map[clean]
            val nextCount = if (existing != null) {
                (existing + 1).coerceAtMost(MAX_COUNT)
            } else {
                if (map.size >= MAX_WORDS_PER_DICT) {
                    evictLowestOldest(map)
                }
                1
            }
            map[clean] = nextCount
            dirty = true
            version++
            return nextCount
        }
    }

    fun countFor(dictId: String, word: String): Int {
        val clean = word.lowercase(Locale.ROOT)
        synchronized(lock) {
            return byDict[dictId]?.get(clean) ?: 0
        }
    }

    fun snapshotFor(dictId: String): Map<String, Int> = synchronized(lock) {
        byDict[dictId]?.toMap() ?: emptyMap()
    }

    fun visibleWordsFor(dictId: String): List<Pair<String, Int>> = synchronized(lock) {
        val map = byDict[dictId] ?: return emptyList()
        val out = ArrayList<Pair<String, Int>>()
        for ((word, count) in map) {
            if (count >= SUGGESTION_THRESHOLD) {
                out.add(word to count)
            }
        }
        out
    }

    fun visibleWordCount(): Int = synchronized(lock) {
        var total = 0
        for (map in byDict.values) {
            for (count in map.values) {
                if (count >= SUGGESTION_THRESHOLD) total++
            }
        }
        total
    }

    fun clearAll() {
        synchronized(lock) {
            byDict.clear()
            dirty = false
            version++
        }
    }

    fun loadFromJsonString(json: String?) {
        synchronized(lock) {
            byDict.clear()
            dirty = false
            version++
            if (json.isNullOrBlank()) return
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return
            val dictKeys = root.keys()
            while (dictKeys.hasNext()) {
                val dictId = dictKeys.next()
                if (dictId.isBlank() || dictId.length > 128) continue
                val obj = root.optJSONObject(dictId) ?: continue
                val map = LinkedHashMap<String, Int>()
                val words = obj.keys()
                while (words.hasNext()) {
                    val rawWord = words.next()
                    val clean = rawWord.trim().lowercase(Locale.ROOT)
                    if (!LearnedWordExtractor.isValidLearnedWord(clean)) continue
                    val count = obj.optInt(rawWord, 0)
                    if (count !in 1..MAX_COUNT) continue
                    val merged = ((map[clean] ?: 0) + count).coerceAtMost(MAX_COUNT)
                    map[clean] = merged
                }
                while (map.size > MAX_WORDS_PER_DICT) {
                    evictLowestOldest(map)
                }
                if (map.isNotEmpty()) {
                    byDict[dictId] = map
                }
            }
        }
    }

    fun toJsonString(): String = synchronized(lock) {
        val root = JSONObject()
        for ((dictId, words) in byDict) {
            if (words.isEmpty()) continue
            val obj = JSONObject()
            for ((w, count) in words) {
                obj.put(w, count)
            }
            root.put(dictId, obj)
        }
        root.toString()
    }

    fun markClean() {
        synchronized(lock) {
            dirty = false
        }
    }

    private fun evictLowestOldest(map: LinkedHashMap<String, Int>) {
        var victimKey: String? = null
        var victimCount = Int.MAX_VALUE
        // Strict `<` over LinkedHashMap iteration order keeps the oldest entry on count ties.
        for ((k, c) in map) {
            if (c < victimCount) {
                victimKey = k
                victimCount = c
            }
        }
        if (victimKey != null) {
            map.remove(victimKey)
        }
    }

    companion object {
        const val MAX_WORDS_PER_DICT = 500
        const val SUGGESTION_THRESHOLD = 2
        const val PROMOTED_THRESHOLD = 3
        const val MAX_COUNT = 100_000
    }
}
