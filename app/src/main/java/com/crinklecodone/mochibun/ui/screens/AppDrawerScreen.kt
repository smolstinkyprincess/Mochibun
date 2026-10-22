/**
 * Mochibun Application Drawer Screen.
 * Manages the scrollable A-Z application list, interactive vertical alphabet rail,
 * and decorative scalloped cloud frames tailored for the Chinoe AT-M120's 480x800 display.
 */
package com.crinklecodone.mochibun.ui.screens

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.ui.theme.*
import com.crinklecodone.mochibun.ui.components.*

private data class SquircleTint(val container: Color, val border: Color)

// Alternating pastel squircle colors matching Mochibun's reference styling
private val SQUIRCLE_TINTS = listOf(
    SquircleTint(container = Color(0xFFD4F1E8), border = Color(0xFFA5E2CD)), // Mint
    SquircleTint(container = Color(0xFFFCE2E6), border = Color(0xFFF6ACB9)), // Soft Peach/Pink
    SquircleTint(container = Color(0xFFE2D9F7), border = Color(0xFFC7B6EE))  // Soft Lilac
)

private val DrawerCardShape = RoundedCornerShape(22.dp)
private val DrawerSquircleShape = RoundedCornerShape(16.dp)

private val DRAWER_ALPHABET = ('A'..'Z').toList()
private val DRAWER_ALPHABET_STRINGS = ('A'..'Z').map { it.toString() }

// Helper for A-Z section grouping and fast scrolling
fun firstLetterOf(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/**
 * Standardized close pill button for dialogs and modals:
 * ♥ Close (◄) ♥
 */
@Composable
fun KawaiiClosePillButton(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "♥ Close ♥"
) {
    val theme = LocalLauncherTheme.current
    val pillShape = CircleShape

    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(
                isSelected = isFocused,
                cornerRadius = 20.dp
            )
            .clip(pillShape)
            .background(if (isFocused) theme.focusedContainerColor else theme.unfocusedCardBgColor)
            .border(
                1.2.dp,
                if (isFocused) theme.cardBorderColor else theme.unfocusedCardBorderColor,
                pillShape
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = theme.palette.deepAccent,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Soft pill-curved app card in the drawer.
 * Renders an unclipped soft glow on D-pad selection and uses FilterQuality.None on icon images
 * to preserve crisp 1:1 pixel rendering on the 480x800 screen.
 */
@Composable
fun AppDrawerCard(
    app: LauncherApp,
    index: Int,
    isSelected: Boolean,
    onAppClick: (LauncherApp) -> Unit,
    onAppLongClick: (LauncherApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val squircleTint = SQUIRCLE_TINTS[index % SQUIRCLE_TINTS.size]

    val cardBrush = remember(isSelected, theme) {
        if (isSelected) {
            Brush.horizontalGradient(
                colors = listOf(
                    theme.focusedContainerColor,
                    theme.focusedContainerEndColor
                )
            )
        } else {
            SolidColor(theme.unfocusedCardBgColor)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isSelected, cornerRadius = 22.dp)
            .combinedClickable(
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) }
            ),
        shape = DrawerCardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = if (isSelected) null else BorderStroke(1.dp, theme.unfocusedCardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Squircle badge with alternating pastel tint
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(DrawerSquircleShape)
                        .background(squircleTint.container)
                        .border(1.dp, squircleTint.border, DrawerSquircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // FilterQuality.None avoids bilinear blurring on low-resolution / pixel icons at 480x800
                    Image(
                        bitmap = app.imageBitmap,
                        contentDescription = app.name,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // App Title in deep mauve/violet typography
                Text(
                    text = app.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.palette.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AlphabetRailItem(
    letterStr: String,
    isActive: Boolean,
    isAvailable: Boolean,
    theme: LauncherTheme,
    modifier: Modifier = Modifier
) {
    Text(
        text = letterStr,
        fontSize = if (isActive) 10.5.sp else 9.5.sp,
        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = when {
            isActive -> theme.palette.textColor
            isAvailable -> theme.palette.textColor.copy(alpha = 0.65f)
            else -> theme.palette.outline.copy(alpha = 0.5f)
        },
        modifier = modifier
    )
}

/**
 * Vertical alphabetical rail (A–Z Index) along the far-right edge.
 * Supports both direct tap and continuous drag-scrubbing across the 800px vertical height.
 */
@Composable
fun DrawerAlphabetRail(
    availableLetters: Set<Char>,
    activeLetter: Char? = null,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val currentOnLetterTap by rememberUpdatedState(onLetterTap)

    Column(
        modifier = modifier
            .width(22.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val height = size.height
                    if (height > 0) {
                        val idx = ((offset.y / height) * DRAWER_ALPHABET.size)
                            .toInt()
                            .coerceIn(0, DRAWER_ALPHABET.lastIndex)
                        currentOnLetterTap(DRAWER_ALPHABET[idx])
                    }
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    val height = size.height
                    if (height > 0) {
                        val idx = ((change.position.y / height) * DRAWER_ALPHABET.size)
                            .toInt()
                            .coerceIn(0, DRAWER_ALPHABET.lastIndex)
                        currentOnLetterTap(DRAWER_ALPHABET[idx])
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        DRAWER_ALPHABET.forEachIndexed { index, letter ->
            val isActive = letter == activeLetter
            val isAvailable = letter in availableLetters
            AlphabetRailItem(
                letterStr = DRAWER_ALPHABET_STRINGS[index],
                isActive = isActive,
                isAvailable = isAvailable,
                theme = theme,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * Mochibun Kawaii App Drawer Screen.
 * Renders the scrollable app collection with edge-to-edge cloud overlays and physical D-pad focus contracts.
 */
@Composable
fun AppDrawerScreen(
    context: Context,
    visibleApps: List<LauncherApp>,
    selectedDrawerIndex: Int,
    currentDrawerLetter: Char?,
    letterStarts: Map<Char, Int>,
    listState: LazyListState = rememberLazyListState(),
    onAppClick: (LauncherApp) -> Unit,
    onAppLongClick: (LauncherApp) -> Unit,
    onBackToHome: () -> Unit,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.bgColor)
            // Focus isolation / event shield: Consume tap events so touches never leak to Home items underneath
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            // Intercept hardware keys at the root level before children can consume them
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        // F4 is the AT-M120 physical hardware shortcut key
                        KeyEvent.KEYCODE_F4,
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            onBackToHome()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // 1. Scrollable App Cards List
        // graphicsLayer { clip = false } is critical here so card selection halos aren't cut off by the list viewport
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
                .padding(start = 14.dp, end = 32.dp),
            contentPadding = PaddingValues(top = 74.dp, bottom = 86.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                visibleApps,
                key = { _, app -> app.componentKey }
            ) { index, app ->
                AppDrawerCard(
                    app = app,
                    index = index,
                    isSelected = index == selectedDrawerIndex,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick
                )
            }
        }

        // 2. Top Scalloped Lavender Clouds Drape
        TopLavenderClouds(
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Decorative Top Header Title: — ♡ Applications ♡ —
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            MochibunSectionHeader("Applications")
        }

        // 4. Vertical Alphabetical Rail (A-Z) along the right edge
        DrawerAlphabetRail(
            availableLetters = letterStarts.keys,
            activeLetter = currentDrawerLetter,
            onLetterTap = onLetterTap,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 74.dp, bottom = 80.dp, end = 4.dp)
                .fillMaxHeight()
        )

        // 5. Bottom Scalloped Lavender Clouds
        BottomLavenderClouds(
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
