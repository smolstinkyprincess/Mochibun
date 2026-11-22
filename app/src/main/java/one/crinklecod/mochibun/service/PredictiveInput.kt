package one.crinklecod.mochibun.service

import java.util.Locale

/**
 * Pure-Kotlin state holder for an in-progress T9 predictive word.
 *
 * Captures [capitalizeFirst] once when the first digit of a word is pushed (because `autoCapNext`
 * flips to `false` mid-word as soon as composing text appears) and keeps the lowercase dictionary
 * candidate list parallel to the displayed candidates in the candidate strip.
 */
internal class PredictiveInput {
    private val digitsBuffer = StringBuilder()

    val digits: String
        get() = digitsBuffer.toString()

    val isEmpty: Boolean
        get() = digitsBuffer.isEmpty()

    var capitalizeFirst: Boolean = false
        private set

    var lowercaseCandidates: List<String> = emptyList()
        private set

    var rawDigitFallback: Boolean = false
        private set

    fun push(digit: Char, autoCapActive: Boolean = false) {
        require(digit in '2'..'9') { "T9 digit must be in '2'..'9', got '$digit'" }
        if (digitsBuffer.isEmpty()) {
            capitalizeFirst = autoCapActive
        }
        digitsBuffer.append(digit)
    }

    fun pop(): Boolean {
        if (digitsBuffer.isEmpty()) return false
        digitsBuffer.deleteCharAt(digitsBuffer.length - 1)
        if (digitsBuffer.isEmpty()) {
            clear()
        }
        return true
    }

    fun setCandidates(words: List<String>, rawDigitFallback: Boolean) {
        this.lowercaseCandidates = words
        this.rawDigitFallback = rawDigitFallback
    }

    fun setCapitalizeFirst(capitalize: Boolean) {
        if (!isEmpty) {
            capitalizeFirst = capitalize
        }
    }

    fun lowercaseWordAt(index: Int): String? {
        if (isEmpty || rawDigitFallback) return null
        return lowercaseCandidates.getOrNull(index)
    }

    fun clear() {
        digitsBuffer.setLength(0)
        capitalizeFirst = false
        lowercaseCandidates = emptyList()
        rawDigitFallback = false
    }

    companion object {
        /**
         * Formats a dictionary [word] for display and commit:
         * - [upperAll] (`ShiftMode.CAPS`) -> full uppercase.
         * - Otherwise capitalises the first letter when [capitalizeFirst] is true, or when the
         *   word is standalone `"i"` or starts with `"i'"` (`"I"`, `"I'm"`, `"I'll"`, `"I've"`, `"I'd"`).
         */
        fun applyCase(word: String, upperAll: Boolean, capitalizeFirst: Boolean): String {
            if (word.isEmpty()) return ""
            if (upperAll) return word.uppercase(Locale.ROOT)
            val alwaysCapI = word.equals("i", ignoreCase = true) || word.startsWith("i'", ignoreCase = true)
            if (!capitalizeFirst && !alwaysCapI) return word
            val first = word[0].titlecase(Locale.ROOT)
            return if (word.length == 1) first else first + word.substring(1)
        }
    }
}
