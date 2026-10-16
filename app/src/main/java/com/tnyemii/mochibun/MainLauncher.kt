package com.tnyemii.mochibun

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// main launcher composable managing focus, gestures, state, and page navigation <3
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

    // wallpaper selection and persistence state :3
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

    // memory-efficient sampled wallpaper loader <3
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
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    var selectedApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconEditingApp by remember { mutableStateOf<LauncherApp?>(null) }
    var iconPickerOpen by remember { mutableStateOf(false) }
    var kawaiiIconNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var iconRefreshKey by remember { mutableIntStateOf(0) }
    var folderIconRefreshKey by remember { mutableIntStateOf(0) }
    var launcherMenuOpen by remember { mutableStateOf(false) }
    var hiddenManagerOpen by remember { mutableStateOf(false) }
    var hardwareButtonsDialogOpen by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(true) }
    var isLowBattery by remember { mutableStateOf(false) }

    LaunchedEffect(requestedPage) {
        requestedPage?.let { target ->
            pagerState.animateScrollToPage(target)
            onPageConsumed()
        }
    }

    var favoriteFolders by remember { mutableStateOf(loadFavoriteFolders(context)) }
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

    // sequential two-phase loader: loads pinned favorites first (~20ms) for instant home screen, then full app list reusing cached icons :3
    LaunchedEffect(appListRefreshCount, iconRefreshKey) {
        if (apps.isEmpty() && favoritePackages.isNotEmpty()) {
            val fastFavs = withContext(Dispatchers.Default) {
                val kawaiiIconContext = getKawaiiIconPackContext(context)
                val kawaiiIconMap = loadKawaiiIconMap(context)

                favoritePackages
                    .filter { it !in hiddenPackages }
                    .mapNotNull { pkgName ->
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkgName)
                            ?: return@mapNotNull null
                        val resolveInfo = context.packageManager.resolveActivity(launchIntent, 0)
                            ?: return@mapNotNull null

                        getOrLoadLauncherApp(
                            context = context,
                            preferences = preferences,
                            resolveInfo = resolveInfo,
                            kawaiiIconContext = kawaiiIconContext,
                            kawaiiIconMap = kawaiiIconMap
                        )
                    }
            }
            earlyFavoriteApps = fastFavs
        }

        apps = withContext(Dispatchers.Default) {
            val kawaiiIconContext = getKawaiiIconPackContext(context)
            val kawaiiIconMap = loadKawaiiIconMap(context)

            context.packageManager
                .queryIntentActivities(intent, 0)
                .map { app ->
                    getOrLoadLauncherApp(
                        context = context,
                        preferences = preferences,
                        resolveInfo = app,
                        kawaiiIconContext = kawaiiIconContext,
                        kawaiiIconMap = kawaiiIconMap
                    )
                }
                .sortedBy { it.lowercaseName }
        }
    }

    // async load icon names list for icon picker <3
    LaunchedEffect(iconPickerOpen) {
        if (iconPickerOpen) {
            kawaiiIconNames = withContext(Dispatchers.Default) {
                loadAllKawaiiIconNames(context)
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

    // hidden apps shown in the hidden apps manager (one entry per package) :3
    val hiddenApps = remember(apps, hiddenPackages) {
        if (hiddenPackages.isEmpty()) {
            emptyList()
        } else {
            apps
                .filter { it.packageName in hiddenPackages }
                .distinctBy { it.packageName }
        }
    }

    // remembers the list position where each starting letter first appears :3
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
    var selectedDrawerIndex by remember { mutableIntStateOf(0) }
    // Home focus area: 0 = FavoritesCard row, 1 = StatusCard (launcher options / page switcher)
    var homeFocusArea by remember { mutableIntStateOf(0) }

    // 0 = center not pressed, 1 = pressed, 2 = held long (menu already opened) :3
    var centerKeyState by remember { mutableIntStateOf(0) }
    var t9Key by remember { mutableStateOf<Int?>(null) }
    var t9Index by remember { mutableIntStateOf(0) }
    var t9Time by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }

    val anyModalOpen = selectedApp != null ||
        launcherMenuOpen ||
        iconPickerOpen ||
        hiddenManagerOpen ||
        themeStudioOpen ||
        hardwareButtonsDialogOpen ||
        openedFolderId != null ||
        createFolderDialogOpen ||
        folderToCreateFrom != null ||
        folderIconMenuOpen

    LaunchedEffect(favoriteApps.size, favoriteFolders.size) {
        selectedFavoriteIndex = selectedFavoriteIndex.coerceIn(
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

    // auto scroll to highlighted item in app drawer :3
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
        } else if (pagerState.currentPage == 1) {
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
            } else {
                launcherMenuOpen = true
            }
        }
    }

    // Timed long-press detection for Center/OK button so holding OK opens menus immediately <3
    LaunchedEffect(centerKeyState, drawerOpen, pagerState.currentPage, anyModalOpen) {
        if (centerKeyState == 1 && !anyModalOpen && (drawerOpen || pagerState.currentPage == 1)) {
            delay(420L)
            if (centerKeyState == 1 && !anyModalOpen) {
                centerKeyState = 2
                openContextActionForCurrentFocus()
            }
        }
    }

    // focus requester handling for physical hardware key navigation <3
    LaunchedEffect(
        drawerOpen,
        pagerState.currentPage,
        anyModalOpen
    ) {
        // Modal dialogs manage their own focus.
        if (anyModalOpen) {
            centerKeyState = 0
            return@LaunchedEffect
        }

        if (drawerOpen || pagerState.currentPage == 1) {
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

    // physical home button press brings user back to home screen :3
    LaunchedEffect(homePressCount) {
        if (homePressCount > 0) {
            drawerOpen = false
            selectedApp = null
            launcherMenuOpen = false
            themeStudioOpen = false
            iconPickerOpen = false
            hiddenManagerOpen = false
            openedFolderId = null
            folderToCreateFrom = null
            createFolderDialogOpen = false
            folderIconMenuOpen = false
            homeFocusArea = if (favoriteItemCount > 0) 0 else 1
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
                .focusable()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    // Let open dialogs and non-home pages (Widgets / AI Chat) handle their own keys
                    if (anyModalOpen) {
                        return@onPreviewKeyEvent false
                    }
                    if (!drawerOpen && pagerState.currentPage != 1) {
                        return@onPreviewKeyEvent false
                    }

                    val nativeEvent = event.nativeKeyEvent
                    val isCenterKey = isCenterActivateKey(nativeEvent.keyCode)

                    if (isCenterKey) {
                        when (nativeEvent.action) {
                            android.view.KeyEvent.ACTION_DOWN -> {
                                if (nativeEvent.repeatCount == 0) {
                                    centerKeyState = 1
                                } else if (centerKeyState == 1) {
                                    centerKeyState = 2
                                    openContextActionForCurrentFocus()
                                }
                                true
                            }

                            android.view.KeyEvent.ACTION_UP -> {
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
                                    } else if (pagerState.currentPage == 1) {
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
                                        } else {
                                            launcherMenuOpen = true
                                        }
                                    }
                                }
                                true
                            }

                            else -> false
                        }
                    } else if (nativeEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                        when (nativeEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_MENU,
                            android.view.KeyEvent.KEYCODE_STAR -> {
                                if (nativeEvent.repeatCount == 0) {
                                    openContextActionForCurrentFocus()
                                }
                                true
                            }

                            android.view.KeyEvent.KEYCODE_POUND -> {
                                if (nativeEvent.repeatCount == 0) {
                                    launcherMenuOpen = true
                                }
                                true
                            }

                            android.view.KeyEvent.KEYCODE_1, android.view.KeyEvent.KEYCODE_NUMPAD_1,
                            android.view.KeyEvent.KEYCODE_2, android.view.KeyEvent.KEYCODE_NUMPAD_2,
                            android.view.KeyEvent.KEYCODE_3, android.view.KeyEvent.KEYCODE_NUMPAD_3,
                            android.view.KeyEvent.KEYCODE_4, android.view.KeyEvent.KEYCODE_NUMPAD_4,
                            android.view.KeyEvent.KEYCODE_5, android.view.KeyEvent.KEYCODE_NUMPAD_5,
                            android.view.KeyEvent.KEYCODE_6, android.view.KeyEvent.KEYCODE_NUMPAD_6,
                            android.view.KeyEvent.KEYCODE_7, android.view.KeyEvent.KEYCODE_NUMPAD_7,
                            android.view.KeyEvent.KEYCODE_8, android.view.KeyEvent.KEYCODE_NUMPAD_8,
                            android.view.KeyEvent.KEYCODE_9, android.view.KeyEvent.KEYCODE_NUMPAD_9 -> {
                                if (nativeEvent.repeatCount > 0) return@onPreviewKeyEvent true
                                if (!drawerOpen) {
                                    when (nativeEvent.keyCode) {
                                        android.view.KeyEvent.KEYCODE_1, android.view.KeyEvent.KEYCODE_NUMPAD_1 -> {
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                            return@onPreviewKeyEvent true
                                        }

                                        android.view.KeyEvent.KEYCODE_2, android.view.KeyEvent.KEYCODE_NUMPAD_2 -> {
                                            scope.launch { pagerState.animateScrollToPage(1) }
                                            return@onPreviewKeyEvent true
                                        }

                                        android.view.KeyEvent.KEYCODE_3, android.view.KeyEvent.KEYCODE_NUMPAD_3 -> {
                                            scope.launch { pagerState.animateScrollToPage(2) }
                                            return@onPreviewKeyEvent true
                                        }

                                        else -> return@onPreviewKeyEvent false
                                    }
                                }
                                val t9Letters = when (nativeEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_2, android.view.KeyEvent.KEYCODE_NUMPAD_2 -> listOf(
                                        'A',
                                        'B',
                                        'C'
                                    )

                                    android.view.KeyEvent.KEYCODE_3, android.view.KeyEvent.KEYCODE_NUMPAD_3 -> listOf(
                                        'D',
                                        'E',
                                        'F'
                                    )

                                    android.view.KeyEvent.KEYCODE_4, android.view.KeyEvent.KEYCODE_NUMPAD_4 -> listOf(
                                        'G',
                                        'H',
                                        'I'
                                    )

                                    android.view.KeyEvent.KEYCODE_5, android.view.KeyEvent.KEYCODE_NUMPAD_5 -> listOf(
                                        'J',
                                        'K',
                                        'L'
                                    )

                                    android.view.KeyEvent.KEYCODE_6, android.view.KeyEvent.KEYCODE_NUMPAD_6 -> listOf(
                                        'M',
                                        'N',
                                        'O'
                                    )

                                    android.view.KeyEvent.KEYCODE_7, android.view.KeyEvent.KEYCODE_NUMPAD_7 -> listOf(
                                        'P',
                                        'Q',
                                        'R',
                                        'S'
                                    )

                                    android.view.KeyEvent.KEYCODE_8, android.view.KeyEvent.KEYCODE_NUMPAD_8 -> listOf(
                                        'T',
                                        'U',
                                        'V'
                                    )

                                    android.view.KeyEvent.KEYCODE_9, android.view.KeyEvent.KEYCODE_NUMPAD_9 -> listOf(
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

                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
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
                                } else if (pagerState.currentPage == 1) {
                                    if (homeFocusArea == 1 && favoriteItemCount > 0) {
                                        homeFocusArea = 0
                                    } else {
                                        selectedDrawerIndex = 0
                                        drawerOpen = true
                                    }
                                }
                                true
                            }

                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (drawerOpen) {
                                    if (selectedDrawerIndex < 0) {
                                        selectedDrawerIndex = 0
                                    } else {
                                        selectedDrawerIndex =
                                            (selectedDrawerIndex + 1)
                                                .coerceIn(0, (visibleApps.size - 1).coerceAtLeast(0))
                                    }
                                } else if (pagerState.currentPage == 1) {
                                    if (homeFocusArea == 0) {
                                        homeFocusArea = 1
                                    } else {
                                        launcherMenuOpen = true
                                    }
                                }
                                true
                            }

                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (drawerOpen) {
                                    if (visibleApps.isNotEmpty()) {
                                        selectedDrawerIndex =
                                            (selectedDrawerIndex - 1).coerceAtLeast(0)
                                    }
                                } else if (pagerState.currentPage == 1) {
                                    if (homeFocusArea == 0 && favoriteItemCount > 0 && selectedFavoriteIndex > 0) {
                                        selectedFavoriteIndex--
                                    } else {
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    }
                                }
                                true
                            }

                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (drawerOpen) {
                                    if (visibleApps.isNotEmpty()) {
                                        selectedDrawerIndex = (selectedDrawerIndex + 1).coerceIn(
                                            0,
                                            visibleApps.size - 1
                                        )
                                    }
                                } else if (pagerState.currentPage == 1) {
                                    if (homeFocusArea == 0 && favoriteItemCount > 0 && selectedFavoriteIndex < favoriteItemCount - 1) {
                                        selectedFavoriteIndex++
                                    } else {
                                        scope.launch { pagerState.animateScrollToPage(2) }
                                    }
                                }
                                true
                            }

                            android.view.KeyEvent.KEYCODE_BACK,
                            android.view.KeyEvent.KEYCODE_ESCAPE -> {
                                if (drawerOpen) {
                                    drawerOpen = false
                                    true
                                } else if (homeFocusArea == 1 && favoriteItemCount > 0) {
                                    homeFocusArea = 0
                                    true
                                } else {
                                    true
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
                        if (pagerState.currentPage == 1 && !drawerOpen) {
                            launcherMenuOpen = true
                        }
                    }
                )
        ) {
            BackHandler(enabled = drawerOpen) {
                drawerOpen = false
            }

            BackHandler(enabled = !drawerOpen && !anyModalOpen && pagerState.currentPage != 1) {
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
            }

            // cute sticker decorations only on main home page when drawer is closed :3
            if (!drawerOpen && pagerState.currentPage == 1) {
                StickerExplosion()
            }

            val keyboardController = LocalSoftwareKeyboardController.current
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != 2) {
                    keyboardController?.hide()
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .pointerInput(drawerOpen, pagerState.currentPage) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (!drawerOpen && pagerState.currentPage == 1 && dragAmount < -32) {
                                selectedDrawerIndex = 0
                                drawerOpen = true
                            }
                        }
                    },
                userScrollEnabled = !drawerOpen && !anyModalOpen
            ) { page ->
                when (page) {
                    0 -> {
                        if (!drawerOpen && (pagerState.currentPage == 0 || pagerState.targetPage == 0)) {
                            WidgetPage(
                                context = context,
                                cardBgColor = cardBgColor,
                                cardTextColor = cardTextColor,
                                appActionColor = appActionColor,
                                cardRoundness = cardRoundness,
                                isActivePage = pagerState.currentPage == 0 && !anyModalOpen,
                                onNavigateHome = {
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }

                    1 -> {
                        if (!drawerOpen) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                GreetingCard(
                                    context = context,
                                    isOnline = isOnline,
                                    isLowBattery = isLowBattery
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusCard(
                                    context = context,
                                    cardBgColor = cardBgColor,
                                    cardTextColor = cardTextColor,
                                    outlineColor = greetingOutlineColor,
                                    isFocused = homeFocusArea == 1 || favoriteItemCount == 0,
                                    onClick = {
                                        homeFocusArea = 1
                                        launcherMenuOpen = true
                                    },
                                    onWifiChanged = { isOnline = it },
                                    onLowBatteryChanged = { isLowBattery = it }
                                )
                                FavoritesCard(
                                    context = context,
                                    favoriteApps = favoriteApps,
                                    favoriteFolders = favoriteFolders,
                                    folderIconRefreshKey = folderIconRefreshKey,
                                    selectedIndex = selectedFavoriteIndex,
                                    isCardFocused = homeFocusArea == 0,
                                    onAppClick = { app ->
                                        val appIdx = favoriteApps.indexOfFirst {
                                            it.packageName == app.packageName
                                        }
                                        if (appIdx != -1) {
                                            homeFocusArea = 0
                                            selectedFavoriteIndex = appIdx
                                        }
                                    },
                                    onAppLongClick = { app ->
                                        val appIdx = favoriteApps.indexOfFirst {
                                            it.packageName == app.packageName
                                        }
                                        if (appIdx != -1) {
                                            homeFocusArea = 0
                                            selectedFavoriteIndex = appIdx
                                        }
                                        selectedApp = app
                                    },
                                    onFolderClick = { folder ->
                                        val folderIdx = favoriteFolders.indexOfFirst { it.id == folder.id }
                                        if (folderIdx != -1) {
                                            homeFocusArea = 0
                                            selectedFavoriteIndex = favoriteApps.size + folderIdx
                                        }
                                        folderOpenStartRename = false
                                        folderOpenStartPicker = false
                                        lastOpenedFolderId = folder.id
                                        openedFolderId = folder.id
                                    },
                                    onFolderLongClick = { folder ->
                                        val folderIdx = favoriteFolders.indexOfFirst { it.id == folder.id }
                                        if (folderIdx != -1) {
                                            homeFocusArea = 0
                                            selectedFavoriteIndex = favoriteApps.size + folderIdx
                                        }
                                        iconEditingFolder = folder
                                        folderIconMenuOpen = true
                                    }
                                )
                            }
                        }
                    }

                    2 -> {
                        if (!drawerOpen && (pagerState.currentPage == 2 || pagerState.targetPage == 2)) {
                            ChatPlaceholderPage(
                                isActivePage = pagerState.currentPage == 2 && !anyModalOpen,
                                onBack = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // long-press launcher menu dialog :3
            if (launcherMenuOpen) {
                LauncherMenuDialog(
                    onDismiss = {
                        launcherMenuOpen = false
                    },
                    onCreateFolderClick = {
                        launcherMenuOpen = false
                        folderToCreateFrom = null
                        createFolderDialogOpen = true
                    },
                    onHardwareButtonsClick = {
                        launcherMenuOpen = false
                        hardwareButtonsDialogOpen = true
                    },
                    onHiddenAppsClick = {
                        launcherMenuOpen = false
                        hiddenManagerOpen = true
                    },
                    onThemeStudioClick = {
                        launcherMenuOpen = false
                        themeStudioOpen = true
                    },
                    onPickWallpaperClick = {
                        launcherMenuOpen = false
                        try {
                            wallpaperPicker.launch(arrayOf("image/*"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onResetWallpaperClick = {
                        launcherMenuOpen = false
                        wallpaperUri = null
                        preferences.edit()
                            .remove("wallpaper_uri")
                            .apply()
                    },
                    showResetWallpaper = wallpaperUri != null
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

            // app options long-press dialog :3
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
                        invalidateCachedAppIcon(pkgName)
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
                            saveFavoriteFolders(context, reordered)
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
                            saveFavoriteFolders(context, reordered)
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
                        saveFavoriteFolders(context, favoriteFolders)
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

            // custom KawaiiIconPack icon picker dialog <3
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
                                invalidateCachedAppIcon(pkgName)
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

            // hidden apps manager dialog :3
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

                        saveFavoriteFolders(context, favoriteFolders)
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
                        saveFavoriteFolders(context, favoriteFolders)

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

                            saveFavoriteFolders(context, favoriteFolders)
                        }
                    },
                    onDeleteFolder = {
                        val deletedId = openedFolder.id
                        favoriteFolders = favoriteFolders.filterNot {
                            it.id == deletedId
                        }
                        preferences.edit()
                            .remove("custom_folder_icon_$deletedId")
                            .apply()

                        saveFavoriteFolders(context, favoriteFolders)
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
                            id = java.util.UUID.randomUUID().toString(),
                            name = folderName,
                            appPackages = packages.distinct()
                        )

                        favoriteFolders = favoriteFolders + folder
                        saveFavoriteFolders(context, favoriteFolders)

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

            // app drawer view overlay :3
            if (drawerOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.bgColor.copy(alpha = 0.88f))
                        .padding(16.dp)
                ) {
                    AppDrawerHeader(
                        isFocused = selectedDrawerIndex == -1,
                        onSwipeDown = { drawerOpen = false }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(cardRoundness))
                                .background(Color.Transparent)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                visibleApps,
                                key = { _, app -> app.componentKey }
                            ) { index, app ->
                                AppDrawerGridItem(
                                    context = context,
                                    app = app,
                                    isSelected = index == selectedDrawerIndex,
                                    onAppClick = { selectedDrawerIndex = index },
                                    onAppLongClick = {
                                        selectedDrawerIndex = index
                                        selectedApp = it
                                    }
                                )
                            }
                        }

                        AlphabetIndex(
                            availableLetters = letterStarts.keys,
                            activeLetter = currentDrawerLetter,
                            onLetterTap = { letter ->
                                val target = letterStarts[letter]
                                    ?: letterStarts.entries.firstOrNull { it.key > letter }?.value
                                    ?: (visibleApps.size - 1)
                                if (target >= 0) {
                                    selectedDrawerIndex = target
                                    scope.launch { listState.scrollToItem(target) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
