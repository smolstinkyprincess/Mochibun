/**
 * Mochibun Application Drawer Screen.
 * Manages the scrollable A-Z application list, interactive vertical alphabet rail,
 * and decorative scalloped cloud frames tailored for the Chinoe AT-M120's 480x800 display.
 */
package com.tnyemii.mochibun

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
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

private data class SquircleTint(val container: Color, val border: Color)

// Alternating pastel squircle colors matching Mochibun's reference styling
private val SQUIRCLE_TINTS = listOf(
    SquircleTint(container = Color(0xFFD4F1E8), border = Color(0xFFA5E2CD)), // Mint
    SquircleTint(container = Color(0xFFFCE2E6), border = Color(0xFFF6ACB9)), // Soft Peach/Pink
    SquircleTint(container = Color(0xFFE2D9F7), border = Color(0xFFC7B6EE))  // Soft Lilac
)

private val DRAWER_ALPHABET = ('A'..'Z').toList()

/**
 * Soft diffused pastel glow focus indicator matching Mochibun's physical D-pad contract.
 *
 * Technical note: MediaTek MT6761 GPU drops frames under runtime shaders, so we avoid Modifier.blur().
 * Instead, we draw 4 hardware-accelerated concentric stroke passes with stepping alphas to produce
 * a smooth halo. We enforce 'graphicsLayer { clip = false }' so parent layout boundaries do not
 * slice off the ~3.5 dp outer glow.
 */
fun Modifier.softGlowFocus(
    isSelected: Boolean,
    cornerRadius: Dp = 22.dp,
    glowColor: Color? = null,
    borderColor: Color? = null,
    containerTint: Color? = null
): Modifier = if (!isSelected) this else composed {
    val theme = LocalLauncherTheme.current
    val effectiveGlow = glowColor ?: theme.selectionGlowColor
    val effectiveBorder = borderColor ?: theme.cardBorderColor
    val effectiveTint = containerTint ?: theme.surfaceTint

    this
        .graphicsLayer { clip = false }
        .drawWithContent {
            val cornerPx = cornerRadius.toPx()

            // 1. Concentric halo passes extending 0.6dp to 3.5dp beyond the component boundary
            val glowSteps = listOf(
                Pair(3.5.dp.toPx(), 0.16f),
                Pair(2.4.dp.toPx(), 0.35f),
                Pair(1.4.dp.toPx(), 0.60f),
                Pair(0.6.dp.toPx(), 0.78f)
            )
            for ((expansion, alpha) in glowSteps) {
                drawRoundRect(
                    color = effectiveGlow.copy(alpha = alpha),
                    topLeft = Offset(-expansion, -expansion),
                    size = Size(size.width + expansion * 2f, size.height + expansion * 2f),
                    cornerRadius = CornerRadius(cornerPx + expansion, cornerPx + expansion),
                    style = Stroke(width = 2.0.dp.toPx())
                )
            }

            // 2. Subtle frosted pastel fill over the focused surface
            drawRoundRect(
                color = effectiveTint.copy(alpha = 0.35f),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerPx, cornerPx)
            )

            // 3. Draw card container content
            drawContent()

            // 4. Crisp theme outline stroke defining inner boundary
            drawRoundRect(
                color = effectiveBorder,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
}

/**
 * Backward-compatible bridge for existing components previously calling glowingDoubleBorder.
 * Delegates directly to the soft pastel glow focus indicator.
 */
fun Modifier.glowingDoubleBorder(
    isSelected: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    cornerRadius: Dp = 22.dp
): Modifier = softGlowFocus(
    isSelected = isSelected,
    cornerRadius = cornerRadius
)

/**
 * Standardized Mochibun Section Header formatted as:
 * — ♡ [Screen/Section Name] ♡ —
 */
@Composable
fun MochibunSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = ActiveTheme.accentDeep
) {
    val formatted = if (title.startsWith("— ♡") && title.endsWith("♡ —")) {
        title
    } else {
        "— ♡ $title ♡ —"
    }
    Text(
        text = formatted,
        fontSize = 17.5.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Scalloped pastel lavender cloud trim hanging from the top screen boundary.
 * Rendered using vector Bézier Canvas paths rather than bitmap assets to eliminate
 * texture memory overhead on low-RAM (~1.8 GB available) hardware.
 */
@Composable
fun TopLavenderClouds(
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        val w = size.width

        // Back cloud layer
        val backPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 28.dp.toPx())
            cubicTo(w * 0.05f, 54.dp.toPx(), w * 0.14f, 56.dp.toPx(), w * 0.20f, 38.dp.toPx())
            cubicTo(w * 0.27f, 62.dp.toPx(), w * 0.40f, 64.dp.toPx(), w * 0.47f, 40.dp.toPx())
            cubicTo(w * 0.54f, 60.dp.toPx(), w * 0.67f, 62.dp.toPx(), w * 0.74f, 42.dp.toPx())
            cubicTo(w * 0.81f, 58.dp.toPx(), w * 0.93f, 57.dp.toPx(), w, 32.dp.toPx())
            lineTo(w, 0f)
            close()
        }
        drawPath(backPath, palette.cloudMid)
        drawPath(backPath, palette.outline, style = Stroke(width = 1.5.dp.toPx()))

        // Front cloud layer
        val frontPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 18.dp.toPx())
            cubicTo(w * 0.04f, 42.dp.toPx(), w * 0.12f, 44.dp.toPx(), w * 0.17f, 26.dp.toPx())
            cubicTo(w * 0.24f, 48.dp.toPx(), w * 0.35f, 49.dp.toPx(), w * 0.42f, 28.dp.toPx())
            cubicTo(w * 0.48f, 46.dp.toPx(), w * 0.59f, 48.dp.toPx(), w * 0.65f, 30.dp.toPx())
            cubicTo(w * 0.72f, 49.dp.toPx(), w * 0.85f, 48.dp.toPx(), w * 0.91f, 28.dp.toPx())
            cubicTo(w * 0.96f, 38.dp.toPx(), w * 0.99f, 36.dp.toPx(), w, 20.dp.toPx())
            lineTo(w, 0f)
            close()
        }
        drawPath(frontPath, palette.cloudFront)
        drawPath(frontPath, palette.primary, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * Matching scalloped lavender cloud trim rising from the bottom screen boundary.
 */
@Composable
fun BottomLavenderClouds(
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val palette = theme.palette

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val w = size.width
        val h = size.height

        // Back cloud layer rising from bottom
        val backPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h - 30.dp.toPx())
            cubicTo(w * 0.06f, h - 58.dp.toPx(), w * 0.17f, h - 60.dp.toPx(), w * 0.24f, h - 40.dp.toPx())
            cubicTo(w * 0.31f, h - 64.dp.toPx(), w * 0.44f, h - 66.dp.toPx(), w * 0.51f, h - 44.dp.toPx())
            cubicTo(w * 0.58f, h - 62.dp.toPx(), w * 0.71f, h - 64.dp.toPx(), w * 0.77f, h - 42.dp.toPx())
            cubicTo(w * 0.84f, h - 58.dp.toPx(), w * 0.95f, h - 57.dp.toPx(), w, h - 28.dp.toPx())
            lineTo(w, h)
            close()
        }
        drawPath(backPath, palette.cloudMid)
        drawPath(backPath, palette.outline, style = Stroke(width = 1.5.dp.toPx()))

        // Front cloud layer rising from bottom
        val frontPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h - 20.dp.toPx())
            cubicTo(w * 0.05f, h - 42.dp.toPx(), w * 0.13f, h - 44.dp.toPx(), w * 0.19f, h - 26.dp.toPx())
            cubicTo(w * 0.25f, h - 48.dp.toPx(), w * 0.37f, h - 50.dp.toPx(), w * 0.43f, h - 30.dp.toPx())
            cubicTo(w * 0.50f, h - 46.dp.toPx(), w * 0.61f, h - 48.dp.toPx(), w * 0.67f, h - 32.dp.toPx())
            cubicTo(w * 0.73f, h - 50.dp.toPx(), w * 0.86f, h - 48.dp.toPx(), w * 0.92f, h - 30.dp.toPx())
            cubicTo(w * 0.97f, h - 38.dp.toPx(), w * 0.99f, h - 36.dp.toPx(), w, h - 20.dp.toPx())
            lineTo(w, h)
            close()
        }
        drawPath(frontPath, palette.cloudFront)
        drawPath(frontPath, palette.primary, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * Bottom Home navigation pill button with upward chevron:
 * ^
 * ♥ Back to Home (F4) ♥
 */
@Composable
fun DrawerHomeNavigation(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "♥ Back to Home (F4) ♥"
) {
    val theme = LocalLauncherTheme.current
    val pillShape = CircleShape

    Column(
        modifier = modifier
            .graphicsLayer { clip = false }
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "^",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = theme.palette.textColor
        )
        Box(
            modifier = Modifier
                .graphicsLayer { clip = false }
                .softGlowFocus(
                    isSelected = isFocused,
                    cornerRadius = 20.dp
                )
                .clip(pillShape)
                .background(if (isFocused) theme.focusedContainerColor else theme.unfocusedCardBgColor)
                .then(
                    if (!isFocused) {
                        Modifier.border(1.2.dp, theme.unfocusedCardBorderColor, pillShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = theme.palette.textColor,
                textAlign = TextAlign.Center
            )
        }
    }
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
    text: String = "♥ Close (◄) ♥"
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
    val cardShape = RoundedCornerShape(22.dp)
    val squircleShape = RoundedCornerShape(16.dp)

    val cardBrush = if (isSelected) {
        Brush.horizontalGradient(
            colors = listOf(
                theme.focusedContainerColor,
                theme.focusedContainerEndColor
            )
        )
    } else {
        SolidColor(theme.unfocusedCardBgColor)
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
        shape = cardShape,
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
                        .clip(squircleShape)
                        .background(squircleTint.container)
                        .border(1.dp, squircleTint.border, squircleShape),
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
        DRAWER_ALPHABET.forEach { letter ->
            val isActive = letter == activeLetter
            val isAvailable = letter in availableLetters
            Text(
                text = letter.toString(),
                fontSize = if (isActive) 10.5.sp else 9.5.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = when {
                    isActive -> theme.palette.textColor
                    isAvailable -> theme.palette.textColor.copy(alpha = 0.65f)
                    else -> theme.palette.outline.copy(alpha = 0.5f)
                },
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
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
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

        // 6. Bottom Home Navigation Pill: ^ ♥ Back to Home ♥
        /*
        DrawerHomeNavigation(
            isFocused = selectedDrawerIndex == -1,
            onClick = onBackToHome,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )*/
    }
}
