package com.crinklecodone.mochibun.ui.screens.settings

import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.repository.BackupRestoreManager
import com.crinklecodone.mochibun.data.repository.BundlePreviewData
import com.crinklecodone.mochibun.data.repository.ThemeBundleManager
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.ExportBundleDialog
import com.crinklecodone.mochibun.ui.dialogs.PresetPreviewDialog
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
import com.crinklecodone.mochibun.ui.theme.ActiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tier 1: Backup & Restore Sub-Screen
 */
@Composable
internal fun BackupRestoreSettingsSubScreen(
    onBack: () -> Unit,
    onSettingsRestored: (() -> Unit)?,
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
            // JSON parsing + bulk prefs writes run off the main thread; UI updates resume on Main
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BackupRestoreManager.importBackup(context, inputStream)
                        }
                    }
                    if (result != null && result.isSuccess) {
                        val summary = result.getOrThrow()
                        onSettingsRestored?.invoke()
                        statusMessage = "Restored: ${summary.folderCount} folders, ${summary.favoriteCount} favorites, ${summary.remapCount} remaps! ♡"
                    } else {
                        statusMessage = "Restore failed: ${result?.exceptionOrNull()?.message ?: "Invalid file"}"
                    }
                } catch (e: Exception) {
                    statusMessage = "Restore failed: ${e.message}"
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
                if (isBackDismissKey(native.keyCode)) {
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
                        title = "Import Setup Bundle",
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
                        title = "Reset to Defaults",
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
