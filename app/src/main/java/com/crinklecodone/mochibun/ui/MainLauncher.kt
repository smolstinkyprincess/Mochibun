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
import com.crinklecodone.mochibun.ui.dialogs.dialogWidth
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.isDpadOrActionKey
import com.crinklecodone.mochibun.ui.screens.AppDrawerScreen
import com.crinklecodone.mochibun.ui.screens.SettingsHubDialog
import com.crinklecodone.mochibun.ui.screens.HomeScreen
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

private class HomeTransitionTracker {
    var wasActive: Boolean = false
    var settledTimestamp: Long = 0L
}

// main launcher composable managing focus, gestures, state, and page navigation
@Composable
fun MainLauncher(
    name: String,
    modifier: Modifier = Modifier,
    homePressCount: Int = 0,
    appListRefreshCount: Int = 0,
    requestedPage: Int? = null,
    onPageConsumed: () -> Unit = {}
) {
    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            "launcher_preferences",
            Context.MODE_PRIVATE
        )
    }

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

    val intent = remember {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
    }

    // wallpaper selection and persistence state
    var wallpaperUri by remember {
        mutableStateOf(
            preferences.getString("wallpaper_uri", null)?.let {
                Uri.parse(it)
            }
        )
    }
    var wallpaperBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }
    val wallpaperImageBitmap = remember(wallpaperBitmap) {
        wallpaperBitmap?.asImageBitmap()
    }
    val wallpaperPicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
            wallpaperUri = uri

            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                preferences.edit()
                    .putString("wallpaper_uri", uri.toString())
                    .apply()
            }
        }

    // memory-efficient sampled wallpaper loader
    LaunchedEffect(wallpaperUri) {
        val uri = wallpaperUri ?: run {
            wallpaperBitmap = null
            return@LaunchedEffect
        }

        wallpaperBitmap = withContext(Dispatchers.IO) {
            loadWallpaperBitmap(context, uri)
        }
    }

    var hiddenPackages by remember {
        mutableStateOf(
            preferences.getStringSet("hidden_apps", emptySet())?.toSet()
                ?: emptySet()
        )
    }

    var favoritePackages by remember {
        mutableStateOf(
            preferences
                .getString("favorite_packages_ordered", "")
                ?.split("|")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        )
    }

    var drawerOpen by remember { mutableStateOf(false) }
    // Active pages: 0 = Widgets, 1 = Home
    val pagerState = rememberPagerState(initialPage = 1) { 2 }
    var pendingTargetPage by remember { mutableStateOf<Int?>(null) }
    var selectedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconEditingApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    var iconPackDialogOpen by remember { mutableStateOf(false) }
    var kawaiiIconNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var iconRefreshKey by remember { mutableIntStateOf(0) }
    var folderIconRefreshKey by remember { mutableIntStateOf(0) }
    var userName by remember {
        mutableStateOf(preferences.getString("custom_user_name", "") ?: "")
    }
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
            val clampedTarget = target.coerceIn(0, 1)
            pendingTargetPage = clampedTarget
            pagerState.animateScrollToPage(clampedTarget)
            onPageConsumed()
        }
    }

    var favoriteFolders by remember { mutableStateOf(PreferencesManager.loadFavoriteFolders(context)) }
    var folderToCreateFrom by remember { mutableStateOf<LauncherApp?>(null) }
    var createFolderDialogOpen by remember { mutableStateOf(false) }
    var openedFolderId by remember { mutableStateOf<String?>(null) }
    var lastOpenedFolderId by remember { mutableStateOf<String?>(null) }
    var folderOpenStartRename by remember { mutableStateOf(false) }
    var folderOpenStartPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }
    var earlyFavoriteApps by remember { mutableStateOf<List<LauncherApp>>(emptyList()) }

    // sequential two-phase loader: loads pinned favorites first (~20ms) for instant home screen, then full app list reusing cached icons
    LaunchedEffect(appListRefreshCount, iconRefreshKey) {
        if (apps.isEmpty() && favoritePackages.isNotEmpty()) {
            val fastFavs = withContext(Dispatchers.IO) {
                IconPackManager.getActiveParsedPack(context)

                favoritePackages
                    .filter { it !in hiddenPackages }
                    .mapNotNull { pkgName ->
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
                            ?: return@mapNotNull null
                        val resolveInfo = context.packageManager.resolveActivity(launchIntent, 0)
                            ?: return@mapNotNull null

                        IconPackManager.getOrLoadLauncherApp(
                            context = context,
                            preferences = preferences,
                            resolveInfo = resolveInfo
                        )
                    }
            }
            earlyFavoriteApps = fastFavs
        }

        apps = withContext(Dispatchers.IO) {
            IconPackManager.getActiveParsedPack(context)

            context.packageManager
                .queryIntentActivities(intent, 0)
                .map { app ->
                    IconPackManager.getOrLoadLauncherApp(
                        context = context,
                        preferences = preferences,
                        resolveInfo = app
                    )
                }
                .sortedBy { it.lowercaseName }
        }
    }

    // async load icon names list for icon picker
    LaunchedEffect(iconPickerOpen) {
        if (iconPickerOpen) {
            kawaiiIconNames = withContext(Dispatchers.IO) {
                IconPackManager.loadAllIconNames(context)
            }
        } else {
            kawaiiIconNames = emptyList()
        }
    }

    val visibleApps = remember(apps, hiddenPackages) {
        if (hiddenPackages.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.packageName !in hiddenPackages
            }
        }
    }

    // hidden apps shown in the hidden apps manager (one entry per package)
    val hiddenApps = remember(apps, hiddenPackages) {
        if (hiddenPackages.isEmpty()) {
            emptyList()
        } else {
            apps
                .filter { it.packageName in hiddenPackages }
                .distinctBy { it.packageName }
        }
    }

    // remembers the list position where each starting letter first appears
    val letterStarts = remember(visibleApps) {
        val map = linkedMapOf<Char, Int>()
        visibleApps.forEachIndexed { index, app ->
            map.getOrPut(firstLetterOf(app.name)) { index }
        }
        map
    }

    val appsByPackage = remember(apps) {
        buildMap(apps.size) {
            for (app in apps) {
                putIfAbsent(app.packageName, app)
            }
        }
    }

    val favoriteApps = remember(apps, appsByPackage, earlyFavoriteApps, favoritePackages, hiddenPackages) {
        if (apps.isEmpty()) {
            earlyFavoriteApps.filter { it.packageName !in hiddenPackages }
        } else {
            val earlyByPkg = earlyFavoriteApps.associateBy { it.packageName }
            favoritePackages
                .filter { it !in hiddenPackages }
                .mapNotNull { pkgName ->
                    appsByPackage[pkgName] ?: earlyByPkg[pkgName]
                }
        }
    }

    val favoriteItemCount = favoriteApps.size + favoriteFolders.size
    var selectedFavoriteIndex by remember { mutableIntStateOf(0) }
    var savedFavoriteIndexOnHome by remember { mutableIntStateOf(0) }
    val homeTracker = remember { HomeTransitionTracker() }
    var selectedDrawerIndex by remember { mutableIntStateOf(0) }
    // Home focus area: 0 = FavoritesCard row, 1 = StatusCard (launcher options / page switcher)
    var homeFocusArea by remember { mutableIntStateOf(0) }

    // 0 = center not pressed, 1 = pressed, 2 = held long (menu already opened)
    var centerKeyState by remember { mutableIntStateOf(0) }
    var t9Key by remember { mutableStateOf<Int?>(null) }
    var t9Index by remember { mutableIntStateOf(0) }
    var t9Time by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

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
        folderIconMenuOpen

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

    LaunchedEffect(favoriteApps.size, favoriteFolders.size) {
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
                if (selectedFavoriteIndex < favoriteApps.size) {
                    favoriteApps.getOrNull(selectedFavoriteIndex)?.let { app ->
                        selectedApp = app
                    }
                } else {
                    favoriteFolders.getOrNull(selectedFavoriteIndex - favoriteApps.size)?.let { folder ->
                        iconEditingFolder = folder
                        folderIconMenuOpen = true
                    }
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

    CompositionLocalProvider(LocalLauncherTheme provides currentTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentTheme.bgColor)
                .focusable()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val nativeEvent = event.nativeKeyEvent
                    // Let open dialogs handle their own keys
                    if (anyModalOpen) {
                        return@onPreviewKeyEvent false
                    }
                    val isPageTransitioning = !drawerOpen && (pagerState.isScrollInProgress || (pendingTargetPage != null && pendingTargetPage != pagerState.settledPage))
                    if (isPageTransitioning) {
                        homeTracker.wasActive = false
                        if (isDpadOrActionKey(nativeEvent.keyCode)) {
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    }
                    if (isHomeScreenActive) {
                        if (!homeTracker.wasActive) {
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
                    } else {
                        homeTracker.wasActive = false
                    }
                    if (!drawerOpen && !isHomeScreenActive) {
                        return@onPreviewKeyEvent false
                    }
                    val isCenterKey = isCenterActivateKey(nativeEvent.keyCode)

                    if (isCenterKey) {
                        when (nativeEvent.action) {
                            KeyEvent.ACTION_DOWN -> {
                                if (nativeEvent.repeatCount == 0) {
                                    centerKeyState = 1
                                } else if (centerKeyState == 1) {
                                    centerKeyState = 2
                                    openContextActionForCurrentFocus()
                                }
                                true
                            }

                            KeyEvent.ACTION_UP -> {
                                val wasShortPress = centerKeyState == 1
                                centerKeyState = 0
                                if (wasShortPress) {
                                    if (drawerOpen) {
                                        if (selectedDrawerIndex == -1) {
                                            drawerOpen = false
                                        } else {
                                            visibleApps.getOrNull(selectedDrawerIndex)?.let { app ->
                                                context.packageManager.getLaunchIntentForPackage(
                                                    app.packageName
                                                )?.let(context::startActivity)
                                            }
                                        }
                                    } else if (isHomeScreenActive) {
                                        if (homeFocusArea == 0 && favoriteItemCount > 0) {
                                            if (selectedFavoriteIndex < favoriteApps.size) {
                                                favoriteApps.getOrNull(selectedFavoriteIndex)
                                                    ?.let { app ->
                                                        context.packageManager.getLaunchIntentForPackage(
                                                            app.packageName
                                                        )?.let(context::startActivity)
                                                    }
                                            } else {
                                                favoriteFolders.getOrNull(
                                                    selectedFavoriteIndex - favoriteApps.size
                                                )?.let { folder ->
                                                    folderOpenStartRename = false
                                                    folderOpenStartPicker = false
                                                    lastOpenedFolderId = folder.id
                                                    openedFolderId = folder.id
                                                }
                                            }
                                        } else if (homeFocusArea == 2) {
                                            selectedDrawerIndex = 0
                                            drawerOpen = true
                                        } else {
                                            launcherMenuOpen = true
                                        }
                                    }
                                }
                                true
                            }

                            else -> false
                        }
                    } else if (nativeEvent.action == KeyEvent.ACTION_DOWN) {
                        when (nativeEvent.keyCode) {
                            KeyEvent.KEYCODE_MENU,
                            KeyEvent.KEYCODE_STAR -> {
                                if (nativeEvent.repeatCount == 0) {
                                    openContextActionForCurrentFocus()
                                }
                                true
                            }

                            KeyEvent.KEYCODE_POUND -> {
                                if (nativeEvent.repeatCount == 0) {
                                    launcherMenuOpen = true
                                }
                                true
                            }

                            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1,
                            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2,
                            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3,
                            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4,
                            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5,
                            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6,
                            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7,
                            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8,
                            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> {
                                if (nativeEvent.repeatCount > 0) return@onPreviewKeyEvent true
                                if (!drawerOpen) {
                                    when (nativeEvent.keyCode) {
                                        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                                            pendingTargetPage = 0
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                            return@onPreviewKeyEvent true
                                        }

                                        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                                            pendingTargetPage = 1
                                            scope.launch { pagerState.animateScrollToPage(1) }
                                            return@onPreviewKeyEvent true
                                        }

                                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                                            // Consume key event so numeric shortcut does not trigger out-of-bounds navigation
                                            return@onPreviewKeyEvent true
                                        }

                                        else -> return@onPreviewKeyEvent false
                                    }
                                }
                                val t9Letters = when (nativeEvent.keyCode) {
                                    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> listOf(
                                        'A',
                                        'B',
                                        'C'
                                    )

                                    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> listOf(
                                        'D',
                                        'E',
                                        'F'
                                    )

                                    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> listOf(
                                        'G',
                                        'H',
                                        'I'
                                    )

                                    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> listOf(
                                        'J',
                                        'K',
                                        'L'
                                    )

                                    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> listOf(
                                        'M',
                                        'N',
                                        'O'
                                    )

                                    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> listOf(
                                        'P',
                                        'Q',
                                        'R',
                                        'S'
                                    )

                                    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> listOf(
                                        'T',
                                        'U',
                                        'V'
                                    )

                                    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> listOf(
                                        'W',
                                        'X',
                                        'Y',
                                        'Z'
                                    )

                                    else -> emptyList()
                                }
                                if (t9Letters.isNotEmpty()) {
                                    val now = System.currentTimeMillis()
                                    val currentKey = nativeEvent.keyCode
                                    val idx = if (t9Key == currentKey && (now - t9Time) < 1000L) {
                                        (t9Index + 1) % t9Letters.size
                                    } else {
                                        0
                                    }
                                    t9Key = currentKey
                                    t9Index = idx
                                    t9Time = now

                                    val letter = t9Letters[idx]
                                    val target = letterStarts[letter]
                                        ?: letterStarts.entries.firstOrNull { it.key > letter }?.value
                                        ?: (visibleApps.size - 1)
                                    if (target >= 0 && visibleApps.isNotEmpty()) {
                                        selectedDrawerIndex = target
                                        scope.launch { listState.scrollToItem(target) }
                                    }
                                }
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (drawerOpen) {
                                    if (selectedDrawerIndex <= 0) {
                                        if (selectedDrawerIndex == 0) {
                                            selectedDrawerIndex = -1 // Focus header
                                        } else {
                                            drawerOpen = false
                                            selectedDrawerIndex = 0
                                        }
                                    } else {
                                        selectedDrawerIndex =
                                            (selectedDrawerIndex - 1).coerceAtLeast(0)
                                    }
                                } else if (isHomeScreenActive) {
                                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                                    if (isCooldown) {
                                        return@onPreviewKeyEvent true
                                    }
                                    if (homeFocusArea == 2) {
                                        homeFocusArea = if (favoriteItemCount > 0) 0 else 1
                                    } else if (homeFocusArea == 0) {
                                        homeFocusArea = 1
                                    } else if (nativeEvent.repeatCount == 0) {
                                        selectedDrawerIndex = 0
                                        drawerOpen = true
                                    }
                                }
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (drawerOpen) {
                                    if (selectedDrawerIndex < 0) {
                                        selectedDrawerIndex = 0
                                    } else {
                                        selectedDrawerIndex =
                                            (selectedDrawerIndex + 1)
                                                .coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))
                                    }
                                } else if (isHomeScreenActive) {
                                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                                    if (isCooldown) {
                                        return@onPreviewKeyEvent true
                                    }
                                    if (homeFocusArea == 1) {
                                        homeFocusArea = if (favoriteItemCount > 0) 0 else 2
                                    } else if (homeFocusArea == 0) {
                                        homeFocusArea = 2
                                    } else if (homeFocusArea == 2 && nativeEvent.repeatCount == 0) {
                                        selectedDrawerIndex = 0
                                        drawerOpen = true
                                    }
                                }
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (drawerOpen) {
                                    if (visibleApps.isNotEmpty()) {
                                        selectedDrawerIndex =
                                            (selectedDrawerIndex - 1).coerceAtLeast(0)
                                    }
                                } else if (isHomeScreenActive) {
                                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                                    if (isCooldown) {
                                        return@onPreviewKeyEvent true
                                    }
                                    if (homeFocusArea == 0 && favoriteItemCount > 0 && selectedFavoriteIndex > 0) {
                                        selectedFavoriteIndex--
                                        savedFavoriteIndexOnHome = selectedFavoriteIndex
                                    } else if (nativeEvent.repeatCount == 0) {
                                        pendingTargetPage = 0
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    }
                                }
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (drawerOpen) {
                                    if (visibleApps.isNotEmpty()) {
                                        selectedDrawerIndex = (selectedDrawerIndex + 1).coerceIn(
                                            0,
                                            visibleApps.size - 1
                                        )
                                    }
                                } else if (isHomeScreenActive) {
                                    val isCooldown = (SystemClock.uptimeMillis() - homeTracker.settledTimestamp) < 350L
                                    if (isCooldown) {
                                        return@onPreviewKeyEvent true
                                    }
                                    if (homeFocusArea == 0 && favoriteItemCount > 0 && selectedFavoriteIndex < favoriteItemCount - 1) {
                                        selectedFavoriteIndex++
                                        savedFavoriteIndexOnHome = selectedFavoriteIndex
                                    }
                                }
                                true
                            }

                            KeyEvent.KEYCODE_BACK,
                            KeyEvent.KEYCODE_ESCAPE,
                            KeyEvent.KEYCODE_F4 -> {
                                if (drawerOpen) {
                                    drawerOpen = false
                                    true
                                } else if (isHomeScreenActive && (homeFocusArea == 1 || homeFocusArea == 2) && favoriteItemCount > 0) {
                                    homeFocusArea = 0
                                    true
                                } else if (isHomeScreenActive) {
                                    true
                                } else {
                                    false
                                }
                            }

                            else -> false
                        }
                    } else {
                        false
                    }
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
                    val folderIdx = favoriteFolders.indexOfFirst { it.id == id }
                    if (folderIdx != -1) {
                        selectedFavoriteIndex = favoriteApps.size + folderIdx
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
            val onHomeAppClick: (LauncherApp) -> Unit = remember(favoriteApps) {
                { app ->
                    val appIdx = favoriteApps.indexOfFirst {
                        it.packageName == app.packageName
                    }
                    if (appIdx != -1) {
                        homeFocusArea = 0
                        selectedFavoriteIndex = appIdx
                        savedFavoriteIndexOnHome = appIdx
                    }
                }
            }
            val onHomeAppLongClick: (LauncherApp) -> Unit = remember(favoriteApps) {
                { app ->
                    val appIdx = favoriteApps.indexOfFirst {
                        it.packageName == app.packageName
                    }
                    if (appIdx != -1) {
                        homeFocusArea = 0
                        selectedFavoriteIndex = appIdx
                        savedFavoriteIndexOnHome = appIdx
                    }
                    selectedApp = app
                }
            }
            val onHomeFolderClick: (FavoriteFolder) -> Unit = remember(favoriteApps, favoriteFolders) {
                { folder ->
                    val folderIdx =
                        favoriteFolders.indexOfFirst { it.id == folder.id }
                    if (folderIdx != -1) {
                        val targetIdx = favoriteApps.size + folderIdx
                        homeFocusArea = 0
                        selectedFavoriteIndex = targetIdx
                        savedFavoriteIndexOnHome = targetIdx
                    }
                    folderOpenStartRename = false
                    folderOpenStartPicker = false
                    lastOpenedFolderId = folder.id
                    openedFolderId = folder.id
                }
            }
            val onHomeFolderLongClick: (FavoriteFolder) -> Unit = remember(favoriteApps, favoriteFolders) {
                { folder ->
                    val folderIdx =
                        favoriteFolders.indexOfFirst { it.id == folder.id }
                    if (folderIdx != -1) {
                        val targetIdx = favoriteApps.size + folderIdx
                        homeFocusArea = 0
                        selectedFavoriteIndex = targetIdx
                        savedFavoriteIndexOnHome = targetIdx
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
                                favoriteApps = favoriteApps,
                                favoriteFolders = favoriteFolders,
                                userName = userName,
                                selectedFavoriteIndex = selectedFavoriteIndex,
                                folderIconRefreshKey = folderIconRefreshKey,
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
                        wallpaperUri = null
                        preferences.edit()
                            .remove("wallpaper_uri")
                            .apply()
                    },
                    hasCustomWallpaper = wallpaperUri != null
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
                        apps = emptyList()
                        earlyFavoriteApps = emptyList()
                        iconRefreshKey++
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
                        userName = newName
                        preferences.edit()
                            .putString("custom_user_name", newName)
                            .apply()
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
                        val pkgName = targetApp.packageName
                        val list = favoritePackages.toMutableList()
                        val index = list.indexOf(pkgName)
                        if (index > 0) {
                            list.removeAt(index)
                            list.add(index - 1, pkgName)
                            favoritePackages = list
                            preferences.edit()
                                .putString("favorite_packages_ordered", list.joinToString("|"))
                                .apply()
                            selectedFavoriteIndex = (index - 1).coerceAtLeast(0)
                            homeFocusArea = 0
                        }
                        selectedApp = null
                    },
                    onMoveFavoriteRight = {
                        val pkgName = targetApp.packageName
                        val list = favoritePackages.toMutableList()
                        val index = list.indexOf(pkgName)
                        if (index >= 0 && index < list.lastIndex) {
                            list.removeAt(index)
                            list.add(index + 1, pkgName)
                            favoritePackages = list
                            preferences.edit()
                                .putString("favorite_packages_ordered", list.joinToString("|"))
                                .apply()
                            selectedFavoriteIndex = index + 1
                            homeFocusArea = 0
                        }
                        selectedApp = null
                    },
                    onToggleFavorite = {
                        val pkgName = targetApp.packageName
                        val list = favoritePackages.toMutableList()
                        if (pkgName in list) {
                            list.remove(pkgName)
                        } else {
                            list.add(pkgName)
                        }
                        favoritePackages = list
                        preferences.edit()
                            .putString("favorite_packages_ordered", list.joinToString("|"))
                            .apply()
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
                        val pkgName = targetApp.packageName
                        preferences.edit()
                            .remove("custom_icon_$pkgName")
                            .apply()
                        IconPackManager.invalidateAppIcon(pkgName)
                        iconRefreshKey++
                        selectedApp = null
                    },
                    onHideApp = {
                        val pkgName = targetApp.packageName
                        val newHidden = hiddenPackages.toMutableSet().apply {
                            add(pkgName)
                        }
                        hiddenPackages = newHidden
                        preferences.edit()
                            .putStringSet("hidden_apps", newHidden)
                            .apply()
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
                val folderIndex = favoriteFolders.indexOfFirst {
                    it.id == targetFolder.id
                }

                FolderActionDialog(
                    folder = targetFolder,
                    canMoveLeft = folderIndex > 0,
                    canMoveRight = folderIndex >= 0 && folderIndex < favoriteFolders.lastIndex,
                    onMoveLeft = {
                        if (folderIndex > 0) {
                            val reordered = favoriteFolders.toMutableList()
                            val folder = reordered.removeAt(folderIndex)
                            reordered.add(folderIndex - 1, folder)
                            favoriteFolders = reordered
                            PreferencesManager.saveFavoriteFolders(context, reordered)
                            selectedFavoriteIndex = favoriteApps.size + folderIndex - 1
                            homeFocusArea = 0
                        }
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                    },
                    onMoveRight = {
                        if (folderIndex >= 0 && folderIndex < favoriteFolders.lastIndex) {
                            val reordered = favoriteFolders.toMutableList()
                            val folder = reordered.removeAt(folderIndex)
                            reordered.add(folderIndex + 1, folder)
                            favoriteFolders = reordered
                            PreferencesManager.saveFavoriteFolders(context, reordered)
                            selectedFavoriteIndex = favoriteApps.size + folderIndex + 1
                            homeFocusArea = 0
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
                        preferences.edit()
                            .remove("custom_folder_icon_${targetFolder.id}")
                            .apply()
                        folderIconRefreshKey++
                        folderIconMenuOpen = false
                        iconEditingFolder = null
                    },
                    onDeleteFolder = {
                        val deletedId = targetFolder.id
                        favoriteFolders = favoriteFolders.filterNot { it.id == deletedId }
                        preferences.edit()
                            .remove("custom_folder_icon_$deletedId")
                            .apply()
                        PreferencesManager.saveFavoriteFolders(context, favoriteFolders)
                        val newCount = favoriteApps.size + favoriteFolders.size
                        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
                            0,
                            (newCount - 1).coerceAtLeast(0)
                        )
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
                        val idx = favoriteFolders.indexOfFirst { it.id == targetFolder.id }
                        if (idx != -1) {
                            selectedFavoriteIndex = favoriteApps.size + idx
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
                                val pkgName = editingApp.packageName

                                preferences.edit()
                                    .putString("custom_icon_$pkgName", selectedIconName)
                                    .apply()
                                IconPackManager.invalidateAppIcon(pkgName)
                                iconRefreshKey++
                            }

                            editingFolder != null -> {
                                preferences.edit()
                                    .putString(
                                        "custom_folder_icon_${editingFolder.id}",
                                        selectedIconName
                                    )
                                    .apply()
                                folderIconRefreshKey++
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
                        kawaiiIconNames = emptyList()
                    }
                )
            }

            // hidden apps manager dialog
            if (hiddenManagerOpen) {
                HiddenAppsDialog(
                    hiddenApps = hiddenApps,
                    onUnhide = { app ->
                        val pkgName = app.packageName
                        val newHidden = hiddenPackages.toMutableSet().apply { remove(pkgName) }
                        hiddenPackages = newHidden
                        preferences.edit()
                            .putStringSet("hidden_apps", newHidden)
                            .apply()
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
                        favoriteFolders = favoriteFolders.map { folder ->
                            if (folder.id == openedFolder.id) {
                                folder.copy(
                                    appPackages = folder.appPackages.filterNot {
                                        it == packageName
                                    }
                                )
                            } else {
                                folder
                            }
                        }

                        PreferencesManager.saveFavoriteFolders(context, favoriteFolders)
                    },
                    onUpdateApps = { updatedPackages ->
                        val distinctPackages = updatedPackages.distinct()
                        favoriteFolders = favoriteFolders.map { folder ->
                            if (folder.id == openedFolder.id) {
                                folder.copy(appPackages = distinctPackages)
                            } else {
                                folder
                            }
                        }
                        PreferencesManager.saveFavoriteFolders(context, favoriteFolders)

                        // Keep apps added to the folder from appearing twice in standalone favorites
                        val updatedFavorites = favoritePackages.filterNot {
                            it in distinctPackages
                        }
                        if (updatedFavorites != favoritePackages) {
                            favoritePackages = updatedFavorites
                            preferences.edit()
                                .putString(
                                    "favorite_packages_ordered",
                                    updatedFavorites.joinToString("|")
                                )
                                .apply()
                        }
                    },
                    onRename = { newName ->
                        val duplicateName = favoriteFolders.any {
                            it.id != openedFolder.id &&
                                    it.name.equals(newName, ignoreCase = true)
                        }

                        if (!duplicateName && newName.isNotBlank()) {
                            favoriteFolders = favoriteFolders.map { folder ->
                                if (folder.id == openedFolder.id) {
                                    folder.copy(name = newName)
                                } else {
                                    folder
                                }
                            }

                            PreferencesManager.saveFavoriteFolders(context, favoriteFolders)
                        }
                    },
                    onDeleteFolder = {
                        val deletedId = openedFolder.id
                        favoriteFolders = favoriteFolders.filterNot { it.id == deletedId }
                        preferences.edit()
                            .remove("custom_folder_icon_$deletedId")
                            .apply()

                        PreferencesManager.saveFavoriteFolders(context, favoriteFolders)
                        val newCount = favoriteApps.size + favoriteFolders.size
                        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
                            0,
                            (newCount - 1).coerceAtLeast(0)
                        )
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
                            val folderIdx = favoriteFolders.indexOfFirst { it.id == id }
                            if (folderIdx != -1) {
                                selectedFavoriteIndex = favoriteApps.size + folderIdx
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
                        val folder = FavoriteFolder(
                            id = UUID.randomUUID().toString(),
                            name = folderName,
                            appPackages = packages.distinct()
                        )

                        favoriteFolders = favoriteFolders + folder
                        PreferencesManager.saveFavoriteFolders(context, favoriteFolders)

                        // Keep apps contained in the new folder from appearing twice.
                        val remainingFavApps = favoritePackages.filterNot {
                            it in folder.appPackages && it !in hiddenPackages
                        }
                        favoritePackages = remainingFavApps

                        preferences.edit()
                            .putString(
                                "favorite_packages_ordered",
                                favoritePackages.joinToString("|")
                            )
                            .apply()

                        val newFavAppCount = remainingFavApps.count { it !in hiddenPackages }
                        selectedFavoriteIndex =
                            (newFavAppCount + favoriteFolders.size - 1)
                                .coerceAtLeast(0)
                        homeFocusArea = 0
                        drawerOpen = false
                        folderToCreateFrom = null
                        createFolderDialogOpen = false
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
