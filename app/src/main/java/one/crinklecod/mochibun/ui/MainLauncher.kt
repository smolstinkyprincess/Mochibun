package one.crinklecod.mochibun.ui

import one.crinklecod.mochibun.service.VirtualCursorService
import one.crinklecod.mochibun.data.prefs.LidOpenAction
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
import one.crinklecod.mochibun.ui.components.WallpaperOverlay
import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_MASCOT
import one.crinklecod.mochibun.core.keypad.HomeTransitionTracker
import one.crinklecod.mochibun.core.keypad.LauncherKeypadActions
import one.crinklecod.mochibun.core.keypad.LauncherKeypadState
import one.crinklecod.mochibun.core.keypad.PET_PILL_AUTO_CLOSE_MS
import one.crinklecod.mochibun.core.keypad.PET_PILL_FEED
import one.crinklecod.mochibun.core.keypad.PET_PILL_PET
import one.crinklecod.mochibun.core.keypad.T9InputState
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.model.AppInfo
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.model.FavoriteSlotItem
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BundlePreviewData
import one.crinklecod.mochibun.data.repository.LidLedAnimationRepository
import one.crinklecod.mochibun.data.repository.ExternalThemeManager
import one.crinklecod.mochibun.data.repository.ThemeBundleManager
import one.crinklecod.mochibun.service.InputArbiter
import one.crinklecod.mochibun.ui.components.WidgetPage
import one.crinklecod.mochibun.ui.components.MascotReaction
import one.crinklecod.mochibun.ui.components.MascotReactionKind
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.ui.dialogs.AppActionDialog
import one.crinklecod.mochibun.ui.dialogs.CreateFavoriteFolderDialog
import one.crinklecod.mochibun.ui.dialogs.FavoriteFolderDialog
import one.crinklecod.mochibun.ui.dialogs.FolderActionDialog
import one.crinklecod.mochibun.ui.dialogs.HardwareButtonsDialog
import one.crinklecod.mochibun.ui.dialogs.HiddenAppsDialog
import one.crinklecod.mochibun.ui.dialogs.IconPackSelectionDialog
import one.crinklecod.mochibun.ui.dialogs.KaomojiWheelDialog
import one.crinklecod.mochibun.ui.dialogs.QuickMemoDialog
import one.crinklecod.mochibun.ui.dialogs.KawaiiIconPicker
import one.crinklecod.mochibun.ui.dialogs.PresetPreviewDialog
import one.crinklecod.mochibun.ui.dialogs.awaitFocusSafely
import one.crinklecod.mochibun.ui.dialogs.dialogWidth
import one.crinklecod.mochibun.ui.screens.AppDrawerScreen
import one.crinklecod.mochibun.ui.screens.firstFavoriteIndexOfPage
import one.crinklecod.mochibun.ui.screens.HomeScreen
import one.crinklecod.mochibun.ui.screens.NotificationsScreen
import one.crinklecod.mochibun.ui.screens.SettingsDestination
import one.crinklecod.mochibun.ui.screens.SettingsHubDialog
import one.crinklecod.mochibun.ui.screens.ThemeStudioDialog
import one.crinklecod.mochibun.ui.screens.firstLetterOf
import one.crinklecod.mochibun.ui.theme.LauncherTheme
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.MochibunSystemBarsEffect
import one.crinklecod.mochibun.ui.theme.SystemUiThemeBridge
import one.crinklecod.mochibun.ui.theme.loadSavedTheme
import one.crinklecod.mochibun.ui.theme.saveTheme
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
    data object KeyboardSettings : ActiveModal
    data object ThemeStudio : ActiveModal
    data object IconPackSelection : ActiveModal
    data object HiddenApps : ActiveModal
    data object KaomojiWheel : ActiveModal
    data object QuickMemo : ActiveModal
    /** Mochibunny's Feed / Pet / Chat pill, drawn inside Home; LauncherKeyHandler routes its keys. */
    data object PetPill : ActiveModal
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
    screenOffResetCount: Int = 0,
    unlockCount: Int = 0,
    settingsRequestCount: Int = 0,
    quickMemoRequestCount: Int = 0,
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
    LaunchedEffect(settingsRequestCount) {
        if (settingsRequestCount > 0) activeModal = ActiveModal.KeyboardSettings
    }

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
        // Lid LED animation files import straight into the Lid LED Studio library
        val isLidAnimation = uri != null && withContext(Dispatchers.IO) {
            LidLedAnimationRepository.isAnimationFileName(LidLedAnimationRepository.displayName(context, uri))
        }
        if (uri != null && isLidAnimation) {
            val imported = withContext(Dispatchers.IO) { LidLedAnimationRepository.importFromUri(context, uri) }
            Toast.makeText(
                context,
                imported.fold(
                    onSuccess = { "Lid animation \"${it.name}\" added to Lid LED Studio ♡" },
                    onFailure = { "Failed to import lid animation: ${it.message ?: "invalid file"}" }
                ),
                Toast.LENGTH_SHORT
            ).show()
            onConsumeIncomingBundleUri()
        } else if (uri != null) {
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
    // Mochibunny pet pill: selected option, auto-close timer key, and the latest reaction for her
    var petPillSelection by remember { mutableIntStateOf(PET_PILL_FEED) }
    var petPillInteractionTick by remember { mutableIntStateOf(0) }
    var mascotReaction by remember { mutableStateOf(MascotReaction()) }
    // Set when the pill closes back to Home, so the focus reset below keeps Mochibunny focused
    var restoreMascotFocus by remember { mutableStateOf(false) }


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

    // Home is on screen with at most the pet pill over it. Unlike isHomeScreenActive (false while
    // the pill, a modal, owns the keys), this keeps Home visuals alive: mascot glow & bob, ticker.
    val isPetPillOpen = activeModal is ActiveModal.PetPill
    val isHomeVisible = !drawerOpen &&
        (activeModal == null || isPetPillOpen) &&
        (pendingTargetPage == null || pendingTargetPage == 1) &&
        pagerState.currentPage == 1 &&
        pagerState.targetPage == 1 &&
        pagerState.settledPage == 1 &&
        !pagerState.isScrollInProgress

    fun openPetPillModal() {
        if (activeModal != null || drawerOpen) return
        PetManager.refresh()
        homeFocusArea = HOME_FOCUS_MASCOT
        petPillSelection = PET_PILL_FEED
        petPillInteractionTick++
        activeModal = ActiveModal.PetPill
    }

    // Back, tap outside, or the auto-close timer: Home focus goes back to Mochibunny.
    // (Lid close, Home press and screen-off reset clear activeModal directly and keep their own reset.)
    fun closePetPillModal() {
        if (activeModal !is ActiveModal.PetPill) return
        activeModal = null
        homeFocusArea = HOME_FOCUS_MASCOT
        restoreMascotFocus = true
    }

    fun runPetPillOption(index: Int) {
        if (activeModal !is ActiveModal.PetPill) return
        petPillSelection = index
        petPillInteractionTick++
        val kind = when (index) {
            PET_PILL_FEED -> if (PetManager.feed()) MascotReactionKind.FED else MascotReactionKind.NO_TREATS
            PET_PILL_PET -> {
                PetManager.pet()
                MascotReactionKind.PETTED
            }
            else -> MascotReactionKind.CHAT
        }
        mascotReaction = MascotReaction(id = mascotReaction.id + 1, kind = kind)
    }

    // Catch her up on decay whenever Home comes back on screen (in memory only, no disk write),
    // so her mood and sprite row are current after time away
    LaunchedEffect(isHomeVisible) {
        if (isHomeVisible) PetManager.refresh()
    }

    // Auto-close after PET_PILL_AUTO_CLOSE_MS without a key or tap (every interaction bumps the tick)
    LaunchedEffect(isPetPillOpen, petPillInteractionTick) {
        if (isPetPillOpen) {
            delay(PET_PILL_AUTO_CLOSE_MS)
            closePetPillModal()
        }
    }

    // Flip physically closed (never a display timeout): dismiss transient modals, collapse folder/drawer, reset T9 state, and position on Home
    // Set on a real close so the open action runs on a flip open, not on a display wake with the flip already open
    var lidClosedSinceOpen by remember { mutableStateOf(false) }

    LaunchedEffect(lidClosedCount) {
        if (lidClosedCount > 0) {
            lidClosedSinceOpen = true
            SoundEffectManager.playSnap(context)
            VirtualCursorService.hideCursor()
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

    // Non-root Screen-Off Flip Reset: same clean slate as a detected flip close, minus the snap
    // sound and the Lid Open Action (a screen-off may just be a display timeout)
    LaunchedEffect(screenOffResetCount) {
        if (screenOffResetCount > 0) {
            VirtualCursorService.hideCursor()
            activeModal = null
            drawerOpen = false
            drawerRailFocused = false
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
            val flipOpened = lidClosedSinceOpen
            lidClosedSinceOpen = false
            val action = if (flipOpened) PreferencesManager.getLidOpenAction(context) else null
            val lastPkg = launcherVm.lastLaunchedPackage
            val resumeIntent = if (action == LidOpenAction.RESUME_LAST_APP && !lastPkg.isNullOrEmpty() && lastPkg != context.packageName) {
                context.packageManager.getLaunchIntentForPackage(lastPkg)
            } else null
            val resumed = resumeIntent != null && try {
                context.startActivity(resumeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: Exception) {
                false
            }
            if (!resumed) {
                val targetPage = when (action) {
                    LidOpenAction.NOTIFICATIONS -> 2
                    null -> null
                    else -> 1
                }
                if (targetPage != null) {
                    pendingTargetPage = targetPage
                    pagerState.scrollToPage(targetPage)
                }
                if (action == LidOpenAction.QUICK_MEMO) {
                    activeModal = ActiveModal.QuickMemo
                }
                homeTracker.wasActive = true
                homeTracker.settledTimestamp = SystemClock.uptimeMillis()
                focusRequester.requestFocus()
            }
        } else if (lidOpenCount > 0) {
            lidClosedSinceOpen = false
        }
    }

    LaunchedEffect(isHomeScreenActive) {
        val returnToMascot = restoreMascotFocus
        restoreMascotFocus = false
        if (isHomeScreenActive && returnToMascot) {
            // Pet pill just closed: Home is active again with Mochibunny still focused
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
            homeFocusArea = HOME_FOCUS_MASCOT
        } else if (isHomeScreenActive) {
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

    // '#' hold (Quick Memo on Home) is timed by LauncherKeyHandler's poundHoldJob.

    // "Open Quick Memo" key remap action (MainActivity.EXTRA_OPEN_QUICK_MEMO): show it over Home
    LaunchedEffect(quickMemoRequestCount) {
        if (quickMemoRequestCount > 0) {
            drawerOpen = false
            poundKeyState = 0
            activeModal = ActiveModal.QuickMemo
            if (pagerState.currentPage != 1) {
                pendingTargetPage = 1
                pagerState.scrollToPage(1)
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
            override fun openQuickMemo() { activeModal = ActiveModal.QuickMemo }
            override fun cycleDrawerSortMode() {
                val nextMode = launcherVm.cycleAppDrawerSortMode()
                SoundEffectManager.play(context, SoundEffect.CLICK)
                Toast.makeText(context, "♡ Sorted: ${nextMode.toastName}", Toast.LENGTH_SHORT).show()
            }
            override fun recordAppLaunch(packageName: String) {
                launcherVm.recordAppLaunch(packageName)
            }
            override fun dismissModal() { activeModal = null }
            override fun openPetPill() = openPetPillModal()
            override fun setPetPillSelection(index: Int) { petPillSelection = index }
            override fun activatePetPillOption(index: Int) = runPetPillOption(index)
            override fun closePetPill() = closePetPillModal()
            override fun onPetPillInteraction() { petPillInteractionTick++ }
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
            currentPage = pagerState.currentPage,
            // Text fields only exist inside modals. Requiring one keeps a stale flag (a dialog
            // closed without a focus event) from silencing digits on Home and in the drawer.
            isInputActive = InputArbiter.isInputActive && activeModal != null,
            isPetPillOpen = activeModal is ActiveModal.PetPill,
            petPillSelectedIndex = petPillSelection
        )
    }

    // Wrap keypad state evaluation and keyHandler in rememberUpdatedState to prevent stale snapshot reads
    val currentKeyHandler by rememberUpdatedState(keyHandler)
    val currentKeypadStateProvider by rememberUpdatedState(::getCurrentKeypadState)

    // Keep the in-app keypad path active whenever foreground; it is the fallback when the
    // system_server hook is unavailable or disabled.
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
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                MainActivity.keyEventInterceptor = null
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            MainActivity.keyEventInterceptor = listener
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            MainActivity.keyEventInterceptor = null
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
            SystemUiThemeBridge.broadcastTheme(context, newTheme)
            activeModal = null
        }
    }
    val onOpenQuickMemo: () -> Unit = remember {
        { activeModal = ActiveModal.QuickMemo }
    }
    val onDismissQuickMemo = remember {
        {
            activeModal = null
            poundKeyState = 0
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
        MochibunSystemBarsEffect()
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
                    is ActiveModal.PetPill -> closePetPillModal()
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
                                isHomeVisible = isHomeVisible,
                                isPetPillOpen = isPetPillOpen,
                                petPillSelection = petPillSelection,
                                mascotReaction = mascotReaction,
                                onMascotClick = {
                                    if (activeModal is ActiveModal.PetPill) closePetPillModal() else openPetPillModal()
                                },
                                onPetPillOptionClick = { index -> runPetPillOption(index) },
                                onDismissPetPill = { closePetPillModal() },
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
                                },
                                onFavoritePageChange = { page ->
                                    // Swipe / page-dot tap: select the first slot of that page so the
                                    // D-pad continues from where the card is now showing.
                                    val firstIdx = firstFavoriteIndexOfPage(page, favoriteItems.size)
                                    selectedFavoriteIndex = firstIdx
                                    savedFavoriteIndexOnHome = firstIdx
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
                is ActiveModal.SettingsHub, is ActiveModal.KeyboardSettings -> {
                    SettingsHubDialog(
                        initialDestination = if (activeModal is ActiveModal.KeyboardSettings) {
                            SettingsDestination.Keyboard
                        } else {
                            SettingsDestination.Hub
                        },
                        onDismiss = dismissActiveModal,
                        onOpenThemeStudio = onOpenThemeStudio,
                        onOpenIconPackDialog = onOpenIconPackDialog,
                        onOpenHardwareButtons = onOpenHardwareButtons,
                        onOpenHiddenApps = onOpenHiddenApps,
                        onCreateFolderClick = onCreateFolderClick,
                        onPickWallpaperClick = onPickWallpaperClick,
                        onResetWallpaperClick = onResetWallpaperClick,
                        hasCustomWallpaper = launcherVm.wallpaperUri != null,
                        onSettingsRestored = onSettingsRestored,
                        onOpenQuickMemo = onOpenQuickMemo
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

                is ActiveModal.QuickMemo -> {
                    // poundKeyState == 2: opened by a '#' hold that may still be down
                    val openedByPoundHold = remember { poundKeyState == 2 }
                    QuickMemoDialog(
                        onDismiss = onDismissQuickMemo,
                        waitForPoundRelease = openedByPoundHold
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
                                        SystemUiThemeBridge.broadcastTheme(context, currentTheme)
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

                is ActiveModal.PetPill -> {
                    // Drawn inside HomeScreen, next to Mochibunny (not a dialog window)
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
