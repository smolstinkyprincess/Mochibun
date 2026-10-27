package com.crinklecodone.mochibun.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.cardBorderColor
import com.crinklecodone.mochibun.ui.theme.focusedContainerColor
import com.crinklecodone.mochibun.ui.theme.palette
import com.crinklecodone.mochibun.ui.theme.selectionGlowColor
import com.crinklecodone.mochibun.ui.theme.surfaceTint
import com.crinklecodone.mochibun.ui.theme.unfocusedCardBgColor
import com.crinklecodone.mochibun.ui.theme.unfocusedCardBorderColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Types of delicate kawaii accent motifs for Mochibun UI card boundaries.
 * Strictly uses procedural Canvas vector paths (zero standard platform Unicode emojis)
 * to maintain 1:1 pixel crispness on the Chinoe AT-M120 (480x800) and avoid GPU shader overhead.
 */
enum class KawaiiMotifType {
    /** Four-pointed sparkle star (✦ filled). */
    SPARKLE_FOUR_POINT_FILLED,

    /** Four-pointed sparkle star (✧ hollow outline). */
    SPARKLE_FOUR_POINT_HOLLOW,

    /** Tiny cross-glint / diamond sparkle (🞢 with fine horizontal & vertical rays). */
    DIAMOND_GLINT,

    /** Soft miniature outline heart (♡). */
    MINI_HEART_OUTLINE,

    /** Soft miniature filled heart (♥). */
    MINI_HEART_FILLED,

    /** Tiny filled pastel starlet (★). */
    STARLET_FILLED,

    /** Tiny hollow pastel starlet (☆). */
    STARLET_HOLLOW
}

/**
 * Preset anchor positions along card boundaries.
 */
enum class CardMotifAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    HEADER_LEFT,
    HEADER_RIGHT,
    CUSTOM
}

/**
 * Specification for a single boundary decorative motif.
 *
 * @property type Motif geometry type (✦, ✧, cross-glint, ♡, ★, ☆).
 * @property anchor Anchor position along card perimeter.
 * @property sizeDp Size in Dp (usually 7.dp - 12.dp for delicate non-intrusive accents).
 * @property offsetXDp Fine horizontal adjustment in Dp.
 * @property offsetYDp Fine vertical adjustment in Dp.
 * @property alpha Soft opacity factor (0.35f to 0.75f for luminous, dreamy look).
 */
@Immutable
data class CardMotifSpec(
    val type: KawaiiMotifType,
    val anchor: CardMotifAnchor,
    val sizeDp: Dp = 10.dp,
    val offsetXDp: Dp = 0.dp,
    val offsetYDp: Dp = 0.dp,
    val alpha: Float = 0.55f
)

/**
 * Default balanced motif layout: 2–4 subtle motifs tucked organically near corners.
 */
fun defaultCardMotifs(): List<CardMotifSpec> = listOf(
    CardMotifSpec(
        type = KawaiiMotifType.SPARKLE_FOUR_POINT_FILLED,
        anchor = CardMotifAnchor.TOP_LEFT,
        sizeDp = 10.dp,
        offsetXDp = 4.dp,
        offsetYDp = (-3).dp,
        alpha = 0.65f
    ),
    CardMotifSpec(
        type = KawaiiMotifType.STARLET_HOLLOW,
        anchor = CardMotifAnchor.TOP_RIGHT,
        sizeDp = 9.dp,
        offsetXDp = (-4).dp,
        offsetYDp = (-2).dp,
        alpha = 0.50f
    ),
    CardMotifSpec(
        type = KawaiiMotifType.MINI_HEART_OUTLINE,
        anchor = CardMotifAnchor.BOTTOM_LEFT,
        sizeDp = 9.dp,
        offsetXDp = 4.dp,
        offsetYDp = 2.dp,
        alpha = 0.55f
    ),
    CardMotifSpec(
        type = KawaiiMotifType.SPARKLE_FOUR_POINT_HOLLOW,
        anchor = CardMotifAnchor.BOTTOM_RIGHT,
        sizeDp = 8.dp,
        offsetXDp = (-3).dp,
        offsetYDp = 2.dp,
        alpha = 0.50f
    )
)

/**
 * Compact 2-motif layout for narrower or shorter cards.
 */
fun compactCardMotifs(): List<CardMotifSpec> = listOf(
    CardMotifSpec(
        type = KawaiiMotifType.SPARKLE_FOUR_POINT_FILLED,
        anchor = CardMotifAnchor.TOP_RIGHT,
        sizeDp = 9.dp,
        offsetXDp = (-3).dp,
        offsetYDp = (-2).dp,
        alpha = 0.60f
    ),
    CardMotifSpec(
        type = KawaiiMotifType.MINI_HEART_OUTLINE,
        anchor = CardMotifAnchor.BOTTOM_LEFT,
        sizeDp = 8.dp,
        offsetXDp = 3.dp,
        offsetYDp = 1.dp,
        alpha = 0.50f
    )
)

/**
 * Header-flanked motif layout for cards with title headers.
 */
fun headerFlankedCardMotifs(): List<CardMotifSpec> = listOf(
    CardMotifSpec(
        type = KawaiiMotifType.DIAMOND_GLINT,
        anchor = CardMotifAnchor.HEADER_LEFT,
        sizeDp = 8.dp,
        offsetXDp = (-2).dp,
        offsetYDp = 0.dp,
        alpha = 0.60f
    ),
    CardMotifSpec(
        type = KawaiiMotifType.SPARKLE_FOUR_POINT_HOLLOW,
        anchor = CardMotifAnchor.HEADER_RIGHT,
        sizeDp = 8.dp,
        offsetXDp = 2.dp,
        offsetYDp = 0.dp,
        alpha = 0.60f
    ),
    CardMotifSpec(
        type = KawaiiMotifType.MINI_HEART_OUTLINE,
        anchor = CardMotifAnchor.BOTTOM_LEFT,
        sizeDp = 8.dp,
        offsetXDp = 3.dp,
        offsetYDp = 1.dp,
        alpha = 0.45f
    ),
    CardMotifSpec(
        type = KawaiiMotifType.STARLET_HOLLOW,
        anchor = CardMotifAnchor.BOTTOM_RIGHT,
        sizeDp = 8.dp,
        offsetXDp = (-3).dp,
        offsetYDp = 1.dp,
        alpha = 0.45f
    )
)

// ============================================================================
// Direct Lightweight Canvas Drawing Functions
// ============================================================================

private val SharedMotifPath = Path()

/**
 * Draws a four-pointed sparkle star (✦ / ✧).
 */
fun DrawScope.drawFourPointSparkle(
    center: Offset,
    sizePx: Float,
    color: Color,
    hollow: Boolean = false,
    strokeWidthPx: Float = 1.2f
) {
    val half = sizePx * 0.5f
    val path = SharedMotifPath.apply {
        reset()
        moveTo(center.x, center.y - half)
        quadraticTo(center.x, center.y, center.x + half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + half)
        quadraticTo(center.x, center.y, center.x - half, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - half)
        close()
    }
    if (hollow) {
        drawPath(path, color, style = Stroke(width = strokeWidthPx))
    } else {
        drawPath(path, color, style = Fill)
    }
}

/**
 * Draws a delicate diamond sparkle with slender cross-glint rays (✢).
 */
fun DrawScope.drawDiamondGlint(
    center: Offset,
    sizePx: Float,
    color: Color,
    strokeWidthPx: Float = 1.0f
) {
    val halfH = sizePx * 0.5f
    val halfW = sizePx * 0.32f
    val diamondPath = SharedMotifPath.apply {
        reset()
        moveTo(center.x, center.y - halfH)
        lineTo(center.x + halfW, center.y)
        lineTo(center.x, center.y + halfH)
        lineTo(center.x - halfW, center.y)
        close()
    }
    drawPath(diamondPath, color, style = Fill)

    val arm = sizePx * 0.65f
    drawLine(
        color = color,
        start = Offset(center.x - arm, center.y),
        end = Offset(center.x + arm, center.y),
        strokeWidth = strokeWidthPx
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y - arm),
        end = Offset(center.x, center.y + arm),
        strokeWidth = strokeWidthPx
    )
}

/**
 * Draws a soft miniature outline or filled kawaii heart (♡ / ♥).
 */
fun DrawScope.drawMiniHeart(
    center: Offset,
    sizePx: Float,
    color: Color,
    hollow: Boolean = true,
    strokeWidthPx: Float = 1.2f
) {
    val w = sizePx
    val h = sizePx
    val path = SharedMotifPath.apply {
        reset()
        moveTo(center.x, center.y + h * 0.38f)
        cubicTo(
            center.x - w * 0.52f, center.y - h * 0.12f,
            center.x - w * 0.52f, center.y - h * 0.52f,
            center.x, center.y - h * 0.18f
        )
        cubicTo(
            center.x + w * 0.52f, center.y - h * 0.52f,
            center.x + w * 0.52f, center.y - h * 0.12f,
            center.x, center.y + h * 0.38f
        )
        close()
    }
    if (hollow) {
        drawPath(path, color, style = Stroke(width = strokeWidthPx))
    } else {
        drawPath(path, color, style = Fill)
    }
}

/**
 * Draws a five-pointed pastel starlet (★ / ☆).
 */
fun DrawScope.drawStarlet(
    center: Offset,
    sizePx: Float,
    color: Color,
    hollow: Boolean = false,
    strokeWidthPx: Float = 1.2f
) {
    val outerR = sizePx * 0.5f
    val innerR = outerR * 0.42f
    val path = SharedMotifPath.apply { reset() }
    val angleOffset = -PI.toFloat() / 2f
    val step = PI.toFloat() / 5f

    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = angleOffset + i * step
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    if (hollow) {
        drawPath(path, color, style = Stroke(width = strokeWidthPx))
    } else {
        drawPath(path, color, style = Fill)
    }
}

// ============================================================================
// Card Boundary Modifier Extension
// ============================================================================

/**
 * Adds delicate, soft kawaii sparkle and decorative motifs around card boundaries.
 *
 * Characteristics:
 * - Restrained density: 2 to 4 subtle motifs tucked organically near corners.
 * - Dynamic theme tinting: binds to [ActiveTheme.accentDeep], [ActiveTheme.primary],
 *   or [ActiveTheme.selectionGlow] with luminous alphas (0.35f to 0.75f).
 * - Focus integration: when [isFocused] is true, renders 1–2 tiny sparkle motifs
 *   gently accentuating the outer glow halo without overwhelming the selection border.
 * - Hardware friendly: executed via [drawBehind] using direct Canvas paths without GPU blur
 *   or bitmap overhead.
 */
fun Modifier.kawaiiCardMotifs(
    isFocused: Boolean = false,
    cornerRadius: Dp = 22.dp,
    motifs: List<CardMotifSpec> = defaultCardMotifs(),
    accentColor: Color? = null,
    primaryColor: Color? = null,
    glowColor: Color? = null
): Modifier = composed {
    val effectiveAccent = accentColor ?: ActiveTheme.accentDeep
    val effectivePrimary = primaryColor ?: ActiveTheme.primary
    val effectiveGlow = glowColor ?: ActiveTheme.selectionGlow

    this
        .graphicsLayer { clip = false }
        .drawBehind {
            val w = size.width
            val h = size.height
            val r = cornerRadius.toPx()

            // 1. Draw static boundary motifs (restrained, delicate accents)
            motifs.forEach { motif ->
                val sizePx = motif.sizeDp.toPx()
                val offXPx = motif.offsetXDp.toPx()
                val offYPx = motif.offsetYDp.toPx()

                val baseCenter = when (motif.anchor) {
                    CardMotifAnchor.TOP_LEFT -> Offset(r * 0.45f + offXPx, offYPx)
                    CardMotifAnchor.TOP_RIGHT -> Offset(w - r * 0.45f + offXPx, offYPx)
                    CardMotifAnchor.BOTTOM_LEFT -> Offset(r * 0.45f + offXPx, h + offYPx)
                    CardMotifAnchor.BOTTOM_RIGHT -> Offset(w - r * 0.45f + offXPx, h + offYPx)
                    CardMotifAnchor.HEADER_LEFT -> Offset(r + offXPx, 14.dp.toPx() + offYPx)
                    CardMotifAnchor.HEADER_RIGHT -> Offset(w - r + offXPx, 14.dp.toPx() + offYPx)
                    CardMotifAnchor.CUSTOM -> Offset(offXPx, offYPx)
                }

                val motifColor = when (motif.type) {
                    KawaiiMotifType.SPARKLE_FOUR_POINT_FILLED -> effectivePrimary
                    KawaiiMotifType.SPARKLE_FOUR_POINT_HOLLOW -> effectiveGlow
                    KawaiiMotifType.DIAMOND_GLINT -> effectivePrimary
                    KawaiiMotifType.MINI_HEART_OUTLINE -> effectiveAccent
                    KawaiiMotifType.MINI_HEART_FILLED -> effectivePrimary
                    KawaiiMotifType.STARLET_FILLED -> effectivePrimary
                    KawaiiMotifType.STARLET_HOLLOW -> effectiveAccent
                }.copy(alpha = motif.alpha)

                when (motif.type) {
                    KawaiiMotifType.SPARKLE_FOUR_POINT_FILLED ->
                        drawFourPointSparkle(baseCenter, sizePx, motifColor, hollow = false)
                    KawaiiMotifType.SPARKLE_FOUR_POINT_HOLLOW ->
                        drawFourPointSparkle(baseCenter, sizePx, motifColor, hollow = true, strokeWidthPx = 1.1.dp.toPx())
                    KawaiiMotifType.DIAMOND_GLINT ->
                        drawDiamondGlint(baseCenter, sizePx, motifColor, strokeWidthPx = 1.0.dp.toPx())
                    KawaiiMotifType.MINI_HEART_OUTLINE ->
                        drawMiniHeart(baseCenter, sizePx, motifColor, hollow = true, strokeWidthPx = 1.1.dp.toPx())
                    KawaiiMotifType.MINI_HEART_FILLED ->
                        drawMiniHeart(baseCenter, sizePx, motifColor, hollow = false)
                    KawaiiMotifType.STARLET_FILLED ->
                        drawStarlet(baseCenter, sizePx, motifColor, hollow = false)
                    KawaiiMotifType.STARLET_HOLLOW ->
                        drawStarlet(baseCenter, sizePx, motifColor, hollow = true, strokeWidthPx = 1.1.dp.toPx())
                }
            }

            // 2. Focus Integration: 1–2 tiny sparkle motifs gently accentuating the outer glow halo
            if (isFocused) {
                // Top-right outer halo sparkle
                val focusSparkleTopRight = Offset(w + 1.5.dp.toPx(), -2.dp.toPx())
                drawFourPointSparkle(
                    center = focusSparkleTopRight,
                    sizePx = 7.5.dp.toPx(),
                    color = effectiveGlow.copy(alpha = 0.85f),
                    hollow = false
                )

                // Bottom-left outer halo diamond glint
                val focusGlintBottomLeft = Offset(-2.dp.toPx(), h + 2.dp.toPx())
                drawDiamondGlint(
                    center = focusGlintBottomLeft,
                    sizePx = 6.0.dp.toPx(),
                    color = effectiveGlow.copy(alpha = 0.75f),
                    strokeWidthPx = 1.0.dp.toPx()
                )
            }
        }
}

/**
 * Reusable Mochibun Kawaii Card wrapper component.
 * Applies delicate boundary accents and gentle focus halo sparkles.
 */
@Composable
fun KawaiiCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    containerColor: Color = ActiveTheme.surface,
    borderColor: Color = ActiveTheme.cardBorder,
    borderWidth: Dp = 1.5.dp,
    isFocused: Boolean = false,
    motifs: List<CardMotifSpec> = defaultCardMotifs(),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .kawaiiCardMotifs(
                isFocused = isFocused,
                cornerRadius = cornerRadius,
                motifs = motifs
            )
            .border(width = borderWidth, color = borderColor, shape = shape)
            .background(color = containerColor, shape = shape),
        content = content
    )
}

private val GLOW_EXPANSIONS_DP = floatArrayOf(3.5f, 2.4f, 1.4f, 0.6f)
private val GLOW_ALPHAS = floatArrayOf(0.16f, 0.35f, 0.60f, 0.78f)

fun Modifier.softGlowFocus(
    isSelected: Boolean,
    cornerRadius: Dp = 22.dp,
    glowColor: Color? = null,
    borderColor: Color? = null,
    containerTint: Color? = null
): Modifier = if (!isSelected) this else composed {
    val theme = LocalLauncherTheme.current
    val effectiveGlow = glowColor ?: theme.selectionGlowColor
    val effectiveBorder = borderColor ?: theme.cardBorderColor
    val effectiveTint = containerTint ?: theme.surfaceTint

    this
        .graphicsLayer { clip = false }
        .drawWithContent {
            val cornerPx = cornerRadius.toPx()
            val d = density
            val glowStroke = Stroke(width = 2.0f * d)
            val borderStroke = Stroke(width = 1.8f * d)

            // 1. Concentric halo passes extending 0.6dp to 3.5dp beyond the component boundary
            for (i in 0 until 4) {
                val expansion = GLOW_EXPANSIONS_DP[i] * d
                val alpha = GLOW_ALPHAS[i]
                drawRoundRect(
                    color = effectiveGlow.copy(alpha = alpha),
                    topLeft = Offset(-expansion, -expansion),
                    size = Size(size.width + expansion * 2f, size.height + expansion * 2f),
                    cornerRadius = CornerRadius(cornerPx + expansion, cornerPx + expansion),
                    style = glowStroke
                )
            }

            // 2. Subtle frosted pastel fill over the focused surface
            if (effectiveTint != Color.Transparent && effectiveTint.alpha > 0f) {
                drawRoundRect(
                    color = effectiveTint.copy(alpha = 0.35f),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
            }

            // 3. Draw card container content
            drawContent()

            // 4. Crisp theme outline stroke defining inner boundary
            drawRoundRect(
                color = effectiveBorder,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = borderStroke
            )

            // 5. Delicate outer glow halo focus accents (1–2 tiny sparkles)
            val focusSparkleTopRight = Offset(size.width + 1.5f * d, -2f * d)
            drawFourPointSparkle(
                center = focusSparkleTopRight,
                sizePx = 7.5f * d,
                color = effectiveGlow.copy(alpha = 0.85f),
                hollow = false
            )
            val focusGlintBottomLeft = Offset(-2f * d, size.height + 1.5f * d)
            drawDiamondGlint(
                center = focusGlintBottomLeft,
                sizePx = 6.0f * d,
                color = effectiveGlow.copy(alpha = 0.75f),
                strokeWidthPx = 1.0f * d
            )
        }
}

/**
 * Backward-compatible bridge for existing components previously calling glowingDoubleBorder.
 * Delegates directly to the soft pastel glow focus indicator.
 */
fun Modifier.glowingDoubleBorder(
    isSelected: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    cornerRadius: Dp = 22.dp
): Modifier = softGlowFocus(
    isSelected = isSelected,
    cornerRadius = cornerRadius
)

/**
 * Standardized Mochibun Section Header formatted as:
 * — ♡ Title ♡ —
 */
@Composable
fun MochibunSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val textColor = color ?: ActiveTheme.accentDeep

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(1.5.dp)
                .background(palette.outline, RoundedCornerShape(1.dp))
        )
        Text(
            text = "— ♡ $title ♡ —",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 0.5.sp
        )
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(1.5.dp)
                .background(palette.outline, RoundedCornerShape(1.dp))
        )
    }
}

/**
 * Scalloped pastel lavender cloud trim hanging from the top screen boundary.
 * Rendered using vector Bézier Canvas paths rather than bitmap assets to eliminate
 * texture memory overhead on low-RAM (~1.8 GB available) hardware.
 */
@Composable
fun TopLavenderClouds(
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val backPath = remember { Path() }
    val frontPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        val w = size.width
        val d = density
        val outlineStroke = Stroke(width = 1.5f * d)

        // Back cloud layer
        backPath.reset()
        backPath.moveTo(0f, 0f)
        backPath.lineTo(0f, 28f * d)
        backPath.cubicTo(w * 0.05f, 54f * d, w * 0.14f, 56f * d, w * 0.20f, 38f * d)
        backPath.cubicTo(w * 0.27f, 62f * d, w * 0.40f, 64f * d, w * 0.47f, 40f * d)
        backPath.cubicTo(w * 0.54f, 60f * d, w * 0.67f, 62f * d, w * 0.74f, 42f * d)
        backPath.cubicTo(w * 0.81f, 58f * d, w * 0.93f, 57f * d, w, 32f * d)
        backPath.lineTo(w, 0f)
        backPath.close()
        drawPath(backPath, palette.cloudMid)
        drawPath(backPath, palette.outline, style = outlineStroke)

        // Front cloud layer
        frontPath.reset()
        frontPath.moveTo(0f, 0f)
        frontPath.lineTo(0f, 18f * d)
        frontPath.cubicTo(w * 0.04f, 42f * d, w * 0.12f, 44f * d, w * 0.17f, 26f * d)
        frontPath.cubicTo(w * 0.24f, 48f * d, w * 0.35f, 49f * d, w * 0.42f, 28f * d)
        frontPath.cubicTo(w * 0.48f, 46f * d, w * 0.59f, 48f * d, w * 0.65f, 30f * d)
        frontPath.cubicTo(w * 0.72f, 49f * d, w * 0.85f, 48f * d, w * 0.91f, 28f * d)
        frontPath.cubicTo(w * 0.96f, 38f * d, w * 0.99f, 36f * d, w, 20f * d)
        frontPath.lineTo(w, 0f)
        frontPath.close()
        drawPath(frontPath, palette.cloudFront)
        drawPath(frontPath, palette.primary, style = outlineStroke)
    }
}

/**
 * Matching scalloped lavender cloud trim rising from the bottom screen boundary.
 */
@Composable
fun BottomLavenderClouds(
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette
    val backPath = remember { Path() }
    val frontPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val w = size.width
        val h = size.height
        val d = density
        val outlineStroke = Stroke(width = 1.5f * d)

        // Back cloud layer rising from bottom
        backPath.reset()
        backPath.moveTo(0f, h)
        backPath.lineTo(0f, h - 30f * d)
        backPath.cubicTo(w * 0.06f, h - 58f * d, w * 0.17f, h - 60f * d, w * 0.24f, h - 40f * d)
        backPath.cubicTo(w * 0.31f, h - 64f * d, w * 0.44f, h - 66f * d, w * 0.51f, h - 44f * d)
        backPath.cubicTo(w * 0.58f, h - 62f * d, w * 0.71f, h - 64f * d, w * 0.77f, h - 42f * d)
        backPath.cubicTo(w * 0.84f, h - 58f * d, w * 0.95f, h - 57f * d, w, h - 28f * d)
        backPath.lineTo(w, h)
        backPath.close()
        drawPath(backPath, palette.cloudMid)
        drawPath(backPath, palette.outline, style = outlineStroke)

        // Front cloud layer rising from bottom
        frontPath.reset()
        frontPath.moveTo(0f, h)
        frontPath.lineTo(0f, h - 20f * d)
        frontPath.cubicTo(w * 0.05f, h - 42f * d, w * 0.13f, h - 44f * d, w * 0.19f, h - 26f * d)
        frontPath.cubicTo(w * 0.25f, h - 48f * d, w * 0.37f, h - 50f * d, w * 0.43f, h - 30f * d)
        frontPath.cubicTo(w * 0.50f, h - 46f * d, w * 0.61f, h - 48f * d, w * 0.67f, h - 32f * d)
        frontPath.cubicTo(w * 0.73f, h - 50f * d, w * 0.86f, h - 48f * d, w * 0.92f, h - 30f * d)
        frontPath.cubicTo(w * 0.97f, h - 38f * d, w * 0.99f, h - 36f * d, w, h - 20f * d)
        frontPath.lineTo(w, h)
        frontPath.close()
        drawPath(frontPath, palette.cloudFront)
        drawPath(frontPath, palette.primary, style = outlineStroke)
    }
}


