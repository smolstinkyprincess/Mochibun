package com.crinklecodone.mochibun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.focusedContainerColor
import kotlinx.coroutines.delay

/**
 * 8-step x 250ms ticker (2000ms period), same rhythm as the Home "♡ ▼ menu ▼ ♡" prompt.
 * Runs only while [active] and the activity is RESUMED; resets to step 0 when inactive.
 * Read the returned state in a layout-phase lambda so a tick never forces a measure pass.
 */
@Composable
private fun rememberKeitaiStep(active: Boolean): IntState {
    val lifecycleOwner = LocalLifecycleOwner.current
    val step = remember { mutableIntStateOf(0) }
    LaunchedEffect(active, lifecycleOwner) {
        if (!active) {
            step.intValue = 0
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(250)
                step.intValue = (step.intValue + 1) % 8
            }
        }
    }
    return step
}

// Integer dp bob: 0,1,2,1 repeating each second
private fun bobDpFor(step: Int): Int = when (step % 4) { 1, 3 -> 1; 2 -> 2; else -> 0 }

/**
 * Shared pill body: "{heart} {chevron} {label} {chevron} {heart}". The hearts flip ♡/♥ each
 * second (a composition read, but derivedStateOf limits it to one recomposition per flip);
 * the chevron bob is read only inside Modifier.offset's layout lambda.
 */
@Composable
private fun KeitaiNavPill(
    label: String,
    chevron: String,
    bobOffset: (bobPx: Int) -> IntOffset,
    isFocused: Boolean,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val step = rememberKeitaiStep(animate)
    val heart by remember { derivedStateOf { if (step.intValue >= 4) "♥" else "♡" } }
    val bobModifier = Modifier.offset {
        val bobPx = bobDpFor(step.intValue).dp.roundToPx()
        bobOffset(bobPx)
    }
    val textColor = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary
    val shape = CircleShape

    @Composable
    fun Glyph(text: String, glyphModifier: Modifier = Modifier) = Text(
        text = text,
        modifier = glyphModifier,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = textColor,
        maxLines = 1
    )

    Box(
        modifier = modifier
            .height(36.dp)
            .graphicsLayer { clip = false }
            .softGlowFocus(
                isSelected = isFocused,
                cornerRadius = 20.dp,
                glowColor = ActiveTheme.selectionGlow,
                borderColor = ActiveTheme.primary,
                containerTint = ActiveTheme.surfaceTint
            )
            .clip(shape)
            .background(if (isFocused) ActiveTheme.current.focusedContainerColor else Color(0xFFF7F1FA))
            .border(
                width = if (isFocused) 1.5.dp else 1.2.dp,
                color = if (isFocused) ActiveTheme.primary else ActiveTheme.cardBorder,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Fixed-width heart slots so the ♡/♥ flip never shifts the label
            Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) { Glyph(heart) }
            Glyph(chevron, bobModifier)
            Glyph(label)
            Glyph(chevron, bobModifier)
            Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) { Glyph(heart) }
        }
    }
}

/**
 * Dialog / modal dismiss pill: "♡ ◄ Close ◄ ♡". Chevrons nudge left while focused
 * (or always, with [animateAlways]).
 */
@Composable
fun KawaiiClosePill(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "close",
    animateAlways: Boolean = false
) {
    KeitaiNavPill(
        label = label,
        chevron = "◄",
        bobOffset = { IntOffset(-it, 0) },
        isFocused = isFocused,
        animate = isFocused || animateAlways,
        onClick = onClick,
        modifier = modifier
    )
}

/** Which way Home lies from the screen hosting a [KawaiiReturnHomePill]. */
enum class HomePillDirection {
    UP,    // App Drawer: ▲, bobs up
    LEFT,  // Notifications (page 2 -> 1): ◀, bobs left
    RIGHT  // Widgets/Stats (page 0 -> 1): ▶, bobs right
}

/**
 * Screen return pill: "♡ ▲ Home ▲ ♡". Chevrons point and bob toward Home while focused.
 */
@Composable
fun KawaiiReturnHomePill(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "home",
    direction: HomePillDirection = HomePillDirection.UP,
    animateAlways: Boolean = false
) {
    val chevron = when (direction) {
        HomePillDirection.UP -> "▲"
        HomePillDirection.LEFT -> "◀"
        HomePillDirection.RIGHT -> "▶"
    }
    val bobOffset: (Int) -> IntOffset = when (direction) {
        HomePillDirection.UP -> { bobPx -> IntOffset(0, -bobPx) }
        HomePillDirection.LEFT -> { bobPx -> IntOffset(-bobPx, 0) }
        HomePillDirection.RIGHT -> { bobPx -> IntOffset(bobPx, 0) }
    }
    KeitaiNavPill(
        label = label,
        chevron = chevron,
        bobOffset = bobOffset,
        isFocused = isFocused,
        animate = isFocused || animateAlways,
        onClick = onClick,
        modifier = modifier
    )
}
