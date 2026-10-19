package com.tnyemii.mochibun

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ALPHABET_LETTERS = ('A'..'Z').toList() + '#'

// alphabet strip in appdrawer
fun firstLetterOf(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
fun AlphabetIndex(
    availableLetters: Set<Char>,
    activeLetter: Char? = null,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val fgTextColor = theme.fgTextColor
    val appActionColor = theme.appActionColor
    val currentOnLetterTap by rememberUpdatedState(onLetterTap)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.greetingBgColor.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val height = size.height
                    if (height > 0) {
                        val idx = ((offset.y / height) * ALPHABET_LETTERS.size)
                            .toInt()
                            .coerceIn(0, ALPHABET_LETTERS.lastIndex)
                        currentOnLetterTap(ALPHABET_LETTERS[idx])
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ALPHABET_LETTERS.forEach { letter ->
            val isActive = letter == activeLetter
            val isAvailable = letter in availableLetters
            Text(
                text = letter.toString(),
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = when {
                    isActive -> appActionColor
                    isAvailable -> fgTextColor
                    else -> fgTextColor.copy(alpha = 0.35f)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}

// single app item row in the vertical app drawer list <3
@Composable
fun AppDrawerGridItem(
    context: Context,
    app: LauncherApp,
    isSelected: Boolean = false,
    onAppClick: ((LauncherApp) -> Unit)? = null,
    onAppLongClick: (LauncherApp) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val cardRoundness = theme.cardRoundnessDp.dp
    val cardBgColor = theme.cardBgColor
    val appActionColor = theme.appActionColor
    val selectedContainerColor = theme.selectedContainerColor
    val shape = RoundedCornerShape(cardRoundness)

    val packageName = app.packageName

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "drawerItemScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .softGlowFocus(isSelected = isSelected, cornerRadius = cardRoundness)
            .then(
                if (!isSelected) {
                    Modifier.border(1.dp, theme.unfocusedCardBorderColor, shape)
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    onAppClick?.invoke(app)
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                },
                onLongClick = {
                    onAppClick?.invoke(app)
                    onAppLongClick(app)
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                theme.focusedContainerColor
            } else {
                theme.unfocusedCardBgColor
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.imageBitmap,
                contentDescription = app.name,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = app.name,
                fontSize = 17.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) theme.fgTextColor else theme.cardTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "♡",
                    color = appActionColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }
        }
    }
}

// app drawer header bar showing title and swipe down button :3
@Composable
fun AppDrawerHeader(
    isFocused: Boolean = false,
    onSwipeDown: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused, cornerRadius = 18.dp)
            .clip(shape)
            .background(
                if (isFocused) theme.focusedContainerColor else theme.greetingBgColor.copy(alpha = 0.85f)
            )
            .then(
                if (!isFocused) {
                    Modifier.border(1.5.dp, theme.greetingOutlineColor, shape)
                } else {
                    Modifier
                }
            )
            .clickable { onSwipeDown() }
            .padding(vertical = 7.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "— ♡ App Drawer ♡ —",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = theme.appActionColor
            )
            Text(
                text = "▾",
                fontSize = 16.sp,
                color = theme.appActionColor
            )
        }
    }
}
