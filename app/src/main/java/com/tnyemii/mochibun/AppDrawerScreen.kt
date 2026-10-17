package com.tnyemii.mochibun

import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SquircleTint(val container: Color, val border: Color)

/**
 * Three alternating soft pastel squircle colors matching the reference design:
 * 1. Soft Lavender
 * 2. Soft Rose / Coral
 * 3. Seafoam / Mint
 */
private val SQUIRCLE_TINTS = listOf(
    SquircleTint(container = Color(0xFFE4DCF9), border = Color(0xFFC7B6EE)),
    SquircleTint(container = Color(0xFFFDD4DC), border = Color(0xFFF6ACB9)),
    SquircleTint(container = Color(0xFFCEF1E4), border = Color(0xFFA5E2CD))
)

private val DRAWER_ALPHABET = ('A'..'Z').toList()

/**
 * Prominent glowing double-line soft rose/crimson focus indicator matching the reference design.
 * Rendered using lightweight hardware-accelerated Canvas operations to avoid GPU blur overhead on MT6761.
 */
fun Modifier.glowingDoubleBorder(
    isSelected: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    cornerRadius: androidx.compose.ui.unit.Dp = 22.dp
): Modifier = if (!isSelected) this else this.drawWithContent {
    drawContent()
    val cornerPx = cornerRadius.toPx()

    // 1. Outer soft glowing halo (soft rose/crimson with alpha)
    drawRoundRect(
        color = Color(0xFFF07085).copy(alpha = 0.38f),
        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
        size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
        cornerRadius = CornerRadius(cornerPx + 3.dp.toPx(), cornerPx + 3.dp.toPx()),
        style = Stroke(width = 4.5.dp.toPx())
    )

    // 2. Outer crisp soft rose/crimson stroke
    drawRoundRect(
        color = Color(0xFFE5576E),
        topLeft = Offset(-1.5.dp.toPx(), -1.5.dp.toPx()),
        size = Size(size.width + 3.dp.toPx(), size.height + 3.dp.toPx()),
        cornerRadius = CornerRadius(cornerPx + 1.5.dp.toPx(), cornerPx + 1.5.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )

    // 3. Inner crisp stroke forming the double-line
    drawRoundRect(
        color = Color(0xFFF27B8E),
        topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
        size = Size(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
        cornerRadius = CornerRadius(cornerPx - 1.5.dp.toPx(), cornerPx - 1.5.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx())
    )
}

/**
 * Scalloped pastel lavender/lilac clouds hanging from the very top boundary across the entire 480px width.
 * Drawn via lightweight Canvas path passes to avoid GPU overdraw on MediaTek MT6761.
 */
@Composable
fun TopLavenderClouds(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        val w = size.width

        // Back cloud layer (slightly deeper lilac/lavender)
        val backPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 32.dp.toPx())
            cubicTo(w * 0.05f, 62.dp.toPx(), w * 0.14f, 64.dp.toPx(), w * 0.20f, 44.dp.toPx())
            cubicTo(w * 0.27f, 70.dp.toPx(), w * 0.40f, 72.dp.toPx(), w * 0.47f, 46.dp.toPx())
            cubicTo(w * 0.54f, 68.dp.toPx(), w * 0.67f, 70.dp.toPx(), w * 0.74f, 48.dp.toPx())
            cubicTo(w * 0.81f, 66.dp.toPx(), w * 0.93f, 65.dp.toPx(), w, 36.dp.toPx())
            lineTo(w, 0f)
            close()
        }
        drawPath(backPath, Color(0xFFD5BAE8))
        drawPath(backPath, Color(0xFFBE9FD8), style = Stroke(width = 1.5.dp.toPx()))

        // Front cloud layer (lighter pastel lilac/lavender)
        val frontPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 22.dp.toPx())
            cubicTo(w * 0.04f, 48.dp.toPx(), w * 0.12f, 50.dp.toPx(), w * 0.17f, 30.dp.toPx())
            cubicTo(w * 0.24f, 54.dp.toPx(), w * 0.35f, 55.dp.toPx(), w * 0.42f, 32.dp.toPx())
            cubicTo(w * 0.48f, 52.dp.toPx(), w * 0.59f, 54.dp.toPx(), w * 0.65f, 34.dp.toPx())
            cubicTo(w * 0.72f, 55.dp.toPx(), w * 0.85f, 54.dp.toPx(), w * 0.91f, 32.dp.toPx())
            cubicTo(w * 0.96f, 44.dp.toPx(), w * 0.99f, 42.dp.toPx(), w, 24.dp.toPx())
            lineTo(w, 0f)
            close()
        }
        drawPath(frontPath, Color(0xFFEADEF8))
        drawPath(frontPath, Color(0xFFC5ACDD), style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * Matching layered scalloped lavender clouds rising from the bottom screen boundary.
 */
@Composable
fun BottomLavenderClouds(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
    ) {
        val w = size.width
        val h = size.height

        // Back cloud layer rising from bottom
        val backPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h - 36.dp.toPx())
            cubicTo(w * 0.06f, h - 68.dp.toPx(), w * 0.17f, h - 70.dp.toPx(), w * 0.24f, h - 46.dp.toPx())
            cubicTo(w * 0.31f, h - 74.dp.toPx(), w * 0.44f, h - 76.dp.toPx(), w * 0.51f, h - 50.dp.toPx())
            cubicTo(w * 0.58f, h - 72.dp.toPx(), w * 0.71f, h - 74.dp.toPx(), w * 0.77f, h - 48.dp.toPx())
            cubicTo(w * 0.84f, h - 66.dp.toPx(), w * 0.95f, h - 65.dp.toPx(), w, h - 34.dp.toPx())
            lineTo(w, h)
            close()
        }
        drawPath(backPath, Color(0xFFD5BAE8))
        drawPath(backPath, Color(0xFFBE9FD8), style = Stroke(width = 1.5.dp.toPx()))

        // Front cloud layer rising from bottom
        val frontPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h - 24.dp.toPx())
            cubicTo(w * 0.05f, h - 50.dp.toPx(), w * 0.13f, h - 52.dp.toPx(), w * 0.19f, h - 32.dp.toPx())
            cubicTo(w * 0.25f, h - 56.dp.toPx(), w * 0.37f, h - 58.dp.toPx(), w * 0.43f, h - 34.dp.toPx())
            cubicTo(w * 0.50f, h - 54.dp.toPx(), w * 0.61f, h - 56.dp.toPx(), w * 0.67f, h - 36.dp.toPx())
            cubicTo(w * 0.73f, h - 58.dp.toPx(), w * 0.86f, h - 56.dp.toPx(), w * 0.92f, h - 34.dp.toPx())
            cubicTo(w * 0.97f, h - 44.dp.toPx(), w * 0.99f, h - 42.dp.toPx(), w, h - 24.dp.toPx())
            lineTo(w, h)
            close()
        }
        drawPath(frontPath, Color(0xFFEADEF8))
        drawPath(frontPath, Color(0xFFC5ACDD), style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * Bottom Home navigation pill button with upward chevron:
 * ^
 * ♥ Back to Home ♥
 */
@Composable
fun DrawerHomeNavigation(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(20.dp)

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
            color = Color(0xFF381E44)
        )
        Box(
            modifier = Modifier
                .graphicsLayer { clip = false }
                .then(
                    if (isFocused) {
                        Modifier.glowingDoubleBorder(true, pillShape)
                    } else {
                        Modifier
                    }
                )
                .clip(pillShape)
                .background(Color.White)
                .border(1.5.dp, Color(0xFFDAC7E8), pillShape)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "♥ Back to Home ♥",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF331B3F),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * High-contrast frosted white app row card without item indices (per strict visual directive).
 * Features squircle icon containers with alternating soft pastel tints and deep violet typography.
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
    val squircleTint = SQUIRCLE_TINTS[index % SQUIRCLE_TINTS.size]
    val cardShape = RoundedCornerShape(22.dp)
    val squircleShape = RoundedCornerShape(14.dp)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "drawerCardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .glowingDoubleBorder(isSelected = isSelected, shape = cardShape)
            .combinedClickable(
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) }
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = if (isSelected) null else BorderStroke(1.2.dp, Color(0xFFF0E2EC)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle container with alternating pastel tint
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(squircleShape)
                    .background(squircleTint.container)
                    .border(1.dp, squircleTint.border, squircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = app.imageBitmap,
                    contentDescription = app.name,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // App Title in deep violet typography
            Text(
                text = app.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D1B36),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Vertical alphabetical rail (A–Z Index) along the far-right edge in soft muted purple text.
 * Touch and drag-scrub friendly for 480 × 800 layout.
 */
@Composable
fun DrawerAlphabetRail(
    availableLetters: Set<Char>,
    activeLetter: Char? = null,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    isActive -> Color(0xFF381E44)
                    isAvailable -> Color(0xFF8E73A6)
                    else -> Color(0xFFC7B9D6)
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
 * Faithfully replicates the reference visual layout, card styling, and lavender cloud borders
 * optimized for the Chinoe AT-M120 (MediaTek MT6761, 480 × 800 display).
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFF0F3))
            // Prevent pointer events from falling through to home screen behind the drawer
            .pointerInput(Unit) {
                detectTapGestures { }
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
                .padding(start = 14.dp, end = 34.dp),
            contentPadding = PaddingValues(top = 84.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                .padding(top = 56.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "— ♡ Applications ♡ —",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF381E44),
                textAlign = TextAlign.Center
            )
        }

        // 4. Vertical Alphabetical Rail (A-Z) along the right edge
        DrawerAlphabetRail(
            availableLetters = letterStarts.keys,
            activeLetter = currentDrawerLetter,
            onLetterTap = onLetterTap,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 80.dp, bottom = 84.dp, end = 4.dp)
                .fillMaxHeight()
        )

        // 5. Bottom Scalloped Lavender Clouds
        BottomLavenderClouds(
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 6. Bottom Home Navigation Pill: ^ ♥ Back to Home (F4) ♥
        DrawerHomeNavigation(
            isFocused = selectedDrawerIndex == -1,
            onClick = onBackToHome,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        )
    }
}
