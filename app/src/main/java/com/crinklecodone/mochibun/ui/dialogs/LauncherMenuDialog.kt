package com.crinklecodone.mochibun.ui.dialogs

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.ui.components.MochibunSectionHeader
import com.crinklecodone.mochibun.ui.components.PixelSpraySettingsRow
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherMenuDialog(
    onDismiss: () -> Unit,
    onHiddenAppsClick: () -> Unit,
    onThemeStudioClick: () -> Unit,
    onPickWallpaperClick: () -> Unit,
    onResetWallpaperClick: () -> Unit,
    showResetWallpaper: Boolean,
    onIconPackClick: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onHardwareButtonsClick: (() -> Unit)? = null,
    onVirtualMouseClick: (() -> Unit)? = null,
    onEditUserNameClick: (() -> Unit)? = null,
    isPixelSprayEnabled: Boolean = true,
    onTogglePixelSpray: ((Boolean) -> Unit)? = null
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var sawCenterDown by remember { mutableStateOf(false) }

    val actions = remember(showResetWallpaper, onIconPackClick, onCreateFolderClick, onHardwareButtonsClick, onVirtualMouseClick, onEditUserNameClick) {
        buildList<Pair<String, () -> Unit>> {
            if (onEditUserNameClick != null) {
                add("✨ Edit user name" to onEditUserNameClick)
            }
            if (onCreateFolderClick != null) {
                add("📁 Make folder" to onCreateFolderClick)
            }
            if (onIconPackClick != null) {
                add("♡ Icon packs" to onIconPackClick)
            }
            if (onHardwareButtonsClick != null) {
                add("🎮 Hardware buttons" to onHardwareButtonsClick)
            }
            if (onVirtualMouseClick != null) {
                add("🖱️ Virtual mouse" to onVirtualMouseClick)
            }
            add("🙈 Hidden apps" to onHiddenAppsClick)
            add("🎨 Theme Studio" to onThemeStudioClick)
            add("🖼 Pick wallpaper from gallery" to onPickWallpaperClick)
            if (showResetWallpaper) {
                add("↩ Reset to default wallpaper" to onResetWallpaperClick)
            }
            add("♥ Close ♥" to onDismiss)
        }
    }

    val hasToggle = onTogglePixelSpray != null
    val totalCount = actions.size + (if (hasToggle) 1 else 0)

    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(totalCount) {
        selectedIndex = selectedIndex.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
    }

    LaunchedEffect(selectedIndex) {
        scrollState.animateScrollTo((selectedIndex * 42))
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
                        if (hasToggle && selectedIndex == 0) {
                            onTogglePixelSpray(!isPixelSprayEnabled)
                        } else {
                            val actionIdx = if (hasToggle) selectedIndex - 1 else selectedIndex
                            actions.getOrNull(actionIdx)?.second?.invoke()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (hasToggle && selectedIndex == 0) {
                            onTogglePixelSpray(!isPixelSprayEnabled)
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        selectedIndex = if (selectedIndex > 0) {
                            selectedIndex - 1
                        } else {
                            (totalCount - 1).coerceAtLeast(0)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        selectedIndex = if (selectedIndex < totalCount - 1) {
                            selectedIndex + 1
                        } else {
                            0
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .width(dialogWidth)
                .graphicsLayer { clip = false },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .width(dialogWidth)
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
                        .padding(horizontal = 14.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MochibunSectionHeader(
                        title = "Settings",
                        color = ActiveTheme.accentDeep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 2.dp,
                                bottom = 6.dp
                            )
                    )

                    HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))

                    if (hasToggle) {
                        Spacer(modifier = Modifier.height(2.dp))
                        PixelSpraySettingsRow(
                            isEnabled = isPixelSprayEnabled,
                            isFocused = selectedIndex == 0,
                            onToggle = { onTogglePixelSpray(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    actions.forEachIndexed { idx, (label, action) ->
                        val itemIndex = if (hasToggle) idx + 1 else idx
                        val isClose = idx == actions.lastIndex
                        if (isClose) {
                            Spacer(modifier = Modifier.height(2.dp))
                            HorizontalDivider(color = theme.appActionColor.copy(alpha = 0.22f))
                        }
                        KawaiiDialogItem(
                            text = label,
                            isFocused = selectedIndex == itemIndex,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = if (isClose) TextAlign.End else TextAlign.Start,
                            onClick = {
                                selectedIndex = itemIndex
                                action()
                            }
                        )
                    }
                }
            }

            // Ribbon Bow centered and overlapping top border rim
            PixelRibbonBow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-23).dp)
            )
        }
    }
}