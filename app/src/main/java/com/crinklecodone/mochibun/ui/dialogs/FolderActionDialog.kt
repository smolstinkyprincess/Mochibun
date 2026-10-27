package com.crinklecodone.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.destructiveColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionDialog(
    folder: FavoriteFolder,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onOpenFolder: () -> Unit,
    onRenameFolder: () -> Unit,
    onEditFolderApps: () -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onDeleteFolder: () -> Unit,
    onOpenLauncherOptions: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var sawCenterDown by remember { mutableStateOf(false) }
    var confirmingDelete by remember(folder.id) { mutableStateOf(false) }
    var confirmDeleteIndex by remember { mutableIntStateOf(0) } // 0 = Cancel, 1 = Delete

    data class FolderMenuAction(
        val label: String,
        val enabled: Boolean = true,
        val isDestructive: Boolean = false,
        val onClick: () -> Unit
    )

    val actions = remember(folder.id, canMoveLeft, canMoveRight, onOpenLauncherOptions) {
        buildList {
            add(FolderMenuAction("◀ Move Left", enabled = canMoveLeft, onClick = onMoveLeft))
            add(FolderMenuAction("Move Right ▶", enabled = canMoveRight, onClick = onMoveRight))
            add(FolderMenuAction("📂 Open folder", onClick = onOpenFolder))
            add(FolderMenuAction("✎ Rename folder", onClick = onRenameFolder))
            add(FolderMenuAction("＋ Add / remove apps", onClick = onEditFolderApps))
            add(FolderMenuAction("🎨 Change folder icon", onClick = onChangeIcon))
            add(FolderMenuAction("↺ Reset folder icon", onClick = onResetIcon))
            add(
                FolderMenuAction(
                    "🗑 Delete folder",
                    isDestructive = true,
                    onClick = {
                        confirmDeleteIndex = 0
                        confirmingDelete = true
                    }
                )
            )
            if (onOpenLauncherOptions != null) {
                add(FolderMenuAction("⚙ Launcher options", onClick = onOpenLauncherOptions))
            }
            add(FolderMenuAction("♥ Close ♥", onClick = onDismiss))
        }
    }

    var selectedIndex by remember { mutableIntStateOf(2) }

    LaunchedEffect(selectedIndex) {
        val total = actions.size.coerceAtLeast(1)
        val targetScroll = ((selectedIndex.toFloat() / total) * scrollState.maxValue).toInt()
        scrollState.animateScrollTo(targetScroll)
    }

    requestFocusSafely(focusRequester, confirmingDelete)

    BackHandler {
        if (confirmingDelete) {
            confirmingDelete = false
        } else {
            onDismiss()
        }
    }

    BasicAlertDialog(
        onDismissRequest = {
            if (confirmingDelete) {
                confirmingDelete = false
            } else {
                onDismiss()
            }
        },
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
                        if (confirmingDelete) {
                            confirmingDelete = false
                        } else {
                            onDismiss()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        if (confirmingDelete) {
                            if (confirmDeleteIndex == 1) {
                                onDeleteFolder()
                            } else {
                                confirmingDelete = false
                            }
                        } else {
                            val item = actions.getOrNull(selectedIndex)
                            if (item != null && item.enabled) {
                                item.onClick()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (confirmingDelete) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            confirmDeleteIndex = 0
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            confirmDeleteIndex = 1
                            true
                        }
                        else -> false
                    }
                } else {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (selectedIndex == 1) {
                                selectedIndex = 0
                            } else if (selectedIndex > 0) {
                                selectedIndex--
                            } else {
                                selectedIndex = actions.lastIndex
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (selectedIndex == 0) {
                                selectedIndex = 1
                            } else if (selectedIndex < actions.lastIndex) {
                                selectedIndex++
                            } else {
                                selectedIndex = 0
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (selectedIndex == 0 || selectedIndex == 1) {
                                selectedIndex = actions.lastIndex
                            } else if (selectedIndex == 2) {
                                selectedIndex = if (canMoveLeft) 0 else if (canMoveRight) 1 else 0
                            } else {
                                selectedIndex--
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (selectedIndex == 0 || selectedIndex == 1) {
                                selectedIndex = 2
                            } else if (selectedIndex < actions.lastIndex) {
                                selectedIndex++
                            } else {
                                selectedIndex = 0
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        val shape = RoundedCornerShape(theme.cardRoundnessDp.coerceIn(22f, 24f).dp)
        Surface(
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 460.dp)
                .border(1.5.dp, theme.greetingOutlineColor, shape),
            shape = shape,
            color = theme.cardBgColor,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            if (confirmingDelete) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🗑 Delete '${folder.name}'?",
                        fontWeight = FontWeight.Bold,
                        color = destructiveColor,
                        fontSize = 17.sp
                    )

                    Text(
                        text = "This removes the folder from favorites. None of your installed apps will be uninstalled ♡",
                        color = theme.fgTextColor,
                        fontSize = 13.sp
                    )

                    HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        KawaiiDialogPillButton(
                            text = "♥ Cancel (◄) ♥",
                            isFocused = confirmDeleteIndex == 0,
                            onClick = { confirmingDelete = false }
                        )
                        KawaiiDialogPillButton(
                            text = "Delete folder",
                            isFocused = confirmDeleteIndex == 1,
                            isDestructive = true,
                            onClick = onDeleteFolder
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "📁 ${folder.name}",
                        color = theme.fgTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${folder.appPackages.size} app(s) inside ♡",
                        color = theme.cardTextColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KawaiiDialogPillButton(
                            text = "◀ Left",
                            isFocused = selectedIndex == 0,
                            enabled = canMoveLeft,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 0
                                if (canMoveLeft) onMoveLeft()
                            }
                        )
                        KawaiiDialogPillButton(
                            text = "Right ▶",
                            isFocused = selectedIndex == 1,
                            enabled = canMoveRight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedIndex = 1
                                if (canMoveRight) onMoveRight()
                            }
                        )
                    }

                    for (idx in 2 until actions.size) {
                        val item = actions[idx]
                        val isClose = idx == actions.lastIndex
                        if (isClose) {
                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                        }
                        KawaiiDialogItem(
                            text = item.label,
                            isFocused = selectedIndex == idx,
                            enabled = item.enabled,
                            isDestructive = item.isDestructive,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isClose) TextAlign.End else TextAlign.Start,
                            onClick = {
                                selectedIndex = idx
                                if (item.enabled) item.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
