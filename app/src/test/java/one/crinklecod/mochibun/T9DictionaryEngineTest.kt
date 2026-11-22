package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.ime.T9DictionaryEngine
import one.crinklecod.mochibun.data.ime.T9Keymap
import one.crinklecod.mochibun.data.ime.UserWordStats
import one.crinklecod.mochibun.data.ime.UserWordStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class T9DictionaryEngineTest {

    @Test
    fun keymapSignatureHandlesAccentsFoldingsApostrophesAndRejectsInvalid() {
        assertEquals("43556", T9Keymap.signature("Hello"))
        assertEquals("2233", T9Keymap.signature("café"))
        assertEquals("62483", T9Keymap.signature("naïve"))
        assertEquals("3668", T9Keymap.signature("don't"))
        assertEquals("2667", T9Keymap.signature("co-op"))
        assertEquals("7872773", T9Keymap.signature("Straße"))
        assertEquals("238437", T9Keymap.signature("æther"))
        assertEquals("26387", T9Keymap.signature("cœur"))
        assertEquals("76", T9Keymap.signature("Sø"))
        assertEquals("5639", T9Keymap.signature("Łódź"))
        assertEquals("32", T9Keymap.signature("đà"))

        assertNull(T9Keymap.signature(""))
        assertNull(T9Keymap.signature("'"))
        assertNull(T9Keymap.signature("---"))
        assertNull(T9Keymap.signature("abc2"))
        assertNull(T9Keymap.signature("hello!"))
        assertNull(T9Keymap.signature("東京"))
    }

    @Test
    fun exactAndPrefixLookupsFollowFrequencyAndCandidateCaps() {
        val tsv = buildString {
            appendLine("4663\thood\t100")
            appendLine("4663\tgood\t900")
            appendLine("4663\thome\t500")
            appendLine("4663\tgone\t300")
            // Longer prefix completions under 4663*
            for (i in 1..12) {
                // "hood..." / "home..." style words all sharing prefix 4663
                val suffix = "a".repeat(i)
                val w = "home$suffix"
                appendLine("${T9Keymap.signature(w)}\t$w\t${1000 - i}")
            }
        }
        val engine = T9DictionaryEngine()
        assertFalse(engine.ready)
        engine.loadFromReader("test", StringReader(tsv))
        assertTrue(engine.ready)

        val exact = engine.exact("4663")
        assertEquals(listOf("good", "home", "gone", "hood"), exact.map { it.word })

        // Longer words only (exact matches excluded) and capped at limit
        val completions = engine.completions("4663", limit = 5)
        assertEquals(5, completions.size)
        assertTrue(completions.none { it.word in setOf("good", "home", "gone", "hood") })
        assertEquals("homea", completions.first().word)

        // Exact + up to 8 completions, capped at 12 total (4 exact + 8 completions = 12)
        val combined = engine.candidatesFor("4663")
        assertTrue(combined.hasDictionaryMatch)
        assertEquals(12, combined.words.size)
        assertEquals(listOf("good", "home", "gone", "hood"), combined.words.take(4))

        // No dictionary match -> raw digits fallback
        val missing = engine.candidatesFor("999999")
        assertFalse(missing.hasDictionaryMatch)
        assertEquals(listOf("999999"), missing.words)
    }

    @Test
    fun learnedWordsTieBreakAtOneAndPromoteAtTwoPlusAndEvictLowest() {
        val tsv = """
            4663	good	900
            4663	home	500
            4663	gone	500
            4663	hood	100
        """.trimIndent()
        val stats = UserWordStats()
        val engine = T9DictionaryEngine(stats)
        engine.loadFromReader("en", StringReader(tsv))

        // 1 commit on 'home' breaks the 500 tie with 'gone' (which precedes 'home' alphabetically),
        // while staying below 'good' (900)
        stats.recordCommit("en", "HOME") // stored lowercase
        assertEquals(1, stats.countFor("en", "home"))
        assertEquals(listOf("good", "home", "gone", "hood"), engine.exact("4663").map { it.word })

        // 1 commit on 'hood' does NOT lift it over higher static frequencies
        stats.recordCommit("en", "hood")
        assertEquals(listOf("good", "home", "gone", "hood"), engine.exact("4663").map { it.word })

        // 2nd commit on 'hood' promotes it above all unpromoted static entries
        stats.recordCommit("en", "hood")
        assertEquals(listOf("hood", "good", "home", "gone"), engine.exact("4663").map { it.word })

        // 3 commits on 'gone' lifts 'gone' above 'hood' (2 commits)
        repeat(3) { stats.recordCommit("en", "gone") }
        assertEquals(listOf("gone", "hood", "good", "home"), engine.exact("4663").map { it.word })

        // Different dictionary id stays unaffected (static order: gone before home on tie)
        engine.loadFromReader("file:custom.txt", StringReader(tsv))
        assertEquals(listOf("good", "gone", "home", "hood"), engine.exact("4663").map { it.word })

        // JSON round-trip preserves counts and clears dirty flag
        val reloaded = UserWordStats()
        assertTrue(stats.dirty)
        stats.markClean()
        assertFalse(stats.dirty)
        reloaded.loadFromJsonString(stats.toJsonString())
        assertFalse(reloaded.dirty)
        assertEquals(3, reloaded.countFor("en", "gone"))
        assertEquals(2, reloaded.countFor("en", "hood"))
        assertEquals(1, reloaded.countFor("en", "home"))

        // 500-word cap per dictionary evicts lowest count
        val capStats = UserWordStats()
        repeat(2) { capStats.recordCommit("en", "keepme") }
        for (i in 1..499) {
            capStats.recordCommit("en", "w$i")
        }
        assertEquals(500, capStats.snapshotFor("en").size)
        // Adding word #501 must evict a count-1 entry, keeping 'keepme' (count 2)
        capStats.recordCommit("en", "extra")
        assertEquals(500, capStats.snapshotFor("en").size)
        assertEquals(2, capStats.countFor("en", "keepme"))
        assertEquals(1, capStats.countFor("en", "extra"))
        assertTrue(capStats.dirty)
    }

    @Test
    fun customLoaderParsesAllThreeFormatsCommentsDedupeAndFilters() {
        val formatA = """
            # Format (a): digits<TAB>word<TAB>freq
            43556	Hello	100
            43556	hello	500
            222	invalid!	999
            ${"a".repeat(33).let { "${T9Keymap.signature(it)}\t$it\t1000" }}
        """.trimIndent()
        val engine = T9DictionaryEngine()
        engine.loadFromReader("file:a.tsv", StringReader(formatA))
        val matchesA = engine.exact("43556")
        assertEquals(1, matchesA.size)
        assertEquals("hello", matchesA[0].word)
        assertEquals(500, matchesA[0].freq)
        assertTrue(engine.exact("222").isEmpty())

        val formatB = """
            # Format (b): word<space-or-tab>count
            work 800
            york	400
            work 200
            bad_word 9999
        """.trimIndent()
        engine.loadFromReader("file:b.txt", StringReader(formatB))
        assertEquals(listOf("work", "york"), engine.exact("9675").map { it.word })
        assertEquals(800, engine.exact("9675").first().freq)

        val formatC = """
            # Format (c): one word per line (ranked by line order)

            home
            good
            gone
            skip123
        """.trimIndent()
        engine.loadFromReader("file:c.txt", StringReader(formatC))
        assertEquals(listOf("home", "good", "gone"), engine.exact("4663").map { it.word })
    }

    @Test
    fun availableDictionariesIncludesBuiltInAndCustomDirsWhenReadable() {
        val context = RuntimeEnvironment.getApplication()
        val appDictDir = File(context.getExternalFilesDir(null), T9DictionaryEngine.DICTIONARIES_DIR_NAME)
        appDictDir.mkdirs()
        val customPrimary = File(appDictDir, "fr_custom.tsv").apply { writeText("2665687\tbonjour\t100\n") }
        val customIgnored = File(appDictDir, "readme.md").apply { writeText("ignored") }

        val secondaryDir = File(context.cacheDir, "mock_sdcard_dicts").apply { mkdirs() }
        val customSecondary = File(secondaryDir, "es_words.txt").apply { writeText("hola\n") }
        val duplicateSecondary = File(secondaryDir, "fr_custom.tsv").apply { writeText("dup\n") }

        val prevProvider = T9DictionaryEngine.secondaryDictionariesDirProvider
        try {
            T9DictionaryEngine.secondaryDictionariesDirProvider = { secondaryDir }
            val list = T9DictionaryEngine.availableDictionaries(context)
            assertEquals(listOf("en", "file:es_words.txt", "file:fr_custom.tsv"), list.map { it.id })
        } finally {
            T9DictionaryEngine.secondaryDictionariesDirProvider = prevProvider
            customPrimary.delete()
            customIgnored.delete()
            customSecondary.delete()
            duplicateSecondary.delete()
            secondaryDir.delete()
        }
    }

    @Test
    fun builtInEnglishAssetIsValidSortedAndWithinLineBudget() {
        val assetFile = listOf(
            File("src/main/assets/t9/en.tsv"),
            File("app/src/main/assets/t9/en.tsv")
        ).firstOrNull { it.isFile }
            ?: error("Could not locate app/src/main/assets/t9/en.tsv from ${File(".").absolutePath}")

        var count = 0
        var prevDigits: String? = null
        var prevFreq = Int.MAX_VALUE

        assetFile.bufferedReader(Charsets.UTF_8).use { br ->
            while (true) {
                val line = br.readLine() ?: break
                val parts = line.split('\t')
                assertEquals("Expected 3 tab columns at line ${count + 1}: $line", 3, parts.size)
                val digits = parts[0]
                val word = parts[1]
                val freq = parts[2].toInt()

                assertEquals(
                    "Signature mismatch at line ${count + 1} for '$word'",
                    T9Keymap.signature(word),
                    digits
                )
                if (prevDigits != null) {
                    assertTrue(
                        "Not sorted by digits at line ${count + 1}: $prevDigits -> $digits",
                        digits >= prevDigits
                    )
                    if (digits == prevDigits) {
                        assertTrue(
                            "Not sorted by freq desc at line ${count + 1}: $prevFreq -> $freq",
                            freq <= prevFreq
                        )
                    }
                }
                prevDigits = digits
                prevFreq = freq
                count++
            }
        }

        assertTrue("en.tsv should not be empty", count > 0)
        assertTrue("en.tsv has $count lines, expected <= 45000", count <= 45_000)

        // Verify loading the real asset into the engine works for key probes
        val engine = T9DictionaryEngine()
        assetFile.bufferedReader(Charsets.UTF_8).use { reader ->
            engine.loadFromReader("en", reader)
        }
        assertEquals("hello", engine.exact("43556").firstOrNull()?.word)
        assertEquals(listOf("good", "home", "gone", "hood"), engine.exact("4663").take(4).map { it.word })
    }

    @Test
    fun builtInEnglishAssetContainsCommonContractions() {
        val assetFile = listOf(
            File("src/main/assets/t9/en.tsv"),
            File("app/src/main/assets/t9/en.tsv")
        ).firstOrNull { it.isFile }
            ?: error("Could not locate app/src/main/assets/t9/en.tsv from ${File(".").absolutePath}")

        val engine = T9DictionaryEngine()
        assetFile.bufferedReader(Charsets.UTF_8).use { reader ->
            engine.loadFromReader("en", reader)
        }

        for (contraction in listOf("don't", "i'm", "it's", "can't", "that's")) {
            val sig = T9Keymap.signature(contraction)!!
            val words = engine.exact(sig).map { it.word }
            assertTrue("Expected '$contraction' under $sig, got $words", contraction in words)
        }
    }

    @Test
    fun userWordStoreJsonRoundTripAndClearReset() {
        val store = UserWordStore()
        store.recordSighting("en", "emii")
        store.recordSighting("en", "emii")
        store.recordSighting("en", "mochibun")
        store.recordSighting("file:custom.tsv", "nyan")
        store.recordSighting("file:custom.tsv", "nyan")

        val json = store.toJsonString()
        val reloaded = UserWordStore()
        reloaded.loadFromJsonString(json)
        assertFalse(reloaded.dirty)
        assertEquals(2, reloaded.countFor("en", "emii"))
        assertEquals(1, reloaded.countFor("en", "mochibun"))
        assertEquals(2, reloaded.countFor("file:custom.tsv", "nyan"))
        assertEquals(2, reloaded.visibleWordCount())

        reloaded.clearAll()
        assertFalse(reloaded.dirty)
        assertEquals(0, reloaded.visibleWordCount())
        assertTrue(reloaded.snapshotFor("en").isEmpty())
    }

    @Test
    fun engineMergesUserWordsByThresholdInExactAndCompletions() {
        val tsv = """
            3644	emig	900
            3644	emih	400
            364422	emigaa	800
            364433	emihdd	500
        """.trimIndent()
        val stats = UserWordStats()
        val store = UserWordStore()
        val engine = T9DictionaryEngine(stats, store)
        engine.loadFromReader("en", StringReader(tsv))

        // Count 1 is stored but hidden from exact() and completions()
        engine.recordUserWordSighting("emii")
        assertEquals(listOf("emig", "emih"), engine.exact("3644").map { it.word })
        engine.recordUserWordSighting("emiiaa")
        assertEquals(listOf("emigaa", "emihdd"), engine.completions("3644", 8).map { it.word })

        // Count 2 appears AFTER dictionary exact matches, and FIRST in completions
        engine.recordUserWordSighting("emii")
        engine.recordUserWordSighting("emiiaa")
        assertEquals(listOf("emig", "emih", "emii"), engine.exact("3644").map { it.word })
        assertEquals(listOf("emiiaa", "emigaa", "emihdd"), engine.completions("3644", 8).map { it.word })

        // Count 3+ promotes user word BEFORE dictionary exact matches
        engine.recordUserWordSighting("emii")
        assertEquals(listOf("emii", "emig", "emih"), engine.exact("3644").map { it.word })

        // Secondary count >= 3 orders by count descending, while count == 2 stays after dict
        repeat(4) { engine.recordUserWordSighting("foii") } // 3644
        repeat(2) { engine.recordUserWordSighting("doii") } // 3644
        assertEquals(
            listOf("foii", "emii", "emig", "emih", "doii"),
            engine.exact("3644").map { it.word }
        )

        // Completion ranking: higher count user words first, then dictionary completions
        repeat(3) { engine.recordUserWordSighting("emiibb") }
        assertEquals(
            listOf("emiibb", "emiiaa", "emigaa", "emihdd"),
            engine.completions("3644", 8).map { it.word }
        )

        // Dictionary vs user-word helpers prevent double counting
        assertTrue(engine.hasDictionaryWord("emig"))
        assertFalse(engine.hasDictionaryWord("emii"))
        assertTrue(engine.isUserWord("emii"))
        assertFalse(engine.isUserWord("emig"))

        // Reloading learned data resets both stores and clears visibility
        engine.reloadLearnedData("", "")
        assertEquals(listOf("emig", "emih"), engine.exact("3644").map { it.word })
        assertEquals(listOf("emigaa", "emihdd"), engine.completions("3644", 8).map { it.word })
    }
}
