package com.crinklecodone.mochibun.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 4-frame stepped keitai mascot bob (0px -> 1px -> 2px -> 1px).
 * Ticks strictly at 4 Hz (250ms), keeping idle CPU usage under 0.5%.
 */
@Composable
fun Modifier.keitaiMascotBob(enabled: Boolean = true): Modifier {
    if (!enabled) return this

    // 4 discrete stepped offsets in screen pixels
    val bobOffsets = listOf(0, 1, 2, 1)

    val stepIndex by produceState(initialValue = 0, key1 = enabled) {
        while (enabled) {
            delay(250L) // 4 FPS clock
            value = (value + 1) % bobOffsets.size
        }
    }

    return this.offset {
        IntOffset(x = 0, y = bobOffsets[stepIndex])
    }
}

/**
 * Keitai "Curtain / Blinds" Modal Unfurl:
 * Replaces modern alpha-fading with a snappy vertical unroll from the top edge downward.
 * Uses GPU scissor clipping to keep render passes cheap on low-spec hardware.
 *
 * Starts rolled up and unrolls on entry in [CURTAIN_STEPS] discrete blinds steps. Each step is
 * held for at least [CURTAIN_STEP_MS] and gets its own drawn frame, so a heavy dialog's slow first
 * frame on the MT6761 can't swallow the whole unroll (a 140 ms tween finished before its second
 * frame). Stepping with delay() also means the system animator duration scale (0 on this device)
 * doesn't skip it. The progress is read only in the graphics layer, so each step redraws the layer
 * without recomposing the dialog.
 */
fun Modifier.curtainUnfurl(visible: Boolean): Modifier = composed {
    val progress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) {
        val from = progress.floatValue
        val target = if (visible) 1f else 0f
        for (step in 1..CURTAIN_STEPS) {
            delay(CURTAIN_STEP_MS)
            withFrameNanos { progress.floatValue = from + (target - from) * step / CURTAIN_STEPS }
        }
    }

    this
        .clip(RectangleShape)
        .graphicsLayer {
            scaleY = progress.floatValue
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
}

private const val CURTAIN_STEPS = 5
private const val CURTAIN_STEP_MS = 28L

/**
 * Stepped Keitai Marquee String Generator:
 * Takes a string and returns a cyclic slice that steps by character offsets at a discrete 3 Hz (320ms).
 * Avoids 60 FPS sub-pixel text measurement while providing authentic flip-phone ticker vibes.
 */
@Composable
fun rememberSteppedTickerText(
    text: String,
    visibleCharCount: Int = 16,
    enabled: Boolean = true
): String {
    if (!enabled || text.length <= visibleCharCount) return text

    val padded = remember(text) { "$text   ★   " }
    var offset by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(padded, enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(320L) // 3 Hz tick
            offset = (offset + 1) % padded.length
        }
    }

    return remember(offset, padded) {
        val end = offset + visibleCharCount
        if (end <= padded.length) {
            padded.substring(offset, end)
        } else {
            padded.substring(offset) + padded.substring(0, end - padded.length)
        }
    }
}

/**
 * Ergonomic composable wrapper for the authentic Galakei focus pop bounce.
 * Wraps content with spring scale bounce and tactile haptic feedback on focus transition.
 */
@Composable
fun KeitaiFocusPop(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isSelected, cornerRadius = cornerRadius),
        content = content
    )
}

/**
 * Modifier alias for [softGlowFocus] providing authentic keitai spring scale bounce
 * and focus glow on selection.
 */
fun Modifier.focusPop(
    isSelected: Boolean,
    cornerRadius: Dp = 22.dp
): Modifier = softGlowFocus(
    isSelected = isSelected,
    cornerRadius = cornerRadius
)
