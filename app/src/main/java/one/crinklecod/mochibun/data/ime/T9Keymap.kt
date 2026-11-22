package one.crinklecod.mochibun.data.ime

import android.view.KeyEvent
import java.text.Normalizer
import java.util.Locale

/**
 * Maps words to T9 keypad digit signatures (`2`–`9`) and physical keypad codes to digits.
 */
object T9Keymap {

    /**
     * Converts [word] to its T9 digit signature (`2`–`9`), or returns `null` if any non-mappable
     * character remains or no digits are produced.
     *
     * Rules: lowercase -> NFD + strip combining marks -> manual fold (`ß->ss`, `æ->ae`, `œ->oe`,
     * `ø->o`, `ł->l`, `đ->d`) -> skip `'` and `-` -> map `a..z` to `2..9`.
     */
    fun signature(word: String): String? {
        if (word.isEmpty()) return null
        val normalized = Normalizer.normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val out = StringBuilder(normalized.length)
        for (i in 0 until normalized.length) {
            val ch = normalized[i]
            val type = Character.getType(ch)
            if (type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            ) {
                continue
            }
            when (ch) {
                'ß' -> out.append("77") // ss
                'æ' -> out.append("23") // ae
                'œ' -> out.append("63") // oe
                'ø' -> out.append('6')  // o
                'ł' -> out.append('5')  // l
                'đ' -> out.append('3')  // d
                '\'', '-' -> Unit
                in 'a'..'z' -> out.append(charToDigit(ch))
                else -> return null
            }
        }
        return if (out.isEmpty()) null else out.toString()
    }

    fun digitForKeyCode(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_2 -> '2'
        KeyEvent.KEYCODE_3 -> '3'
        KeyEvent.KEYCODE_4 -> '4'
        KeyEvent.KEYCODE_5 -> '5'
        KeyEvent.KEYCODE_6 -> '6'
        KeyEvent.KEYCODE_7 -> '7'
        KeyEvent.KEYCODE_8 -> '8'
        KeyEvent.KEYCODE_9 -> '9'
        else -> null
    }

    /**
     * Individual letters mapped to a T9 digit key (`2`–`9`), prioritizing common single-letter
     * words (`"a"` on `2` and `"i"` on `4`).
     */
    fun lettersForDigit(digit: Char): List<String> = when (digit) {
        '2' -> LETTERS_2
        '3' -> LETTERS_3
        '4' -> LETTERS_4
        '5' -> LETTERS_5
        '6' -> LETTERS_6
        '7' -> LETTERS_7
        '8' -> LETTERS_8
        '9' -> LETTERS_9
        else -> emptyList()
    }

    private val LETTERS_2 = listOf("a", "b", "c")
    private val LETTERS_3 = listOf("d", "e", "f")
    private val LETTERS_4 = listOf("i", "g", "h")
    private val LETTERS_5 = listOf("j", "k", "l")
    private val LETTERS_6 = listOf("m", "n", "o")
    private val LETTERS_7 = listOf("p", "q", "r", "s")
    private val LETTERS_8 = listOf("t", "u", "v")
    private val LETTERS_9 = listOf("w", "x", "y", "z")

    private fun charToDigit(ch: Char): Char = when (ch) {
        'a', 'b', 'c' -> '2'
        'd', 'e', 'f' -> '3'
        'g', 'h', 'i' -> '4'
        'j', 'k', 'l' -> '5'
        'm', 'n', 'o' -> '6'
        'p', 'q', 'r', 's' -> '7'
        't', 'u', 'v' -> '8'
        'w', 'x', 'y', 'z' -> '9'
        else -> error("Unexpected character: $ch")
    }
}
