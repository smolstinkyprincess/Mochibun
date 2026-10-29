package com.crinklecodone.mochibun.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.data.repository.MochibunMediaState
import com.crinklecodone.mochibun.ui.theme.*

/**
 * Slim pastel pink heart-scrubber progress bar with a cute heart thumb knob.
 */
@Composable
fun HeartScrubberProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val heartPath = remember { Path() }
    val density = LocalDensity.current
    val scrubberStroke = remember(density) { Stroke(width = with(density) { 1.2.dp.toPx() }) }

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

        heartPath.reset()
        heartPath.moveTo(hx, hy + s * 0.75f)
        heartPath.cubicTo(hx - s * 1.15f, hy + s * 0.1f, hx - s * 1.15f, hy - s * 0.85f, hx, hy - s * 0.35f)
        heartPath.cubicTo(hx + s * 1.15f, hy - s * 0.85f, hx + s * 1.15f, hy + s * 0.1f, hx, hy + s * 0.75f)
        heartPath.close()
        drawPath(heartPath, Color(0xFFFCD3DE))
        drawPath(heartPath, Color(0xFFE59AA8), style = scrubberStroke)
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
    modifier: Modifier = Modifier,
    theme: LauncherTheme = LocalLauncherTheme.current
) {
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .glowingDoubleBorder(isSelected = isFocused, cornerRadius = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(if (isFocused) theme.focusedContainerColor else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = theme.cardTextColor
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
    val theme = LocalLauncherTheme.current
    val cardShape = RoundedCornerShape(22.dp)

    val mascotMood = when {
        mediaState.isPlaying -> MascotMood.MUSIC
        else -> getDefaultTimeOfDayMood()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp) // space for prominent mascot pin
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
                .background(theme.cardBgColor)
                .nowPlayingCardConcentricBorder(
                    cornerRadius = 22.dp,
                    outerColor = theme.cardBorderColor,
                    innerColor = theme.cardBorderColor.copy(alpha = 0.5f)
                )
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
                            .background(theme.greetingBgColor)
                            .clickable { onOpenMediaApp() },
                        contentAlignment = Alignment.Center
                    ) {
                        val artworkBitmap = remember(mediaState.artwork) { mediaState.artwork?.asImageBitmap() }
                        if (artworkBitmap != null) {
                            Image(
                                bitmap = artworkBitmap,
                                contentDescription = "Album Artwork",
                                filterQuality = FilterQuality.None,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            MochibunMascot(
                                mood = mascotMood,
                                size = 52.dp,
                                themeTintColor = theme.mascotTintColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Track Information & Heart Progress
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenMediaApp() }
                    ) {
                        val displayTitle = rememberSteppedTickerText(
                            text = mediaState.title.ifBlank { "No Media Active" },
                            visibleCharCount = 18,
                            enabled = mediaState.isPlaying
                        )
                        Text(
                            text = displayTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.cardTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mediaState.artist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = theme.cardTextColor.copy(alpha = 0.7f),
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

        // Overlapping Mascot Pin Badge anchored at the top-left edge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 12.dp, y = (-16).dp)
                .keitaiMascotBob(enabled = mediaState.isPlaying)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.cardBgColor)
                .border(1.5.dp, theme.cardBorderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            MochibunMascot(
                mood = mascotMood,
                size = 48.dp,
                themeTintColor = theme.mascotTintColor
            )
        }
    }
}
