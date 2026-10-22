package com.crinklecodone.mochibun.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.crinklecodone.mochibun.data.repository.IconPackManager
import com.crinklecodone.mochibun.data.repository.ExternalThemeManager
import com.crinklecodone.mochibun.data.model.AppInfo
import com.crinklecodone.mochibun.data.model.FavoriteFolder
import com.crinklecodone.mochibun.data.model.FavoriteSlotItem
import com.crinklecodone.mochibun.data.model.LauncherApp
import com.crinklecodone.mochibun.core.util.loadWallpaperBitmap
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.components.WidgetPage
import com.crinklecodone.mochibun.ui.dialogs.AppActionDialog
import com.crinklecodone.mochibun.ui.dialogs.CreateFavoriteFolderDialog
import com.crinklecodone.mochibun.ui.dialogs.EditUserNameDialog
import com.crinklecodone.mochibun.ui.dialogs.FavoriteFolderDialog
import com.crinklecodone.mochibun.ui.dialogs.FolderActionDialog
import com.crinklecodone.mochibun.ui.dialogs.HardwareButtonsDialog
import com.crinklecodone.mochibun.ui.dialogs.HiddenAppsDialog
import com.crinklecodone.mochibun.ui.dialogs.IconPackSelectionDialog
import com.crinklecodone.mochibun.ui.dialogs.KawaiiIconPicker
import com.crinklecodone.mochibun.ui.dialogs.SpeedDialDialog
import com.crinklecodone.mochibun.ui.dialogs.dialogWidth
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.isDpadOrActionKey
import com.crinklecodone.mochibun.ui.screens.AppDrawerScreen
import com.crinklecodone.mochibun.ui.screens.SettingsHubDialog
import com.crinklecodone.mochibun.data.repository.ThemeBundleManager
import com.crinklecodone.mochibun.data.repository.BundlePreviewData
import com.crinklecodone.mochibun.ui.dialogs.PresetPreviewDialog
import com.crinklecodone.mochibun.ui.screens.HomeScreen
import com.crinklecodone.mochibun.ui.screens.NotificationsScreen
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.screens.ThemeStudioDialog
import com.crinklecodone.mochibun.ui.screens.firstLetterOf
import com.crinklecodone.mochibun.ui.theme.loadSavedTheme
import com.crinklecodone.mochibun.ui.theme.saveTheme
import com.crinklecodone.mochibun.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.collections.get
import kotlin.text.get

// main launcher composable managing focus, gestures, state, and page navigation
@Composable
fun MainLauncher(
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel? = null,
    homePressCount: Int = 0,
    appListRefreshCount: Int = 0,
    lidOpenCount: Int = 0,
    lidClosedCount: Int = 0,
    requestedPage: Int? = null,
    onPageConsumed: () -> Unit = {},
    incomingBundleUri: Uri? = null,
    onConsumeIncomingBundleUri: () -> Unit = {}
) {
    val context = LocalContext.current
    val launcherVm: LauncherViewModel = viewModel ?: remember {
        LauncherViewModel(context.applicationContext as android.app.Application)
    }

    var activeIncomingPreview by remember { mutableStateOf<BundlePreviewData?>(null) }
    var pendingIncomingUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(incomingBundleUri) {
        val uri = incomingBundleUri
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val previewRes = ThemeBundleManager.readBundleManifest(stream)
                    if (previewRes.isSuccess) {
                        activeIncomingPreview = previewRes.getOrThrow()
                        pendingIncomingUri = uri
                    } else {
                        android.util.Log.e("MainLauncher", "Invalid theme bundle", previewRes.exceptionOrNull())
                        android.widget.Toast.makeText(context, "Invalid .mochibun theme bundle", android.widget.Toast.LENGTH_SHORT).show()
                        onConsumeIncomingBundleUri()
                    }
                } ?: run {
                    android.util.Log.e("MainLauncher", "Could not open stream for uri: $uri")
                    android.widget.Toast.makeText(context, "Could not open theme bundle", android.widget.Toast.LENGTH_SHORT).show()
                    onConsumeIncomingBundleUri()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainLauncher", "Failed to inspect theme bundle", e)
                android.widget.Toast.makeText(context, "Failed to read theme bundle: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                onConsumeIncomingBundleUri()
            }
        }
    }

    val preferences = remember { PreferencesManager.getPrefs(context) }

    var currentTheme by remember {
        mutableStateOf(loadSavedTheme(preferences))
    }
    val themeOptions by ExternalThemeManager.themes.collectAsState()
    LaunchedEffect(themeOptions) {
        val savedPreset = preferences.getString("theme_preset", null)
        if (savedPreset != null && currentTheme.name != savedPreset) {
            val matching = ExternalThemeManager.findTheme(savedPreset)
            if (matching != null) {
                currentTheme = matching.theme
            }
        }
    }

    var themeStudioOpen by remember { mutableStateOf(false) }

    val theme = currentTheme
    val cardRoundness = theme.cardRoundnessDp.dp
    val cardBgColor = theme.cardBgColor
    val cardTextColor = theme.cardTextColor
    val appActionColor = theme.appActionColor
    val greetingOutlineColor = theme.greetingOutlineColor
    var iconEditingFolder by remember { mutableStateOf<FavoriteFolder?>(null) }
    var folderIconMenuOpen by remember { mutableStateOf(false) }

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
    var selectedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconEditingApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    var iconPackDialogOpen by remember { mutableStateOf(false) }
    val userName by PreferencesManager.customUserName.collectAsState()
    LaunchedEffect(Unit) {
        PreferencesManager.init(context)
        ExternalThemeManager.init(context)
    }
    val isPixelSprayEnabled by PreferencesManager.isPixelSprayEnabled.collectAsState()
    var editUserNameDialogOpen by remember { mutableStateOf(false) }
    var launcherMenuOpen by remember { mutableStateOf(false) }
    var hiddenManagerOpen by remember { mutableStateOf(false) }
    var hardwareButtonsDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(requestedPage) {
        requestedPage?.let { target ->
            val clampedTarget = target.coerceIn(0, 2)
            pendingTargetPage = clampedTarget
            pagerState.animateScrollToPage(clampedTarget)
            onPageConsumed()
        }
    }

    var folderToCreateFrom by remember { mutableStateOf<LauncherApp?>(null) }
    var createFolderDialogOpen by remember { mutableStateOf(false) }
    var openedFolderId by remember { mutableStateOf<String?>(null) }
    var lastOpenedFolderId by remember { mutableStateOf<String?>(null) }
    var folderOpenStartRename by remember { mutableStateOf(false) }
    var folderOpenStartPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(appListRefreshCount) {
        launcherVm.loadApps()
    }

    LaunchedEffect(iconPickerOpen) {
        if (iconPickerOpen) {
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
    // Home focus area: 0 = FavoritesCard row, 1 = StatusCard (launcher options / page switcher)
    var homeFocusArea by remember { mutableIntStateOf(0) }

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
    val t9State = remember { T9InputState() }
    val focusRequester = remember { FocusRequester() }
    var speedDialDialogOpen by remember { mutableStateOf(false) }

    val anyModalOpen = selectedApp != null ||
        launcherMenuOpen ||
        iconPickerOpen ||
        iconPackDialogOpen ||
        hiddenManagerOpen ||
        themeStudioOpen ||
        hardwareButtonsDialogOpen ||
        editUserNameDialogOpen ||
        openedFolderId != null ||
        createFolderDialogOpen ||
        folderToCreateFrom != null ||
        folderIconMenuOpen ||
        speedDialDialogOpen ||
        activeIncomingPreview != null

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

    // Clamshell lid closed: cleanly dismiss transient modals, collapse folder/drawer, reset T9 state, and position on Home
    LaunchedEffect(lidClosedCount) {
        if (lidClosedCount > 0) {
            selectedApp = null
            launcherMenuOpen = false
            iconPickerOpen = false
            iconPackDialogOpen = false
            hiddenManagerOpen = false
            themeStudioOpen = false
            hardwareButtonsDialogOpen = false
            editUserNameDialogOpen = false
            openedFolderId = null
            createFolderDialogOpen = false
            folderToCreateFrom = null
            folderIconMenuOpen = false
            speedDialDialogOpen = false
            drawerOpen = false
            centerKeyState = 0
            t9State.t9Key = null
            t9State.t9Time = 0L
            homeFocusArea = if (favoriteItemCount > 0) 0 else 1
            pendingTargetPage = 1
            pagerState.scrollToPage(1)
        }
    }

    // Clamshell lid opened: wake focus recovery for physical D-pad contracts
    LaunchedEffect(lidOpenCount) {
        if (lidOpenCount > 0) {
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(isHomeScreenActive) {
        if (isHomeScreenActive) {
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
            selectedFavoriteIndex = savedFavoriteIndexOnHome.coerceIn(
                0,
                (favoriteItemCount - 1).coerceAtLeast(0)
            )
            if (favoriteItemCount > 0 && homeFocusArea != 1) {
                homeFocusArea = 0
            }
        }
    }

    LaunchedEffect(favoriteItemCount) {
        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
            0,
            (favoriteItemCount - 1).coerceAtLeast(0)
        )
        savedFavoriteIndexOnHome = savedFavoriteIndexOnHome.coerceIn(
            0,
            (favoriteItemCount - 1).coerceAtLeast(0)
        )
        if (favoriteItemCount == 0) {
            homeFocusArea = 1
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
                listState.animateScrollToItem(selectedDrawerIndex)
            }
        }
    }

    val openContextActionForCurrentFocus: () -> Unit = {
        if (drawerOpen) {
            visibleApps.getOrNull(selectedDrawerIndex)?.let { app ->
                selectedApp = app
            }
        } else if (isHomeScreenActive) {
            if (homeFocusArea == 0 && favoriteItemCount > 0) {
                when (val item = favoriteItems.getOrNull(selectedFavoriteIndex)) {
                    is FavoriteSlotItem.App -> {
                        selectedApp = item.app
                    }
                    is FavoriteSlotItem.Folder -> {
                        iconEditingFolder = item.folder
                        folderIconMenuOpen = true
                    }
                    null -> {}
                }
            } else if (homeFocusArea == 2) {
                selectedDrawerIndex = 0
                drawerOpen = true
            } else {
                launcherMenuOpen = true
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
            repeat(6) {
                try {
                    focusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(35)
                }
            }
        }
    }

    // physical home button press brings user back to home screen
    LaunchedEffect(homePressCount) {
        if (homePressCount > 0) {
            drawerOpen = false
            selectedApp = null
            launcherMenuOpen = false
            themeStudioOpen = false
            iconPickerOpen = false
            iconPackDialogOpen = false
            hiddenManagerOpen = false
            editUserNameDialogOpen = false
            openedFolderId = null
            folderToCreateFrom = null
            createFolderDialogOpen = false
            folderIconMenuOpen = false
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
            override fun setDrawerOpen(open: Boolean) { drawerOpen = open }
            override fun setSelectedDrawerIndex(index: Int) { selectedDrawerIndex = index }
            override fun setHomeFocusArea(area: Int) { homeFocusArea = area }
            override fun setSelectedFavoriteIndex(index: Int) { selectedFavoriteIndex = index }
            override fun setSavedFavoriteIndexOnHome(index: Int) { savedFavoriteIndexOnHome = index }
            override fun setPendingTargetPage(page: Int?) { pendingTargetPage = page }
            override fun setCenterKeyState(state: Int) { centerKeyState = state }
            override fun openContextActionForCurrentFocus() = openContextActionForCurrentFocus()
            override fun openFolder(folderId: String) {
                folderOpenStartRename = false
                folderOpenStartPicker = false
                lastOpenedFolderId = folderId
                openedFolderId = folderId
            }
            override fun openLauncherMenu() { launcherMenuOpen = true }
            override fun openSpeedDial(digit: Int) { speedDialDialogOpen = true }
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

    CompositionLocalProvider(LocalLauncherTheme provides currentTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentTheme.bgColor)
                .focusable()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val keypadState = LauncherKeypadState(
                        anyModalOpen = anyModalOpen,
                        drawerOpen = drawerOpen,
                        isHomeScreenActive = isHomeScreenActive,
                        isPageTransitioning = !drawerOpen && (pagerState.isScrollInProgress || (pendingTargetPage != null && pendingTargetPage != pagerState.settledPage)),
                        pendingTargetPage = pendingTargetPage,
                        favoriteItemCount = favoriteItemCount,
                        selectedFavoriteIndex = selectedFavoriteIndex,
                        savedFavoriteIndexOnHome = savedFavoriteIndexOnHome,
                        homeFocusArea = homeFocusArea,
                        centerKeyState = centerKeyState,
                        selectedDrawerIndex = selectedDrawerIndex,
                        visibleApps = visibleApps,
                        favoriteApps = launcherVm.favoriteApps,
                        favoriteFolders = favoriteFolders,
                        favoriteItems = favoriteItems,
                        letterStarts = letterStarts
                    )
                    keyHandler.handleKeyEvent(event, keypadState)
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (isHomeScreenActive) {
                            launcherMenuOpen = true
                        }
                    }
                )
        ) {
            BackHandler(enabled = drawerOpen) {
                drawerOpen = false
            }

            BackHandler(enabled = !drawerOpen && !anyModalOpen && (pagerState.currentPage != 1 || pagerState.targetPage != 1 || (pendingTargetPage != null && pendingTargetPage != 1))) {
                pendingTargetPage = 1
                scope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }

            BackHandler(enabled = openedFolderId != null) {
                lastOpenedFolderId?.let { id ->
                    val itemIdx = favoriteItems.indexOfFirst {
                        it is FavoriteSlotItem.Folder && it.folder.id == id
                    }
                    if (itemIdx != -1) {
                        selectedFavoriteIndex = itemIdx
                        savedFavoriteIndexOnHome = itemIdx
                        homeFocusArea = 0
                    }
                }
                openedFolderId = null
                folderOpenStartRename = false
                folderOpenStartPicker = false
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

            val keyboardController = LocalSoftwareKeyboardController.current
            LaunchedEffect(pagerState.currentPage) {
                keyboardController?.hide()
            }

            val onHomeHeaderClick = remember {
                {
                    homeFocusArea = 1
                    launcherMenuOpen = true
                }
            }
            val onHomeHeaderLongClick = remember {
                {
                    homeFocusArea = 1
                    launcherMenuOpen = true
                }
            }
            val onHomeAppClick: (LauncherApp) -> Unit = remember(favoriteItems) {
                { app ->
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
                    selectedApp = app
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
                    folderOpenStartRename = false
                    folderOpenStartPicker = false
                    lastOpenedFolderId = folder.id
                    openedFolderId = folder.id
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
                    iconEditingFolder = folder
                    folderIconMenuOpen = true
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
                                isPixelSprayEnabled = isPixelSprayEnabled,
                                onHeaderClick = onHomeHeaderClick,
                                onHeaderLongClick = onHomeHeaderLongClick,
                                onAppClick = onHomeAppClick,
                                onAppLongClick = onHomeAppLongClick,
                                onFolderClick = onHomeFolderClick,
                                onFolderLongClick = onHomeFolderLongClick,
                                onOpenDrawer = onHomeOpenDrawer
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
                                cardBgColor = cardBgColor,
                                cardTextColor = cardTextColor,
                                appActionColor = appActionColor,
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

            // Unified Mochibun Settings Hub (replaces fragmented old LauncherMenuDialog)
            if (launcherMenuOpen) {
                SettingsHubDialog(
                    onDismiss = {
                        launcherMenuOpen = false
                    },
                    onOpenThemeStudio = {
                        launcherMenuOpen = false
                        themeStudioOpen = true
                    },
                    onOpenIconPackDialog = {
                        launcherMenuOpen = false
                        iconPackDialogOpen = true
                    },
                    onOpenHardwareButtons = {
                        launcherMenuOpen = false
                        hardwareButtonsDialogOpen = true
                    },
                    onOpenHiddenApps = {
                        launcherMenuOpen = false
                        hiddenManagerOpen = true
                    },
                    onCreateFolderClick = {
                        launcherMenuOpen = false
                        folderToCreateFrom = null
                        createFolderDialogOpen = true
                    },
                    onPickWallpaperClick = {
                        launcherMenuOpen = false
                        try {
                            wallpaperPicker.launch(arrayOf("image/*"))
                        } catch (e: Exception) {
                            android.util.Log.e("MainLauncher", "Failed to launch wallpaper picker", e)
                        }
                    },
                    onResetWallpaperClick = {
                        launcherMenuOpen = false
                        launcherVm.resetWallpaper()
                    },
                    onOpenSpeedDial = {
                        launcherMenuOpen = false
                        speedDialDialogOpen = true
                    },
                    hasCustomWallpaper = launcherVm.wallpaperUri != null,
                    onSettingsRestored = {
                        currentTheme = loadSavedTheme(preferences)
                        launcherVm.reloadAllFromPreferences()
                    }
                )
            }

            // third-party icon pack selection dialog
            if (iconPackDialogOpen) {
                IconPackSelectionDialog(
                    context = context,
                    onDismiss = {
                        iconPackDialogOpen = false
                    },
                    onIconPackSelected = { _ ->
                        iconPackDialogOpen = false
                        launcherVm.onIconPackChanged()
                    }
                )
            }

            if (editUserNameDialogOpen) {
                EditUserNameDialog(
                    currentName = userName,
                    onDismiss = {
                        editUserNameDialogOpen = false
                    },
                    onSave = { newName ->
                        PreferencesManager.setCustomUserName(context, newName)
                        editUserNameDialogOpen = false
                    }
                )
            }

            if (themeStudioOpen) {
                ThemeStudioDialog(
                    currentTheme = currentTheme,
                    dialogWidth = dialogWidth,
                    onDismiss = {
                        themeStudioOpen = false
                    },
                    onApply = { newTheme ->
                        currentTheme = newTheme
                        saveTheme(preferences, newTheme)
                        PreferencesManager.setThemePreset(context, newTheme.name)
                        themeStudioOpen = false
                    }
                )
            }

            if (hardwareButtonsDialogOpen) {
                HardwareButtonsDialog(
                    installedApps = apps.map { AppInfo(it.packageName, it.name) },
                    onDismiss = {
                        hardwareButtonsDialogOpen = false
                    }
                )
            }

            if (speedDialDialogOpen) {
                SpeedDialDialog(
                    apps = visibleApps,
                    onDismiss = {
                        speedDialDialogOpen = false
                    }
                )
            }

            // app options long-press dialog
            if (selectedApp != null) {
                val targetApp = selectedApp!!
                AppActionDialog(
                    app = targetApp,
                    favoritePackages = favoritePackages,
                    cardBgColor = cardBgColor,
                    cardTextColor = cardTextColor,
                    appActionColor = appActionColor,
                    cardRoundness = cardRoundness,
                    onDismiss = {
                        selectedApp = null
                    },
                    onMoveFavoriteLeft = {
                        moveFavoriteSlotItem("app:${targetApp.packageName}", -1)
                        selectedApp = null
                    },
                    onMoveFavoriteRight = {
                        moveFavoriteSlotItem("app:${targetApp.packageName}", 1)
                        selectedApp = null
                    },
                    onToggleFavorite = {
                        launcherVm.toggleFavoriteApp(targetApp.packageName)
                        selectedApp = null
                    },
                    onAppInfo = {
                        val pkgName = targetApp.packageName
                        val appInfoIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$pkgName")
                        )
                        context.startActivity(appInfoIntent)
                        selectedApp = null
                    },
                    onChangeIcon = {
                        iconEditingApp = targetApp
                        iconPickerOpen = true
                        selectedApp = null
                    },
                    onResetIcon = {
                        launcherVm.resetCustomAppIcon(targetApp.packageName)
                        selectedApp = null
                    },
                    onHideApp = {
                        launcherVm.hideApp(targetApp.packageName)
                        selectedApp = null
                    },
                    onCreateFolder = {
                        folderToCreateFrom = targetApp
                        createFolderDialogOpen = true
                        selectedApp = null
                    },
                    onOpenLauncherOptions = {
                        selectedApp = null
                        launcherMenuOpen = true
                    }
                )
            }

            // folder customization & action menu ♡
            if (folderIconMenuOpen && iconEditingFolder != null) {
                val targetFolder = favoriteFolders.firstOrNull {
                    it.id == iconEditingFolder!!.id
                } ?: iconEditingFolder!!
                val folderItemIndex = favoriteItems.indexOfFirst {
                    it is FavoriteSlotItem.Folder && it.folder.id == targetFolder.id
                }

                FolderActionDialog(
                    folder = targetFolder,
                    canMoveLeft = folderItemIndex > 0,
                    canMoveRight = folderItemIndex in 0 until favoriteItems.lastIndex,
                    onMoveLeft = {
                        if (folderItemIndex > 0) {
                            moveFavoriteSlotItem("folder:${targetFolder.id}", -1)
                        }
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                    },
                    onMoveRight = {
                        if (folderItemIndex in 0 until favoriteItems.lastIndex) {
                            moveFavoriteSlotItem("folder:${targetFolder.id}", 1)
                        }
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                    },
                    onOpenFolder = {
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                        folderOpenStartRename = false
                        folderOpenStartPicker = false
                        lastOpenedFolderId = targetFolder.id
                        openedFolderId = targetFolder.id
                    },
                    onRenameFolder = {
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                        folderOpenStartRename = true
                        folderOpenStartPicker = false
                        lastOpenedFolderId = targetFolder.id
                        openedFolderId = targetFolder.id
                    },
                    onEditFolderApps = {
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                        folderOpenStartRename = false
                        folderOpenStartPicker = true
                        lastOpenedFolderId = targetFolder.id
                        openedFolderId = targetFolder.id
                    },
                    onChangeIcon = {
                        folderIconMenuOpen = false
                        iconPickerOpen = true
                    },
                    onResetIcon = {
                        launcherVm.resetCustomFolderIcon(targetFolder.id)
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                    },
                    onDeleteFolder = {
                        val deletedId = targetFolder.id
                        launcherVm.deleteFolder(deletedId)

                        val newCount = launcherVm.favoriteItems.size
                        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
                            0,
                            (newCount - 1).coerceAtLeast(0)
                        )
                        savedFavoriteIndexOnHome = selectedFavoriteIndex
                        homeFocusArea = if (newCount > 0) 0 else 1
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                        if (openedFolderId == deletedId) {
                            openedFolderId = null
                        }
                    },
                    onOpenLauncherOptions = {
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                        launcherMenuOpen = true
                    },
                    onDismiss = {
                        val idx = favoriteItems.indexOfFirst {
                            it is FavoriteSlotItem.Folder && it.folder.id == targetFolder.id
                        }
                        if (idx != -1) {
                            selectedFavoriteIndex = idx
                            savedFavoriteIndexOnHome = idx
                            homeFocusArea = 0
                        }
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                    }
                )
            }

            // custom KawaiiIconPack icon picker dialog
            if (iconPickerOpen &&
                (iconEditingApp != null || iconEditingFolder != null)
            ) {
                KawaiiIconPicker(
                    context = context,
                    iconNames = kawaiiIconNames,
                    onIconSelected = { selectedIconName ->
                        val editingApp = iconEditingApp
                        val editingFolder = iconEditingFolder

                        when {
                            editingApp != null -> {
                                launcherVm.setCustomAppIcon(editingApp.packageName, selectedIconName)
                            }

                            editingFolder != null -> {
                                launcherVm.setCustomFolderIcon(editingFolder.id, selectedIconName)
                            }
                        }

                        iconPickerOpen = false
                        iconEditingApp = null
                        iconEditingFolder = null
                        folderIconMenuOpen = false
                    },
                    onDismiss = {
                        iconPickerOpen = false
                        iconEditingApp = null
                        iconEditingFolder = null
                        launcherVm.clearKawaiiIconNames()
                    }
                )
            }

            // hidden apps manager dialog
            if (hiddenManagerOpen) {
                HiddenAppsDialog(
                    hiddenApps = hiddenApps,
                    onUnhide = { app ->
                        launcherVm.unhideApp(app.packageName)
                    },
                    onDismiss = { hiddenManagerOpen = false }
                )
            }

            val openedFolder = favoriteFolders.firstOrNull {
                it.id == openedFolderId
            }

            if (openedFolder != null) {
                FavoriteFolderDialog(
                    folder = openedFolder,
                    apps = apps,
                    availableApps = visibleApps,
                    existingFolders = favoriteFolders,
                    initialEditingName = folderOpenStartRename,
                    initialPickingApps = folderOpenStartPicker,
                    cardBgColor = cardBgColor,
                    cardTextColor = cardTextColor,
                    appActionColor = appActionColor,
                    cardRoundness = cardRoundness,
                    onLaunchApp = { app ->
                        context.packageManager.getLaunchIntentForPackage(
                            app.packageName
                        )?.let(context::startActivity)
                    },
                    onRemoveApp = { packageName ->
                        launcherVm.removeAppFromFolder(openedFolder.id, packageName)
                    },
                    onUpdateApps = { updatedPackages ->
                        launcherVm.updateFolderApps(openedFolder.id, updatedPackages)
                    },
                    onRename = { newName ->
                        launcherVm.renameFolder(openedFolder.id, newName)
                    },
                    onDeleteFolder = {
                        val deletedId = openedFolder.id
                        launcherVm.deleteFolder(deletedId)

                        val newCount = launcherVm.favoriteItems.size
                        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
                            0,
                            (newCount - 1).coerceAtLeast(0)
                        )
                        savedFavoriteIndexOnHome = selectedFavoriteIndex
                        homeFocusArea = if (newCount > 0) 0 else 1
                        openedFolderId = null
                        lastOpenedFolderId = null
                        folderOpenStartRename = false
                        folderOpenStartPicker = false
                    },
                    onOpenFolderOptions = {
                        iconEditingFolder = openedFolder
                        folderIconMenuOpen = true
                        openedFolderId = null
                        folderOpenStartRename = false
                        folderOpenStartPicker = false
                    },
                    onDismiss = {
                        lastOpenedFolderId?.let { id ->
                            val itemIdx = favoriteItems.indexOfFirst {
                                it is FavoriteSlotItem.Folder && it.folder.id == id
                            }
                            if (itemIdx != -1) {
                                selectedFavoriteIndex = itemIdx
                                savedFavoriteIndexOnHome = itemIdx
                                homeFocusArea = 0
                            }
                        }
                        openedFolderId = null
                        folderOpenStartRename = false
                        folderOpenStartPicker = false
                    }
                )
            }

            if (createFolderDialogOpen || folderToCreateFrom != null) {
                CreateFavoriteFolderDialog(
                    initialApp = folderToCreateFrom,
                    availableApps = visibleApps,
                    existingFolders = favoriteFolders,
                    cardBgColor = cardBgColor,
                    cardTextColor = cardTextColor,
                    appActionColor = appActionColor,
                    cardRoundness = cardRoundness,
                    onDismiss = {
                        folderToCreateFrom = null
                        createFolderDialogOpen = false
                    },
                    onCreate = { folderName, packages ->
                        launcherVm.createFolder(folderName, packages)

                        val newCount = launcherVm.favoriteItems.size
                        selectedFavoriteIndex = (newCount - 1).coerceAtLeast(0)
                        savedFavoriteIndexOnHome = selectedFavoriteIndex
                        homeFocusArea = 0
                        drawerOpen = false
                        folderToCreateFrom = null
                        createFolderDialogOpen = false
                    }
                )
            }

            activeIncomingPreview?.let { preview ->
                PresetPreviewDialog(
                    previewData = preview,
                    onApply = {
                        val uri = pendingIncomingUri
                        if (uri != null) {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    ThemeBundleManager.applyBundle(context, stream)
                                }
                                currentTheme = loadSavedTheme(preferences)
                                launcherVm.reloadAllFromPreferences()
                            } catch (e: Exception) {
                                android.util.Log.e("MainLauncher", "Failed to apply bundle", e)
                            }
                        }
                        try {
                            preview.wallpaperPreview?.recycle()
                        } catch (_: Exception) {}
                        activeIncomingPreview = null
                        pendingIncomingUri = null
                        onConsumeIncomingBundleUri()
                    },
                    onDismiss = {
                        try {
                            preview.wallpaperPreview?.recycle()
                        } catch (_: Exception) {}
                        activeIncomingPreview = null
                        pendingIncomingUri = null
                        onConsumeIncomingBundleUri()
                    }
                )
            }

            // app drawer view overlay
            if (drawerOpen) {
                AppDrawerScreen(
                    context = context,
                    visibleApps = visibleApps,
                    selectedDrawerIndex = selectedDrawerIndex,
                    currentDrawerLetter = currentDrawerLetter,
                    letterStarts = letterStarts,
                    listState = listState,
                    onAppClick = { app ->
                        selectedDrawerIndex =
                            visibleApps.indexOfFirst { it.componentKey == app.componentKey }
                        val launchIntent =
                            context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    },
                    onAppLongClick = { app ->
                        selectedDrawerIndex =
                            visibleApps.indexOfFirst { it.componentKey == app.componentKey }
                        selectedApp = app
                    },
                    onBackToHome = {
                        drawerOpen = false
                    },
                    onLetterTap = { letter ->
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
