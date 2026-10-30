package com.crinklecodone.mochibun.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.crinklecodone.mochibun.ui.components.WallpaperOverlay
import com.crinklecodone.mochibun.core.keypad.HomeTransitionTracker
import com.crinklecodone.mochibun.core.keypad.LauncherKeypadActions
import com.crinklecodone.mochibun.core.keypad.LauncherKeypadState
import com.crinklecodone.mochibun.core.keypad.T9InputState
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.model.AppInfo
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.FavoriteSlotItem
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.BundlePreviewData
import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.crinklecodone.mochibun.data.repository.ThemeBundleManager
import com.crinklecodone.mochibun.ui.components.WidgetPage
import com.crinklecodone.mochibun.ui.dialogs.AppActionDialog
import com.crinklecodone.mochibun.ui.dialogs.CreateFavoriteFolderDialog
import com.crinklecodone.mochibun.ui.dialogs.FavoriteFolderDialog
import com.crinklecodone.mochibun.ui.dialogs.FolderActionDialog
import com.crinklecodone.mochibun.ui.dialogs.HardwareButtonsDialog
import com.crinklecodone.mochibun.ui.dialogs.HiddenAppsDialog
import com.crinklecodone.mochibun.ui.dialogs.IconPackSelectionDialog
import com.crinklecodone.mochibun.ui.dialogs.KaomojiWheelDialog
import com.crinklecodone.mochibun.ui.dialogs.KawaiiIconPicker
import com.crinklecodone.mochibun.ui.dialogs.PresetPreviewDialog
import com.crinklecodone.mochibun.ui.dialogs.awaitFocusSafely
import com.crinklecodone.mochibun.ui.dialogs.dialogWidth
import com.crinklecodone.mochibun.ui.screens.AppDrawerScreen
import com.crinklecodone.mochibun.ui.screens.HomeScreen
import com.crinklecodone.mochibun.ui.screens.NotificationsScreen
import com.crinklecodone.mochibun.ui.screens.SettingsHubDialog
import com.crinklecodone.mochibun.ui.screens.ThemeStudioDialog
import com.crinklecodone.mochibun.ui.screens.firstLetterOf
import com.crinklecodone.mochibun.ui.theme.LauncherTheme
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.loadSavedTheme
import com.crinklecodone.mochibun.ui.theme.saveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified modal hierarchy to eliminate state explosion and prevent modal overlap.
 */
sealed interface ActiveModal {
    data class AppAction(val app: LauncherApp) : ActiveModal
    data class FolderAction(val folder: FavoriteFolder) : ActiveModal
    data class FolderView(
        val folderId: String,
        val startRename: Boolean = false,
        val startPicker: Boolean = false
    ) : ActiveModal
    data class CreateFolder(val initialApp: LauncherApp? = null) : ActiveModal
    data class IconPicker(
        val targetApp: LauncherApp? = null,
        val targetFolder: FavoriteFolder? = null
    ) : ActiveModal
    data class HardwareButtons(val initialKeyCode: Int? = null) : ActiveModal
    data class IncomingBundlePreview(
        val preview: BundlePreviewData,
        val uri: Uri
    ) : ActiveModal
    data object SettingsHub : ActiveModal
    data object ThemeStudio : ActiveModal
    data object IconPackSelection : ActiveModal
    data object HiddenApps : ActiveModal
    data object KaomojiWheel : ActiveModal
}

// main launcher composable managing focus, gestures, state, and page navigation
@Composable
fun MainLauncher(
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel? = null,
    homePressCount: Int = 0,
    homePressedWhileVisible: Boolean = false,
    appListRefreshCount: Int = 0,
    lidOpenCount: Int = 0,
    lidClosedCount: Int = 0,
    unlockCount: Int = 0,
    requestedPage: Int? = null,
    onPageConsumed: () -> Unit = {},
    incomingBundleUri: Uri? = null,
    onConsumeIncomingBundleUri: () -> Unit = {}
) {
    val context = LocalContext.current
    val launcherVm: LauncherViewModel = viewModel ?: remember {
        LauncherViewModel(context.applicationContext as android.app.Application)
    }

    var activeModal by remember { mutableStateOf<ActiveModal?>(null) }

    // Initialize PreferencesManager and ExternalThemeManager synchronously in remember
    // before any StateFlow collection to prevent initial-composition race conditions.
    val preferences = remember(context) {
        PreferencesManager.init(context)
        ExternalThemeManager.init(context)
        PreferencesManager.getPrefs(context)
    }

    var currentTheme by remember {
        mutableStateOf(loadSavedTheme(preferences))
    }
    val themeOptions by ExternalThemeManager.themes.collectAsState()
    LaunchedEffect(themeOptions) {
        val savedPreset = preferences.getString(PreferencesManager.KEY_THEME_PRESET, null)
        if (savedPreset != null && currentTheme.name != savedPreset) {
            val matching = ExternalThemeManager.findTheme(savedPreset)
            if (matching != null) {
                currentTheme = matching.theme
            }
        }
    }

    // Inspect incoming theme bundles completely on Dispatchers.IO to avoid blocking the main UI thread
    LaunchedEffect(incomingBundleUri) {
        val uri = incomingBundleUri
        if (uri != null) {
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        ThemeBundleManager.readBundleManifest(stream)
                    } ?: Result.failure(IllegalStateException("Could not open stream for uri: $uri"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            if (result.isSuccess) {
                activeModal = ActiveModal.IncomingBundlePreview(
                    preview = result.getOrThrow(),
                    uri = uri
                )
            } else {
                val exception = result.exceptionOrNull()
                android.util.Log.e("MainLauncher", "Invalid or unreadable theme bundle", exception)
                Toast.makeText(
                    context,
                    "Failed to read theme bundle: ${exception?.message ?: "Invalid bundle"}",
                    Toast.LENGTH_SHORT
                ).show()
                onConsumeIncomingBundleUri()
            }
        }
    }

    val theme = currentTheme
    val cardRoundness = theme.cardRoundnessDp.dp
    val cardBgColor = theme.cardBgColor
    val cardTextColor = theme.cardTextColor
    val appActionColor = theme.appActionColor

    // wallpaper selection and persistence state
    val wallpaperBitmap = launcherVm.wallpaperBitmap
    val wallpaperImageBitmap = remember(wallpaperBitmap) {
        wallpaperBitmap?.asImageBitmap()
    }
    val wallpaperPicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                launcherVm.setWallpaper(uri)
            }
        }

    var drawerOpen by remember { mutableStateOf(false) }
    // Active pages: 0 = Widgets, 1 = Home, 2 = Notifications
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    var pendingTargetPage by remember { mutableStateOf<Int?>(null) }
    val userName by PreferencesManager.customUserName.collectAsState()
    val isWallpaperOverlayEnabled by PreferencesManager.isWallpaperOverlayEnabled.collectAsState()

    LaunchedEffect(requestedPage) {
        requestedPage?.let { target ->
            val clampedTarget = target.coerceIn(0, 2)
            pendingTargetPage = clampedTarget
            pagerState.animateScrollToPage(clampedTarget)
            onPageConsumed()
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(appListRefreshCount) {
        launcherVm.loadApps()
    }

    val isIconPickerOpen = activeModal is ActiveModal.IconPicker
    LaunchedEffect(isIconPickerOpen) {
        if (isIconPickerOpen) {
            launcherVm.loadKawaiiIconNames()
        } else {
            launcherVm.clearKawaiiIconNames()
        }
    }

    val apps = launcherVm.apps
    val visibleApps = launcherVm.visibleApps
    val hiddenApps = launcherVm.hiddenApps
    val letterStarts = launcherVm.letterStarts
    val favoriteFolders = launcherVm.favoriteFolders
    val favoriteItems = launcherVm.favoriteItems
    val favoritePackages = launcherVm.favoritePackages
    val kawaiiIconNames = launcherVm.kawaiiIconNames

    val favoriteItemCount = favoriteItems.size
    var selectedFavoriteIndex by remember { mutableIntStateOf(0) }
    var savedFavoriteIndexOnHome by remember { mutableIntStateOf(0) }
    val homeTracker = remember { HomeTransitionTracker() }
    var selectedDrawerIndex by remember { mutableIntStateOf(0) }
    var drawerRailFocused by remember { mutableStateOf(false) }
    var drawerRailHoveredLetter by remember { mutableStateOf<Char?>('A') }
    // Home focus area: 0 = FavoritesCard row, 1 = StatusCard (launcher options / page switcher)
    var homeFocusArea by remember { mutableIntStateOf(0) }
    var mascotBoopRequest by remember { mutableIntStateOf(0) }

    val moveFavoriteSlotItem: (itemKey: String, direction: Int) -> Unit = remember(launcherVm) {
        { itemKey, direction ->
            val targetIndex = launcherVm.moveFavoriteSlotItem(itemKey, direction)
            if (targetIndex != null) {
                selectedFavoriteIndex = targetIndex
                savedFavoriteIndexOnHome = targetIndex
                homeFocusArea = 0
            }
        }
    }

    // 0 = center not pressed, 1 = pressed, 2 = held long (menu already opened)
    var centerKeyState by remember { mutableIntStateOf(0) }
    var poundKeyState by remember { mutableIntStateOf(0) }
    val t9State = remember { T9InputState() }
    val focusRequester = remember { FocusRequester() }

    val anyModalOpen = activeModal != null

    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && (pendingTargetPage == null || pendingTargetPage == pagerState.settledPage)) {
            pendingTargetPage = null
        }
    }

    val isHomeScreenActive = !drawerOpen &&
        !anyModalOpen &&
        (pendingTargetPage == null || pendingTargetPage == 1) &&
        pagerState.currentPage == 1 &&
        pagerState.targetPage == 1 &&
        pagerState.settledPage == 1 &&
        !pagerState.isScrollInProgress

    val isSidePageActive = !drawerOpen &&
        !anyModalOpen &&
        (pagerState.currentPage == 0 || pagerState.currentPage == 2)

    val isDrawerActive = drawerOpen && !anyModalOpen

    val isModalActive = anyModalOpen

    // Flip physically closed (never a display timeout): dismiss transient modals, collapse folder/drawer, reset T9 state, and position on Home
    LaunchedEffect(lidClosedCount) {
        if (lidClosedCount > 0) {
            activeModal = null
            drawerOpen = false
            centerKeyState = 0
            poundKeyState = 0
            t9State.t9Key = null
            t9State.t9Time = 0L
            homeFocusArea = if (favoriteItemCount > 0) 0 else 1
            pendingTargetPage = 1
            pagerState.scrollToPage(1)
        }
    }

    // Keyguard dismissed: unlocking always lands on Home, never inside a drawer left open before the lock
    LaunchedEffect(unlockCount) {
        if (unlockCount > 0) {
            drawerOpen = false
            drawerRailFocused = false
        }
    }

    // Entered OPEN_AWAKE (flip opened, or display woke with the flip open): wake focus recovery for
    // physical D-pad contracts. A modal kept open across a display timeout keeps its own focus.
    LaunchedEffect(lidOpenCount) {
        if (lidOpenCount > 0 && !anyModalOpen) {
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isHomeScreenActive) {
        if (isHomeScreenActive) {
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
            val liveFavCount = launcherVm.favoriteItems.size
            selectedFavoriteIndex = savedFavoriteIndexOnHome.coerceIn(
                0,
                (liveFavCount - 1).coerceAtLeast(0)
            )
            if (liveFavCount > 0) {
                homeFocusArea = 0
            }
        }
    }

    LaunchedEffect(favoriteItemCount) {
        val liveFavCount = launcherVm.favoriteItems.size
        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
            0,
            (liveFavCount - 1).coerceAtLeast(0)
        )
        savedFavoriteIndexOnHome = savedFavoriteIndexOnHome.coerceIn(
            0,
            (liveFavCount - 1).coerceAtLeast(0)
        )
        if (liveFavCount == 0) {
            homeFocusArea = 1
        } else if (homeFocusArea == 1) {
            homeFocusArea = 0
        }
    }

    LaunchedEffect(visibleApps) {
        selectedDrawerIndex =
            selectedDrawerIndex.coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))
    }

    // auto scroll to highlighted item in app drawer
    LaunchedEffect(selectedDrawerIndex, drawerOpen, visibleApps.size) {
        if (drawerOpen && visibleApps.isNotEmpty() && selectedDrawerIndex in visibleApps.indices) {
            val selectedItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == selectedDrawerIndex
            }
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val isFullyVisible = selectedItem != null &&
                selectedItem.offset >= 0 &&
                (selectedItem.offset + selectedItem.size) <= viewportEnd

            if (!isFullyVisible) {
                listState.scrollToItem(selectedDrawerIndex)
            }
        }
    }

    val openContextActionForCurrentFocus: () -> Unit = {
        if (drawerOpen) {
            launcherVm.visibleApps.getOrNull(selectedDrawerIndex)?.let { app ->
                activeModal = ActiveModal.AppAction(app)
            }
        } else if (isHomeScreenActive) {
            val favItems = launcherVm.favoriteItems
            if (homeFocusArea == 0 && favItems.isNotEmpty()) {
                when (val item = favItems.getOrNull(selectedFavoriteIndex)) {
                    is FavoriteSlotItem.App -> {
                        activeModal = ActiveModal.AppAction(item.app)
                    }
                    is FavoriteSlotItem.Folder -> {
                        activeModal = ActiveModal.FolderAction(item.folder)
                    }
                    null -> {}
                }
            } else if (homeFocusArea == 2) {
                selectedDrawerIndex = 0
                drawerOpen = true
            } else {
                activeModal = ActiveModal.SettingsHub
            }
        }
    }

    // Timed long-press detection for Center/OK button so holding OK opens menus immediately
    LaunchedEffect(centerKeyState, drawerOpen, isHomeScreenActive, anyModalOpen) {
        if (centerKeyState == 1 && !anyModalOpen && (drawerOpen || isHomeScreenActive)) {
            delay(420L)
            if (centerKeyState == 1 && !anyModalOpen) {
                centerKeyState = 2
                openContextActionForCurrentFocus()
            }
        }
    }

    // Timed long-press detection on '#' to open Kaomoji radial wheel
    LaunchedEffect(poundKeyState, drawerOpen, isHomeScreenActive, anyModalOpen) {
        if (poundKeyState == 1 && !anyModalOpen && (drawerOpen || isHomeScreenActive)) {
            delay(400L)
            if (poundKeyState == 1 && !anyModalOpen) {
                poundKeyState = 2
                performBoundaryHaptic(context)
                SoundEffectManager.play(context, SoundEffect.CLICK)
                activeModal = ActiveModal.KaomojiWheel
            }
        }
    }

    // focus requester handling for physical hardware key navigation
    LaunchedEffect(
        drawerOpen,
        isHomeScreenActive,
        anyModalOpen
    ) {
        // Modal dialogs manage their own focus.
        if (anyModalOpen) {
            centerKeyState = 0
            return@LaunchedEffect
        }

        if (drawerOpen || isHomeScreenActive) {
            focusRequester.awaitFocusSafely(attempts = 6)
        }
    }

    // physical home button press brings user back to home screen;
    // pressed again while already sitting on Home, it opens the app drawer instead
    LaunchedEffect(homePressCount) {
        if (homePressCount > 0 && homePressedWhileVisible && isHomeScreenActive) {
            selectedDrawerIndex = 0
            drawerOpen = true
        } else if (homePressCount > 0) {
            drawerOpen = false
            activeModal = null
            poundKeyState = 0
            homeFocusArea = if (favoriteItemCount > 0) 0 else 1
            pendingTargetPage = 1
            scope.launch {
                pagerState.animateScrollToPage(1)
            }
        }
    }

    val currentDrawerLetter = remember(drawerOpen, selectedDrawerIndex, visibleApps) {
        if (drawerOpen && selectedDrawerIndex in visibleApps.indices) {
            firstLetterOf(visibleApps[selectedDrawerIndex].name)
        } else {
            null
        }
    }

    val keypadActions = remember(launcherVm) {
        object : LauncherKeypadActions {
            override fun setDrawerOpen(open: Boolean) {
                drawerOpen = open
                if (!open) {
                    drawerRailFocused = false
                }
            }
            override fun setSelectedDrawerIndex(index: Int) { selectedDrawerIndex = index }
            override fun setHomeFocusArea(area: Int) { homeFocusArea = area }
            override fun setSelectedFavoriteIndex(index: Int) { selectedFavoriteIndex = index }
            override fun setSavedFavoriteIndexOnHome(index: Int) { savedFavoriteIndexOnHome = index }
            override fun setPendingTargetPage(page: Int?) { pendingTargetPage = page }
            override fun setCenterKeyState(state: Int) { centerKeyState = state }
            override fun setDrawerRailFocused(focused: Boolean) { drawerRailFocused = focused }
            override fun setDrawerRailHoveredLetter(letter: Char?) { drawerRailHoveredLetter = letter }
            override fun openContextActionForCurrentFocus() = openContextActionForCurrentFocus()
            override fun openFolder(folderId: String) {
                activeModal = ActiveModal.FolderView(
                    folderId = folderId,
                    startRename = false,
                    startPicker = false
                )
            }
            override fun openLauncherMenu() { activeModal = ActiveModal.SettingsHub }
            override fun openSpeedDial(digit: Int) {
                activeModal = ActiveModal.HardwareButtons(
                    initialKeyCode = android.view.KeyEvent.KEYCODE_0 + digit
                )
            }
            override fun setPoundKeyState(state: Int) { poundKeyState = state }
            override fun openKaomojiWheel() { activeModal = ActiveModal.KaomojiWheel }
            override fun cycleDrawerSortMode() {
                val nextMode = launcherVm.cycleAppDrawerSortMode()
                SoundEffectManager.play(context, SoundEffect.CLICK)
                Toast.makeText(context, "♡ Sorted: ${nextMode.toastName}", Toast.LENGTH_SHORT).show()
            }
            override fun recordAppLaunch(packageName: String) {
                launcherVm.recordAppLaunch(packageName)
            }
            override fun boopMascot() { mascotBoopRequest++ }
        }
    }

    val keyHandler = remember(context, scope, pagerState, listState, homeTracker, t9State, keypadActions) {
        LauncherKeyHandler(
            context = context,
            scope = scope,
            pagerState = pagerState,
            listState = listState,
            homeTracker = homeTracker,
            t9State = t9State,
            actions = keypadActions
        )
    }

    fun getCurrentKeypadState(): LauncherKeypadState {
        return LauncherKeypadState(
            // Read activeModal live: the captured anyModalOpen val goes stale here, which let the
            // handler eat D-pad presses meant for an open dialog
            anyModalOpen = activeModal != null,
            drawerOpen = drawerOpen,
            isHomeScreenActive = isHomeScreenActive,
            isPageTransitioning = !drawerOpen && (pagerState.isScrollInProgress || (pendingTargetPage != null && pendingTargetPage != pagerState.settledPage)),
            pendingTargetPage = pendingTargetPage,
            favoriteItemCount = launcherVm.favoriteItems.size,
            selectedFavoriteIndex = selectedFavoriteIndex,
            savedFavoriteIndexOnHome = savedFavoriteIndexOnHome,
            homeFocusArea = homeFocusArea,
            centerKeyState = centerKeyState,
            selectedDrawerIndex = selectedDrawerIndex,
            visibleApps = launcherVm.visibleApps,
            favoriteApps = launcherVm.favoriteApps,
            favoriteFolders = launcherVm.favoriteFolders,
            favoriteItems = launcherVm.favoriteItems,
            letterStarts = launcherVm.letterStarts,
            poundKeyState = poundKeyState,
            isDrawerRailFocused = drawerRailFocused,
            drawerRailHoveredLetter = drawerRailHoveredLetter,
            currentPage = pagerState.currentPage
        )
    }

    // Wrap keypad state evaluation and keyHandler in rememberUpdatedState to prevent stale snapshot reads
    val currentKeyHandler by rememberUpdatedState(keyHandler)
    val currentKeypadStateProvider by rememberUpdatedState(::getCurrentKeypadState)

    // Bind accessibility key listener strictly to Lifecycle.State.RESUMED so it is never active in background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, keyHandler) {
        val listener: (android.view.KeyEvent) -> Boolean = { nativeEvent ->
            currentKeyHandler.handleKeyEvent(
                androidx.compose.ui.input.key.KeyEvent(nativeEvent),
                currentKeypadStateProvider()
            )
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                MainActivity.keyEventInterceptor = listener
                com.crinklecodone.mochibun.service.MochibunAccessibilityService.launcherKeyListener = listener
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                MainActivity.keyEventInterceptor = null
                com.crinklecodone.mochibun.service.MochibunAccessibilityService.launcherKeyListener = null
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            MainActivity.keyEventInterceptor = listener
            com.crinklecodone.mochibun.service.MochibunAccessibilityService.launcherKeyListener = listener
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            MainActivity.keyEventInterceptor = null
            com.crinklecodone.mochibun.service.MochibunAccessibilityService.launcherKeyListener = null
        }
    }

    // Hoisted stable dialog action & navigation callbacks to avoid reallocations on background ticks
    val dismissActiveModal = remember {
        { activeModal = null }
    }
    val onOpenLauncherOptions = remember {
        { activeModal = ActiveModal.SettingsHub }
    }
    val onOpenThemeStudio = remember {
        { activeModal = ActiveModal.ThemeStudio }
    }
    val onOpenIconPackDialog = remember {
        { activeModal = ActiveModal.IconPackSelection }
    }
    val onOpenHardwareButtons = remember {
        { activeModal = ActiveModal.HardwareButtons() }
    }
    val onOpenHiddenApps = remember {
        { activeModal = ActiveModal.HiddenApps }
    }
    val onCreateFolderClick = remember {
        { activeModal = ActiveModal.CreateFolder() }
    }
    val onPickWallpaperClick: () -> Unit = remember(wallpaperPicker) {
        {
            activeModal = null
            try {
                wallpaperPicker.launch(arrayOf("image/*"))
            } catch (e: Exception) {
                android.util.Log.e("MainLauncher", "Failed to launch wallpaper picker", e)
            }
        }
    }
    val onResetWallpaperClick = remember(launcherVm) {
        {
            activeModal = null
            launcherVm.resetWallpaper()
        }
    }
    val onSettingsRestored = remember(context, preferences, launcherVm) {
        {
            currentTheme = loadSavedTheme(preferences)
            launcherVm.reloadAllFromPreferences()
        }
    }
    val onIconPackSelected: (String?) -> Unit = remember(launcherVm) {
        { _ ->
            activeModal = null
            launcherVm.onIconPackChanged()
        }
    }
    val onApplyThemeStudio: (LauncherTheme) -> Unit = remember(context, preferences) {
        { newTheme ->
            currentTheme = newTheme
            saveTheme(preferences, newTheme)
            PreferencesManager.setThemePreset(context, newTheme.name)
            activeModal = null
        }
    }
    val onDismissKaomojiWheel = remember {
        {
            activeModal = null
            poundKeyState = 0
        }
    }
    val onDismissIconPicker = remember(launcherVm) {
        {
            activeModal = null
            launcherVm.clearKawaiiIconNames()
        }
    }
    val onUnhideApp: (LauncherApp) -> Unit = remember(launcherVm) {
        { app ->
            launcherVm.unhideApp(app.packageName)
        }
    }
    val onCreateFolder: (String, List<String>) -> Unit = remember(launcherVm) {
        { folderName, packages ->
            launcherVm.createFolder(folderName, packages)

            val newCount = launcherVm.favoriteItems.size
            selectedFavoriteIndex = (newCount - 1).coerceAtLeast(0)
            savedFavoriteIndexOnHome = selectedFavoriteIndex
            homeFocusArea = 0
            drawerOpen = false
            activeModal = null
        }
    }
    val installedAppsForButtons = remember(apps) {
        apps.map { AppInfo(it.packageName, it.name) }
    }

    CompositionLocalProvider(LocalLauncherTheme provides currentTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentTheme.bgColor)
                .focusable()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    keyHandler.handleKeyEvent(event, getCurrentKeypadState())
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (isHomeScreenActive) {
                            activeModal = ActiveModal.SettingsHub
                        }
                    }
                )
        ) {
            // Unified modal back handler
            BackHandler(enabled = activeModal != null) {
                when (val modal = activeModal) {
                    is ActiveModal.FolderView -> {
                        val itemIdx = favoriteItems.indexOfFirst {
                            it is FavoriteSlotItem.Folder && it.folder.id == modal.folderId
                        }
                        if (itemIdx != -1) {
                            selectedFavoriteIndex = itemIdx
                            savedFavoriteIndexOnHome = itemIdx
                            homeFocusArea = 0
                        }
                        activeModal = null
                    }
                    is ActiveModal.FolderAction -> {
                        val itemIdx = favoriteItems.indexOfFirst {
                            it is FavoriteSlotItem.Folder && it.folder.id == modal.folder.id
                        }
                        if (itemIdx != -1) {
                            selectedFavoriteIndex = itemIdx
                            savedFavoriteIndexOnHome = itemIdx
                            homeFocusArea = 0
                        }
                        activeModal = null
                    }
                    is ActiveModal.IconPicker -> {
                        launcherVm.clearKawaiiIconNames()
                        if (modal.targetFolder != null) {
                            activeModal = ActiveModal.FolderAction(modal.targetFolder)
                        } else if (modal.targetApp != null) {
                            activeModal = ActiveModal.AppAction(modal.targetApp)
                        } else {
                            activeModal = null
                        }
                    }
                    is ActiveModal.IncomingBundlePreview -> {
                        try {
                            modal.preview.wallpaperPreview?.recycle()
                        } catch (_: Exception) {}
                        activeModal = null
                        onConsumeIncomingBundleUri()
                    }
                    is ActiveModal.KaomojiWheel -> {
                        poundKeyState = 0
                        activeModal = null
                    }
                    else -> {
                        activeModal = null
                    }
                }
            }

            // Return to Home page from side pages
            BackHandler(enabled = isSidePageActive || (!drawerOpen && !anyModalOpen && (pagerState.currentPage != 1 || pagerState.targetPage != 1 || (pendingTargetPage != null && pendingTargetPage != 1)))) {
                pendingTargetPage = 1
                scope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }

            if (wallpaperImageBitmap != null) {
                Image(
                    bitmap = wallpaperImageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(currentTheme.bgColor)
                )
            }

            if (isWallpaperOverlayEnabled) {
                WallpaperOverlay()
            }

            val keyboardController = LocalSoftwareKeyboardController.current
            LaunchedEffect(pagerState.currentPage) {
                keyboardController?.hide()
            }

            val onHomeHeaderClick = remember {
                {
                    homeFocusArea = 1
                    activeModal = ActiveModal.SettingsHub
                }
            }
            val onHomeHeaderLongClick = remember {
                {
                    homeFocusArea = 1
                    activeModal = ActiveModal.SettingsHub
                }
            }
            val onHomeAppClick: (LauncherApp) -> Unit = remember(favoriteItems) {
                { app ->
                    launcherVm.recordAppLaunch(app.packageName)
                    val appIdx = favoriteItems.indexOfFirst {
                        it is FavoriteSlotItem.App && it.app.packageName == app.packageName
                    }
                    if (appIdx != -1) {
                        homeFocusArea = 0
                        selectedFavoriteIndex = appIdx
                        savedFavoriteIndexOnHome = appIdx
                    }
                }
            }
            val onHomeAppLongClick: (LauncherApp) -> Unit = remember(favoriteItems) {
                { app ->
                    val appIdx = favoriteItems.indexOfFirst {
                        it is FavoriteSlotItem.App && it.app.packageName == app.packageName
                    }
                    if (appIdx != -1) {
                        homeFocusArea = 0
                        selectedFavoriteIndex = appIdx
                        savedFavoriteIndexOnHome = appIdx
                    }
                    activeModal = ActiveModal.AppAction(app)
                }
            }
            val onHomeFolderClick: (FavoriteFolder) -> Unit = remember(favoriteItems) {
                { folder ->
                    val folderIdx = favoriteItems.indexOfFirst {
                        it is FavoriteSlotItem.Folder && it.folder.id == folder.id
                    }
                    if (folderIdx != -1) {
                        homeFocusArea = 0
                        selectedFavoriteIndex = folderIdx
                        savedFavoriteIndexOnHome = folderIdx
                    }
                    activeModal = ActiveModal.FolderView(
                        folderId = folder.id,
                        startRename = false,
                        startPicker = false
                    )
                }
            }
            val onHomeFolderLongClick: (FavoriteFolder) -> Unit = remember(favoriteItems) {
                { folder ->
                    val folderIdx = favoriteItems.indexOfFirst {
                        it is FavoriteSlotItem.Folder && it.folder.id == folder.id
                    }
                    if (folderIdx != -1) {
                        homeFocusArea = 0
                        selectedFavoriteIndex = folderIdx
                        savedFavoriteIndexOnHome = folderIdx
                    }
                    activeModal = ActiveModal.FolderAction(folder)
                }
            }
            val onHomeOpenDrawer = remember {
                {
                    selectedDrawerIndex = 0
                    drawerOpen = true
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .pointerInput(drawerOpen, pagerState.currentPage, isHomeScreenActive) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (!drawerOpen && isHomeScreenActive && dragAmount < -32) {
                                selectedDrawerIndex = 0
                                drawerOpen = true
                            }
                        }
                    },
                userScrollEnabled = !drawerOpen && !anyModalOpen
            ) { page ->
                when (page) {
                    0 -> {
                        val isWidgetTarget = (pendingTargetPage ?: pagerState.targetPage) == 0
                        val isWidgetPageActive = !drawerOpen &&
                            !anyModalOpen &&
                            isWidgetTarget &&
                            pagerState.currentPage == 0 &&
                            pagerState.settledPage == 0 &&
                            !pagerState.isScrollInProgress

                        if (!drawerOpen && (pagerState.currentPage == 0 || pagerState.targetPage == 0 || pendingTargetPage == 0)) {
                            WidgetPage(
                                context = context,
                                cardBgColor = cardBgColor,
                                cardTextColor = cardTextColor,
                                appActionColor = appActionColor,
                                cardRoundness = cardRoundness,
                                isActivePage = isWidgetPageActive,
                                onNavigateHome = {
                                    savedFavoriteIndexOnHome = 0
                                    pendingTargetPage = 1
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }

                    1 -> {
                        if (!drawerOpen) {
                            HomeScreen(
                                context = context,
                                favoriteItems = favoriteItems,
                                userName = userName,
                                selectedFavoriteIndex = selectedFavoriteIndex,
                                folderIconRefreshKey = launcherVm.folderIconRefreshKey,
                                homeFocusArea = homeFocusArea,
                                isHomeScreenActive = isHomeScreenActive,
                                mascotBoopRequest = mascotBoopRequest,
                                onHeaderClick = onHomeHeaderClick,
                                onHeaderLongClick = onHomeHeaderLongClick,
                                onAppClick = onHomeAppClick,
                                onAppLongClick = onHomeAppLongClick,
                                onFolderClick = onHomeFolderClick,
                                onFolderLongClick = onHomeFolderLongClick,
                                onOpenDrawer = onHomeOpenDrawer,
                                onNavigateToStats = {
                                    savedFavoriteIndexOnHome = selectedFavoriteIndex
                                    pendingTargetPage = 0
                                    scope.launch { pagerState.animateScrollToPage(0) }
                                },
                                onNavigateToNotifications = {
                                    savedFavoriteIndexOnHome = selectedFavoriteIndex
                                    pendingTargetPage = 2
                                    scope.launch { pagerState.animateScrollToPage(2) }
                                }
                            )
                        }
                    }

                    2 -> {
                        val isNotificationTarget = (pendingTargetPage ?: pagerState.targetPage) == 2
                        val isNotificationPageActive = !drawerOpen &&
                            !anyModalOpen &&
                            isNotificationTarget &&
                            pagerState.currentPage == 2 &&
                            pagerState.settledPage == 2 &&
                            !pagerState.isScrollInProgress

                        if (!drawerOpen && (pagerState.currentPage == 2 || pagerState.targetPage == 2 || pendingTargetPage == 2)) {
                            NotificationsScreen(
                                context = context,
                                cardRoundness = cardRoundness,
                                isActivePage = isNotificationPageActive,
                                onNavigateHome = {
                                    savedFavoriteIndexOnHome = 0
                                    pendingTargetPage = 1
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // Modal dialog dispatcher rendering
            when (val modal = activeModal) {
                is ActiveModal.SettingsHub -> {
                    SettingsHubDialog(
                        onDismiss = dismissActiveModal,
                        onOpenThemeStudio = onOpenThemeStudio,
                        onOpenIconPackDialog = onOpenIconPackDialog,
                        onOpenHardwareButtons = onOpenHardwareButtons,
                        onOpenHiddenApps = onOpenHiddenApps,
                        onCreateFolderClick = onCreateFolderClick,
                        onPickWallpaperClick = onPickWallpaperClick,
                        onResetWallpaperClick = onResetWallpaperClick,
                        hasCustomWallpaper = launcherVm.wallpaperUri != null,
                        onSettingsRestored = onSettingsRestored
                    )
                }

                is ActiveModal.IconPackSelection -> {
                    IconPackSelectionDialog(
                        context = context,
                        onDismiss = dismissActiveModal,
                        onIconPackSelected = onIconPackSelected
                    )
                }

                is ActiveModal.ThemeStudio -> {
                    ThemeStudioDialog(
                        currentTheme = currentTheme,
                        dialogWidth = dialogWidth,
                        onDismiss = dismissActiveModal,
                        onApply = onApplyThemeStudio
                    )
                }

                is ActiveModal.HardwareButtons -> {
                    HardwareButtonsDialog(
                        installedApps = installedAppsForButtons,
                        initialCapturedKeyCode = modal.initialKeyCode,
                        onDismiss = dismissActiveModal
                    )
                }

                is ActiveModal.KaomojiWheel -> {
                    KaomojiWheelDialog(
                        onDismiss = onDismissKaomojiWheel
                    )
                }

                is ActiveModal.AppAction -> {
                    val targetApp = modal.app
                    val onMoveFavoriteLeft = remember(targetApp) {
                        {
                            moveFavoriteSlotItem("app:${targetApp.packageName}", -1)
                            activeModal = null
                        }
                    }
                    val onMoveFavoriteRight = remember(targetApp) {
                        {
                            moveFavoriteSlotItem("app:${targetApp.packageName}", 1)
                            activeModal = null
                        }
                    }
                    val onToggleFavorite = remember(targetApp, launcherVm) {
                        {
                            launcherVm.toggleFavoriteApp(targetApp.packageName)
                            activeModal = null
                        }
                    }
                    val onAppInfo = remember(context, targetApp) {
                        {
                            val pkgName = targetApp.packageName
                            val appInfoIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$pkgName")
                            )
                            context.startActivity(appInfoIntent)
                            activeModal = null
                        }
                    }
                    val onChangeIcon = remember(targetApp) {
                        {
                            activeModal = ActiveModal.IconPicker(targetApp = targetApp)
                        }
                    }
                    val onResetIcon = remember(targetApp, launcherVm) {
                        {
                            launcherVm.resetCustomAppIcon(targetApp.packageName)
                            activeModal = null
                        }
                    }
                    val onHideApp = remember(targetApp, launcherVm) {
                        {
                            launcherVm.hideApp(targetApp.packageName)
                            activeModal = null
                        }
                    }
                    val onCreateFolder = remember(targetApp) {
                        {
                            activeModal = ActiveModal.CreateFolder(initialApp = targetApp)
                        }
                    }

                    AppActionDialog(
                        app = targetApp,
                        favoritePackages = favoritePackages,
                        cardRoundness = cardRoundness,
                        onDismiss = dismissActiveModal,
                        onMoveFavoriteLeft = onMoveFavoriteLeft,
                        onMoveFavoriteRight = onMoveFavoriteRight,
                        onToggleFavorite = onToggleFavorite,
                        onAppInfo = onAppInfo,
                        onChangeIcon = onChangeIcon,
                        onResetIcon = onResetIcon,
                        onHideApp = onHideApp,
                        onCreateFolder = onCreateFolder,
                        onOpenLauncherOptions = onOpenLauncherOptions
                    )
                }

                is ActiveModal.FolderAction -> {
                    val targetFolder = favoriteFolders.firstOrNull {
                        it.id == modal.folder.id
                    } ?: modal.folder
                    val folderItemIndex = favoriteItems.indexOfFirst {
                        it is FavoriteSlotItem.Folder && it.folder.id == targetFolder.id
                    }

                    val onMoveFolderLeft = remember(targetFolder.id, folderItemIndex) {
                        {
                            if (folderItemIndex > 0) {
                                moveFavoriteSlotItem("folder:${targetFolder.id}", -1)
                            }
                            activeModal = null
                        }
                    }
                    val onMoveFolderRight = remember(targetFolder.id, folderItemIndex, favoriteItems.lastIndex) {
                        {
                            if (folderItemIndex in 0 until favoriteItems.lastIndex) {
                                moveFavoriteSlotItem("folder:${targetFolder.id}", 1)
                            }
                            activeModal = null
                        }
                    }
                    val onOpenFolder = remember(targetFolder.id) {
                        {
                            activeModal = ActiveModal.FolderView(
                                folderId = targetFolder.id,
                                startRename = false,
                                startPicker = false
                            )
                        }
                    }
                    val onRenameFolder = remember(targetFolder.id) {
                        {
                            activeModal = ActiveModal.FolderView(
                                folderId = targetFolder.id,
                                startRename = true,
                                startPicker = false
                            )
                        }
                    }
                    val onEditFolderApps = remember(targetFolder.id) {
                        {
                            activeModal = ActiveModal.FolderView(
                                folderId = targetFolder.id,
                                startRename = false,
                                startPicker = true
                            )
                        }
                    }
                    val onChangeIconFolder = remember(targetFolder) {
                        {
                            activeModal = ActiveModal.IconPicker(targetFolder = targetFolder)
                        }
                    }
                    val onResetIconFolder = remember(targetFolder.id, launcherVm) {
                        {
                            launcherVm.resetCustomFolderIcon(targetFolder.id)
                            activeModal = null
                        }
                    }
                    val onDeleteFolder = remember(targetFolder.id, launcherVm) {
                        {
                            val deletedId = targetFolder.id
                            launcherVm.deleteFolder(deletedId)

                            val newCount = launcherVm.favoriteItems.size
                            selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
                                0,
                                (newCount - 1).coerceAtLeast(0)
                            )
                            savedFavoriteIndexOnHome = selectedFavoriteIndex
                            homeFocusArea = if (newCount > 0) 0 else 1
                            activeModal = null
                        }
                    }
                    val onDismissFolderAction = remember(targetFolder.id, favoriteItems) {
                        {
                            val idx = favoriteItems.indexOfFirst {
                                it is FavoriteSlotItem.Folder && it.folder.id == targetFolder.id
                            }
                            if (idx != -1) {
                                selectedFavoriteIndex = idx
                                savedFavoriteIndexOnHome = idx
                                homeFocusArea = 0
                            }
                            activeModal = null
                        }
                    }

                    FolderActionDialog(
                        folder = targetFolder,
                        canMoveLeft = folderItemIndex > 0,
                        canMoveRight = folderItemIndex in 0 until favoriteItems.lastIndex,
                        onMoveLeft = onMoveFolderLeft,
                        onMoveRight = onMoveFolderRight,
                        onOpenFolder = onOpenFolder,
                        onRenameFolder = onRenameFolder,
                        onEditFolderApps = onEditFolderApps,
                        onChangeIcon = onChangeIconFolder,
                        onResetIcon = onResetIconFolder,
                        onDeleteFolder = onDeleteFolder,
                        onOpenLauncherOptions = onOpenLauncherOptions,
                        onDismiss = onDismissFolderAction
                    )
                }

                is ActiveModal.IconPicker -> {
                    val onIconSelected = remember(modal, launcherVm) {
                        { selectedIconName: String ->
                            val editingApp = modal.targetApp
                            val editingFolder = modal.targetFolder

                            when {
                                editingApp != null -> {
                                    launcherVm.setCustomAppIcon(editingApp.packageName, selectedIconName)
                                }
                                editingFolder != null -> {
                                    launcherVm.setCustomFolderIcon(editingFolder.id, selectedIconName)
                                }
                            }
                            activeModal = null
                        }
                    }

                    KawaiiIconPicker(
                        context = context,
                        iconNames = kawaiiIconNames,
                        onIconSelected = onIconSelected,
                        onDismiss = onDismissIconPicker
                    )
                }

                is ActiveModal.HiddenApps -> {
                    HiddenAppsDialog(
                        hiddenApps = hiddenApps,
                        onUnhide = onUnhideApp,
                        onDismiss = dismissActiveModal
                    )
                }

                is ActiveModal.FolderView -> {
                    val openedFolder = favoriteFolders.firstOrNull {
                        it.id == modal.folderId
                    }

                    if (openedFolder != null) {
                        val onLaunchApp: (LauncherApp) -> Unit = remember(context, launcherVm) {
                            { app ->
                                launcherVm.recordAppLaunch(app.packageName)
                                context.packageManager.getLaunchIntentForPackage(
                                    app.packageName
                                )?.let(context::startActivity)
                            }
                        }
                        val onRemoveApp: (String) -> Unit = remember(openedFolder.id, launcherVm) {
                            { packageName ->
                                launcherVm.removeAppFromFolder(openedFolder.id, packageName)
                            }
                        }
                        val onUpdateApps: (List<String>) -> Unit = remember(openedFolder.id, launcherVm) {
                            { updatedPackages ->
                                launcherVm.updateFolderApps(openedFolder.id, updatedPackages)
                            }
                        }
                        val onRename: (String) -> Unit = remember(openedFolder.id, launcherVm) {
                            { newName ->
                                launcherVm.renameFolder(openedFolder.id, newName)
                            }
                        }
                        val onDeleteFolder: () -> Unit = remember(openedFolder.id, launcherVm) {
                            {
                                val deletedId = openedFolder.id
                                launcherVm.deleteFolder(deletedId)

                                val newCount = launcherVm.favoriteItems.size
                                selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
                                    0,
                                    (newCount - 1).coerceAtLeast(0)
                                )
                                savedFavoriteIndexOnHome = selectedFavoriteIndex
                                homeFocusArea = if (newCount > 0) 0 else 1
                                activeModal = null
                            }
                        }
                        val onDismissFolder: () -> Unit = remember(openedFolder.id, favoriteItems) {
                            {
                                val itemIdx = favoriteItems.indexOfFirst {
                                    it is FavoriteSlotItem.Folder && it.folder.id == modal.folderId
                                }
                                if (itemIdx != -1) {
                                    selectedFavoriteIndex = itemIdx
                                    savedFavoriteIndexOnHome = itemIdx
                                    homeFocusArea = 0
                                }
                                activeModal = null
                            }
                        }

                        FavoriteFolderDialog(
                            folder = openedFolder,
                            apps = apps,
                            availableApps = visibleApps,
                            existingFolders = favoriteFolders,
                            initialEditingName = modal.startRename,
                            initialPickingApps = modal.startPicker,
                            cardRoundness = cardRoundness,
                            onLaunchApp = onLaunchApp,
                            onRemoveApp = onRemoveApp,
                            onUpdateApps = onUpdateApps,
                            onRename = onRename,
                            onDeleteFolder = onDeleteFolder,
                            onDismiss = onDismissFolder
                        )
                    } else {
                        activeModal = null
                    }
                }

                is ActiveModal.CreateFolder -> {
                    CreateFavoriteFolderDialog(
                        initialApp = modal.initialApp,
                        availableApps = visibleApps,
                        existingFolders = favoriteFolders,
                        onDismiss = dismissActiveModal,
                        onCreate = onCreateFolder
                    )
                }

                is ActiveModal.IncomingBundlePreview -> {
                    val onApplyBundle: () -> Unit = remember(modal, context, preferences, launcherVm) {
                        {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(modal.uri)?.use { stream ->
                                        ThemeBundleManager.applyBundle(context, stream)
                                    }
                                    withContext(Dispatchers.Main) {
                                        currentTheme = loadSavedTheme(preferences)
                                        launcherVm.reloadAllFromPreferences()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainLauncher", "Failed to apply bundle", e)
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        try {
                                            modal.preview.wallpaperPreview?.recycle()
                                        } catch (_: Exception) {}
                                        if (activeModal == modal) {
                                            activeModal = null
                                        }
                                        onConsumeIncomingBundleUri()
                                    }
                                }
                            }
                        }
                    }
                    val onDismissBundle = remember(modal) {
                        {
                            try {
                                modal.preview.wallpaperPreview?.recycle()
                            } catch (_: Exception) {}
                            activeModal = null
                            onConsumeIncomingBundleUri()
                        }
                    }

                    PresetPreviewDialog(
                        previewData = modal.preview,
                        onApply = onApplyBundle,
                        onDismiss = onDismissBundle
                    )
                }

                null -> {
                    // No modal open
                }
            }

            // app drawer view overlay
            if (drawerOpen) {
                AppDrawerScreen(
                    visibleApps = visibleApps,
                    selectedDrawerIndex = selectedDrawerIndex,
                    currentDrawerLetter = currentDrawerLetter,
                    letterStarts = letterStarts,
                    isRailFocused = drawerRailFocused,
                    hoveredRailLetter = drawerRailHoveredLetter,
                    sortMode = launcherVm.appDrawerSortMode,
                    listState = listState,
                    onOptionsClick = {
                        visibleApps.getOrNull(selectedDrawerIndex)?.let { app ->
                            activeModal = ActiveModal.AppAction(app)
                        }
                    },
                    onCycleSortClick = {
                        val nextMode = launcherVm.cycleAppDrawerSortMode()
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                        Toast.makeText(context, "♡ Sorted: ${nextMode.toastName}", Toast.LENGTH_SHORT).show()
                    },
                    onAppClick = { app ->
                        selectedDrawerIndex =
                            visibleApps.indexOfFirst { it.componentKey == app.componentKey }
                        launcherVm.recordAppLaunch(app.packageName)
                        val launchIntent =
                            context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    },
                    onAppLongClick = { app ->
                        selectedDrawerIndex =
                            visibleApps.indexOfFirst { it.componentKey == app.componentKey }
                        activeModal = ActiveModal.AppAction(app)
                    },
                    onBackToHome = {
                        drawerRailFocused = false
                        drawerOpen = false
                    },
                    isModalOpen = anyModalOpen,
                    onLetterTap = { letter ->
                        drawerRailHoveredLetter = letter
                        val target = letterStarts[letter]
                            ?: letterStarts.entries.firstOrNull { it.key > letter }?.value
                            ?: (visibleApps.size - 1)
                        if (target >= 0 && visibleApps.isNotEmpty()) {
                            selectedDrawerIndex = target
                            scope.launch { listState.scrollToItem(target) }
                        }
                    }
                )
            }
        }
    }
}
