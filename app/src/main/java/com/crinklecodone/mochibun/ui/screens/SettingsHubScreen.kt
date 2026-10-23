package com.crinklecodone.mochibun.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.crinklecodone.mochibun.BuildConfig
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.data.prefs.ClickMethod
import com.crinklecodone.mochibun.data.prefs.CursorPreferences
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.service.VirtualCursorService
import com.crinklecodone.mochibun.ui.components.*
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.isDpadOrActionKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import android.widget.Toast
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
import com.crinklecodone.mochibun.data.repository.BackupRestoreManager
import com.crinklecodone.mochibun.data.repository.TypographyManager
import com.crinklecodone.mochibun.core.system.LidLedManager
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Navigation destination tiers for the Mochibun Settings Hub.
 */
sealed class SettingsDestination {
    object Hub : SettingsDestination()
    object Appearance : SettingsDestination()
    object CursorHardware : SettingsDestination()
    object Behavior : SettingsDestination()
    object BackupRestore : SettingsDestination()
    object About : SettingsDestination()
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
    onOpenSpeedDial: (() -> Unit)? = null,
    onPickWallpaperClick: (() -> Unit)? = null,
    onResetWallpaperClick: (() -> Unit)? = null,
    hasCustomWallpaper: Boolean = false,
    onSettingsRestored: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var currentDestination by remember { mutableStateOf<SettingsDestination>(SettingsDestination.Hub) }

    when (currentDestination) {
        SettingsDestination.Hub -> {
            SettingsHubRootScreen(
                onNavigate = { currentDestination = it },
                onClose = onClose,
                modifier = modifier
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
                modifier = modifier
            )
        }
        SettingsDestination.CursorHardware -> {
            CursorHardwareSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                onOpenHardwareButtons = onOpenHardwareButtons,
                modifier = modifier
            )
        }
        SettingsDestination.Behavior -> {
            BehaviorSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                onOpenHiddenApps = onOpenHiddenApps,
                onCreateFolderClick = onCreateFolderClick,
                onOpenSpeedDial = onOpenSpeedDial,
                modifier = modifier
            )
        }
        SettingsDestination.BackupRestore -> {
            BackupRestoreSettingsSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                onSettingsRestored = onSettingsRestored,
                modifier = modifier
            )
        }
        SettingsDestination.About -> {
            AboutMochibunSubScreen(
                onBack = { currentDestination = SettingsDestination.Hub },
                modifier = modifier
            )
        }
    }
}

/**
 * Root Hub Screen showing the 4 primary category cards.
 */
@Composable
private fun SettingsHubRootScreen(
    onNavigate: (SettingsDestination) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val totalItems = 5 // 5 primary navigation cards

    LaunchedEffect(focusedIndex) {
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
                if (isBackDismissKey(native.keyCode)) {
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
                        when (focusedIndex) {
                            0 -> onNavigate(SettingsDestination.Appearance)
                            1 -> onNavigate(SettingsDestination.CursorHardware)
                            2 -> onNavigate(SettingsDestination.Behavior)
                            3 -> onNavigate(SettingsDestination.BackupRestore)
                            4 -> onNavigate(SettingsDestination.About)
                        }
                    }
                    return@onPreviewKeyEvent true
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
                        onClick = { onNavigate(SettingsDestination.Appearance) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Pointer & Controls") {
                    SettingsNavigationRow(
                        title = "Cursor & Hardware",
                        subtitle = "Virtual mouse, speed, sprite & key remap",
                        icon = "🖱️",
                        isFocused = focusedIndex == 1,
                        onClick = { onNavigate(SettingsDestination.CursorHardware) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Layout & Features") {
                    SettingsNavigationRow(
                        title = "Behavior",
                        subtitle = "Folders, app drawer, search & system monitor",
                        icon = "📁",
                        isFocused = focusedIndex == 2,
                        onClick = { onNavigate(SettingsDestination.Behavior) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Data & Storage") {
                    SettingsNavigationRow(
                        title = "Backup & Restore",
                        subtitle = "Export or restore layout, themes, folders & key remaps",
                        icon = "💾",
                        isFocused = focusedIndex == 3,
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
                        isFocused = focusedIndex == 4,
                        onClick = { onNavigate(SettingsDestination.About) }
                    )
                }
            }
        }
    }
}

/**
 * Tier 1: Look & Aesthetic Sub-Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsSubScreen(
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

    val isPixelSprayEnabled by PreferencesManager.isPixelSprayEnabled.collectAsState()
    val fontId by PreferencesManager.themeFontFamily.collectAsState()
    val fontOptions by TypographyManager.options.collectAsState()

    // Dynamic item list based on custom wallpaper state
    val items = remember(hasCustomWallpaper) {
        buildList {
            add("theme")
            add("typography")
            add("name")
            add("icon_pack")
            add("pick_wallpaper")
            if (hasCustomWallpaper) {
                add("reset_wallpaper")
            }
            add("pixelspray")
        }
    }

    val totalItems = items.size

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedIndex)
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
                if (isBackDismissKey(native.keyCode)) {
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
                        when (items.getOrNull(focusedIndex)) {
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
                            "pixelspray" -> {
                                PreferencesManager.setPixelSprayWallpaperEnabled(context, !isPixelSprayEnabled)
                                SoundEffectManager.play(context, SoundEffect.SELECT)
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
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
                        onClick = { onOpenThemeStudio?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val currentFont = remember(fontId, fontOptions) {
                        fontOptions.firstOrNull { it.id == fontId } ?: TypographyManager.BuiltInTypographyOptions.first()
                    }
                    SettingsStepperRow(
                        title = "Typography",
                        subtitle = currentFont.subtitle,
                        valueDisplay = currentFont.name,
                        isFocused = items.getOrNull(focusedIndex) == "typography",
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
                SettingsCard(categoryTitle = "Greeting Personalization") {
                    SettingsTextInputRow(
                        title = "User Name",
                        subtitle = if (customName.isNotBlank()) "♡ Welcome home, $customName ♡" else "Tap or Enter to set greeting name",
                        value = customName.ifBlank { "Not set" },
                        isFocused = items.getOrNull(focusedIndex) == "name",
                        onClick = {
                            tempNameInput = customName
                            isEditingName = true
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Icon Pack") {
                    SettingsNavigationRow(
                        title = "Installed Icon Packs",
                        subtitle = "Apply ADW / Nova compatible kawaii packs",
                        icon = "🌸",
                        isFocused = items.getOrNull(focusedIndex) == "icon_pack",
                        onClick = { onOpenIconPackDialog?.invoke() }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Wallpaper") {
                    SettingsNavigationRow(
                        title = "Pick from Gallery",
                        subtitle = "Select custom wallpaper image",
                        icon = "🖼",
                        isFocused = items.getOrNull(focusedIndex) == "pick_wallpaper",
                        onClick = { onPickWallpaperClick?.invoke() }
                    )
                    if (hasCustomWallpaper) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Reset to Default Wallpaper",
                            isFocused = items.getOrNull(focusedIndex) == "reset_wallpaper",
                            isDestructive = true,
                            onClick = { onResetWallpaperClick?.invoke() }
                        )
                    }
                }
            }

            item {
                SettingsCard(categoryTitle = "Visual Effects") {
                    SettingsToggleRow(
                        title = "PixelSpray Overlay",
                        subtitle = "Particle effects on home wallpaper",
                        checked = isPixelSprayEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "pixelspray",
                        onCheckedChange = { PreferencesManager.setPixelSprayWallpaperEnabled(context, it) }
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

/**
 * Tier 2: Pointer & Hardware Sub-Screen
 */
@Composable
private fun CursorHardwareSettingsSubScreen(
    onBack: () -> Unit,
    onOpenHardwareButtons: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var moveSpeed by remember { mutableFloatStateOf(CursorPreferences.getMoveSpeed(context)) }
    var acceleration by remember { mutableFloatStateOf(CursorPreferences.getAcceleration(context)) }
    var cursorSizeDp by remember { mutableIntStateOf(CursorPreferences.getCursorSizeDp(context)) }
    var clickMethod by remember { mutableStateOf(CursorPreferences.getClickMethod(context)) }
    var customUri by remember { mutableStateOf(CursorPreferences.getCustomCursorUri(context)) }

    var isServiceActive by remember { mutableStateOf(false) }

    fun checkServiceStatus() {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isEnabledInSettings = enabledServices?.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name.contains("VirtualCursorService")
        } == true
        isServiceActive = isEnabledInSettings || VirtualCursorService.isServiceRunning
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkServiceStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { checkServiceStatus() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            CursorPreferences.setCustomCursorUri(context, uri)
            customUri = uri
        }
    }

    val totalItems = 7 // Service, Speed, Accel, Size, ClickMethod, SpritePicker, HardwareKeys

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedIndex)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Cursor & Hardware",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
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
                        when (focusedIndex) {
                            0 -> {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {}
                            }
                            4 -> {
                                val nextMethod = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE) {
                                    ClickMethod.ROOT_INPUT_TAP
                                } else {
                                    ClickMethod.ACCESSIBILITY_GESTURE
                                }
                                CursorPreferences.setClickMethod(context, nextMethod)
                                clickMethod = nextMethod
                            }
                            5 -> {
                                try {
                                    filePicker.launch(arrayOf("image/gif", "image/png"))
                                } catch (_: Exception) {}
                            }
                            6 -> onOpenHardwareButtons?.invoke()
                        }
                    }
                    return@onPreviewKeyEvent true
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
                        val handled = when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                when (focusedIndex) {
                                    1 -> {
                                        val newSpeed = (moveSpeed - 2f).coerceIn(4f, 26f)
                                        CursorPreferences.setMoveSpeed(context, newSpeed)
                                        moveSpeed = newSpeed
                                        true
                                    }
                                    2 -> {
                                        val newAccel = (acceleration - 0.02f).coerceIn(1.00f, 1.25f)
                                        CursorPreferences.setAcceleration(context, newAccel)
                                        acceleration = newAccel
                                        true
                                    }
                                    3 -> {
                                        val newSize = (cursorSizeDp - 4).coerceIn(20, 52)
                                        CursorPreferences.setCursorSizeDp(context, newSize)
                                        cursorSizeDp = newSize
                                        true
                                    }
                                    else -> false
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                when (focusedIndex) {
                                    1 -> {
                                        val newSpeed = (moveSpeed + 2f).coerceIn(4f, 26f)
                                        CursorPreferences.setMoveSpeed(context, newSpeed)
                                        moveSpeed = newSpeed
                                        true
                                    }
                                    2 -> {
                                        val newAccel = (acceleration + 0.02f).coerceIn(1.00f, 1.25f)
                                        CursorPreferences.setAcceleration(context, newAccel)
                                        acceleration = newAccel
                                        true
                                    }
                                    3 -> {
                                        val newSize = (cursorSizeDp + 4).coerceIn(20, 52)
                                        CursorPreferences.setCursorSizeDp(context, newSize)
                                        cursorSizeDp = newSize
                                        true
                                    }
                                    else -> false
                                }
                            }
                            else -> false
                        }
                        if (handled) {
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
                SettingsCard(categoryTitle = "Accessibility Service") {
                    SettingsNavigationRow(
                        title = "Virtual Cursor Status",
                        subtitle = if (isServiceActive) "Active (Press Side Key/F4 to toggle)" else "Inactive (Click to open Android Settings)",
                        icon = if (isServiceActive) "✓" else "!",
                        isFocused = focusedIndex == 0,
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Movement & Physics") {
                    SettingsStepperRow(
                        title = "Base Speed",
                        subtitle = "Pointer movement velocity",
                        valueDisplay = "${moveSpeed.roundToInt()} px",
                        isFocused = focusedIndex == 1,
                        onDecrement = {
                            val newSpeed = (moveSpeed - 2f).coerceIn(4f, 26f)
                            CursorPreferences.setMoveSpeed(context, newSpeed)
                            moveSpeed = newSpeed
                        },
                        onIncrement = {
                            val newSpeed = (moveSpeed + 2f).coerceIn(4f, 26f)
                            CursorPreferences.setMoveSpeed(context, newSpeed)
                            moveSpeed = newSpeed
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Acceleration",
                        subtitle = "Velocity ramp multiplier",
                        valueDisplay = String.format(Locale.US, "%.2fx", acceleration),
                        isFocused = focusedIndex == 2,
                        onDecrement = {
                            val newAccel = (acceleration - 0.02f).coerceIn(1.00f, 1.25f)
                            CursorPreferences.setAcceleration(context, newAccel)
                            acceleration = newAccel
                        },
                        onIncrement = {
                            val newAccel = (acceleration + 0.02f).coerceIn(1.00f, 1.25f)
                            CursorPreferences.setAcceleration(context, newAccel)
                            acceleration = newAccel
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Cursor Size",
                        subtitle = "On-screen pointer dimensions",
                        valueDisplay = "$cursorSizeDp dp",
                        isFocused = focusedIndex == 3,
                        onDecrement = {
                            val newSize = (cursorSizeDp - 4).coerceIn(20, 52)
                            CursorPreferences.setCursorSizeDp(context, newSize)
                            cursorSizeDp = newSize
                        },
                        onIncrement = {
                            val newSize = (cursorSizeDp + 4).coerceIn(20, 52)
                            CursorPreferences.setCursorSizeDp(context, newSize)
                            cursorSizeDp = newSize
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Click Dispatch Mechanism") {
                    SettingsNavigationRow(
                        title = "Click Mode",
                        subtitle = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE) {
                            "Accessibility Gesture (Non-root)"
                        } else {
                            "Root Shell Injection (libsu daemon)"
                        },
                        icon = if (clickMethod == ClickMethod.ROOT_INPUT_TAP) "⚡" else "👆",
                        isFocused = focusedIndex == 4,
                        onClick = {
                            val nextMethod = if (clickMethod == ClickMethod.ACCESSIBILITY_GESTURE) {
                                ClickMethod.ROOT_INPUT_TAP
                            } else {
                                ClickMethod.ACCESSIBILITY_GESTURE
                            }
                            CursorPreferences.setClickMethod(context, nextMethod)
                            clickMethod = nextMethod
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Custom Cursor Sprite") {
                    SettingsNavigationRow(
                        title = "Cursor Graphic",
                        subtitle = if (customUri != null) "Custom sprite active" else "Default kawaii paw sprite",
                        icon = "🐾",
                        isFocused = focusedIndex == 5,
                        onClick = {
                            try {
                                filePicker.launch(arrayOf("image/gif", "image/png"))
                            } catch (_: Exception) {}
                        }
                    )
                    if (customUri != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsActionRow(
                            title = "Reset to Default Sprite",
                            isFocused = false,
                            isDestructive = true,
                            onClick = {
                                CursorPreferences.setCustomCursorUri(context, null)
                                customUri = null
                            }
                        )
                    }
                }
            }

            item {
                SettingsCard(categoryTitle = "Hardware Buttons") {
                    SettingsNavigationRow(
                        title = "Key Remapping",
                        subtitle = "Physical key behavior (Side key, F1-F4, Recents)",
                        icon = "🎮",
                        isFocused = focusedIndex == 6,
                        onClick = { onOpenHardwareButtons?.invoke() }
                    )
                }
            }
        }
    }
}

/**
 * Tier 3: Layout & Behavior Sub-Screen
 */
@Composable
private fun BehaviorSettingsSubScreen(
    onBack: () -> Unit,
    onOpenHiddenApps: (() -> Unit)?,
    onCreateFolderClick: (() -> Unit)?,
    onOpenSpeedDial: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val isFolderGridMode by PreferencesManager.isFolderGridMode.collectAsState()
    val isT9SearchEnabled by PreferencesManager.isT9SearchEnabled.collectAsState()
    val isDpadWrapAround by PreferencesManager.isDpadWrapAround.collectAsState()
    val isDpadBoundaryHaptics by PreferencesManager.isDpadBoundaryHaptics.collectAsState()
    val keypadSoundMode by PreferencesManager.keypadSoundMode.collectAsState()
    val isLidLedEnabled by PreferencesManager.isLidLedEnabled.collectAsState()
    val isLidLedChargingEnabled by PreferencesManager.isLidLedChargingEnabled.collectAsState()
    val lidLedColor by PreferencesManager.lidLedColor.collectAsState()
    val isMediaVolumeLockEnabled by PreferencesManager.isMediaVolumeLockEnabled.collectAsState()
    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()
    val systemMonitorInterval by PreferencesManager.systemMonitorInterval.collectAsState()

    // Dynamic item list matching LazyColumn visual card order
    val items = remember(onCreateFolderClick, isSystemMonitorEnabled, isLidLedEnabled) {
        buildList {
            if (onCreateFolderClick != null) {
                add("create_folder")
            }
            add("folder_grid")
            add("t9_search")
            add("hidden_apps")
            add("speed_dial")
            add("dpad_wrap")
            add("boundary_haptics")
            add("keypad_sound")
            add("media_volume_lock")
            add("lid_led_notification")
            if (isLidLedEnabled) {
                add("lid_led_color")
                add("lid_led_charging")
            }
            add("system_monitor")
            if (isSystemMonitorEnabled) {
                add("monitor_interval")
            }
        }
    }

    val totalItems = items.size

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedIndex)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Behavior",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (items.getOrNull(focusedIndex)) {
                            "create_folder" -> onCreateFolderClick?.invoke()
                            "folder_grid" -> PreferencesManager.setFolderGridMode(context, !isFolderGridMode)
                            "t9_search" -> PreferencesManager.setT9SearchEnabled(context, !isT9SearchEnabled)
                            "speed_dial" -> onOpenSpeedDial?.invoke()
                            "dpad_wrap" -> PreferencesManager.setDpadWrapAround(context, !isDpadWrapAround)
                            "boundary_haptics" -> PreferencesManager.setDpadBoundaryHaptics(context, !isDpadBoundaryHaptics)
                            "keypad_sound" -> {
                                val nextMode = when (keypadSoundMode) {
                                    "kawaii" -> "retro"
                                    "retro" -> "off"
                                    else -> "kawaii"
                                }
                                PreferencesManager.setKeypadSoundMode(context, nextMode)
                                SoundEffectManager.play(context, SoundEffect.SELECT)
                            }
                            "media_volume_lock" -> {
                                val next = !isMediaVolumeLockEnabled
                                if (next && !HardwareRemapManager.isAccessibilityServiceEnabled(context)) {
                                    Toast.makeText(
                                        context,
                                        "Please enable Mochibun in Accessibility Services ♡",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    try {
                                        context.startActivity(
                                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {}
                                } else {
                                    PreferencesManager.setMediaVolumeLockEnabled(context, next)
                                    if (next) {
                                        SoundEffectManager.play(context, SoundEffect.SELECT)
                                    } else {
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                }
                            }
                            "lid_led_notification" -> {
                                val next = !isLidLedEnabled
                                PreferencesManager.setLidLedEnabled(context, next)
                                if (next) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
                            }
                            "lid_led_color" -> {
                                val nextColor = when (lidLedColor) {
                                    "pink" -> "purple"
                                    "purple" -> "white"
                                    "white" -> "green"
                                    else -> "pink"
                                }
                                PreferencesManager.setLidLedColor(context, nextColor)
                                SoundEffectManager.play(context, SoundEffect.SELECT)
                            }
                            "lid_led_charging" -> {
                                val next = !isLidLedChargingEnabled
                                PreferencesManager.setLidLedChargingEnabled(context, next)
                                LidLedManager.updateLedState()
                            }
                            "hidden_apps" -> onOpenHiddenApps?.invoke()
                            "system_monitor" -> PreferencesManager.setSystemMonitorEnabled(context, !isSystemMonitorEnabled)
                            "monitor_interval" -> {
                                val nextInterval = when (systemMonitorInterval) {
                                    1 -> 2
                                    2 -> 3
                                    3 -> 5
                                    else -> 1
                                }
                                PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
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
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                when (items.getOrNull(focusedIndex)) {
                                    "keypad_sound" -> {
                                        val nextMode = when (keypadSoundMode) {
                                            "kawaii" -> "off"
                                            "retro" -> "kawaii"
                                            else -> "retro"
                                        }
                                        PreferencesManager.setKeypadSoundMode(context, nextMode)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "lid_led_color" -> {
                                        val nextColor = when (lidLedColor) {
                                            "pink" -> "green"
                                            "purple" -> "pink"
                                            "white" -> "purple"
                                            else -> "white"
                                        }
                                        PreferencesManager.setLidLedColor(context, nextColor)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "monitor_interval" -> {
                                        val nextInterval = when (systemMonitorInterval) {
                                            5 -> 3
                                            3 -> 2
                                            2 -> 1
                                            else -> 5
                                        }
                                        PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                when (items.getOrNull(focusedIndex)) {
                                    "keypad_sound" -> {
                                        val nextMode = when (keypadSoundMode) {
                                            "kawaii" -> "retro"
                                            "retro" -> "off"
                                            else -> "kawaii"
                                        }
                                        PreferencesManager.setKeypadSoundMode(context, nextMode)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "lid_led_color" -> {
                                        val nextColor = when (lidLedColor) {
                                            "pink" -> "purple"
                                            "purple" -> "white"
                                            "white" -> "green"
                                            else -> "pink"
                                        }
                                        PreferencesManager.setLidLedColor(context, nextColor)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                    "monitor_interval" -> {
                                        val nextInterval = when (systemMonitorInterval) {
                                            1 -> 2
                                            2 -> 3
                                            3 -> 5
                                            else -> 1
                                        }
                                        PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                                        SoundEffectManager.play(context, SoundEffect.NAV)
                                    }
                                }
                            }
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
                SettingsCard(categoryTitle = "Folder Options") {
                    if (onCreateFolderClick != null) {
                        SettingsNavigationRow(
                            title = "Create New Folder",
                            subtitle = "Group favorite apps into a kawaii folder",
                            icon = "📁",
                            isFocused = items.getOrNull(focusedIndex) == "create_folder",
                            onClick = { onCreateFolderClick() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    SettingsToggleRow(
                        title = "2×2 Folder Grid",
                        subtitle = if (isFolderGridMode) "2×2 Grid layout active" else "Compact single-column list active",
                        checked = isFolderGridMode,
                        isFocused = items.getOrNull(focusedIndex) == "folder_grid",
                        onCheckedChange = { PreferencesManager.setFolderGridMode(context, it) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "App Drawer") {
                    SettingsToggleRow(
                        title = "T9 Keyboard Search",
                        subtitle = "Filter applications using physical numeric keypad",
                        checked = isT9SearchEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "t9_search",
                        onCheckedChange = { PreferencesManager.setT9SearchEnabled(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Hidden Apps",
                        subtitle = "Manage hidden launcher packages",
                        icon = "🙈",
                        isFocused = items.getOrNull(focusedIndex) == "hidden_apps",
                        onClick = { onOpenHiddenApps?.invoke() }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Keypad & D-Pad") {
                    SettingsNavigationRow(
                        title = "Speed Dial (Keys 2-9)",
                        subtitle = "Assign numeric shortcuts for home screen long-press",
                        icon = "⚡",
                        isFocused = items.getOrNull(focusedIndex) == "speed_dial",
                        onClick = { onOpenSpeedDial?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "D-Pad Wrap-Around",
                        subtitle = if (isDpadWrapAround) "Navigation loops top to bottom & left to right" else "Navigation stops at list boundaries",
                        checked = isDpadWrapAround,
                        isFocused = items.getOrNull(focusedIndex) == "dpad_wrap",
                        onCheckedChange = { PreferencesManager.setDpadWrapAround(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Boundary Haptics",
                        subtitle = if (isDpadBoundaryHaptics) "Subtle buzz when hitting edge limits" else "Silent boundaries",
                        checked = isDpadBoundaryHaptics,
                        isFocused = items.getOrNull(focusedIndex) == "boundary_haptics",
                        onCheckedChange = { PreferencesManager.setDpadBoundaryHaptics(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val soundModeLabel = when (keypadSoundMode) {
                        "kawaii" -> "Kawaii Pop"
                        "retro" -> "Retro Keitai"
                        else -> "Muted / Off"
                    }
                    val soundModeSubtitle = when (keypadSoundMode) {
                        "kawaii" -> "Sweet 8-bit resonant chimes & bubbles"
                        "retro" -> "Crisp tactile mechanical switch clicks"
                        else -> "Silent keypad navigation"
                    }
                    SettingsStepperRow(
                        title = "Keypad Audio FX",
                        subtitle = soundModeSubtitle,
                        valueDisplay = soundModeLabel,
                        isFocused = items.getOrNull(focusedIndex) == "keypad_sound",
                        onDecrement = {
                            val nextMode = when (keypadSoundMode) {
                                "kawaii" -> "off"
                                "retro" -> "kawaii"
                                else -> "retro"
                            }
                            PreferencesManager.setKeypadSoundMode(context, nextMode)
                            if (nextMode != "off") {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                        },
                        onIncrement = {
                            val nextMode = when (keypadSoundMode) {
                                "kawaii" -> "retro"
                                "retro" -> "off"
                                else -> "kawaii"
                            }
                            PreferencesManager.setKeypadSoundMode(context, nextMode)
                            if (nextMode != "off") {
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Lock Volume Keys to Media",
                        subtitle = if (isMediaVolumeLockEnabled) "Physical volume buttons adjust media only & keep call vol max" else "System default volume behavior",
                        checked = isMediaVolumeLockEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "media_volume_lock",
                        onCheckedChange = { enabled ->
                            if (enabled && !HardwareRemapManager.isAccessibilityServiceEnabled(context)) {
                                Toast.makeText(
                                    context,
                                    "Please enable Mochibun in Accessibility Services ♡",
                                    Toast.LENGTH_LONG
                                ).show()
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            } else {
                                PreferencesManager.setMediaVolumeLockEnabled(context, enabled)
                                if (enabled) {
                                    SoundEffectManager.play(context, SoundEffect.SELECT)
                                } else {
                                    SoundEffectManager.play(context, SoundEffect.NAV)
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Clamshell & Exterior LED") {
                    SettingsToggleRow(
                        title = "Lid Notification LED",
                        subtitle = if (isLidLedEnabled) "Pulse exterior breathing LED when lid is closed" else "Exterior notification LED disabled",
                        checked = isLidLedEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "lid_led_notification",
                        onCheckedChange = {
                            PreferencesManager.setLidLedEnabled(context, it)
                            if (it) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
                        }
                    )
                    if (isLidLedEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val colorLabel = when (lidLedColor) {
                            "purple" -> "💜 Lavender Purple"
                            "white" -> "🤍 Pure White"
                            "green" -> "🌿 Retro Green"
                            else -> "🌸 Sakura Pink"
                        }
                        SettingsStepperRow(
                            title = "Lid LED Color",
                            subtitle = "Exterior light accent for notifications & charging",
                            valueDisplay = colorLabel,
                            isFocused = items.getOrNull(focusedIndex) == "lid_led_color",
                            onDecrement = {
                                val nextColor = when (lidLedColor) {
                                    "pink" -> "green"
                                    "purple" -> "pink"
                                    "white" -> "purple"
                                    else -> "white"
                                }
                                PreferencesManager.setLidLedColor(context, nextColor)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            },
                            onIncrement = {
                                val nextColor = when (lidLedColor) {
                                    "pink" -> "purple"
                                    "purple" -> "white"
                                    "white" -> "green"
                                    else -> "pink"
                                }
                                PreferencesManager.setLidLedColor(context, nextColor)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Lid Charging Indicator",
                            subtitle = if (isLidLedChargingEnabled) "Light exterior LED while charging with lid closed" else "Charging light disabled",
                            checked = isLidLedChargingEnabled,
                            isFocused = items.getOrNull(focusedIndex) == "lid_led_charging",
                            onCheckedChange = {
                                PreferencesManager.setLidLedChargingEnabled(context, it)
                                LidLedManager.updateLedState()
                            }
                        )
                    }
                }
            }

            item {
                SettingsCard(categoryTitle = "System Monitor Card") {
                    SettingsToggleRow(
                        title = "System Monitor Deck",
                        subtitle = "Display live CPU, RAM, & storage gauges",
                        checked = isSystemMonitorEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "system_monitor",
                        onCheckedChange = { PreferencesManager.setSystemMonitorEnabled(context, it) }
                    )
                    if (isSystemMonitorEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Refresh Interval",
                            subtitle = "Polling frequency (lower = smoother, higher = battery friendly)",
                            valueDisplay = "$systemMonitorInterval sec",
                            isFocused = items.getOrNull(focusedIndex) == "monitor_interval",
                            onDecrement = {
                                val nextInterval = when (systemMonitorInterval) {
                                    5 -> 3
                                    3 -> 2
                                    2 -> 1
                                    else -> 5
                                }
                                PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                            },
                            onIncrement = {
                                val nextInterval = when (systemMonitorInterval) {
                                    1 -> 2
                                    2 -> 3
                                    3 -> 5
                                    else -> 1
                                }
                                PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tier 4: About & System Sub-Screen
 */
@Composable
private fun AboutMochibunSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val rt = Runtime.getRuntime()
    val usedRamMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    val maxRamMb = rt.maxMemory() / (1024 * 1024)

    val dm = context.resources.displayMetrics
    val displayResolution = remember(configuration, dm.widthPixels, dm.heightPixels) {
        "${dm.widthPixels} × ${dm.heightPixels} px (${configuration.densityDpi} dpi)"
    }

    val manufacturer = remember {
        Build.MANUFACTURER.orEmpty().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }
    val model = remember { Build.MODEL.orEmpty() }
    val deviceDisplay = remember(manufacturer, model) {
        if (manufacturer.isNotEmpty() && !model.startsWith(manufacturer, ignoreCase = true)) {
            "$manufacturer $model".trim()
        } else {
            model.ifEmpty { manufacturer.ifEmpty { "Android Device" } }
        }
    }

    val osVersion = remember {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    val primaryAbi = remember {
        Build.SUPPORTED_ABIS?.firstOrNull() ?: "Unknown"
    }

    val isTargetDevice = remember {
        Build.MODEL.contains("M120", ignoreCase = true) || Build.DEVICE.contains("M120", ignoreCase = true)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "About Mochibun",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                val theme = LocalLauncherTheme.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MochibunMascot(
                        mood = MascotMood.DEFAULT,
                        size = 78.dp,
                        themeTintColor = theme.mascotTintColor
                    )
                }
                SettingsCard(categoryTitle = "Application Info") {
                    SettingsInfoRow(label = "Application", value = "Mochibun Launcher")
                    SettingsInfoRow(label = "Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    SettingsInfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
                }
            }

            item {
                SettingsCard(categoryTitle = "Hardware Specifications") {
                    SettingsInfoRow(label = "Model / Device", value = deviceDisplay)
                    if (isTargetDevice) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ActiveTheme.surfaceTint.copy(alpha = 0.25f))
                                .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🌸",
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = "Home sweet home! Running on native M120 hardware ✨",
                                    color = ActiveTheme.accentDeep,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    SettingsInfoRow(label = "OS Version", value = osVersion)
                    SettingsInfoRow(label = "Architecture", value = primaryAbi)
                    SettingsInfoRow(label = "Display", value = displayResolution)
                    SettingsInfoRow(label = "Active RAM Envelope", value = "$usedRamMb MB / $maxRamMb MB")
                }
            }

            item {
                SettingsCard(categoryTitle = "Open Source, License & Credits") {
                    SettingsInfoRow(label = "License", value = "Apache 2.0 License")
                    SettingsInfoRow(label = "Root Bridge", value = "libsu:core daemon")
                    SettingsInfoRow(label = "Image Loader", value = "Coil (GIF & Bitmap)")
                }
            }
        }
    }
}

/**
 * Tier 1: Backup & Restore Sub-Screen
 */
@Composable
private fun BackupRestoreSettingsSubScreen(
    onBack: () -> Unit,
    onSettingsRestored: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showExportBundleDialog by remember { mutableStateOf(false) }
    var pendingImportPreviewData by remember { mutableStateOf<com.crinklecodone.mochibun.data.repository.BundlePreviewData?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // State for pending export configuration awaiting file creation URI
    var pendingBundleName by remember { mutableStateOf("Mochibun Setup") }
    var pendingIncludeWallpaper by remember { mutableStateOf(true) }
    var pendingIncludeCursor by remember { mutableStateOf(true) }
    var pendingIncludeDisplayName by remember { mutableStateOf(true) }

    // Setup Bundle (.mochibun) Create launcher
    val bundleCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val result = com.crinklecodone.mochibun.data.repository.ThemeBundleManager.exportBundle(
                        context = context,
                        outputStream = outputStream,
                        bundleName = pendingBundleName,
                        includeWallpaper = pendingIncludeWallpaper,
                        includeCustomCursor = pendingIncludeCursor,
                        includeDisplayName = pendingIncludeDisplayName
                    )
                    if (result.isSuccess) {
                        statusMessage = "Theme bundle exported! ♡"
                    } else {
                        statusMessage = "Export failed: ${result.exceptionOrNull()?.message}"
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Export failed: ${e.message}"
            }
        }
    }

    // Setup Bundle (.mochibun) Import launcher
    val bundleImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val previewRes = com.crinklecodone.mochibun.data.repository.ThemeBundleManager.readBundleManifest(inputStream)
                    if (previewRes.isSuccess) {
                        pendingImportPreviewData = previewRes.getOrThrow()
                        pendingImportUri = uri
                    } else {
                        statusMessage = "Invalid bundle: ${previewRes.exceptionOrNull()?.message}"
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Failed to inspect bundle: ${e.message}"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BackupRestoreManager.exportBackup(context, outputStream)
                }
                statusMessage = "Backup exported successfully! ♡"
            } catch (e: Exception) {
                statusMessage = "Export failed: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val result = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BackupRestoreManager.importBackup(context, inputStream)
                }
                if (result != null && result.isSuccess) {
                    val summary = result.getOrThrow()
                    onSettingsRestored?.invoke()
                    statusMessage = "Restored: ${summary.folderCount} folders, ${summary.favoriteCount} favorites, ${summary.remapCount} remaps! ♡"
                } else {
                    statusMessage = "Restore failed: ${result?.exceptionOrNull()?.message ?: "Invalid file"}"
                }
            } catch (e: Exception) {
                statusMessage = "Restore failed: ${e.message}"
            }
        }
    }

    val items = listOf("export_bundle", "import_bundle", "export", "restore", "reset")
    val totalItems = items.size

    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedIndex)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Backup & Restore",
        showBack = true,
        onBack = onBack,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onBack()
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
                        when (items.getOrNull(focusedIndex)) {
                            "export_bundle" -> showExportBundleDialog = true
                            "import_bundle" -> bundleImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            "export" -> exportLauncher.launch(BackupRestoreManager.generateBackupFileName())
                            "restore" -> importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                            "reset" -> showResetConfirmDialog = true
                        }
                    }
                    return@onPreviewKeyEvent true
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
                SettingsCard(categoryTitle = "Share & Theme Presets") {
                    SettingsNavigationRow(
                        title = "Export Setup Bundle (.mochibun)",
                        subtitle = "Share theme, colors, greeting & cursor in a safe archive",
                        icon = "🎀",
                        isFocused = focusedIndex == 0,
                        onClick = {
                            showExportBundleDialog = true
                        }
                    )

                    SettingsNavigationRow(
                        title = "Import Setup Bundle",
                        subtitle = "Preview and apply a shared .mochibun preset",
                        icon = "✨",
                        isFocused = focusedIndex == 1,
                        onClick = {
                            bundleImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Full System Backup") {
                    SettingsNavigationRow(
                        title = "Export Full Backup",
                        subtitle = "Save layout, folders, themes & key remaps to a .json file",
                        icon = "📤",
                        isFocused = focusedIndex == 2,
                        onClick = {
                            exportLauncher.launch(BackupRestoreManager.generateBackupFileName())
                        }
                    )

                    SettingsNavigationRow(
                        title = "Restore Full Backup",
                        subtitle = "Import a complete Mochibun .json backup file",
                        icon = "📥",
                        isFocused = focusedIndex == 3,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Factory Reset") {
                    SettingsNavigationRow(
                        title = "Reset to Defaults",
                        subtitle = "Reset launcher layout, folders, remaps & preferences",
                        icon = "🔄",
                        isFocused = focusedIndex == 4,
                        onClick = {
                            showResetConfirmDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showExportBundleDialog) {
        com.crinklecodone.mochibun.ui.dialogs.ExportBundleDialog(
            onDismiss = { showExportBundleDialog = false },
            onExport = { name, wall, cur, disp ->
                pendingBundleName = name
                pendingIncludeWallpaper = wall
                pendingIncludeCursor = cur
                pendingIncludeDisplayName = disp
                showExportBundleDialog = false
                bundleCreateLauncher.launch(com.crinklecodone.mochibun.data.repository.ThemeBundleManager.generateBundleFileName(name))
            }
        )
    }

    pendingImportPreviewData?.let { preview ->
        com.crinklecodone.mochibun.ui.dialogs.PresetPreviewDialog(
            previewData = preview,
            onApply = {
                val uri = pendingImportUri
                if (uri != null) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val applyRes = com.crinklecodone.mochibun.data.repository.ThemeBundleManager.applyBundle(context, stream)
                            if (applyRes.isSuccess) {
                                onSettingsRestored?.invoke()
                                statusMessage = "Preset '${preview.manifest.bundleName}' applied! ♡"
                            } else {
                                statusMessage = "Apply failed: ${applyRes.exceptionOrNull()?.message}"
                            }
                        }
                    } catch (e: Exception) {
                        statusMessage = "Apply failed: ${e.message}"
                    }
                }
                try {
                    preview.wallpaperPreview?.recycle()
                } catch (_: Exception) {}
                pendingImportPreviewData = null
                pendingImportUri = null
            },
            onDismiss = {
                try {
                    preview.wallpaperPreview?.recycle()
                } catch (_: Exception) {}
                pendingImportPreviewData = null
                pendingImportUri = null
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset to Factory Defaults", fontWeight = FontWeight.Bold, color = ActiveTheme.accentDeep) },
            text = { Text("Are you sure you want to reset all Mochibun settings, layout, folders, and key remaps back to factory defaults?", color = ActiveTheme.textPrimary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        BackupRestoreManager.resetToDefaults(context)
                        onSettingsRestored?.invoke()
                        showResetConfirmDialog = false
                        statusMessage = "Reset to factory defaults completed."
                    }
                ) {
                    Text("Reset", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = ActiveTheme.textPrimary)
                }
            },
            containerColor = ActiveTheme.container,
            shape = RoundedCornerShape(20.dp)
        )
    }

    statusMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            statusMessage = null
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
    onOpenSpeedDial: (() -> Unit)? = null,
    onPickWallpaperClick: (() -> Unit)? = null,
    onResetWallpaperClick: (() -> Unit)? = null,
    hasCustomWallpaper: Boolean = false,
    onSettingsRestored: (() -> Unit)? = null
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        SettingsHubScreen(
            onClose = onDismiss,
            onOpenThemeStudio = onOpenThemeStudio,
            onOpenIconPackDialog = onOpenIconPackDialog,
            onOpenHardwareButtons = onOpenHardwareButtons,
            onOpenHiddenApps = onOpenHiddenApps,
            onCreateFolderClick = onCreateFolderClick,
            onOpenSpeedDial = onOpenSpeedDial,
            onPickWallpaperClick = onPickWallpaperClick,
            onResetWallpaperClick = onResetWallpaperClick,
            hasCustomWallpaper = hasCustomWallpaper,
            onSettingsRestored = onSettingsRestored
        )
    }
}
