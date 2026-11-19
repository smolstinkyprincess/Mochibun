package one.crinklecod.mochibun.ui.screens.settings

import one.crinklecod.mochibun.ui.dialogs.ResizeWithKeyboard
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.CustomCardBorderManager
import one.crinklecod.mochibun.data.repository.CustomCardBorderSource
import one.crinklecod.mochibun.data.repository.CustomMascotManager
import one.crinklecod.mochibun.data.repository.TypographyManager
import one.crinklecod.mochibun.ui.components.MascotSpriteSource
import one.crinklecod.mochibun.ui.components.MascotSpriteStore
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.components.mochibunCardBorder
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import one.crinklecod.mochibun.ui.theme.loadSavedTheme
import one.crinklecod.mochibun.ui.theme.saveTheme

internal const val COMMUNITY_GALLERY_URL = "https://crinklecod.one/Mochibun/community/"

internal fun openCommunityGallery(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, COMMUNITY_GALLERY_URL.toUri()))
    }.onFailure {
        Toast.makeText(context, "Visit crinklecod.one/Mochibun/community/", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Tier 1: Look & Aesthetic Sub-Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsScreen(
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
    val isSubDisplayTickerEnabled by PreferencesManager.isSubDisplayTickerEnabled.collectAsState()
    val isSubDisplayTickerDark by PreferencesManager.isSubDisplayTickerDark.collectAsState()
    val homeMascotSize by PreferencesManager.homeMascotSize.collectAsState()
    val fontId by PreferencesManager.themeFontFamily.collectAsState()
    val fontOptions by TypographyManager.options.collectAsState()
    val mascotSource by MascotSpriteStore.source.collectAsState()
    val hasCustomMascot = mascotSource is MascotSpriteSource.Custom
    val activeBorderStyle by CustomCardBorderManager.activeBorderStyle.collectAsState()
    val customBorderSource by CustomCardBorderManager.source.collectAsState()
    val hasCustomBorder = customBorderSource is CustomCardBorderSource.Custom
    val scope = rememberCoroutineScope()

    // Custom mascot atlas: copied (max 1.5 MB) and validated off the main thread, then hot-swapped.
    // A rejected file keeps the current mascot and explains why.
    val mascotPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                        ?.use { CustomMascotManager.importPng(context, it) }
                        ?: Result.failure(IllegalArgumentException("Couldn't open that file"))
                }
                val message = result.fold(
                    onSuccess = { "♡ Custom mascot applied" },
                    onFailure = { "Mascot not changed: ${it.message}" }
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Custom 9-slice card border PNG (16x16..256x256 px, max 512 KB) validated off the main thread.
    val borderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    CustomCardBorderManager.importPng(context, uri)
                }
                val message = result.fold(
                    onSuccess = { spec -> "♡ Custom card border applied (${spec.width}×${spec.height}px)" },
                    onFailure = { "Card border not changed: ${it.message}" }
                )
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Dynamic item list based on custom wallpaper, mascot, and border state
    val items = remember(
        hasCustomWallpaper,
        hasCustomMascot,
        hasCustomBorder,
        isSystemMonitorEnabled,
        isSubDisplayTickerEnabled
    ) {
        buildList {
            add("theme")
            add("typography")
            add("community_gallery")
            add("border_style")
            add("pick_border")
            if (hasCustomBorder) add("reset_border")
            add("name")
            add("sub_display_ticker")
            if (isSubDisplayTickerEnabled) add("sub_display_style")
            add("pick_wallpaper")
            if (hasCustomWallpaper) {
                add("reset_wallpaper")
            }
            add("pick_mascot")
            if (hasCustomMascot) add("reset_mascot")
            add("mascot_size")
            add("icon_pack")
            add("wallpaper_stickers")
            add("system_monitor")
            if (isSystemMonitorEnabled) add("monitor_interval")
            add("photo_frame")
        }
    }

    val totalItems = items.size

    // Card that holds the focused row; read lazily by ScrollToFocusedItem (not in composition).
    fun targetCardIndex(): Int =
        when (items.getOrNull(focusedIndex)) {
            "theme", "typography", "community_gallery", "border_style", "pick_border", "reset_border" -> 0
            "name", "sub_display_ticker", "sub_display_style" -> 1
            "icon_pack", "pick_wallpaper", "reset_wallpaper", "pick_mascot", "reset_mascot",
            "mascot_size", "wallpaper_stickers" -> 2
            "system_monitor", "monitor_interval", "photo_frame" -> 3
            else -> 0
        }

    LaunchedEffect(Unit) {
        TypographyManager.scanFontsAsync(context)
    }

    ScrollToFocusedItem(listState) { targetCardIndex() }

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
            "community_gallery" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                openCommunityGallery(context)
            }
            "border_style" -> {
                val nextStyle = activeBorderStyle.next()
                CustomCardBorderManager.setBorderStyle(context, nextStyle)
                val prefs = PreferencesManager.getPrefs(context)
                saveTheme(prefs, loadSavedTheme(prefs).copy(borderStyle = nextStyle.id))
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "pick_border" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                borderPicker.launch(arrayOf("image/png"))
            }
            "reset_border" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                CustomCardBorderManager.resetCustomBorder(context)
                focusedIndex = items.indexOf("pick_border").coerceAtLeast(0)
                Toast.makeText(context, "♡ Custom card border reset", Toast.LENGTH_SHORT).show()
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
            "pick_mascot" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                mascotPicker.launch(arrayOf("image/png"))
            }
            "reset_mascot" -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                CustomMascotManager.reset(context)
                focusedIndex = items.indexOf("pick_mascot").coerceAtLeast(0)
                Toast.makeText(context, "♡ Mochibunny is back to default", Toast.LENGTH_SHORT).show()
            }
            "mascot_size" -> {
                PreferencesManager.setHomeMascotSize(context, homeMascotSize.next())
                SoundEffectManager.play(context, SoundEffect.SELECT)
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
            "sub_display_ticker" -> {
                PreferencesManager.setSubDisplayTickerEnabled(context, !isSubDisplayTickerEnabled)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            "sub_display_style" -> {
                PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Appearance",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == totalItems - 1 },
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
                        } else if (items.getOrNull(focusedIndex) == "border_style") {
                            val nextStyle = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                activeBorderStyle.previous()
                            } else {
                                activeBorderStyle.next()
                            }
                            CustomCardBorderManager.setBorderStyle(context, nextStyle)
                            val prefs = PreferencesManager.getPrefs(context)
                            saveTheme(prefs, loadSavedTheme(prefs).copy(borderStyle = nextStyle.id))
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else if (items.getOrNull(focusedIndex) == "sub_display_style") {
                            // Two-value stepper: Left / Right both flip the style.
                            PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else if (items.getOrNull(focusedIndex) == "mascot_size") {
                            val nextSize = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                homeMascotSize.previous()
                            } else {
                                homeMascotSize.next()
                            }
                            PreferencesManager.setHomeMascotSize(context, nextSize)
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
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "appearance_theme_card") {
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
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Community Gallery & Workshop",
                        subtitle = "crinklecod.one/Mochibun ♡",
                        icon = "✨",
                        isFocused = items.getOrNull(focusedIndex) == "community_gallery",
                        jumpNumber = (items.indexOf("community_gallery") + 1).takeIf { it <= 9 },
                        onClick = { activateAppearanceItem(items.indexOf("community_gallery")) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Card Border Style",
                        subtitle = if (hasCustomBorder) {
                            "Overridden by custom 9-slice PNG"
                        } else {
                            activeBorderStyle.subtitle
                        },
                        valueDisplay = activeBorderStyle.label,
                        isFocused = items.getOrNull(focusedIndex) == "border_style",
                        jumpNumber = (items.indexOf("border_style") + 1).takeIf { it <= 9 },
                        onDecrement = {
                            val prevStyle = activeBorderStyle.previous()
                            CustomCardBorderManager.setBorderStyle(context, prevStyle)
                            val prefs = PreferencesManager.getPrefs(context)
                            saveTheme(prefs, loadSavedTheme(prefs).copy(borderStyle = prevStyle.id))
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            val nextStyle = activeBorderStyle.next()
                            CustomCardBorderManager.setBorderStyle(context, nextStyle)
                            val prefs = PreferencesManager.getPrefs(context)
                            saveTheme(prefs, loadSavedTheme(prefs).copy(borderStyle = nextStyle.id))
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val customBorderSubtitle = (customBorderSource as? CustomCardBorderSource.Custom)?.let {
                        "Active: ${it.spec.width}×${it.spec.height}px (${it.spec.slicePx}px slice)"
                    } ?: "9-slice PNG frame (16–256px, max 512 KB)"
                    SettingsNavigationRow(
                        title = "Custom Border Frame (.png)",
                        subtitle = customBorderSubtitle,
                        icon = "🎀",
                        isFocused = items.getOrNull(focusedIndex) == "pick_border",
                        jumpNumber = (items.indexOf("pick_border") + 1).takeIf { it <= 9 },
                        onClick = { activateAppearanceItem(items.indexOf("pick_border")) }
                    )
                    if (hasCustomBorder) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Reset Custom Border",
                            isFocused = items.getOrNull(focusedIndex) == "reset_border",
                            isDestructive = true,
                            jumpNumber = (items.indexOf("reset_border") + 1).takeIf { it <= 9 },
                            onClick = { activateAppearanceItem(items.indexOf("reset_border")) }
                        )
                    }
                }
            }

            item(contentType = "appearance_greeting_card") {
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
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Outer Sub-Display Ticker",
                        subtitle = "Retro keitai status marquee on Home",
                        checked = isSubDisplayTickerEnabled,
                        isFocused = items.getOrNull(focusedIndex) == "sub_display_ticker",
                        jumpNumber = (items.indexOf("sub_display_ticker") + 1).takeIf { it <= 9 },
                        onCheckedChange = { PreferencesManager.setSubDisplayTickerEnabled(context, it) }
                    )
                    if (isSubDisplayTickerEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Sub-Display Style",
                            subtitle = "Lid-screen look of the ticker",
                            valueDisplay = if (isSubDisplayTickerDark) "Dark Sub-OLED" else "Pastel Theme",
                            isFocused = items.getOrNull(focusedIndex) == "sub_display_style",
                            jumpNumber = (items.indexOf("sub_display_style") + 1).takeIf { it <= 9 },
                            onDecrement = { PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark) },
                            onIncrement = { PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark) }
                        )
                    }
                }
            }

            item(contentType = "appearance_wallpaper_card") {
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
                        title = "Custom Mascot",
                        subtitle = "8-row PNG atlas, max 1.5 MB",
                        icon = "🐰",
                        isFocused = items.getOrNull(focusedIndex) == "pick_mascot",
                        jumpNumber = (items.indexOf("pick_mascot") + 1).takeIf { it <= 9 },
                        onClick = { activateAppearanceItem(items.indexOf("pick_mascot")) }
                    )
                    if (hasCustomMascot) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Reset to Default Mochibunny",
                            isFocused = items.getOrNull(focusedIndex) == "reset_mascot",
                            isDestructive = true,
                            jumpNumber = (items.indexOf("reset_mascot") + 1).takeIf { it <= 9 },
                            onClick = { activateAppearanceItem(items.indexOf("reset_mascot")) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Home Mascot Size",
                        subtitle = "Size of Mochibunny on the home screen",
                        valueDisplay = homeMascotSize.label,
                        isFocused = items.getOrNull(focusedIndex) == "mascot_size",
                        jumpNumber = (items.indexOf("mascot_size") + 1).takeIf { it <= 9 },
                        onDecrement = {
                            PreferencesManager.setHomeMascotSize(context, homeMascotSize.previous())
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            PreferencesManager.setHomeMascotSize(context, homeMascotSize.next())
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
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

            item(contentType = "appearance_widget_deck_card") {
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
                        .mochibunCardBorder(
                            cornerRadius = 22.dp,
                            borderColor = ActiveTheme.cardBorder,
                            borderWidth = 1.5.dp
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(ActiveTheme.container)
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
