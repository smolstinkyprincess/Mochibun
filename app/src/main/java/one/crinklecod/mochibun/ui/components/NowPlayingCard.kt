package one.crinklecod.mochibun.ui.components

import androidx.compose.foundation.layout.width
import one.crinklecod.mochibun.data.model.CardBorderStyle
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
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
import one.crinklecod.mochibun.data.repository.MochibunMediaState
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.LauncherTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.cardBorderColor
import one.crinklecod.mochibun.ui.theme.focusedContainerColor

internal fun computeDisplayPositionMs(
    mediaState: MochibunMediaState,
    elapsedRealtimeMs: Long
): Long {
    if (mediaState.durationMs <= 0L) return 0L
    val raw = if (mediaState.isPlaying && mediaState.lastPositionUpdateTime > 0L) {
        val elapsed = (elapsedRealtimeMs - mediaState.lastPositionUpdateTime).coerceAtLeast(0L)
        (mediaState.positionMs + (elapsed * mediaState.playbackSpeed)).toLong()
    } else {
        mediaState.positionMs
    }
    return raw.coerceIn(0L, mediaState.durationMs)
}

// Hand-rolled instead of String.format: this runs every second while music plays, and
// java.util.Formatter (pattern parse + allocations) is slow under JIT-only ART on the MT6761.
internal fun formatMediaDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val sb = StringBuilder(8)
    if (hours > 0L) {
        sb.append(hours).append(':')
        if (minutes < 10L) sb.append('0')
    }
    sb.append(minutes).append(':')
    if (seconds < 10L) sb.append('0')
    sb.append(seconds)
    return sb.toString()
}

/**
 * Slim pastel heart-scrubber progress bar with a 4dp pill track and a small heart thumb.
 */
@Composable
fun HeartScrubberProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    // Read only inside the draw block, so a playback tick invalidates draw, not composition
    tick: (() -> Int)? = null,
    progressProvider: (() -> Float)? = null
) {
    val heartPath = remember { Path() }
    val density = LocalDensity.current
    val scrubberStroke = remember(density) { Stroke(width = with(density) { 1.1.dp.toPx() }) }
    val trackColor = ActiveTheme.surfaceTint.copy(alpha = 0.28f)
    val accentColor = ActiveTheme.palette.focusedCardBorder
    val heartFillColor = ActiveTheme.primary
    val heartOutlineColor = ActiveTheme.accentDeep

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        tick?.invoke() // Invalidates draw only when the 1 Hz playback tick advances

        val w = size.width
        val h = size.height
        val trackHeight = 4.dp.toPx()
        val trackRadius = trackHeight / 2f
        val trackY = (h - trackHeight) / 2f
        val clampedProgress = (progressProvider?.invoke() ?: progress).coerceIn(0f, 1f)

        // 1. Inactive pastel pill track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackY),
            size = Size(w, trackHeight),
            cornerRadius = CornerRadius(trackRadius, trackRadius)
        )

        // 2. Active playback progress fill
        val activeWidth = w * clampedProgress
        if (activeWidth > 0f) {
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(0f, trackY),
                size = Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )
        }

        // 3. Small heart indicator at the current playback position
        val thumbRadius = 5.dp.toPx()
        val hx = (w * clampedProgress).coerceIn(thumbRadius, (w - thumbRadius).coerceAtLeast(thumbRadius))
        val hy = h / 2f
        val s = thumbRadius

        heartPath.reset()
        heartPath.moveTo(hx, hy + s * 0.75f)
        heartPath.cubicTo(hx - s * 1.15f, hy + s * 0.1f, hx - s * 1.15f, hy - s * 0.85f, hx, hy - s * 0.35f)
        heartPath.cubicTo(hx + s * 1.15f, hy - s * 0.85f, hx + s * 1.15f, hy + s * 0.1f, hx, hy + s * 0.75f)
        heartPath.close()
        drawPath(heartPath, heartFillColor)
        drawPath(heartPath, heartOutlineColor, style = scrubberStroke)
    }
}

/**
 * Themed pastel CD / MiniDisc fallback artwork drawn with cached geometry (zero per-frame allocations).
 */
@Composable
private fun PastelMiniDiscIcon(
    modifier: Modifier = Modifier
) {
    val discFill = ActiveTheme.primary.copy(alpha = 0.45f)
    val sheenColor = ActiveTheme.surfaceTint.copy(alpha = 0.42f)
    val hubFill = ActiveTheme.container
    val holeFill = ActiveTheme.background
    val lineColor = ActiveTheme.accentDeep.copy(alpha = 0.68f)

    Spacer(
        modifier = modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val outerR = minOf(w, h) * 0.38f
            val hubR = outerR * 0.40f
            val holeR = outerR * 0.15f
            val outerTopLeft = Offset(cx - outerR, cy - outerR)
            val outerSize = Size(outerR * 2f, outerR * 2f)
            val outerStroke = Stroke(width = 1.4.dp.toPx())
            val innerStroke = Stroke(width = 1.1.dp.toPx())
            val sparkleCenter = Offset(w * 0.80f, h * 0.22f)
            val sparkleSize = 3.2.dp.toPx()
            val sparklePath = Path().apply {
                moveTo(sparkleCenter.x, sparkleCenter.y - sparkleSize)
                lineTo(sparkleCenter.x + sparkleSize * 0.28f, sparkleCenter.y - sparkleSize * 0.28f)
                lineTo(sparkleCenter.x + sparkleSize, sparkleCenter.y)
                lineTo(sparkleCenter.x + sparkleSize * 0.28f, sparkleCenter.y + sparkleSize * 0.28f)
                lineTo(sparkleCenter.x, sparkleCenter.y + sparkleSize)
                lineTo(sparkleCenter.x - sparkleSize * 0.28f, sparkleCenter.y + sparkleSize * 0.28f)
                lineTo(sparkleCenter.x - sparkleSize, sparkleCenter.y)
                lineTo(sparkleCenter.x - sparkleSize * 0.28f, sparkleCenter.y - sparkleSize * 0.28f)
                close()
            }

            onDrawBehind {
                drawCircle(color = discFill, radius = outerR, center = Offset(cx, cy))
                drawArc(
                    color = sheenColor,
                    startAngle = -55f,
                    sweepAngle = 48f,
                    useCenter = true,
                    topLeft = outerTopLeft,
                    size = outerSize
                )
                drawArc(
                    color = sheenColor,
                    startAngle = 125f,
                    sweepAngle = 48f,
                    useCenter = true,
                    topLeft = outerTopLeft,
                    size = outerSize
                )
                drawCircle(color = lineColor, radius = outerR, center = Offset(cx, cy), style = outerStroke)
                drawCircle(color = hubFill, radius = hubR, center = Offset(cx, cy))
                drawCircle(color = lineColor, radius = hubR, center = Offset(cx, cy), style = innerStroke)
                drawCircle(color = holeFill, radius = holeR, center = Offset(cx, cy))
                drawCircle(color = lineColor, radius = holeR, center = Offset(cx, cy), style = innerStroke)
                drawPath(path = sparklePath, color = lineColor)
            }
        }
    )
}

/**
 * Concentric double-border modifier preserved for compatibility.
 */
fun Modifier.nowPlayingCardConcentricBorder(
    cornerRadius: Dp = 22.dp,
    outerColor: Color,
    innerColor: Color
): Modifier = this.drawWithCache {
    val r = cornerRadius.toPx()
    val outerHalf = 0.75.dp.toPx()
    val outerWidth = 1.5.dp.toPx()
    val outerStroke = Stroke(width = outerWidth)
    val inset = 3.5.dp.toPx()
    val innerStroke = Stroke(width = 1.dp.toPx())
    val innerR = (r - inset).coerceAtLeast(0f)

    onDrawWithContent {
        drawContent()
        drawRoundRect(
            color = outerColor,
            topLeft = Offset(outerHalf, outerHalf),
            size = Size(size.width - outerWidth, size.height - outerWidth),
            cornerRadius = CornerRadius(r, r),
            style = outerStroke
        )
        if (size.width > inset * 2 && size.height > inset * 2) {
            drawRoundRect(
                color = innerColor,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius(innerR, innerR),
                style = innerStroke
            )
        }
    }
}

/**
 * Tactile pastel pill transport button (`◄◄`, `❚❚` / `▶`, `►►`).
 * Enforces single-line non-wrapping glyphs and unclipped `softGlowFocus` ring.
 */
@Composable
fun TransportButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    theme: LauncherTheme = LocalLauncherTheme.current
) {
    val pillShape = remember { RoundedCornerShape(18.dp) }
    val bgColor = when {
        isFocused -> theme.focusedContainerColor
        isPrimary -> ActiveTheme.primary.copy(alpha = 0.22f)
        else -> ActiveTheme.unfocusedCardBg
    }
    val borderColor = when {
        isFocused -> ActiveTheme.palette.focusedCardBorder
        isPrimary -> ActiveTheme.primary.copy(alpha = 0.75f)
        else -> ActiveTheme.cardBorder.copy(alpha = 0.75f)
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 18.dp)
            .clip(pillShape)
            .background(bgColor)
            .border(1.2.dp, borderColor, pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (isPrimary) 15.sp else 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = ActiveTheme.accentDeep,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun PlaybackArtistAndTimeRow(
    artist: String,
    mediaState: MochibunMediaState,
    secondTick: () -> Int
) {
    val hasDuration = mediaState.durationMs > 0L
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = artist,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal,
            color = ActiveTheme.textPrimary.copy(alpha = 0.72f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (hasDuration) {
            val durationText = remember(mediaState.durationMs) { formatMediaDuration(mediaState.durationMs) }
            PlaybackTimeText(mediaState = mediaState, durationText = durationText, secondTick = secondTick)
        }
    }
}

/** The ticking "m:ss / m:ss" label: the only part of the card that recomposes once a second. */
@Composable
private fun PlaybackTimeText(
    mediaState: MochibunMediaState,
    durationText: String,
    secondTick: () -> Int
) {
    secondTick() // Subscribes only this scope to the 1 Hz playback tick
    val posMs = computeDisplayPositionMs(mediaState, SystemClock.elapsedRealtime())
    Text(
        text = "${formatMediaDuration(posMs)} / $durationText",
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = ActiveTheme.textPrimary.copy(alpha = 0.62f),
        maxLines = 1,
        softWrap = false
    )
}

/**
 * Compact two-band retro-keitai pocket music player card for the Widgets screen (480x800).
 * Keeps active MediaController state, D-pad transport focus, 9-slice border framing,
 * and overlapping corner mascot pin badge intact.
 */
@Composable
fun NowPlayingCard(
    mediaState: MochibunMediaState,
    focusedControl: Int, // 0 = Prev, 1 = Play/Pause, 2 = Next, -1 = None
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenMediaApp: () -> Unit,
    modifier: Modifier = Modifier,
    // Compact MiniDisc / pocket-deck skin (Widgets page arrange options)
    miniSkin: Boolean = false,
    // Under washi tape / a corner pin: swap a custom 9-slice frame for a plain stitched border
    plainBorder: Boolean = false
) {
    if (miniSkin) {
        NowPlayingMiniDeck(
            mediaState = mediaState,
            focusedControl = focusedControl,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onOpenMediaApp = onOpenMediaApp,
            plainBorder = plainBorder,
            modifier = modifier
        )
        return
    }
    val theme = LocalLauncherTheme.current
    val cardShape = remember { RoundedCornerShape(22.dp) }
    val artShape = remember { RoundedCornerShape(14.dp) }
    val badgeShape = remember { RoundedCornerShape(10.dp) }

    // Bopping along (HAPPY frames + bob) while music plays; otherwise her mood, still frame.
    val mascotStep = rememberKeitaiStep(active = mediaState.isPlaying, totalSteps = MascotAtlasMath.TICK_WRAP)
    val mascotTick = remember(mascotStep) { { mascotStep.intValue } }
    val mascotSlot = if (mediaState.isPlaying) MascotAnimSlot.HAPPY else currentMoodSlot()

    // 1 Hz playback progress ticker, paused automatically off-screen or when paused. Read only via
    // playbackTickProvider in the time label / scrubber draw, never in this body: reading it here
    // recomposed the whole card (marquee title, transport pills) every second.
    val playbackTick = rememberKeitaiStep(
        active = mediaState.isPlaying && mediaState.durationMs > 0L,
        totalSteps = 36_000,
        stepDurationMs = 1_000L
    )
    val playbackTickProvider = remember(playbackTick) { { playbackTick.intValue } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp) // space for overlapping corner mascot pin
            .graphicsLayer { clip = false }
    ) {
        // Main Card Container (unclipped so child pill softGlowFocus halos are never clipped)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false }
                .kawaiiCardMotifs(
                    isFocused = focusedControl != -1,
                    cornerRadius = 22.dp
                )
                .background(color = theme.cardBgColor, shape = cardShape)
                .mochibunCardBorder(
                    cornerRadius = 22.dp,
                    borderColor = theme.cardBorderColor,
                    borderWidth = 1.5.dp,
                    allowCustomNineSlice = !plainBorder,
                    styleOverride = if (plainBorder) CardBorderStyle.STITCH else null
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { clip = false }
            ) {
                // Band 1: Album Art / Pixel Disc + Track Info & Heart Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(artShape)
                            .background(ActiveTheme.surfaceTint.copy(alpha = 0.22f))
                            .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.7f), artShape)
                            .clickable(onClick = onOpenMediaApp),
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
                            PastelMiniDiscIcon(modifier = Modifier.fillMaxSize())
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                            .clickable(onClick = onOpenMediaApp)
                    ) {
                        Text(
                            text = mediaState.title.ifBlank { "No Media Active" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ActiveTheme.accentDeep,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        PlaybackArtistAndTimeRow(
                            artist = mediaState.artist.ifBlank { "Unknown Artist" },
                            mediaState = mediaState,
                            secondTick = playbackTickProvider
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        HeartScrubberProgressBar(
                            progress = 0f,
                            tick = playbackTickProvider,
                            progressProvider = {
                                if (mediaState.durationMs <= 0L) {
                                    0f
                                } else {
                                    val posMs = computeDisplayPositionMs(
                                        mediaState = mediaState,
                                        elapsedRealtimeMs = SystemClock.elapsedRealtime()
                                    )
                                    (posMs.toFloat() / mediaState.durationMs.toFloat()).coerceIn(0f, 1f)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Band 2: Tactile Pastel Pill Transport Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TransportButton(
                        text = "◄◄",
                        isFocused = focusedControl == 0,
                        isPrimary = false,
                        onClick = onPrevious,
                        modifier = Modifier.weight(1f)
                    )
                    TransportButton(
                        text = if (mediaState.isPlaying) "❚❚" else "▶",
                        isFocused = focusedControl == 1,
                        isPrimary = true,
                        onClick = onPlayPause,
                        modifier = Modifier.weight(1f)
                    )
                    TransportButton(
                        text = "►►",
                        isFocused = focusedControl == 2,
                        isPrimary = false,
                        onClick = onNext,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Overlapping Mascot Pin Badge anchored at the top-left edge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = (-18).dp)
                .keitaiMascotBob(mascotTick)
                .background(theme.cardBgColor, badgeShape)
                .border(1.5.dp, theme.cardBorderColor, badgeShape)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            MochibunMascot(
                slot = mascotSlot,
                frameTick = mascotTick,
                size = 30.dp,
                themeTintColor = theme.mascotTintColor
            )
        }
    }
}

/** Steps per disc turn: 8 x 45 degrees at the shared 250ms keitai rhythm = one turn every 2s. */
private const val DISC_STEPS = 8

/**
 * Compact MiniDisc / pocket-deck Now Playing: a stepped spinning disc, a one-line marquee track
 * ticker and three small transport keys. Small enough to tuck over the corner of another card.
 *
 * The disc turns in 45 degree steps on [rememberKeitaiStep] (4 Hz, paused unless RESUMED and
 * playing). The step is read only inside graphicsLayer, so a tick redraws the disc layer and
 * never recomposes the card.
 */
@Composable
private fun NowPlayingMiniDeck(
    mediaState: MochibunMediaState,
    focusedControl: Int,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenMediaApp: () -> Unit,
    plainBorder: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val cardShape = remember { RoundedCornerShape(18.dp) }
    val discStep = rememberKeitaiStep(active = mediaState.isPlaying, totalSteps = DISC_STEPS)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .kawaiiCardMotifs(isFocused = focusedControl != -1, cornerRadius = 18.dp)
            .background(color = theme.cardBgColor, shape = cardShape)
            .mochibunCardBorder(
                cornerRadius = 18.dp,
                borderColor = theme.cardBorderColor,
                borderWidth = 1.5.dp,
                allowCustomNineSlice = !plainBorder,
                styleOverride = if (plainBorder) CardBorderStyle.STITCH else null
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false },
            verticalAlignment = Alignment.CenterVertically
        ) {
            PastelMiniDiscIcon(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = discStep.intValue * (360f / DISC_STEPS) }
                    .clickable(onClick = onOpenMediaApp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clickable(onClick = onOpenMediaApp)
            ) {
                Text(
                    text = mediaState.title.ifBlank { "No Media Active" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ActiveTheme.accentDeep,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                )
                Text(
                    text = mediaState.artist.ifBlank { "Unknown Artist" },
                    fontSize = 10.sp,
                    color = theme.cardTextColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.graphicsLayer { clip = false },
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TransportButton(
                    text = "◄◄",
                    isFocused = focusedControl == 0,
                    onClick = onPrevious,
                    modifier = Modifier.width(36.dp)
                )
                TransportButton(
                    text = if (mediaState.isPlaying) "❚❚" else "▶",
                    isFocused = focusedControl == 1,
                    isPrimary = true,
                    onClick = onPlayPause,
                    modifier = Modifier.width(36.dp)
                )
                TransportButton(
                    text = "►►",
                    isFocused = focusedControl == 2,
                    onClick = onNext,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}
