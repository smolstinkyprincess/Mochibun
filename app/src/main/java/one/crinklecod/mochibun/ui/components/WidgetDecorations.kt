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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import one.crinklecod.mochibun.core.widget.DecoStyle
import one.crinklecod.mochibun.ui.theme.ActiveTheme

// Scrapbook decorations for the freeform Widgets canvas. Everything here is static: drawn once
// per size/colour through drawWithCache (paths and strokes built in the cache block, never per
// frame), no animation, no bitmaps beyond the existing pixel stickers.

/**
 * Washi tape or a corner pin for a card. Sits in the card's slot wrapper and draws past its
 * edges ([graphicsLayer] with `clip = false`), so nothing is sliced at the card border.
 *
 * [topInset] / [sideInset] move the anchor in to where the card really starts (Now Playing
 * leaves room above for its mascot pin; the Photo Frame is narrower than its slot).
 * [pinAtEnd] forces a corner for pins on cards with one corner already busy; null lets the
 * style choose (bow top-right, heart top-left).
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
        DecoStyle.CORNER_BOW, DecoStyle.CORNER_HEART -> {
            val atEnd = pinAtEnd ?: (deco == DecoStyle.CORNER_BOW)
            val pinModifier = Modifier
                .align(if (atEnd) Alignment.TopEnd else Alignment.TopStart)
                .zIndex(1f)
                .padding(top = topInset, start = sideInset, end = sideInset)
                .offset(x = if (atEnd) 10.dp else (-10).dp, y = (-12).dp)
                .graphicsLayer { clip = false }
            if (deco == DecoStyle.CORNER_BOW) {
                // Axis-aligned on purpose: a rotated pixel sprite loses its crisp 1:1 pixel clusters
                BowSticker(size = 36.dp, color = ActiveTheme.primary, modifier = pinModifier)
            } else {
                HeartSticker(size = 26.dp, color = ActiveTheme.primary, modifier = pinModifier)
            }
        }
    }
}

/** Translucent pastel tape strip with zigzag torn ends and diagonal stripes. */
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
