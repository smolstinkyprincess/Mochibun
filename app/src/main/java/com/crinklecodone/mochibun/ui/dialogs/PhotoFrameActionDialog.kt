package com.crinklecodone.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.ui.components.KawaiiClosePill
import com.crinklecodone.mochibun.ui.components.KeitaiLaceContainer
import com.crinklecodone.mochibun.ui.components.curtainUnfurl
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.components.MochibunSectionHeader
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.softSurfaceColor

/** Longest label the frame accepts; the card ellipsizes anything that doesn't fit anyway. */
private const val MAX_LABEL_LENGTH = 32

private class PhotoFrameAction(
    val label: String,
    val isDestructive: Boolean = false,
    val run: () -> Unit
)

// OK on the widget-page photo frame: pick photo, cycle style, edit label, remove photo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFrameActionDialog(
    hasPhoto: Boolean,
    styleDisplayName: String,
    label: String,
    onPickPhoto: () -> Unit,
    onCycleStyle: () -> Unit,
    onEditLabel: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    var sawCenterDown by remember { mutableStateOf(false) }

    val actions = remember(hasPhoto, styleDisplayName, label) {
        buildList {
            add(PhotoFrameAction(if (hasPhoto) "📷 Change Photo" else "📷 Pick Photo", run = onPickPhoto))
            add(PhotoFrameAction("🎀 Style: $styleDisplayName", run = onCycleStyle))
            add(PhotoFrameAction("✏️ Label: $label", run = onEditLabel))
            if (hasPhoto) add(PhotoFrameAction("🗑️ Remove Photo", isDestructive = true, run = onRemovePhoto))
            add(PhotoFrameAction("♥ Close ♥", run = onDismiss))
        }
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(actions.size) {
        selectedIndex = selectedIndex.coerceIn(0, actions.lastIndex)
    }

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
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        actions.getOrNull(selectedIndex)?.run?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..actions.size) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        selectedIndex = digit - 1
                        actions[digit - 1].run()
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else actions.lastIndex
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = if (selectedIndex < actions.lastIndex) selectedIndex + 1 else 0
                        true
                    }
                    else -> false
                }
            }
    ) {
        KeitaiLaceContainer(
            cardCornerRadius = 22.dp,
            modifier = Modifier
                .width(dialogWidth)
                .curtainUnfurl(visible = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "📸 Photo Frame",
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                Spacer(modifier = Modifier.height(2.dp))

                actions.forEachIndexed { idx, action ->
                    if (idx == actions.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            KawaiiClosePill(
                                isFocused = selectedIndex == idx,
                                onClick = { selectedIndex = idx; action.run() }
                            )
                        }
                    } else {
                        KawaiiDialogItem(
                            text = action.label,
                            isFocused = selectedIndex == idx,
                            isDestructive = action.isDestructive,
                            modifier = Modifier.fillMaxWidth(),
                            jumpNumber = idx + 1,
                            onClick = {
                                selectedIndex = idx
                                action.run()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Free-text label editor for the photo frame. Focus zones: 0 = text field (typing goes to the
 * IME; DOWN or Enter leaves it), 1 = Cancel / Save buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFrameLabelDialog(
    initialLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var labelInput by remember {
        mutableStateOf(TextFieldValue(initialLabel, selection = TextRange(initialLabel.length)))
    }
    var focusZone by remember { mutableIntStateOf(0) }
    var buttonCol by remember { mutableIntStateOf(1) } // 0 = Cancel, 1 = Save
    var sawCenterDown by remember { mutableStateOf(false) }

    val dialogFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }

    val save: () -> Unit = {
        keyboardController?.hide()
        onSave(labelInput.text.trim())
    }

    LaunchedEffect(focusZone) {
        if (focusZone == 0) {
            fieldFocusRequester.awaitFocusSafely(attempts = 6)
        } else {
            keyboardController?.hide()
            dialogFocusRequester.awaitFocusSafely(attempts = 6)
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
            .focusRequester(dialogFocusRequester)
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

                // While the field has focus, let typing through; only DOWN/UP/Enter move out of it
                if (focusZone == 0) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                                focusZone = 1
                                buttonCol = 1
                                return@onPreviewKeyEvent true
                            }
                            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                save()
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
                        if (buttonCol == 0) onDismiss() else save()
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusZone = 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        buttonCol = 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        buttonCol = 1
                        true
                    }
                    else -> false
                }
            }
    ) {
        KeitaiLaceContainer(
            cardCornerRadius = 22.dp,
            modifier = Modifier
                .width(dialogWidth)
                .curtainUnfurl(visible = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                MochibunSectionHeader(
                    title = "Frame Label",
                    color = ActiveTheme.accentDeep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = labelInput,
                    onValueChange = {
                        labelInput = if (it.text.length <= MAX_LABEL_LENGTH) it
                            else it.copy(text = it.text.take(MAX_LABEL_LENGTH))
                    },
                    placeholder = { Text("e.g. Mochi ♡") },
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocusRequester)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${labelInput.text.length}/$MAX_LABEL_LENGTH · ▼ for buttons",
                    color = theme.fgTextColor.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KawaiiDialogPillButton(
                        text = "Cancel",
                        isFocused = focusZone == 1 && buttonCol == 0,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    KawaiiDialogPillButton(
                        text = "Save ♡",
                        isFocused = focusZone == 1 && buttonCol == 1,
                        isPrimary = true,
                        modifier = Modifier.weight(1f),
                        onClick = save
                    )
                }
            }
        }
    }
}
