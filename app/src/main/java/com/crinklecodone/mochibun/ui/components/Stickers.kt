/**
 * Procedural pixel-art sticker and explosion effect engine for Mochibun.
 * Uses run-length encoded canvas drawing routines instead of bitmaps, maintaining 1:1 pixel
 * sharpness at 480x800 and bypassing the MediaTek MT6761's GPU shader overhead.
 */
package com.crinklecodone.mochibun.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ============================================================================
// Mochibun Pixel Art Sticker Engine & Retro Arcade Explosion
// ============================================================================

// a horizontal run of same-colored pixels for fast, seam-free Canvas rendering
private class StickerPixelRun(val x: Int, val y: Int, val length: Int, val color: Color)

// color extension helpers for authentic retro pixel-art shading & highlights
private fun Color.outlineTone(): Color {
    // deep plum/berry tint (0xFF482650) to give pixel outlines rich retro depth without muddiness
    val plum = Color(0xFF482650)
    return Color(
        red = (this.red * 0.30f + plum.red * 0.70f).coerceIn(0f, 1f),
        green = (this.green * 0.30f + plum.green * 0.70f).coerceIn(0f, 1f),
        blue = (this.blue * 0.30f + plum.blue * 0.70f).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}

private fun Color.darker(factor: Float): Color = Color(
    red = (this.red * factor).coerceIn(0f, 1f),
    green = (this.green * factor).coerceIn(0f, 1f),
    blue = (this.blue * factor).coerceIn(0f, 1f),
    alpha = this.alpha
)

private fun Color.lighter(factor: Float): Color = Color(
    red = (this.red + (1f - this.red) * factor).coerceIn(0f, 1f),
    green = (this.green + (1f - this.green) * factor).coerceIn(0f, 1f),
    blue = (this.blue + (1f - this.blue) * factor).coerceIn(0f, 1f),
    alpha = this.alpha
)

// converts ASCII sprite rows into merged horizontal pixel runs
private fun runsFromRows(rows: List<String>, palette: (Char) -> Color?): List<StickerPixelRun> {
    val runs = ArrayList<StickerPixelRun>(rows.size * 4)
    rows.forEachIndexed { rowIndex, row ->
        var x = 0
        while (x < row.length) {
            val ch = row[x]
            val color = palette(ch)
            if (color == null) {
                x++
            } else {
                val start = x
                while (x < row.length && row[x] == ch) x++
                runs.add(StickerPixelRun(start, rowIndex, x - start, color))
            }
        }
    }
    return runs
}

// draws pixel runs with integer-snapped edges so pixels remain razor-sharp with zero gaps
private fun DrawScope.drawPixelSprite(
    runs: List<StickerPixelRun>,
    gridW: Int,
    gridH: Int
) {
    val cell = minOf(this.size.width / gridW, this.size.height / gridH).coerceAtLeast(1f)
    val left = ((this.size.width - cell * gridW) / 2f).toInt().toFloat()
    val top = ((this.size.height - cell * gridH) / 2f).toInt().toFloat()

    runs.forEach { run ->
        val rx = (left + run.x * cell).toInt().toFloat()
        val ry = (top + run.y * cell).toInt().toFloat()
        val rw = ((run.x + run.length) * cell).toInt().toFloat() - (run.x * cell).toInt().toFloat()
        val rh = ((run.y + 1) * cell).toInt().toFloat() - (run.y * cell).toInt().toFloat()

        drawRect(
            color = run.color,
            topLeft = Offset(rx, ry),
            size = Size(rw, rh)
        )
    }
}

// ============================================================================
// Sticker Sprite Definitions (Genuine Pixel Art with Die-Cut White Borders)
// ============================================================================

// 1. Heart: 16 x 16 grid (Directly inspired by reference pixel heart: clean silhouette, volume highlight & shadow)
private val HEART_ROWS = listOf(
    "..BBBB....BBBB..",
    ".BOOOOB..BOOOOB.",
    "BOLHPOB..BOPSSOB",
    "BOLHHPPOOPPPSSOB",
    "BOLHHPPOPPPPPSOB",
    "BOLHPOPPPPPPPSOB",
    ".BOLHPPPPPPPSOB.",
    ".BOPPPPPPPPPSOB.",
    "..BOPPPPPPPSOB..",
    "..BOPPPPPPSSOB..",
    "...BOPPPPSSOB...",
    "....BOPPSSOB....",
    ".....BOPSOB.....",
    "......BOOB......",
    ".......BB.......",
    "................"
)

// 2. Ribbon Bow: 20 x 16 grid (Directly inspired by reference pixel bows: puffy loops, distinct center knot, notched flaring tails)
private val BOW_ROWS = listOf(
    "..BBBBB......BBBBB..",
    ".BOOOOOB....BOOOOOB.",
    "BOPLHPOB....BOPLHPOB",
    "BOPPPOB.BBBB.BOPPPOB",
    "BOPPPOBBOOOOBBOPPPOB",
    "BOPPSOBBOHHOBBOPSSOB",
    ".BOOSSOBOPPOBOSSOOB.",
    "..BOOOBBOKKOBBOOOB..",
    "...BBOBBOOOOBBOBB...",
    "..BBOPOBBOOBOPOBB...",
    ".BOPPPOB....BOPPPOB.",
    "BOPPPSOB....BOPPPSOB",
    "BOPOBOB......BOPOBOB",
    ".BBOOB........BOOBB.",
    "..BB............BB..",
    "...................."
)

// 3. Sparkle: 15 x 15 grid (Perfect 4-way radial symmetry, crisp 1-px contours, brilliant central diamond gleam)
private val SPARKLE_ROWS = listOf(
    ".......B.......",
    "......BOB......",
    "......BOB......",
    ".....BOPOB.....",
    ".....BOPOB.....",
    "....BOLPLOB....",
    ".BOPLPOHOPLPOB.",
    "BOPPPLLHLLPPPOB",
    ".BOPLPOHOPLPOB.",
    "....BOLPLOB....",
    ".....BOPOB.....",
    ".....BOPOB.....",
    "......BOB......",
    "......BOB......",
    ".......B......."
)

// 4. Cloud: 24 x 14 grid (Directly inspired by reference pixel clouds: 3 puffy lobes, volumetric shading tucked under lobes)
private val CLOUD_ROWS = listOf(
    "..........BBBBB.........",
    ".........BOOOOOB........",
    "...BBBB.BOOHHPPOB.BBBB..",
    "..BOOOOBBOPPPPPOBBOOOOB.",
    ".BOOHPPOBOOOPPPPOBOOHPOB",
    "BOPPPPOPPPPPPPPPOPPPPPOB",
    "BOPPPSSOPPPPPPSSOPPPPPOB",
    "BOOPPPPPPPPPPPPPPPPPPPPOB",
    ".BOPPPPPPPPPPPPPPPPPPPPOB",
    ".BOPPPPPPPPPPPPPPPSSSSOB",
    "..BOSSSSSSSSSSSSSSSSSOB.",
    "...BOOOOOOOOOOOOOOOOB...",
    "....BBBBBBBBBBBBBBBB....",
    "........................"
)

// 5. Flower: 17 x 17 grid (Directly inspired by reference pixel flower: 5 rounded petals, outer specular gleams, golden stamen core)
private val FLOWER_ROWS = listOf(
    "......BBBBB......",
    ".....BOOOOOB.....",
    "....BOPLHPPOB....",
    "...BOOPPPPPOOB...",
    ".BBBOOPPPPPOOBBB.",
    ".BOOPPPPOPPPPOOB.",
    "BOOPLHPOOOPHLPOOB",
    "BOPPPPOOCYCOOPPPPOB",
    "BOPPPPOOKYKOOPPPPOB",
    "BOPPPPOOCYCOOPPPPOB",
    "BOPPOOOOOOOOOPOOB",
    "..BOOPPPPPPPPPOOB..",
    ".BBOPLHPPPLHPOBB.",
    "..BOOPPPPPPPPPOOB..",
    "...BBOOPPPPPOOBB...",
    ".....BBOOOOBB.....",
    "......BBBBB......"
)

// 6. Mini Accents (Pixel mini heart, sparkle, diamond charms)
private val MINI_HEART_ROWS = listOf(
    ".BB...BB.",
    "BOOB.BOOB",
    "BOHB.BOPB",
    "BOPPPPOPB",
    ".BOPPPOB.",
    "..BOPOB..",
    "...BOOB..",
    "....BB..."
)

private val MINI_SPARKLE_ROWS = listOf(
    "....B....",
    "...BOB...",
    "..BOPOB..",
    ".BBOPOBB.",
    "BOPLHPLOB",
    ".BBOPOBB.",
    "..BOPOB..",
    "...BOB...",
    "....B...."
)

private val MINI_DIAMOND_ROWS = listOf(
    "...B...",
    "..BOB..",
    ".BOPOB.",
    "BOPHPOB",
    ".BOPOB.",
    "..BOB..",
    "...B..."
)

// ============================================================================
// Sticker Composables (100% Backward Compatible API)
// ============================================================================

// pixel-art heart sticker with die-cut white border and highlight reflection
@Composable
fun HeartSticker(
    size: Dp = 42.dp,
    color: Color = Color(0xFFFF8FCB),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(color) {
        val outline = color.outlineTone()
        val shadow = color.darker(0.80f)
        val highlight = color.lighter(0.35f)
        runsFromRows(HEART_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> color
                'S' -> shadow
                'L' -> highlight
                'H' -> Color.White
                else -> null
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        drawPixelSprite(runs, 16, 16)
    }
}

// pixel-art ribbon bow sticker with die-cut white border and center knot
@Composable
fun BowSticker(
    size: Dp = 64.dp,
    color: Color = Color(0xFFFF9FD2),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(color) {
        val outline = color.outlineTone()
        val shadow = color.darker(0.78f)
        val knotShadow = color.darker(0.68f)
        val highlight = color.lighter(0.35f)
        runsFromRows(BOW_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> color
                'S' -> shadow
                'K' -> knotShadow
                'L' -> highlight
                'H' -> Color.White
                else -> null
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        drawPixelSprite(runs, 20, 16)
    }
}

// pixel-art 4-pointed sparkle sticker
@Composable
fun SparkleSticker(
    size: Dp = 36.dp,
    color: Color = Color(0xFFE675FF),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(color) {
        val outline = color.outlineTone()
        val highlight = color.lighter(0.35f)
        runsFromRows(SPARKLE_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> color
                'L' -> highlight
                'H' -> Color.White
                else -> null
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        drawPixelSprite(runs, 15, 15)
    }
}

// pixel-art fluffy cloud sticker
@Composable
fun CloudSticker(
    width: Dp = 82.dp,
    height: Dp = 48.dp,
    color: Color = Color(0xFFE8D5FF),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(color) {
        val outline = color.outlineTone()
        val shadow = color.darker(0.85f)
        val highlight = color.lighter(0.35f)
        runsFromRows(CLOUD_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> color
                'S' -> shadow
                'L' -> highlight
                'H' -> Color.White
                else -> null
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .rotate(rotation)
    ) {
        drawPixelSprite(runs, 24, 14)
    }
}

// pixel-art 5-petal flower sticker with contrasting center
@Composable
fun FlowerSticker(
    size: Dp = 42.dp,
    petalColor: Color = Color(0xFFFFB6DD),
    centerColor: Color = Color(0xFFE675FF),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(petalColor, centerColor) {
        val outline = petalColor.outlineTone()
        val shadow = petalColor.darker(0.80f)
        val highlight = petalColor.lighter(0.35f)
        val centerShadow = centerColor.darker(0.75f)
        val centerHighlight = centerColor.lighter(0.35f)
        runsFromRows(FLOWER_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> petalColor
                'S' -> shadow
                'L' -> highlight
                'C' -> centerColor
                'K' -> centerShadow
                'Y' -> centerHighlight
                'H' -> Color.White
                else -> null
            }
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        drawPixelSprite(runs, 17, 17)
    }
}

// mini pixel art charms replacing raw unicode glyphs for authentic retro aesthetics
@Composable
fun PixelMiniHeart(
    size: Dp = 16.dp,
    color: Color = Color(0xFFFF9FD2),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(color) {
        val outline = color.outlineTone()
        val highlight = color.lighter(0.35f)
        runsFromRows(MINI_HEART_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> color
                'L' -> highlight
                'H' -> Color.White
                else -> null
            }
        }
    }
    Canvas(modifier = modifier.size(size).rotate(rotation)) {
        drawPixelSprite(runs, 9, 8)
    }
}

@Composable
fun PixelMiniSparkle(
    size: Dp = 14.dp,
    color: Color = Color(0xFFE675FF),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(color) {
        val outline = color.outlineTone()
        val highlight = color.lighter(0.35f)
        runsFromRows(MINI_SPARKLE_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> color
                'L' -> highlight
                'H' -> Color.White
                else -> null
            }
        }
    }
    Canvas(modifier = modifier.size(size).rotate(rotation)) {
        drawPixelSprite(runs, 9, 9)
    }
}

@Composable
fun PixelMiniDiamond(
    size: Dp = 14.dp,
    color: Color = Color(0xFFD791E3),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    val runs = remember(color) {
        val outline = color.outlineTone()
        val highlight = color.lighter(0.35f)
        runsFromRows(MINI_DIAMOND_ROWS) { ch ->
            when (ch) {
                'B' -> Color.White
                'O' -> outline
                'P' -> color
                'L' -> highlight
                'H' -> Color.White
                else -> null
            }
        }
    }
    Canvas(modifier = modifier.size(size).rotate(rotation)) {
        drawPixelSprite(runs, 7, 7)
    }
}

// ============================================================================
// Retro Arcade Pixel Explosion Engine
// ============================================================================

private class ArcadeParticle(
    val angleRad: Float,
    val speedDp: Float,
    val sizeDp: Float,
    val color: Color,
    val isChunkyFragment: Boolean,
    val isStarlet: Boolean,
    val gravityDp: Float = 0f
)

// pre-allocated static pool of 42 arcade particles: zero allocations at runtime
private val ARCADE_EXPLOSION_PARTICLES: List<ArcadeParticle> = listOf(
    // 16 chunky foreground pixel fragments (2x2 clusters with highlights & shadows)
    ArcadeParticle(0.26f, 160f, 9.5f, Color(0xFFFF62AE), isChunkyFragment = true, isStarlet = false, gravityDp = 20f),
    ArcadeParticle(0.70f, 135f, 8.5f, Color(0xFFFFDE59), isChunkyFragment = true, isStarlet = false, gravityDp = 25f),
    ArcadeParticle(1.13f, 175f, 10.5f, Color(0xFFBA75FF), isChunkyFragment = true, isStarlet = false, gravityDp = 15f),
    ArcadeParticle(1.48f, 120f, 8.0f, Color(0xFF6EECDE), isChunkyFragment = true, isStarlet = false, gravityDp = 30f),
    ArcadeParticle(1.92f, 165f, 9.5f, Color(0xFFFF9FD2), isChunkyFragment = true, isStarlet = false, gravityDp = 22f),
    ArcadeParticle(2.36f, 140f, 8.5f, Color(0xFFFFFFFF), isChunkyFragment = true, isStarlet = false, gravityDp = 18f),
    ArcadeParticle(2.79f, 180f, 10.5f, Color(0xFFFF62AE), isChunkyFragment = true, isStarlet = false, gravityDp = 20f),
    ArcadeParticle(3.32f, 150f, 9.0f, Color(0xFFFFDE59), isChunkyFragment = true, isStarlet = false, gravityDp = 24f),
    ArcadeParticle(3.75f, 170f, 8.5f, Color(0xFFBA75FF), isChunkyFragment = true, isStarlet = false, gravityDp = 16f),
    ArcadeParticle(4.19f, 135f, 9.5f, Color(0xFF6EECDE), isChunkyFragment = true, isStarlet = false, gravityDp = 28f),
    ArcadeParticle(4.63f, 160f, 8.5f, Color(0xFFFF9FD2), isChunkyFragment = true, isStarlet = false, gravityDp = 22f),
    ArcadeParticle(5.06f, 145f, 10.0f, Color(0xFFFFFFFF), isChunkyFragment = true, isStarlet = false, gravityDp = 19f),
    ArcadeParticle(5.50f, 175f, 9.0f, Color(0xFFFF62AE), isChunkyFragment = true, isStarlet = false, gravityDp = 21f),
    ArcadeParticle(5.85f, 130f, 8.0f, Color(0xFFFFDE59), isChunkyFragment = true, isStarlet = false, gravityDp = 25f),
    ArcadeParticle(6.20f, 165f, 9.5f, Color(0xFFBA75FF), isChunkyFragment = true, isStarlet = false, gravityDp = 17f),
    ArcadeParticle(3.14f, 125f, 8.0f, Color(0xFF6EECDE), isChunkyFragment = true, isStarlet = false, gravityDp = 26f),

    // 20 trailing square pixel sparkles (twinkling debris trailing behind)
    ArcadeParticle(0.15f, 85f, 4.5f, Color(0xFFFFFFFF), isChunkyFragment = false, isStarlet = false, gravityDp = 10f),
    ArcadeParticle(0.45f, 65f, 3.5f, Color(0xFFFF9FD2), isChunkyFragment = false, isStarlet = false, gravityDp = 12f),
    ArcadeParticle(0.80f, 100f, 5.0f, Color(0xFFFFE066), isChunkyFragment = false, isStarlet = false, gravityDp = 8f),
    ArcadeParticle(1.05f, 75f, 4.0f, Color(0xFFD6B2FF), isChunkyFragment = false, isStarlet = false, gravityDp = 14f),
    ArcadeParticle(1.35f, 95f, 4.5f, Color(0xFFFFFFFF), isChunkyFragment = false, isStarlet = false, gravityDp = 11f),
    ArcadeParticle(1.70f, 60f, 3.5f, Color(0xFFFF62AE), isChunkyFragment = false, isStarlet = false, gravityDp = 15f),
    ArcadeParticle(2.05f, 105f, 5.0f, Color(0xFF80F0D8), isChunkyFragment = false, isStarlet = false, gravityDp = 9f),
    ArcadeParticle(2.40f, 70f, 4.0f, Color(0xFFFFFFFF), isChunkyFragment = false, isStarlet = false, gravityDp = 13f),
    ArcadeParticle(2.70f, 90f, 4.5f, Color(0xFFFF9FD2), isChunkyFragment = false, isStarlet = false, gravityDp = 10f),
    ArcadeParticle(3.00f, 65f, 3.5f, Color(0xFFFFE066), isChunkyFragment = false, isStarlet = false, gravityDp = 14f),
    ArcadeParticle(3.40f, 100f, 5.0f, Color(0xFFD6B2FF), isChunkyFragment = false, isStarlet = false, gravityDp = 8f),
    ArcadeParticle(3.70f, 80f, 4.0f, Color(0xFFFFFFFF), isChunkyFragment = false, isStarlet = false, gravityDp = 12f),
    ArcadeParticle(4.05f, 95f, 4.5f, Color(0xFFFF62AE), isChunkyFragment = false, isStarlet = false, gravityDp = 11f),
    ArcadeParticle(4.40f, 70f, 3.5f, Color(0xFF80F0D8), isChunkyFragment = false, isStarlet = false, gravityDp = 13f),
    ArcadeParticle(4.75f, 105f, 5.0f, Color(0xFFFF9FD2), isChunkyFragment = false, isStarlet = false, gravityDp = 9f),
    ArcadeParticle(5.10f, 75f, 4.0f, Color(0xFFFFFFFF), isChunkyFragment = false, isStarlet = false, gravityDp = 12f),
    ArcadeParticle(5.45f, 90f, 4.5f, Color(0xFFFFE066), isChunkyFragment = false, isStarlet = false, gravityDp = 10f),
    ArcadeParticle(5.75f, 65f, 3.5f, Color(0xFFD6B2FF), isChunkyFragment = false, isStarlet = false, gravityDp = 14f),
    ArcadeParticle(6.05f, 100f, 5.0f, Color(0xFFFF62AE), isChunkyFragment = false, isStarlet = false, gravityDp = 8f),
    ArcadeParticle(0.00f, 80f, 4.0f, Color(0xFFFFFFFF), isChunkyFragment = false, isStarlet = false, gravityDp = 11f),

    // 6 spinning retro arcade pixel starlets
    ArcadeParticle(0.52f, 115f, 6.5f, Color(0xFFFFDE59), isChunkyFragment = false, isStarlet = true, gravityDp = 6f),
    ArcadeParticle(1.57f, 125f, 6.5f, Color(0xFFFF78C0), isChunkyFragment = false, isStarlet = true, gravityDp = 8f),
    ArcadeParticle(2.62f, 110f, 6.5f, Color(0xFFFFFFFF), isChunkyFragment = false, isStarlet = true, gravityDp = 7f),
    ArcadeParticle(3.67f, 120f, 6.5f, Color(0xFFBA75FF), isChunkyFragment = false, isStarlet = true, gravityDp = 6f),
    ArcadeParticle(4.71f, 130f, 6.5f, Color(0xFFFFDE59), isChunkyFragment = false, isStarlet = true, gravityDp = 8f),
    ArcadeParticle(5.76f, 115f, 6.5f, Color(0xFF6EECDE), isChunkyFragment = false, isStarlet = true, gravityDp = 7f)
)

// renders the retro arcade explosion burst with central flash, expanding shockwave & fragments
private fun DrawScope.drawRetroArcadeExplosion(progress: Float, origin: Offset) {
    if (progress >= 1f) return

    val p = progress.coerceIn(0f, 1f)

    // 1. Initial Central Bright Pixel Flash (progress 0.0f .. 0.22f)
    if (p < 0.22f) {
        val tFlash = p / 0.22f
        val flashAlpha = (1f - tFlash).coerceIn(0f, 1f)
        val maxFlashRadius = 48.dp.toPx()
        val currentR = (tFlash * maxFlashRadius).coerceAtLeast(6f)
        val stepPx = 4.dp.toPx()

        // core white diamond
        drawRect(
            color = Color.White.copy(alpha = flashAlpha),
            topLeft = Offset(origin.x - currentR * 0.45f, origin.y - currentR * 0.45f),
            size = Size(currentR * 0.90f, currentR * 0.90f)
        )
        // horizontal & vertical bright beams
        drawRect(
            color = Color(0xFFFFF275).copy(alpha = flashAlpha * 0.9f),
            topLeft = Offset(origin.x - currentR, origin.y - stepPx),
            size = Size(currentR * 2f, stepPx * 2f)
        )
        drawRect(
            color = Color(0xFFFFF275).copy(alpha = flashAlpha * 0.9f),
            topLeft = Offset(origin.x - stepPx, origin.y - currentR),
            size = Size(stepPx * 2f, currentR * 2f)
        )
        // stepped diagonal flash accents
        val diag = currentR * 0.70f
        val diagSize = stepPx * 1.8f
        for (sx in listOf(-1f, 1f)) {
            for (sy in listOf(-1f, 1f)) {
                drawRect(
                    color = Color(0xFFFF78C0).copy(alpha = flashAlpha * 0.85f),
                    topLeft = Offset(origin.x + sx * diag - diagSize / 2f, origin.y + sy * diag - diagSize / 2f),
                    size = Size(diagSize, diagSize)
                )
            }
        }
    }

    // 2. Expanding Pixel-Art Shockwave Ring (progress 0.04f .. 0.52f)
    if (p in 0.04f..0.52f) {
        val tShock = (p - 0.04f) / 0.48f
        val shockEase = 1f - (1f - tShock).pow(2.2f)
        val shockRadius = shockEase * 175.dp.toPx()
        val shockAlpha = (1f - tShock).coerceIn(0f, 1f)
        val sparkSize = (5.5.dp.toPx() * (1f - tShock * 0.5f)).coerceAtLeast(2f)

        val numSparks = 16
        val angleStep = (2f * PI / numSparks).toFloat()
        for (i in 0 until numSparks) {
            val ang = i * angleStep
            val sx = origin.x + cos(ang) * shockRadius
            val sy = origin.y + sin(ang) * shockRadius
            val sparkColor = when (i % 3) {
                0 -> Color.White
                1 -> Color(0xFFFF62AE)
                else -> Color(0xFFC895FF)
            }.copy(alpha = shockAlpha)

            drawRect(
                color = sparkColor,
                topLeft = Offset(sx - sparkSize / 2f, sy - sparkSize / 2f),
                size = Size(sparkSize, sparkSize)
            )
        }
    }

    // 3. Foreground Chunky Fragments, Starlets, and Trailing Sparkles
    ARCADE_EXPLOSION_PARTICLES.forEachIndexed { index, part ->
        val distEase = 1f - (1f - p).pow(2.4f)
        val dist = part.speedDp.dp.toPx() * distEase
        val x = origin.x + cos(part.angleRad) * dist
        val y = origin.y + sin(part.angleRad) * dist + part.gravityDp.dp.toPx() * (p * p)
        val baseSize = part.sizeDp.dp.toPx()

        when {
            part.isChunkyFragment -> {
                // 2x2 multi-tone chunky pixel fragment with top-left highlight and bottom-right shadow
                val alpha = if (p < 0.65f) 1f else ((1f - p) / 0.35f).coerceIn(0f, 1f)
                if (alpha > 0.01f) {
                    val scale = if (p < 0.15f) 0.8f + (p / 0.15f) * 0.45f else 1.25f - ((p - 0.15f) / 0.85f) * 0.5f
                    val curSize = baseSize * scale
                    val halfSize = curSize / 2f

                    // top-left: bright specular highlight
                    drawRect(
                        color = part.color.lighter(0.45f).copy(alpha = alpha),
                        topLeft = Offset(x - halfSize, y - halfSize),
                        size = Size(halfSize, halfSize)
                    )
                    // top-right: main body color
                    drawRect(
                        color = part.color.copy(alpha = alpha),
                        topLeft = Offset(x, y - halfSize),
                        size = Size(halfSize, halfSize)
                    )
                    // bottom-left: main body color
                    drawRect(
                        color = part.color.copy(alpha = alpha),
                        topLeft = Offset(x - halfSize, y),
                        size = Size(halfSize, halfSize)
                    )
                    // bottom-right: shadow tone for solid 3D pixel chunk weight
                    drawRect(
                        color = part.color.darker(0.75f).copy(alpha = alpha),
                        topLeft = Offset(x, y),
                        size = Size(halfSize, halfSize)
                    )
                }
            }
            part.isStarlet -> {
                // 3x3 pixel cross starlet
                val alpha = if (p < 0.70f) 1f else ((1f - p) / 0.30f).coerceIn(0f, 1f)
                if (alpha > 0.01f) {
                    val px = (baseSize / 3f).coerceAtLeast(1.5f)
                    // center core: white
                    drawRect(
                        color = Color.White.copy(alpha = alpha),
                        topLeft = Offset(x - px / 2f, y - px / 2f),
                        size = Size(px, px)
                    )
                    // cross arms: starlet color
                    drawRect(color = part.color.copy(alpha = alpha), topLeft = Offset(x - px * 1.5f, y - px / 2f), size = Size(px, px))
                    drawRect(color = part.color.copy(alpha = alpha), topLeft = Offset(x + px * 0.5f, y - px / 2f), size = Size(px, px))
                    drawRect(color = part.color.copy(alpha = alpha), topLeft = Offset(x - px / 2f, y - px * 1.5f), size = Size(px, px))
                    drawRect(color = part.color.copy(alpha = alpha), topLeft = Offset(x - px / 2f, y + px * 0.5f), size = Size(px, px))
                }
            }
            else -> {
                // trailing square sparkle with authentic retro twinkle flicker
                val flicker = if (p > 0.45f && ((p * 35f + index).toInt() % 2 == 0)) 0.35f else 1f
                val alpha = ((1f - p) * flicker).coerceIn(0f, 1f)
                if (alpha > 0.01f) {
                    val curSize = baseSize * (1f - p * 0.4f)
                    drawRect(
                        color = part.color.copy(alpha = alpha),
                        topLeft = Offset(x - curSize / 2f, y - curSize / 2f),
                        size = Size(curSize, curSize)
                    )
                }
            }
        }
    }
}

// ============================================================================
// StickerExplosion Screen Overlay
// ============================================================================

// decorative sticker layout scatter overlay on launcher background with retro arcade explosion
@Composable
fun StickerExplosion() {
    var explosionTrigger by remember { mutableStateOf(1) }
    var burstOrigin by remember { mutableStateOf<Offset?>(null) }
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(explosionTrigger) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 720, easing = LinearEasing)
        )
    }

    // playful scale pop-in for background stickers during the explosion burst
    val p = animProgress.value
    val stickerScale = if (p < 0.35f) {
        (0.65f + (p / 0.35f) * 0.45f).coerceAtMost(1.10f)
    } else if (p < 0.60f) {
        1.10f - ((p - 0.35f) / 0.25f) * 0.10f
    } else {
        1.0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    burstOrigin = tapOffset
                    explosionTrigger++
                }
            }
    ) {
        // decorative pixel art stickers scattering across wallpaper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(stickerScale)
        ) {
            HeartSticker(
                size = 96.dp,
                color = Color(0xFFFF8FCB),
                rotation = -18f,
                modifier = Modifier.offset(x = 18.dp, y = 350.dp)
            )

            HeartSticker(
                size = 72.dp,
                color = Color(0xFFD791E3),
                rotation = 22f,
                modifier = Modifier.offset(x = 220.dp, y = 380.dp)
            )

            BowSticker(
                size = 72.dp,
                color = Color(0xFFFF9FD2),
                rotation = -12f,
                modifier = Modifier.offset(x = 250.dp, y = 80.dp)
            )

            SparkleSticker(
                size = 34.dp,
                color = Color(0xFFE675FF),
                rotation = 12f,
                modifier = Modifier.offset(x = 105.dp, y = 330.dp)
            )

            SparkleSticker(
                size = 20.dp,
                color = Color(0xFFFF9FD2),
                rotation = -15f,
                modifier = Modifier.offset(x = 165.dp, y = 350.dp)
            )

            CloudSticker(
                width = 86.dp,
                height = 50.dp,
                color = Color(0xFFE8D5FF),
                rotation = -8f,
                modifier = Modifier.offset(x = 24.dp, y = 16.dp)
            )

            CloudSticker(
                width = 62.dp,
                height = 36.dp,
                color = Color(0xFFFFD9EE),
                rotation = 10f,
                modifier = Modifier.offset(x = 180.dp, y = 100.dp)
            )

            FlowerSticker(
                size = 42.dp,
                petalColor = Color(0xFFFFB6DD),
                centerColor = Color(0xFFE675FF),
                rotation = -12f,
                modifier = Modifier.offset(x = 28.dp, y = 440.dp)
            )

            FlowerSticker(
                size = 30.dp,
                petalColor = Color(0xFFE8D5FF),
                centerColor = Color(0xFFFF9FD2),
                rotation = 18f,
                modifier = Modifier.offset(x = 210.dp, y = 455.dp)
            )

            // tiny top-left pixel sparkle/heart accents
            PixelMiniHeart(
                size = 24.dp,
                color = Color(0xFFFF9FD2),
                rotation = -18f,
                modifier = Modifier.offset(x = 12.dp, y = 105.dp)
            )

            PixelMiniSparkle(
                size = 14.dp,
                color = Color(0xFFE675FF),
                rotation = 12f,
                modifier = Modifier.offset(x = 68.dp, y = 115.dp)
            )

            PixelMiniHeart(
                size = 16.dp,
                color = Color(0xFFD791E3),
                rotation = 20f,
                modifier = Modifier.offset(x = 52.dp, y = 150.dp)
            )

            // tiny top-right pixel accents
            PixelMiniDiamond(
                size = 18.dp,
                color = Color(0xFFD791E3),
                rotation = -10f,
                modifier = Modifier.offset(x = 225.dp, y = 110.dp)
            )

            PixelMiniHeart(
                size = 16.dp,
                color = Color(0xFFFF9FD2),
                rotation = 12f,
                modifier = Modifier.offset(x = 245.dp, y = 145.dp)
            )

            // left-middle pixel accents
            PixelMiniHeart(
                size = 16.dp,
                color = Color(0xFFFF9FD2),
                rotation = 20f,
                modifier = Modifier.offset(x = 8.dp, y = 430.dp)
            )

            PixelMiniSparkle(
                size = 14.dp,
                color = Color(0xFFE675FF),
                rotation = -15f,
                modifier = Modifier.offset(x = 62.dp, y = 425.dp)
            )

            PixelMiniHeart(
                size = 18.dp,
                color = Color(0xFFD791E3),
                rotation = 10f,
                modifier = Modifier.offset(x = 12.dp, y = 485.dp)
            )

            // right-middle pixel accents
            PixelMiniHeart(
                size = 22.dp,
                color = Color(0xFFD791E3),
                rotation = -10f,
                modifier = Modifier.offset(x = 238.dp, y = 420.dp)
            )

            PixelMiniDiamond(
                size = 16.dp,
                color = Color(0xFFFF9FD2),
                rotation = 15f,
                modifier = Modifier.offset(x = 225.dp, y = 475.dp)
            )

            PixelMiniHeart(
                size = 14.dp,
                color = Color(0xFFE675FF),
                rotation = -20f,
                modifier = Modifier.offset(x = 250.dp, y = 500.dp)
            )

            // floating decorative pixel accents
            PixelMiniSparkle(
                size = 16.dp,
                color = Color(0xFFE675FF),
                rotation = 15f,
                modifier = Modifier.offset(x = 82.dp, y = 205.dp)
            )

            PixelMiniHeart(
                size = 14.dp,
                color = Color(0xFFFF9FD2),
                rotation = -15f,
                modifier = Modifier.offset(x = 225.dp, y = 300.dp)
            )

            PixelMiniDiamond(
                size = 16.dp,
                color = Color(0xFFD791E3),
                rotation = 20f,
                modifier = Modifier.offset(x = 195.dp, y = 510.dp)
            )
        }

        // arcade explosion canvas overlay: draws fast, bright retro burst & shockwaves
        if (animProgress.value < 1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val origin = burstOrigin ?: Offset(size.width * 0.50f, size.height * 0.46f)
                drawRetroArcadeExplosion(animProgress.value, origin)
            }
        }
    }
}

// ============================================================================
// Status Bar Wi-Fi Icon (Preserved for LauncherComponents.kt StatusCard)
// ============================================================================

// custom vector drawn Wi-Fi status icon for StatusCard
@Composable
fun TinyWifiIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    Canvas(
        modifier = modifier.size(16.dp)
    ) {
        val strokeWidth = 2.dp.toPx()

        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )

        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )

        drawCircle(
            color = color,
            radius = strokeWidth * 0.8f,
            center = Offset(size.width / 2f, size.height * 0.78f)
        )
    }
}
