package com.crinklecodone.mochibun.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.MaruMinyaMFontFamily

/**
 * Authentic 2000s Galakei numeric jump indicator badge ([1]–[9]).
 * Allows flip-phone physical keypad shortcuts to instantly trigger menu rows.
 */
@Composable
fun JumpNumberBadge(
    digit: Int,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = if (isFocused) {
        ActiveTheme.selectionGlow
    } else {
        ActiveTheme.accentDeep.copy(alpha = 0.65f)
    }

    Text(
        text = "[$digit]",
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = MaruMinyaMFontFamily,
        modifier = modifier
    )
}

/**
 * Maps physical keypad numbers KEYCODE_1 through KEYCODE_9 to their 1-based digit (1..9).
 * Returns null for non-numeric or out-of-range keys.
 */
fun getDigitFromKeyCode(keyCode: Int): Int? = when (keyCode) {
    android.view.KeyEvent.KEYCODE_1 -> 1
    android.view.KeyEvent.KEYCODE_2 -> 2
    android.view.KeyEvent.KEYCODE_3 -> 3
    android.view.KeyEvent.KEYCODE_4 -> 4
    android.view.KeyEvent.KEYCODE_5 -> 5
    android.view.KeyEvent.KEYCODE_6 -> 6
    android.view.KeyEvent.KEYCODE_7 -> 7
    android.view.KeyEvent.KEYCODE_8 -> 8
    android.view.KeyEvent.KEYCODE_9 -> 9
    else -> null
}
