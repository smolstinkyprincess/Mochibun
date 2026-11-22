package one.crinklecod.mochibun.data.ime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory offline T9 predictive dictionary engine using parallel arrays and binary search.
 *
 * Loading runs off the main thread; once [ready] is true, [exact], [completions], and
 * [candidatesFor] execute synchronously with zero file I/O.
 */
class T9DictionaryEngine(
    val userStats: UserWordStats = UserWordStats(),
    val userWords: UserWordStore = UserWordStore()
) {
    data class Match(
        val word: String,
        val freq: Int,
        val learnedCount: Int = 0
    )

    data class LookupResult(
        val words: List<String>,
        val hasDictionaryMatch: Boolean
    )

    data class DictionaryDescriptor(
        val id: String,
        val displayName: String
    )

    private class DictionaryData(
        val id: String,
        val digits: Array<String>,
        val words: Array<String>,
        val freqs: IntArray
    )

    private class ParsedEntry(
        val digits: String,
        val word: String,
        var freq: Int
    )

    private class UserWordEntry(
        val digits: String,
        val word: String,
        val count: Int
    )

    private class UserWordIndex(
        val dictId: String,
        val version: Int,
        val byDigits: Map<String, List<UserWordEntry>>,
        val allEntries: List<UserWordEntry>
    ) {
        companion object {
            val EMPTY = UserWordIndex("", -1, emptyMap(), emptyList())
        }
    }

    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    private var snapshot: DictionaryData? = null

    @Volatile
    private var userWordIndex: UserWordIndex = UserWordIndex.EMPTY

    private val loadGeneration = AtomicInteger(0)

    val activeDictionaryId: String
        get() = snapshot?.id ?: BUILT_IN_EN_ID

    /**
     * Loads [dictId] asynchronously on [Dispatchers.IO]. While loading (or if switching to a new
     * dictionary), [ready] becomes `false` until the new snapshot is published. Unknown or broken
     * `"file:<name>"` dictionaries fall back to built-in `"en"`. Calls [onLoaded] with the
     * resolved dictionary id once published.
     */
    fun loadAsync(
        context: Context,
        dictId: String,
        onLoaded: ((loadedId: String) -> Unit)? = null
    ) {
        val current = snapshot
        if (ready && current != null && current.id == dictId) {
            ensureUserWordIndex(current.id)
            onLoaded?.invoke(current.id)
            return
        }

        val appContext = context.applicationContext
        val generation = loadGeneration.incrementAndGet()
        ready = false

        ioScope.launch {
            val loaded = runCatching { loadDictionaryData(appContext, dictId) }.getOrNull()
                ?: runCatching { loadBuiltInEnglish(appContext) }.getOrNull()
            if (loadGeneration.get() != generation) return@launch
            if (loaded != null) {
                snapshot = loaded
                rebuildUserWordIndex(loaded.id)
                ready = true
                onLoaded?.invoke(loaded.id)
            }
        }
    }

    /** Synchronous loader for unit tests and in-memory streams. */
    fun loadFromReader(dictId: String, reader: Reader) {
        val parsed = parseReader(dictId, reader)
        loadGeneration.incrementAndGet()
        snapshot = parsed
        rebuildUserWordIndex(parsed.id)
        ready = true
    }

    /** Replaces both learned stores from preferences and rebuilds the active user-word index. */
    fun reloadLearnedData(statsJson: String?, userWordsJson: String?) {
        userStats.loadFromJsonString(statsJson)
        userWords.loadFromJsonString(userWordsJson)
        snapshot?.id?.let(::rebuildUserWordIndex) ?: run { userWordIndex = UserWordIndex.EMPTY }
    }

    /** Records a sighting of a non-dictionary word for the active dictionary and updates the index. */
    fun recordUserWordSighting(word: String): Int {
        val dictId = activeDictionaryId
        val nextCount = userWords.recordSighting(dictId, word)
        if (nextCount >= UserWordStore.SUGGESTION_THRESHOLD) {
            rebuildUserWordIndex(dictId)
        }
        return nextCount
    }

    /** True if [word] exists verbatim (lowercase) in the active built-in or custom dictionary. */
    fun hasDictionaryWord(word: String): Boolean {
        val data = snapshot
        if (!ready || data == null) return false
        val clean = word.lowercase(Locale.ROOT)
        val sig = T9Keymap.signature(clean) ?: return false
        val start = lowerBound(data.digits, sig)
        var i = start
        while (i < data.digits.size && data.digits[i] == sig) {
            if (data.words[i] == clean) return true
            i++
        }
        return false
    }

    /** True if [word] is tracked in [userWords] for the active dictionary. */
    fun isUserWord(word: String): Boolean =
        userWords.countFor(activeDictionaryId, word) > 0

    /** Exact matches: user words >= 3 (by count), dictionary order, then user words == 2 (by count). */
    fun exact(seq: String): List<Match> {
        val data = snapshot
        if (!ready || data == null || seq.isEmpty()) return emptyList()

        val index = ensureUserWordIndex(data.id)
        val userMatches = index.byDigits[seq].orEmpty()

        val start = lowerBound(data.digits, seq)
        val hasDict = start < data.digits.size && data.digits[start] == seq
        if (!hasDict && userMatches.isEmpty()) return emptyList()

        val dictMatches = if (hasDict) {
            val hasLearned = userStats.hasAnyFor(data.id)
            val list = ArrayList<Match>(8)
            var i = start
            while (i < data.digits.size && data.digits[i] == seq) {
                val w = data.words[i]
                val learned = if (hasLearned) userStats.countFor(data.id, w) else 0
                list.add(Match(word = w, freq = data.freqs[i], learnedCount = learned))
                i++
            }
            if (hasLearned && list.size > 1) {
                list.sortWith(MATCH_COMPARATOR)
            }
            list
        } else {
            emptyList()
        }

        if (userMatches.isEmpty()) return dictMatches

        val dictWords = if (dictMatches.isNotEmpty()) {
            HashSet<String>(dictMatches.size).apply {
                for (i in 0 until dictMatches.size) add(dictMatches[i].word)
            }
        } else {
            emptySet()
        }
        val topUser = ArrayList<Match>()
        val midUser = ArrayList<Match>()
        for (i in 0 until userMatches.size) {
            val entry = userMatches[i]
            if (entry.word in dictWords) continue
            val match = Match(word = entry.word, freq = 0, learnedCount = entry.count)
            if (entry.count >= UserWordStore.PROMOTED_THRESHOLD) {
                topUser.add(match)
            } else {
                midUser.add(match)
            }
        }
        if (topUser.isEmpty() && midUser.isEmpty()) return dictMatches

        val merged = ArrayList<Match>(topUser.size + dictMatches.size + midUser.size)
        merged.addAll(topUser)
        merged.addAll(dictMatches)
        merged.addAll(midUser)
        return merged
    }

    /**
     * Prefix completions for [prefix] (strictly longer words only): promoted user words first
     * (by count), then dictionary completions up to [limit].
     */
    fun completions(prefix: String, limit: Int): List<Match> {
        val data = snapshot
        if (!ready || data == null || prefix.isEmpty() || limit <= 0) return emptyList()

        val index = ensureUserWordIndex(data.id)
        val userComps = collectUserCompletions(index, prefix, limit)
        val out = ArrayList<Match>(limit)
        out.addAll(userComps)

        val remaining = limit - out.size
        if (remaining <= 0) return out

        val start = lowerBound(data.digits, prefix)
        if (start >= data.digits.size || !data.digits[start].startsWith(prefix)) return out

        val seenUserWords = if (userComps.isNotEmpty()) {
            HashSet<String>(userComps.size).apply {
                for (i in 0 until userComps.size) add(userComps[i].word)
            }
        } else {
            emptySet()
        }
        val hasLearned = userStats.hasAnyFor(data.id)
        val dictBest = ArrayList<Match>(remaining + 1)
        var i = start
        var scanned = 0
        while (i < data.digits.size && scanned < SCAN_CAP) {
            val d = data.digits[i]
            if (!d.startsWith(prefix)) break
            scanned++
            if (d.length > prefix.length) {
                val w = data.words[i]
                if (w !in seenUserWords) {
                    val freq = data.freqs[i]
                    val learned = if (hasLearned) userStats.countFor(data.id, w) else 0
                    if (dictBest.size < remaining || isBetterThan(w, freq, learned, dictBest[dictBest.size - 1])) {
                        insertSortedBounded(dictBest, Match(w, freq, learned), remaining)
                    }
                }
            }
            i++
        }
        out.addAll(dictBest)
        return out
    }

    private fun ensureUserWordIndex(dictId: String): UserWordIndex {
        val current = userWordIndex
        if (current.dictId == dictId && current.version == userWords.version) {
            return current
        }
        return rebuildUserWordIndex(dictId)
    }

    private fun rebuildUserWordIndex(dictId: String): UserWordIndex {
        val ver = userWords.version
        val visible = userWords.visibleWordsFor(dictId)
        if (visible.isEmpty()) {
            val empty = UserWordIndex(dictId, ver, emptyMap(), emptyList())
            userWordIndex = empty
            return empty
        }
        val byDigits = HashMap<String, MutableList<UserWordEntry>>(visible.size)
        val all = ArrayList<UserWordEntry>(visible.size)
        for ((word, count) in visible) {
            val sig = T9Keymap.signature(word) ?: continue
            val entry = UserWordEntry(digits = sig, word = word, count = count)
            byDigits.getOrPut(sig) { ArrayList(2) }.add(entry)
            all.add(entry)
        }
        val order = compareByDescending<UserWordEntry> { it.count }.thenBy { it.word }
        for (bucket in byDigits.values) {
            if (bucket.size > 1) bucket.sortWith(order)
        }
        all.sortWith(order)
        val built = UserWordIndex(dictId, ver, byDigits, all)
        userWordIndex = built
        return built
    }

    private fun collectUserCompletions(
        index: UserWordIndex,
        prefix: String,
        limit: Int
    ): List<Match> {
        if (index.allEntries.isEmpty() || limit <= 0) return emptyList()
        val matches = ArrayList<Match>(minOf(limit, 8))
        for (i in 0 until index.allEntries.size) {
            val entry = index.allEntries[i]
            if (entry.digits.length > prefix.length && entry.digits.startsWith(prefix)) {
                matches.add(Match(word = entry.word, freq = 0, learnedCount = entry.count))
                if (matches.size == limit) break
            }
        }
        return matches
    }

    /**
     * Builds the ordered candidate list for [seq]: exact matches first, then up to [maxCompletions]
     * longer completions, capped at [maxTotal]. When nothing matches, returns [seq] itself with
     * `hasDictionaryMatch = false`.
     */
    fun candidatesFor(
        seq: String,
        maxCompletions: Int = DEFAULT_MAX_COMPLETIONS,
        maxTotal: Int = DEFAULT_MAX_CANDIDATES
    ): LookupResult {
        if (!ready || seq.isEmpty()) return LookupResult(emptyList(), hasDictionaryMatch = false)

        val exactMatches = exact(seq)
        val wordsOut = ArrayList<String>(maxTotal)
        val exactTake = minOf(exactMatches.size, maxTotal)
        for (i in 0 until exactTake) {
            wordsOut.add(exactMatches[i].word)
        }
        val remaining = minOf(maxCompletions, maxTotal - wordsOut.size)
        if (remaining > 0) {
            val comps = completions(seq, remaining)
            for (i in 0 until comps.size) {
                wordsOut.add(comps[i].word)
            }
        }
        return if (wordsOut.isEmpty()) {
            LookupResult(listOf(seq), hasDictionaryMatch = false)
        } else {
            LookupResult(wordsOut, hasDictionaryMatch = true)
        }
    }

    private fun loadDictionaryData(context: Context, dictId: String): DictionaryData? {
        if (dictId == BUILT_IN_EN_ID) {
            return loadBuiltInEnglish(context)
        }
        if (dictId.startsWith(FILE_ID_PREFIX)) {
            val fileName = dictId.removePrefix(FILE_ID_PREFIX)
            val customFile = findCustomFile(context, fileName) ?: return null
            val parsed = customFile.bufferedReader(Charsets.UTF_8).use { reader ->
                parseReader(dictId, reader)
            }
            if (parsed.digits.isEmpty()) return null
            return parsed
        }
        return null
    }

    private fun loadBuiltInEnglish(context: Context): DictionaryData {
        return context.assets.open(BUILT_IN_EN_ASSET).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                parseReader(BUILT_IN_EN_ID, reader)
            }
        }
    }

    private fun parseReader(dictId: String, reader: Reader): DictionaryData {
        val dedup = HashMap<String, ParsedEntry>(16_384)
        var lineIndex = 0
        BufferedReader(reader).use { br ->
            while (true) {
                val rawLine = br.readLine() ?: break
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                val parsed = parseLine(trimmed, lineIndex++) ?: continue
                val existing = dedup[parsed.word]
                if (existing == null) {
                    dedup[parsed.word] = parsed
                } else if (parsed.freq > existing.freq) {
                    existing.freq = parsed.freq
                }
            }
        }

        val entries = dedup.values.toMutableList()
        entries.sortWith(
            compareBy<ParsedEntry>({ it.digits })
                .thenByDescending { it.freq }
                .thenBy { it.word }
        )

        val count = entries.size
        val digits = Array(count) { entries[it].digits }
        val words = Array(count) { entries[it].word }
        val freqs = IntArray(count) { entries[it].freq }
        return DictionaryData(dictId, digits, words, freqs)
    }

    private fun parseLine(trimmed: String, lineRankIndex: Int): ParsedEntry? {
        // (a) digits<TAB>word<TAB>freq
        val tab1 = trimmed.indexOf('\t')
        if (tab1 > 0) {
            val tab2 = trimmed.indexOf('\t', tab1 + 1)
            if (tab2 > tab1 + 1 && trimmed.indexOf('\t', tab2 + 1) == -1) {
                val rawDigits = trimmed.substring(0, tab1).trim()
                val rawWord = trimmed.substring(tab1 + 1, tab2).trim()
                val rawFreq = trimmed.substring(tab2 + 1).trim()
                val parsedFreq = rawFreq.toLongOrNull()
                if (parsedFreq != null && parsedFreq >= 0L && isDigits2To9(rawDigits)) {
                    val word = rawWord.lowercase(Locale.ROOT)
                    if (word.length > MAX_WORD_LENGTH) return null
                    val sig = T9Keymap.signature(word) ?: return null
                    if (sig != rawDigits) return null
                    val freq = parsedFreq.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    return ParsedEntry(sig, word, freq)
                }
            }
        }

        // (b) word<space-or-tab>count  or  (c) single word per line
        val wsParts = trimmed.split(WHITESPACE_RE)
        return when (wsParts.size) {
            2 -> {
                val parsedFreq = wsParts[1].toLongOrNull() ?: return null
                if (parsedFreq < 0L) return null
                val word = wsParts[0].lowercase(Locale.ROOT)
                if (word.length > MAX_WORD_LENGTH) return null
                val sig = T9Keymap.signature(word) ?: return null
                val freq = parsedFreq.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                ParsedEntry(sig, word, freq)
            }
            1 -> {
                val word = wsParts[0].lowercase(Locale.ROOT)
                if (word.length > MAX_WORD_LENGTH) return null
                val sig = T9Keymap.signature(word) ?: return null
                val freq = (LINE_ORDER_BASE_FREQ - lineRankIndex).coerceAtLeast(1)
                ParsedEntry(sig, word, freq)
            }
            else -> null
        }
    }

    private fun isDigits2To9(s: String): Boolean {
        if (s.isEmpty()) return false
        for (i in 0 until s.length) {
            if (s[i] !in '2'..'9') return false
        }
        return true
    }

    private fun lowerBound(digits: Array<String>, target: String): Int {
        var lo = 0
        var hi = digits.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (digits[mid] < target) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return lo
    }

    private fun isBetterThan(word: String, freq: Int, learned: Int, other: Match): Boolean =
        compareMatchValues(word, freq, learned, other.word, other.freq, other.learnedCount) < 0

    private fun insertSortedBounded(list: MutableList<Match>, item: Match, limit: Int) {
        var pos = list.size
        for (i in 0 until list.size) {
            val existing = list[i]
            if (compareMatchValues(
                    item.word, item.freq, item.learnedCount,
                    existing.word, existing.freq, existing.learnedCount
                ) < 0
            ) {
                pos = i
                break
            }
        }
        if (pos < limit) {
            if (list.size == limit) {
                list.removeAt(limit - 1)
            }
            list.add(pos, item)
        }
    }

    companion object {
        private const val TAG = "T9DictionaryEngine"
        const val BUILT_IN_EN_ID = "en"
        const val BUILT_IN_EN_DISPLAY_NAME = "English (Built-in)"
        const val FILE_ID_PREFIX = "file:"
        const val BUILT_IN_EN_ASSET = "t9/en.tsv"
        const val DICTIONARIES_DIR_NAME = "dictionaries"
        const val MAX_WORD_LENGTH = 32
        const val SCAN_CAP = 4000
        const val DEFAULT_MAX_COMPLETIONS = 8
        const val DEFAULT_MAX_CANDIDATES = 12
        private const val LINE_ORDER_BASE_FREQ = 1_000_000_000
        private val WHITESPACE_RE = Regex("[ \\t]+")

        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Overridable in tests so the optional secondary directory check does not depend on device
         * `/sdcard` state. Returns null when inaccessible (per scoped storage on targetSdk 31).
         */
        internal var secondaryDictionariesDirProvider: () -> File = {
            File("/sdcard/Mochibun/$DICTIONARIES_DIR_NAME")
        }

        private val MATCH_COMPARATOR = Comparator<Match> { a, b ->
            compareMatchValues(a.word, a.freq, a.learnedCount, b.word, b.freq, b.learnedCount)
        }

        /**
         * Words with >= 2 learned commits float above static order (by learned count desc, then
         * static freq desc); 1 learned commit only breaks static frequency ties.
         */
        internal fun compareMatchValues(
            aWord: String,
            aFreq: Int,
            aLearned: Int,
            bWord: String,
            bFreq: Int,
            bLearned: Int
        ): Int {
            val aPromoted = aLearned >= 2
            val bPromoted = bLearned >= 2
            if (aPromoted != bPromoted) {
                return if (aPromoted) -1 else 1
            }
            if (aPromoted) {
                if (aLearned != bLearned) return bLearned.compareTo(aLearned)
                if (aFreq != bFreq) return bFreq.compareTo(aFreq)
                return aWord.compareTo(bWord)
            }
            if (aFreq != bFreq) return bFreq.compareTo(aFreq)
            if (aLearned != bLearned) return bLearned.compareTo(aLearned)
            return aWord.compareTo(bWord)
        }

        /**
         * Discovers built-in and custom `.tsv`/`.txt` dictionaries across
         * `getExternalFilesDir(null)/dictionaries` and (only when `listFiles()` is non-null)
         * `/sdcard/Mochibun/dictionaries`.
         */
        fun availableDictionaries(context: Context): List<DictionaryDescriptor> {
            val result = mutableListOf(
                DictionaryDescriptor(id = BUILT_IN_EN_ID, displayName = BUILT_IN_EN_DISPLAY_NAME)
            )
            val seenNames = HashSet<String>()
            val customFiles = discoverCustomDictionaryFiles(context)
            for (file in customFiles.sortedBy { it.name.lowercase(Locale.ROOT) }) {
                if (seenNames.add(file.name.lowercase(Locale.ROOT))) {
                    result.add(
                        DictionaryDescriptor(
                            id = "$FILE_ID_PREFIX${file.name}",
                            displayName = file.nameWithoutExtension
                                .replace('_', ' ')
                                .replace('-', ' ')
                                .ifBlank { file.name }
                        )
                    )
                }
            }
            return result
        }

        fun displayNameFor(context: Context, dictId: String): String {
            if (dictId == BUILT_IN_EN_ID) return BUILT_IN_EN_DISPLAY_NAME
            return availableDictionaries(context)
                .firstOrNull { it.id == dictId }
                ?.displayName
                ?: BUILT_IN_EN_DISPLAY_NAME
        }

        private fun findCustomFile(context: Context, fileName: String): File? {
            if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') || fileName == "..") {
                return null
            }
            return discoverCustomDictionaryFiles(context).firstOrNull { it.name == fileName }
        }

        private fun discoverCustomDictionaryFiles(context: Context): List<File> {
            val dirs = mutableListOf<File>()
            try {
                context.getExternalFilesDir(null)?.let { base ->
                    dirs.add(File(base, DICTIONARIES_DIR_NAME))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to query app external files dir", e)
            }
            try {
                val secondary = secondaryDictionariesDirProvider()
                if (secondary.listFiles() != null) {
                    dirs.add(secondary)
                }
            } catch (_: Exception) {
            }

            val files = mutableListOf<File>()
            val seen = HashSet<String>()
            for (dir in dirs) {
                val listed = try {
                    dir.listFiles { f ->
                        f.isFile && f.extension.lowercase(Locale.ROOT) in ALLOWED_EXTENSIONS
                    }
                } catch (_: Exception) {
                    null
                } ?: continue
                for (file in listed.sortedBy { it.name.lowercase(Locale.ROOT) }) {
                    if (seen.add(file.name.lowercase(Locale.ROOT))) {
                        files.add(file)
                    }
                }
            }
            return files
        }

        private val ALLOWED_EXTENSIONS = setOf("tsv", "txt")
    }
}

/**
 * Tracks per-dictionary learned word commit counts, capped at [MAX_WORDS_PER_DICT] entries per
 * dictionary (evicting the lowest-count entry when full).
 */
class UserWordStats {
    private val lock = Any()
    private val byDict = HashMap<String, HashMap<String, Int>>()

    @Volatile
    var dirty: Boolean = false
        private set

    fun recordCommit(dictId: String, word: String) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty() || clean.length > MAX_WORD_LENGTH) return
        synchronized(lock) {
            val map = byDict.getOrPut(dictId) { HashMap() }
            val existing = map[clean]
            if (existing != null) {
                map[clean] = (existing + 1).coerceAtMost(MAX_COUNT)
            } else {
                if (map.size >= MAX_WORDS_PER_DICT) {
                    evictLowest(map)
                }
                map[clean] = 1
            }
            dirty = true
        }
    }

    fun countFor(dictId: String, word: String): Int {
        val key = word.lowercase(Locale.ROOT)
        synchronized(lock) {
            return byDict[dictId]?.get(key) ?: 0
        }
    }

    fun hasAnyFor(dictId: String): Boolean = synchronized(lock) {
        !byDict[dictId].isNullOrEmpty()
    }

    fun snapshotFor(dictId: String): Map<String, Int> = synchronized(lock) {
        byDict[dictId]?.toMap() ?: emptyMap()
    }

    fun totalEntryCount(): Int = synchronized(lock) {
        var total = 0
        for (map in byDict.values) {
            total += map.size
        }
        total
    }

    fun clearAll() {
        synchronized(lock) {
            byDict.clear()
            dirty = false
        }
    }

    fun loadFromJsonString(json: String?) {
        synchronized(lock) {
            byDict.clear()
            dirty = false
            if (json.isNullOrBlank()) return
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return
            val dictKeys = root.keys()
            while (dictKeys.hasNext()) {
                val dictId = dictKeys.next()
                if (dictId.isBlank() || dictId.length > 128) continue
                val obj = root.optJSONObject(dictId) ?: continue
                val map = HashMap<String, Int>()
                val words = obj.keys()
                while (words.hasNext()) {
                    val rawWord = words.next()
                    val clean = rawWord.trim().lowercase(Locale.ROOT)
                    if (clean.isEmpty() || clean.length > MAX_WORD_LENGTH) continue
                    val count = obj.optInt(rawWord, 0)
                    if (count !in 1..MAX_COUNT) continue
                    val merged = ((map[clean] ?: 0) + count).coerceAtMost(MAX_COUNT)
                    map[clean] = merged
                }
                while (map.size > MAX_WORDS_PER_DICT) {
                    evictLowest(map)
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

    private fun evictLowest(map: HashMap<String, Int>) {
        var victimKey: String? = null
        var victimCount = Int.MAX_VALUE
        for ((k, c) in map) {
            if (c < victimCount || (c == victimCount && (victimKey == null || k < victimKey))) {
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
        const val MAX_WORD_LENGTH = 64
        const val MAX_COUNT = 100_000
    }
}
