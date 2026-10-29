package com.crinklecodone.mochibun.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.ui.components.BottomLavenderClouds
import com.crinklecodone.mochibun.ui.components.JumpNumberBadge
import com.crinklecodone.mochibun.ui.components.TopLavenderClouds
import com.crinklecodone.mochibun.ui.components.softGlowFocus
import com.crinklecodone.mochibun.ui.dialogs.PixelRibbonBow
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme

/**
 * Standardized Settings Screen Scaffold for the Chinoe AT-M120 (480x800).
 * Features vector Bézier scalloped cloud framing, centered kawaii title, and back navigation pill.
 */
@Composable
fun SettingsScaffold(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalLauncherTheme.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ActiveTheme.background)
    ) {
        // Content container
        content()

        // 1. Top Decorative Scalloped Clouds
        TopLavenderClouds(
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 2. Bottom Decorative Scalloped Clouds
        BottomLavenderClouds(
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 3. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (showBack) {
                // Back button pill
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ActiveTheme.container)
                        .border(1.5.dp, ActiveTheme.cardBorder, CircleShape)
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◄ Back",
                        color = ActiveTheme.accentDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                PixelRibbonBow(
                    width = 28.dp,
                    height = 23.dp
                )
            }

            // Centered Title
            Text(
                text = "— ♡ $title ♡ —",
                color = ActiveTheme.accentDeep,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            // Right decorative ribbon balance
            PixelRibbonBow(
                width = 26.dp,
                height = 21.dp
            )
        }
    }
}

/**
 * Themed settings card container with rounded corners (22.dp) and category pill.
 */
@Composable
fun SettingsCard(
    categoryTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Category Pill
        Row(
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                    .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "♡ $categoryTitle ♡",
                    color = ActiveTheme.accentDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Card Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ActiveTheme.container)
                .border(1.5.dp, ActiveTheme.cardBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            content()
        }
    }
}

/**
 * Navigation row leading to a dedicated sub-screen.
 */
@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String? = null,
    icon: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    jumpNumber: Int? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent
    val borderStroke = if (isFocused) {
        BorderStroke(1.5.dp, ActiveTheme.selectionGlow)
    } else {
        BorderStroke(0.dp, Color.Transparent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .border(borderStroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading squircle icon badge
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ActiveTheme.surfaceTint.copy(alpha = 0.45f))
                .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Trailing Navigation Arrow & Jump Number
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = isFocused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            Text(
                text = "›",
                color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.cardBorder,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Kawaii Toggle Switch Row (Boolean preference).
 */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    jumpNumber: Int? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent
    val borderStroke = if (isFocused) {
        BorderStroke(1.5.dp, ActiveTheme.selectionGlow)
    } else {
        BorderStroke(0.dp, Color.Transparent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .border(borderStroke, shape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = isFocused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Pill-shaped kawaii switch
            val switchBg = if (checked) ActiveTheme.primary else ActiveTheme.cardBorder.copy(alpha = 0.4f)
            val switchText = if (checked) "ON ♡" else "OFF"
            val switchTextColor = if (checked) ActiveTheme.accentDeep else ActiveTheme.textPrimary.copy(alpha = 0.6f)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(switchBg, CircleShape)
                    .border(1.dp, ActiveTheme.cardBorder, CircleShape)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = switchText,
                    color = switchTextColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Numeric Stepper Row supporting physical D-pad Left/Right navigation.
 */
@Composable
fun SettingsStepperRow(
    title: String,
    subtitle: String? = null,
    valueDisplay: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    jumpNumber: Int? = null,
    onClick: (() -> Unit)? = null,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent
    val borderStroke = if (isFocused) {
        BorderStroke(1.5.dp, ActiveTheme.selectionGlow)
    } else {
        BorderStroke(0.dp, Color.Transparent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .border(borderStroke, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onClick?.invoke() ?: onIncrement() }
        ) {
            Text(
                text = title,
                color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Stepper controls
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = isFocused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Decrement button
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ActiveTheme.container)
                    .border(1.dp, ActiveTheme.cardBorder, CircleShape)
                    .clickable { onDecrement() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◄",
                    color = ActiveTheme.accentDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Value badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.45f))
                    .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                    .clickable { onIncrement() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = valueDisplay,
                    color = ActiveTheme.accentDeep,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Increment button
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ActiveTheme.container)
                    .border(1.dp, ActiveTheme.cardBorder, CircleShape)
                    .clickable { onIncrement() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "►",
                    color = ActiveTheme.accentDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Text input row opening focused inline / modal editing.
 */
@Composable
fun SettingsTextInputRow(
    title: String,
    subtitle: String? = null,
    value: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    jumpNumber: Int? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent
    val borderStroke = if (isFocused) {
        BorderStroke(1.5.dp, ActiveTheme.selectionGlow)
    } else {
        BorderStroke(0.dp, Color.Transparent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 14.dp)
            .clip(shape)
            .background(bg, shape)
            .border(borderStroke, shape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isFocused) ActiveTheme.accentDeep else ActiveTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = ActiveTheme.textPrimary.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (jumpNumber != null) {
                JumpNumberBadge(
                    digit = jumpNumber,
                    isFocused = isFocused,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveTheme.surfaceTint.copy(alpha = 0.45f))
                    .border(1.dp, ActiveTheme.cardBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.ifBlank { "Not set" },
                    color = ActiveTheme.accentDeep,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Informational display row for read-only system and hardware statistics.
 */
@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isFocused) ActiveTheme.surfaceTint.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = ActiveTheme.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = ActiveTheme.accentDeep,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Action button row (e.g. Reset, Pick, Launch).
 */
@Composable
fun SettingsActionRow(
    title: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
    jumpNumber: Int? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        isFocused && isDestructive -> Color(0xFFFFEBEE)
        isFocused -> ActiveTheme.surfaceTint.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isDestructive -> Color(0xFFD32F2F)
        isFocused -> ActiveTheme.accentDeep
        else -> ActiveTheme.textPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 12.dp)
            .clip(shape)
            .background(bg, shape)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) ActiveTheme.selectionGlow else ActiveTheme.cardBorder.copy(alpha = 0.6f),
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        if (jumpNumber != null) {
            JumpNumberBadge(
                digit = jumpNumber,
                isFocused = isFocused,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
