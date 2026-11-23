package one.crinklecod.mochibun.ui

import one.crinklecod.mochibun.service.VirtualCursorService
import one.crinklecod.mochibun.core.system.ClamshellManager
import one.crinklecod.mochibun.data.prefs.LidOpenAction
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import one.crinklecod.mochibun.ui.components.WallpaperOverlay
import one.crinklecod.mochibun.core.keypad.HOME_FOCUS_MASCOT
import one.crinklecod.mochibun.core.keypad.LauncherKeypadState
import one.crinklecod.mochibun.core.keypad.PET_PILL_AUTO_CLOSE_MS
import one.crinklecod.mochibun.core.keypad.PET_PILL_FEED
import one.crinklecod.mochibun.core.keypad.PET_PILL_PET
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.model.FavoriteSlotItem
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.LidLedAnimationRepository
import one.crinklecod.mochibun.data.repository.ExternalThemeManager
import one.crinklecod.mochibun.data.repository.ThemeBundleManager
import one.crinklecod.mochibun.ui.components.WidgetPage
import one.crinklecod.mochibun.ui.components.MascotReaction
import one.crinklecod.mochibun.ui.components.MascotReactionKind
import one.crinklecod.mochibun.pet.FeedResult
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.ui.dialogs.awaitFocusSafely
import one.crinklecod.mochibun.ui.screens.AppDrawerScreen
import one.crinklecod.mochibun.ui.screens.firstFavoriteIndexOfPage
import one.crinklecod.mochibun.ui.screens.HomeScreen
import one.crinklecod.mochibun.ui.screens.NotificationsScreen
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.MochibunSystemBarsEffect
import one.crinklecod.mochibun.ui.theme.loadSavedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext


internal const val LID_OPEN_ACTION_FRESHNESS_MS = 4_000L

internal fun isPendingOpenActionFresh(
    nowUptimeMs: Long,
    pendingActionUptimeMs: Long,
    lastUnlockUptimeMs: Long,
    lastUserHomePressUptimeMs: Long,
    maxAgeMs: Long = LID_OPEN_ACTION_FRESHNESS_MS
): Boolean {
    val referenceTime = maxOf(pendingActionUptimeMs, lastUnlockUptimeMs)
    if (lastUserHomePressUptimeMs > referenceTime) return false
    val ageMs = (nowUptimeMs - referenceTime).coerceAtLeast(0L)
    return ageMs <= maxAgeMs
}

@Composable
fun MainLauncher(
    viewModel: LauncherViewModel? = null,
    homePressCount: Int = 0,
    homePressedWhileVisible: Boolean = false,
    appListRefreshCount: Int = 0,
    lidOpenCount: Int = 0,
    lidClosedCount: Int = 0,
    screenOffResetCount: Int = 0,
    unlockCount: Int = 0,
    isActivityResumed: Boolean = true,
    isKeyguardLocked: Boolean = false,
    lastUnlockUptimeMs: Long = 0L,
    lastUserHomePressUptimeMs: Long = 0L,
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

    val nav = rememberLauncherNavigationState()
    var activeModal by nav.activeModalState
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

    val themeState = remember {
        mutableStateOf(loadSavedTheme(preferences))
    }
    var currentTheme by themeState
    val activeBorderStyle by one.crinklecod.mochibun.data.repository.CustomCardBorderManager.activeBorderStyle.collectAsState()
    LaunchedEffect(activeBorderStyle) {
        if (currentTheme.borderStyle != activeBorderStyle.id) {
            currentTheme = currentTheme.copy(borderStyle = activeBorderStyle.id)
        }
    }
    val themeOptions by ExternalThemeManager.themes.collectAsState()
    LaunchedEffect(themeOptions) {
        val savedPreset = preferences.getString(PreferencesManager.KEY_THEME_PRESET, null)
        if (savedPreset != null && currentTheme.name != savedPreset) {
            val matching = ExternalThemeManager.findTheme(savedPreset)
            if (matching != null) {
                currentTheme = matching.theme
                one.crinklecod.mochibun.data.repository.CustomCardBorderManager.setBorderStyle(
                    context,
                    matching.theme.borderStyle
                )
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

    var drawerOpen by nav.drawerOpenState
    // Active pages: 0 = Widgets, 1 = Home, 2 = Notifications
    val pagerState = nav.pagerState
    var pendingTargetPage by nav.pendingTargetPageState
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

    val visibleApps = launcherVm.visibleApps
    val letterStarts = launcherVm.letterStarts
    val favoriteItems = launcherVm.favoriteItems

    val favoriteItemCount = favoriteItems.size
    var selectedFavoriteIndex by nav.selectedFavoriteIndexState
    var savedFavoriteIndexOnHome by nav.savedFavoriteIndexOnHomeState
    val homeTracker = nav.homeTracker
    var selectedDrawerIndex by nav.selectedDrawerIndexState
    var drawerRailFocused by nav.drawerRailFocusedState
    var drawerRailHoveredLetter by nav.drawerRailHoveredLetterState
    // Home focus area: 0 = FavoritesCard row, 1 = StatusCard (launcher options / page switcher)
    var homeFocusArea by nav.homeFocusAreaState
    // Mochibunny pet pill: selected option, auto-close timer key, and the latest reaction for her
    var petPillSelection by remember { mutableIntStateOf(PET_PILL_FEED) }
    var petPillInteractionTick by remember { mutableIntStateOf(0) }
    var showPetStats by remember { mutableStateOf(false) }
    var mascotReaction by remember { mutableStateOf(MascotReaction()) }
    // Set when the pill closes back to Home, so the focus reset below keeps Mochibunny focused
    var restoreMascotFocus by remember { mutableStateOf(false) }

    // 0 = center not pressed, 1 = pressed, 2 = held long (menu already opened)
    var centerKeyState by nav.centerKeyPressState
    var poundKeyState by nav.poundKeyPressState
    val t9State = nav.t9State
    val focusRequester = nav.focusRequester

    val anyModalOpen = nav.anyModalOpen

    // snapshotFlow, not LaunchedEffect keys: reading settledPage / isScrollInProgress as keys in
    // this body would recompose the whole launcher root on every swipe. pendingTargetPage is read
    // inside collect (untracked) so it fires on the same transitions as before.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }
            .collect { (settledPage, scrolling) ->
                if (!scrolling && (pendingTargetPage == null || pendingTargetPage == settledPage)) {
                    pendingTargetPage = null
                }
            }
    }

    val isHomeScreenActive = nav.isHomeScreenActive

    val isSidePageActive = nav.isSidePageActive

    // Home is on screen with at most the pet pill over it. Unlike isHomeScreenActive (false while
    // the pill, a modal, owns the keys), this keeps Home visuals alive: mascot glow & bob, ticker.
    val isPetPillOpen = nav.isPetPillOpen
    val isHomeVisible = nav.isHomeVisible

    fun openPetPillModal() {
        if (activeModal != null || drawerOpen) return
        PetManager.refresh()
        homeFocusArea = HOME_FOCUS_MASCOT
        petPillSelection = PET_PILL_FEED
        showPetStats = false
        petPillInteractionTick++
        activeModal = ActiveModal.PetPill
    }

    // Back, tap outside, or the auto-close timer: Home focus goes back to Mochibunny.
    // (Lid close, Home press and screen-off reset clear activeModal directly and keep their own reset.)
    fun closePetPillModal() {
        if (activeModal !is ActiveModal.PetPill) return
        activeModal = null
        showPetStats = false
        homeFocusArea = HOME_FOCUS_MASCOT
        restoreMascotFocus = true
    }

    fun runPetPillOption(index: Int) {
        if (activeModal !is ActiveModal.PetPill) return
        petPillSelection = index
        petPillInteractionTick++
        val kind = when (index) {
            PET_PILL_FEED -> when (PetManager.tryFeed()) {
                FeedResult.FED -> {
                    SoundEffectManager.play(context, SoundEffect.FEED)
                    MascotReactionKind.FED
                }
                FeedResult.ALREADY_FULL -> MascotReactionKind.ALREADY_FULL
                FeedResult.NO_TREATS -> MascotReactionKind.NO_TREATS
            }
            PET_PILL_PET -> {
                PetManager.pet()
                SoundEffectManager.play(context, SoundEffect.PET)
                MascotReactionKind.PETTED
            }
            else -> {
                showPetStats = true
                MascotReactionKind.CHAT
            }
        }
        mascotReaction = MascotReaction(id = mascotReaction.id + 1, kind = kind)
    }

    // Clear showPetStats whenever the pill closes through another reset path (lid close, Home, screen off)
    LaunchedEffect(isPetPillOpen) {
        if (!isPetPillOpen) showPetStats = false
    }

    // Catch her up on decay, grant today's check-in treat, and show +N 🍡 if treats were earned
    val pendingEarnedTreats by PetManager.pendingEarnedTreats.collectAsState()
    LaunchedEffect(isHomeVisible, pendingEarnedTreats) {
        if (isHomeVisible) {
            PetManager.refresh()
            PetManager.claimDailyCheckIn()
            val earned = PetManager.consumePendingEarnedTreats()
            if (earned > 0) {
                mascotReaction = MascotReaction(
                    id = mascotReaction.id + 1,
                    kind = MascotReactionKind.TREATS_EARNED,
                    earnedCount = earned
                )
            }
        }
    }

    // Auto-close after PET_PILL_AUTO_CLOSE_MS without a key or tap (every interaction bumps the tick)
    LaunchedEffect(isPetPillOpen, petPillInteractionTick) {
        if (isPetPillOpen) {
            delay(PET_PILL_AUTO_CLOSE_MS)
            closePetPillModal()
        }
    }

    // Process-wide clamshell sequences from ClamshellManager so MainActivity never misses a close
    // or open that occurred before MainActivity was created or while Keyguard was locked.
    val lidCloseSeq by ClamshellManager.lidCloseSequence.collectAsState()
    val openAwakeSeq by ClamshellManager.openAwakeSequence.collectAsState()
    val pendingOpenActionSeq by ClamshellManager.pendingOpenActionSequence.collectAsState()
    val initialCloseSeq = remember { lidCloseSeq }
    var handledCloseSeq by remember { mutableIntStateOf(0) }
    var handledWakeSeq by remember { mutableIntStateOf(openAwakeSeq) }
    var handledOpenActionSeq by remember { mutableIntStateOf(0) }

    // Flip physically closed (never a display timeout): dismiss transient modals, collapse folder/drawer, reset T9 state, and position on Home
    LaunchedEffect(lidCloseSeq, lidClosedCount) {
        if (lidCloseSeq > handledCloseSeq || lidClosedCount > 0) {
            val playSnap = lidCloseSeq > initialCloseSeq || lidClosedCount > 0
            handledCloseSeq = lidCloseSeq
            if (playSnap) SoundEffectManager.playSnap(context)
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

    // Non-root Screen-Off Flip Reset: same clean slate as a detected flip close, minus the snap sound
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

    // Entered OPEN_AWAKE: wake focus recovery for physical D-pad contracts.
    LaunchedEffect(openAwakeSeq, lidOpenCount) {
        if ((openAwakeSeq > handledWakeSeq || lidOpenCount > 0) && !anyModalOpen) {
            handledWakeSeq = openAwakeSeq
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
            focusRequester.requestFocus()
        } else if (openAwakeSeq > handledWakeSeq) {
            handledWakeSeq = openAwakeSeq
        }
    }

    // Flip open / non-root wake action: waits until MainActivity is resumed and Keyguard is unlocked
    // so Android 12 never silently blocks startActivity() behind the lockscreen or background state,
    // and waits for any pending close-reset to clear activeModal first.
    LaunchedEffect(
        pendingOpenActionSeq,
        handledCloseSeq,
        lidCloseSeq,
        isActivityResumed,
        isKeyguardLocked,
        unlockCount,
        activeModal
    ) {
        if (pendingOpenActionSeq <= handledOpenActionSeq) return@LaunchedEffect
        if (handledCloseSeq < lidCloseSeq) return@LaunchedEffect
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val currentlyLocked = isKeyguardLocked || (keyguardManager?.isKeyguardLocked == true)
        if (!isActivityResumed || currentlyLocked) return@LaunchedEffect

        handledOpenActionSeq = pendingOpenActionSeq
        val fresh = isPendingOpenActionFresh(
            nowUptimeMs = SystemClock.uptimeMillis(),
            pendingActionUptimeMs = ClamshellManager.lastPendingOpenActionUptimeMs,
            lastUnlockUptimeMs = lastUnlockUptimeMs,
            lastUserHomePressUptimeMs = lastUserHomePressUptimeMs
        )
        if (!fresh || activeModal != null) return@LaunchedEffect

        val action = PreferencesManager.getLidOpenAction(context)
        val lastPkg = launcherVm.lastLaunchedPackage ?: PreferencesManager.getLastLaunchedPackage(context)
        val resumeIntent = if (
            action == LidOpenAction.RESUME_LAST_APP &&
            !lastPkg.isNullOrEmpty() &&
            lastPkg != context.packageName
        ) {
            context.packageManager.getLaunchIntentForPackage(lastPkg)
        } else null
        val resumed = resumeIntent != null && try {
            context.startActivity(resumeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
        if (!resumed) {
            if (action == LidOpenAction.NOTIFICATIONS || action == LidOpenAction.QUICK_MEMO) {
                drawerOpen = false
                drawerRailFocused = false
            }
            val targetPage = when (action) {
                LidOpenAction.NOTIFICATIONS -> 2
                LidOpenAction.QUICK_MEMO, LidOpenAction.RESUME_LAST_APP -> 1
                LidOpenAction.HOME -> null
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
    }

    LaunchedEffect(isHomeScreenActive) {
        val returnToMascot = restoreMascotFocus
        restoreMascotFocus = false
        if (isHomeScreenActive) {
            homeTracker.wasActive = true
            homeTracker.settledTimestamp = SystemClock.uptimeMillis()
        }
        if (isHomeScreenActive && returnToMascot) {
            // Pet pill just closed: keep Mochibunny focused
            homeFocusArea = HOME_FOCUS_MASCOT
        } else if (isHomeScreenActive) {
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

    // Keep the focused drawer card on screen. selectedDrawerIndex is observed with snapshotFlow
    // rather than used as an effect key: a key read here recomposed the whole launcher root on
    // every D-pad step / T9 jump in the drawer. collectLatest cancels an in-flight scroll when the
    // next step arrives, like the old effect restart did.
    LaunchedEffect(drawerOpen, visibleApps.size) {
        if (!drawerOpen) return@LaunchedEffect
        snapshotFlow { nav.selectedDrawerIndex }.collectLatest { selectedDrawerIndex ->
            if (visibleApps.isNotEmpty() && selectedDrawerIndex in visibleApps.indices) {
                val layoutInfo = listState.layoutInfo
                val clearBottom = (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)
                    .coerceAtLeast(0)
                val selectedItem = layoutInfo.visibleItemsInfo.firstOrNull {
                    it.index == selectedDrawerIndex
                }
                if (selectedItem != null) {
                    val itemEnd = selectedItem.offset + selectedItem.size
                    if (selectedItem.offset < 0) {
                        listState.scrollBy(selectedItem.offset.toFloat())
                    } else if (itemEnd > clearBottom) {
                        listState.scrollBy((itemEnd - clearBottom).toFloat())
                    }
                } else {
                    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                    if (lastVisible != null &&
                        selectedDrawerIndex > lastVisible.index &&
                        selectedDrawerIndex - lastVisible.index <= 2
                    ) {
                        // Use scrollBy so LazyColumn keeps item prefetching active on 1-row steps
                        val delta = selectedDrawerIndex - lastVisible.index
                        val stepPx = lastVisible.size + layoutInfo.mainAxisItemSpacing
                        val estimatedBottom = lastVisible.offset + lastVisible.size + delta * stepPx
                        val scrollDelta = (estimatedBottom - clearBottom).toFloat()
                        if (scrollDelta > 0f) {
                            listState.scrollBy(scrollDelta)
                        } else {
                            listState.scrollToItem(
                                index = selectedDrawerIndex,
                                scrollOffset = -(clearBottom - lastVisible.size).coerceAtLeast(0)
                            )
                        }
                    } else if (firstVisible != null &&
                        selectedDrawerIndex < firstVisible.index &&
                        firstVisible.index - selectedDrawerIndex <= 2
                    ) {
                        val delta = firstVisible.index - selectedDrawerIndex
                        val stepPx = firstVisible.size + layoutInfo.mainAxisItemSpacing
                        val scrollDelta = (firstVisible.offset - delta * stepPx).toFloat()
                        if (scrollDelta < 0f) {
                            listState.scrollBy(scrollDelta)
                        } else {
                            listState.scrollToItem(selectedDrawerIndex)
                        }
                    } else {
                        listState.scrollToItem(selectedDrawerIndex)
                    }
                }
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

    val keypadActions = remember(launcherVm) {
        LauncherNavigationKeypadActions(
            nav = nav,
            launcherVm = launcherVm,
            context = context,
            onOpenContextAction = openContextActionForCurrentFocus,
            onOpenPetPill = ::openPetPillModal,
            onSetPetPillSelection = { index -> petPillSelection = index },
            onActivatePetPillOption = ::runPetPillOption,
            onClosePetPill = ::closePetPillModal,
            onPetPillInteractionTick = { petPillInteractionTick++ }
        )
    }

    val keyHandler = rememberLauncherKeyHandler(context, scope, nav, listState, keypadActions)

    fun getCurrentKeypadState(): LauncherKeypadState =
        nav.buildKeypadState(isHomeScreenActive, launcherVm, petPillSelection)

    LauncherKeyEventInterceptorEffect(keyHandler, ::getCurrentKeypadState)

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
            LauncherBackHandlers(
                nav = nav,
                launcherVm = launcherVm,
                favoriteItems = favoriteItems,
                isSidePageActive = isSidePageActive,
                anyModalOpen = anyModalOpen,
                scope = scope,
                onClosePetPill = ::closePetPillModal,
                onConsumeIncomingBundleUri = onConsumeIncomingBundleUri
            )

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
            LaunchedEffect(pagerState, keyboardController) {
                snapshotFlow { pagerState.currentPage }.collect { keyboardController?.hide() }
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
                    // Keyed on Unit and reading nav live: keying on currentPage restarted the
                    // detector mid-swipe and subscribed the root to page changes.
                    .pointerInput(nav) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (!nav.drawerOpen && nav.isHomeScreenActive && dragAmount < -32) {
                                nav.selectedDrawerIndex = 0
                                nav.drawerOpen = true
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
                                showPetStats = showPetStats,
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

            LauncherModalHost(
                nav = nav,
                launcherVm = launcherVm,
                themeState = themeState,
                preferences = preferences,
                scope = scope,
                cardRoundness = cardRoundness,
                onPickWallpaperClick = onPickWallpaperClick,
                onConsumeIncomingBundleUri = onConsumeIncomingBundleUri
            )

            val currentVisibleApps by rememberUpdatedState(visibleApps)
            // Read lazily (callbacks / AppDrawerScreen scopes) so drawer focus moves never
            // invalidate this root composable
            val drawerIndexProvider = remember(nav) { { nav.selectedDrawerIndex } }
            val currentLetterStarts by rememberUpdatedState(letterStarts)
            val onDrawerOptionsClick = remember(launcherVm) {
                {
                    currentVisibleApps.getOrNull(nav.selectedDrawerIndex)?.let { app ->
                        activeModal = ActiveModal.AppAction(app)
                    }
                    Unit
                }
            }
            val onDrawerCycleSortClick = remember(context, launcherVm) {
                {
                    val nextMode = launcherVm.cycleAppDrawerSortMode()
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    Toast.makeText(context, "♡ Sorted: ${nextMode.toastName}", Toast.LENGTH_SHORT).show()
                }
            }
            val onDrawerAppClick = remember(context, launcherVm) {
                { app: LauncherApp ->
                    selectedDrawerIndex =
                        currentVisibleApps.indexOfFirst { it.componentKey == app.componentKey }
                    launcherVm.recordAppLaunch(app.packageName)
                    val launchIntent =
                        context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                }
            }
            val onDrawerAppLongClick = remember {
                { app: LauncherApp ->
                    selectedDrawerIndex =
                        currentVisibleApps.indexOfFirst { it.componentKey == app.componentKey }
                    activeModal = ActiveModal.AppAction(app)
                }
            }
            val onDrawerBackToHome = remember {
                {
                    drawerRailFocused = false
                    drawerOpen = false
                }
            }
            val onDrawerLetterTap = remember(scope, listState) {
                { letter: Char ->
                    drawerRailHoveredLetter = letter
                    val apps = currentVisibleApps
                    val starts = currentLetterStarts
                    val target = starts[letter]
                        ?: starts.entries.firstOrNull { it.key > letter }?.value
                        ?: (apps.size - 1)
                    if (target >= 0 && apps.isNotEmpty()) {
                        selectedDrawerIndex = target
                        scope.launch { listState.scrollToItem(target) }
                    }
                }
            }

            if (drawerOpen) {
                AppDrawerScreen(
                    visibleApps = visibleApps,
                    selectedDrawerIndex = drawerIndexProvider,
                    letterStarts = letterStarts,
                    isRailFocused = drawerRailFocused,
                    hoveredRailLetter = drawerRailHoveredLetter,
                    sortMode = launcherVm.appDrawerSortMode,
                    listState = listState,
                    onOptionsClick = onDrawerOptionsClick,
                    onCycleSortClick = onDrawerCycleSortClick,
                    onAppClick = onDrawerAppClick,
                    onAppLongClick = onDrawerAppLongClick,
                    onBackToHome = onDrawerBackToHome,
                    isModalOpen = anyModalOpen,
                    onLetterTap = onDrawerLetterTap
                )
            }
        }
    }
}
