package com.tnyemii.m120launcher

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// mascot assistant expression states :3
enum class MascotExpression {
    IDLE,
    THINKING,
    RESPONDING,
    OFFLINE
}

// the sprite lives on a 20 x 15 grid of square pixels <3
private const val GRID_W = 20
private const val GRID_H = 15

// pixel color legend: each letter in the sprite below is one colored pixel :3
// . = empty   O = outline   P = pink      L = light pink   C = cheek
// D = dark (eyes/mouth)  W = white shine   H = heart nose   T = tongue
// S = yellow sparkle     V = violet sparkle   K = pink sparkle
private fun paletteColor(c: Char): Color? = when (c) {
    'O' -> Color(0xFFAC79B8)
    'P' -> Color(0xFFFFC9E6)
    'L' -> Color(0xFFFFE4F2)
    'C' -> Color(0xFFFF91C3)
    'D' -> Color(0xFF60466F)
    'W' -> Color(0xFFFFFFFF)
    'H' -> Color(0xFFFF76B5)
    'T' -> Color(0xFFFF8DBB)
    'S' -> Color(0xFFFFD36E)
    'V' -> Color(0xFFD6B2FF)
    'K' -> Color(0xFFFFB6D9)
    else -> null
}

// a horizontal strip of same-colored pixels (fewer draw calls = lighter on the phone) <3
private class PixelRun(val x: Int, val y: Int, val length: Int, val color: Color)

// turns rows of letters into strips of pixels :3
private fun runsFromRows(rows: List<String>, startRow: Int): List<PixelRun> {
    val runs = mutableListOf<PixelRun>()
    rows.forEachIndexed { rowIndex, row ->
        var x = 0
        while (x < row.length) {
            val ch = row[x]
            val color = paletteColor(ch)
            if (color == null) {
                x++
            } else {
                val start = x
                while (x < row.length && row[x] == ch) x++
                runs.add(PixelRun(start, startRow + rowIndex, x - start, color))
            }
        }
    }
    return runs
}

// a little sticker of pixels placed at (x, y) on the grid <3
private fun patch(x: Int, y: Int, vararg rows: String): List<PixelRun> =
    runsFromRows(rows.toList(), y).map { PixelRun(it.x + x, it.y, it.length, it.color) }

// the bunny's head, ears, cheeks and paws (16 wide, padded by 2 on each side) :3
private val BASE_ROWS = listOf(
    "...OOO....OOO...",
    "..OPLPO..OPLPO..",
    "..OPLPO..OPLPO..",
    "..OPLPO..OPLPO..",
    "..OPPPO..OPPPO..",
    "..OOOOOOOOOOOO..",
    ".OPPPPPPPPPPPPO.",
    "OPPPPPPPPPPPPPPO",
    "OPPPPPPPPPPPPPPO",
    "OPPPPPPPPPPPPPPO",
    "OPCCPPPPPPPPCCPO",
    "OPPPPPPPPPPPPPPO",
    ".OPPPPPPPPPPPPO.",
    "..OPLLPPPPLLPO..",
    "...OOOOOOOOOO..."
).map { ".." + it + ".." }

private val BASE_RUNS = runsFromRows(BASE_ROWS, 0)

// each expression = base bunny + its own face and sparkles <3
private val EXPRESSION_RUNS: Map<MascotExpression, List<PixelRun>> = mapOf(
    MascotExpression.IDLE to (
            BASE_RUNS +
                    patch(6, 8, "WD", "DD") +        // left sparkle eye
                    patch(12, 8, "WD", "DD") +       // right sparkle eye
                    patch(9, 10, "HH") +             // heart nose
                    patch(8, 11, "D..D") +           // tiny smile
                    patch(9, 12, "DD")
            ),
    MascotExpression.THINKING to (
            BASE_RUNS +
                    patch(6, 8, "WD", "DD") +        // sparkle eye
                    patch(12, 8, "DD", "DD") +       // plain eye
                    patch(9, 10, "HH") +
                    patch(9, 11, "DD") +             // little "o" mouth
                    patch(17, 1, ".K.", "KKK", ".K.") +  // thought sparkle
                    patch(17, 5, "V")
            ),
    MascotExpression.RESPONDING to (
            BASE_RUNS +
                    patch(5, 8, ".D.", "D.D") +      // happy ^ eye
                    patch(12, 8, ".D.", "D.D") +     // happy ^ eye
                    patch(9, 10, "HH") +
                    patch(8, 11, "DDDD") +           // open mouth
                    patch(9, 12, "TT") +             // tongue
                    patch(0, 2, ".S.", "SSS", ".S.") +   // yellow sparkle
                    patch(17, 2, ".V.", "VVV", ".V.")    // violet sparkle
            ),
    MascotExpression.OFFLINE to (
            BASE_RUNS +
                    patch(6, 9, "DD") +              // sleepy closed eye
                    patch(12, 9, "DD") +             // sleepy closed eye
                    patch(9, 10, "HH") +
                    patch(8, 11, "D..D") +
                    patch(9, 12, "DD") +
                    patch(17, 2, "VVV", ".V.", "VVV")    // sleepy Z
            )
)

// pixel art bunny mascot that hops one whole pixel at a time and changes expressions <3
@Composable
fun PixelArtMascot(
    expression: MascotExpression,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val shouldHop = expression == MascotExpression.IDLE ||
            expression == MascotExpression.THINKING

    // simple timer hop: flips up/down twice a second (works even if system animations are off) :3
    var hopUp by remember { mutableStateOf(false) }
    LaunchedEffect(shouldHop) {
        hopUp = false
        while (shouldHop) {
            delay(1000)
            hopUp = !hopUp
        }
    }

    val runs = EXPRESSION_RUNS[expression] ?: BASE_RUNS

    Canvas(modifier = modifier.size(size)) {
        // every pixel is a whole number of screen pixels so the edges stay crisp :3
        val cell = (this.size.minDimension / GRID_W).toInt().coerceAtLeast(1).toFloat()
        val left = ((this.size.width - cell * GRID_W) / 2f).toInt().toFloat()
        val top = ((this.size.height - cell * GRID_H) / 2f).toInt().toFloat() +
                (if (hopUp) -cell else 0f)

        runs.forEach { run ->
            drawRect(
                color = run.color,
                topLeft = Offset(left + run.x * cell, top + run.y * cell),
                size = Size(run.length * cell, cell)
            )
        }
    }
}