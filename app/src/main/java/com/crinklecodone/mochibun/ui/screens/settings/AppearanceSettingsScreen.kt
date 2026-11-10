package com.crinklecodone.mochibun.ui.screens.settings

import com.crinklecodone.mochibun.ui.dialogs.ResizeWithKeyboard
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.TypographyManager
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.ActiveTheme

/**
 * Tier 1: Look & Aesthetic Sub-Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsSubScreen(
    onBack: () -> Unit,
    onOpenThemeStudio: (() -> Unit)?,
    onOpenIconPackDialog: (() -> Unit)?,
    onPickWallpaperClick: (() -> Unit)?,
    onResetWallpaperClick: (() -> Unit)?,
    hasCustomWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var customName by remember { mutableStateOf(PreferencesManager.getCustomUserName(context)) }
    var isEditingName by remember { mutableStateOf(false) }
    var tempNameInput by remember { mutableStateOf(customName) }

    val isWallpaperOverlayEnabled by PreferencesManager.isWallpaperOverlayEnabled.collectAsState()
    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()
    val systemMonitorInterval by PreferencesManager.systemMonitorInterval.collectAsState()
    val isPhotoFrameEnabled by PreferencesManager.isPhotoFrameEnabled.collectAsState()
    val fontId by PreferencesManager.themeFontFamily.collectAsState()
    val fontOptions by TypographyManager.options.collectAsState()

    // Dynamic item list based on custom wallpaper state
    val items = remember(hasCustomWallpaper, isSystemMonitorEnabled) {
        buildList {
            add("theme")
            add("typography")
            add("name")
            add("pick_wallpaper")
            if (hasCustomWallpaper) {
                add("reset_wallpaper")
            }
            add("icon_pack")
            add("wallpaper_stickers")
            add("system_monitor")
            if (isSystemMonitorEnabled) add("monitor_interval")
            add("photo_frame")
        }
    }

    val totalItems = items.size

    val targetCardIndex = remember(focusedIndex, items) {
        when (items.getOrNull(focusedIndex)) {
            "theme", "typography" -> 0
            "name" -> 1
            "icon_pack", "pick_wallpaper", "reset_wallpaper", "wallpaper_stickers" -> 2
            "system_monitor", "monitor_interval", "photo_frame" -> 3
            else -> 0
        }
    }

    LaunchedEffect(Unit) {
        TypographyManager.scanFonts(context)
    }

    LaunchedEffect(targetCardIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || targetCardIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(targetCardIndex)
    }

    fun activateAppearanceItem(index: Int) {
        when (items.getOrNull(index)) {
            "theme" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                onOpenThemeStudio?.invoke()
            }
            "typography" -> {
                val curIdx = fontOptions.indexOfFirst { it.id == fontId }.coerceAtLeast(0)
                val nextIdx = if (curIdx < fontOptions.size - 1) curIdx + 1 else 0
                PreferencesManager.setThemeFontFamily(context, fontOptions[nextIdx].id)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "name" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                tempNameInput = customName
                isEditingName = true
            }
            "icon_pack" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                onOpenIconPackDialog?.invoke()
            }
            "pick_wallpaper" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                onPickWallpaperClick?.invoke()
            }
            "reset_wallpaper" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                onResetWallpaperClick?.invoke()
            }
            "wallpaper_stickers" -> {
                PreferencesManager.setWallpaperOverlayEnabled(context, !isWallpaperOverlayEnabled)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "system_monitor" -> PreferencesManager.setSystemMonitorEnabled(context, !isSystemMonitorEnabled)
            "monitor_interval" -> {
                val next = when (systemMonitorInterval) {
                    1 -> 2
                    2 -> 3
                    3 -> 5
                    else -> 1
                }
                PreferencesManager.setSystemMonitorInterval(context, next)
            }
            "photo_frame" -> PreferencesManager.setPhotoFrameEnabled(context, !isPhotoFrameEnabled)
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Appearance",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        if (isEditingName) {
                            isEditingName = false
                        } else {
                            onBack()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        activateAppearanceItem(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (!isEditingName) {
                    val digit = getDigitFromKeyCode(native.keyCode)
                    if (digit != null && digit in 1..items.size.coerceAtMost(9)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            val targetIndex = digit - 1
                            focusedIndex = targetIndex
                            activateAppearanceItem(targetIndex)
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        focusedIndex = if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        } else {
                            if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        }
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (items.getOrNull(focusedIndex) == "typography") {
                            val curIdx = fontOptions.indexOfFirst { it.id == fontId }.coerceAtLeast(0)
                            val nextIdx = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                if (curIdx > 0) curIdx - 1 else fontOptions.size - 1
                            } else {
                                if (curIdx < fontOptions.size - 1) curIdx + 1 else 0
                            }
                            PreferencesManager.setThemeFontFamily(context, fontOptions[nextIdx].id)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else {
                            val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                            focusedIndex = (focusedIndex + direction + totalItems) % totalItems
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
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
                SettingsCard(categoryTitle = "Theme & Palette") {
                    SettingsNavigationRow(
                        title = "Theme Studio",
                        subtitle = "Active: ${ActiveTheme.palette.name}",
                        icon = "🎨",
                        isFocused = items.getOrNull(focusedIndex) == "theme",
                        jumpNumber = (items.indexOf("theme") + 1).takeIf { it <= 9 },
                        onClick = { onOpenThemeStudio?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val currentFont = remember(fontId, fontOptions) {
                        fontOptions.firstOrNull { it.id == fontId } ?: TypographyManager.BuiltInTypographyOptions.first()
                    }
                    SettingsStepperRow(
                        title = "Font Style",
                        subtitle = currentFont.subtitle,
                        valueDisplay = currentFont.name,
                        isFocused = items.getOrNull(focusedIndex) == "typography",
                        jumpNumber = (items.indexOf("typography") + 1).takeIf { it <= 9 },
                        onDecrement = {
                            val curIdx = fontOptions.indexOfFirst { it.id == fontId }.coerceAtLeast(0)
                            val prevIdx = if (curIdx > 0) curIdx - 1 else fontOptions.size - 1
                            PreferencesManager.setThemeFontFamily(context, fontOptions[prevIdx].id)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            val curIdx = fontOptions.indexOfFirst { it.id == fontId }.coerceAtLeast(0)
                            val nextIdx = if (curIdx < fontOptions.size - 1) curIdx + 1 else 0
                            PreferencesManager.setThemeFontFamily(context, fontOptions[nextIdx].id)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Greeting & Standby") {
                    SettingsTextInputRow(
                        title = "Display Name (Greeting)",
                        subtitle = if (customName.isNotBlank()) "♡ Welcome home, $customName ♡" else "Tap or Enter to set greeting name",
                        value = customName.ifBlank { "Not set" },
                        isFocused = items.getOrNull(focusedIndex) == "name",
                        jumpNumber = (items.indexOf("name") + 1).takeIf { it <= 9 },
                        onClick = {
                            tempNameInput = customName
                            isEditingName = true
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Wallpaper & Deco") {
                    SettingsNavigationRow(
                        title = "Pick from Gallery",
                        subtitle = "Select custom wallpaper image",
                        icon = "🖼",
                        isFocused = items.getOrNull(focusedIndex) == "pick_wallpaper",
                        jumpNumber = (items.indexOf("pick_wallpaper") + 1).takeIf { it <= 9 },
                        onClick = { onPickWallpaperClick?.invoke() }
                    )
                    if (hasCustomWallpaper) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Reset to Default Wallpaper",
                            isFocused = items.getOrNull(focusedIndex) == "reset_wallpaper",
                            isDestructive = true,
                            jumpNumber = (items.indexOf("reset_wallpaper") + 1).takeIf { it <= 9 },
                            onClick = { onResetWallpaperClick?.invoke() }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Installed Icon Packs",
                        subtitle = "Apply compatible icon packs",
                        icon = "🌸",
                        isFocused = items.getOrNull(focusedIndex) == "icon_pack",
                        jumpNumber = (items.indexOf("icon_pack") + 1).takeIf { it <= 9 },
                        onClick = { onOpenIconPackDialog?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Wallpaper Stickers (PixelSpray)",
                        subtitle = "Show rotating pastel pixel overlays above wallpaper",
                        checked = isWallpaperOverlayEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "wallpaper_stickers",
                        jumpNumber = (items.indexOf("wallpaper_stickers") + 1).takeIf { it <= 9 },
                        onCheckedChange = { PreferencesManager.setWallpaperOverlayEnabled(context, it) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Widget Deck") {
                    SettingsToggleRow(
                        title = "System Monitor Card",
                        subtitle = "Display live CPU, RAM, and storage gauges",
                        checked = isSystemMonitorEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "system_monitor",
                        jumpNumber = (items.indexOf("system_monitor") + 1).takeIf { it <= 9 },
                        onCheckedChange = { PreferencesManager.setSystemMonitorEnabled(context, it) }
                    )
                    if (isSystemMonitorEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Refresh Interval",
                            subtitle = "System monitor update frequency",
                            valueDisplay = "$systemMonitorInterval sec",
                            isFocused = items.getOrNull(focusedIndex) == "monitor_interval",
                            jumpNumber = (items.indexOf("monitor_interval") + 1).takeIf { it <= 9 },
                            onDecrement = {
                                val next = when (systemMonitorInterval) {
                                    5 -> 3
                                    3 -> 2
                                    2 -> 1
                                    else -> 5
                                }
                                PreferencesManager.setSystemMonitorInterval(context, next)
                            },
                            onIncrement = {
                                val next = when (systemMonitorInterval) {
                                    1 -> 2
                                    2 -> 3
                                    3 -> 5
                                    else -> 1
                                }
                                PreferencesManager.setSystemMonitorInterval(context, next)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Photo Frame Card",
                        subtitle = "Show your framed photo on the Widgets page",
                        checked = isPhotoFrameEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "photo_frame",
                        jumpNumber = (items.indexOf("photo_frame") + 1).takeIf { it <= 9 },
                        onCheckedChange = { PreferencesManager.setPhotoFrameEnabled(context, it) }
                    )
                }
            }
        }

        // Inline modal dialog for Name Editing
        if (isEditingName) {
            val focusManager = LocalFocusManager.current
            val nameInputRequester = remember { FocusRequester() }
            requestFocusSafely(nameInputRequester)

            BasicAlertDialog(
                onDismissRequest = { isEditingName = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ResizeWithKeyboard()
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ActiveTheme.container)
                        .border(1.5.dp, ActiveTheme.cardBorder, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "— ♡ Edit Greeting Name ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = tempNameInput,
                        onValueChange = { tempNameInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                            .border(1.5.dp, ActiveTheme.selectionGlow, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .focusRequester(nameInputRequester),
                        textStyle = TextStyle(
                            color = ActiveTheme.accentDeep,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(ActiveTheme.accentDeep),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            PreferencesManager.setCustomUserName(context, tempNameInput.trim())
                            customName = tempNameInput.trim()
                            isEditingName = false
                        })
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsActionRow(
                            title = "Save",
                            isFocused = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                PreferencesManager.setCustomUserName(context, tempNameInput.trim())
                                customName = tempNameInput.trim()
                                isEditingName = false
                            }
                        )
                        SettingsActionRow(
                            title = "Cancel",
                            isFocused = false,
                            modifier = Modifier.weight(1f),
                            onClick = { isEditingName = false }
                        )
                    }
                }
            }
        }
    }
}
