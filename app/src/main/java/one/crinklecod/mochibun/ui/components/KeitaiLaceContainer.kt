package one.crinklecod.mochibun.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import one.crinklecod.mochibun.ui.dialogs.PixelRibbonBow
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Default pearl fill: warm ivory rather than stark white, so the piping reads as pearls. */
private val PearlIvory = Color(0xFFFFFCF5)

// Two-tone organza styling (relative alphas, multiplied with the caller's colors).
private const val ORGANZA_WASH_ALPHA = 0.54f
private const val ORGANZA_RIM_ALPHA = 0.28f
private const val PEARL_OUTLINE_ALPHA = 0.65f

/**
 * Keitai "two-tone organza" frame, from the outside in:
 * 1. a sheer scalloped organza ruffle (translucent [containerColor] wash with a hairline
 *    [borderColor] rim) that lets the wallpaper and stickers peek through,
 * 2. a ring of ivory pearls piped along the seam between ruffle and card,
 * 3. an opaque rounded card ([cardCornerRadius]) with a crisp 1.35dp outline, filled with
 *    [containerBrush] when given (e.g. a gradient header pill) or [containerColor] otherwise,
 * plus optional pixel-art bows pinned at 45° on the four corners.
 *
 * The ruffle and pearls follow the card's rounded outline, so corners stay even. The ruffle
 * reaches the measured bounds and the card sits [scallopDepth] + pearl radius + 1dp inside them.
 * Everything is drawn with plain Canvas calls (no shaders, no shadows); paths and pearl
 * positions are built once per size in drawWithCache, so drawing allocates nothing.
 *
 * Content is inset by [contentPadding] and kept unclipped so softGlowFocus halos stay intact.
 * Apply curtainUnfurl to [modifier]: the ruffle, pearls and bow knots sit inside the measured
 * bounds, so its RectangleShape clip never slices the frame.
 */
@Composable
fun KeitaiLaceContainer(
    modifier: Modifier = Modifier,
    cardCornerRadius: Dp = 20.dp,
    scallopDepth: Dp = 5.dp,
    beadDiameter: Dp = 7.dp,
    contentPadding: Dp = scallopDepth + beadDiameter + 8.dp,
    containerColor: Color = ActiveTheme.surface,
    containerBrush: Brush? = null,
    borderColor: Color = ActiveTheme.cardBorder,
    beadFillColor: Color = PearlIvory,
    beadBorderColor: Color = borderColor,
    showCornerBows: Boolean = true,
    bowWidth: Dp = 28.dp,
    bowHeight: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .drawWithCache {
                val rimStroke = 0.8.dp.toPx()
                val pearlStroke = 0.85.dp.toPx()
                val cardStroke = 1.35.dp.toPx()
                val depth = scallopDepth.toPx()
                val pearlRadius = beadDiameter.toPx() / 2f

                // Inner card: the pearls are centred on its edge, so leave room for half a pearl.
                val inset = depth + pearlRadius + 1.dp.toPx()
                val cardW = (size.width - inset * 2).coerceAtLeast(0f)
                val cardH = (size.height - inset * 2).coerceAtLeast(0f)
                val corner = min(cardCornerRadius.toPx(), min(cardW, cardH) / 2f)

                // ── Layer 1: organza ruffle ──────────────────────────────────────────────
                // Lobes are quadratic arcs along a baseline concentric with the card (inset by
                // the scallop depth). A control point 2x the bulge away from the baseline puts
                // each lobe's peak exactly `bulge` out, i.e. half a rim stroke inside the bounds.
                val base = RoundRectWalker(
                    inset = depth, width = size.width, height = size.height,
                    radius = corner + (inset - depth)
                )
                val bulge = depth - rimStroke / 2f
                val lobes = (base.length / (depth * 2f)).roundToInt().coerceAtLeast(4)
                val lobeLen = base.length / lobes
                val p = FloatArray(4)
                val rufflePath = Path()
                base.at(0f, p)
                rufflePath.moveTo(p[0], p[1])
                for (k in 0 until lobes) {
                    base.at((k + 0.5f) * lobeLen, p)
                    val cx = p[0] + p[2] * bulge * 2f
                    val cy = p[1] + p[3] * bulge * 2f
                    base.at(((k + 1) * lobeLen).coerceAtMost(base.length), p)
                    rufflePath.quadraticTo(cx, cy, p[0], p[1])
                }
                rufflePath.close()

                // Sheer wash fills only the ring outside the card (even-odd hole), so a
                // translucent containerColor is never composited twice under the card.
                val cardRect = RoundRect(
                    left = inset, top = inset, right = inset + cardW, bottom = inset + cardH,
                    cornerRadius = CornerRadius(corner)
                )
                val washPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addPath(rufflePath)
                    addRoundRect(cardRect)
                }

                // ── Layer 2: pearl piping on the seam ────────────────────────────────────
                val seam = RoundRectWalker(inset = inset, width = size.width, height = size.height, radius = corner)
                val pearlCount = (seam.length / (pearlRadius * 2f * 1.2f)).roundToInt().coerceAtLeast(4)
                val pearlStep = seam.length / pearlCount
                val pearls = FloatArray(pearlCount * 2)
                for (i in 0 until pearlCount) {
                    seam.at(i * pearlStep, p)
                    pearls[i * 2] = p[0]
                    pearls[i * 2 + 1] = p[1]
                }
                val pearlDrawRadius = pearlRadius - pearlStroke / 2f

                // ── Layer 3: opaque card ─────────────────────────────────────────────────
                val cardTopLeft = Offset(inset, inset)
                val cardSize = Size(cardW, cardH)
                val cardRadius = CornerRadius(corner)
                val half = cardStroke / 2f
                val strokeTopLeft = Offset(inset + half, inset + half)
                val strokeSize = Size(cardW - cardStroke, cardH - cardStroke)
                val cardStrokeRadius = CornerRadius((corner - half).coerceAtLeast(0f))

                val washColor = containerColor.copy(alpha = containerColor.alpha * ORGANZA_WASH_ALPHA)
                val rimColor = borderColor.copy(alpha = borderColor.alpha * ORGANZA_RIM_ALPHA)
                val pearlOutline = beadBorderColor.copy(alpha = beadBorderColor.alpha * PEARL_OUTLINE_ALPHA)
                val rimStyle = Stroke(width = rimStroke)
                val pearlStyle = Stroke(width = pearlStroke)
                val cardStrokeStyle = Stroke(width = cardStroke)

                onDrawBehind {
                    drawPath(washPath, washColor)
                    drawPath(rufflePath, rimColor, style = rimStyle)
                    if (containerBrush != null) {
                        drawRoundRect(containerBrush, cardTopLeft, cardSize, cardRadius)
                    } else {
                        drawRoundRect(containerColor, cardTopLeft, cardSize, cardRadius)
                    }
                    drawRoundRect(
                        borderColor, strokeTopLeft, strokeSize, cardStrokeRadius,
                        style = cardStrokeStyle
                    )
                    // Pearls last so they sit on top of the card edge like piping.
                    var i = 0
                    while (i < pearls.size) {
                        val c = Offset(pearls[i], pearls[i + 1])
                        drawCircle(beadFillColor, pearlDrawRadius, c)
                        drawCircle(pearlOutline, pearlDrawRadius, c, style = pearlStyle)
                        i += 2
                    }
                }
            }
    ) {
        // Same default text color a Material Surface would give, so dialogs moved here from
        // a plain Material Surface keep the look of any Text that doesn't set its own color.
        CompositionLocalProvider(LocalContentColor provides contentColorFor(containerColor)) {
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .graphicsLayer { clip = false },
                content = content
            )
        }

        if (showCornerBows) {
            for (spec in CornerBows) {
                PixelRibbonBow(
                    modifier = Modifier
                        .align(spec.alignment)
                        .offset(x = spec.offsetX, y = spec.offsetY)
                        .graphicsLayer { rotationZ = spec.rotationZ },
                    width = bowWidth,
                    height = bowHeight
                )
            }
        }
    }
}

/** The bow bitmap's tails hang downward; each rotation points them toward the card centre. */
private class CornerBowSpec(
    val alignment: Alignment,
    val rotationZ: Float,
    val offsetX: Dp,
    val offsetY: Dp
)

private val CornerBows = arrayOf(
    CornerBowSpec(Alignment.TopStart, -45f, 2.dp, 2.dp),
    CornerBowSpec(Alignment.TopEnd, 45f, (-2).dp, 2.dp),
    CornerBowSpec(Alignment.BottomStart, -135f, 2.dp, (-2).dp),
    CornerBowSpec(Alignment.BottomEnd, 135f, (-2).dp, (-2).dp)
)

/**
 * Walks the perimeter of a rounded rectangle (inset by [inset] inside a [width] x [height]
 * box) clockwise from the start of the top edge. [at] writes the point and its outward unit
 * normal into a caller-owned array, so callers can sample many points without allocating.
 * Only used while building the frame geometry in drawWithCache, never per frame.
 * Internal (not private) so unit tests can check the perimeter math.
 */
internal class RoundRectWalker(inset: Float, width: Float, height: Float, radius: Float) {
    private val left = inset
    private val top = inset
    private val right = width - inset
    private val bottom = height - inset
    private val r = radius.coerceIn(0f, min(right - left, bottom - top).coerceAtLeast(0f) / 2f)
    private val edgeW = (right - left - 2f * r).coerceAtLeast(0f)
    private val edgeH = (bottom - top - 2f * r).coerceAtLeast(0f)
    private val arc = (PI.toFloat() / 2f) * r
    val length = 2f * edgeW + 2f * edgeH + 4f * arc

    /** out = [x, y, normalX, normalY] at distance [s] along the perimeter. */
    fun at(s: Float, out: FloatArray) {
        var d = s.coerceIn(0f, length)
        // Top edge
        if (d <= edgeW) return line(left + r + d, top, 0f, -1f, out)
        d -= edgeW
        if (d <= arc) return corner(right - r, top + r, -90f, d, out)
        d -= arc
        // Right edge
        if (d <= edgeH) return line(right, top + r + d, 1f, 0f, out)
        d -= edgeH
        if (d <= arc) return corner(right - r, bottom - r, 0f, d, out)
        d -= arc
        // Bottom edge
        if (d <= edgeW) return line(right - r - d, bottom, 0f, 1f, out)
        d -= edgeW
        if (d <= arc) return corner(left + r, bottom - r, 90f, d, out)
        d -= arc
        // Left edge
        if (d <= edgeH) return line(left, bottom - r - d, -1f, 0f, out)
        d -= edgeH
        corner(left + r, top + r, 180f, d.coerceAtMost(arc), out)
    }

    private fun line(x: Float, y: Float, nx: Float, ny: Float, out: FloatArray) {
        out[0] = x; out[1] = y; out[2] = nx; out[3] = ny
    }

    private fun corner(cx: Float, cy: Float, startDeg: Float, d: Float, out: FloatArray) {
        val sweep = if (r > 0f) d / r else 0f
        val a = startDeg * (PI.toFloat() / 180f) + sweep
        val nx = cos(a)
        val ny = sin(a)
        line(cx + nx * r, cy + ny * r, nx, ny, out)
    }
}
