package one.crinklecod.mochibun.ui.dialogs

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.core.keypad.WheelMode
import one.crinklecod.mochibun.core.keypad.isWheelModeToggleKey
import one.crinklecod.mochibun.core.keypad.wheelPetalForDigit
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.ClipRingEntry
import one.crinklecod.mochibun.data.repository.ClipboardRingRepository
import one.crinklecod.mochibun.data.repository.KaomojiRepository
import one.crinklecod.mochibun.ui.components.BottomLavenderClouds
import one.crinklecod.mochibun.ui.components.KeitaiLaceContainer
import one.crinklecod.mochibun.ui.components.curtainUnfurl
import one.crinklecod.mochibun.ui.performBoundaryHaptic
import one.crinklecod.mochibun.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Lightweight alpha glow ramp matching Mochibun's soft pastel focus glow
private val GLOW_EXPANSIONS_DP = floatArrayOf(2.5f, 1.8f, 1.0f, 0.4f)
private val GLOW_ALPHAS = floatArrayOf(0.12f, 0.28f, 0.50f, 0.70f)

private val ThinBorderStroke = Stroke(width = 1.5f)
private val HighlightBorderStroke = Stroke(width = 2.0f)

/**
 * Hoisted wheel selection so the same UI can be driven by the dialog or the global overlay.
 *
 * [clipEntriesProvider] is read when the wheel switches to [WheelMode.CLIPBOARD_SNIPPETS], so the
 * petals show the clipboard as it is at that moment rather than when the wheel opened.
 */
@Stable
class KaomojiWheelState(
    private val clipEntriesProvider: () -> List<ClipRingEntry> = ClipboardRingRepository::ringEntries
) {
    val categories = KaomojiRepository.categories
    var categoryIndex by mutableIntStateOf(0)
    var itemIndex by mutableIntStateOf(0)

    var mode by mutableStateOf(WheelMode.KAOMOJI)
        private set
    var clipEntries by mutableStateOf<List<ClipRingEntry>>(emptyList())
        private set
    var clipIndex by mutableIntStateOf(0)
        private set

    val currentKaomoji: String
        get() {
            val c = categories[categoryIndex]
            return c.items.getOrElse(itemIndex) { c.items.firstOrNull() ?: "" }
        }

    val currentClip: ClipRingEntry?
        get() = clipEntries.getOrNull(clipIndex)

    /** Text the wheel inserts / copies on confirm in the current mode. */
    val currentText: String
        get() = when (mode) {
            WheelMode.KAOMOJI -> currentKaomoji
            WheelMode.CLIPBOARD_SNIPPETS -> currentClip?.text.orEmpty()
        }

    fun toggleMode() {
        mode = mode.toggled()
        if (mode == WheelMode.CLIPBOARD_SNIPPETS) {
            clipEntries = clipEntriesProvider()
            clipIndex = 0
        }
    }

    fun selectCategory(index: Int): Boolean {
        if (index == categoryIndex || index !in categories.indices) return false
        categoryIndex = index
        itemIndex = 0
        return true
    }

    fun stepCategory(delta: Int) {
        categoryIndex = (categoryIndex + delta + categories.size) % categories.size
        itemIndex = 0
    }

    fun stepItem(delta: Int): Boolean {
        val count = categories[categoryIndex].items.size
        if (count == 0) return false
        itemIndex = (itemIndex + delta + count) % count
        return true
    }

    fun selectClip(index: Int): Boolean {
        if (index !in clipEntries.indices) return false
        clipIndex = index
        return true
    }

    fun stepClip(delta: Int): Boolean {
        val count = clipEntries.size
        if (count <= 1) return false
        clipIndex = (clipIndex + delta + count) % count
        return true
    }
}

/**
 * The IME's Kaomoji / Snippet Wheel, staged "rising out of the cloud shelf" like
 * FavoriteFolderDialog. Z-order, back to front:
 *   1. flat dim scrim + backdrop sparkles (never blur: PowerVR GE8300); tapping it dismisses
 *   2. wheel card anchored low, crowned with the ribbon bow; its bottom rim tucks behind the clouds
 *   3. raised BottomLavenderClouds shelf (+ solid base strip to the screen edge)
 * No close pill: BACK / F4 / `#` (or a tap on the scrim) dismiss it.
 *
 * Hosted only by MochibunImeService (`#` hold while typing), which owns the keypad routing and
 * makes its window touchable while this is shown.
 */
@Composable
fun KaomojiWheelCloudStage(
    state: KaomojiWheelState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmVerb: String = "Insert"
) {
    val context = LocalContext.current
    val dismissWithSound = {
        SoundEffectManager.play(context, SoundEffect.BACK)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(onClick = dismissWithSound)
    ) {
        FloatingPixelBackdrop()

        // Taps inside the card must not reach the dismissing scrim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WHEEL_CARD_BOTTOM_INSET)
                .graphicsLayer { clip = false }
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            KaomojiWheelCard(
                state = state,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                confirmVerb = confirmVerb,
                showHeaderClose = false,
                bottomContentInset = WHEEL_CARD_INNER_BOTTOM
            )
            // Pixel ribbon bow straddling the top lace rim. KaomojiWheelCard keeps 8dp of outer
            // padding above the lace, hence -4dp rather than FavoriteFolderDialog's -12dp.
            PixelRibbonBow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-4).dp)
                    .graphicsLayer { clip = false },
                width = 34.dp,
                height = 28.dp
            )
        }

        // Cloud shelf over the card's lower rim. The cloud paths are anchored to their box
        // bottom, so the shelf is lifted with an offset rather than made taller.
        BottomLavenderClouds(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -WHEEL_CLOUD_SHELF_LIFT)
        )
        // Solid base under the lifted shelf: fills the gap to the screen edge and hides the
        // cloud path's closing outline along its (now visible) bottom edge.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WHEEL_CLOUD_SHELF_LIFT + 1.dp)
                .background(ActiveTheme.palette.cloudFront)
        )
    }
}

// Cloud-shelf geometry (dp above the screen's bottom edge), matching FavoriteFolderDialog for
// 480 × 800: the shelf's back puffs cover the lace card's bottom rim (35dp up: the inset plus the
// card's own 8dp outer padding), and WHEEL_CARD_INNER_BOTTOM lifts the hint pill to ~80dp, clear
// of the clouds. Clip mode is the tallest layout (~410dp + bow) and still fits under the status bar.
private val WHEEL_CLOUD_SHELF_LIFT = 10.dp
private val WHEEL_CARD_BOTTOM_INSET = 27.dp
private val WHEEL_CARD_INNER_BOTTOM = 16.dp

/**
 * Shared D-pad / digit navigation. Returns true if the key was handled.
 *
 * `*` / `0` switch mode. Kaomoji: ◀▶ mood, ▲▼ kaomoji, `1`–`6` jump to a mood.
 * Clips & Snippets: any D-pad direction steps petals, `1`–`8` picks a petal and confirms it
 * at once via [onConfirm].
 */
fun handleKaomojiWheelNavKey(
    state: KaomojiWheelState,
    keyCode: Int,
    context: Context,
    onConfirm: () -> Unit = {}
): Boolean {
    if (isWheelModeToggleKey(keyCode)) {
        state.toggleMode()
        SoundEffectManager.play(context, SoundEffect.NAV)
        return true
    }
    if (state.mode == WheelMode.CLIPBOARD_SNIPPETS) {
        val petal = wheelPetalForDigit(keyCode)
        if (petal != null) {
            if (state.selectClip(petal)) {
                onConfirm()
            } else {
                performBoundaryHaptic(context)
            }
            return true
        }
        val moved = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> state.stepClip(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> state.stepClip(1)
            else -> return false
        }
        if (moved) SoundEffectManager.play(context, SoundEffect.NAV)
        return true
    }
    val moved = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { state.stepCategory(-1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { state.stepCategory(1); true }
        KeyEvent.KEYCODE_DPAD_UP -> state.stepItem(-1)
        KeyEvent.KEYCODE_DPAD_DOWN -> state.stepItem(1)
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_6 -> state.selectCategory(keyCode - KeyEvent.KEYCODE_1)
        in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_6 -> state.selectCategory(keyCode - KeyEvent.KEYCODE_NUMPAD_1)
        else -> return false
    }
    if (moved) SoundEffectManager.play(context, SoundEffect.NAV)
    return true
}

/** Single-line petal label: whitespace runs collapsed, cut to a few characters. */
internal fun clipPetalLabel(entry: ClipRingEntry, maxChars: Int = 7): String {
    val flat = entry.text.replace(WHITESPACE_RUN, " ")
    val cut = if (flat.length > maxChars) flat.take(maxChars - 1).trimEnd() + "…" else flat
    return if (entry.pinned) "★$cut" else cut
}

private val WHITESPACE_RUN = Regex("\\s+")

@Composable
fun KaomojiWheelCard(
    state: KaomojiWheelState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** "Copy" in the launcher dialog, "Insert" in the IME overlay. */
    confirmVerb: String = "Copy",
    /** Header "♡ [#]" dismiss badge; the cloud stage hides it (BACK / F4 / `#` dismiss). */
    showHeaderClose: Boolean = true,
    /** Extra space under the hint pill so it clears a cloud shelf drawn over the card's rim. */
    bottomContentInset: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val theme = LocalLauncherTheme.current
    val categories = state.categories
    val isClipMode = state.mode == WheelMode.CLIPBOARD_SNIPPETS
    val clipEntries = state.clipEntries
    val triggerBadge = PreferencesManager.getKaomojiTriggerBadge()

    val currentCategory = categories[state.categoryIndex]
    val currentKaomoji = state.currentKaomoji
    val currentClip = state.currentClip

    // Petals: mood categories, or one per clip. An empty clip ring still draws 8 blank petals
    // (nothing selected) so the wheel keeps its shape.
    val petalCount = when {
        !isClipMode -> categories.size
        clipEntries.isEmpty() -> ClipboardRingRepository.MAX_RING_PETALS
        else -> clipEntries.size
    }
    val selectedPetal = when {
        !isClipMode -> state.categoryIndex
        clipEntries.isEmpty() -> -1
        else -> state.clipIndex
    }

    // High-contrast theme & translucent surface tokens
    val plumOutline = theme.greetingOutlineColor
    val cardBackground = theme.cardBgColor.copy(alpha = 0.82f)
    val glowColor = theme.selectionGlowColor
    val surfaceTint = theme.surfaceTint

    val deepPlumText = Color(0xFF4A2E56)
    val selectedVioletText = Color(0xFF3A1C44)
    val unselectedGlyphColor = Color(0xFF6E4A74)
    val selectedGlyphColor = Color(0xFF5A2248)
    val activeCategoryRose = Color(0xFF8A2E54)
    val activeKaomojiPlum = Color(0xFF34153B)
    val stepperPlum = Color(0xFF5A335E)
    val helperRose = Color(0xFF7D2C4D)

    // Snug geometric dimensions optimized for 480x800 portrait display
    val wheelDiameter = 208.dp
    val rOuter = 100.dp
    val rInner = 56.dp
    val strokeWidth = (rOuter - rInner)
    val rMid = (rInner + rOuter) / 2f

    fun selectPetal(index: Int) {
        if (isClipMode) {
            // Touch: tap a clip to pick it, tap the picked one again to use it
            if (index == state.clipIndex && currentClip != null) onConfirm()
            else if (state.selectClip(index)) SoundEffectManager.play(context, SoundEffect.NAV)
        } else if (state.selectCategory(index)) {
            SoundEffectManager.play(context, SoundEffect.NAV)
        }
    }

    fun toggleMode() {
        state.toggleMode()
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    KeitaiLaceContainer(
        cardCornerRadius = 24.dp,
        containerColor = cardBackground,
        borderColor = plumOutline.copy(alpha = 0.75f),
        modifier = Modifier
            .width(268.dp)
            .wrapContentSize()
            .curtainUnfurl(visible = true)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header in signature Mochibun style; tapping the title flips the mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (showHeaderClose) Arrangement.SpaceBetween else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isClipMode) "— ✂ Clips & Snippets —" else "— ♡ Kaomoji ♡ —",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = plumOutline,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { toggleMode() }
                        .padding(horizontal = 2.dp)
                )
                if (showHeaderClose) Text(
                    text = "♡ [$triggerBadge]",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = plumOutline.copy(alpha = 0.9f),
                    modifier = Modifier
                        .clickable {
                            SoundEffectManager.play(context, SoundEffect.BACK)
                            onDismiss()
                        }
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Radial Petal Wheel
            val bodyStroke = remember(density) { Stroke(width = strokeWidth.value * density) }
            val glowStrokes = remember(density) {
                Array(4) { g ->
                    Stroke(width = (strokeWidth.value + GLOW_EXPANSIONS_DP[g] * 2f) * density)
                }
            }
            Box(
                modifier = Modifier
                    .size(wheelDiameter)
                    .graphicsLayer { clip = false },
                contentAlignment = Alignment.Center
            ) {
                // Canvas Petals Drawing Pass
                Canvas(
                    modifier = Modifier
                        .size(wheelDiameter)
                        .graphicsLayer { clip = false }
                ) {
                    val centerPx = Offset(size.width / 2f, size.height / 2f)
                    val rInnerPx = rInner.toPx()
                    val rOuterPx = rOuter.toPx()
                    val rMidPx = rMid.toPx()

                    val gapAngle = 6f
                    val sweepAngle = (360f / petalCount) - gapAngle

                    for (i in 0 until petalCount) {
                        val centerAngle = -90f + i * (360f / petalCount)
                        val startAngle = centerAngle - sweepAngle / 2f
                        val isSelected = (i == selectedPetal)

                        if (isSelected) {
                            // Concentric glow passes matching softGlowFocus
                            for (g in 0 until 4) {
                                val expansion = GLOW_EXPANSIONS_DP[g] * density
                                drawArc(
                                    color = glowColor.copy(alpha = GLOW_ALPHAS[g]),
                                    startAngle = startAngle - 1f,
                                    sweepAngle = sweepAngle + 2f,
                                    useCenter = false,
                                    topLeft = Offset(centerPx.x - rMidPx - expansion, centerPx.y - rMidPx - expansion),
                                    size = Size((rMidPx + expansion) * 2f, (rMidPx + expansion) * 2f),
                                    style = glowStrokes[g]
                                )
                            }

                            // Selected petal body fill
                            drawArc(
                                color = glowColor.copy(alpha = 0.55f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(centerPx.x - rMidPx, centerPx.y - rMidPx),
                                size = Size(rMidPx * 2f, rMidPx * 2f),
                                style = bodyStroke
                            )

                            // Soft outline
                            drawArc(
                                color = plumOutline,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(centerPx.x - rOuterPx, centerPx.y - rOuterPx),
                                size = Size(rOuterPx * 2f, rOuterPx * 2f),
                                style = HighlightBorderStroke
                            )
                            drawArc(
                                color = plumOutline,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(centerPx.x - rInnerPx, centerPx.y - rInnerPx),
                                size = Size(rInnerPx * 2f, rInnerPx * 2f),
                                style = HighlightBorderStroke
                            )
                        } else {
                            // Unselected translucent petal fill
                            drawArc(
                                color = surfaceTint.copy(alpha = 0.38f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(centerPx.x - rMidPx, centerPx.y - rMidPx),
                                size = Size(rMidPx * 2f, rMidPx * 2f),
                                style = bodyStroke
                            )

                            // Unselected subtle border
                            drawArc(
                                color = plumOutline.copy(alpha = 0.30f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(centerPx.x - rOuterPx, centerPx.y - rOuterPx),
                                size = Size(rOuterPx * 2f, rOuterPx * 2f),
                                style = ThinBorderStroke
                            )
                            drawArc(
                                color = plumOutline.copy(alpha = 0.30f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(centerPx.x - rInnerPx, centerPx.y - rInnerPx),
                                size = Size(rInnerPx * 2f, rInnerPx * 2f),
                                style = ThinBorderStroke
                            )
                        }
                    }
                }

                // Petal Mini Labels
                val labelCount = if (isClipMode) clipEntries.size else categories.size
                for (i in 0 until labelCount) {
                    val centerAngle = -90.0 + i * (360.0 / petalCount)
                    val angleRad = Math.toRadians(centerAngle)
                    val xOffset = (rMid.value * cos(angleRad)).dp
                    val yOffset = (rMid.value * sin(angleRad)).dp
                    val isSelected = (i == selectedPetal)
                    val title: String
                    val subtitle: String
                    if (isClipMode) {
                        title = clipPetalLabel(clipEntries[i])
                        subtitle = "${i + 1}"
                    } else {
                        title = categories[i].shortLabel
                        subtitle = categories[i].icon
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = xOffset, y = yOffset)
                            .widthIn(max = 52.dp)
                            .clickable { selectPetal(i) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            fontSize = if (isSelected) 10.sp else 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) selectedVioletText else deepPlumText,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                        Text(
                            text = subtitle,
                            fontSize = if (isSelected) 8.5.sp else 8.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            color = if (isSelected) selectedGlyphColor else unselectedGlyphColor
                        )
                    }
                }

                // Cozy Center Preview Hub
                val hubDiameter = (rInner * 2f) - 6.dp
                Box(
                    modifier = Modifier
                        .size(hubDiameter)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(cardBackground)
                        .border(1.8.dp, plumOutline.copy(alpha = 0.85f), CircleShape)
                        .clickable { onConfirm() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val hubTitle: String
                        val hubBody: String
                        val hubCounter: String
                        when {
                            !isClipMode -> {
                                hubTitle = "♥ ${currentCategory.shortLabel} ♥"
                                hubBody = currentKaomoji
                                hubCounter = "♡ ${state.itemIndex + 1} / ${currentCategory.items.size} ♡"
                            }
                            currentClip == null -> {
                                hubTitle = "✂ Empty ✂"
                                hubBody = "no clips yet"
                                hubCounter = "♡ 0 / 0 ♡"
                            }
                            else -> {
                                hubTitle = if (currentClip.pinned) "★ Snippet ★" else "✂ Clip ✂"
                                hubBody = clipPetalLabel(currentClip.copy(pinned = false), maxChars = 10)
                                hubCounter = "♡ ${state.clipIndex + 1} / ${clipEntries.size} ♡"
                            }
                        }
                        Text(
                            text = hubTitle,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryRose,
                            maxLines = 1
                        )

                        Text(
                            text = hubBody,
                            fontSize = if (isClipMode) 11.sp else 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeKaomojiPlum,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )

                        Text(
                            text = hubCounter,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepperPlum
                        )

                        Text(
                            text = "♥ Center to $confirmVerb ♥",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = helperRose,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }

            // Full text of the picked clip: the hub only has room for a few characters
            if (isClipMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBackground)
                        .border(1.dp, plumOutline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = currentClip?.text
                            ?: "Copy some text, or add Quick Snippets in Settings → Typing ♡",
                        fontSize = 10.sp,
                        fontWeight = if (currentClip != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (currentClip != null) activeKaomojiPlum else deepPlumText,
                        textAlign = if (currentClip != null) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom D-Pad instruction pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaceTint.copy(alpha = 0.55f))
                    .border(1.dp, plumOutline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isClipMode) {
                        "◀▶▲▼ Pick  •  1–8 $confirmVerb\n✱/0 Kaomoji  •  Center: $confirmVerb"
                    } else {
                        "◀▶ Mood  •  ▲▼ Kaomoji\n✱/0 Clips  •  Center: $confirmVerb"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = deepPlumText,
                    textAlign = TextAlign.Center
                )
            }
            if (bottomContentInset > 0.dp) Spacer(modifier = Modifier.height(bottomContentInset))
        }
    }
}
