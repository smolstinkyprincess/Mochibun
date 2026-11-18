package one.crinklecod.mochibun.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.core.keypad.PET_PILL_CHAT
import one.crinklecod.mochibun.core.keypad.PET_PILL_FEED
import one.crinklecod.mochibun.core.keypad.PET_PILL_PET
import one.crinklecod.mochibun.pet.PetLogic
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.pet.PetTuning
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.MaruMinyaMFontFamily

private val PillShape = RoundedCornerShape(18.dp)
private val ButtonShape = RoundedCornerShape(14.dp)
private val StatsChipShape = RoundedCornerShape(12.dp)
private val LowStatHeartColor = Color(0xFFF06292)

/**
 * Mochibunny's floating Feed / Pet / Chat pill, drawn next to her on Home.
 * When [showStats] is true (after tapping Chat), a tiny stat chip with `maruminyam` ♥/♡ glyphs
 * floats just above the right side of the pill without shifting the button row.
 *
 * Focus contract: there are no focusable nodes here. The pill is an `ActiveModal`, and
 * `LauncherKeyHandler` drives [selectedIndex] from the D-pad / keypad; touch taps call
 * [onOptionClick] directly. The selected button gets the shared soft glow (unclipped).
 * Plain alpha translucency only (no blur).
 */
@Composable
fun PetActionPill(
    selectedIndex: Int,
    onOptionClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showStats: Boolean = false
) {
    // Only the pill re-reads the pet state, and the state only changes on pet events.
    val petState by PetManager.state.collectAsState()
    val treats = petState.treats

    Box(modifier = modifier.graphicsLayer { clip = false }) {
        AnimatedVisibility(
            visible = showStats,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-24).dp)
        ) {
            PetStatsChip(
                fullness = petState.fullness,
                joy = petState.joy
            )
        }

        Row(
            modifier = Modifier
                .graphicsLayer { clip = false }
                .background(ActiveTheme.surface.copy(alpha = 0.94f), PillShape)
                .border(1.5.dp, ActiveTheme.cardBorder, PillShape)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PetPillButton(
                label = "🍡 Feed ×$treats",
                isSelected = selectedIndex == PET_PILL_FEED,
                // Still selectable at zero: she then explains how to earn treats
                isDimmed = treats == 0,
                onClick = { onOptionClick(PET_PILL_FEED) }
            )
            PetPillButton(
                label = "♡ Pet",
                isSelected = selectedIndex == PET_PILL_PET,
                onClick = { onOptionClick(PET_PILL_PET) }
            )
            PetPillButton(
                label = "💬 Chat",
                isSelected = selectedIndex == PET_PILL_CHAT,
                onClick = { onOptionClick(PET_PILL_CHAT) }
            )
        }
    }
}

@Composable
private fun PetStatsChip(
    fullness: Float,
    joy: Float,
    modifier: Modifier = Modifier
) {
    val accent = ActiveTheme.accentDeep
    Row(
        modifier = modifier
            .background(ActiveTheme.surface.copy(alpha = 0.94f), StatsChipShape)
            .border(1.25.dp, ActiveTheme.cardBorder, StatsChipShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatHeartsPair(
            icon = "🍡",
            usePixelFontForIcon = false,
            value = fullness,
            isLow = fullness < PetTuning.HUNGRY_BELOW,
            accent = accent
        )
        StatHeartsPair(
            icon = "♡",
            usePixelFontForIcon = true,
            value = joy,
            isLow = joy < PetTuning.SULKY_BELOW,
            accent = accent
        )
    }
}

@Composable
private fun StatHeartsPair(
    icon: String,
    usePixelFontForIcon: Boolean,
    value: Float,
    isLow: Boolean,
    accent: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontFamily = if (usePixelFontForIcon) MaruMinyaMFontFamily else null,
            fontSize = if (usePixelFontForIcon) 11.sp else 10.sp,
            lineHeight = 13.sp,
            color = accent,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = PetLogic.statHearts(value),
            fontFamily = MaruMinyaMFontFamily,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = if (isLow) LowStatHeartColor else accent,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PetPillButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDimmed: Boolean = false
) {
    val accent = ActiveTheme.accentDeep
    Box(
        modifier = Modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isSelected, cornerRadius = 14.dp, bringIntoView = false)
            .background(
                if (isSelected) ActiveTheme.primary.copy(alpha = 0.55f) else ActiveTheme.container,
                ButtonShape
            )
            .border(1.5.dp, if (isSelected) accent else ActiveTheme.cardBorder, ButtonShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            // Sized so the whole pill fits left of her on a 480 dp wide screen
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = accent.copy(alpha = if (isDimmed) 0.45f else 1f),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Where the pill is on screen, for "tap outside closes it". A plain holder (not Compose state):
 * positions update every layout without causing recomposition.
 */
class PetPillBounds {
    var root: LayoutCoordinates? = null
    var pill: LayoutCoordinates? = null

    /** Whether [position] (in [root]'s coordinates) is inside the pill. */
    fun contains(position: Offset): Boolean {
        val r = root ?: return false
        val p = pill ?: return false
        if (!r.isAttached || !p.isAttached) return false
        return r.localBoundingBoxOf(p, clipBounds = false).contains(position)
    }
}
