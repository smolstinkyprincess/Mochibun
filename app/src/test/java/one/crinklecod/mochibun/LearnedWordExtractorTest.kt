package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.ime.LearnedEpochAction
import one.crinklecod.mochibun.data.ime.LearnedWordExtractor
import one.crinklecod.mochibun.data.ime.UserWordStore
import one.crinklecod.mochibun.data.ime.decideLearnedEpochAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class LearnedWordExtractorTest {

    @Test
    fun extractsValidWordsAtStartAndAfterAllowedBoundaries() {
        assertEquals("hello", LearnedWordExtractor.lastWord("hello"))
        assertEquals("hello", LearnedWordExtractor.lastWord("Hello"))
        assertEquals("don't", LearnedWordExtractor.lastWord("don't"))
        assertEquals("emii", LearnedWordExtractor.lastWord("hey emii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("  (emii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("[Emii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("{emii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("\"emii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("\u201Cemii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("\u2013emii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("\u2014emii"))
        assertEquals("emii", LearnedWordExtractor.lastWord("'emii'"))
        assertEquals("a".repeat(32), LearnedWordExtractor.lastWord(" " + "a".repeat(32)))
    }

    @Test
    fun rejectsDisallowedPrefixesLengthsAndNonMappableChars() {
        assertNull(LearnedWordExtractor.lastWord(null))
        assertNull(LearnedWordExtractor.lastWord(""))
        assertNull(LearnedWordExtractor.lastWord("   "))
        assertNull(LearnedWordExtractor.lastWord("user@host"))
        assertNull(LearnedWordExtractor.lastWord("#tag"))
        assertNull(LearnedWordExtractor.lastWord("abc123"))
        assertNull(LearnedWordExtractor.lastWord("abc123def"))
        assertNull(LearnedWordExtractor.lastWord("a-b"))
        assertNull(LearnedWordExtractor.lastWord("foo-bar"))
        assertNull(LearnedWordExtractor.lastWord("ab"))
        assertNull(LearnedWordExtractor.lastWord("hi"))
        assertNull(LearnedWordExtractor.lastWord("a".repeat(33)))
        assertNull(LearnedWordExtractor.lastWord("東京"))
        assertNull(LearnedWordExtractor.lastWord("hello!"))
        assertNull(LearnedWordExtractor.lastWord("hello "))
    }

    @Test
    fun userWordStoreHandlesThresholdCapAndOldestTieEviction() {
        val store = UserWordStore()
        assertEquals(1, store.recordSighting("en", "emii"))
        assertEquals(0, store.visibleWordCount())
        assertTrue(store.visibleWordsFor("en").isEmpty())

        assertEquals(2, store.recordSighting("en", "EMII"))
        assertEquals(1, store.visibleWordCount())
        assertEquals(listOf("emii" to 2), store.visibleWordsFor("en"))
        assertTrue(store.dirty)

        // 500-word cap evicts lowest count, breaking ties by oldest-inserted first
        val capStore = UserWordStore()
        capStore.recordSighting("en", "oldestword") // count 1, oldest
        capStore.recordSighting("en", "secondword") // count 1, 2nd oldest
        capStore.recordSighting("en", "promotedkeep")
        capStore.recordSighting("en", "promotedkeep") // count 2
        for (i in 0 until 497) {
            val a = ('a' + (i / 26))
            val b = ('a' + (i % 26))
            capStore.recordSighting("en", "zz${a}${b}word")
        }
        assertEquals(500, capStore.snapshotFor("en").size)

        // Entry #501 must evict "oldestword" (count 1, oldest), keeping "secondword" and "promotedkeep"
        capStore.recordSighting("en", "newestword")
        assertEquals(500, capStore.snapshotFor("en").size)
        assertEquals(0, capStore.countFor("en", "oldestword"))
        assertEquals(1, capStore.countFor("en", "secondword"))
        assertEquals(2, capStore.countFor("en", "promotedkeep"))
        assertEquals(1, capStore.countFor("en", "newestword"))
    }

    @Test
    fun epochDecisionRequiresExactMatch() {
        assertEquals(LearnedEpochAction.KEEP_IN_MEMORY, decideLearnedEpochAction(0, 0))
        assertEquals(LearnedEpochAction.KEEP_IN_MEMORY, decideLearnedEpochAction(4, 4))
        assertEquals(LearnedEpochAction.RELOAD_FROM_PREFS, decideLearnedEpochAction(0, 1))
        assertEquals(LearnedEpochAction.RELOAD_FROM_PREFS, decideLearnedEpochAction(3, 2))
    }
}
