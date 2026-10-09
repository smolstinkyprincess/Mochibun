package com.tnyemii.m120launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeartSticker(
    size: Dp = 42.dp,
    color: Color = Color(0xFFFF8FCB),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        val w = this.size.width
        val h = this.size.height

        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.90f)

            cubicTo(
                w * 0.10f, h * 0.62f,
                w * 0.05f, h * 0.22f,
                w * 0.27f, h * 0.14f
            )

            cubicTo(
                w * 0.40f, h * 0.08f,
                w * 0.48f, h * 0.20f,
                w * 0.50f, h * 0.30f
            )

            cubicTo(
                w * 0.52f, h * 0.20f,
                w * 0.60f, h * 0.08f,
                w * 0.73f, h * 0.14f
            )

            cubicTo(
                w * 0.95f, h * 0.22f,
                w * 0.90f, h * 0.62f,
                w * 0.50f, h * 0.90f
            )
        }

        // white sticker border
        drawPath(
            path = path,
            color = Color.White
        )

        // pink heart
        scale(
            scaleX = 0.78f,
            scaleY = 0.78f,
            pivot = center
        ) {
            drawPath(
                path = path,
                color = color
            )
        }
    }
}

@Composable
fun BowSticker(
    size: Dp = 64.dp,
    color: Color = Color(0xFFFF9FD2),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ){
        val w = this.size.width
        val h = this.size.height

        // Left bow loop
        val leftLoop = Path().apply {
            moveTo(w * 0.46f, h * 0.45f)

            cubicTo(
                w * 0.25f, h * 0.12f,
                w * 0.02f, h * 0.18f,
                w * 0.08f, h * 0.50f
            )

            cubicTo(
                w * 0.12f, h * 0.75f,
                w * 0.34f, h * 0.70f,
                w * 0.46f, h * 0.55f
            )

            close()
        }

        // Right bow loop
        val rightLoop = Path().apply {
            moveTo(w * 0.54f, h * 0.45f)

            cubicTo(
                w * 0.75f, h * 0.12f,
                w * 0.98f, h * 0.18f,
                w * 0.92f, h * 0.50f
            )

            cubicTo(
                w * 0.88f, h * 0.75f,
                w * 0.66f, h * 0.70f,
                w * 0.54f, h * 0.55f
            )

            close()
        }

        // Ribbon tails
        val leftTail = Path().apply {
            moveTo(w * 0.43f, h * 0.55f)
            lineTo(w * 0.20f, h * 0.95f)
            lineTo(w * 0.45f, h * 0.82f)
            close()
        }

        val rightTail = Path().apply {
            moveTo(w * 0.57f, h * 0.55f)
            lineTo(w * 0.80f, h * 0.95f)
            lineTo(w * 0.55f, h * 0.82f)
            close()
        }

        // White sticker outline
        drawPath(leftLoop, Color.White)
        drawPath(rightLoop, Color.White)
        drawPath(leftTail, Color.White)
        drawPath(rightTail, Color.White)

        // Colored bow
        scale(
            scaleX = 0.80f,
            scaleY = 0.80f,
            pivot = center
        ) {
            drawPath(leftLoop, color)
            drawPath(rightLoop, color)
            drawPath(leftTail, color)
            drawPath(rightTail, color)
        }

        // Center knot
        drawCircle(
            color = Color.White,
            radius = w * 0.13f,
            center = Offset(w / 2f, h * 0.50f)
        )

        drawCircle(
            color = color,
            radius = w * 0.095f,
            center = Offset(w / 2f, h * 0.50f)
        )
    }
}

@Composable
fun SparkleSticker(
    size: Dp = 36.dp,
    color: Color = Color(0xFFE675FF),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        val w = this.size.width
        val h = this.size.height

        val sparkle = Path().apply {
            moveTo(w * 0.50f, 0f)

            lineTo(w * 0.63f, h * 0.37f)

            lineTo(w, h * 0.50f)

            lineTo(w * 0.63f, h * 0.63f)

            lineTo(w * 0.50f, h)

            lineTo(w * 0.37f, h * 0.63f)

            lineTo(0f, h * 0.50f)

            lineTo(w * 0.37f, h * 0.37f)

            close()
        }

        // White sticker border
        drawPath(
            path = sparkle,
            color = Color.White
        )

        // Colored center
        scale(
            scaleX = 0.72f,
            scaleY = 0.72f,
            pivot = center
        ) {
            drawPath(
                path = sparkle,
                color = color
            )
        }
    }
}

@Composable
fun CloudSticker(
    width: Dp = 82.dp,
    height: Dp = 48.dp,
    color: Color = Color(0xFFE8D5FF),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .rotate(rotation)
    ) {
        val w = size.width
        val h = size.height

        val cloud = Path().apply {
            // Start at the bottom-left
            moveTo(w * 0.15f, h * 0.70f)

            // Left bump
            cubicTo(
                w * 0.02f, h * 0.45f,
                w * 0.15f, h * 0.20f,
                w * 0.35f, h * 0.32f
            )

            // Big middle bump
            cubicTo(
                w * 0.38f, h * 0.02f,
                w * 0.68f, h * 0.02f,
                w * 0.72f, h * 0.34f
            )

            // Right bump
            cubicTo(
                w * 0.95f, h * 0.25f,
                w * 1.02f, h * 0.52f,
                w * 0.88f, h * 0.70f
            )

            // Bottom
            cubicTo(
                w * 0.72f, h * 0.88f,
                w * 0.30f, h * 0.88f,
                w * 0.15f, h * 0.70f
            )

            close()
        }

        // White sticker border
        drawPath(
            path = cloud,
            color = Color.White
        )

        // Colored cloud
        scale(
            scaleX = 0.82f,
            scaleY = 0.78f,
            pivot = center
        ) {
            drawPath(
                path = cloud,
                color = color
            )
        }
    }
}

@Composable
fun FlowerSticker(
    size: Dp = 42.dp,
    petalColor: Color = Color(0xFFFFB6DD),
    centerColor: Color = Color(0xFFE675FF),
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        val radius = minOf(this.size.width, this.size.height) / 2f

        // White sticker outline
        for (i in 0 until 5) {
            rotate(i * 72f) {
                drawOval(
                    color = Color.White,
                    topLeft = Offset(
                        center.x - radius * 0.25f,
                        center.y - radius * 0.95f
                    ),
                    size = Size(
                        radius * 0.50f,
                        radius * 0.85f
                    )
                )
            }
        }

        // Colored petals
        for (i in 0 until 5) {
            rotate(i * 72f) {
                drawOval(
                    color = petalColor,
                    topLeft = Offset(
                        center.x - radius * 0.17f,
                        center.y - radius * 0.78f
                    ),
                    size = Size(
                        radius * 0.34f,
                        radius * 0.68f
                    )
                )
            }
        }

        // White outline around center
        drawCircle(
            color = Color.White,
            radius = radius * 0.25f
        )

        // Flower center
        drawCircle(
            color = centerColor,
            radius = radius * 0.17f
        )
    }
}

@Composable
fun StickerExplosion() {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        // ───── custom stickers ─────

        HeartSticker(
            size = 96.dp,
            color = Color(0xFFFF8FCB),
            rotation = -18f,
            modifier = Modifier.offset(
                x = 18.dp,
                y = 350.dp
            )
        )

        HeartSticker(
            size = 72.dp,
            color = Color(0xFFD791E3),
            rotation = 22f,
            modifier = Modifier.offset(
                x = 220.dp,
                y = 380.dp
            )
        )

        BowSticker(
            size = 72.dp,
            color = Color(0xFFFF9FD2),
            rotation = -12f,
            modifier = Modifier.offset(
                x = 250.dp,
                y = 80.dp
            )
        )

        SparkleSticker(
            size = 34.dp,
            color = Color(0xFFE675FF),
            rotation = 12f,
            modifier = Modifier.offset(
                x = 105.dp,
                y = 330.dp
            )
        )

        SparkleSticker(
            size = 20.dp,
            color = Color(0xFFFF9FD2),
            rotation = -15f,
            modifier = Modifier.offset(
                x = 165.dp,
                y = 350.dp
            )
        )

        CloudSticker(
            width = 86.dp,
            height = 50.dp,
            color = Color(0xFFE8D5FF),
            rotation = -8f,
            modifier = Modifier.offset(
                x = 24.dp,
                y = 16.dp
            )
        )

        CloudSticker(
            width = 62.dp,
            height = 36.dp,
            color = Color(0xFFFFD9EE),
            rotation = 10f,
            modifier = Modifier.offset(
                x = 180.dp,
                y = 100.dp
            )
        )

        FlowerSticker(
            size = 42.dp,
            petalColor = Color(0xFFFFB6DD),
            centerColor = Color(0xFFE675FF),
            rotation = -12f,
            modifier = Modifier.offset(
                x = 28.dp,
                y = 440.dp
            )
        )

        FlowerSticker(
            size = 30.dp,
            petalColor = Color(0xFFE8D5FF),
            centerColor = Color(0xFFFF9FD2),
            rotation = 18f,
            modifier = Modifier.offset(
                x = 210.dp,
                y = 455.dp
            )
        )


        // ───── tiny top-left accents ─────

        Text(
            text = "♡",
            fontSize = 26.sp,
            color = Color(0xFFFF9FD2),
            modifier = Modifier
                .offset(x = 12.dp, y = 105.dp)
                .rotate(-18f)
        )

        Text(
            text = "✦",
            fontSize = 14.sp,
            color = Color(0xFFE675FF),
            modifier = Modifier
                .offset(x = 68.dp, y = 115.dp)
                .rotate(12f)
        )

        Text(
            text = "♡",
            fontSize = 16.sp,
            color = Color(0xFFD791E3),
            modifier = Modifier
                .offset(x = 52.dp, y = 150.dp)
                .rotate(20f)
        )


        // ───── tiny top-right accents ─────

        Text(
            text = "✧",
            fontSize = 20.sp,
            color = Color(0xFFD791E3),
            modifier = Modifier
                .offset(x = 225.dp, y = 110.dp)
                .rotate(-10f)
        )

        Text(
            text = "♡",
            fontSize = 18.sp,
            color = Color(0xFFFF9FD2),
            modifier = Modifier
                .offset(x = 245.dp, y = 145.dp)
                .rotate(12f)
        )


        // ───── left-middle accents ─────

        Text(
            text = "♥",
            fontSize = 16.sp,
            color = Color(0xFFFF9FD2),
            modifier = Modifier
                .offset(x = 8.dp, y = 430.dp)
                .rotate(20f)
        )

        Text(
            text = "✦",
            fontSize = 14.sp,
            color = Color(0xFFE675FF),
            modifier = Modifier
                .offset(x = 62.dp, y = 425.dp)
                .rotate(-15f)
        )

        Text(
            text = "♡",
            fontSize = 18.sp,
            color = Color(0xFFD791E3),
            modifier = Modifier
                .offset(x = 12.dp, y = 485.dp)
                .rotate(10f)
        )


        // ───── right-middle accents ─────

        Text(
            text = "♡",
            fontSize = 24.sp,
            color = Color(0xFFD791E3),
            modifier = Modifier
                .offset(x = 238.dp, y = 420.dp)
                .rotate(-10f)
        )

        Text(
            text = "✧",
            fontSize = 16.sp,
            color = Color(0xFFFF9FD2),
            modifier = Modifier
                .offset(x = 225.dp, y = 475.dp)
                .rotate(15f)
        )

        Text(
            text = "♥",
            fontSize = 14.sp,
            color = Color(0xFFE675FF),
            modifier = Modifier
                .offset(x = 250.dp, y = 500.dp)
                .rotate(-20f)
        )


        // ───── tiny floating decorations ─────

        Text(
            text = "✦",
            fontSize = 16.sp,
            color = Color(0xFFE675FF),
            modifier = Modifier
                .offset(x = 82.dp, y = 205.dp)
                .rotate(15f)
        )

        Text(
            text = "♡",
            fontSize = 14.sp,
            color = Color(0xFFFF9FD2),
            modifier = Modifier
                .offset(x = 225.dp, y = 300.dp)
                .rotate(-15f)
        )

        Text(
            text = "✧",
            fontSize = 16.sp,
            color = Color(0xFFD791E3),
            modifier = Modifier
                .offset(x = 195.dp, y = 510.dp)
                .rotate(20f)
        )
    }
}
