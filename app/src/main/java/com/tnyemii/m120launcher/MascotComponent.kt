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

// mascot assistant expression states :3
enum class MascotExpression {
    IDLE,
    THINKING,
    RESPONDING,
    OFFLINE
}

// custom vector-drawn pixel art bunny mascot with animated bouncing and expressions <3
@Composable
fun PixelArtMascot(
    expression: MascotExpression,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    // idle bouncing transition animation :3
    val transition = rememberInfiniteTransition(label = "bunny")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (expression == MascotExpression.IDLE ||
            expression == MascotExpression.THINKING) -2.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bunny_bounce"
    )

    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension
        val cx = this.size.width / 2f
        val cy = this.size.height * 0.57f + bounce
        val s = unit / 40f

        fun p(x: Float, y: Float) = Offset(cx + x * s, cy + y * s)

        val pink = Color(0xFFFFC9E6)
        val lightPink = Color(0xFFFFE4F2)
        val outline = Color(0xFFAC79B8)
        val dark = Color(0xFF60466F)
        val cheek = Color(0xFFFF91C3)
        val white = Color(0xFFFFFFFF)

        // bunny ears <3
        drawOval(
            color = outline,
            topLeft = p(-13f, -27f),
            size = androidx.compose.ui.geometry.Size(9f * s, 19f * s)
        )
        drawOval(
            color = pink,
            topLeft = p(-12f, -27f),
            size = androidx.compose.ui.geometry.Size(7f * s, 17f * s)
        )
        drawOval(
            color = outline,
            topLeft = p(4f, -27f),
            size = androidx.compose.ui.geometry.Size(9f * s, 19f * s)
        )
        drawOval(
            color = pink,
            topLeft = p(5f, -27f),
            size = androidx.compose.ui.geometry.Size(7f * s, 17f * s)
        )

        // head and body :3
        drawOval(
            color = outline,
            topLeft = p(-17f, -18f),
            size = androidx.compose.ui.geometry.Size(34f * s, 34f * s)
        )
        drawOval(
            color = pink,
            topLeft = p(-16f, -17f),
            size = androidx.compose.ui.geometry.Size(32f * s, 32f * s)
        )

        // tiny paws <3
        drawCircle(color = outline, radius = 5f * s, center = p(-10f, 13f))
        drawCircle(color = outline, radius = 5f * s, center = p(10f, 13f))
        drawCircle(color = lightPink, radius = 4f * s, center = p(-10f, 12f))
        drawCircle(color = lightPink, radius = 4f * s, center = p(10f, 12f))

        // blush cheeks :3
        drawOval(
            color = cheek.copy(alpha = 0.8f),
            topLeft = p(-13f, -1f),
            size = androidx.compose.ui.geometry.Size(7f * s, 4f * s)
        )
        drawOval(
            color = cheek.copy(alpha = 0.8f),
            topLeft = p(6f, -1f),
            size = androidx.compose.ui.geometry.Size(7f * s, 4f * s)
        )

        // facial expressions based on current mascot state <3
        when (expression) {
            MascotExpression.IDLE -> {
                drawSparkleEye(p(-6f, -5f), s, dark, white)
                drawSparkleEye(p(6f, -5f), s, dark, white)
                drawTinySmile(p(0f, 2f), s, dark)
            }

            MascotExpression.THINKING -> {
                drawSparkleEye(p(-6f, -5f), s, dark, white)
                drawCircle(dark, 2.1f * s, p(6f, -5f))
                drawCircle(white, 0.7f * s, p(6.5f, -5.7f))
                drawCircle(dark, 1.6f * s, p(0f, 2f))
                // thought sparkles
                drawStar(p(16f, -19f), 3f * s, Color(0xFFFFB6D9))
                drawCircle(Color(0xFFD9B7FF), 1.3f * s, p(19f, -12f))
            }

            MascotExpression.RESPONDING -> {
                drawHappyEye(p(-6f, -5f), s, dark)
                drawHappyEye(p(6f, -5f), s, dark)
                drawOval(
                    color = dark,
                    topLeft = p(-3f, 0f),
                    size = androidx.compose.ui.geometry.Size(6f * s, 5f * s)
                )
                drawOval(
                    color = Color(0xFFFF8DBB),
                    topLeft = p(-1.5f, 2f),
                    size = androidx.compose.ui.geometry.Size(3f * s, 2f * s)
                )
                drawStar(p(-17f, -17f), 3f * s, Color(0xFFFFD36E))
                drawStar(p(17f, -18f), 3f * s, Color(0xFFD6B2FF))
            }

            MascotExpression.OFFLINE -> {
                drawClosedEye(p(-6f, -5f), s, dark)
                drawClosedEye(p(6f, -5f), s, dark)
                drawTinySmile(p(0f, 2f), s, dark)
                // sleepy zzz bubble
                drawCircle(Color(0xFFD6B2FF), 2f * s, p(17f, -17f))
                drawCircle(Color(0xFFE8D8FF), 1.2f * s, p(21f, -22f))
            }
        }

        // heart nose :3
        drawHeart(p(0f, -1f), 2f * s, Color(0xFFFF76B5))
    }
}

// draws anime sparkle eye with catchlight highlights <3
private fun DrawScope.drawSparkleEye(
    center: Offset,
    scale: Float,
    dark: Color,
    shine: Color
) {
    drawCircle(dark, 3.2f * scale, center)
    drawCircle(shine, 1.25f * scale, center + Offset(-0.8f * scale, -1f * scale))
    drawCircle(shine, 0.55f * scale, center + Offset(1.4f * scale, 1f * scale))
}

// draws happy curved eye arch :3
private fun DrawScope.drawHappyEye(
    center: Offset,
    scale: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x - 3f * scale, center.y + 1f * scale)
        quadraticTo(
            center.x, center.y - 4f * scale,
            center.x + 3f * scale, center.y + 1f * scale
        )
    }
    drawPath(path, color, style = Stroke(width = 1.7f * scale))
}

// draws closed sleeping eye arc <3
private fun DrawScope.drawClosedEye(
    center: Offset,
    scale: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x - 2.5f * scale, center.y)
        quadraticTo(
            center.x, center.y + 2f * scale,
            center.x + 2.5f * scale, center.y
        )
    }
    drawPath(path, color, style = Stroke(width = 1.4f * scale))
}

// draws tiny curved smile mouth :3
private fun DrawScope.drawTinySmile(
    center: Offset,
    scale: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x - 2f * scale, center.y)
        quadraticTo(
            center.x, center.y + 2.5f * scale,
            center.x + 2f * scale, center.y
        )
    }
    drawPath(path, color, style = Stroke(width = 1.3f * scale))
}

// draws 5-pointed decorative star sparkle <3
private fun DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path()
    for (i in 0 until 10) {
        val angle = Math.PI * i / 5.0 - Math.PI / 2.0
        val r = if (i % 2 == 0) radius else radius * 0.42f
        val x = center.x + kotlin.math.cos(angle).toFloat() * r
        val y = center.y + kotlin.math.sin(angle).toFloat() * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

// draws heart shape :3
private fun DrawScope.drawHeart(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius)
        cubicTo(
            center.x - radius * 2f, center.y - radius * 0.2f,
            center.x - radius, center.y - radius * 1.5f,
            center.x, center.y - radius * 0.45f
        )
        cubicTo(
            center.x + radius, center.y - radius * 1.5f,
            center.x + radius * 2f, center.y - radius * 0.2f,
            center.x, center.y + radius
        )
        close()
    }
    drawPath(path, color)
}
