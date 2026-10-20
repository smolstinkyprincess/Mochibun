package com.crinklecodone.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.ui.components.MochibunSectionHeader
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.softSurfaceColor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUserNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }
    var nameInput by remember { mutableStateOf(currentName) }
    var sawCenterDown by remember { mutableStateOf(false) }

    // Focus zone: 0 = text field, 1 = Cancel, 2 = Clear, 3 = Save
    var focusZone by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusZone) {
        if (focusZone == 0) {
            repeat(6) {
                try {
                    textFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        } else {
            keyboardController?.hide()
            repeat(6) {
                try {
                    focusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        }
    }

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
                        keyboardController?.hide()
                        onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }
                if (focusZone == 0) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                focusZone = 3
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    return@onPreviewKeyEvent false
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusZone) {
                            1 -> {
                                keyboardController?.hide()
                                onDismiss()
                            }
                            2 -> {
                                nameInput = ""
                                focusZone = 0
                            }
                            3 -> {
                                keyboardController?.hide()
                                onSave(nameInput.trim())
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusZone = 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        focusZone = (focusZone - 1).coerceAtLeast(1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        focusZone = (focusZone + 1).coerceAtMost(3)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 380.dp)
                .border(2.dp, ActiveTheme.cardBorder, shape),
            shape = shape,
            color = ActiveTheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MochibunSectionHeader(
                    title = "User Name",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                )

                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                Text(
                    text = "Enter your cute name for the home screen greeting ♡",
                    fontSize = 12.sp,
                    color = theme.cardTextColor,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(20) },
                    placeholder = {
                        Text("e.g. emii ♡", color = theme.cardTextColor.copy(alpha = 0.6f))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.appActionColor,
                        unfocusedBorderColor = theme.greetingOutlineColor,
                        focusedTextColor = theme.fgTextColor,
                        unfocusedTextColor = theme.fgTextColor,
                        focusedContainerColor = softSurfaceColor,
                        unfocusedContainerColor = softSurfaceColor.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(textFocusRequester)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                ) {
                    KawaiiDialogPillButton(
                        text = "Cancel",
                        isFocused = focusZone == 1,
                        onClick = {
                            keyboardController?.hide()
                            onDismiss()
                        }
                    )

                    KawaiiDialogPillButton(
                        text = "Clear",
                        isFocused = focusZone == 2,
                        onClick = {
                            nameInput = ""
                            focusZone = 0
                        }
                    )

                    KawaiiDialogPillButton(
                        text = "Save ♡",
                        isFocused = focusZone == 3,
                        isPrimary = true,
                        onClick = {
                            keyboardController?.hide()
                            onSave(nameInput.trim())
                        }
                    )
                }
            }
        }
    }
}
