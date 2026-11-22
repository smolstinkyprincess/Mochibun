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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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

// Cloud strip geometry: compact strips (< 200 dp) draw a plump 5-lobe flat-bottomed cloud
// with outward cheeks, while expanded strips (>= 200 dp) draw a 5-puff asymmetrical cloud.
// Headroom reserves space for the peaks plus the up/right back-cloud layer (2 dp compact, 3 dp wide).
private val CLOUD_HEADROOM_COMPACT = 15.dp
private val CLOUD_HEADROOM_WIDE = 20.dp
private val CLOUD_BODY_HEIGHT_ACTIVE = 38.dp
private val CLOUD_BODY_HEIGHT_IDLE = 36.dp
private val CLOUD_WIDE_THRESHOLD = 200.dp
private val CLOUD_BACK_OFFSET_COMPACT = 2.dp
private val CLOUD_BACK_OFFSET_WIDE = 3.dp

// Wide 5-puff asymmetrical cloud: soft outer puffs -> medium-tall flanking puffs -> tall central dome
private val CLOUD_5_WEIGHTS = floatArrayOf(0.14f, 0.21f, 0.30f, 0.20f, 0.15f)
private val CLOUD_5_RISES_DP = floatArrayOf(7.4f, 12.0f, 15.6f, 11.2f, 7.6f)

private val CandidatePillShape = RoundedCornerShape(50)
private val CANDIDATE_CHIP_HEIGHT = 28.dp
private val CANDIDATE_CHIP_CORNER_RADIUS = 14.dp
private val ModeTagShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 8.dp, bottomEnd = 8.dp)

private fun buildCompactCloudPaths(
    width: Float,
    height: Float,
    bodyTop: Float,
    density: Float,
    outline: Path,
    sparkle: Path,
    heart: Path
) {
    val bodyH = (height - bodyTop).coerceAtLeast(1f)
    val cheekW = (width * 0.13f).coerceIn(11f * density, 16f * density).coerceAtMost(width * 0.22f)
    val bottomR = cheekW.coerceAtMost(bodyH * 0.42f)
    val cheekApexY = bodyTop + bodyH * 0.56f
    val cheekValleyY = bodyTop + 3.0f * density
    val innerValleyY = bodyTop + 1.2f * density

    val xStart = cheekW
    val xEnd = (width - cheekW).coerceAtLeast(xStart + 1f)
    val topSpan = xEnd - xStart

    // 3 upper lobes across [xStart, xEnd]: left-upper -> big round center dome -> right-upper
    val v2X = xStart + topSpan * 0.33f
    val v3X = xStart + topSpan * 0.67f
    val p1X = xStart + topSpan * 0.18f
    val p2X = xStart + topSpan * 0.50f
    val p3X = xStart + topSpan * 0.82f

    val p1Y = bodyTop - 4.6f * density
    val p2Y = bodyTop - 11.4f * density
    val p3Y = bodyTop - 4.2f * density

    outline.reset()
    // Start at bottom-left of flat cloud base and trace clockwise around the 5 lobes
    outline.moveTo(bottomR, height)
    // 1. Left outer cheek puff: flat base -> outward bulge at x = 0 -> cheek valley V1
    outline.cubicTo(
        bottomR * 0.28f, height,
        0f, height - (height - cheekApexY) * 0.42f,
        0f, cheekApexY
    )
    outline.cubicTo(
        0f, cheekApexY - (cheekApexY - cheekValleyY) * 0.62f,
        xStart * 0.42f, cheekValleyY,
        xStart, cheekValleyY
    )

    // 2. Left-upper lobe (V1 -> P1 -> V2, ~5.8 dp dip into V2)
    outline.cubicTo(
        xStart + (p1X - xStart) * 0.12f, cheekValleyY - (cheekValleyY - p1Y) * 0.66f,
        p1X - (p1X - xStart) * 0.44f, p1Y,
        p1X, p1Y
    )
    outline.cubicTo(
        p1X + (v2X - p1X) * 0.44f, p1Y,
        v2X - (v2X - p1X) * 0.10f, innerValleyY - (innerValleyY - p1Y) * 0.62f,
        v2X, innerValleyY
    )

    // 3. Big round center dome (V2 -> P2 -> V3)
    outline.cubicTo(
        v2X + (p2X - v2X) * 0.10f, innerValleyY - (innerValleyY - p2Y) * 0.64f,
        p2X - (p2X - v2X) * 0.48f, p2Y,
        p2X, p2Y
    )
    outline.cubicTo(
        p2X + (v3X - p2X) * 0.48f, p2Y,
        v3X - (v3X - p2X) * 0.10f, innerValleyY - (innerValleyY - p2Y) * 0.64f,
        v3X, innerValleyY
    )

    // 4. Right-upper lobe (V3 -> P3 -> V4, ~5.4 dp dip from P3)
    outline.cubicTo(
        v3X + (p3X - v3X) * 0.10f, innerValleyY - (innerValleyY - p3Y) * 0.62f,
        p3X - (p3X - v3X) * 0.44f, p3Y,
        p3X, p3Y
    )
    outline.cubicTo(
        p3X + (xEnd - p3X) * 0.44f, p3Y,
        xEnd - (xEnd - p3X) * 0.12f, cheekValleyY - (cheekValleyY - p3Y) * 0.66f,
        xEnd, cheekValleyY
    )

    // 5. Right outer cheek puff: cheek valley V4 -> outward bulge at x = width -> flat base
    outline.cubicTo(
        width - xStart * 0.42f, cheekValleyY,
        width, cheekApexY - (cheekApexY - cheekValleyY) * 0.62f,
        width, cheekApexY
    )
    outline.cubicTo(
        width, height - (height - cheekApexY) * 0.42f,
        width - bottomR * 0.28f, height,
        width - bottomR, height
    )
    // Soft flat bottom base
    outline.lineTo(bottomR, height)
    outline.close()

    buildSparkle(sparkle, p1X, bodyTop - 0.6f * density, 4.2f * density)
    buildHeart(heart, p3X, bodyTop - 0.4f * density, 4.0f * density)
}

private fun buildWideCloudPaths(
    width: Float,
    height: Float,
    bodyTop: Float,
    density: Float,
    outline: Path,
    sparkle: Path,
    heart: Path
) {
    val capR = ((height - bodyTop) / 2f).coerceAtLeast(1f)
    val span = (width - capR * 2f).coerceAtLeast(1f)
    val inset = (span * 0.04f).coerceIn(2f * density, 6f * density).coerceAtMost(span * 0.2f)
    val puffStart = capR + inset
    val puffEnd = width - capR - inset
    val puffSpan = (puffEnd - puffStart).coerceAtLeast(1f)

    val weights = CLOUD_5_WEIGHTS
    val risesDp = CLOUD_5_RISES_DP
    val puffCount = weights.size

    outline.reset()
    outline.moveTo(capR, bodyTop)
    if (puffStart > capR) {
        outline.lineTo(puffStart, bodyTop)
    }

    var cursorX = puffStart
    var sparkleCenterX = puffStart
    var sparkleRisePx = 0f
    var heartCenterX = puffEnd
    var heartRisePx = 0f

    val sparkleIndex = 1
    val heartIndex = 3
    val valleyRatio = 0.28f
    val crownHandle = 0.46f
    val outerPullX = 0.16f
    val outerPullY = 0.58f
    val innerPullX = 0.22f
    val innerPullY = 0.48f

    for (i in 0 until puffCount) {
        val xL = cursorX
        val puffW = puffSpan * weights[i]
        val xR = if (i == puffCount - 1) puffEnd else xL + puffW
        cursorX = xR

        val risePx = risesDp[i] * density
        val dipL = if (i == 0) 0f else minOf(risesDp[i - 1], risesDp[i]) * density * valleyRatio
        val dipR = if (i == puffCount - 1) 0f else minOf(risesDp[i], risesDp[i + 1]) * density * valleyRatio

        val peakBias = when (i) {
            1 -> 0.52f
            3 -> 0.48f
            else -> 0.50f
        }
        val peakX = xL + puffW * peakBias
        val peakY = bodyTop - risePx
        val yL = bodyTop - dipL
        val yR = bodyTop - dipR

        if (i == sparkleIndex) {
            sparkleCenterX = peakX
            sparkleRisePx = risePx
        }
        if (i == heartIndex) {
            heartCenterX = peakX
            heartRisePx = risePx
        }

        val wL = (peakX - xL).coerceAtLeast(0f)
        val hL = (yL - peakY).coerceAtLeast(0f)
        val startPullX = if (i == 0) outerPullX else innerPullX
        val startPullY = if (i == 0) outerPullY else innerPullY
        outline.cubicTo(
            xL + wL * startPullX, yL - hL * startPullY,
            peakX - wL * crownHandle, peakY,
            peakX, peakY
        )

        val wR = (xR - peakX).coerceAtLeast(0f)
        val hR = (yR - peakY).coerceAtLeast(0f)
        val endPullX = if (i == puffCount - 1) outerPullX else innerPullX
        val endPullY = if (i == puffCount - 1) outerPullY else innerPullY
        outline.cubicTo(
            peakX + wR * crownHandle, peakY,
            xR - wR * endPullX, yR - hR * endPullY,
            xR, yR
        )
    }

    if (width - capR > puffEnd) {
        outline.lineTo(width - capR, bodyTop)
    }
    // Right rounded pill cap (top -> right -> bottom)
    outline.arcTo(Rect(width - capR * 2f, bodyTop, width, height), -90f, 180f, false)
    outline.lineTo(capR, height)
    // Left rounded pill cap (bottom -> left -> top)
    outline.arcTo(Rect(0f, bodyTop, capR * 2f, height), 90f, 180f, false)
    outline.close()

    val sparkleScale = (puffSpan * weights[sparkleIndex] * 0.45f).coerceIn(3.5f * density, 5.4f * density)
    val heartScale = (puffSpan * weights[heartIndex] * 0.42f).coerceIn(3.5f * density, 5.0f * density)
    buildSparkle(sparkle, sparkleCenterX, bodyTop - sparkleRisePx * 0.52f, sparkleScale)
    buildHeart(heart, heartCenterX, bodyTop - heartRisePx * 0.52f, heartScale)
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

    val fill = ActiveTheme.surface.copy(alpha = 0.95f)
    val backCloudFill = ActiveTheme.palette.cloudFront.copy(alpha = 0.78f)
    val outlineColor = ActiveTheme.cardBorder
    // Flat offset copy of the outline stands in for the old CircleShape elevation shadow:
    // elevation outlines must be convex before API 30, and the cloud path isn't.
    val shadowColor = ActiveTheme.cardBorder.copy(alpha = 0.22f)
    val accentColor = ActiveTheme.accentDeep.copy(alpha = 0.55f)
    val heartColor = ActiveTheme.primary.copy(alpha = 0.85f)

    val headroom = if (isActive) CLOUD_HEADROOM_WIDE else CLOUD_HEADROOM_COMPACT
    val bodyHeight = if (isActive) CLOUD_BODY_HEIGHT_ACTIVE else CLOUD_BODY_HEIGHT_IDLE
    val sizing = if (isActive) {
        Modifier
            .wrapContentWidth()
            .widthIn(min = 96.dp, max = 440.dp)
            .height(bodyHeight + headroom)
    } else {
        Modifier
            .wrapContentWidth()
            .widthIn(min = 104.dp)
            .height(bodyHeight + headroom)
    }

    Row(
        modifier = modifier
            .then(sizing)
            .drawWithCache {
                val d = density
                val isWide = size.width >= CLOUD_WIDE_THRESHOLD.value * d
                val backOffset = (if (isWide) CLOUD_BACK_OFFSET_WIDE else CLOUD_BACK_OFFSET_COMPACT).value * d
                val outlineStroke = Stroke(width = 1.5f * d)
                val backDx = backOffset
                val backDy = -backOffset
                val shadowDy = 1.5f * d
                val outlinePath = Path()
                val sparklePath = Path()
                val heartPath = Path()
                val bodyTop = headroom.toPx()
                if (isWide) {
                    buildWideCloudPaths(
                        width = size.width,
                        height = size.height,
                        bodyTop = bodyTop,
                        density = d,
                        outline = outlinePath,
                        sparkle = sparklePath,
                        heart = heartPath
                    )
                } else {
                    buildCompactCloudPaths(
                        width = size.width,
                        height = size.height,
                        bodyTop = bodyTop,
                        density = d,
                        outline = outlinePath,
                        sparkle = sparklePath,
                        heart = heartPath
                    )
                }
                onDrawBehind {
                    translate(left = backDx, top = backDy) { drawPath(outlinePath, backCloudFill) }
                    translate(top = shadowDy) { drawPath(outlinePath, shadowColor) }
                    drawPath(outlinePath, fill)
                    drawPath(outlinePath, outlineColor, style = outlineStroke)
                    drawPath(sparklePath, accentColor)
                    drawPath(heartPath, heartColor)
                }
            }
            .padding(
                top = headroom,
                start = if (isActive) 8.dp else 16.dp,
                end = if (isActive) 12.dp else 16.dp
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
                    val isSingleChar = candidate.length == 1
                    val chipShape = if (isSingleChar) CircleShape else CandidatePillShape
                    val sizeModifier = if (isSingleChar) {
                        Modifier.size(CANDIDATE_CHIP_HEIGHT)
                    } else {
                        Modifier
                            .defaultMinSize(minWidth = CANDIDATE_CHIP_HEIGHT)
                            .height(CANDIDATE_CHIP_HEIGHT)
                    }
                    val chipModifier = if (isSelected) {
                        sizeModifier
                            .graphicsLayer { clip = false }
                            // No scale pop / bring-into-view: the strip repaints per keypress
                            // and already scrolls the selection itself.
                            .softGlowFocus(
                                isSelected = true,
                                cornerRadius = CANDIDATE_CHIP_CORNER_RADIUS,
                                borderColor = ActiveTheme.accentDeep,
                                bringIntoView = false,
                                animateScale = false
                            )
                            .background(ActiveTheme.primary.copy(alpha = 0.35f), chipShape)
                            .let { mod ->
                                if (isSingleChar) mod else mod.padding(horizontal = 8.dp)
                            }
                    } else {
                        sizeModifier
                            .background(ActiveTheme.primary.copy(alpha = 0.12f), chipShape)
                            .border(0.8.dp, ActiveTheme.primary.copy(alpha = 0.30f), chipShape)
                            .let { mod ->
                                if (isSingleChar) mod else mod.padding(horizontal = 6.dp)
                            }
                    }

                    Box(
                        modifier = chipModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (candidate == " ") "␣" else candidate,
                            color = if (isSelected) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
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
