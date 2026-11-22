package one.crinklecod.mochibun.ui

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.data.model.AppInfo
import one.crinklecod.mochibun.data.model.FavoriteFolder
import one.crinklecod.mochibun.data.model.FavoriteSlotItem
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BundlePreviewData
import one.crinklecod.mochibun.data.repository.ThemeBundleManager
import one.crinklecod.mochibun.ui.dialogs.AppActionDialog
import one.crinklecod.mochibun.ui.dialogs.CreateFavoriteFolderDialog
import one.crinklecod.mochibun.ui.dialogs.FavoriteFolderDialog
import one.crinklecod.mochibun.ui.dialogs.FolderActionDialog
import one.crinklecod.mochibun.ui.dialogs.HardwareButtonsDialog
import one.crinklecod.mochibun.ui.dialogs.HiddenAppsDialog
import one.crinklecod.mochibun.ui.dialogs.IconPackSelectionDialog
import one.crinklecod.mochibun.ui.dialogs.IconPickerDialog
import one.crinklecod.mochibun.ui.dialogs.PresetPreviewDialog
import one.crinklecod.mochibun.ui.dialogs.QuickMemoDialog
import one.crinklecod.mochibun.ui.dialogs.dialogWidth
import one.crinklecod.mochibun.ui.screens.SettingsDestination
import one.crinklecod.mochibun.ui.screens.SettingsHubDialog
import one.crinklecod.mochibun.ui.screens.ThemeStudioDialog
import one.crinklecod.mochibun.ui.theme.LauncherTheme
import one.crinklecod.mochibun.ui.theme.SystemUiThemeBridge
import one.crinklecod.mochibun.ui.theme.loadSavedTheme
import one.crinklecod.mochibun.ui.theme.saveTheme

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
    data object QuickMemo : ActiveModal
    /** Mochibunny's Feed / Pet / Chat pill, drawn inside Home; LauncherKeyHandler routes its keys. */
    data object PetPill : ActiveModal
}

/**
 * Renders whichever [ActiveModal] is open in [LauncherNavigationState.activeModal] and routes each
 * dialog's actions back into navigation state (focus restore, folder/icon sub-flows, theme apply).
 * Always composed by [MainLauncher] (also when no modal is open), so its remembered callbacks
 * live exactly as long as the launcher.
 */
@Composable
internal fun LauncherModalHost(
    nav: LauncherNavigationState,
    launcherVm: LauncherViewModel,
    themeState: MutableState<LauncherTheme>,
    preferences: SharedPreferences,
    scope: CoroutineScope,
    cardRoundness: Dp,
    onPickWallpaperClick: () -> Unit,
    onConsumeIncomingBundleUri: () -> Unit
) {
    val context = LocalContext.current
    var activeModal by nav.activeModalState
    var currentTheme by themeState
    var drawerOpen by nav.drawerOpenState
    var selectedFavoriteIndex by nav.selectedFavoriteIndexState
    var savedFavoriteIndexOnHome by nav.savedFavoriteIndexOnHomeState
    var homeFocusArea by nav.homeFocusAreaState
    var poundKeyState by nav.poundKeyPressState

    val apps = launcherVm.apps
    val visibleApps = launcherVm.visibleApps
    val hiddenApps = launcherVm.hiddenApps
    val favoriteFolders = launcherVm.favoriteFolders
    val favoriteItems = launcherVm.favoriteItems
    val favoritePackages = launcherVm.favoritePackages
    val kawaiiIconNames = launcherVm.kawaiiIconNames

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
    val onOpenHiddenApps = remember {
        { activeModal = ActiveModal.HiddenApps }
    }
    val onCreateFolderClick = remember {
        { activeModal = ActiveModal.CreateFolder() }
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
            one.crinklecod.mochibun.data.repository.CustomCardBorderManager.setBorderStyle(
                context,
                newTheme.borderStyle
            )
            PreferencesManager.setThemePreset(context, newTheme.name)
            SystemUiThemeBridge.broadcastTheme(context, newTheme)
            activeModal = null
        }
    }
    val onDismissQuickMemo = remember {
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
                onOpenHiddenApps = onOpenHiddenApps,
                onCreateFolderClick = onCreateFolderClick,
                onPickWallpaperClick = onPickWallpaperClick,
                onResetWallpaperClick = onResetWallpaperClick,
                hasCustomWallpaper = launcherVm.wallpaperUri != null,
                onSettingsRestored = onSettingsRestored,
                installedApps = { launcherVm.apps }
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

            IconPickerDialog(
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
            val onApplyBundle: (Boolean) -> Unit = remember(modal, context, preferences, launcherVm) {
                { clearUnbundledAssets ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            var mascotNote: String? = null
                            context.contentResolver.openInputStream(modal.uri)?.use { stream ->
                                ThemeBundleManager.applyBundle(
                                    context = context,
                                    inputStream = stream,
                                    clearUnbundledAssets = clearUnbundledAssets
                                ) { mascotNote = it }
                            }
                            mascotNote?.let { note ->
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, note, Toast.LENGTH_LONG).show()
                                }
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
                                    modal.preview.mascotPreview?.recycle()
                                    modal.preview.cardBorderPreview?.recycle()
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
                        modal.preview.mascotPreview?.recycle()
                        modal.preview.cardBorderPreview?.recycle()
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
}
