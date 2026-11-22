package one.crinklecod.mochibun.ui.components

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.ui.theme.ActiveTheme

/** Multi-tap pools for the keypad. */
object KeitaiCandidatePools {
    val POOL_2 = arrayOf("a", "b", "c", "A", "B", "C", "2")
    val POOL_3 = arrayOf("d", "e", "f", "D", "E", "F", "3")
    val POOL_4 = arrayOf("g", "h", "i", "G", "H", "I", "4")
    val POOL_5 = arrayOf("j", "k", "l", "J", "K", "L", "5")
    val POOL_6 = arrayOf("m", "n", "o", "M", "N", "O", "6")
    val POOL_7 = arrayOf("p", "q", "r", "s", "P", "Q", "R", "S", "7")
    val POOL_8 = arrayOf("t", "u", "v", "T", "U", "V", "8")
    val POOL_9 = arrayOf("w", "x", "y", "z", "W", "X", "Y", "Z", "9")
    val POOL_1 = arrayOf(
        ".", ",", "?", "!", "-", "'", "\"", ":", ";",
        "♡", "★", "♥", "♪"
    )
    val POOL_0 = arrayOf(
        " ", "0",
        "/", "\\", "_", "@", "#", "$", "%", "^", "&", "*", "+", "=", "~",
        "|", "<", ">",
        "(", ")", "[", "]", "{", "}", "`"
    )

    // Backward compatibility for any external references
    val POOL_SYMBOLS = POOL_1

    fun forKey(keyCode: Int): Array<String>? = when (keyCode) {
        KeyEvent.KEYCODE_1 -> POOL_1
        KeyEvent.KEYCODE_2 -> POOL_2
        KeyEvent.KEYCODE_3 -> POOL_3
        KeyEvent.KEYCODE_4 -> POOL_4
        KeyEvent.KEYCODE_5 -> POOL_5
        KeyEvent.KEYCODE_6 -> POOL_6
        KeyEvent.KEYCODE_7 -> POOL_7
        KeyEvent.KEYCODE_8 -> POOL_8
        KeyEvent.KEYCODE_9 -> POOL_9
        KeyEvent.KEYCODE_0 -> POOL_0
        KeyEvent.KEYCODE_STAR -> POOL_1
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

    /** Replaces the strip candidates with a predictive list (e.g. T9 words); empty resets. */
    @JvmName("applyCandidates")
    fun setCandidates(words: Array<String>) {
        if (words.isEmpty()) {
            reset()
            return
        }
        candidates = words
        selectedIndex = 0
        isActive = true
        lastKeyCode = KeyEvent.KEYCODE_UNKNOWN
        lastPressTime = 0L
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
        if (keyCode == KeyEvent.KEYCODE_POUND) {
            if (isDown && repeatCount == 0 && isActive) {
                commitActive(onCommit)
            }
            return false
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

/** Limits the text while preserving valid selection and IME composition ranges. */
fun TextFieldValue.limitTextLength(maxLength: Int): TextFieldValue {
    val limitedText = text.take(maxLength)
    fun clamp(range: TextRange) = TextRange(
        range.start.coerceIn(0, limitedText.length),
        range.end.coerceIn(0, limitedText.length)
    )
    val limitedComposition = composition?.takeIf {
        it.min >= 0 && it.max <= limitedText.length
    }
    return TextFieldValue(limitedText, clamp(selection), limitedComposition)
}

// Cloud strip geometry: puffs rise CLOUD_PUFF_DEPTH above the rounded body, so the strip is that
// much taller than the old 38/36dp pill while its text row keeps the same body height.
private val CLOUD_PUFF_DEPTH = 8.dp
private val CLOUD_BODY_HEIGHT_ACTIVE = 38.dp
private val CLOUD_BODY_HEIGHT_IDLE = 36.dp
/** Target width of one scallop along the top edge; the count adapts to the strip's width. */
private val CLOUD_PUFF_WIDTH = 22.dp
private val CandidatePillShape = RoundedCornerShape(9.dp)
private val ModeTagShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 8.dp, bottomEnd = 8.dp)

/**
 * Cached outline + accent paths for the cloud strip. [update] rebuilds them only when the strip's
 * pixel size changes (the strip grows/shrinks as the candidate pool changes), so draw passes,
 * including every frame of the idle fade, allocate nothing.
 */
private class CloudStripPaths {
    val outline = Path()
    val sparkle = Path()
    val heart = Path()
    private var builtWidth = -1f
    private var builtHeight = -1f

    fun update(width: Float, height: Float, density: Float) {
        if (width == builtWidth && height == builtHeight) return
        builtWidth = width
        builtHeight = height

        val puff = CLOUD_PUFF_DEPTH.value * density
        val bodyTop = puff
        val capR = ((height - bodyTop) / 2f).coerceAtLeast(1f)
        val span = (width - capR * 2f).coerceAtLeast(0f)
        val count = (span / (CLOUD_PUFF_WIDTH.value * density)).toInt().coerceAtLeast(1)
        val step = span / count

        outline.reset()
        outline.moveTo(capR, height)
        outline.lineTo(width - capR, height)
        // Right end cap: bottom -> right -> top of the body
        outline.arcTo(Rect(width - capR * 2f, bodyTop, width, height), 90f, -180f, false)
        // Scallops along the top, right to left. Heights alternate for an organic silhouette;
        // the two outermost puffs stay tall since they carry the accents.
        for (i in 0 until count) {
            val right = width - capR - i * step
            val left = right - step
            val tall = i == 0 || i == count - 1 || i % 2 == 0
            val rise = if (tall) puff else puff * 0.62f
            outline.arcTo(Rect(left, bodyTop - rise, right, bodyTop + rise), 0f, -180f, false)
        }
        // Left end cap: top -> left -> bottom
        outline.arcTo(Rect(0f, bodyTop, capR * 2f, height), 270f, -180f, false)
        outline.close()

        // ✦ in the leftmost puff, ♡ in the rightmost, both sitting in the puff above the body line
        val accentY = bodyTop - puff * 0.38f
        buildSparkle(sparkle, capR + step / 2f, accentY, 5.5f * density)
        buildHeart(heart, width - capR - step / 2f, accentY, 5f * density)
    }

    private fun buildSparkle(path: Path, cx: Float, cy: Float, s: Float) {
        val h = s / 2f
        path.reset()
        path.moveTo(cx, cy - h)
        path.quadraticTo(cx, cy, cx + h, cy)
        path.quadraticTo(cx, cy, cx, cy + h)
        path.quadraticTo(cx, cy, cx - h, cy)
        path.quadraticTo(cx, cy, cx, cy - h)
        path.close()
    }

    private fun buildHeart(path: Path, cx: Float, cy: Float, s: Float) {
        val h = s / 2f
        path.reset()
        path.moveTo(cx, cy + h)
        path.cubicTo(cx - s * 0.9f, cy, cx - h, cy - h * 1.15f, cx, cy - h * 0.35f)
        path.cubicTo(cx + h, cy - h * 1.15f, cx + s * 0.9f, cy, cx, cy + h)
        path.close()
    }
}

@Composable
fun KeitaiCandidateStrip(
    state: KeitaiCandidateState,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null
) {
    val listState = rememberLazyListState()
    val candidates = state.candidates
    val selected = state.selectedIndex
    val isActive = state.isActive

    // Instantly keep the selected candidate in view without measure/onPlaced overhead
    LaunchedEffect(candidates, selected) {
        if (candidates.isNotEmpty() && selected in candidates.indices) {
            listState.scrollToItem(selected)
        }
    }

    val cloudPaths = remember { CloudStripPaths() }
    val density = LocalDensity.current.density
    val outlineStroke = remember(density) { Stroke(width = 1.5f * density) }
    val fill = ActiveTheme.surface.copy(alpha = 0.95f)
    val outlineColor = ActiveTheme.cardBorder
    // Flat offset copy of the outline stands in for the old CircleShape elevation shadow:
    // elevation outlines must be convex before API 30, and the scalloped path isn't.
    val shadowColor = ActiveTheme.cardBorder.copy(alpha = 0.22f)
    val accentColor = ActiveTheme.accentDeep.copy(alpha = 0.55f)
    val heartColor = ActiveTheme.primary.copy(alpha = 0.85f)

    val bodyHeight = if (isActive) CLOUD_BODY_HEIGHT_ACTIVE else CLOUD_BODY_HEIGHT_IDLE
    val sizing = if (isActive) {
        Modifier
            .wrapContentWidth()
            .widthIn(max = 440.dp)
            .height(bodyHeight + CLOUD_PUFF_DEPTH)
    } else {
        Modifier
            .wrapContentWidth()
            .height(bodyHeight + CLOUD_PUFF_DEPTH)
    }

    Row(
        modifier = modifier
            .then(sizing)
            .drawBehind {
                cloudPaths.update(size.width, size.height, density)
                translate(top = 1.5f * density) { drawPath(cloudPaths.outline, shadowColor) }
                drawPath(cloudPaths.outline, fill)
                drawPath(cloudPaths.outline, outlineColor, style = outlineStroke)
                drawPath(cloudPaths.sparkle, accentColor)
                drawPath(cloudPaths.heart, heartColor)
            }
            .padding(
                top = CLOUD_PUFF_DEPTH,
                start = if (isActive) 8.dp else 12.dp,
                end = 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingLabel != null) {
            ModeTag(leadingLabel)
        } else {
            Text(text = "♡", color = ActiveTheme.accentDeep, fontSize = 12.sp)
        }

        if (isActive) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .wrapContentWidth()
                    .graphicsLayer { clip = false }
                    .padding(vertical = 3.dp),
                // Room for the selected pill's halo and corner sparkles at the row's ends
                contentPadding = PaddingValues(horizontal = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(candidates) { index, candidate ->
                    val isSelected = index == selected
                    val chipModifier = if (isSelected) {
                        Modifier
                            .graphicsLayer { clip = false }
                            // No scale pop / bring-into-view: the strip repaints per keypress
                            // and already scrolls the selection itself.
                            .softGlowFocus(
                                isSelected = true,
                                cornerRadius = 9.dp,
                                borderColor = ActiveTheme.accentDeep,
                                bringIntoView = false,
                                animateScale = false
                            )
                            .background(ActiveTheme.primary.copy(alpha = 0.35f), CandidatePillShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    } else {
                        Modifier
                            .background(ActiveTheme.primary.copy(alpha = 0.12f), CandidatePillShape)
                            .border(0.8.dp, ActiveTheme.primary.copy(alpha = 0.30f), CandidatePillShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    }

                    Box(
                        modifier = chipModifier.defaultMinSize(minWidth = 24.dp),
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
        } else if (leadingLabel != null) {
            Text(
                text = "♡",
                color = ActiveTheme.accentDeep,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** Input-mode badge (`abc` / `ABC` / `123`) styled as a little pastel luggage tag. */
@Composable
private fun ModeTag(label: String) {
    Row(
        modifier = Modifier
            .background(ActiveTheme.primary.copy(alpha = 0.24f), ModeTagShape)
            .border(1.dp, ActiveTheme.primary.copy(alpha = 0.50f), ModeTagShape)
            .padding(start = 4.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tag eyelet
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(ActiveTheme.surface, CircleShape)
                .border(0.8.dp, ActiveTheme.accentDeep.copy(alpha = 0.55f), CircleShape)
        )
        Text(
            text = label,
            color = ActiveTheme.accentDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 3.dp)
        )
    }
}
