package one.crinklecod.mochibun.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import one.crinklecod.mochibun.core.widget.DecoStyle
import one.crinklecod.mochibun.core.widget.StickerKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import one.crinklecod.mochibun.ui.theme.ActiveTheme

// Scrapbook decorations for the freeform Widgets canvas. Everything here is static and vector:
// drawn once per size/colour through drawWithCache (paths and strokes built in the cache block,
// never per frame), no animation, no bitmaps and no blur, so it stays crisp at 1.5x density.

/**
 * Washi tape, corner tape or a puffy die-cut sticker for a card. Sits in the card's slot wrapper
 * and draws past its edges ([graphicsLayer] with `clip = false`), so nothing is sliced at the
 * card border.
 *
 * [topInset] / [sideInset] move the anchor in to where the card really starts (Now Playing
 * leaves room above for its mascot pin; the Photo Frame is narrower than its slot).
 * [pinAtEnd] forces a top corner for cards with one corner already busy (true = top-right is
 * free, false = top-left is free); null lets the style choose (bow top-right, heart top-left,
 * corner tape top-left + bottom-right).
 */
@Composable
fun BoxScope.WidgetDecoOverlay(
    deco: DecoStyle,
    pinAtEnd: Boolean? = null,
    topInset: Dp = 0.dp,
    sideInset: Dp = 0.dp
) {
    when (deco) {
        DecoStyle.NONE -> Unit
        DecoStyle.WASHI_TAPE -> WashiTape(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .offset(y = topInset - 9.dp)
                .size(width = 68.dp, height = 18.dp)
                // A slight tilt reads as hand-placed tape; static, so it costs nothing per frame
                .graphicsLayer {
                    clip = false
                    rotationZ = -4f
                }
        )
        DecoStyle.CORNER_TAPE -> {
            // Polaroid-style strips across two opposite corners. Default top-left + bottom-right;
            // flips to top-right + bottom-left when only the top-right corner is free.
            val topEnd = pinAtEnd ?: false
            val halfLen = CornerTapeLength / 2
            val halfThick = CornerTapeThickness / 2
            val tuck = 7.dp // how far the strip centre sits inside the corner
            CornerTapeStrip(
                modifier = Modifier
                    .align(if (topEnd) Alignment.TopEnd else Alignment.TopStart)
                    .padding(top = topInset, start = sideInset, end = sideInset)
                    .offset(
                        x = if (topEnd) halfLen - tuck else tuck - halfLen,
                        y = tuck - halfThick
                    ),
                rotation = if (topEnd) 43f else -43f
            )
            CornerTapeStrip(
                modifier = Modifier
                    .align(if (topEnd) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(start = sideInset, end = sideInset)
                    .offset(
                        x = if (topEnd) tuck - halfLen else halfLen - tuck,
                        y = halfThick - tuck
                    ),
                rotation = if (topEnd) 47f else -47f
            )
        }
        DecoStyle.CORNER_BOW, DecoStyle.CORNER_HEART -> {
            val atEnd = pinAtEnd ?: (deco == DecoStyle.CORNER_BOW)
            val pinModifier = Modifier
                .align(if (atEnd) Alignment.TopEnd else Alignment.TopStart)
                .zIndex(1f)
                .padding(top = topInset, start = sideInset, end = sideInset)
                .offset(x = if (atEnd) 10.dp else (-10).dp, y = (-12).dp)
                .graphicsLayer { clip = false }
            if (deco == DecoStyle.CORNER_BOW) {
                PuffyBowSticker(modifier = pinModifier.size(width = 38.dp, height = 32.dp))
            } else {
                PuffyHeartSticker(modifier = pinModifier.size(28.dp))
            }
        }
    }
}

private val CornerTapeLength = 46.dp
private val CornerTapeThickness = 15.dp

@Composable
private fun BoxScope.CornerTapeStrip(modifier: Modifier, rotation: Float) {
    WashiTape(
        modifier = modifier
            .zIndex(1f)
            .size(width = CornerTapeLength, height = CornerTapeThickness)
            .graphicsLayer {
                clip = false
                rotationZ = rotation
            }
    )
}

// ── Puffy die-cut stickers ──────────────────────────────────────────

private val DieCutWhite = Color(0xFFFFFFFF)
private val DieCutBacking = 2.75.dp // white vinyl margin around the art
private val DieCutRim = 0.75.dp // faint edge so the white backing still reads on cream cards
private val StickerOutline = 1.2.dp

/**
 * Shared die-cut renderer: a white vinyl silhouette (with a faint rim) around [silhouette], then
 * each pastel [fills] layer with a crisp outline, then [details] (creases) and white [glints].
 */
private class DieCutArt(
    val silhouette: Path,
    val fills: List<Pair<Path, Color>>,
    val details: List<Path> = emptyList(),
    val glints: List<Path> = emptyList(),
    val glintDots: List<Pair<Offset, Float>> = emptyList()
)

private fun Modifier.dieCutSticker(
    outlineColor: Color,
    build: (pad: Float, w: Float, h: Float) -> DieCutArt
): Modifier = drawWithCache {
    val backing = DieCutBacking.toPx()
    val rim = DieCutRim.toPx()
    val pad = backing + rim
    val art = build(pad, size.width - 2 * pad, size.height - 2 * pad)
    val rimStroke = Stroke(width = 2 * (backing + rim), join = StrokeJoin.Round, cap = StrokeCap.Round)
    val backingStroke = Stroke(width = 2 * backing, join = StrokeJoin.Round, cap = StrokeCap.Round)
    val outline = Stroke(width = StickerOutline.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
    val crease = Stroke(width = 0.9.dp.toPx(), cap = StrokeCap.Round)
    val glint = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
    val rimColor = outlineColor.copy(alpha = 0.22f)
    val creaseColor = outlineColor.copy(alpha = 0.45f)
    onDrawBehind {
        drawPath(art.silhouette, rimColor, style = rimStroke)
        drawPath(art.silhouette, DieCutWhite, style = backingStroke)
        drawPath(art.silhouette, DieCutWhite)
        for ((path, color) in art.fills) {
            drawPath(path, color)
            drawPath(path, outlineColor, style = outline)
        }
        for (path in art.details) drawPath(path, creaseColor, style = crease)
        for (path in art.glints) drawPath(path, DieCutWhite, style = glint)
        for ((center, radius) in art.glintDots) drawCircle(DieCutWhite, radius, center)
    }
}

// ── Standalone canvas stickers ──────────────────────────────────────

/** Butter-yellow pastel for the star and the smallest sparkle; reads on every theme. */
private val PastelButter = Color(0xFFFFE8A3)

/**
 * One standalone scrapbook sticker filling [modifier]'s (square) box: a die-cut puffy vector, or
 * a 1-bit Docomo pictogram integer-scaled onto a white die-cut backing.
 */
@Composable
fun ScrapbookSticker(kind: StickerKind, modifier: Modifier = Modifier) {
    when (kind) {
        StickerKind.PUFFY_BOW -> PuffyBowSticker(modifier = modifier.padding(vertical = 3.dp))
        StickerKind.PUFFY_HEART -> PuffyHeartSticker(modifier = modifier)
        StickerKind.SPARKLES -> SparkleClusterSticker(modifier = modifier)
        StickerKind.PASTEL_STAR -> PastelStarSticker(modifier = modifier)
        StickerKind.RIBBON -> RibbonBannerSticker(modifier = modifier)
        else -> DieCutPictogram(
            index = kind.docomoIndex ?: 0,
            tint = lerp(ActiveTheme.primary, ActiveTheme.accentDeep, 0.55f),
            rimColor = ActiveTheme.accentDeep.copy(alpha = 0.22f),
            modifier = modifier
        )
    }
}

/** Four-point sparkle centred on ([cx], [cy]) with radius [r], its sides pinched toward the middle. */
private fun sparklePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val pinch = 0.14f * r
    moveTo(cx, cy - r)
    quadraticTo(cx + pinch, cy - pinch, cx + r, cy)
    quadraticTo(cx + pinch, cy + pinch, cx, cy + r)
    quadraticTo(cx - pinch, cy + pinch, cx - r, cy)
    quadraticTo(cx - pinch, cy - pinch, cx, cy - r)
    close()
}

/** One big and two small sparkles on a shared white die-cut. */
@Composable
private fun SparkleClusterSticker(modifier: Modifier = Modifier) {
    val bigColor = ActiveTheme.primary
    val smallColor = ActiveTheme.palette.squircleSecondary
    val outlineColor = ActiveTheme.accentDeep
    Box(
        modifier = modifier.dieCutSticker(outlineColor) { pad, w, h ->
            fun x(u: Float) = pad + u * w
            fun y(v: Float) = pad + v * h
            val big = sparklePath(x(0.40f), y(0.58f), 0.40f * w)
            val small = sparklePath(x(0.80f), y(0.22f), 0.20f * w)
            val tiny = sparklePath(x(0.84f), y(0.80f), 0.14f * w)
            DieCutArt(
                silhouette = Path().apply { listOf(big, small, tiny).forEach { addPath(it) } },
                fills = listOf(big to bigColor, small to smallColor, tiny to PastelButter),
                glints = listOf(
                    Path().apply {
                        moveTo(x(0.34f), y(0.42f))
                        quadraticTo(x(0.37f), y(0.50f), x(0.30f), y(0.55f))
                    }
                )
            )
        }
    )
}

/** Plump five-point star in pastel butter; the round outline join softens its tips. */
@Composable
private fun PastelStarSticker(modifier: Modifier = Modifier) {
    val outlineColor = ActiveTheme.accentDeep
    Box(
        modifier = modifier.dieCutSticker(outlineColor) { pad, w, h ->
            val cx = pad + 0.5f * w
            val cy = pad + 0.54f * h
            val outer = 0.5f * min(w, h)
            val inner = outer * 0.52f
            val star = Path().apply {
                for (i in 0 until 10) {
                    val radius = if (i % 2 == 0) outer else inner
                    val angle = (-90f + i * 36f) * (PI.toFloat() / 180f)
                    val px = cx + radius * cos(angle)
                    val py = cy + radius * sin(angle)
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            DieCutArt(
                silhouette = star,
                fills = listOf(star to PastelButter),
                glints = listOf(
                    Path().apply {
                        moveTo(cx - 0.30f * outer, cy - 0.02f * outer)
                        quadraticTo(cx - 0.30f * outer, cy - 0.28f * outer, cx - 0.08f * outer, cy - 0.36f * outer)
                    }
                ),
                glintDots = listOf(Offset(cx + 0.22f * outer, cy + 0.12f * outer) to 0.05f * outer)
            )
        }
    )
}

/** A wavy scrapbook ribbon banner with V-notched ends and a centre fold line. */
@Composable
private fun RibbonBannerSticker(modifier: Modifier = Modifier) {
    val fillColor = ActiveTheme.primary
    val outlineColor = ActiveTheme.accentDeep
    Box(
        modifier = modifier.dieCutSticker(outlineColor) { pad, w, h ->
            fun x(u: Float) = pad + u * w
            fun y(v: Float) = pad + v * h
            val band = Path().apply {
                moveTo(x(0.0f), y(0.36f))
                cubicTo(x(0.30f), y(0.14f), x(0.70f), y(0.54f), x(1.0f), y(0.32f))
                lineTo(x(0.90f), y(0.47f)) // right notch
                lineTo(x(1.0f), y(0.62f))
                cubicTo(x(0.70f), y(0.84f), x(0.30f), y(0.44f), x(0.0f), y(0.66f))
                lineTo(x(0.10f), y(0.51f)) // left notch
                close()
            }
            val fold = Path().apply {
                moveTo(x(0.16f), y(0.47f))
                cubicTo(x(0.36f), y(0.33f), x(0.64f), y(0.65f), x(0.84f), y(0.49f))
            }
            val glint = Path().apply {
                moveTo(x(0.14f), y(0.37f))
                quadraticTo(x(0.24f), y(0.29f), x(0.34f), y(0.29f))
            }
            DieCutArt(
                silhouette = band,
                fills = listOf(band to fillColor),
                details = listOf(fold),
                glints = listOf(glint)
            )
        }
    )
}

/** Puffy ribbon bow: two rounded loops, notched tails and a plump knot on white vinyl. */
@Composable
private fun PuffyBowSticker(modifier: Modifier = Modifier) {
    val loopColor = ActiveTheme.primary
    val tailColor = ActiveTheme.palette.squircleSecondary
    val outlineColor = ActiveTheme.accentDeep
    Box(
        modifier = modifier.dieCutSticker(outlineColor) { pad, w, h ->
            fun x(u: Float, mirror: Boolean = false) = pad + (if (mirror) 1f - u else u) * w
            fun y(v: Float) = pad + v * h
            fun loop(m: Boolean) = Path().apply {
                moveTo(x(0.5f, m), y(0.44f))
                cubicTo(x(0.36f, m), y(0.06f), x(0.0f, m), y(0.02f), x(0.02f, m), y(0.38f))
                cubicTo(x(0.04f, m), y(0.72f), x(0.34f, m), y(0.72f), x(0.5f, m), y(0.44f))
                close()
            }
            fun tail(m: Boolean) = Path().apply {
                moveTo(x(0.45f, m), y(0.50f))
                lineTo(x(0.20f, m), y(0.92f))
                lineTo(x(0.31f, m), y(0.86f)) // the notch
                lineTo(x(0.36f, m), y(1.0f))
                lineTo(x(0.57f, m), y(0.56f))
                close()
            }
            fun crease(m: Boolean) = Path().apply {
                moveTo(x(0.40f, m), y(0.42f))
                quadraticTo(x(0.28f, m), y(0.34f), x(0.18f, m), y(0.38f))
            }
            fun glint(m: Boolean) = Path().apply {
                moveTo(x(0.10f, m), y(0.38f))
                quadraticTo(x(0.11f, m), y(0.20f), x(0.24f, m), y(0.17f))
            }
            val knot = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = x(0.39f), top = y(0.30f), right = x(0.61f), bottom = y(0.60f),
                        cornerRadius = CornerRadius(0.10f * w, 0.12f * h)
                    )
                )
            }
            val tails = listOf(tail(false), tail(true))
            val loops = listOf(loop(false), loop(true))
            val silhouette = Path().apply {
                (tails + loops + knot).forEach { addPath(it) }
            }
            DieCutArt(
                silhouette = silhouette,
                fills = tails.map { it to tailColor } + loops.map { it to loopColor } + (knot to loopColor),
                details = listOf(crease(false), crease(true)),
                glints = listOf(glint(false), glint(true)),
                glintDots = listOf(Offset(x(0.46f), y(0.38f)) to 0.11f * (x(0.61f) - x(0.39f)))
            )
        }
    )
}

/** Plump symmetric Bezier heart on white vinyl with a curved specular glint on the left lobe. */
@Composable
private fun PuffyHeartSticker(modifier: Modifier = Modifier) {
    val fillColor = ActiveTheme.primary
    val outlineColor = ActiveTheme.accentDeep
    Box(
        modifier = modifier.dieCutSticker(outlineColor) { pad, w, h ->
            fun x(u: Float) = pad + u * w
            fun y(v: Float) = pad + v * h
            val heart = Path().apply {
                moveTo(x(0.5f), y(0.96f))
                cubicTo(x(0.20f), y(0.76f), x(0.0f), y(0.58f), x(0.02f), y(0.34f))
                cubicTo(x(0.04f), y(0.08f), x(0.36f), y(0.0f), x(0.5f), y(0.24f))
                cubicTo(x(0.64f), y(0.0f), x(0.96f), y(0.08f), x(0.98f), y(0.34f))
                cubicTo(x(1.0f), y(0.58f), x(0.80f), y(0.76f), x(0.5f), y(0.96f))
                close()
            }
            val glint = Path().apply {
                moveTo(x(0.15f), y(0.42f))
                quadraticTo(x(0.14f), y(0.22f), x(0.30f), y(0.18f))
            }
            DieCutArt(
                silhouette = heart,
                fills = listOf(heart to fillColor),
                glints = listOf(glint),
                glintDots = listOf(Offset(x(0.15f), y(0.52f)) to 0.035f * w)
            )
        }
    )
}

/** Translucent pastel tape strip (shared by [DecoStyle.WASHI_TAPE] and [DecoStyle.CORNER_TAPE]) with zigzag torn ends and diagonal stripes. */
@Composable
private fun WashiTape(modifier: Modifier = Modifier) {
    val tapeColor = ActiveTheme.primary.copy(alpha = 0.62f)
    val stripeColor = ActiveTheme.surface.copy(alpha = 0.55f)
    val edgeColor = ActiveTheme.cardBorder.copy(alpha = 0.35f)
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val tooth = h / 6f
            val notch = 3.dp.toPx()
            // Zigzag left/right ends, straight top and bottom, like torn washi
            val tape = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                var y = 0f
                var out = true
                while (y < h) {
                    y = (y + tooth).coerceAtMost(h)
                    lineTo(if (out) w - notch else w, y)
                    out = !out
                }
                lineTo(0f, h)
                y = h
                out = true
                while (y > 0f) {
                    y = (y - tooth).coerceAtLeast(0f)
                    lineTo(if (out) notch else 0f, y)
                    out = !out
                }
                close()
            }
            val stripeGap = 7.dp.toPx()
            val stripeWidth = 2.5.dp.toPx()
            val edgeStroke = Stroke(width = 0.75.dp.toPx())
            onDrawBehind {
                drawPath(tape, tapeColor)
                clipPath(tape) {
                    var x = -h
                    while (x < w) {
                        drawLine(stripeColor, Offset(x, h), Offset(x + h, 0f), strokeWidth = stripeWidth)
                        x += stripeGap
                    }
                }
                drawPath(tape, edgeColor, style = edgeStroke)
            }
        }
    )
}

/**
 * Arrange-mode selection: a dashed "stitched" outline around the slot, with a `◢` grip in the
 * bottom-right corner while resizing (cards grow and shrink from that corner).
 */
@Composable
fun ArrangeStitchFrame(showResizeHandle: Boolean, modifier: Modifier = Modifier) {
    val stitchColor = ActiveTheme.palette.focusedCardBorder
    val fillColor = stitchColor.copy(alpha = 0.08f)
    val handleColor = ActiveTheme.accentDeep
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .drawWithCache {
                val inset = 1.5.dp.toPx()
                val radius = 22.dp.toPx()
                val outline = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = inset,
                            top = inset,
                            right = size.width - inset,
                            bottom = size.height - inset,
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    )
                }
                val stitch = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                )
                val grip = 12.dp.toPx()
                val handle = Path().apply {
                    moveTo(size.width, size.height - grip)
                    lineTo(size.width, size.height)
                    lineTo(size.width - grip, size.height)
                    close()
                }
                onDrawBehind {
                    drawPath(outline, fillColor)
                    drawPath(outline, stitchColor, style = stitch)
                    if (showResizeHandle) drawPath(handle, handleColor)
                }
            }
    )
}
