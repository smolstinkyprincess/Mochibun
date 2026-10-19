package com.crinklecodone.mochibun.ui.screens

import android.view.KeyEvent
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.ui.dialogs.PixelRibbonBow
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.*
import com.crinklecodone.mochibun.ui.components.*

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
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusIndex = if (focusIndex > 0) focusIndex - 1 else 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusIndex = if (focusIndex < 1) focusIndex + 1 else 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
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
        Box(
            modifier = Modifier
                .width(288.dp)
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(288.dp)
                    .heightIn(max = 480.dp)
                    .border(2.dp, ActiveTheme.cardBorder, shape),
                shape = shape,
                color = ActiveTheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MochibunSectionHeader(
                        title = "Settings",
                        color = ActiveTheme.accentDeep,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = ActiveTheme.accentDeep.copy(alpha = 0.22f))

                    // 1. Wallpaper PixelSpray row
                    PixelSpraySettingsRow(
                        isEnabled = isPixelSprayEnabled,
                        isFocused = focusIndex == 0,
                        onToggle = onTogglePixelSpray,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. Close button
                    KawaiiClosePillButton(
                        isFocused = focusIndex == 1,
                        onClick = onDismiss
                    )
                }
            }

            // Unified ribbon bow badge overlapping the upper border rim
            PixelRibbonBow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-23).dp)
            )
        }
    }
}
