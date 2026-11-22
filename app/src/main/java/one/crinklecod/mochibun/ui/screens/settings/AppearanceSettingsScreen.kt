package one.crinklecod.mochibun.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import one.crinklecod.mochibun.data.repository.SystemFontSyncManager
import one.crinklecod.mochibun.data.repository.TypographyManager
import one.crinklecod.mochibun.ui.components.MascotSpriteSource
import one.crinklecod.mochibun.ui.components.MascotSpriteStore
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.components.mochibunCardBorder
import one.crinklecod.mochibun.ui.dialogs.ResizeWithKeyboard
import one.crinklecod.mochibun.ui.dialogs.isBackDismissKey
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

internal enum class AppearanceRowId(val id: String) {
    THEME("theme"),
    COMMUNITY_GALLERY("community_gallery"),
    TYPOGRAPHY_BORDERS("typography_borders"),
    GREETING_TICKER("greeting_ticker"),
    WALLPAPER_DECO("wallpaper_deco"),
    WIDGETS_CARDS("widgets_cards")
}

internal val APPEARANCE_ROW_IDS: List<String> = AppearanceRowId.entries.map { it.id }

internal enum class AppearanceTypographyBordersRowId(val id: String) {
    TYPOGRAPHY("typography"),
    RESET_FONT("reset_font"),
    APPLY_SYSTEM_FONT("apply_system_font"),
    RESET_SYSTEM_FONT("reset_system_font"),
    BORDER_STYLE("border_style"),
    PICK_BORDER("pick_border"),
    RESET_BORDER("reset_border")
}

internal enum class AppearanceGreetingTickerRowId(val id: String) {
    NAME("name"),
    SUB_DISPLAY_TICKER("sub_display_ticker"),
    SUB_DISPLAY_STYLE("sub_display_style")
}

internal enum class AppearanceWallpaperDecoRowId(val id: String) {
    PICK_WALLPAPER("pick_wallpaper"),
    RESET_WALLPAPER("reset_wallpaper"),
    PICK_MASCOT("pick_mascot"),
    RESET_MASCOT("reset_mascot"),
    MASCOT_SIZE("mascot_size"),
    ICON_PACK("icon_pack"),
    WALLPAPER_STICKERS("wallpaper_stickers")
}

internal enum class AppearanceWidgetsCardsRowId(val id: String) {
    SYSTEM_MONITOR("system_monitor"),
    MONITOR_INTERVAL("monitor_interval"),
    PHOTO_FRAME("photo_frame")
}

internal fun appearanceTypographyBordersRows(
    hasBundleFont: Boolean,
    showRootOptions: Boolean,
    isSystemFontInstalled: Boolean,
    hasCustomBorder: Boolean
): List<AppearanceTypographyBordersRowId> = buildList {
    add(AppearanceTypographyBordersRowId.TYPOGRAPHY)
    if (hasBundleFont) {
        add(AppearanceTypographyBordersRowId.RESET_FONT)
        if (showRootOptions) {
            add(AppearanceTypographyBordersRowId.APPLY_SYSTEM_FONT)
            if (isSystemFontInstalled) add(AppearanceTypographyBordersRowId.RESET_SYSTEM_FONT)
        }
    }
    add(AppearanceTypographyBordersRowId.BORDER_STYLE)
    add(AppearanceTypographyBordersRowId.PICK_BORDER)
    if (hasCustomBorder) add(AppearanceTypographyBordersRowId.RESET_BORDER)
}

internal fun appearanceTypographyBordersRowIds(
    hasBundleFont: Boolean,
    showRootOptions: Boolean,
    isSystemFontInstalled: Boolean,
    hasCustomBorder: Boolean
): List<String> = appearanceTypographyBordersRows(
    hasBundleFont = hasBundleFont,
    showRootOptions = showRootOptions,
    isSystemFontInstalled = isSystemFontInstalled,
    hasCustomBorder = hasCustomBorder
).map { it.id }

internal fun appearanceGreetingTickerRows(
    isSubDisplayTickerEnabled: Boolean
): List<AppearanceGreetingTickerRowId> = buildList {
    add(AppearanceGreetingTickerRowId.NAME)
    add(AppearanceGreetingTickerRowId.SUB_DISPLAY_TICKER)
    if (isSubDisplayTickerEnabled) add(AppearanceGreetingTickerRowId.SUB_DISPLAY_STYLE)
}

internal fun appearanceGreetingTickerRowIds(
    isSubDisplayTickerEnabled: Boolean
): List<String> = appearanceGreetingTickerRows(isSubDisplayTickerEnabled).map { it.id }

internal fun appearanceWallpaperDecoRows(
    hasCustomWallpaper: Boolean,
    hasCustomMascot: Boolean
): List<AppearanceWallpaperDecoRowId> = buildList {
    add(AppearanceWallpaperDecoRowId.PICK_WALLPAPER)
    if (hasCustomWallpaper) add(AppearanceWallpaperDecoRowId.RESET_WALLPAPER)
    add(AppearanceWallpaperDecoRowId.PICK_MASCOT)
    if (hasCustomMascot) add(AppearanceWallpaperDecoRowId.RESET_MASCOT)
    add(AppearanceWallpaperDecoRowId.MASCOT_SIZE)
    add(AppearanceWallpaperDecoRowId.ICON_PACK)
    add(AppearanceWallpaperDecoRowId.WALLPAPER_STICKERS)
}

internal fun appearanceWallpaperDecoRowIds(
    hasCustomWallpaper: Boolean,
    hasCustomMascot: Boolean
): List<String> = appearanceWallpaperDecoRows(hasCustomWallpaper, hasCustomMascot).map { it.id }

internal fun appearanceWidgetsCardsRows(
    isSystemMonitorEnabled: Boolean
): List<AppearanceWidgetsCardsRowId> = buildList {
    add(AppearanceWidgetsCardsRowId.SYSTEM_MONITOR)
    if (isSystemMonitorEnabled) add(AppearanceWidgetsCardsRowId.MONITOR_INTERVAL)
    add(AppearanceWidgetsCardsRowId.PHOTO_FRAME)
}

internal fun appearanceWidgetsCardsRowIds(
    isSystemMonitorEnabled: Boolean
): List<String> = appearanceWidgetsCardsRows(isSystemMonitorEnabled).map { it.id }

internal fun typographyBordersSummarySubtitle(
    fontName: String,
    borderLabel: String,
    hasCustomBorder: Boolean
): String = if (hasCustomBorder) {
    "$fontName · Custom border"
} else {
    "$fontName · $borderLabel"
}

internal fun greetingTickerSummarySubtitle(
    customName: String,
    isSubDisplayTickerEnabled: Boolean
): String {
    val namePart = customName.ifBlank { "No greeting" }
    val tickerPart = if (isSubDisplayTickerEnabled) "Ticker On" else "Ticker Off"
    return "$namePart · $tickerPart"
}

internal fun wallpaperDecoSummarySubtitle(
    mascotSizeLabel: String,
    stickersEnabled: Boolean
): String = "Mascot $mascotSizeLabel · Stickers ${if (stickersEnabled) "On" else "Off"}"

internal fun widgetsCardsSummarySubtitle(
    isSystemMonitorEnabled: Boolean,
    systemMonitorInterval: Int,
    isPhotoFrameEnabled: Boolean
): String {
    val monitorPart = if (isSystemMonitorEnabled) "Monitor ${systemMonitorInterval}s" else "Monitor Off"
    val photoPart = if (isPhotoFrameEnabled) "Photo On" else "Photo Off"
    return "$monitorPart · $photoPart"
}

/**
 * Tier 1: Look & Aesthetic Parent Screen (6 fixed rows + 4 sub-screens).
 */
@Composable
internal fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    onOpenThemeStudio: (() -> Unit)?,
    onOpenTypographyBorders: () -> Unit = {},
    onOpenGreetingTicker: () -> Unit = {},
    onOpenWallpaperDeco: () -> Unit = {},
    onOpenWidgetsCards: () -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val rows = AppearanceRowId.entries
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    val customName by remember { mutableStateOf(PreferencesManager.getCustomUserName(context)) }
    val isWallpaperOverlayEnabled by PreferencesManager.isWallpaperOverlayEnabled.collectAsState()
    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()
    val systemMonitorInterval by PreferencesManager.systemMonitorInterval.collectAsState()
    val isPhotoFrameEnabled by PreferencesManager.isPhotoFrameEnabled.collectAsState()
    val isSubDisplayTickerEnabled by PreferencesManager.isSubDisplayTickerEnabled.collectAsState()
    val homeMascotSize by PreferencesManager.homeMascotSize.collectAsState()
    val fontId by PreferencesManager.themeFontFamily.collectAsState()
    val fontOptions by TypographyManager.options.collectAsState()
    val activeBorderStyle by CustomCardBorderManager.activeBorderStyle.collectAsState()
    val customBorderSource by CustomCardBorderManager.source.collectAsState()
    val hasCustomBorder = customBorderSource is CustomCardBorderSource.Custom

    LaunchedEffect(Unit) {
        TypographyManager.scanFontsAsync(context)
    }

    fun isRowFocused(row: AppearanceRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: AppearanceRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun targetCardIndex(): Int = when (rows.getOrNull(focusedIndex)) {
        AppearanceRowId.THEME,
        AppearanceRowId.COMMUNITY_GALLERY,
        AppearanceRowId.TYPOGRAPHY_BORDERS -> 0
        AppearanceRowId.GREETING_TICKER,
        AppearanceRowId.WALLPAPER_DECO,
        AppearanceRowId.WIDGETS_CARDS -> 1
        null -> 0
    }

    ScrollToFocusedItem(listState) { targetCardIndex() }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun activateRow(row: AppearanceRowId) {
        SoundEffectManager.play(context, SoundEffect.SELECT)
        when (row) {
            AppearanceRowId.THEME -> onOpenThemeStudio?.invoke()
            AppearanceRowId.COMMUNITY_GALLERY -> openCommunityGallery(context)
            AppearanceRowId.TYPOGRAPHY_BORDERS -> onOpenTypographyBorders()
            AppearanceRowId.GREETING_TICKER -> onOpenGreetingTicker()
            AppearanceRowId.WALLPAPER_DECO -> onOpenWallpaperDeco()
            AppearanceRowId.WIDGETS_CARDS -> onOpenWidgetsCards()
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Appearance",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 104,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(rows[targetIndex])
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (
                            native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                            native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                        ) -1 else 1
                        stepIndex(direction)
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val currentFont = remember(fontId, fontOptions) {
            fontOptions.firstOrNull { it.id == fontId } ?: TypographyManager.BuiltInTypographyOptions.first()
        }

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
                        docomoIndex = 95,
                        isFocused = isRowFocused(AppearanceRowId.THEME),
                        jumpNumber = jumpNumber(AppearanceRowId.THEME),
                        onClick = { activateRow(AppearanceRowId.THEME) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Community Gallery",
                        subtitle = "Browse community themes & LEDs • crinklecod.one/Mochibun ♡",
                        docomoIndex = 13,
                        isFocused = isRowFocused(AppearanceRowId.COMMUNITY_GALLERY),
                        jumpNumber = jumpNumber(AppearanceRowId.COMMUNITY_GALLERY),
                        onClick = { activateRow(AppearanceRowId.COMMUNITY_GALLERY) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Typography & Borders",
                        subtitle = typographyBordersSummarySubtitle(
                            currentFont.name,
                            activeBorderStyle.label,
                            hasCustomBorder
                        ),
                        docomoIndex = 104,
                        isFocused = isRowFocused(AppearanceRowId.TYPOGRAPHY_BORDERS),
                        jumpNumber = jumpNumber(AppearanceRowId.TYPOGRAPHY_BORDERS),
                        onClick = { activateRow(AppearanceRowId.TYPOGRAPHY_BORDERS) }
                    )
                }
            }

            item(contentType = "appearance_home_deco_card") {
                SettingsCard(categoryTitle = "Home & Deco") {
                    SettingsNavigationRow(
                        title = "Greeting & Ticker",
                        subtitle = greetingTickerSummarySubtitle(customName, isSubDisplayTickerEnabled),
                        docomoIndex = 132,
                        isFocused = isRowFocused(AppearanceRowId.GREETING_TICKER),
                        jumpNumber = jumpNumber(AppearanceRowId.GREETING_TICKER),
                        onClick = { activateRow(AppearanceRowId.GREETING_TICKER) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Wallpaper & Deco",
                        subtitle = wallpaperDecoSummarySubtitle(
                            homeMascotSize.label,
                            isWallpaperOverlayEnabled
                        ),
                        docomoIndex = 109,
                        isFocused = isRowFocused(AppearanceRowId.WALLPAPER_DECO),
                        jumpNumber = jumpNumber(AppearanceRowId.WALLPAPER_DECO),
                        onClick = { activateRow(AppearanceRowId.WALLPAPER_DECO) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Widgets & Cards",
                        subtitle = widgetsCardsSummarySubtitle(
                            isSystemMonitorEnabled,
                            systemMonitorInterval,
                            isPhotoFrameEnabled
                        ),
                        docomoIndex = 105,
                        isFocused = isRowFocused(AppearanceRowId.WIDGETS_CARDS),
                        jumpNumber = jumpNumber(AppearanceRowId.WIDGETS_CARDS),
                        onClick = { activateRow(AppearanceRowId.WIDGETS_CARDS) }
                    )
                }
            }
        }
    }
}

/**
 * Appearance → Typography & Borders sub-screen: font style, bundle/system font actions, and card borders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceTypographyBordersSettingsScreen(
    onBack: () -> Unit,
    onNestedBackHandlerChanged: ((() -> Unit)?) -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val fontId by PreferencesManager.themeFontFamily.collectAsState()
    val fontOptions by TypographyManager.options.collectAsState()
    val hasBundleFont = fontOptions.any { it.id == TypographyManager.BUNDLE_FONT_ID }
    val showRootOptions by PreferencesManager.showRootOptions.collectAsState()
    val showSystemFontRootActions = hasBundleFont && showRootOptions
    var isSystemFontInstalled by remember { mutableStateOf(false) }
    var isSystemFontBusy by remember { mutableStateOf(false) }
    var systemFontStatusMessage by remember { mutableStateOf<String?>(null) }
    var showRebootConfirmDialog by remember { mutableStateOf(false) }
    var rebootDialogMessage by remember { mutableStateOf("") }
    val activeBorderStyle by CustomCardBorderManager.activeBorderStyle.collectAsState()
    val customBorderSource by CustomCardBorderManager.source.collectAsState()
    val hasCustomBorder = customBorderSource is CustomCardBorderSource.Custom

    val rows = remember(hasBundleFont, showRootOptions, isSystemFontInstalled, hasCustomBorder) {
        appearanceTypographyBordersRows(
            hasBundleFont = hasBundleFont,
            showRootOptions = showRootOptions,
            isSystemFontInstalled = isSystemFontInstalled,
            hasCustomBorder = hasCustomBorder
        )
    }
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    DisposableEffect(showRebootConfirmDialog) {
        onNestedBackHandlerChanged(
            if (showRebootConfirmDialog) {
                { showRebootConfirmDialog = false }
            } else {
                null
            }
        )
        onDispose { onNestedBackHandlerChanged(null) }
    }

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

    LaunchedEffect(rows.size) {
        focusedIndex = focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(Unit) {
        TypographyManager.scanFontsAsync(context)
    }

    LaunchedEffect(showSystemFontRootActions) {
        isSystemFontInstalled = if (showSystemFontRootActions) {
            SystemFontSyncManager.isModuleInstalled()
        } else {
            false
        }
    }

    fun isRowFocused(row: AppearanceTypographyBordersRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: AppearanceTypographyBordersRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun targetCardIndex(): Int = when (rows.getOrNull(focusedIndex)) {
        AppearanceTypographyBordersRowId.TYPOGRAPHY,
        AppearanceTypographyBordersRowId.RESET_FONT,
        AppearanceTypographyBordersRowId.APPLY_SYSTEM_FONT,
        AppearanceTypographyBordersRowId.RESET_SYSTEM_FONT -> 0
        AppearanceTypographyBordersRowId.BORDER_STYLE,
        AppearanceTypographyBordersRowId.PICK_BORDER,
        AppearanceTypographyBordersRowId.RESET_BORDER -> 1
        null -> 0
    }

    ScrollToFocusedItem(listState) { targetCardIndex() }

    fun stepFont(direction: Int) {
        val curIdx = fontOptions.indexOfFirst { it.id == fontId }.coerceAtLeast(0)
        val nextIdx = (curIdx + direction + fontOptions.size) % fontOptions.size
        PreferencesManager.setThemeFontFamily(context, fontOptions[nextIdx].id)
    }

    fun stepBorderStyle(direction: Int) {
        val nextStyle = if (direction < 0) activeBorderStyle.previous() else activeBorderStyle.next()
        CustomCardBorderManager.setBorderStyle(context, nextStyle)
        val prefs = PreferencesManager.getPrefs(context)
        saveTheme(prefs, loadSavedTheme(prefs).copy(borderStyle = nextStyle.id))
    }

    fun activateRow(row: AppearanceTypographyBordersRowId) {
        when (row) {
            AppearanceTypographyBordersRowId.TYPOGRAPHY -> {
                stepFont(1)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            AppearanceTypographyBordersRowId.RESET_FONT -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                focusedIndex = rows.indexOf(AppearanceTypographyBordersRowId.TYPOGRAPHY).coerceAtLeast(0)
                scope.launch {
                    withContext(Dispatchers.IO) { TypographyManager.resetBundleFont(context) }
                    Toast.makeText(context, "♡ Bundle font removed", Toast.LENGTH_SHORT).show()
                }
            }
            AppearanceTypographyBordersRowId.APPLY_SYSTEM_FONT -> {
                if (isSystemFontBusy) return
                SoundEffectManager.play(context, SoundEffect.SELECT)
                isSystemFontBusy = true
                systemFontStatusMessage = "Installing Magisk system font module…"
                scope.launch {
                    val result = SystemFontSyncManager.applyBundleFontSystemWide(context)
                    isSystemFontBusy = false
                    result.fold(
                        onSuccess = {
                            isSystemFontInstalled = true
                            val note = "♡ Magisk font staged — reboot to apply system-wide"
                            systemFontStatusMessage = note
                            rebootDialogMessage =
                                "Staged custom font in ${SystemFontSyncManager.MODULE_DIR}.\n\nReboot now so Android loads it system-wide?"
                            showRebootConfirmDialog = true
                            Toast.makeText(context, note, Toast.LENGTH_LONG).show()
                        },
                        onFailure = { error ->
                            val msg = "System font not applied: ${error.message ?: "Root error"}"
                            systemFontStatusMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            AppearanceTypographyBordersRowId.RESET_SYSTEM_FONT -> {
                if (isSystemFontBusy) return
                SoundEffectManager.play(context, SoundEffect.SELECT)
                isSystemFontBusy = true
                systemFontStatusMessage = "Removing Magisk system font module…"
                focusedIndex = rows.indexOf(AppearanceTypographyBordersRowId.APPLY_SYSTEM_FONT).coerceAtLeast(0)
                scope.launch {
                    val result = SystemFontSyncManager.removeSystemFontModule()
                    isSystemFontBusy = false
                    result.fold(
                        onSuccess = {
                            isSystemFontInstalled = false
                            val note = "♡ System font module removed — reboot to restore default"
                            systemFontStatusMessage = note
                            rebootDialogMessage =
                                "Removed ${SystemFontSyncManager.MODULE_DIR}.\n\nReboot now to restore the default system font?"
                            showRebootConfirmDialog = true
                            Toast.makeText(context, note, Toast.LENGTH_LONG).show()
                        },
                        onFailure = { error ->
                            val msg = "Reset failed: ${error.message ?: "Root error"}"
                            systemFontStatusMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            AppearanceTypographyBordersRowId.BORDER_STYLE -> {
                stepBorderStyle(1)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            }
            AppearanceTypographyBordersRowId.PICK_BORDER -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                borderPicker.launch(arrayOf("image/png"))
            }
            AppearanceTypographyBordersRowId.RESET_BORDER -> {
                SoundEffectManager.play(context, SoundEffect.SELECT)
                CustomCardBorderManager.resetCustomBorder(context)
                focusedIndex = rows.indexOf(AppearanceTypographyBordersRowId.PICK_BORDER).coerceAtLeast(0)
                Toast.makeText(context, "♡ Custom card border reset", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Typography & Borders",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 104,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(rows[targetIndex])
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        when (rows.getOrNull(focusedIndex)) {
                            AppearanceTypographyBordersRowId.TYPOGRAPHY -> {
                                stepFont(direction)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            AppearanceTypographyBordersRowId.BORDER_STYLE -> {
                                stepBorderStyle(direction)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            else -> stepIndex(direction)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val currentFont = remember(fontId, fontOptions) {
            fontOptions.firstOrNull { it.id == fontId } ?: TypographyManager.BuiltInTypographyOptions.first()
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "appearance_typography_card") {
                SettingsCard(categoryTitle = "Typography") {
                    SettingsStepperRow(
                        title = "Font Style",
                        subtitle = currentFont.subtitle,
                        valueDisplay = currentFont.name,
                        isFocused = isRowFocused(AppearanceTypographyBordersRowId.TYPOGRAPHY),
                        jumpNumber = jumpNumber(AppearanceTypographyBordersRowId.TYPOGRAPHY),
                        onDecrement = {
                            stepFont(-1)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            stepFont(1)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                    if (hasBundleFont) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Remove Bundle Font",
                            isFocused = isRowFocused(AppearanceTypographyBordersRowId.RESET_FONT),
                            isDestructive = true,
                            jumpNumber = jumpNumber(AppearanceTypographyBordersRowId.RESET_FONT),
                            onClick = { activateRow(AppearanceTypographyBordersRowId.RESET_FONT) }
                        )
                        if (showRootOptions) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsActionRow(
                                title = if (isSystemFontBusy) "Applying System Font…" else "Apply Font System-Wide (Root)",
                                isFocused = isRowFocused(AppearanceTypographyBordersRowId.APPLY_SYSTEM_FONT),
                                jumpNumber = jumpNumber(AppearanceTypographyBordersRowId.APPLY_SYSTEM_FONT),
                                onClick = { activateRow(AppearanceTypographyBordersRowId.APPLY_SYSTEM_FONT) }
                            )
                            if (isSystemFontInstalled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                SettingsActionRow(
                                    title = "Reset System Font",
                                    isFocused = isRowFocused(AppearanceTypographyBordersRowId.RESET_SYSTEM_FONT),
                                    isDestructive = true,
                                    jumpNumber = jumpNumber(AppearanceTypographyBordersRowId.RESET_SYSTEM_FONT),
                                    onClick = { activateRow(AppearanceTypographyBordersRowId.RESET_SYSTEM_FONT) }
                                )
                            }
                            val bannerText = systemFontStatusMessage
                                ?: if (isSystemFontInstalled) {
                                    "✓ Magisk system font active/staged • reboot after changes"
                                } else null
                            if (bannerText != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ActiveTheme.surfaceTint.copy(alpha = 0.35f))
                                        .border(1.dp, ActiveTheme.cardBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = bannerText,
                                        color = ActiveTheme.accentDeep,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(contentType = "appearance_borders_card") {
                SettingsCard(categoryTitle = "Card Borders") {
                    SettingsStepperRow(
                        title = "Card Border Style",
                        subtitle = if (hasCustomBorder) {
                            "Overridden by custom 9-slice PNG"
                        } else {
                            activeBorderStyle.subtitle
                        },
                        valueDisplay = activeBorderStyle.label,
                        isFocused = isRowFocused(AppearanceTypographyBordersRowId.BORDER_STYLE),
                        jumpNumber = jumpNumber(AppearanceTypographyBordersRowId.BORDER_STYLE),
                        onDecrement = {
                            stepBorderStyle(-1)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            stepBorderStyle(1)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val customBorderSubtitle = (customBorderSource as? CustomCardBorderSource.Custom)?.let {
                        "Active: ${it.spec.width}×${it.spec.height}px (${it.spec.slicePx}px slice)"
                    } ?: "9-slice PNG frame (16–256px, max 512 KB)"
                    SettingsNavigationRow(
                        title = "Custom Border Frame",
                        subtitle = customBorderSubtitle,
                        docomoIndex = 104,
                        isFocused = isRowFocused(AppearanceTypographyBordersRowId.PICK_BORDER),
                        jumpNumber = jumpNumber(AppearanceTypographyBordersRowId.PICK_BORDER),
                        onClick = { activateRow(AppearanceTypographyBordersRowId.PICK_BORDER) }
                    )
                    if (hasCustomBorder) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Reset Card Border",
                            isFocused = isRowFocused(AppearanceTypographyBordersRowId.RESET_BORDER),
                            isDestructive = true,
                            jumpNumber = jumpNumber(AppearanceTypographyBordersRowId.RESET_BORDER),
                            onClick = { activateRow(AppearanceTypographyBordersRowId.RESET_BORDER) }
                        )
                    }
                }
            }
        }

        if (showRebootConfirmDialog) {
            val rebootDialogRequester = remember { FocusRequester() }
            var rebootButtonIndex by remember { mutableIntStateOf(1) } // Default to "Later" for safety
            var sawDialogCenterDown by remember { mutableStateOf(false) }

            fun confirmRebootSelection(index: Int) {
                if (index == 0) {
                    showRebootConfirmDialog = false
                    scope.launch {
                        val res = SystemFontSyncManager.rebootDevice()
                        res.onFailure { err ->
                            Toast.makeText(
                                context,
                                "Please reboot manually: ${err.message ?: "Reboot failed"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    showRebootConfirmDialog = false
                }
            }

            requestFocusSafely(rebootDialogRequester)

            BasicAlertDialog(
                onDismissRequest = { showRebootConfirmDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
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
                        .padding(16.dp)
                        .focusRequester(rebootDialogRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            if (isBackDismissKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_SOFT_RIGHT) {
                                if (event.type == KeyEventType.KeyUp) {
                                    SoundEffectManager.play(context, SoundEffect.BACK)
                                    showRebootConfirmDialog = false
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (isCenterActivateKey(native.keyCode)) {
                                if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                                    sawDialogCenterDown = true
                                    SoundEffectManager.play(context, SoundEffect.CLICK)
                                } else if (event.type == KeyEventType.KeyUp && sawDialogCenterDown) {
                                    sawDialogCenterDown = false
                                    SoundEffectManager.play(context, SoundEffect.SELECT)
                                    confirmRebootSelection(rebootButtonIndex)
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (event.type == KeyEventType.KeyDown &&
                                (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                                    native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                            ) {
                                rebootButtonIndex = if (rebootButtonIndex == 0) 1 else 0
                                SoundEffectManager.play(context, SoundEffect.NAV)
                                return@onPreviewKeyEvent true
                            }
                            false
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "— ♡ Reboot Required ♡ —",
                        color = ActiveTheme.accentDeep,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = rebootDialogMessage,
                        color = ActiveTheme.textPrimary,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsActionRow(
                            title = "Reboot Now",
                            isFocused = rebootButtonIndex == 0,
                            modifier = Modifier.weight(1f),
                            onClick = { confirmRebootSelection(0) }
                        )
                        SettingsActionRow(
                            title = "Later",
                            isFocused = rebootButtonIndex == 1,
                            modifier = Modifier.weight(1f),
                            onClick = { confirmRebootSelection(1) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Appearance → Greeting & Ticker sub-screen: greeting name and outer sub-display ticker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceGreetingTickerSettingsScreen(
    onBack: () -> Unit,
    onNestedBackHandlerChanged: ((() -> Unit)?) -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var customName by remember { mutableStateOf(PreferencesManager.getCustomUserName(context)) }
    var isEditingName by remember { mutableStateOf(false) }
    var tempNameInput by remember { mutableStateOf(customName) }
    val isSubDisplayTickerEnabled by PreferencesManager.isSubDisplayTickerEnabled.collectAsState()
    val isSubDisplayTickerDark by PreferencesManager.isSubDisplayTickerDark.collectAsState()

    val rows = remember(isSubDisplayTickerEnabled) {
        appearanceGreetingTickerRows(isSubDisplayTickerEnabled)
    }
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    DisposableEffect(isEditingName) {
        onNestedBackHandlerChanged(
            if (isEditingName) {
                { isEditingName = false }
            } else {
                null
            }
        )
        onDispose { onNestedBackHandlerChanged(null) }
    }

    LaunchedEffect(rows.size) {
        focusedIndex = focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    }

    fun isRowFocused(row: AppearanceGreetingTickerRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: AppearanceGreetingTickerRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    ScrollToFocusedItem(listState) { 0 }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun activateRow(row: AppearanceGreetingTickerRowId) {
        SoundEffectManager.play(context, SoundEffect.SELECT)
        when (row) {
            AppearanceGreetingTickerRowId.NAME -> {
                tempNameInput = customName
                isEditingName = true
            }
            AppearanceGreetingTickerRowId.SUB_DISPLAY_TICKER ->
                PreferencesManager.setSubDisplayTickerEnabled(context, !isSubDisplayTickerEnabled)
            AppearanceGreetingTickerRowId.SUB_DISPLAY_STYLE ->
                PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark)
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Greeting & Ticker",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 132,
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                    }
                    return@onPreviewKeyEvent true
                }
                if (!isEditingName) {
                    val digit = getDigitFromKeyCode(native.keyCode)
                    if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            val targetIndex = digit - 1
                            focusedIndex = targetIndex
                            activateRow(rows[targetIndex])
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (rows.getOrNull(focusedIndex) == AppearanceGreetingTickerRowId.SUB_DISPLAY_STYLE) {
                            PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else {
                            stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "appearance_greeting_card") {
                SettingsCard(categoryTitle = "Greeting & Ticker") {
                    SettingsTextInputRow(
                        title = "Greeting Name",
                        subtitle = if (customName.isNotBlank()) "♡ Welcome home, $customName ♡" else "Tap or Enter to set greeting name",
                        value = customName.ifBlank { "Not set" },
                        isFocused = isRowFocused(AppearanceGreetingTickerRowId.NAME),
                        jumpNumber = jumpNumber(AppearanceGreetingTickerRowId.NAME),
                        onClick = { activateRow(AppearanceGreetingTickerRowId.NAME) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Sub-Display Ticker",
                        subtitle = "Cozy retro keitai status marquee on Home ♡",
                        checked = isSubDisplayTickerEnabled,
                        isFocused = isRowFocused(AppearanceGreetingTickerRowId.SUB_DISPLAY_TICKER),
                        jumpNumber = jumpNumber(AppearanceGreetingTickerRowId.SUB_DISPLAY_TICKER),
                        onCheckedChange = { PreferencesManager.setSubDisplayTickerEnabled(context, it) }
                    )
                    if (isSubDisplayTickerEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Ticker Style",
                            subtitle = "Pick the lid-screen look of your status ticker",
                            valueDisplay = if (isSubDisplayTickerDark) "Dark Sub-OLED" else "Pastel Theme",
                            isFocused = isRowFocused(AppearanceGreetingTickerRowId.SUB_DISPLAY_STYLE),
                            jumpNumber = jumpNumber(AppearanceGreetingTickerRowId.SUB_DISPLAY_STYLE),
                            onDecrement = { PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark) },
                            onIncrement = { PreferencesManager.setSubDisplayTickerDark(context, !isSubDisplayTickerDark) }
                        )
                    }
                }
            }
        }

        if (isEditingName) {
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

/**
 * Appearance → Wallpaper & Deco sub-screen: wallpaper, home mascot sprite/size, icon packs, stickers.
 */
@Composable
internal fun AppearanceWallpaperDecoSettingsScreen(
    onBack: () -> Unit,
    onOpenIconPackDialog: (() -> Unit)?,
    onPickWallpaperClick: (() -> Unit)?,
    onResetWallpaperClick: (() -> Unit)?,
    hasCustomWallpaper: Boolean,
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val isWallpaperOverlayEnabled by PreferencesManager.isWallpaperOverlayEnabled.collectAsState()
    val homeMascotSize by PreferencesManager.homeMascotSize.collectAsState()
    val mascotSource by MascotSpriteStore.source.collectAsState()
    val hasCustomMascot = mascotSource is MascotSpriteSource.Custom

    val rows = remember(hasCustomWallpaper, hasCustomMascot) {
        appearanceWallpaperDecoRows(
            hasCustomWallpaper = hasCustomWallpaper,
            hasCustomMascot = hasCustomMascot
        )
    }
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

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

    LaunchedEffect(rows.size) {
        focusedIndex = focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    }

    fun isRowFocused(row: AppearanceWallpaperDecoRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: AppearanceWallpaperDecoRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun targetCardIndex(): Int = when (rows.getOrNull(focusedIndex)) {
        AppearanceWallpaperDecoRowId.PICK_WALLPAPER,
        AppearanceWallpaperDecoRowId.RESET_WALLPAPER,
        AppearanceWallpaperDecoRowId.PICK_MASCOT,
        AppearanceWallpaperDecoRowId.RESET_MASCOT,
        AppearanceWallpaperDecoRowId.MASCOT_SIZE -> 0
        AppearanceWallpaperDecoRowId.ICON_PACK,
        AppearanceWallpaperDecoRowId.WALLPAPER_STICKERS -> 1
        null -> 0
    }

    ScrollToFocusedItem(listState) { targetCardIndex() }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun activateRow(row: AppearanceWallpaperDecoRowId) {
        SoundEffectManager.play(context, SoundEffect.SELECT)
        when (row) {
            AppearanceWallpaperDecoRowId.PICK_WALLPAPER -> onPickWallpaperClick?.invoke()
            AppearanceWallpaperDecoRowId.RESET_WALLPAPER -> {
                onResetWallpaperClick?.invoke()
                focusedIndex = rows.indexOf(AppearanceWallpaperDecoRowId.PICK_WALLPAPER).coerceAtLeast(0)
            }
            AppearanceWallpaperDecoRowId.PICK_MASCOT -> mascotPicker.launch(arrayOf("image/png"))
            AppearanceWallpaperDecoRowId.RESET_MASCOT -> {
                CustomMascotManager.reset(context)
                focusedIndex = rows.indexOf(AppearanceWallpaperDecoRowId.PICK_MASCOT).coerceAtLeast(0)
                Toast.makeText(context, "♡ Mochibunny is back to default", Toast.LENGTH_SHORT).show()
            }
            AppearanceWallpaperDecoRowId.MASCOT_SIZE ->
                PreferencesManager.setHomeMascotSize(context, homeMascotSize.next())
            AppearanceWallpaperDecoRowId.ICON_PACK -> onOpenIconPackDialog?.invoke()
            AppearanceWallpaperDecoRowId.WALLPAPER_STICKERS ->
                PreferencesManager.setWallpaperOverlayEnabled(context, !isWallpaperOverlayEnabled)
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Wallpaper & Deco",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 109,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(rows[targetIndex])
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (rows.getOrNull(focusedIndex) == AppearanceWallpaperDecoRowId.MASCOT_SIZE) {
                            val nextSize = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                homeMascotSize.previous()
                            } else {
                                homeMascotSize.next()
                            }
                            PreferencesManager.setHomeMascotSize(context, nextSize)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else {
                            stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "appearance_wallpaper_mascot_card") {
                SettingsCard(categoryTitle = "Wallpaper & Mascot") {
                    SettingsNavigationRow(
                        title = "Pick Wallpaper",
                        subtitle = "Choose a cute background from your gallery",
                        docomoIndex = 109,
                        isFocused = isRowFocused(AppearanceWallpaperDecoRowId.PICK_WALLPAPER),
                        jumpNumber = jumpNumber(AppearanceWallpaperDecoRowId.PICK_WALLPAPER),
                        onClick = { activateRow(AppearanceWallpaperDecoRowId.PICK_WALLPAPER) }
                    )
                    if (hasCustomWallpaper) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Reset Wallpaper",
                            isFocused = isRowFocused(AppearanceWallpaperDecoRowId.RESET_WALLPAPER),
                            isDestructive = true,
                            jumpNumber = jumpNumber(AppearanceWallpaperDecoRowId.RESET_WALLPAPER),
                            onClick = { activateRow(AppearanceWallpaperDecoRowId.RESET_WALLPAPER) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Custom Mascot",
                        subtitle = "Upload an 8-row PNG sprite sheet (max 1.5 MB)",
                        docomoIndex = 132,
                        isFocused = isRowFocused(AppearanceWallpaperDecoRowId.PICK_MASCOT),
                        jumpNumber = jumpNumber(AppearanceWallpaperDecoRowId.PICK_MASCOT),
                        onClick = { activateRow(AppearanceWallpaperDecoRowId.PICK_MASCOT) }
                    )
                    if (hasCustomMascot) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsActionRow(
                            title = "Reset Mascot",
                            isFocused = isRowFocused(AppearanceWallpaperDecoRowId.RESET_MASCOT),
                            isDestructive = true,
                            jumpNumber = jumpNumber(AppearanceWallpaperDecoRowId.RESET_MASCOT),
                            onClick = { activateRow(AppearanceWallpaperDecoRowId.RESET_MASCOT) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Mascot Size",
                        subtitle = "How big Mochibunny appears on Home",
                        valueDisplay = homeMascotSize.label,
                        isFocused = isRowFocused(AppearanceWallpaperDecoRowId.MASCOT_SIZE),
                        jumpNumber = jumpNumber(AppearanceWallpaperDecoRowId.MASCOT_SIZE),
                        onDecrement = {
                            PreferencesManager.setHomeMascotSize(context, homeMascotSize.previous())
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        },
                        onIncrement = {
                            PreferencesManager.setHomeMascotSize(context, homeMascotSize.next())
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        }
                    )
                }
            }

            item(contentType = "appearance_icons_stickers_card") {
                SettingsCard(categoryTitle = "Icons & Stickers") {
                    SettingsNavigationRow(
                        title = "Icon Packs",
                        subtitle = "Dress up your apps with installed icon packs",
                        docomoIndex = 105,
                        isFocused = isRowFocused(AppearanceWallpaperDecoRowId.ICON_PACK),
                        jumpNumber = jumpNumber(AppearanceWallpaperDecoRowId.ICON_PACK),
                        onClick = { activateRow(AppearanceWallpaperDecoRowId.ICON_PACK) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Wallpaper Stickers",
                        subtitle = "Sprinkle floating pastel pixel stickers over wallpaper ✦",
                        checked = isWallpaperOverlayEnabled,
                        isFocused = isRowFocused(AppearanceWallpaperDecoRowId.WALLPAPER_STICKERS),
                        jumpNumber = jumpNumber(AppearanceWallpaperDecoRowId.WALLPAPER_STICKERS),
                        onCheckedChange = { PreferencesManager.setWallpaperOverlayEnabled(context, it) }
                    )
                }
            }
        }
    }
}

/**
 * Appearance → Widgets & Cards sub-screen: System Monitor gauges, refresh interval, and Photo Frame card.
 */
@Composable
internal fun AppearanceWidgetsCardsSettingsScreen(
    onBack: () -> Unit,
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val isSystemMonitorEnabled by PreferencesManager.isSystemMonitorEnabled.collectAsState()
    val systemMonitorInterval by PreferencesManager.systemMonitorInterval.collectAsState()
    val isPhotoFrameEnabled by PreferencesManager.isPhotoFrameEnabled.collectAsState()

    val rows = remember(isSystemMonitorEnabled) {
        appearanceWidgetsCardsRows(isSystemMonitorEnabled)
    }
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    LaunchedEffect(rows.size) {
        focusedIndex = focusedIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    }

    fun isRowFocused(row: AppearanceWidgetsCardsRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: AppearanceWidgetsCardsRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    ScrollToFocusedItem(listState) { 0 }

    fun stepMonitorInterval(direction: Int) {
        val next = if (direction < 0) {
            when (systemMonitorInterval) {
                5 -> 3
                3 -> 2
                2 -> 1
                else -> 5
            }
        } else {
            when (systemMonitorInterval) {
                1 -> 2
                2 -> 3
                3 -> 5
                else -> 1
            }
        }
        PreferencesManager.setSystemMonitorInterval(context, next)
    }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun activateRow(row: AppearanceWidgetsCardsRowId) {
        when (row) {
            AppearanceWidgetsCardsRowId.SYSTEM_MONITOR ->
                PreferencesManager.setSystemMonitorEnabled(context, !isSystemMonitorEnabled)
            AppearanceWidgetsCardsRowId.MONITOR_INTERVAL -> stepMonitorInterval(1)
            AppearanceWidgetsCardsRowId.PHOTO_FRAME ->
                PreferencesManager.setPhotoFrameEnabled(context, !isPhotoFrameEnabled)
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Widgets & Cards",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 105,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(rows[targetIndex])
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        if (rows.getOrNull(focusedIndex) == AppearanceWidgetsCardsRowId.MONITOR_INTERVAL) {
                            stepMonitorInterval(direction)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else {
                            stepIndex(direction)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "appearance_widget_deck_card") {
                SettingsCard(categoryTitle = "Widget Deck") {
                    SettingsToggleRow(
                        title = "System Monitor",
                        subtitle = "Show live CPU, RAM & storage gauges on Widgets page",
                        checked = isSystemMonitorEnabled,
                        isFocused = isRowFocused(AppearanceWidgetsCardsRowId.SYSTEM_MONITOR),
                        jumpNumber = jumpNumber(AppearanceWidgetsCardsRowId.SYSTEM_MONITOR),
                        onCheckedChange = { PreferencesManager.setSystemMonitorEnabled(context, it) }
                    )
                    if (isSystemMonitorEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Refresh Interval",
                            subtitle = "How often the system monitor updates",
                            valueDisplay = "$systemMonitorInterval sec",
                            isFocused = isRowFocused(AppearanceWidgetsCardsRowId.MONITOR_INTERVAL),
                            jumpNumber = jumpNumber(AppearanceWidgetsCardsRowId.MONITOR_INTERVAL),
                            onDecrement = { stepMonitorInterval(-1) },
                            onIncrement = { stepMonitorInterval(1) }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Photo Frame Card",
                        subtitle = "Pin a framed photo onto your Widgets page ♡",
                        checked = isPhotoFrameEnabled,
                        isFocused = isRowFocused(AppearanceWidgetsCardsRowId.PHOTO_FRAME),
                        jumpNumber = jumpNumber(AppearanceWidgetsCardsRowId.PHOTO_FRAME),
                        onCheckedChange = { PreferencesManager.setPhotoFrameEnabled(context, it) }
                    )
                }
            }
        }
    }
}
