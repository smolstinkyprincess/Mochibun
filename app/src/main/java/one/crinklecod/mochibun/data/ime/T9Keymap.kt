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
