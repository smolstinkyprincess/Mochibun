package one.crinklecod.mochibun.data.ime

import java.util.Locale

/**
 * Extracts learnable whole words from text immediately preceding the cursor.
 */
object LearnedWordExtractor {
    const val MIN_WORD_LENGTH = 3
    const val MAX_WORD_LENGTH = T9DictionaryEngine.MAX_WORD_LENGTH

    private val ALLOWED_BOUNDARY_PUNCTUATION = setOf(
        '(', '[', '{', '"', '\u201C', '\u2013', '\u2014'
    )

    /**
     * Returns the trailing word in [textBeforeCursor] normalized to lowercase if it is a valid
     * standalone word of length `3..32`, or `null` if empty, bounded by disqualifying characters
     * (`@`, `#`, `-`, digits, etc.), or not T9-mappable.
     */
    fun lastWord(textBeforeCursor: CharSequence?): String? {
        if (textBeforeCursor.isNullOrEmpty()) return null
        var runStart = textBeforeCursor.length
        while (runStart > 0 && isWordChar(textBeforeCursor[runStart - 1])) {
            runStart--
        }
        if (runStart == textBeforeCursor.length) return null

        if (runStart > 0) {
            val before = textBeforeCursor[runStart - 1]
            if (!before.isWhitespace() && before !in ALLOWED_BOUNDARY_PUNCTUATION) {
                return null
            }
        }

        val raw = textBeforeCursor.subSequence(runStart, textBeforeCursor.length).toString()
        val stripped = raw.trim('\'')
        if (stripped.length !in MIN_WORD_LENGTH..MAX_WORD_LENGTH) return null
        val lower = stripped.lowercase(Locale.ROOT)
        if (T9Keymap.signature(lower) == null) return null
        return lower
    }

    /**
     * Checks whether [word] satisfies learned-word rules (`3..32` letters/apostrophes, no
     * leading/trailing apostrophe, T9-mappable).
     */
    fun isValidLearnedWord(word: String): Boolean {
        if (word.length !in MIN_WORD_LENGTH..MAX_WORD_LENGTH) return false
        if (word.first() == '\'' || word.last() == '\'') return false
        for (i in 0 until word.length) {
            if (!isWordChar(word[i])) return false
        }
        return T9Keymap.signature(word) != null
    }

    private fun isWordChar(ch: Char): Boolean = ch.isLetter() || ch == '\''
}
