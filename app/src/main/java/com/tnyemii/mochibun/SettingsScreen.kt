package com.tnyemii.mochibun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/**
 * Dedicated settings row component for toggling PixelSpray wallpaper stickers.
 * Follows Mochibun's pastel design system and supports physical D-pad focus on the Chinoe AT-M120.
 */
@Composable
fun PixelSpraySettingsRow(
    isEnabled: Boolean,
    isFocused: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 2.dp)
            .glowingDoubleBorder(isSelected = isFocused, shape = cardShape, cornerRadius = 18.dp)
            .background(
                color = if (isFocused) Color(0xFFFFF0F4) else Color(0xFFFAF5FC),
                shape = cardShape
            )
            .border(
                width = if (isFocused) 2.dp else 1.2.dp,
                color = if (isFocused) Color(0xFFE5607E) else Color(0xFFDFB3C4),
                shape = cardShape
            )
            .clip(cardShape)
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = "♡ Wallpaper PixelSpray ♡",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2452)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scatter pastel pixel stickers above home wallpaper",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF8D7B9D),
                    lineHeight = 15.sp
                )
            }

            // Kawaii Pill Switch (ON / OFF)
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isEnabled) Color(0xFFF8BBD0) else Color(0xFFD1C4E9))
                    .border(
                        1.2.dp,
                        if (isEnabled) Color(0xFFE06287) else Color(0xFF9E8DB8),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isEnabled) Arrangement.End else Arrangement.Start
                ) {
                    if (isEnabled) {
                        Text(
                            text = "ON",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF703350),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    // Knob circle
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE06287).copy(alpha = 0.5f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEnabled) "♡" else "・",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color(0xFFE06287) else Color(0xFF8D7B9D)
                        )
                    }

                    if (!isEnabled) {
                        Text(
                            text = "OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5E35B1),
                            modifier = Modifier.padding(start = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated launcher settings dialog composable scroll-contained for 480 × 800 displays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSettingsDialog(
    onDismiss: () -> Unit,
    isPixelSprayEnabled: Boolean,
    onTogglePixelSpray: (Boolean) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    var focusIndex by remember { mutableIntStateOf(0) } // 0 = PixelSpray toggle, 1 = Close
    var sawCenterDown by remember { mutableStateOf(false) }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }

                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        if (focusIndex == 0) {
                            onTogglePixelSpray(!isPixelSprayEnabled)
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        focusIndex = if (focusIndex > 0) focusIndex - 1 else 1
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusIndex = if (focusIndex < 1) focusIndex + 1 else 0
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focusIndex == 0) {
                            onTogglePixelSpray(!isPixelSprayEnabled)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .width(280.dp)
                .heightIn(max = 480.dp)
                .border(2.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "— ♡ Settings ♡ —",
                    fontWeight = FontWeight.Bold,
                    color = theme.fgTextColor,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                // 1. Wallpaper PixelSpray row
                PixelSpraySettingsRow(
                    isEnabled = isPixelSprayEnabled,
                    isFocused = focusIndex == 0,
                    onToggle = onTogglePixelSpray,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Close button
                KawaiiDialogPillButton(
                    text = "Close",
                    isFocused = focusIndex == 1,
                    onClick = onDismiss
                )
            }
        }
    }
}
