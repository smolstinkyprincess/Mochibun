package com.crinklecodone.mochibun.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.KaomojiRepository
import com.crinklecodone.mochibun.ui.components.curtainUnfurl
import com.crinklecodone.mochibun.ui.performBoundaryHaptic
import com.crinklecodone.mochibun.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// Lightweight alpha glow ramp matching Mochibun's soft pastel focus glow
private val GLOW_EXPANSIONS_DP = floatArrayOf(2.5f, 1.8f, 1.0f, 0.4f)
private val GLOW_ALPHAS = floatArrayOf(0.12f, 0.28f, 0.50f, 0.70f)

private val ThinBorderStroke = Stroke(width = 1.5f)
private val HighlightBorderStroke = Stroke(width = 2.0f)

/** Hoisted wheel selection so the same UI can be driven by the dialog or the global overlay. */
@Stable
class KaomojiWheelState {
    val categories = KaomojiRepository.categories
    var categoryIndex by mutableIntStateOf(0)
    var itemIndex by mutableIntStateOf(0)

    val currentKaomoji: String
        get() {
            val c = categories[categoryIndex]
            return c.items.getOrElse(itemIndex) { c.items.firstOrNull() ?: "" }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaomojiWheelDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val state = remember { KaomojiWheelState() }
    var sawCenterDown by remember { mutableStateOf(false) }
    var sawTriggerDown by remember { mutableStateOf(false) }

    requestFocusSafely(focusRequester)

    fun copyAndDismiss() {
        val kaomoji = state.currentKaomoji
        if (kaomoji.isNotBlank()) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Kaomoji", kaomoji)
                clipboard.setPrimaryClip(clip)
            } catch (_: Exception) {}

            performBoundaryHaptic(context)
            SoundEffectManager.play(context, SoundEffect.SELECT)
            Toast.makeText(context, "♡ Copied $kaomoji ♡", Toast.LENGTH_SHORT).show()
        }
        onDismiss()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent

                if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_F4) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (PreferencesManager.matchesKaomojiTrigger(native.keyCode, native.scanCode, context)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawTriggerDown = true
                    } else if (event.type == KeyEventType.KeyUp) {
                        if (sawTriggerDown) {
                            sawTriggerDown = false
                            SoundEffectManager.play(context, SoundEffect.BACK)
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        copyAndDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                handleKaomojiWheelNavKey(state, native.keyCode, context)
            }
    ) {
        KaomojiWheelCard(state = state, onConfirm = ::copyAndDismiss, onDismiss = onDismiss)
    }
}

/** Shared D-pad / digit navigation. Returns true if the key was handled. */
fun handleKaomojiWheelNavKey(state: KaomojiWheelState, keyCode: Int, context: Context): Boolean {
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

@Composable
fun KaomojiWheelCard(
    state: KaomojiWheelState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    hintText: String = "◀▶ Mood  •  ▲▼ Kaomoji  •  Center: Copy"
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val theme = LocalLauncherTheme.current
    val categories = state.categories
    val selectedCategoryIndex = state.categoryIndex
    val selectedItemIndex = state.itemIndex
    val triggerBadge = PreferencesManager.getKaomojiTriggerBadge()

    val currentCategory = categories[selectedCategoryIndex]
    val currentKaomoji = state.currentKaomoji

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

    fun selectCategory(index: Int) {
        if (state.selectCategory(index)) SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun copyAndDismiss() = onConfirm()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = BorderStroke(1.5.dp, plumOutline.copy(alpha = 0.75f)),
        modifier = Modifier
            .width(260.dp)
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
            // Header in signature Mochibun style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "— ♡ Kaomoji ♡ —",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = plumOutline
                )
                Text(
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
                    val strokePx = strokeWidth.toPx()

                    val gapAngle = 6f
                    val sweepAngle = (360f / categories.size) - gapAngle

                    for (i in categories.indices) {
                        val centerAngle = -90f + i * (360f / categories.size)
                        val startAngle = centerAngle - sweepAngle / 2f
                        val isSelected = (i == selectedCategoryIndex)

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
                                    style = Stroke(width = strokePx + expansion * 2f)
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
                                style = Stroke(width = strokePx)
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
                                style = Stroke(width = strokePx)
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
                for (i in categories.indices) {
                    val category = categories[i]
                    val centerAngle = -90.0 + i * (360.0 / categories.size)
                    val angleRad = Math.toRadians(centerAngle)
                    val xOffset = (rMid.value * cos(angleRad)).dp
                    val yOffset = (rMid.value * sin(angleRad)).dp
                    val isSelected = (i == selectedCategoryIndex)

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = xOffset, y = yOffset)
                            .clickable { selectCategory(i) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = category.shortLabel,
                            fontSize = if (isSelected) 10.sp else 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) selectedVioletText else deepPlumText
                        )
                        Text(
                            text = category.icon,
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
                        .clickable { copyAndDismiss() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "♥ ${currentCategory.shortLabel} ♥",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryRose,
                            maxLines = 1
                        )

                        Text(
                            text = currentKaomoji,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeKaomojiPlum,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )

                        Text(
                            text = "♡ ${selectedItemIndex + 1} / ${currentCategory.items.size} ♡",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = stepperPlum
                        )

                        Text(
                            text = "♥ Center to Copy ♥",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = helperRose,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
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
                    text = hintText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = deepPlumText
                )
            }
        }
    }
}
