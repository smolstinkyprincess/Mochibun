package com.crinklecodone.mochibun.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.ui.MainActivity
import com.crinklecodone.mochibun.ui.components.curtainUnfurl
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.screens.settings.*

/**
 * Navigation destination tiers for the Mochibun Settings Hub.
 */
sealed class SettingsDestination {
    object Hub : SettingsDestination()
    object Appearance : SettingsDestination()
    object CursorHardware : SettingsDestination()
    object SoundFeedback : SettingsDestination()
    object Behavior : SettingsDestination()
    object PermissionsRoot : SettingsDestination()
    object BackupRestore : SettingsDestination()
    object About : SettingsDestination()
    object Keyboard : SettingsDestination()
}

/**
 * Unified Mochibun Settings Screen.
 * Implements a clean, two-tier settings architecture optimized for D-pad spatial navigation
 * and 480 × 800 resolution on the ALT Mive Style Folder AT-M120.
 */
@Composable
fun SettingsHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenThemeStudio: (() -> Unit)? = null,
    onOpenIconPackDialog: (() -> Unit)? = null,
    onOpenHardwareButtons: (() -> Unit)? = null,
    onOpenHiddenApps: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onPickWallpaperClick: (() -> Unit)? = null,
    onResetWallpaperClick: (() -> Unit)? = null,
    hasCustomWallpaper: Boolean = false,
    onSettingsRestored: (() -> Unit)? = null,
    initialDestination: SettingsDestination = SettingsDestination.Hub
) {
    var currentDestination by remember { mutableStateOf(initialDestination) }
    var rootFocusedIndex by remember { mutableIntStateOf(0) }
    val rootListState = rememberLazyListState()
    val context = LocalContext.current

    fun handleBack() {
        SoundEffectManager.play(context, SoundEffect.BACK)
        if (currentDestination == SettingsDestination.Hub) {
            onClose()
        } else {
            currentDestination = SettingsDestination.Hub
        }
    }

    DisposableEffect(Unit) {
        MainActivity.settingsBackKeyHandler = { event ->
            if (event.keyCode != KeyEvent.KEYCODE_BACK &&
                event.keyCode != KeyEvent.KEYCODE_ESCAPE
            ) {
                false
            } else {
                if (event.action == KeyEvent.ACTION_UP) handleBack()
                true
            }
        }
        onDispose {
            MainActivity.settingsBackKeyHandler = null
        }
    }

    val settingsModifier = modifier.onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
            return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyUp) {
            handleBack()
        }
        true
    }

    BackHandler {
        handleBack()
    }

    when (currentDestination) {
        SettingsDestination.Hub -> {
            SettingsHubRootScreen(
                onNavigate = { currentDestination = it },
                onClose = onClose,
                focusedIndex = rootFocusedIndex,
                onFocusedIndexChange = { rootFocusedIndex = it },
                listState = rootListState,
                modifier = settingsModifier
            )
        }
        SettingsDestination.Appearance -> {
            AppearanceSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                onOpenThemeStudio = onOpenThemeStudio,
                onOpenIconPackDialog = onOpenIconPackDialog,
                onPickWallpaperClick = onPickWallpaperClick,
                onResetWallpaperClick = onResetWallpaperClick,
                hasCustomWallpaper = hasCustomWallpaper,
                modifier = settingsModifier
            )
        }
        SettingsDestination.CursorHardware -> {
            CursorHardwareSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                onOpenHardwareButtons = onOpenHardwareButtons,
                modifier = settingsModifier
            )
        }
        SettingsDestination.SoundFeedback -> {
            SoundFeedbackSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                modifier = settingsModifier
            )
        }
        SettingsDestination.Behavior -> {
            BehaviorSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                onOpenHiddenApps = onOpenHiddenApps,
                onCreateFolderClick = onCreateFolderClick,
                modifier = settingsModifier
            )
        }
        SettingsDestination.PermissionsRoot -> {
            PermissionsRootSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                modifier = settingsModifier
            )
        }
        SettingsDestination.BackupRestore -> {
            BackupRestoreSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                onSettingsRestored = onSettingsRestored,
                modifier = settingsModifier
            )
        }
        SettingsDestination.About -> {
            AboutMochibunSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                modifier = settingsModifier
            )
        }
        SettingsDestination.Keyboard -> {
            KeyboardSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                modifier = settingsModifier
            )
        }
    }
}

/**
 * Root Hub Screen showing the primary category cards.
 */
@Composable
private fun SettingsHubRootScreen(
    onNavigate: (SettingsDestination) -> Unit,
    onClose: () -> Unit,
    focusedIndex: Int,
    onFocusedIndexChange: (Int) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var sawCenterDown by remember { mutableStateOf(false) }

    val totalItems = 8 // Eight primary navigation cards

    fun openDestination(index: Int) {
        when (index) {
            0 -> onNavigate(SettingsDestination.Appearance)
            1 -> onNavigate(SettingsDestination.Keyboard)
            2 -> onNavigate(SettingsDestination.CursorHardware)
            3 -> onNavigate(SettingsDestination.SoundFeedback)
            4 -> onNavigate(SettingsDestination.Behavior)
            5 -> onNavigate(SettingsDestination.PermissionsRoot)
            6 -> onNavigate(SettingsDestination.BackupRestore)
            7 -> onNavigate(SettingsDestination.About)
        }
    }

    LaunchedEffect(focusedIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || focusedIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(focusedIndex)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Settings Hub",
        showBack = true,
        onBack = onClose,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onClose()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        openDestination(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        onFocusedIndexChange(targetIndex)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        openDestination(targetIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        onFocusedIndexChange(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        } else {
                            if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        })
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsCard(categoryTitle = "Look & Aesthetic") {
                    SettingsNavigationRow(
                        title = "Appearance",
                        subtitle = "Themes, greeting name, icon packs, wallpapers & FX",
                        icon = "🎨",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = { onNavigate(SettingsDestination.Appearance) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Typing") {
                    SettingsNavigationRow(
                        title = "Keyboard",
                        subtitle = "Keitai multi-tap keyboard, hold-for-digit & key guide",
                        icon = "⌨",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = { onNavigate(SettingsDestination.Keyboard) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Pointer & Controls") {
                    SettingsNavigationRow(
                        title = "Virtual Cursor",
                        subtitle = "Virtual mouse speed, acceleration, scroll step & sprite",
                        icon = "🖱️",
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onClick = { onNavigate(SettingsDestination.CursorHardware) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Sound & Feedback") {
                    SettingsNavigationRow(
                        title = "Sound & Feedback",
                        subtitle = "Audio routing, keypad volume & sound profiles",
                        icon = "♫",
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onClick = { onNavigate(SettingsDestination.SoundFeedback) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Layout & Features") {
                    SettingsNavigationRow(
                        title = "Behavior",
                        subtitle = "Folders, app drawer & clamshell options",
                        icon = "📁",
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onClick = { onNavigate(SettingsDestination.Behavior) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Permissions & Superuser") {
                    SettingsNavigationRow(
                        title = "Permissions & Root",
                        subtitle = "Overlay permission, Magisk root status & LSPosed features",
                        icon = "🛡️",
                        isFocused = focusedIndex == 5,
                        jumpNumber = 6,
                        onClick = { onNavigate(SettingsDestination.PermissionsRoot) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Data & Storage") {
                    SettingsNavigationRow(
                        title = "Backup & Restore",
                        subtitle = "Export or restore layout, themes, folders & key remaps",
                        icon = "💾",
                        isFocused = focusedIndex == 6,
                        jumpNumber = 7,
                        onClick = { onNavigate(SettingsDestination.BackupRestore) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "System & Specs") {
                    SettingsNavigationRow(
                        title = "About Mochibun",
                        subtitle = "Version, hardware specs & credits",
                        icon = "♡",
                        isFocused = focusedIndex == 7,
                        jumpNumber = 8,
                        onClick = { onNavigate(SettingsDestination.About) }
                    )
                }
            }
        }
    }
}

/**
 * Mochibun dialog wrapper for [SettingsHubScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubDialog(
    onDismiss: () -> Unit,
    onOpenThemeStudio: (() -> Unit)? = null,
    onOpenIconPackDialog: (() -> Unit)? = null,
    onOpenHardwareButtons: (() -> Unit)? = null,
    onOpenHiddenApps: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onPickWallpaperClick: (() -> Unit)? = null,
    onResetWallpaperClick: (() -> Unit)? = null,
    hasCustomWallpaper: Boolean = false,
    onSettingsRestored: (() -> Unit)? = null,
    initialDestination: SettingsDestination = SettingsDestination.Hub
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        SettingsHubScreen(
            modifier = Modifier
                .fillMaxSize()
                .curtainUnfurl(visible = true),
            onClose = onDismiss,
            onOpenThemeStudio = onOpenThemeStudio,
            onOpenIconPackDialog = onOpenIconPackDialog,
            onOpenHardwareButtons = onOpenHardwareButtons,
            onOpenHiddenApps = onOpenHiddenApps,
            onCreateFolderClick = onCreateFolderClick,
            onPickWallpaperClick = onPickWallpaperClick,
            onResetWallpaperClick = onResetWallpaperClick,
            hasCustomWallpaper = hasCustomWallpaper,
            onSettingsRestored = onSettingsRestored,
            initialDestination = initialDestination
        )
    }
}
