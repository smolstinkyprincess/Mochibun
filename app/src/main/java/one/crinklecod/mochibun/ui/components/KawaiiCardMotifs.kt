package one.crinklecod.mochibun.ui.components

import kotlinx.coroutines.launch
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import one.crinklecod.mochibun.data.repository.CustomCardBorderSource
import one.crinklecod.mochibun.data.repository.LocalCardBorderState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import one.crinklecod.mochibun.core.haptic.HapticManager
import one.crinklecod.mochibun.data.model.CardBorderStyle
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.CustomCardBorderFrame
import one.crinklecod.mochibun.data.repository.CustomCardBorderManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.cardBorderColor
import one.crinklecod.mochibun.ui.theme.palette
import one.crinklecod.mochibun.ui.theme.selectionGlowColor
import one.crinklecod.mochibun.ui.theme.surfaceTint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

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

// Immutable unit paths (size = 1f, centered at origin) so Skia keeps stable path generation IDs
// and reuses cached masks across draws instead of invalidating SW Path Mask on reset().
private val UnitFourPointSparklePath = Path().apply {
    val half = 0.5f
    moveTo(0f, -half)
    quadraticTo(0f, 0f, half, 0f)
    quadraticTo(0f, 0f, 0f, half)
    quadraticTo(0f, 0f, -half, 0f)
    quadraticTo(0f, 0f, 0f, -half)
    close()
}

private val UnitDiamondGlintPath = Path().apply {
    val halfH = 0.5f
    val halfW = 0.32f
    moveTo(0f, -halfH)
    lineTo(halfW, 0f)
    lineTo(0f, halfH)
    lineTo(-halfW, 0f)
    close()
}

private val UnitMiniHeartPath = Path().apply {
    moveTo(0f, 0.38f)
    cubicTo(-0.52f, -0.12f, -0.52f, -0.52f, 0f, -0.18f)
    cubicTo(0.52f, -0.52f, 0.52f, -0.12f, 0f, 0.38f)
    close()
}

private val UnitStarletPath = Path().apply {
    val outerR = 0.5f
    val innerR = outerR * 0.42f
    val angleOffset = -PI.toFloat() / 2f
    val step = PI.toFloat() / 5f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = angleOffset + i * step
        val x = r * cos(angle)
        val y = r * sin(angle)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

// Motifs are drawn on the main thread only, so a single cached Stroke is safe to share.
private var cachedMotifStroke = Stroke(width = 1.2f)

private fun motifStroke(widthPx: Float): Stroke {
    if (cachedMotifStroke.width != widthPx) cachedMotifStroke = Stroke(width = widthPx)
    return cachedMotifStroke
}

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
    if (sizePx <= 0f) return
    withTransform({
        translate(center.x, center.y)
        scale(sizePx, sizePx, Offset.Zero)
    }) {
        if (hollow) {
            drawPath(UnitFourPointSparklePath, color, style = motifStroke(strokeWidthPx / sizePx))
        } else {
            drawPath(UnitFourPointSparklePath, color, style = Fill)
        }
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
    if (sizePx <= 0f) return
    withTransform({
        translate(center.x, center.y)
        scale(sizePx, sizePx, Offset.Zero)
    }) {
        drawPath(UnitDiamondGlintPath, color, style = Fill)
    }

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
    if (sizePx <= 0f) return
    withTransform({
        translate(center.x, center.y)
        scale(sizePx, sizePx, Offset.Zero)
    }) {
        if (hollow) {
            drawPath(UnitMiniHeartPath, color, style = motifStroke(strokeWidthPx / sizePx))
        } else {
            drawPath(UnitMiniHeartPath, color, style = Fill)
        }
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
    if (sizePx <= 0f) return
    withTransform({
        translate(center.x, center.y)
        scale(sizePx, sizePx, Offset.Zero)
    }) {
        if (hollow) {
            drawPath(UnitStarletPath, color, style = motifStroke(strokeWidthPx / sizePx))
        } else {
            drawPath(UnitStarletPath, color, style = Fill)
        }
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
 * - Hardware friendly: drawn behind the content using direct Canvas paths without GPU blur
 *   or bitmap overhead.
 *
 * Implemented as a [Modifier.Node] (not `composed`) so each card skips a per-modifier
 * subcomposition; theme tokens are read via [currentValueOf] inside draw, which Compose observes
 * so a live theme switch still redraws the motifs.
 */
fun Modifier.kawaiiCardMotifs(
    isFocused: Boolean = false,
    cornerRadius: Dp = 22.dp,
    motifs: List<CardMotifSpec> = defaultCardMotifs(),
    accentColor: Color? = null,
    primaryColor: Color? = null,
    glowColor: Color? = null
): Modifier = this
    .graphicsLayer { clip = false }
    .then(
        KawaiiCardMotifsElement(
            isFocused = isFocused,
            cornerRadius = cornerRadius,
            motifs = motifs,
            accentColor = accentColor,
            primaryColor = primaryColor,
            glowColor = glowColor
        )
    )

private data class KawaiiCardMotifsElement(
    val isFocused: Boolean,
    val cornerRadius: Dp,
    val motifs: List<CardMotifSpec>,
    val accentColor: Color?,
    val primaryColor: Color?,
    val glowColor: Color?
) : ModifierNodeElement<KawaiiCardMotifsNode>() {
    override fun create() = KawaiiCardMotifsNode(
        isFocused, cornerRadius, motifs, accentColor, primaryColor, glowColor
    )

    // Draw nodes auto-invalidate on update, so only the fields need refreshing.
    override fun update(node: KawaiiCardMotifsNode) {
        node.isFocused = isFocused
        node.cornerRadius = cornerRadius
        node.motifs = motifs
        node.accentColor = accentColor
        node.primaryColor = primaryColor
        node.glowColor = glowColor
    }
}

private class KawaiiCardMotifsNode(
    var isFocused: Boolean,
    var cornerRadius: Dp,
    var motifs: List<CardMotifSpec>,
    var accentColor: Color?,
    var primaryColor: Color?,
    var glowColor: Color?
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    override fun ContentDrawScope.draw() {
        val palette = currentValueOf(LocalLauncherTheme).palette
        val effectiveAccent = accentColor ?: palette.deepAccent
        val effectivePrimary = primaryColor ?: palette.primary
        val effectiveGlow = glowColor ?: palette.selectionGlow

        val w = size.width
        val h = size.height
        val r = cornerRadius.toPx()

        // 1. Draw static boundary motifs (restrained, delicate accents).
        // Indexed loop: no Iterator allocation per frame.
        val specs = motifs
        for (i in specs.indices) {
            val motif = specs[i]
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

        // Motifs sit behind the card (same order as the former drawBehind).
        drawContent()
    }
}

// ============================================================================
// Custom 9-Slice & Procedural Card Border Renderer
// ============================================================================

/**
 * Builds the 2-step chamfered pixel-art path for [CardBorderStyle.PIXEL].
 * Reused inside `drawWithCache` so zero objects are allocated during per-frame drawing.
 */
internal fun buildPixelBorderPath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    stepPx: Float,
    outPath: Path = Path()
): Path {
    val w = (right - left).coerceAtLeast(0f)
    val h = (bottom - top).coerceAtLeast(0f)
    val s = stepPx.coerceIn(1f, (min(w, h) / 6f).coerceAtLeast(1f))
    val s2 = s * 2f

    outPath.reset()
    // Start on top edge after top-left 2-step chamfer
    outPath.moveTo(left + s2, top)
    // Top edge -> top-right 2-step chamfer
    outPath.lineTo(right - s2, top)
    outPath.lineTo(right - s2, top + s)
    outPath.lineTo(right - s, top + s)
    outPath.lineTo(right - s, top + s2)
    outPath.lineTo(right, top + s2)
    // Right edge -> bottom-right 2-step chamfer
    outPath.lineTo(right, bottom - s2)
    outPath.lineTo(right - s, bottom - s2)
    outPath.lineTo(right - s, bottom - s)
    outPath.lineTo(right - s2, bottom - s)
    outPath.lineTo(right - s2, bottom)
    // Bottom edge -> bottom-left 2-step chamfer
    outPath.lineTo(left + s2, bottom)
    outPath.lineTo(left + s2, bottom - s)
    outPath.lineTo(left + s, bottom - s)
    outPath.lineTo(left + s, bottom - s2)
    outPath.lineTo(left, bottom - s2)
    // Left edge -> top-left 2-step chamfer
    outPath.lineTo(left, top + s2)
    outPath.lineTo(left + s, top + s2)
    outPath.lineTo(left + s, top + s)
    outPath.lineTo(left + s2, top + s)
    outPath.close()
    return outPath
}

/**
 * Builds the scalloped lace path for [CardBorderStyle.LACE] along the rounded rectangle perimeter.
 * Reused inside `drawWithCache` so zero objects are allocated during per-frame drawing.
 */
internal fun buildLaceBorderPath(
    inset: Float,
    width: Float,
    height: Float,
    cornerRadiusPx: Float,
    scallopRadiusPx: Float,
    scallopStepPx: Float,
    outPath: Path = Path()
): Path {
    outPath.reset()
    if (width <= inset * 2f || height <= inset * 2f) return outPath
    val walker = RoundRectWalker(
        inset = inset,
        width = width,
        height = height,
        radius = cornerRadiusPx
    )
    if (walker.length <= 0f) return outPath
    val lobes = (walker.length / scallopStepPx.coerceAtLeast(2f)).roundToInt().coerceAtLeast(8)
    val lobeLen = walker.length / lobes
    val bulge = scallopRadiusPx.coerceAtLeast(1f)
    val pt = FloatArray(4)
    walker.at(0f, pt)
    outPath.moveTo(pt[0], pt[1])
    for (k in 0 until lobes) {
        walker.at((k + 0.5f) * lobeLen, pt)
        val cx = pt[0] + pt[2] * bulge * 1.85f
        val cy = pt[1] + pt[3] * bulge * 1.85f
        walker.at(((k + 1) * lobeLen).coerceAtMost(walker.length), pt)
        outPath.quadraticTo(cx, cy, pt[0], pt[1])
    }
    outPath.close()
    return outPath
}

/**
 * Draws the 8 border patches (4 corners at 1:1 + 4 stretched edges; center patch skipped) of a
 * custom 9-slice [CustomCardBorderFrame] using `FilterQuality.None` and precomputed [NineSliceDestGeometry].
 */
internal class NineSliceDestGeometry(
    frame: CustomCardBorderFrame,
    leftPx: Int,
    topPx: Int,
    widthPx: Int,
    heightPx: Int
) {
    private val dstSlice = min(frame.slicePx, min(widthPx, heightPx) / 2).coerceAtLeast(1)
    private val midW = (widthPx - dstSlice * 2).coerceAtLeast(0)
    private val midH = (heightPx - dstSlice * 2).coerceAtLeast(0)
    private val rightX = leftPx + widthPx - dstSlice
    private val bottomY = topPx + heightPx - dstSlice

    val hasEdgesW: Boolean = midW > 0
    val hasEdgesH: Boolean = midH > 0

    val cornerDstSize = IntSize(dstSlice, dstSlice)
    val topBottomDstSize = IntSize(midW, dstSlice)
    val leftRightDstSize = IntSize(dstSlice, midH)

    val topLeftDstOffset = IntOffset(leftPx, topPx)
    val topRightDstOffset = IntOffset(rightX, topPx)
    val bottomLeftDstOffset = IntOffset(leftPx, bottomY)
    val bottomRightDstOffset = IntOffset(rightX, bottomY)

    val topEdgeDstOffset = IntOffset(leftPx + dstSlice, topPx)
    val bottomEdgeDstOffset = IntOffset(leftPx + dstSlice, bottomY)
    val leftEdgeDstOffset = IntOffset(leftPx, topPx + dstSlice)
    val rightEdgeDstOffset = IntOffset(rightX, topPx + dstSlice)

    fun draw(scope: DrawScope, frame: CustomCardBorderFrame) = with(scope) {
        val img = frame.image
        // 4 corners (drawn 1:1 at dstSlice x dstSlice)
        drawImage(
            image = img,
            srcOffset = frame.topLeftSrcOffset,
            srcSize = frame.cornerSrcSize,
            dstOffset = topLeftDstOffset,
            dstSize = cornerDstSize,
            filterQuality = FilterQuality.None
        )
        drawImage(
            image = img,
            srcOffset = frame.topRightSrcOffset,
            srcSize = frame.cornerSrcSize,
            dstOffset = topRightDstOffset,
            dstSize = cornerDstSize,
            filterQuality = FilterQuality.None
        )
        drawImage(
            image = img,
            srcOffset = frame.bottomLeftSrcOffset,
            srcSize = frame.cornerSrcSize,
            dstOffset = bottomLeftDstOffset,
            dstSize = cornerDstSize,
            filterQuality = FilterQuality.None
        )
        drawImage(
            image = img,
            srcOffset = frame.bottomRightSrcOffset,
            srcSize = frame.cornerSrcSize,
            dstOffset = bottomRightDstOffset,
            dstSize = cornerDstSize,
            filterQuality = FilterQuality.None
        )
        // Top & bottom stretched edges
        if (hasEdgesW) {
            drawImage(
                image = img,
                srcOffset = frame.topEdgeSrcOffset,
                srcSize = frame.topBottomSrcSize,
                dstOffset = topEdgeDstOffset,
                dstSize = topBottomDstSize,
                filterQuality = FilterQuality.None
            )
            drawImage(
                image = img,
                srcOffset = frame.bottomEdgeSrcOffset,
                srcSize = frame.topBottomSrcSize,
                dstOffset = bottomEdgeDstOffset,
                dstSize = topBottomDstSize,
                filterQuality = FilterQuality.None
            )
        }
        // Left & right stretched edges (center patch is deliberately skipped to save fill-rate)
        if (hasEdgesH) {
            drawImage(
                image = img,
                srcOffset = frame.leftEdgeSrcOffset,
                srcSize = frame.leftRightSrcSize,
                dstOffset = leftEdgeDstOffset,
                dstSize = leftRightDstSize,
                filterQuality = FilterQuality.None
            )
            drawImage(
                image = img,
                srcOffset = frame.rightEdgeSrcOffset,
                srcSize = frame.leftRightSrcSize,
                dstOffset = rightEdgeDstOffset,
                dstSize = leftRightDstSize,
                filterQuality = FilterQuality.None
            )
        }
    }
}

/**
 * Reusable Mochibun Card Border modifier supporting both:
 * 1. **Custom 9-Slice PNG Frame** (`card_border.png` via [CustomCardBorderManager]) with `FilterQuality.None`
 *    when [allowCustomNineSlice] is `true` and a custom border PNG is installed.
 * 2. **5 Procedural Border Styles** ([CardBorderStyle]: `SOLID`, `DOUBLE`, `LACE`, `STITCH`, `PIXEL`)
 *    when no custom 9-slice PNG is active.
 *
 * Implemented as a [Modifier.Node] (not `composed`) so each bordered card skips a per-modifier
 * subcomposition. All `Stroke`, `Path`, `PathEffect`, and `IntOffset`/`IntSize` objects are
 * precomputed into a cached draw plan that is rebuilt only when the size, density, resolved
 * color, style, or 9-slice frame changes, so zero allocations occur on ordinary frames.
 */
fun Modifier.mochibunCardBorder(
    cornerRadius: Dp = 22.dp,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.5.dp,
    allowCustomNineSlice: Boolean = true,
    styleOverride: CardBorderStyle? = null
): Modifier = this
    .graphicsLayer { clip = false }
    .then(
        MochibunCardBorderElement(
            cornerRadius = cornerRadius,
            borderColor = borderColor,
            borderWidth = borderWidth,
            allowCustomNineSlice = allowCustomNineSlice,
            styleOverride = styleOverride
        )
    )

private data class MochibunCardBorderElement(
    val cornerRadius: Dp,
    val borderColor: Color,
    val borderWidth: Dp,
    val allowCustomNineSlice: Boolean,
    val styleOverride: CardBorderStyle?
) : ModifierNodeElement<MochibunCardBorderNode>() {
    override fun create() = MochibunCardBorderNode(
        cornerRadius, borderColor, borderWidth, allowCustomNineSlice, styleOverride
    )

    override fun update(node: MochibunCardBorderNode) {
        node.cornerRadius = cornerRadius
        node.borderColor = borderColor
        node.borderWidth = borderWidth
        node.allowCustomNineSlice = allowCustomNineSlice
        node.styleOverride = styleOverride
        // Geometry depends on radius/width; the draw itself is auto-invalidated on update.
        node.invalidatePlan()
    }
}

private class MochibunCardBorderNode(
    var cornerRadius: Dp,
    var borderColor: Color,
    var borderWidth: Dp,
    var allowCustomNineSlice: Boolean,
    var styleOverride: CardBorderStyle?
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var plan: (DrawScope.() -> Unit)? = null
    private var planWidth = 0f
    private var planHeight = 0f
    private var planDensity = 0f
    private var planColor = Color.Unspecified
    private var planStyle: CardBorderStyle? = null
    private var planFrame: CustomCardBorderFrame? = null

    fun invalidatePlan() {
        plan = null
    }

    override fun onReset() {
        plan = null
        planFrame = null
    }

    override fun ContentDrawScope.draw() {
        // currentValueOf reads inside draw are snapshot-observed, so a theme or border swap
        // invalidates this draw just like the former composed{} recomposition did.
        val resolvedColor = if (borderColor != Color.Unspecified) {
            borderColor
        } else {
            currentValueOf(LocalLauncherTheme).palette.outline
        }
        val borderState = currentValueOf(LocalCardBorderState)
        // Every Mochibun composition root (MochibunTheme, IME, Recents) provides
        // LocalCardBorderState; the manager snapshot only covers bare trees such as previews.
        val resolvedStyle = styleOverride
            ?: borderState?.style
            ?: CustomCardBorderManager.activeBorderStyle.value
        val customFrame = if (!allowCustomNineSlice) {
            null
        } else if (borderState != null) {
            borderState.frame
        } else {
            CustomCardBorderManager.source.value.let { source ->
                if (source is CustomCardBorderSource.Custom) CustomCardBorderManager.cachedFrame(source) else null
            }
        }

        val w = size.width
        val h = size.height
        var drawPlan = plan
        if (
            drawPlan == null ||
            w != planWidth ||
            h != planHeight ||
            density != planDensity ||
            resolvedColor != planColor ||
            resolvedStyle != planStyle ||
            customFrame !== planFrame
        ) {
            drawPlan = buildCardBorderPlan(
                w = w,
                h = h,
                cornerRadius = cornerRadius,
                borderWidth = borderWidth,
                resolvedColor = resolvedColor,
                resolvedStyle = resolvedStyle,
                customFrame = customFrame
            )
            plan = drawPlan
            planWidth = w
            planHeight = h
            planDensity = density
            planColor = resolvedColor
            planStyle = resolvedStyle
            planFrame = customFrame
        }

        drawContent()
        drawPlan()
    }
}

/**
 * Precomputes every geometry object for one border configuration and returns the zero-allocation
 * draw block (the equivalent of a `drawWithCache` build pass).
 */
private fun DrawScope.buildCardBorderPlan(
    w: Float,
    h: Float,
    cornerRadius: Dp,
    borderWidth: Dp,
    resolvedColor: Color,
    resolvedStyle: CardBorderStyle,
    customFrame: CustomCardBorderFrame?
): DrawScope.() -> Unit {
    val maxRadius = min(w, h) / 2f
    val r = cornerRadius.toPx().coerceIn(0f, maxRadius)

    if (customFrame != null && w >= 4f && h >= 4f) {
        val nineSlice = NineSliceDestGeometry(
            frame = customFrame,
            leftPx = 0,
            topPx = 0,
            widthPx = w.roundToInt(),
            heightPx = h.roundToInt()
        )
        return borderPlan { nineSlice.draw(this, customFrame) }
    }

    return when (resolvedStyle) {
        CardBorderStyle.SOLID -> {
            val strokePx = borderWidth.toPx()
            val half = strokePx / 2f
            val topLeft = Offset(half, half)
            val rectSize = Size((w - strokePx).coerceAtLeast(0f), (h - strokePx).coerceAtLeast(0f))
            val corner = CornerRadius((r - half).coerceAtLeast(0f))
            val stroke = Stroke(width = strokePx)
            borderPlan {
                drawRoundRect(
                    color = resolvedColor,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = corner,
                    style = stroke
                )
            }
        }

        CardBorderStyle.DOUBLE -> {
            val outerStrokePx = 1.5.dp.toPx()
            val innerStrokePx = 1.0.dp.toPx()
            val outerHalf = outerStrokePx / 2f
            val innerInset = 3.5.dp.toPx()

            val outerTopLeft = Offset(outerHalf, outerHalf)
            val outerSize = Size(
                (w - outerStrokePx).coerceAtLeast(0f),
                (h - outerStrokePx).coerceAtLeast(0f)
            )
            val outerCorner = CornerRadius((r - outerHalf).coerceAtLeast(0f))
            val outerStroke = Stroke(width = outerStrokePx)

            val innerTopLeft = Offset(innerInset, innerInset)
            val innerSize = Size(
                (w - innerInset * 2f).coerceAtLeast(0f),
                (h - innerInset * 2f).coerceAtLeast(0f)
            )
            val innerCorner = CornerRadius((r - innerInset).coerceAtLeast(0f))
            val innerStroke = Stroke(width = innerStrokePx)
            val innerColor = resolvedColor.copy(alpha = (resolvedColor.alpha * 0.72f).coerceIn(0f, 1f))

            borderPlan {
                drawRoundRect(
                    color = resolvedColor,
                    topLeft = outerTopLeft,
                    size = outerSize,
                    cornerRadius = outerCorner,
                    style = outerStroke
                )
                if (innerSize.width > 0f && innerSize.height > 0f) {
                    drawRoundRect(
                        color = innerColor,
                        topLeft = innerTopLeft,
                        size = innerSize,
                        cornerRadius = innerCorner,
                        style = innerStroke
                    )
                }
            }
        }

        CardBorderStyle.LACE -> {
            val baseStrokePx = 1.2.dp.toPx()
            val scallopStrokePx = 1.1.dp.toPx()
            val half = baseStrokePx / 2f
            val baseTopLeft = Offset(half, half)
            val baseSize = Size((w - baseStrokePx).coerceAtLeast(0f), (h - baseStrokePx).coerceAtLeast(0f))
            val baseCorner = CornerRadius((r - half).coerceAtLeast(0f))
            val baseStroke = Stroke(width = baseStrokePx)
            val scallopStroke = Stroke(
                width = scallopStrokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
            val lacePath = buildLaceBorderPath(
                inset = half,
                width = w,
                height = h,
                cornerRadiusPx = (r - half).coerceAtLeast(0f),
                scallopRadiusPx = 3.dp.toPx(),
                scallopStepPx = 6.dp.toPx()
            )
            val laceColor = resolvedColor.copy(alpha = (resolvedColor.alpha * 0.85f).coerceIn(0f, 1f))
            borderPlan {
                drawPath(
                    path = lacePath,
                    color = laceColor,
                    style = scallopStroke
                )
                drawRoundRect(
                    color = resolvedColor,
                    topLeft = baseTopLeft,
                    size = baseSize,
                    cornerRadius = baseCorner,
                    style = baseStroke
                )
            }
        }

        CardBorderStyle.STITCH -> {
            val outerStrokePx = 1.0.dp.toPx()
            val outerHalf = outerStrokePx / 2f
            val outerTopLeft = Offset(outerHalf, outerHalf)
            val outerSize = Size(
                (w - outerStrokePx).coerceAtLeast(0f),
                (h - outerStrokePx).coerceAtLeast(0f)
            )
            val outerCorner = CornerRadius((r - outerHalf).coerceAtLeast(0f))
            val outerStroke = Stroke(width = outerStrokePx)
            val outerColor = resolvedColor.copy(alpha = (resolvedColor.alpha * 0.55f).coerceIn(0f, 1f))

            val stitchInset = 3.dp.toPx()
            val stitchStrokePx = 1.35.dp.toPx()
            val stitchTopLeft = Offset(stitchInset, stitchInset)
            val stitchSize = Size(
                (w - stitchInset * 2f).coerceAtLeast(0f),
                (h - stitchInset * 2f).coerceAtLeast(0f)
            )
            val stitchCorner = CornerRadius((r - stitchInset).coerceAtLeast(0f))
            val dashEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(5.dp.toPx(), 3.dp.toPx()),
                phase = 0f
            )
            val stitchStroke = Stroke(
                width = stitchStrokePx,
                cap = StrokeCap.Round,
                pathEffect = dashEffect
            )

            borderPlan {
                drawRoundRect(
                    color = outerColor,
                    topLeft = outerTopLeft,
                    size = outerSize,
                    cornerRadius = outerCorner,
                    style = outerStroke
                )
                if (stitchSize.width > 0f && stitchSize.height > 0f) {
                    drawRoundRect(
                        color = resolvedColor,
                        topLeft = stitchTopLeft,
                        size = stitchSize,
                        cornerRadius = stitchCorner,
                        style = stitchStroke
                    )
                }
            }
        }

        CardBorderStyle.PIXEL -> {
            val stepPx = 2.dp.toPx().coerceAtLeast(1f)
            val half = stepPx / 2f
            val pixelPath = buildPixelBorderPath(
                left = half,
                top = half,
                right = w - half,
                bottom = h - half,
                stepPx = stepPx
            )
            val pixelStroke = Stroke(
                width = stepPx,
                cap = StrokeCap.Butt,
                join = StrokeJoin.Miter
            )
            borderPlan {
                drawPath(
                    path = pixelPath,
                    color = resolvedColor,
                    style = pixelStroke
                )
            }
        }
    }
}

/** Names a border draw block; avoids a bare lambda being parsed as the preceding call's trailing lambda. */
private fun borderPlan(block: DrawScope.() -> Unit): DrawScope.() -> Unit = block

/**
 * Reusable Mochibun Kawaii Card wrapper component.
 * Applies delicate boundary accents, gentle focus halo sparkles, and the active
 * [mochibunCardBorder] (custom 9-slice PNG or procedural [CardBorderStyle]).
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
            .background(color = containerColor, shape = shape)
            .mochibunCardBorder(
                cornerRadius = cornerRadius,
                borderColor = borderColor,
                borderWidth = borderWidth
            ),
        content = content
    )
}

private val GLOW_EXPANSIONS_DP = floatArrayOf(3.5f, 2.4f, 1.4f, 0.6f)
private val GLOW_ALPHAS = floatArrayOf(0.16f, 0.35f, 0.60f, 0.78f)

/**
 * Mochibun soft pastel focus halo for D-pad selection.
 *
 * Built from two [Modifier.Node]s instead of `composed` (no per-item subcomposition, which matters
 * for long D-pad lists on the MT6761):
 * 1. A selection-effects node that runs the focus-gain tick haptic, the optional 1.0 → 1.06 → 1.0
 *    spring pop (applied on a `clip = false` graphics layer around the content), and the optional
 *    bring-into-view request from its own coroutine scope.
 * 2. A draw node placed *inside* that layer that paints the unclipped glow passes, tint, outline,
 *    and halo sparkles, so the halo scales with the pop exactly as before.
 *
 * `isSelected` remains the caller-owned selection flag (not a focus listener), preserving each
 * screen's explicit D-pad focus contract.
 */
fun Modifier.softGlowFocus(
    isSelected: Boolean,
    cornerRadius: Dp = 22.dp,
    glowColor: Color? = null,
    borderColor: Color? = null,
    containerTint: Color? = null,
    // Scroll the focused item into view. Off for fixed overlays (the pet pill): its request
    // would otherwise reach the Home HorizontalPager and flip it to another page.
    bringIntoView: Boolean = true,
    // Set false in rapid-scroll lists (App Drawer) to avoid multi-frame scale layer invalidation.
    animateScale: Boolean = true
): Modifier {
    // Separate element types so the non-scaling variant adds no layout coordinator or layer.
    val effects: Modifier = if (animateScale) {
        SoftGlowScaleEffectsElement(isSelected, bringIntoView)
    } else {
        SoftGlowEffectsElement(isSelected, bringIntoView)
    }
    return this
        .then(effects)
        .then(SoftGlowDrawElement(isSelected, cornerRadius, glowColor, borderColor, containerTint))
}

private data class SoftGlowEffectsElement(
    val isSelected: Boolean,
    val bringIntoView: Boolean
) : ModifierNodeElement<SoftGlowEffectsNode>() {
    override fun create() = SoftGlowEffectsNode(isSelected, bringIntoView)
    override fun update(node: SoftGlowEffectsNode) = node.update(isSelected, bringIntoView)
}

private data class SoftGlowScaleEffectsElement(
    val isSelected: Boolean,
    val bringIntoView: Boolean
) : ModifierNodeElement<SoftGlowScaleEffectsNode>() {
    override fun create() = SoftGlowScaleEffectsNode(isSelected, bringIntoView)
    override fun update(node: SoftGlowScaleEffectsNode) = node.update(isSelected, bringIntoView)
}

/**
 * Runs the selection side effects that used to live in `LaunchedEffect(isSelected)`: relaunched
 * whenever [isSelected] flips, cancelled with the node's coroutine scope on detach.
 */
private open class SoftGlowEffectsNode(
    var isSelected: Boolean,
    var requestBringIntoView: Boolean
) : Modifier.Node(), CompositionLocalConsumerModifierNode {

    /** Spring pop driver; only the scaling subclass owns one. */
    open val scaleAnim: Animatable<Float, AnimationVector1D>? get() = null

    private var effectJob: Job? = null

    // Parameter updates never require relayout/redraw here: the layer reads the Animatable.
    override val shouldAutoInvalidate: Boolean get() = false

    override fun onAttach() {
        // A fresh LaunchedEffect would only do work when selected (or to settle a stale pop),
        // so skip launching a coroutine for every unselected list item that attaches.
        val anim = scaleAnim
        if (isSelected || (anim != null && anim.value != 1.0f)) launchSelectionEffect()
    }

    override fun onDetach() {
        effectJob = null
    }

    fun update(isSelected: Boolean, bringIntoView: Boolean) {
        val selectionChanged = this.isSelected != isSelected
        this.isSelected = isSelected
        this.requestBringIntoView = bringIntoView
        if (selectionChanged && isAttached) launchSelectionEffect()
    }

    private fun launchSelectionEffect() {
        effectJob?.cancel()
        effectJob = coroutineScope.launch { runSelectionEffect() }
    }

    private suspend fun CoroutineScope.runSelectionEffect() {
        val anim = scaleAnim
        if (isSelected) {
            // Tactile tick haptic on focus gain
            if (PreferencesManager.isDpadBoundaryHaptics.value) {
                HapticManager.vibrateTick(currentValueOf(LocalContext), fallbackDurationMs = 14)
            }
            if (anim != null) {
                launch {
                    try {
                        // Keitai focus pop: transient spring scale 1.0f -> 1.06f -> 1.0f
                        anim.animateTo(
                            targetValue = 1.06f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                        anim.animateTo(
                            targetValue = 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    } catch (_: Exception) {
                        if (anim.value != 1.0f) anim.snapTo(1.0f)
                    }
                }
            }
            if (requestBringIntoView) {
                try {
                    val density = requireDensity()
                    val topMargin = with(density) { 58.dp.toPx() }
                    val bottomMargin = with(density) { 72.dp.toPx() }
                    val componentHeight = requireLayoutCoordinates().size.height.toFloat()
                    val rect = Rect(
                        left = 0f,
                        top = -topMargin,
                        right = 0f,
                        bottom = componentHeight + bottomMargin
                    )
                    bringIntoView { rect }
                } catch (_: Throwable) {
                }
            }
        } else if (anim != null && anim.value != 1.0f) {
            anim.snapTo(1.0f)
        }
    }
}

/**
 * Scaling variant: also a [LayoutModifierNode] that places the content on a `clip = false`
 * graphics layer whose scale reads [scaleAnim], so the pop only updates RenderNode properties
 * (no re-measure, no re-record), exactly like the former `graphicsLayer {}` block.
 */
private class SoftGlowScaleEffectsNode(
    isSelected: Boolean,
    bringIntoView: Boolean
) : SoftGlowEffectsNode(isSelected, bringIntoView), LayoutModifierNode {

    override val scaleAnim: Animatable<Float, AnimationVector1D> = Animatable(1.0f)

    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val s = scaleAnim.value
        scaleX = s
        scaleY = s
        clip = false
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
        }
    }
}

private data class SoftGlowDrawElement(
    val isSelected: Boolean,
    val cornerRadius: Dp,
    val glowColor: Color?,
    val borderColor: Color?,
    val containerTint: Color?
) : ModifierNodeElement<SoftGlowDrawNode>() {
    override fun create() = SoftGlowDrawNode(isSelected, cornerRadius, glowColor, borderColor, containerTint)

    // Draw nodes auto-invalidate on update.
    override fun update(node: SoftGlowDrawNode) {
        node.isSelected = isSelected
        node.cornerRadius = cornerRadius
        node.glowColor = glowColor
        node.borderColor = borderColor
        node.containerTint = containerTint
    }
}

private class SoftGlowDrawNode(
    var isSelected: Boolean,
    var cornerRadius: Dp,
    var glowColor: Color?,
    var borderColor: Color?,
    var containerTint: Color?
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    // Built once per density instead of on every focused frame.
    private var strokeDensity = 0f
    private var glowStroke = Stroke(width = 2.0f)
    private var borderStroke = Stroke(width = 1.8f)

    override fun ContentDrawScope.draw() {
        if (!isSelected) {
            drawContent()
            return
        }

        val d = density
        if (d != strokeDensity) {
            strokeDensity = d
            glowStroke = Stroke(width = 2.0f * d)
            borderStroke = Stroke(width = 1.8f * d)
        }

        // Read in draw (snapshot-observed) so a live theme switch repaints the halo.
        val theme = currentValueOf(LocalLauncherTheme)
        val effectiveGlow = glowColor ?: theme.selectionGlowColor
        val effectiveBorder = borderColor ?: theme.cardBorderColor
        val effectiveTint = containerTint ?: theme.surfaceTint

        val cornerPx = cornerRadius.toPx()

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
    val drift = currentCloudDriftPx()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        // Back cloud layer (static path built once per size in drawWithCache; shifted via RenderNode)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationX = -drift.value.toFloat() }
                .drawWithCache {
                    val w = size.width
                    val d = density
                    // Path overhangs both screen edges so the drift never uncovers a gap
                    val pad = cloudDriftOverhang(d)
                    val outlineStroke = Stroke(width = 1.5f * d)
                    val backPath = Path().apply {
                        moveTo(-pad, 0f)
                        lineTo(-pad, 28f * d)
                        lineTo(0f, 28f * d)
                        cubicTo(w * 0.05f, 54f * d, w * 0.14f, 56f * d, w * 0.20f, 38f * d)
                        cubicTo(w * 0.27f, 62f * d, w * 0.40f, 64f * d, w * 0.47f, 40f * d)
                        cubicTo(w * 0.54f, 60f * d, w * 0.67f, 62f * d, w * 0.74f, 42f * d)
                        cubicTo(w * 0.81f, 58f * d, w * 0.93f, 57f * d, w, 32f * d)
                        lineTo(w + pad, 32f * d)
                        lineTo(w + pad, 0f)
                        close()
                    }
                    onDrawBehind {
                        drawPath(backPath, palette.cloudMid)
                        drawPath(backPath, palette.outline, style = outlineStroke)
                    }
                }
        )

        // Front cloud layer (drifts opposite to the back layer)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationX = drift.value.toFloat() }
                .drawWithCache {
                    val w = size.width
                    val d = density
                    val pad = cloudDriftOverhang(d)
                    val outlineStroke = Stroke(width = 1.5f * d)
                    val frontPath = Path().apply {
                        moveTo(-pad, 0f)
                        lineTo(-pad, 18f * d)
                        lineTo(0f, 18f * d)
                        cubicTo(w * 0.04f, 42f * d, w * 0.12f, 44f * d, w * 0.17f, 26f * d)
                        cubicTo(w * 0.24f, 48f * d, w * 0.35f, 49f * d, w * 0.42f, 28f * d)
                        cubicTo(w * 0.48f, 46f * d, w * 0.59f, 48f * d, w * 0.65f, 30f * d)
                        cubicTo(w * 0.72f, 49f * d, w * 0.85f, 48f * d, w * 0.91f, 28f * d)
                        cubicTo(w * 0.96f, 38f * d, w * 0.99f, 36f * d, w, 20f * d)
                        lineTo(w + pad, 20f * d)
                        lineTo(w + pad, 0f)
                        close()
                    }
                    onDrawBehind {
                        drawPath(frontPath, palette.cloudFront)
                        drawPath(frontPath, palette.primary, style = outlineStroke)
                    }
                }
        )
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
    val drift = currentCloudDriftPx()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        // Back cloud layer rising from bottom
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationX = -drift.value.toFloat() }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val d = density
                    val pad = cloudDriftOverhang(d)
                    val outlineStroke = Stroke(width = 1.5f * d)
                    val backPath = Path().apply {
                        moveTo(-pad, h)
                        lineTo(-pad, h - 30f * d)
                        lineTo(0f, h - 30f * d)
                        cubicTo(w * 0.06f, h - 58f * d, w * 0.17f, h - 60f * d, w * 0.24f, h - 40f * d)
                        cubicTo(w * 0.31f, h - 64f * d, w * 0.44f, h - 66f * d, w * 0.51f, h - 44f * d)
                        cubicTo(w * 0.58f, h - 62f * d, w * 0.71f, h - 64f * d, w * 0.77f, h - 42f * d)
                        cubicTo(w * 0.84f, h - 58f * d, w * 0.95f, h - 57f * d, w, h - 28f * d)
                        lineTo(w + pad, h - 28f * d)
                        lineTo(w + pad, h)
                        close()
                    }
                    onDrawBehind {
                        drawPath(backPath, palette.cloudMid)
                        drawPath(backPath, palette.outline, style = outlineStroke)
                    }
                }
        )

        // Front cloud layer rising from bottom
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationX = drift.value.toFloat() }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val d = density
                    val pad = cloudDriftOverhang(d)
                    val outlineStroke = Stroke(width = 1.5f * d)
                    val frontPath = Path().apply {
                        moveTo(-pad, h)
                        lineTo(-pad, h - 20f * d)
                        lineTo(0f, h - 20f * d)
                        cubicTo(w * 0.05f, h - 42f * d, w * 0.13f, h - 44f * d, w * 0.19f, h - 26f * d)
                        cubicTo(w * 0.25f, h - 48f * d, w * 0.37f, h - 50f * d, w * 0.43f, h - 30f * d)
                        cubicTo(w * 0.50f, h - 46f * d, w * 0.61f, h - 48f * d, w * 0.67f, h - 32f * d)
                        cubicTo(w * 0.73f, h - 50f * d, w * 0.86f, h - 48f * d, w * 0.92f, h - 30f * d)
                        cubicTo(w * 0.97f, h - 38f * d, w * 0.99f, h - 36f * d, w, h - 20f * d)
                        lineTo(w + pad, h - 20f * d)
                        lineTo(w + pad, h)
                        close()
                    }
                    onDrawBehind {
                        drawPath(frontPath, palette.cloudFront)
                        drawPath(frontPath, palette.primary, style = outlineStroke)
                    }
                }
        )
    }
}

private const val CLOUD_DRIFT_PX = 4f
private const val CLOUD_DRIFT_PERIOD_MS = 4000L
private const val CLOUD_DRIFT_STEP_MS = 250L

/** Path overhang past each screen edge: the drift plus the outline stroke width. */
private fun cloudDriftOverhang(density: Float): Float = CLOUD_DRIFT_PX + 2f * density

val LocalCloudDriftPx = compositionLocalOf<State<Int>?> { null }

/**
 * Gentle horizontal cloud drift: ±[CLOUD_DRIFT_PX] over a 4 s sine period, in whole pixels.
 *
 * Keitai-stepped like the mascot bob: sampled every [CLOUD_DRIFT_STEP_MS] instead of animated per
 * frame, and shifted on the RenderNode via graphicsLayer without mutating paths or re-recording
 * draw blocks. Gated to [Lifecycle.State.RESUMED] so paused activities/dialogs do 0 work, and
 * hoisted via [LocalCloudDriftPx] in [one.crinklecod.mochibun.ui.theme.MochibunTheme] so top
 * and bottom clouds across all pager tabs and dialogs share a single ticker.
 */
@Composable
fun rememberLiveCloudDriftPx(): State<Int> {
    val drift = remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var startNanos = -1L
            while (true) {
                withFrameNanos { now ->
                    if (startNanos < 0L) startNanos = now
                    val periodMs = (now - startNanos) / 1_000_000L % CLOUD_DRIFT_PERIOD_MS
                    val phase = 2 * PI * periodMs / CLOUD_DRIFT_PERIOD_MS
                    drift.intValue = (CLOUD_DRIFT_PX * sin(phase)).roundToInt()
                }
                delay(CLOUD_DRIFT_STEP_MS)
            }
        }
    }
    return drift
}

@Composable
private fun currentCloudDriftPx(): State<Int> =
    LocalCloudDriftPx.current ?: rememberLiveCloudDriftPx()
