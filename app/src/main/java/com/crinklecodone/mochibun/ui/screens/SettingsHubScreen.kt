package com.crinklecodone.mochibun.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
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
    onPickWallpaperClick: (() -> Unit)? = null,
    onResetWallpaperClick: (() -> Unit)? = null,
    hasCustomWallpaper: Boolean = false
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
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val totalItems = 4 // 4 primary navigation cards

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
                        onClose()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (focusedIndex) {
                            0 -> onNavigate(SettingsDestination.Appearance)
                            1 -> onNavigate(SettingsDestination.CursorHardware)
                            2 -> onNavigate(SettingsDestination.Behavior)
                            3 -> onNavigate(SettingsDestination.About)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusedIndex = if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        true
                    }
                    else -> false
                }
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
                SettingsCard(categoryTitle = "System & Specs") {
                    SettingsNavigationRow(
                        title = "About Mochibun",
                        subtitle = "Version, target hardware stats & credits",
                        icon = "♡",
                        isFocused = focusedIndex == 3,
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

    // Dynamic item list based on custom wallpaper state
    val items = remember(hasCustomWallpaper) {
        buildList {
            add("theme")
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
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (items.getOrNull(focusedIndex)) {
                            "theme" -> onOpenThemeStudio?.invoke()
                            "name" -> isEditingName = true
                            "icon_pack" -> onOpenIconPackDialog?.invoke()
                            "pick_wallpaper" -> onPickWallpaperClick?.invoke()
                            "reset_wallpaper" -> onResetWallpaperClick?.invoke()
                            "pixelspray" -> PreferencesManager.setPixelSprayWallpaperEnabled(context, !isPixelSprayEnabled)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusedIndex = if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        true
                    }
                    else -> false
                }
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
                    if (event.type == KeyEventType.KeyUp) onBack()
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusedIndex = if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        true
                    }
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    val isFolderGridMode by PreferencesManager.isFolderGridMode.collectAsState()
    val isT9SearchEnabled by PreferencesManager.isT9SearchEnabled.collectAsState()
    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()
    val systemMonitorInterval by PreferencesManager.systemMonitorInterval.collectAsState()

    // Dynamic item list
    val items = remember(onCreateFolderClick, isSystemMonitorEnabled) {
        buildList {
            if (onCreateFolderClick != null) {
                add("create_folder")
            }
            add("folder_grid")
            add("t9_search")
            add("hidden_apps")
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
                    if (event.type == KeyEventType.KeyUp) onBack()
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        when (items.getOrNull(focusedIndex)) {
                            "create_folder" -> onCreateFolderClick?.invoke()
                            "folder_grid" -> PreferencesManager.setFolderGridMode(context, !isFolderGridMode)
                            "t9_search" -> PreferencesManager.setT9SearchEnabled(context, !isT9SearchEnabled)
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusedIndex = if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusedIndex = if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (items.getOrNull(focusedIndex) == "monitor_interval") {
                            val nextInterval = when (systemMonitorInterval) {
                                5 -> 3
                                3 -> 2
                                2 -> 1
                                else -> 5
                            }
                            PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (items.getOrNull(focusedIndex) == "monitor_interval") {
                            val nextInterval = when (systemMonitorInterval) {
                                1 -> 2
                                2 -> 3
                                3 -> 5
                                else -> 1
                            }
                            PreferencesManager.setSystemMonitorInterval(context, nextInterval)
                            true
                        } else false
                    }
                    else -> false
                }
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
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val rt = Runtime.getRuntime()
    val usedRamMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
    val maxRamMb = rt.maxMemory() / (1024 * 1024)

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
                    if (event.type == KeyEventType.KeyUp) onBack()
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
                SettingsCard(categoryTitle = "Application Info") {
                    SettingsInfoRow(label = "Application", value = "Mochibun Launcher")
                    SettingsInfoRow(label = "Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    SettingsInfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
                }
            }

            item {
                SettingsCard(categoryTitle = "Target Hardware") {
                    SettingsInfoRow(label = "Device", value = "ALT Mive Style Folder AT-M120")
                    SettingsInfoRow(label = "SoC", value = "MediaTek MT6761 (Helio A22)")
                    SettingsInfoRow(label = "Architecture", value = "ARMv7 (armeabi-v7a 32-bit)")
                    SettingsInfoRow(label = "Display", value = "480 × 800 px (xhdpi)")
                    SettingsInfoRow(label = "Target OS", value = "Android 12 (API 31)")
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
    hasCustomWallpaper: Boolean = false
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
            onPickWallpaperClick = onPickWallpaperClick,
            onResetWallpaperClick = onResetWallpaperClick,
            hasCustomWallpaper = hasCustomWallpaper
        )
    }
}
