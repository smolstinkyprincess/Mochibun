package com.crinklecodone.mochibun.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crinklecodone.mochibun.ui.dialogs.PixelRibbonBow
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import kotlin.math.roundToInt

/**
 * Keitai lace modal frame, from the outside in: a scalloped lace trim with one eyelet per lobe,
 * a pearl-bead ribbon, an inset rounded card, and pixel-art bows pinned at 45° on the four
 * corners. Everything is drawn with plain Canvas calls (no shaders, no shadows); paths and
 * bead/eyelet positions are built once per size in drawWithCache, so drawing allocates nothing.
 *
 * Content is inset by [contentPadding] and kept unclipped so softGlowFocus halos stay intact.
 * Apply curtainUnfurl to [modifier]: the lace, beads and bow knots sit inside the measured
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
    borderColor: Color = ActiveTheme.cardBorder,
    beadFillColor: Color = Color.White,
    showCornerBows: Boolean = true,
    bowWidth: Dp = 28.dp,
    bowHeight: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .drawWithCache {
                val laceStroke = 1.2.dp.toPx()
                val beadStroke = 1.dp.toPx()
                val cardStroke = 1.5.dp.toPx()
                val eyeletRadius = 1.5.dp.toPx()
                val depth = scallopDepth.toPx()
                val pitch = depth * 2f
                val laceHalf = laceStroke / 2f
                // Lobes bulge outward from a baseline rectangle inset by the scallop depth. The
                // bulge is shortened by half the stroke so the outline stays inside the bounds.
                val bulge = depth - laceHalf
                val bx0 = depth
                val by0 = depth
                val bx1 = size.width - depth
                val by1 = size.height - depth
                val lobesX = ((bx1 - bx0) / pitch).roundToInt().coerceAtLeast(1)
                val lobesY = ((by1 - by0) / pitch).roundToInt().coerceAtLeast(1)
                val segW = (bx1 - bx0) / lobesX
                val segH = (by1 - by0) / lobesY

                val lacePath = Path()
                val eyelets = ArrayList<Offset>((lobesX + lobesY) * 2)
                lacePath.moveTo(bx0, by0)
                for (i in 0 until lobesX) {
                    val x = bx0 + segW * i
                    lacePath.arcTo(Rect(x, by0 - bulge, x + segW, by0 + bulge), 180f, 180f, false)
                    eyelets += Offset(x + segW / 2f, by0)
                }
                for (j in 0 until lobesY) {
                    val y = by0 + segH * j
                    lacePath.arcTo(Rect(bx1 - bulge, y, bx1 + bulge, y + segH), 270f, 180f, false)
                    eyelets += Offset(bx1, y + segH / 2f)
                }
                for (i in 0 until lobesX) {
                    val x = bx1 - segW * i
                    lacePath.arcTo(Rect(x - segW, by1 - bulge, x, by1 + bulge), 0f, 180f, false)
                    eyelets += Offset(x - segW / 2f, by1)
                }
                for (j in 0 until lobesY) {
                    val y = by1 - segH * j
                    lacePath.arcTo(Rect(bx0 - bulge, y - segH, bx0 + bulge, y), 90f, 180f, false)
                    eyelets += Offset(bx0, y - segH / 2f)
                }
                lacePath.close()

                // Pearl ribbon: bead centres run along a rectangle 3dp inside the lace baseline.
                // Integer step counts per edge keep spacing uniform, and corner beads are shared
                // so seams never overlap.
                val beadRadius = beadDiameter.toPx() / 2f
                val drawRadius = beadRadius - beadStroke / 2f
                val beadInset = depth + 3.dp.toPx() + beadRadius
                val x0 = beadInset
                val y0 = beadInset
                val x1 = size.width - beadInset
                val y1 = size.height - beadInset
                val beadPitch = beadDiameter.toPx() * 1.15f
                val stepsX = ((x1 - x0) / beadPitch).roundToInt().coerceAtLeast(1)
                val stepsY = ((y1 - y0) / beadPitch).roundToInt().coerceAtLeast(1)
                val beads = ArrayList<Offset>((stepsX + stepsY) * 2)
                for (i in 0..stepsX) {
                    val x = x0 + (x1 - x0) * i / stepsX
                    beads += Offset(x, y0)
                    beads += Offset(x, y1)
                }
                for (j in 1 until stepsY) {
                    val y = y0 + (y1 - y0) * j / stepsY
                    beads += Offset(x0, y)
                    beads += Offset(x1, y)
                }

                // Inner card sits just inside the pearl ribbon.
                val inset = beadInset + beadRadius + 1.dp.toPx()
                val corner = cardCornerRadius.toPx()
                val cardTopLeft = Offset(inset, inset)
                val cardSize = Size(size.width - inset * 2, size.height - inset * 2)
                val half = cardStroke / 2f
                val strokeTopLeft = Offset(inset + half, inset + half)
                val strokeSize = Size(cardSize.width - cardStroke, cardSize.height - cardStroke)
                val cardRadius = CornerRadius(corner)
                val cardStrokeRadius = CornerRadius((corner - half).coerceAtLeast(0f))

                val laceStyle = Stroke(width = laceStroke)
                val beadStyle = Stroke(width = beadStroke)
                val cardStrokeStyle = Stroke(width = cardStroke)

                onDrawBehind {
                    drawPath(lacePath, containerColor)
                    drawPath(lacePath, borderColor, style = laceStyle)
                    for (c in eyelets) {
                        drawCircle(containerColor, eyeletRadius, c)
                        drawCircle(borderColor, eyeletRadius, c, style = beadStyle)
                    }
                    for (c in beads) {
                        drawCircle(beadFillColor, drawRadius, c)
                        drawCircle(borderColor, drawRadius, c, style = beadStyle)
                    }
                    drawRoundRect(containerColor, cardTopLeft, cardSize, cardRadius)
                    drawRoundRect(
                        borderColor, strokeTopLeft, strokeSize, cardStrokeRadius,
                        style = cardStrokeStyle
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .graphicsLayer { clip = false },
            content = content
        )

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
