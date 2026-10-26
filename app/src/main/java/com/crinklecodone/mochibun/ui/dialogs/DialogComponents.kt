package com.crinklecodone.mochibun.ui.dialogs

import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.R
import com.crinklecodone.mochibun.ui.components.softGlowFocus
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.destructiveBgColor
import com.crinklecodone.mochibun.ui.theme.destructiveColor
import com.crinklecodone.mochibun.ui.theme.selectedContainerColor
import com.crinklecodone.mochibun.ui.theme.softSurfaceColor
import kotlinx.coroutines.delay

val dialogWidth = 268.dp

fun isCenterActivateKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == KeyEvent.KEYCODE_BUTTON_A

fun isBackDismissKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BACK ||
        keyCode == KeyEvent.KEYCODE_ESCAPE

fun isDpadOrActionKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == KeyEvent.KEYCODE_BUTTON_A

/**
 * Retries requesting focus on a [FocusRequester] across composition frames
 * until the target node is attached.
 */
suspend fun FocusRequester.awaitFocusSafely(attempts: Int = 8, delayMs: Long = 35L) {
    repeat(attempts) {
        try {
            requestFocus()
            return
        } catch (_: Exception) {
            delay(delayMs)
        }
    }
}

@Composable
fun requestFocusSafely(focusRequester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        focusRequester.awaitFocusSafely()
    }
}

@Composable
fun KawaiiDialogItem(
    text: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    onClick: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        !enabled -> Color.Transparent
        isFocused && isDestructive -> destructiveBgColor
        isFocused -> theme.selectedContainerColor
        else -> Color.Transparent
    }
    val borderColor = when {
        !enabled -> Color.Transparent
        isFocused && isDestructive -> destructiveColor
        isFocused -> theme.appActionColor
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> theme.fgTextColor.copy(alpha = 0.38f)
        isDestructive -> destructiveColor
        isFocused -> theme.fgTextColor
        else -> theme.appActionColor
    }

    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused && enabled, cornerRadius = 12.dp)
            .clip(shape)
            .background(bg, shape)
            .border(
                width = if (isFocused && enabled) 1.5.dp else 0.dp,
                color = borderColor,
                shape = shape
            )
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = when (textAlign) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun KawaiiDialogPillButton(
    text: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val shape = CircleShape
    val bg = when {
        !enabled -> softSurfaceColor.copy(alpha = 0.45f)
        isDestructive && isFocused -> destructiveColor
        isDestructive -> destructiveBgColor
        isPrimary && isFocused -> theme.appActionColor
        isPrimary -> theme.appActionColor.copy(alpha = 0.85f)
        isFocused -> theme.selectedContainerColor
        else -> Color.Transparent
    }
    val borderColor = when {
        !enabled -> theme.greetingOutlineColor.copy(alpha = 0.3f)
        isDestructive -> destructiveColor
        isFocused -> theme.fgTextColor
        isPrimary -> theme.appActionColor
        else -> theme.greetingOutlineColor.copy(alpha = 0.6f)
    }
    val contentColor = when {
        !enabled -> theme.fgTextColor.copy(alpha = 0.38f)
        isDestructive && isFocused -> Color.White
        isDestructive -> destructiveColor
        isPrimary -> Color.White
        isFocused -> theme.fgTextColor
        else -> theme.appActionColor
    }

    Box(
        modifier = modifier
            .graphicsLayer { clip = false }
            .softGlowFocus(isSelected = isFocused && enabled, cornerRadius = 20.dp)
            .clip(shape)
            .background(bg, shape)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Crisp pixel-art ribbon bow badge for dialog headers and decorative accents.
 * Uses the pixel-art ribbon bow asset ([com.crinklecodone.mochibun.R.drawable.bow]).
 *
 * FilterQuality.None is strictly required here: on the AT-M120's 480x800 display, default bilinear
 * filtering causes muddy interpolation artifacts along 1-bit pixel art edges.
 */
private var cachedBowBitmap: ImageBitmap? = null

@Composable
fun rememberBowBitmap(): ImageBitmap {
    val context = LocalContext.current
    return remember {
        cachedBowBitmap ?: run {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, R.drawable.bow, boundsOpts)
            val sampleOpts = BitmapFactory.Options().apply {
                inSampleSize = (boundsOpts.outWidth / 128).coerceAtLeast(1)
            }
            BitmapFactory.decodeResource(context.resources, R.drawable.bow, sampleOpts)
                .asImageBitmap()
                .also { cachedBowBitmap = it }
        }
    }
}

@Composable
fun PixelRibbonBow(
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 46.dp
) {
    val bowBitmap = rememberBowBitmap()
    Image(
        bitmap = bowBitmap,
        contentDescription = "Ribbon Bow",
        filterQuality = FilterQuality.None,
        modifier = modifier.size(width = width, height = height)
    )
}

