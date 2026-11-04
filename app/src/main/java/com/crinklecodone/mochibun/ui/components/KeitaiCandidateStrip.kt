package com.crinklecodone.mochibun.ui.components

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.ui.theme.ActiveTheme

/** Multi-tap pools for the keypad. `1` and `*` both open the symbol pool. */
object KeitaiCandidatePools {
    val POOL_2 = arrayOf("a", "b", "c", "A", "B", "C", "2")
    val POOL_3 = arrayOf("d", "e", "f", "D", "E", "F", "3")
    val POOL_4 = arrayOf("g", "h", "i", "G", "H", "I", "4")
    val POOL_5 = arrayOf("j", "k", "l", "J", "K", "L", "5")
    val POOL_6 = arrayOf("m", "n", "o", "M", "N", "O", "6")
    val POOL_7 = arrayOf("p", "q", "r", "s", "P", "Q", "R", "S", "7")
    val POOL_8 = arrayOf("t", "u", "v", "T", "U", "V", "8")
    val POOL_9 = arrayOf("w", "x", "y", "z", "W", "X", "Y", "Z", "9")
    val POOL_0 = arrayOf(" ", "0")
    val POOL_SYMBOLS = arrayOf(".", ",", "?", "!", "-", "@", "/", ":", "♡", "★", "♥", "♪")

    fun forKey(keyCode: Int): Array<String>? = when (keyCode) {
        KeyEvent.KEYCODE_2 -> POOL_2
        KeyEvent.KEYCODE_3 -> POOL_3
        KeyEvent.KEYCODE_4 -> POOL_4
        KeyEvent.KEYCODE_5 -> POOL_5
        KeyEvent.KEYCODE_6 -> POOL_6
        KeyEvent.KEYCODE_7 -> POOL_7
        KeyEvent.KEYCODE_8 -> POOL_8
        KeyEvent.KEYCODE_9 -> POOL_9
        KeyEvent.KEYCODE_0 -> POOL_0
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_STAR -> POOL_SYMBOLS
        else -> null
    }
}

/**
 * Multi-tap state machine. The cycling character stays pending in the strip until it is
 * committed: by pressing a different key, by pressing the same key after [timeoutMs], or via
 * [commitActive].
 */
class KeitaiCandidateState(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val poolProvider: (Int) -> Array<String>? = KeitaiCandidatePools::forKey
) {
    var isActive by mutableStateOf(false)
        private set
    var candidates by mutableStateOf(EMPTY)
        private set
    var selectedIndex by mutableIntStateOf(0)
        private set
    var lastKeyCode = KeyEvent.KEYCODE_UNKNOWN
        private set
    var lastPressTime = 0L
        private set

    /** Returns true if [keyCode] is a multi-tap key (and was consumed). */
    fun handleKey(keyCode: Int, onCommit: (String) -> Unit): Boolean {
        val pool = poolProvider(keyCode) ?: return false
        val now = clock()
        // `1` and `*` share a pool, but are still different keys.
        if (isActive && keyCode == lastKeyCode && now - lastPressTime <= timeoutMs) {
            selectedIndex = (selectedIndex + 1) % candidates.size
        } else {
            if (isActive) onCommit(candidates[selectedIndex])
            candidates = pool
            selectedIndex = 0
            isActive = true
        }
        lastKeyCode = keyCode
        lastPressTime = now
        return true
    }

    /** Wraps from the first candidate to the last. */
    fun navigateLeft() {
        if (!isActive) return
        selectedIndex = (selectedIndex - 1 + candidates.size) % candidates.size
    }

    /** Wraps from the last candidate to the first. */
    fun navigateRight() {
        if (!isActive) return
        selectedIndex = (selectedIndex + 1) % candidates.size
    }

    /**
     * Swaps the pending pool for an equally sized one (e.g. the same key in another letter case),
     * keeping the selection. Ignored when nothing is pending or the sizes differ.
     */
    fun replaceCandidates(pool: Array<String>) {
        if (!isActive || pool.size != candidates.size) return
        candidates = pool
    }

    fun commitActive(onCommit: (String) -> Unit) {
        if (!isActive) return
        onCommit(candidates[selectedIndex])
        reset()
    }

    fun reset() {
        isActive = false
        candidates = EMPTY
        selectedIndex = 0
        lastKeyCode = KeyEvent.KEYCODE_UNKNOWN
        lastPressTime = 0L
    }

    /** Drops the pending candidate without touching committed text. */
    fun handleBackspace(): Boolean {
        if (!isActive) return false
        reset()
        return true
    }

    /** Set when BACK cancelled a candidate on DOWN, so its UP doesn't also dismiss the dialog. */
    private var swallowBackUp = false

    /**
     * Routes a key event from a dialog's `onPreviewKeyEvent` while its text field has focus.
     * Returns true if the event was consumed. DPAD_UP/DOWN commit the pending candidate and
     * return false, so the dialog's own focus navigation still runs.
     */
    fun handleFieldKeyEvent(keyCode: Int, isDown: Boolean, repeatCount: Int, onCommit: (String) -> Unit): Boolean {
        if (KeitaiCandidatePools.forKey(keyCode) != null) {
            if (isDown && repeatCount == 0) handleKey(keyCode, onCommit)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (isDown && isActive) {
                reset()
                swallowBackUp = true
                return true
            }
            if (!isDown && swallowBackUp) {
                swallowBackUp = false
                return true
            }
            return false
        }
        if (!isActive || !isDown) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { navigateLeft(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { navigateRight(); true }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> { commitActive(onCommit); true }
            KeyEvent.KEYCODE_DEL -> handleBackspace()
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> { commitActive(onCommit); false }
            else -> false
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 900L
        private val EMPTY = emptyArray<String>()
    }
}

/** Inserts [insert] at the cursor (replacing any selection), capped at [maxLength]. */
fun TextFieldValue.insertAtCursor(insert: String, maxLength: Int = Int.MAX_VALUE): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (text.length - (end - start) + insert.length > maxLength) return this
    val newText = text.substring(0, start) + insert + text.substring(end)
    return TextFieldValue(newText, TextRange(start + insert.length))
}

@Composable
fun KeitaiCandidateStrip(
    state: KeitaiCandidateState,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(ActiveTheme.surface.copy(alpha = 0.95f), shape)
            .border(1.5.dp, ActiveTheme.cardBorder, shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingLabel != null) {
            Text(
                text = leadingLabel,
                color = ActiveTheme.accentDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(text = "♡", color = ActiveTheme.accentDeep, fontSize = 12.sp)
        }
        // Padding inside the scroll container keeps the focus halo from being clipped.
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val candidates = state.candidates
            val selected = state.selectedIndex
            candidates.forEachIndexed { index, candidate ->
                val isSelected = index == selected
                Box(
                    modifier = Modifier
                        .graphicsLayer { clip = false }
                        .softGlowFocus(isSelected = isSelected, cornerRadius = 10.dp)
                        .defaultMinSize(minWidth = 26.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (candidate == " ") "␣" else candidate,
                        color = if (isSelected) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Text(text = "♡", color = ActiveTheme.accentDeep, fontSize = 12.sp)
    }
}
