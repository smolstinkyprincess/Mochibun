package com.tnyemii.mochibun

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Crisp pixel-art smiling strawberry sprite matching the reference mockup.
 * Rendered using 1:1 direct Canvas pixel rectangles with no bilinear filtering/blur
 * to remain ultra lightweight on the MediaTek MT6761.
 */
@Composable
fun PixelStrawberrySprite(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val pixelSize = this.size.width / 16f

        val rows = listOf(
            "......SS........", // Row 0: stem
            ".....SSS........", // Row 1: stem curve
            "...GLLSSLLG.....", // Row 2: leaves top
            "..GLLLLLLLLG....", // Row 3: leaves spread
            "..OGGGLLGGGO....", // Row 4: leaf base / crown
            ".ORRRRRRRRRRO...", // Row 5: top of strawberry
            "ORRWRRRRRRWRRO..", // Row 6: upper seeds
            "ORRRRRRRRRRRRO..", // Row 7: body
            "ORRERRRRRRERRO..", // Row 8: eyes
            "ORBBERRRREBBRO..", // Row 9: blush & eye lower
            "ORRRRM..MRRRRO..", // Row 10: smile corners
            "ORRRR.MM.RRRRO..", // Row 11: smile bottom
            ".OKRRWRRRRWRKO..", // Row 12: lower seeds & shadow
            "..OKKRRRRKKO....", // Row 13: tapering body
            "...OKKKKKKO.....", // Row 14: bottom tip
            ".....OOOO......."  // Row 15: tip outline
        )

        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                val color = when (char) {
                    'S' -> Color(0xFF2C6B33) // stem
                    'L' -> Color(0xFF58BD66) // bright leaf
                    'G' -> Color(0xFF1F4D25) // dark leaf outline
                    'O' -> Color(0xFF691322) // dark strawberry outline
                    'R' -> Color(0xFFEE3357) // strawberry red
                    'K' -> Color(0xFFC01A3D) // shadow red
                    'E' -> Color(0xFF261217) // eye
                    'B' -> Color(0xFFFFAAB9) // blush
                    'M' -> Color(0xFF261217) // mouth
                    'W' -> Color(0xFFFFF4A3) // seed
                    else -> null
                }
                if (color != null) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x * pixelSize, y * pixelSize),
                        size = Size(pixelSize + 0.5f, pixelSize + 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Slim pastel pink heart-scrubber progress bar with a cute heart thumb knob.
 */
@Composable
fun HeartScrubberProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
    ) {
        val w = size.width
        val h = size.height
        val trackHeight = 3.5.dp.toPx()
        val trackY = (h - trackHeight) / 2f
        val clampedProgress = progress.coerceIn(0f, 1f)

        // 1. Inactive background track
        drawRoundRect(
            color = Color(0xFFF9D6E0),
            topLeft = Offset(0f, trackY),
            size = Size(w, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
        )

        // 2. Active playback track
        val activeWidth = w * clampedProgress
        if (activeWidth > 0f) {
            drawRoundRect(
                color = Color(0xFFF4A4B8),
                topLeft = Offset(0f, trackY),
                size = Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            )
        }

        // 3. Heart scrubber thumb knob
        val thumbRadius = 6.dp.toPx()
        val hx = (w * clampedProgress).coerceIn(thumbRadius, (w - thumbRadius).coerceAtLeast(thumbRadius))
        val hy = h / 2f
        val s = thumbRadius

        val heartPath = Path().apply {
            moveTo(hx, hy + s * 0.75f)
            cubicTo(hx - s * 1.15f, hy + s * 0.1f, hx - s * 1.15f, hy - s * 0.85f, hx, hy - s * 0.35f)
            cubicTo(hx + s * 1.15f, hy - s * 0.85f, hx + s * 1.15f, hy + s * 0.1f, hx, hy + s * 0.75f)
            close()
        }
        drawPath(heartPath, Color(0xFFFCD3DE))
        drawPath(heartPath, Color(0xFFE59AA8), style = Stroke(width = 1.2.dp.toPx()))
    }
}

/**
 * Concentric double-border modifier for the Now Playing card matching the mockup.
 */
fun Modifier.nowPlayingCardConcentricBorder(
    cornerRadius: Dp = 22.dp,
    outerColor: Color = Color(0xFFE5A4B4),
    innerColor: Color = Color(0xFFECD0D8)
): Modifier = this.drawWithContent {
    drawContent()
    val r = cornerRadius.toPx()
    // Outer border
    drawRoundRect(
        color = outerColor,
        topLeft = Offset(0.75.dp.toPx(), 0.75.dp.toPx()),
        size = Size(size.width - 1.5.dp.toPx(), size.height - 1.5.dp.toPx()),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.5.dp.toPx())
    )
    // Inner concentric line inset by 3.5 dp
    val inset = 3.5.dp.toPx()
    if (size.width > inset * 2 && size.height > inset * 2) {
        val innerR = (r - inset).coerceAtLeast(0f)
        drawRoundRect(
            color = innerColor,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(innerR, innerR),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * Retro bracketed transport button: [ ◄◄ ], [ ▶ ] / [ ❚❚ ], [ ►► ].
 * Shows glowing double-line rose outline when focused via D-pad.
 */
@Composable
fun TransportButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .glowingDoubleBorder(isSelected = isFocused, cornerRadius = 14.dp)
            .background(if (isFocused) Color(0xFFFFF0F4) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3E2452)
        )
    }
}

/**
 * Native "Now Playing" card composable matching the mockup styling,
 * component hierarchy, and kawaii aesthetic.
 */
@Composable
fun NowPlayingCard(
    mediaState: MochibunMediaState,
    focusedControl: Int, // 0 = Prev, 1 = Play/Pause, 2 = Next, -1 = None
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenMediaApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp) // space for overlapping bunny pin
    ) {
        // Main Card Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false }
                .kawaiiCardMotifs(
                    isFocused = focusedControl != -1,
                    cornerRadius = 22.dp
                )
                .clip(cardShape)
                .background(Color(0xFFF6F0FD)) // Frosted lavender-cream
                .nowPlayingCardConcentricBorder(cornerRadius = 22.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top Row: Album Art Squircle + Metadata + Heart Scrubber
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Soft Pastel Mint Squircle with Artwork or Strawberry
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFBFE8D6))
                            .clickable { onOpenMediaApp() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaState.artwork != null) {
                            Image(
                                bitmap = mediaState.artwork.asImageBitmap(),
                                contentDescription = "Album Artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PixelStrawberrySprite(size = 46.dp)
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Track Information & Heart Progress
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenMediaApp() }
                    ) {
                        Text(
                            text = mediaState.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3E2452),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mediaState.artist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF8D7B9D),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        HeartScrubberProgressBar(progress = mediaState.progressFraction)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Row: Transport Controls Centered
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton(
                        text = "[ ◄◄ ]",
                        isFocused = focusedControl == 0,
                        onClick = onPrevious
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    TransportButton(
                        text = if (mediaState.isPlaying) "[ ❚❚ ]" else "[ ▶ ]",
                        isFocused = focusedControl == 1,
                        onClick = onPlayPause
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    TransportButton(
                        text = "[ ►► ]",
                        isFocused = focusedControl == 2,
                        onClick = onNext
                    )
                }
            }
        }

        // Overlapping Bunny Pin Badge anchored at the top-left edge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = (-8).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFFFDFE))
                .border(1.5.dp, Color(0xFFE5A4B4), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            PixelBunnyAvatar(size = 20.dp)
        }
    }
}
