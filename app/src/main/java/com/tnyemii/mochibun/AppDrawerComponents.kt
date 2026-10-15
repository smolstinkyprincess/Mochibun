package com.tnyemii.mochibun

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

//alphabet strip in appdrawer
fun firstLetterOf(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
fun AlphabetIndex(
    availableLetters: Set<Char>,
    onLetterTap: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current
    val fgTextColor = theme.fgTextColor

    val letters = remember { ('A'..'Z').toList() + '#' }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(letter) {
                        detectTapGestures { onLetterTap(letter) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    fontSize = 11.sp,
                    color = fgTextColor.copy(
                        alpha = if (letter in availableLetters) 1f else 0.35f
                    )
                )
            }
        }
    }
}


// single app item row in the vertical app drawer list <3
@Composable
fun AppDrawerGridItem(
    context: Context,
    app: LauncherApp,
    isSelected: Boolean = false,
    onAppLongClick: (LauncherApp) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val cardRoundness = theme.cardRoundnessDp.dp
    val cardTextColor = theme.cardTextColor
    val cardBgColor = theme.cardBgColor
    val appActionColor = theme.appActionColor
    val selectedContainerColor = theme.selectedContainerColor

    val packageName = app.resolveInfo.activityInfo.packageName
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 150)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                onClick = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                },
                onLongClick = { onAppLongClick(app) }
            ),
        shape = RoundedCornerShape(cardRoundness),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) selectedContainerColor else cardBgColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = app.imageBitmap,
                contentDescription = app.name,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.width(32.dp))

            Text(
                text = app.name,
                fontSize = 20.sp,
                color = cardTextColor,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
            )

            if (isSelected) {
                Text(
                    text = "♡",
                    color = appActionColor,
                    fontSize = 32.sp
                )
            }
        }
    }
}

// app drawer header bar showing title and swipe down button :3
@Composable
fun AppDrawerHeader(onSwipeDown: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val cardTextColor = theme.cardTextColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSwipeDown() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "♡ apps ♡",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = cardTextColor
            )
            Text(
                text = "▾",
                fontSize = 18.sp,
                color = cardTextColor
            )
        }
    }
}


