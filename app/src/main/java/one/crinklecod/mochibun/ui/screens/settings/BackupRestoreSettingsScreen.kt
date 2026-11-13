package one.crinklecod.mochibun.ui.screens.settings

import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.repository.BackupPreviewSummary
import one.crinklecod.mochibun.data.repository.BackupRestoreManager
import one.crinklecod.mochibun.data.repository.BundlePreviewData
import one.crinklecod.mochibun.data.repository.ThemeBundleManager
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.ExportBundleDialog
import one.crinklecod.mochibun.ui.dialogs.PresetPreviewDialog
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Tier 1: Backup & Restore Sub-Screen
 */
@Composable
internal fun BackupRestoreSettingsSubScreen(
    onBack: () -> Unit,
    onSettingsRestored: (() -> Unit)?,
    onPreviewDialogBackHandlerChanged: (( (() -> Unit)? ) -> Unit),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showExportBundleDialog by remember { mutableStateOf(false) }
    var pendingImportPreviewData by remember { mutableStateOf<BundlePreviewData?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBackupPreview by remember { mutableStateOf<BackupPreviewSummary?>(null) }
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }

    // State for pending export configuration awaiting file creation URI
    var pendingBundleName by remember { mutableStateOf("Mochibun Setup") }
    var pendingIncludeWallpaper by remember { mutableStateOf(true) }
    var pendingIncludeCursor by remember { mutableStateOf(true) }
    var pendingIncludeDisplayName by remember { mutableStateOf(true) }

    // Setup Bundle (.mochibun) Create launcher
    val bundleCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val result = ThemeBundleManager.exportBundle(
                        context = context,
                        outputStream = outputStream,
                        bundleName = pendingBundleName,
                        includeWallpaper = pendingIncludeWallpaper,
                        includeCustomCursor = pendingIncludeCursor,
                        includeDisplayName = pendingIncludeDisplayName
                    )
                    if (result.isSuccess) {
                        statusMessage = "Theme bundle exported! ♡"
                    } else {
                        statusMessage = "Export failed: ${result.exceptionOrNull()?.message}"
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Export failed: ${e.message}"
            }
        }
    }

    // Setup Bundle (.mochibun) Import launcher
    val bundleImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val previewRes = ThemeBundleManager.readBundleManifest(inputStream)
                    if (previewRes.isSuccess) {
                        pendingImportPreviewData = previewRes.getOrThrow()
                        pendingImportUri = uri
                    } else {
                        statusMessage = "Invalid bundle: ${previewRes.exceptionOrNull()?.message}"
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Failed to inspect bundle: ${e.message}"
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BackupRestoreManager.exportBackup(context, outputStream)
                }
                statusMessage = "Backup exported successfully! ♡"
            } catch (e: Exception) {
                statusMessage = "Export failed: ${e.message}"
            }
        }
    }

    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Validate off the main thread before offering to apply anything.
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        BackupRestoreManager.previewBackup(context, uri)
                    }
                    if (result.isSuccess) {
                        pendingBackupUri = uri
                        pendingBackupPreview = result.getOrThrow()
                    } else {
                        statusMessage = "Invalid backup: ${result.exceptionOrNull()?.message ?: "The file could not be validated."}"
                    }
                } catch (e: Exception) {
                    statusMessage = "Invalid backup: ${e.message}"
                }
            }
        }
    }

    val items = listOf("export_bundle", "import_bundle", "export", "restore", "reset")
    val totalItems = items.size

    val targetCardIndex = remember(focusedIndex, items) {
        when (items.getOrNull(focusedIndex)) {
            "export_bundle", "import_bundle" -> 0
            "export", "restore" -> 1
            "reset" -> 2
            else -> 0
        }
    }

    LaunchedEffect(targetCardIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || targetCardIndex in visible) return@LaunchedEffect
        listState.animateScrollToItem(targetCardIndex)
    }

    fun activateBackupItem(index: Int) {
        when (items.getOrNull(index)) {
            "export_bundle" -> showExportBundleDialog = true
            "import_bundle" -> bundleImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            "export" -> exportLauncher.launch(BackupRestoreManager.generateBackupFileName())
            "restore" -> importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            "reset" -> showResetConfirmDialog = true
        }
    }

    val dismissBackupPreview: () -> Unit = remember {
        {
            pendingBackupPreview = null
            pendingBackupUri = null
        }
    }

    SideEffect {
        onPreviewDialogBackHandlerChanged(
            if (pendingBackupPreview != null) dismissBackupPreview else null
        )
    }

    DisposableEffect(Unit) {
        onDispose { onPreviewDialogBackHandlerChanged(null) }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Backup & Restore",
        showBack = true,
        onBack = onBack,
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
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activateBackupItem(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activateBackupItem(targetIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        focusedIndex = if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (focusedIndex > 0) focusedIndex - 1 else totalItems - 1
                        } else {
                            if (focusedIndex < totalItems - 1) focusedIndex + 1 else 0
                        }
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        focusedIndex = (focusedIndex + direction + totalItems) % totalItems
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
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsCard(categoryTitle = "Share & Theme Presets") {
                    SettingsNavigationRow(
                        title = "Export Setup Bundle (.mochibun)",
                        subtitle = "Share theme, colors, greeting & cursor in a safe archive",
                        icon = "🎀",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = {
                            showExportBundleDialog = true
                        }
                    )

                    SettingsNavigationRow(
                        title = "Import Setup Bundle (.mochibun)",
                        subtitle = "Preview and apply a shared .mochibun preset",
                        icon = "✨",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = {
                            bundleImportLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Full System Backup") {
                    SettingsNavigationRow(
                        title = "Export Full Backup",
                        subtitle = "Save layout, folders, themes & key remaps to a .json file",
                        icon = "📤",
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onClick = {
                            exportLauncher.launch(BackupRestoreManager.generateBackupFileName())
                        }
                    )

                    SettingsNavigationRow(
                        title = "Restore Full Backup",
                        subtitle = "Import a complete Mochibun .json backup file",
                        icon = "📥",
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                        }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Factory Reset") {
                    SettingsNavigationRow(
                        title = "Reset to Factory Defaults",
                        subtitle = "Reset launcher layout, folders, remaps & preferences",
                        icon = "🔄",
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onClick = {
                            showResetConfirmDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showExportBundleDialog) {
        ExportBundleDialog(
            onDismiss = { showExportBundleDialog = false },
            onExport = { name, wall, cur, disp ->
                pendingBundleName = name
                pendingIncludeWallpaper = wall
                pendingIncludeCursor = cur
                pendingIncludeDisplayName = disp
                showExportBundleDialog = false
                bundleCreateLauncher.launch(ThemeBundleManager.generateBundleFileName(name))
            }
        )
    }

    pendingBackupPreview?.let { preview ->
        BackupPreviewConfirmationDialog(
            preview = preview,
            onRestore = {
                val uri = pendingBackupUri
                dismissBackupPreview()
                if (uri != null) {
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val input = context.contentResolver.openInputStream(uri)
                                    ?: throw IllegalArgumentException("The selected backup could not be opened.")
                                input.use { BackupRestoreManager.importBackup(context, it) }
                            }
                            if (result.isSuccess) {
                                val summary = result.getOrThrow()
                                onSettingsRestored?.invoke()
                                statusMessage = "Restored: ${summary.folderCount} folders, ${summary.favoriteCount} favorites, ${summary.remapCount} remaps! ♡"
                            } else {
                                statusMessage = "Restore failed: ${result.exceptionOrNull()?.message}"
                            }
                        } catch (e: Exception) {
                            statusMessage = "Restore failed: ${e.message}"
                        }
                    }
                }
            },
            onDismiss = dismissBackupPreview
        )
    }

    pendingImportPreviewData?.let { preview ->
        PresetPreviewDialog(
            previewData = preview,
            onApply = {
                val uri = pendingImportUri
                if (uri != null) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val applyRes = ThemeBundleManager.applyBundle(context, stream)
                            if (applyRes.isSuccess) {
                                onSettingsRestored?.invoke()
                                statusMessage = "Preset '${preview.manifest.bundleName}' applied! ♡"
                            } else {
                                statusMessage = "Apply failed: ${applyRes.exceptionOrNull()?.message}"
                            }
                        }

                    } catch (e: Exception) {
                        statusMessage = "Apply failed: ${e.message}"
                    }
                }
                try {
                    preview.wallpaperPreview?.recycle()
                } catch (_: Exception) {}
                pendingImportPreviewData = null
                pendingImportUri = null
            },
            onDismiss = {
                try {
                    preview.wallpaperPreview?.recycle()
                } catch (_: Exception) {}
                pendingImportPreviewData = null
                pendingImportUri = null
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset to Factory Defaults", fontWeight = FontWeight.Bold, color = ActiveTheme.accentDeep) },
            text = { Text("Are you sure you want to reset all Mochibun settings, layout, folders, and key remaps back to factory defaults?", color = ActiveTheme.textPrimary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        BackupRestoreManager.resetToDefaults(context)
                        onSettingsRestored?.invoke()
                        showResetConfirmDialog = false
                        statusMessage = "Reset to factory defaults completed."
                    }
                ) {
                    Text("Reset", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = ActiveTheme.textPrimary)
                }
            },
            containerColor = ActiveTheme.container,
            shape = RoundedCornerShape(20.dp)
        )
    }

    statusMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            statusMessage = null
        }
    }
}

@Composable
private fun BackupPreviewConfirmationDialog(
    preview: BackupPreviewSummary,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var focusedButton by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    requestFocusSafely(focusRequester)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    return@onPreviewKeyEvent true
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        focusedButton = 1 - focusedButton
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                if (isCenterActivateKey(keyCode)) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        sawCenterDown = true
                    } else if (event.type == KeyEventType.KeyUp && sawCenterDown) {
                        sawCenterDown = false
                        if (focusedButton == 0) onRestore() else onDismiss()
                    }
                    return@onPreviewKeyEvent true
                }
                false
            },
        title = {
            Text("Restore this backup?", fontWeight = FontWeight.Bold, color = ActiveTheme.accentDeep)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Exported ${DateFormat.getDateTimeInstance().format(Date(preview.timestamp))} · Mochibun ${preview.appVersion}",
                    color = ActiveTheme.textPrimary
                )
                Text("Favorites: ${preview.favoriteCount}", color = ActiveTheme.textPrimary)
                Text("Folders: ${preview.folderCount}", color = ActiveTheme.textPrimary)
                Text("Hardware remaps: ${preview.remapCount}", color = ActiveTheme.textPrimary)
                Text("Theme: ${preview.themeName ?: "Default"}", color = ActiveTheme.textPrimary)
            }
        },
        confirmButton = {
            Button(
                onClick = onRestore,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusedButton == 0) ActiveTheme.accentDeep else ActiveTheme.surfaceTint,
                    contentColor = if (focusedButton == 0) Color.White else ActiveTheme.textPrimary
                ),
                modifier = Modifier.border(
                    width = if (focusedButton == 0) 2.dp else 1.dp,
                    color = if (focusedButton == 0) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                    shape = RoundedCornerShape(14.dp)
                )
            ) {
                Text("Restore Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (focusedButton == 1) ActiveTheme.accentDeep else ActiveTheme.surfaceTint,
                    contentColor = if (focusedButton == 1) Color.White else ActiveTheme.textPrimary
                ),
                modifier = Modifier.border(
                    width = if (focusedButton == 1) 2.dp else 1.dp,
                    color = if (focusedButton == 1) ActiveTheme.selectionGlow else ActiveTheme.cardBorder,
                    shape = RoundedCornerShape(14.dp)
                )
            ) {
                Text("Cancel")
            }
        },
        containerColor = ActiveTheme.container,
        shape = RoundedCornerShape(22.dp)
    )
}
