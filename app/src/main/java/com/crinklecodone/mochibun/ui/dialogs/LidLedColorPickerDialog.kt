package com.crinklecodone.mochibun.ui.dialogs

import com.crinklecodone.mochibun.ui.components.KawaiiClosePill
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.core.system.LidLedManager
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme

/**
 * Aesthetic D-Pad-optimized Color Picker Dialog for exterior clamshell LEDs.
 *
 * Features:
 * - 15 curated Kawaii & Keitai presets arranged in a 5x3 grid.
 * - Full D-Pad navigation (UP/DOWN/LEFT/RIGHT/CENTER) matching physical keypad controls.
 * - Live hardware preview: illuminates exterior lid LEDs as swatches are highlighted.
 * - Custom 6-digit hex code entry (#RRGGBB).
 */
@Composable
fun LidLedColorPickerDialog(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val presets = LidLedManager.PRESET_COLORS
    val cols = 3
    val totalSwatches = presets.size

    // Focus state: 0 until totalSwatches = swatch index; totalSwatches = Custom Hex button; totalSwatches + 1 = Close button
    val initialIndex = remember(currentColor) {
        val found = presets.indexOfFirst { it.id.equals(currentColor, ignoreCase = true) }
        if (found >= 0) found else 0
    }
    var focusedIndex by remember { mutableIntStateOf(initialIndex) }
    var isCustomHexMode by remember { mutableStateOf(false) }
    var customHexInput by remember {
        mutableStateOf(currentColor.removePrefix("0x").removePrefix("#"))
    }

    val dialogFocusRequester = remember { FocusRequester() }

    // Live preview on hardware whenever focused swatch changes
    LaunchedEffect(focusedIndex, isCustomHexMode) {
        if (!isCustomHexMode) {
            presets.getOrNull(focusedIndex)?.let { swatch ->
                LidLedManager.setSolidColor(swatch.hex)
            }
        }
    }

    // Restore dialog focus after launch
    requestFocusSafely(dialogFocusRequester)

    Dialog(
        onDismissRequest = {
            LidLedManager.stopLeds()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(16.dp)
                .focusRequester(dialogFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent

                    if (isBackDismissKey(native.keyCode)) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        if (isCustomHexMode) {
                            isCustomHexMode = false
                        } else {
                            LidLedManager.stopLeds()
                            onDismiss()
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (isCustomHexMode) {
                        return@onPreviewKeyEvent false
                    }

                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            focusedIndex = when {
                                focusedIndex == totalSwatches || focusedIndex == totalSwatches + 1 -> {
                                    // Move from bottom buttons back to bottom row of swatches
                                    totalSwatches - 1
                                }
                                focusedIndex - cols >= 0 -> focusedIndex - cols
                                else -> focusedIndex
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusedIndex = when {
                                focusedIndex + cols < totalSwatches -> focusedIndex + cols
                                focusedIndex < totalSwatches -> totalSwatches // Drop to Custom Hex button
                                else -> focusedIndex
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            focusedIndex = when {
                                focusedIndex == totalSwatches + 1 -> totalSwatches
                                focusedIndex > 0 && focusedIndex < totalSwatches -> focusedIndex - 1
                                else -> focusedIndex
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            focusedIndex = when {
                                focusedIndex == totalSwatches -> totalSwatches + 1
                                focusedIndex < totalSwatches - 1 -> focusedIndex + 1
                                else -> focusedIndex
                            }
                            SoundEffectManager.play(context, SoundEffect.NAV)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                            when (focusedIndex) {
                                totalSwatches -> {
                                    isCustomHexMode = true
                                }
                                totalSwatches + 1 -> {
                                    LidLedManager.stopLeds()
                                    onDismiss()
                                }
                                else -> {
                                    presets.getOrNull(focusedIndex)?.let { swatch ->
                                        onColorSelected(swatch.id)
                                        LidLedManager.stopLeds()
                                        onDismiss()
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                },
            colors = CardDefaults.cardColors(containerColor = theme.greetingBgColor),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, theme.greetingOutlineColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎨",
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Lid LED Palette",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.fgTextColor
                        )
                    }

                    // Live active swatch indicator
                    val activeLabel = if (isCustomHexMode) {
                        "#${customHexInput.uppercase()}"
                    } else {
                        presets.getOrNull(focusedIndex)?.label ?: "Sakura Pink"
                    }
                    Text(
                        text = activeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.fgTextColor.copy(alpha = 0.8f)
                    )
                }

                if (!isCustomHexMode) {
                    // Swatch Grid: 5 rows x 3 columns
                    presets.chunked(cols).forEachIndexed { rIdx, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowItems.forEachIndexed { cIdx, swatch ->
                                val idx = rIdx * cols + cIdx
                                val isFocused = focusedIndex == idx
                                val isSelected = swatch.id.equals(currentColor, ignoreCase = true) ||
                                        swatch.hex.equals(currentColor, ignoreCase = true)

                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(swatch.colorValue))
                                        .border(
                                            width = if (isFocused) 3.dp else 1.dp,
                                            color = if (isFocused) theme.fgTextColor else Color.Black.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            focusedIndex = idx
                                            onColorSelected(swatch.id)
                                            LidLedManager.stopLeds()
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = if (swatch.id == "white" || swatch.id == "candle") Color.Black else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KawaiiDialogPillButton(
                            text = "Custom Hex",
                            isFocused = focusedIndex == totalSwatches,
                            onClick = {
                                SoundEffectManager.play(context, SoundEffect.SELECT)
                                isCustomHexMode = true
                            }
                        )
                        KawaiiClosePill(
                            isFocused = focusedIndex == totalSwatches + 1,
                            onClick = {
                                SoundEffectManager.play(context, SoundEffect.BACK)
                                LidLedManager.stopLeds()
                                onDismiss()
                            }
                        )
                    }
                } else {
                    // Custom Hex Input Mode
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Enter 6-character hex code (e.g. FFAAB9):",
                            fontSize = 11.sp,
                            color = theme.fgTextColor.copy(alpha = 0.7f)
                        )

                        val parsedColor = customHexInput.takeIf { it.length == 6 }?.let {
                            it.toLongOrNull(16)?.let { num -> Color(0xFF000000 or num) }
                        } ?: Color.Transparent

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parsedColor)
                                    .border(1.dp, theme.fgTextColor, CircleShape)
                            )
                            OutlinedTextField(
                                value = customHexInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(6)
                                    customHexInput = filtered
                                    if (filtered.length == 6) {
                                        LidLedManager.setSolidColor("0x$filtered")
                                    }
                                },
                                prefix = { Text("#") },
                                singleLine = true,
                                modifier = Modifier.width(130.dp),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboardController?.hide()
                                    if (customHexInput.length == 6) {
                                        onColorSelected("#$customHexInput")
                                        LidLedManager.stopLeds()
                                        onDismiss()
                                    }
                                })
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KawaiiDialogPillButton(
                                text = "Back",
                                isFocused = false,
                                onClick = {
                                    isCustomHexMode = false
                                }
                            )
                            KawaiiDialogPillButton(
                                text = "Apply",
                                isFocused = true,
                                isPrimary = true,
                                enabled = customHexInput.length == 6,
                                onClick = {
                                    if (customHexInput.length == 6) {
                                        onColorSelected("#$customHexInput")
                                        LidLedManager.stopLeds()
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
