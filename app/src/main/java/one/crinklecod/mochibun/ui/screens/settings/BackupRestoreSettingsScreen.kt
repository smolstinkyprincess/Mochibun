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
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.theme.ActiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

internal val BACKUP_RESTORE_ROW_IDS = listOf(
    "export",
    "restore",
    "reset"
)

/**
 * Tier 1: Backup & Restore Sub-Screen
 */
@Composable
internal fun BackupRestoreSettingsScreen(
    onBack: () -> Unit,
    onSettingsRestored: (() -> Unit)?,
    onPreviewDialogBackHandlerChanged: (( (() -> Unit)? ) -> Unit),
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, BACKUP_RESTORE_ROW_IDS.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var pendingBackupPreview by remember { mutableStateOf<BackupPreviewSummary?>(null) }
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

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

    val items = BACKUP_RESTORE_ROW_IDS
    val totalItems = items.size

    // Card that holds the focused row; read lazily by ScrollToFocusedItem (not in composition).
    fun targetCardIndex(): Int =
        when (items.getOrNull(focusedIndex)) {
            "export", "restore" -> 0
            "reset" -> 1
            else -> 0
        }

    ScrollToFocusedItem(listState) { targetCardIndex() }

    fun activateBackupItem(index: Int) {
        when (items.getOrNull(index)) {
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
        isLastItemFocused = { focusedIndex == totalItems - 1 },
        headerDocomoIndex = 105,
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
                false
            }
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "backup_full_card") {
                SettingsCard(categoryTitle = "Full System Backup") {
                    SettingsNavigationRow(
                        title = "Export Full Backup",
                        subtitle = "Save layout, folders, themes & key remaps to a .json file",
                        docomoIndex = 140,
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = {
                            exportLauncher.launch(BackupRestoreManager.generateBackupFileName())
                        }
                    )

                    SettingsNavigationRow(
                        title = "Restore Full Backup",
                        subtitle = "Load a complete Mochibun .json backup file",
                        docomoIndex = 40,
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                        }
                    )
                }
            }

            item(contentType = "backup_factory_reset_card") {
                SettingsCard(categoryTitle = "Factory Reset") {
                    SettingsNavigationRow(
                        title = "Factory Reset",
                        subtitle = "Reset launcher layout, folders, remaps & preferences",
                        docomoIndex = 17,
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onClick = {
                            showResetConfirmDialog = true
                        }
                    )
                }
            }
        }
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
