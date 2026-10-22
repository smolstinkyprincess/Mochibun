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
import com.crinklecodone.mochibun.ui.components.SystemMonitorSettingsRow
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
    onSettingsClick: (() -> Unit)? = null,
    onIconPackClick: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onHardwareButtonsClick: (() -> Unit)? = null,
    onVirtualMouseClick: (() -> Unit)? = null,
    onEditUserNameClick: (() -> Unit)? = null,
    isPixelSprayEnabled: Boolean = true,
    onTogglePixelSpray: ((Boolean) -> Unit)? = null,
    isSystemMonitorEnabled: Boolean = true,
    onToggleSystemMonitor: ((Boolean) -> Unit)? = null
) {
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    var sawCenterDown by remember { mutableStateOf(false) }

    val actions = remember(showResetWallpaper, onSettingsClick, onIconPackClick, onCreateFolderClick, onHardwareButtonsClick, onVirtualMouseClick, onEditUserNameClick) {
        buildList<Pair<String, () -> Unit>> {
            if (onSettingsClick != null) {
                add("⚙ Settings Hub" to onSettingsClick)
            }
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

    val hasPixelSprayToggle = onTogglePixelSpray != null
    val hasSystemMonitorToggle = onToggleSystemMonitor != null
    val pixelSprayIndex = if (hasPixelSprayToggle) 0 else -1
    val systemMonitorIndex = when {
        hasSystemMonitorToggle && hasPixelSprayToggle -> 1
        hasSystemMonitorToggle -> 0
        else -> -1
    }
    val togglesCount = (if (hasPixelSprayToggle) 1 else 0) + (if (hasSystemMonitorToggle) 1 else 0)
    val totalCount = actions.size + togglesCount

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
                        when (selectedIndex) {
                            pixelSprayIndex -> onTogglePixelSpray?.invoke(!isPixelSprayEnabled)
                            systemMonitorIndex -> onToggleSystemMonitor?.invoke(!isSystemMonitorEnabled)
                            else -> {
                                val actionIdx = selectedIndex - togglesCount
                                actions.getOrNull(actionIdx)?.second?.invoke()
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else totalCount - 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        selectedIndex = if (selectedIndex < totalCount - 1) selectedIndex + 1 else 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (selectedIndex == pixelSprayIndex) {
                            onTogglePixelSpray?.invoke(!isPixelSprayEnabled)
                            true
                        } else if (selectedIndex == systemMonitorIndex) {
                            onToggleSystemMonitor?.invoke(!isSystemMonitorEnabled)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    ) {
        Box(
            modifier = Modifier.width(dialogWidth),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .border(
                        width = 1.5.dp,
                        color = ActiveTheme.cardBorder,
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                color = ActiveTheme.container,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    MochibunSectionHeader(
                        title = "Launcher Options",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )

                    HorizontalDivider(
                        color = ActiveTheme.cardBorder.copy(alpha = 0.4f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (hasPixelSprayToggle && onTogglePixelSpray != null) {
                            PixelSpraySettingsRow(
                                isEnabled = isPixelSprayEnabled,
                                isFocused = selectedIndex == pixelSprayIndex,
                                onToggle = onTogglePixelSpray
                            )
                        }

                        if (hasSystemMonitorToggle && onToggleSystemMonitor != null) {
                            SystemMonitorSettingsRow(
                                isEnabled = isSystemMonitorEnabled,
                                isFocused = selectedIndex == systemMonitorIndex,
                                onToggle = onToggleSystemMonitor
                            )
                        }

                        actions.forEachIndexed { index, (label, onClick) ->
                            val isFocused = selectedIndex == (index + togglesCount)
                            val isDestructive = label.contains("Reset", ignoreCase = true)
                            val isClose = label.contains("Close", ignoreCase = true)

                            KawaiiDialogItem(
                                text = label,
                                isFocused = isFocused,
                                isDestructive = isDestructive,
                                textAlign = if (isClose) TextAlign.Center else TextAlign.Start,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onClick
                            )
                        }
                    }
                }
            }

            PixelRibbonBow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-6).dp)
                    .graphicsLayer {
                        shadowElevation = 0f
                    }
            )
        }
    }
}
