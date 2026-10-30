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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.ui.components.KawaiiClosePill
import com.crinklecodone.mochibun.ui.components.KeitaiLaceContainer
import com.crinklecodone.mochibun.ui.components.curtainUnfurl
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme

private class PhotoFrameAction(
    val label: String,
    val isDestructive: Boolean = false,
    val run: () -> Unit
)

// OK on the widget-page photo frame: pick photo, cycle style/label, remove photo
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFrameActionDialog(
    hasPhoto: Boolean,
    styleDisplayName: String,
    label: String,
    onPickPhoto: () -> Unit,
    onCycleStyle: () -> Unit,
    onCycleLabel: () -> Unit,
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
            add(PhotoFrameAction("✏️ Label: $label", run = onCycleLabel))
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
