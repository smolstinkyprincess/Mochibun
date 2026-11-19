package one.crinklecod.mochibun.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import one.crinklecod.mochibun.core.keypad.HomeTransitionTracker
import one.crinklecod.mochibun.core.keypad.LauncherKeypadActions
import one.crinklecod.mochibun.core.keypad.LauncherKeypadState
import one.crinklecod.mochibun.core.keypad.T9InputState
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.model.FavoriteSlotItem
import one.crinklecod.mochibun.service.InputArbiter

/**
 * Navigation / focus state shared by [MainLauncher], [LauncherKeyHandler], the Back arbiter and
 * [LauncherModalHost]: which modal is open, drawer & pager position, and D-pad selection.
 *
 * Each value is exposed both as its backing [MutableState] (so composables can write
 * `var drawerOpen by nav.drawerOpenState` and keep reading/writing it like a local) and as a
 * plain property for non-composable callers such as [LauncherNavigationKeypadActions].
 */
@Stable
internal class LauncherNavigationState(
    /** Active pages: 0 = Widgets, 1 = Home, 2 = Notifications */
    val pagerState: PagerState
) {
    val activeModalState: MutableState<ActiveModal?> = mutableStateOf(null)
    var activeModal by activeModalState

    val drawerOpenState: MutableState<Boolean> = mutableStateOf(false)
    var drawerOpen by drawerOpenState

    val pendingTargetPageState: MutableState<Int?> = mutableStateOf(null)
    var pendingTargetPage by pendingTargetPageState

    val selectedFavoriteIndexState: MutableIntState = mutableIntStateOf(0)
    var selectedFavoriteIndex by selectedFavoriteIndexState

    val savedFavoriteIndexOnHomeState: MutableIntState = mutableIntStateOf(0)
    var savedFavoriteIndexOnHome by savedFavoriteIndexOnHomeState

    val selectedDrawerIndexState: MutableIntState = mutableIntStateOf(0)
    var selectedDrawerIndex by selectedDrawerIndexState

    val drawerRailFocusedState: MutableState<Boolean> = mutableStateOf(false)
    var drawerRailFocused by drawerRailFocusedState

    val drawerRailHoveredLetterState: MutableState<Char?> = mutableStateOf('A')
    var drawerRailHoveredLetter by drawerRailHoveredLetterState

    /** Home focus area: 0 = FavoritesCard row, 1 = StatusCard (launcher options / page switcher) */
    val homeFocusAreaState: MutableIntState = mutableIntStateOf(0)
    var homeFocusArea by homeFocusAreaState

    /** 0 = center not pressed, 1 = pressed, 2 = held long (menu already opened) */
    val centerKeyPressState: MutableIntState = mutableIntStateOf(0)
    var centerKeyState by centerKeyPressState

    val poundKeyPressState: MutableIntState = mutableIntStateOf(0)
    var poundKeyState by poundKeyPressState

    val homeTracker = HomeTransitionTracker()
    val t9State = T9InputState()
    val focusRequester = FocusRequester()

    val anyModalOpen: Boolean
        get() = activeModal != null

    val isPetPillOpen: Boolean
        get() = activeModal is ActiveModal.PetPill

    // The three flags below are derivedStateOf-backed: MainLauncher reads them in its body, and a
    // plain getter would subscribe it to currentPage / targetPage / settledPage / isScrollInProgress,
    // recomposing the whole launcher root 4-6 times per swipe. Derived, the root only recomposes
    // when the Boolean itself flips (MT6761 JIT: each root pass costs whole frames).

    private val isHomeScreenActiveState = derivedStateOf {
        !drawerOpen &&
            !anyModalOpen &&
            (pendingTargetPage == null || pendingTargetPage == 1) &&
            pagerState.currentPage == 1 &&
            pagerState.targetPage == 1 &&
            pagerState.settledPage == 1 &&
            !pagerState.isScrollInProgress
    }

    private val isSidePageActiveState = derivedStateOf {
        !drawerOpen &&
            !anyModalOpen &&
            (pagerState.currentPage == 0 || pagerState.currentPage == 2)
    }

    private val isHomeVisibleState = derivedStateOf {
        !drawerOpen &&
            (activeModal == null || isPetPillOpen) &&
            (pendingTargetPage == null || pendingTargetPage == 1) &&
            pagerState.currentPage == 1 &&
            pagerState.targetPage == 1 &&
            pagerState.settledPage == 1 &&
            !pagerState.isScrollInProgress
    }

    /** Home owns the keypad: settled on page 1 with no drawer, modal or page transition. */
    val isHomeScreenActive: Boolean
        get() = isHomeScreenActiveState.value

    val isSidePageActive: Boolean
        get() = isSidePageActiveState.value

    /**
     * Home is on screen with at most the pet pill over it. Unlike [isHomeScreenActive] (false while
     * the pill, a modal, owns the keys), this keeps Home visuals alive: mascot glow & bob, ticker.
     */
    val isHomeVisible: Boolean
        get() = isHomeVisibleState.value

    /**
     * Snapshot handed to [LauncherKeyHandler] for every key event. [isHomeScreenActive] is passed in
     * (the value from the latest composition) rather than re-derived, matching the original wiring.
     */
    fun buildKeypadState(
        isHomeScreenActive: Boolean,
        launcherVm: LauncherViewModel,
        petPillSelection: Int
    ): LauncherKeypadState {
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
}

@Composable
internal fun rememberLauncherNavigationState(): LauncherNavigationState {
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    return remember(pagerState) { LauncherNavigationState(pagerState) }
}

/**
 * [LauncherKeypadActions] backed by [LauncherNavigationState]. Pet pill and context-menu actions
 * are owned by [MainLauncher] (they touch Home-only state) and passed in as callbacks.
 */
internal class LauncherNavigationKeypadActions(
    private val nav: LauncherNavigationState,
    private val launcherVm: LauncherViewModel,
    private val context: Context,
    private val onOpenContextAction: () -> Unit,
    private val onOpenPetPill: () -> Unit,
    private val onSetPetPillSelection: (Int) -> Unit,
    private val onActivatePetPillOption: (Int) -> Unit,
    private val onClosePetPill: () -> Unit,
    private val onPetPillInteractionTick: () -> Unit
) : LauncherKeypadActions {
    override fun setDrawerOpen(open: Boolean) {
        nav.drawerOpen = open
        if (!open) {
            nav.drawerRailFocused = false
        }
    }
    override fun setSelectedDrawerIndex(index: Int) { nav.selectedDrawerIndex = index }
    override fun setHomeFocusArea(area: Int) { nav.homeFocusArea = area }
    override fun setSelectedFavoriteIndex(index: Int) { nav.selectedFavoriteIndex = index }
    override fun setSavedFavoriteIndexOnHome(index: Int) { nav.savedFavoriteIndexOnHome = index }
    override fun setPendingTargetPage(page: Int?) { nav.pendingTargetPage = page }
    override fun setCenterKeyState(state: Int) { nav.centerKeyState = state }
    override fun setDrawerRailFocused(focused: Boolean) { nav.drawerRailFocused = focused }
    override fun setDrawerRailHoveredLetter(letter: Char?) { nav.drawerRailHoveredLetter = letter }
    override fun openContextActionForCurrentFocus() = onOpenContextAction()
    override fun openFolder(folderId: String) {
        nav.activeModal = ActiveModal.FolderView(
            folderId = folderId,
            startRename = false,
            startPicker = false
        )
    }
    override fun openLauncherMenu() { nav.activeModal = ActiveModal.SettingsHub }
    override fun openSpeedDial(digit: Int) {
        nav.activeModal = ActiveModal.HardwareButtons(
            initialKeyCode = android.view.KeyEvent.KEYCODE_0 + digit
        )
    }
    override fun setPoundKeyState(state: Int) { nav.poundKeyState = state }
    override fun openKaomojiWheel() { nav.activeModal = ActiveModal.KaomojiWheel }
    override fun openQuickMemo() { nav.activeModal = ActiveModal.QuickMemo }
    override fun cycleDrawerSortMode() {
        val nextMode = launcherVm.cycleAppDrawerSortMode()
        SoundEffectManager.play(context, SoundEffect.CLICK)
        Toast.makeText(context, "♡ Sorted: ${nextMode.toastName}", Toast.LENGTH_SHORT).show()
    }
    override fun recordAppLaunch(packageName: String) {
        launcherVm.recordAppLaunch(packageName)
    }
    override fun dismissModal() { nav.activeModal = null }
    override fun openPetPill() = onOpenPetPill()
    override fun setPetPillSelection(index: Int) = onSetPetPillSelection(index)
    override fun activatePetPillOption(index: Int) = onActivatePetPillOption(index)
    override fun closePetPill() = onClosePetPill()
    override fun onPetPillInteraction() = onPetPillInteractionTick()
}

@Composable
internal fun rememberLauncherKeyHandler(
    context: Context,
    scope: CoroutineScope,
    nav: LauncherNavigationState,
    listState: LazyListState,
    keypadActions: LauncherKeypadActions
): LauncherKeyHandler {
    val pagerState = nav.pagerState
    val homeTracker = nav.homeTracker
    val t9State = nav.t9State
    return remember(context, scope, pagerState, listState, homeTracker, t9State, keypadActions) {
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
}

/**
 * Keeps the in-app keypad path active whenever foreground; it is the fallback when the
 * system_server hook is unavailable or disabled.
 *
 * Non-skippable so the rememberUpdatedState wrappers refresh on every [MainLauncher] recomposition,
 * exactly as when this lived inline (the provider closes over composition-time values).
 */
@NonSkippableComposable
@Composable
internal fun LauncherKeyEventInterceptorEffect(
    keyHandler: LauncherKeyHandler,
    keypadStateProvider: () -> LauncherKeypadState
) {
    // Wrap keypad state evaluation and keyHandler in rememberUpdatedState to prevent stale snapshot reads
    val currentKeyHandler by rememberUpdatedState(keyHandler)
    val currentKeypadStateProvider by rememberUpdatedState(keypadStateProvider)

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
}

/**
 * Back arbiter: the unified modal back handler, then the side-page "return to Home" handler.
 * Registration order matters (the later handler wins while both are enabled), so keep this order.
 */
@NonSkippableComposable
@Composable
internal fun LauncherBackHandlers(
    nav: LauncherNavigationState,
    launcherVm: LauncherViewModel,
    favoriteItems: List<FavoriteSlotItem>,
    isSidePageActive: Boolean,
    anyModalOpen: Boolean,
    scope: CoroutineScope,
    onClosePetPill: () -> Unit,
    onConsumeIncomingBundleUri: () -> Unit
) {
    var activeModal by nav.activeModalState
    var selectedFavoriteIndex by nav.selectedFavoriteIndexState
    var savedFavoriteIndexOnHome by nav.savedFavoriteIndexOnHomeState
    var homeFocusArea by nav.homeFocusAreaState
    var poundKeyState by nav.poundKeyPressState
    var pendingTargetPage by nav.pendingTargetPageState
    val drawerOpen by nav.drawerOpenState
    val pagerState = nav.pagerState

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
            is ActiveModal.PetPill -> onClosePetPill()
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
}
