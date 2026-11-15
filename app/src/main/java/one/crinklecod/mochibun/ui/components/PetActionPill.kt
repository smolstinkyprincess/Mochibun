package one.crinklecod.mochibun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.crinklecod.mochibun.core.keypad.PET_PILL_CHAT
import one.crinklecod.mochibun.core.keypad.PET_PILL_FEED
import one.crinklecod.mochibun.core.keypad.PET_PILL_PET
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.ui.theme.ActiveTheme

private val PillShape = RoundedCornerShape(18.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

/**
 * Mochibunny's floating Feed / Pet / Chat pill, drawn next to her on Home.
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
    modifier: Modifier = Modifier
) {
    // Only the pill re-reads the treat count, and the state only changes on pet events.
    val petState by PetManager.state.collectAsState()
    val treats = petState.treats

    Row(
        modifier = modifier
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
