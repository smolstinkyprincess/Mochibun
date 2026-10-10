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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MascotExpression {
    IDLE,
    THINKING,
    RESPONDING,
    OFFLINE
}

@Composable
fun PixelArtMascot(
    expression: MascotExpression,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_anim")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (expression == MascotExpression.IDLE || expression == MascotExpression.THINKING) 3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_bounce"
    )

    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        val yOffset = bounce

        val bodyColor = Color(0xFFFFC2F0) // pastel pink
        val outlineColor = Color(0xFF9B5FA5) // soft lavender / purple
        val faceColor = Color(0xFF684A7C)

        val bodyRadius = w * 0.35f
        val centerX = w * 0.5f
        val centerY = h * 0.55f + yOffset

        // Ears
        val earPath = Path().apply {
            // Left ear
            moveTo(centerX - bodyRadius * 0.8f, centerY - bodyRadius * 0.2f)
            lineTo(centerX - bodyRadius * 1.1f, centerY - bodyRadius * 0.9f)
            lineTo(centerX - bodyRadius * 0.2f, centerY - bodyRadius * 0.7f)
            close()
            // Right ear
            moveTo(centerX + bodyRadius * 0.8f, centerY - bodyRadius * 0.2f)
            lineTo(centerX + bodyRadius * 1.1f, centerY - bodyRadius * 0.9f)
            lineTo(centerX + bodyRadius * 0.2f, centerY - bodyRadius * 0.7f)
            close()
        }
        drawPath(earPath, bodyColor)
        drawPath(earPath, outlineColor, style = Stroke(width = 2.dp.toPx()))

        // Head/Body circle
        drawCircle(
            color = bodyColor,
            radius = bodyRadius,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = outlineColor,
            radius = bodyRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )

        // Face features based on expression
        when (expression) {
            MascotExpression.IDLE -> {
                drawEye(centerX - bodyRadius * 0.3f, centerY - bodyRadius * 0.1f, faceColor, isClosed = true)
                drawEye(centerX + bodyRadius * 0.3f, centerY - bodyRadius * 0.1f, faceColor, isClosed = true)
                drawSmile(centerX, centerY + bodyRadius * 0.2f, faceColor)
            }
            MascotExpression.THINKING -> {
                drawEye(centerX - bodyRadius * 0.3f, centerY - bodyRadius * 0.1f, faceColor, isClosed = false)
                drawEye(centerX + bodyRadius * 0.3f, centerY - bodyRadius * 0.15f, faceColor, isClosed = false)
                drawOpenMouth(centerX, centerY + bodyRadius * 0.25f, faceColor)
            }
            MascotExpression.RESPONDING -> {
                drawEye(centerX - bodyRadius * 0.3f, centerY - bodyRadius * 0.1f, faceColor, isClosed = true)
                drawEye(centerX + bodyRadius * 0.3f, centerY - bodyRadius * 0.1f, faceColor, isClosed = true)
                drawWideSmile(centerX, centerY + bodyRadius * 0.2f, faceColor)
            }
            MascotExpression.OFFLINE -> {
                drawSleepyEye(centerX - bodyRadius * 0.3f, centerY - bodyRadius * 0.05f, faceColor)
                drawSleepyEye(centerX + bodyRadius * 0.3f, centerY - bodyRadius * 0.05f, faceColor)
                drawFlatMouth(centerX, centerY + bodyRadius * 0.25f, faceColor)
            }
        }
    }
}

private fun DrawScope.drawEye(x: Float, y: Float, color: Color, isClosed: Boolean) {
    if (isClosed) {
        val path = Path().apply {
            moveTo(x - 5f, y + 3f)
            quadraticTo(x, y - 4f, x + 5f, y + 3f)
        }
        drawPath(path, color, style = Stroke(width = 2f))
    } else {
        drawCircle(color, radius = 3.5f, center = Offset(x, y))
    }
}

private fun DrawScope.drawSleepyEye(x: Float, y: Float, color: Color) {
    drawLine(color, start = Offset(x - 4f, y), end = Offset(x + 4f, y), strokeWidth = 2f)
}

private fun DrawScope.drawSmile(x: Float, y: Float, color: Color) {
    val path = Path().apply {
        moveTo(x - 6f, y - 2f)
        quadraticTo(x, y + 5f, x + 6f, y - 2f)
    }
    drawPath(path, color, style = Stroke(width = 2f))
}

private fun DrawScope.drawWideSmile(x: Float, y: Float, color: Color) {
    val path = Path().apply {
        moveTo(x - 8f, y - 3f)
        quadraticTo(x, y + 8f, x + 8f, y - 3f)
    }
    drawPath(path, color, style = Stroke(width = 2f))
}

private fun DrawScope.drawOpenMouth(x: Float, y: Float, color: Color) {
    drawCircle(color, radius = 3f, center = Offset(x, y))
}

private fun DrawScope.drawFlatMouth(x: Float, y: Float, color: Color) {
    drawLine(color, start = Offset(x - 4f, y), end = Offset(x + 4f, y), strokeWidth = 2f)
}
