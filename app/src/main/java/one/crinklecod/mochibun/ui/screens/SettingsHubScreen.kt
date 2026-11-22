package one.crinklecod.mochibun.ui.screens

import one.crinklecod.mochibun.data.model.LauncherApp
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.ui.MainActivity
import one.crinklecod.mochibun.ui.components.curtainUnfurl
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.screens.settings.*

/**
 * Navigation destination tiers for the Mochibun Settings Hub.
 */
sealed class SettingsDestination {
    object Hub : SettingsDestination()
    object Appearance : SettingsDestination()
    object AppearanceThemePacks : SettingsDestination()
    object AppearanceTypographyBorders : SettingsDestination()
    object AppearanceGreetingTicker : SettingsDestination()
    object AppearanceWallpaperDeco : SettingsDestination()
    object AppearanceWidgetsCards : SettingsDestination()
    object CursorHardware : SettingsDestination()
    object CursorMotion : SettingsDestination()
    object SoundFeedback : SettingsDestination()
    object Behavior : SettingsDestination()
    object ClamshellLid : SettingsDestination()
    object PermissionsRoot : SettingsDestination()
    object MagiskRootLsposed : SettingsDestination()
    object BackupRestore : SettingsDestination()
    object About : SettingsDestination()
    object Keyboard : SettingsDestination()
    object KeyboardPredictiveText : SettingsDestination()
    object LidLedStudio : SettingsDestination()
}

/** Hub card order: index i is opened by jump key i + 1. */
internal val SETTINGS_HUB_DESTINATIONS: List<SettingsDestination> = listOf(
    SettingsDestination.Appearance,
    SettingsDestination.Keyboard,
    SettingsDestination.CursorHardware,
    SettingsDestination.SoundFeedback,
    SettingsDestination.Behavior,
    SettingsDestination.PermissionsRoot,
    SettingsDestination.BackupRestore,
    SettingsDestination.About
)

/**
 * Where Back goes from [destination] once no nested editor / preview dialog claims it.
 * null = close the Settings Hub. Exactly one step per press (see MochibunSystemActions.hookKeyEvent
 * for the hook bug that once made one press step twice).
 */
internal fun settingsBackTarget(destination: SettingsDestination): SettingsDestination? =
    when (destination) {
        SettingsDestination.Hub -> null
        SettingsDestination.AppearanceThemePacks,
        SettingsDestination.AppearanceTypographyBorders,
        SettingsDestination.AppearanceGreetingTicker,
        SettingsDestination.AppearanceWallpaperDeco,
        SettingsDestination.AppearanceWidgetsCards -> SettingsDestination.Appearance
        SettingsDestination.CursorMotion -> SettingsDestination.CursorHardware
        SettingsDestination.KeyboardPredictiveText -> SettingsDestination.Keyboard
        SettingsDestination.ClamshellLid -> SettingsDestination.Behavior
        SettingsDestination.LidLedStudio -> SettingsDestination.ClamshellLid
        SettingsDestination.MagiskRootLsposed -> SettingsDestination.PermissionsRoot
        else -> SettingsDestination.Hub
    }

internal fun buildInitialSettingsStack(destination: SettingsDestination): List<SettingsDestination> =
    generateSequence(destination) { settingsBackTarget(it) }
        .toList()
        .asReversed()

/**
 * Navigation and focus stack for [SettingsHubScreen].
 *
 * - Keeps an explicit stack of [SettingsDestination]s so nested routes (e.g.
 *   `Behavior -> Clamshell & Lid -> Lid LED Studio`) pop one step at a time.
 * - Tracks `savedFocusByDestination` in a plain map (not snapshot-observed) so returning to a
 *   parent restores the exact row that opened the sub-screen without recomposing on D-pad steps.
 * - Maintains an ordered stack of active nested modal/editor back handlers so `handleBack`
 *   always dismisses the topmost open dialog/editor before popping a [SettingsDestination].
 */
internal class SettingsNavigationState(
    initialDestination: SettingsDestination = SettingsDestination.Hub,
    private val backStack: MutableList<SettingsDestination> =
        buildInitialSettingsStack(initialDestination).toMutableList()
) {
    val savedFocusByDestination: MutableMap<SettingsDestination, Int> = mutableMapOf()
    private val nestedBackHandlers: MutableList<Pair<Any, () -> Unit>> = mutableListOf()

    val currentDestination: SettingsDestination
        get() = backStack.last()

    val stackDepth: Int
        get() = backStack.size

    fun initialFocusFor(destination: SettingsDestination): Int =
        savedFocusByDestination[destination] ?: 0

    fun saveFocusIfActive(destination: SettingsDestination, index: Int) {
        if (destination in backStack) {
            savedFocusByDestination[destination] = index
        }
    }

    fun navigateTo(target: SettingsDestination, fromFocusIndex: Int? = null) {
        if (fromFocusIndex != null) {
            savedFocusByDestination[currentDestination] = fromFocusIndex
        }
        savedFocusByDestination.remove(target)
        backStack.add(target)
    }

    fun setNestedBackHandler(owner: Any, handler: (() -> Unit)?) {
        nestedBackHandlers.removeAll { it.first == owner }
        if (handler != null) {
            nestedBackHandlers.add(owner to handler)
        }
    }

    fun handleBack(onClose: () -> Unit): Boolean {
        val topModal = nestedBackHandlers.lastOrNull()
        if (topModal != null) {
            topModal.second.invoke()
            return true
        }
        return popDestination(onClose)
    }

    fun popDestination(onClose: () -> Unit): Boolean {
        if (backStack.size > 1) {
            val popped = backStack.removeAt(backStack.lastIndex)
            savedFocusByDestination.remove(popped)
            return true
        }
        onClose()
        return false
    }
}

/**
 * Unified Mochibun Settings Screen.
 * Implements a clean settings hub architecture optimized for D-pad spatial navigation
 * and 480 × 800 resolution on the ALT Mive Style Folder AT-M120.
 */
@Composable
fun SettingsHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenThemeStudio: (() -> Unit)? = null,
    onOpenIconPackDialog: (() -> Unit)? = null,
    onOpenHiddenApps: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onPickWallpaperClick: (() -> Unit)? = null,
    onResetWallpaperClick: (() -> Unit)? = null,
    hasCustomWallpaper: Boolean = false,
    onSettingsRestored: (() -> Unit)? = null,
    initialDestination: SettingsDestination = SettingsDestination.Hub,
    /** Launchable apps for Lid LED Studio → Per-App LED Rules. */
    installedApps: () -> List<LauncherApp> = { emptyList() }
) {
    val navState = remember(initialDestination) {
        SettingsNavigationState(
            initialDestination = initialDestination,
            backStack = mutableStateListOf<SettingsDestination>().apply {
                addAll(buildInitialSettingsStack(initialDestination))
            }
        )
    }
    var rootFocusedIndex by remember(initialDestination) {
        mutableIntStateOf(navState.initialFocusFor(SettingsDestination.Hub))
    }
    val rootListState = rememberLazyListState()
    val context = LocalContext.current

    fun handleBack() {
        SoundEffectManager.play(context, SoundEffect.BACK)
        navState.handleBack(onClose)
    }

    DisposableEffect(Unit) {
        MainActivity.settingsBackKeyHandler = { event ->
            if (event.keyCode != KeyEvent.KEYCODE_BACK &&
                event.keyCode != KeyEvent.KEYCODE_ESCAPE
            ) {
                false
            } else {
                if (event.action == KeyEvent.ACTION_UP) handleBack()
                true
            }
        }
        onDispose {
            MainActivity.settingsBackKeyHandler = null
        }
    }

    val settingsModifier = modifier.onPreviewKeyEvent { event ->
        if (event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
            return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyUp) {
            handleBack()
        }
        true
    }

    BackHandler {
        handleBack()
    }

    when (val destination = navState.currentDestination) {
        SettingsDestination.Hub -> {
            SettingsHubRootScreen(
                onNavigate = { target ->
                    navState.navigateTo(target, fromFocusIndex = rootFocusedIndex)
                },
                onClose = onClose,
                // Deferred read: passing the Int would recompose SettingsHubScreen (and rebuild the
                // whole root screen) on every D-pad step; the lambda defers the read to the rows.
                focusedIndex = { rootFocusedIndex },
                onFocusedIndexChange = { rootFocusedIndex = it },
                listState = rootListState,
                modifier = settingsModifier
            )
        }
        SettingsDestination.Appearance -> {
            AppearanceSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onOpenThemeStudio = onOpenThemeStudio,
                onOpenThemePacks = { navState.navigateTo(SettingsDestination.AppearanceThemePacks) },
                onOpenTypographyBorders = { navState.navigateTo(SettingsDestination.AppearanceTypographyBorders) },
                onOpenGreetingTicker = { navState.navigateTo(SettingsDestination.AppearanceGreetingTicker) },
                onOpenWallpaperDeco = { navState.navigateTo(SettingsDestination.AppearanceWallpaperDeco) },
                onOpenWidgetsCards = { navState.navigateTo(SettingsDestination.AppearanceWidgetsCards) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.AppearanceThemePacks -> {
            AppearanceThemePacksSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onSettingsRestored = onSettingsRestored,
                onNestedBackHandlerChanged = { navState.setNestedBackHandler("appearance_theme_packs", it) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.AppearanceTypographyBorders -> {
            AppearanceTypographyBordersSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onNestedBackHandlerChanged = { navState.setNestedBackHandler("appearance_reboot", it) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.AppearanceGreetingTicker -> {
            AppearanceGreetingTickerSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onNestedBackHandlerChanged = { navState.setNestedBackHandler("greeting_name", it) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.AppearanceWallpaperDeco -> {
            AppearanceWallpaperDecoSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onOpenIconPackDialog = onOpenIconPackDialog,
                onPickWallpaperClick = onPickWallpaperClick,
                onResetWallpaperClick = onResetWallpaperClick,
                hasCustomWallpaper = hasCustomWallpaper,
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.AppearanceWidgetsCards -> {
            AppearanceWidgetsCardsSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.CursorHardware -> {
            CursorHardwareSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onOpenCursorMotion = { navState.navigateTo(SettingsDestination.CursorMotion) },
                installedApps = installedApps,
                onNestedBackHandlerChanged = { navState.setNestedBackHandler("key_guide", it) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.CursorMotion -> {
            CursorMotionSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.SoundFeedback -> {
            SoundFeedbackSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.Behavior -> {
            BehaviorSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onOpenHiddenApps = onOpenHiddenApps,
                onCreateFolderClick = onCreateFolderClick,
                onOpenClamshellLid = { navState.navigateTo(SettingsDestination.ClamshellLid) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.ClamshellLid -> {
            ClamshellLidSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onOpenLidLedStudio = { navState.navigateTo(SettingsDestination.LidLedStudio) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.LidLedStudio -> {
            LidLedStudioScreen(
                onBack = { navState.popDestination(onClose) },
                onNestedBackHandlerChanged = { navState.setNestedBackHandler("lid_studio", it) },
                modifier = settingsModifier,
                installedApps = installedApps
            )
        }
        SettingsDestination.PermissionsRoot -> {
            PermissionsRootSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onOpenMagiskRootLsposed = { navState.navigateTo(SettingsDestination.MagiskRootLsposed) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.MagiskRootLsposed -> {
            MagiskRootLsposedSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.BackupRestore -> {
            BackupRestoreSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onSettingsRestored = onSettingsRestored,
                onPreviewDialogBackHandlerChanged = { navState.setNestedBackHandler("backup_preview", it) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.About -> {
            AboutMochibunScreen(
                onBack = { navState.popDestination(onClose) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.Keyboard -> {
            KeyboardSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                onOpenPredictiveText = { navState.navigateTo(SettingsDestination.KeyboardPredictiveText) },
                installedApps = installedApps,
                onNestedBackHandlerChanged = { navState.setNestedBackHandler("key_guide", it) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
        SettingsDestination.KeyboardPredictiveText -> {
            KeyboardPredictiveTextSettingsScreen(
                onBack = { navState.popDestination(onClose) },
                initialFocusedIndex = navState.initialFocusFor(destination),
                onFocusedIndexChange = { navState.saveFocusIfActive(destination, it) },
                modifier = settingsModifier
            )
        }
    }
}

/**
 * Root Hub Screen showing the primary category cards.
 */
@Composable
private fun SettingsHubRootScreen(
    onNavigate: (SettingsDestination) -> Unit,
    onClose: () -> Unit,
    focusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var sawCenterDown by remember { mutableStateOf(false) }

    // Eight settings categories
    val totalItems = SETTINGS_HUB_DESTINATIONS.size

    fun openDestination(index: Int) {
        SETTINGS_HUB_DESTINATIONS.getOrNull(index)?.let(onNavigate)
    }

    ScrollToFocusedItem(listState) { focusedIndex() }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Settings Hub",
        showBack = true,
        onBack = onClose,
        isLastItemFocused = { focusedIndex() == totalItems - 1 },
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onClose()
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        sawCenterDown = true
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        openDestination(focusedIndex())
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        onFocusedIndexChange(targetIndex)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        openDestination(targetIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        val current = focusedIndex()
                        onFocusedIndexChange(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (current > 0) current - 1 else totalItems - 1
                        } else {
                            if (current < totalItems - 1) current + 1 else 0
                        })
                        SoundEffectManager.play(context, SoundEffect.NAV)
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
            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "Look & Vibes") {
                    SettingsNavigationRow(
                        title = "Appearance",
                        subtitle = "Pastel themes, greeting banner, icons & deco ♡",
                        docomoIndex = 104,
                        isFocused = focusedIndex() == 0,
                        jumpNumber = 1,
                        onClick = { onNavigate(SettingsDestination.Appearance) }
                    )
                }
            }

            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "Typing") {
                    SettingsNavigationRow(
                        title = "Keyboard",
                        subtitle = "Keitai multi-tap typing, digit hold & key guide",
                        docomoIndex = 153,
                        isFocused = focusedIndex() == 1,
                        jumpNumber = 2,
                        onClick = { onNavigate(SettingsDestination.Keyboard) }
                    )
                }
            }

            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "Cursor & Hardware") {
                    SettingsNavigationRow(
                        title = "Virtual Cursor",
                        subtitle = "Pointer speed, scroll step, sprite & key remaps",
                        docomoIndex = 108,
                        isFocused = focusedIndex() == 2,
                        jumpNumber = 3,
                        onClick = { onNavigate(SettingsDestination.CursorHardware) }
                    )
                }
            }

            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "Sound & Clicks") {
                    SettingsNavigationRow(
                        title = "Sound & Feedback",
                        subtitle = "Audio routing, keypad volume & cute click themes",
                        docomoIndex = 9,
                        isFocused = focusedIndex() == 3,
                        jumpNumber = 4,
                        onClick = { onNavigate(SettingsDestination.SoundFeedback) }
                    )
                }
            }

            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "Home & Clamshell") {
                    SettingsNavigationRow(
                        title = "Behavior",
                        subtitle = "Folders, hidden apps, lid actions & outer LEDs",
                        docomoIndex = 72,
                        isFocused = focusedIndex() == 4,
                        jumpNumber = 5,
                        onClick = { onNavigate(SettingsDestination.Behavior) }
                    )
                }
            }

            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "Setup & Root") {
                    SettingsNavigationRow(
                        title = "Permissions & Root",
                        subtitle = "Core permissions, Magisk root & LSPosed hooks",
                        docomoIndex = 146,
                        isFocused = focusedIndex() == 5,
                        jumpNumber = 6,
                        onClick = { onNavigate(SettingsDestination.PermissionsRoot) }
                    )
                }
            }

            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "Saves & Reset") {
                    SettingsNavigationRow(
                        title = "Backup & Restore",
                        subtitle = "Save a full .json backup or reset to defaults",
                        docomoIndex = 105,
                        isFocused = focusedIndex() == 6,
                        jumpNumber = 7,
                        onClick = { onNavigate(SettingsDestination.BackupRestore) }
                    )
                }
            }

            item(contentType = "settings_hub_nav_card") {
                SettingsCard(categoryTitle = "System & Info") {
                    SettingsNavigationRow(
                        title = "About Mochibun",
                        subtitle = "App version, flip-phone specs & credits ♡",
                        docomoIndex = 132,
                        isFocused = focusedIndex() == 7,
                        jumpNumber = 8,
                        onClick = { onNavigate(SettingsDestination.About) }
                    )
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
    onOpenHiddenApps: (() -> Unit)? = null,
    onCreateFolderClick: (() -> Unit)? = null,
    onPickWallpaperClick: (() -> Unit)? = null,
    onResetWallpaperClick: (() -> Unit)? = null,
    hasCustomWallpaper: Boolean = false,
    onSettingsRestored: (() -> Unit)? = null,
    initialDestination: SettingsDestination = SettingsDestination.Hub,
    /** Launchable apps for Lid LED Studio → Per-App LED Rules. */
    installedApps: () -> List<LauncherApp> = { emptyList() }
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        SettingsHubScreen(
            modifier = Modifier
                .fillMaxSize()
                .curtainUnfurl(visible = true),
            onClose = onDismiss,
            onOpenThemeStudio = onOpenThemeStudio,
            onOpenIconPackDialog = onOpenIconPackDialog,
            onOpenHiddenApps = onOpenHiddenApps,
            onCreateFolderClick = onCreateFolderClick,
            onPickWallpaperClick = onPickWallpaperClick,
            onResetWallpaperClick = onResetWallpaperClick,
            hasCustomWallpaper = hasCustomWallpaper,
            onSettingsRestored = onSettingsRestored,
            initialDestination = initialDestination,
            installedApps = installedApps
        )
    }
}
