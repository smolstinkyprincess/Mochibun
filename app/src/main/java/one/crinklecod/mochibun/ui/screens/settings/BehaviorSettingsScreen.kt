package one.crinklecod.mochibun.ui.screens.settings

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.ClamshellManager
import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.J2meGameRepository
import one.crinklecod.mochibun.ui.components.curtainUnfurl
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.KawaiiDialogItem
import one.crinklecod.mochibun.ui.dialogs.KeitaiLaceDialogWithClose
import one.crinklecod.mochibun.ui.dialogs.LidLedColorPickerDialog
import one.crinklecod.mochibun.ui.dialogs.dialogWidth
import one.crinklecod.mochibun.ui.dialogs.isBackDismissKey
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import org.json.JSONObject

internal enum class BehaviorRowId(val id: String) {
    CREATE_FOLDER("create_folder"),
    FOLDER_SCATTER("folder_scatter"),
    HIDDEN_APPS("hidden_apps"),
    J2ME_FOLDER("j2me_folder"),
    J2ME_TEMPLATE_SYNC("j2me_template_sync"),
    CLAMSHELL_LID("clamshell_lid")
}

internal enum class ClamshellLidRowId(val id: String) {
    LID_OPEN("lid_open"),
    SCREEN_OFF_RESET("screen_off_reset"),
    LID_LED("lid_led"),
    LED_COLOR("led_color"),
    LED_STUDIO("led_studio"),
    LED_CHARGING("led_charging")
}

/**
 * Visible rows of the Behavior screen, in on-screen order. Focus index = list index,
 * jump number = index + 1, so hiding [BehaviorRowId.CREATE_FOLDER] never leaves a gap.
 */
internal fun behaviorRowIds(
    hasCreateFolder: Boolean
): List<String> = buildList {
    if (hasCreateFolder) add(BehaviorRowId.CREATE_FOLDER.id)
    add(BehaviorRowId.FOLDER_SCATTER.id)
    add(BehaviorRowId.HIDDEN_APPS.id)
    add(BehaviorRowId.J2ME_FOLDER.id)
    add(BehaviorRowId.J2ME_TEMPLATE_SYNC.id)
    add(BehaviorRowId.CLAMSHELL_LID.id)
}

/**
 * Visible rows of the Clamshell & Lid sub-screen, in on-screen order. Focus index = list index,
 * jump number = index + 1, so hiding rows never leaves a gap.
 */
internal fun clamshellLidRowIds(
    showRootOptions: Boolean,
    showScreenOffReset: Boolean,
    showLidLedRows: Boolean = showRootOptions
): List<String> = buildList {
    add(ClamshellLidRowId.LID_OPEN.id)
    if (showScreenOffReset) add(ClamshellLidRowId.SCREEN_OFF_RESET.id)
    // Outer LED rows write vendor sysfs nodes, which needs root and the AT-M120 LED chip
    if (showRootOptions && showLidLedRows) {
        add(ClamshellLidRowId.LID_LED.id)
        add(ClamshellLidRowId.LED_COLOR.id)
        add(ClamshellLidRowId.LED_STUDIO.id)
        add(ClamshellLidRowId.LED_CHARGING.id)
    }
}

/**
 * Screen-Off Flip Reset is offered unless root options are shown and the flip close is already
 * detected (root hall reader / LSPosed key hook).
 */
internal fun showScreenOffResetRow(showRootOptions: Boolean, lidDetectionActive: Boolean): Boolean =
    !showRootOptions || !lidDetectionActive

@Composable
internal fun BehaviorSettingsScreen(
    onBack: () -> Unit,
    onOpenHiddenApps: (() -> Unit)?,
    onCreateFolderClick: (() -> Unit)?,
    onOpenClamshellLid: () -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by rememberSettingsFocusIndex(initialFocusedIndex, onFocusedIndexChange)
    var sawCenterDown by remember { mutableStateOf(false) }

    val folderScatter by PreferencesManager.isFolderScatterLayout.collectAsState()
    val lidAction by PreferencesManager.lidOpenAction.collectAsState()
    val lidLed by PreferencesManager.isLidLedEnabled.collectAsState()
    val showRootOptions by PreferencesManager.showRootOptions.collectAsState()
    val lidLedHardware by LidLedManager.hasLedHardware.collectAsState()
    val showLidLedRows = showRootOptions && lidLedHardware != false
    val j2meFolderUri by PreferencesManager.j2meFolderUri.collectAsState()
    val j2meConfigsFolderUri by PreferencesManager.j2meConfigsFolderUri.collectAsState()
    val j2meTemplateConfigJson by PreferencesManager.j2meTemplateConfigJson.collectAsState()
    val j2meAutoSyncOnLaunch by PreferencesManager.isJ2meAutoSyncOnLaunchEnabled.collectAsState()
    var showJ2meFolderOptions by remember { mutableStateOf(false) }
    var showJ2meTemplateDialog by remember { mutableStateOf(false) }

    val j2meFolderDisplayName by produceState<String?>(initialValue = null, j2meFolderUri) {
        value = if (j2meFolderUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                J2meGameRepository.getFolderDisplayName(context, j2meFolderUri)
            }
        }
    }

    val j2meFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            J2meGameRepository.saveFolderTreeUri(context, uri)
        }
    }

    val j2meConfigsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            J2meGameRepository.saveConfigsFolderTreeUri(context, uri)
            Toast.makeText(context, "Linked J2ME configs folder ♡", Toast.LENGTH_SHORT).show()
        }
    }

    val items = remember(onCreateFolderClick) {
        behaviorRowIds(hasCreateFolder = onCreateFolderClick != null)
    }
    val totalItems = items.size

    // Keep focus on a real row if the list shrinks
    LaunchedEffect(totalItems) {
        focusedIndex = focusedIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    }

    fun isRowFocused(row: BehaviorRowId): Boolean = items.getOrNull(focusedIndex) == row.id
    fun jumpNumber(row: BehaviorRowId): Int? =
        (items.indexOf(row.id) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + totalItems) % totalItems
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun activateRow(id: String?) {
        when (id) {
            BehaviorRowId.CREATE_FOLDER.id -> onCreateFolderClick?.invoke()
            BehaviorRowId.FOLDER_SCATTER.id -> PreferencesManager.setFolderScatterLayout(context, !folderScatter)
            BehaviorRowId.HIDDEN_APPS.id -> onOpenHiddenApps?.invoke()
            BehaviorRowId.J2ME_FOLDER.id -> {
                if (j2meFolderUri.isNullOrBlank()) {
                    j2meFolderPicker.launch(null)
                } else {
                    showJ2meFolderOptions = true
                }
            }
            BehaviorRowId.J2ME_TEMPLATE_SYNC.id -> showJ2meTemplateDialog = true
            BehaviorRowId.CLAMSHELL_LID.id -> onOpenClamshellLid()
        }
    }

    ScrollToFocusedItem(listState) {
        when (items.getOrNull(focusedIndex)) {
            BehaviorRowId.CREATE_FOLDER.id, BehaviorRowId.FOLDER_SCATTER.id -> 0
            BehaviorRowId.HIDDEN_APPS.id,
            BehaviorRowId.J2ME_FOLDER.id,
            BehaviorRowId.J2ME_TEMPLATE_SYNC.id -> 1
            else -> 2
        }
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Behavior",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == totalItems - 1 },
        headerDocomoIndex = 72,
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
                        activateRow(items.getOrNull(focusedIndex))
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(items.getOrNull(targetIndex))
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val clamshellSubtitle = if (showLidLedRows) {
            "${lidAction.label} · Outer LED ${if (lidLed) "On" else "Off"}"
        } else {
            lidAction.label
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 44.dp, bottom = 72.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(contentType = "behavior_folder_card") {
                SettingsCard(categoryTitle = "Folder Options") {
                    if (onCreateFolderClick != null) {
                        SettingsNavigationRow(
                            title = "Create Folder",
                            subtitle = "Bundle favorite apps into a cozy folder ♡",
                            docomoIndex = 102,
                            isFocused = isRowFocused(BehaviorRowId.CREATE_FOLDER),
                            jumpNumber = jumpNumber(BehaviorRowId.CREATE_FOLDER),
                            onClick = { activateRow(BehaviorRowId.CREATE_FOLDER.id) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    SettingsToggleRow(
                        title = "Scattered Folder Stickers",
                        subtitle = "Playful tilted sticker layout (off = neat grid)",
                        checked = folderScatter,
                        isFocused = isRowFocused(BehaviorRowId.FOLDER_SCATTER),
                        jumpNumber = jumpNumber(BehaviorRowId.FOLDER_SCATTER),
                        onCheckedChange = { PreferencesManager.setFolderScatterLayout(context, it) }
                    )
                }
            }
            item(contentType = "behavior_drawer_card") {
                SettingsCard(categoryTitle = "App Drawer") {
                    SettingsNavigationRow(
                        title = "Hidden Apps",
                        subtitle = "Tuck away apps you don't want in your drawer",
                        docomoIndex = 19,
                        isFocused = isRowFocused(BehaviorRowId.HIDDEN_APPS),
                        jumpNumber = jumpNumber(BehaviorRowId.HIDDEN_APPS),
                        onClick = { onOpenHiddenApps?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "J2ME Games Folder (.jar)",
                        subtitle = j2meFolderDisplayName ?: "Not set",
                        docomoIndex = 102,
                        isFocused = isRowFocused(BehaviorRowId.J2ME_FOLDER),
                        jumpNumber = jumpNumber(BehaviorRowId.J2ME_FOLDER),
                        onClick = { activateRow(BehaviorRowId.J2ME_FOLDER.id) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "J2ME Controls & Screen Template",
                        subtitle = if (!j2meTemplateConfigJson.isNullOrBlank()) {
                            "Saved • Ready to sync"
                        } else {
                            "Tap to link AbyssME configs & save template"
                        },
                        docomoIndex = 72,
                        isFocused = isRowFocused(BehaviorRowId.J2ME_TEMPLATE_SYNC),
                        jumpNumber = jumpNumber(BehaviorRowId.J2ME_TEMPLATE_SYNC),
                        onClick = { activateRow(BehaviorRowId.J2ME_TEMPLATE_SYNC.id) }
                    )
                }
            }
            item(contentType = "behavior_clamshell_nav_card") {
                SettingsCard(categoryTitle = "Clamshell & Hardware") {
                    SettingsNavigationRow(
                        title = "Clamshell & Lid",
                        subtitle = clamshellSubtitle,
                        docomoIndex = 72,
                        isFocused = isRowFocused(BehaviorRowId.CLAMSHELL_LID),
                        jumpNumber = jumpNumber(BehaviorRowId.CLAMSHELL_LID),
                        onClick = { activateRow(BehaviorRowId.CLAMSHELL_LID.id) }
                    )
                }
            }
        }
    }

    if (showJ2meFolderOptions) {
        J2meFolderOptionsDialog(
            folderName = j2meFolderDisplayName ?: "Selected folder",
            onChangeFolder = {
                showJ2meFolderOptions = false
                j2meFolderPicker.launch(null)
            },
            onClearFolder = {
                J2meGameRepository.saveFolderTreeUri(context, null)
                showJ2meFolderOptions = false
            },
            onDismiss = { showJ2meFolderOptions = false }
        )
    }

    if (showJ2meTemplateDialog) {
        J2meTemplateSyncDialog(
            configsFolderUri = j2meConfigsFolderUri,
            hasTemplate = !j2meTemplateConfigJson.isNullOrBlank(),
            autoSyncOnLaunch = j2meAutoSyncOnLaunch,
            onPickConfigsFolder = {
                j2meConfigsPicker.launch(J2meGameRepository.buildAbyssmeConfigsInitialUri())
            },
            onDismiss = { showJ2meTemplateDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun J2meFolderOptionsDialog(
    folderName: String,
    onChangeFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val focusRequester = remember { FocusRequester() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    fun runAction(idx: Int) {
        when (idx) {
            0 -> onChangeFolder()
            1 -> onClearFolder()
            else -> onDismiss()
        }
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        onDismiss()
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
                        runAction(selectedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..2) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        selectedIndex = digit - 1
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        runAction(selectedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = (selectedIndex - 1 + 3) % 3
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = (selectedIndex + 1) % 3
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        true
                    }
                    else -> false
                }
            }
    ) {
        KeitaiLaceDialogWithClose(
            isCloseFocused = selectedIndex == 2,
            onClose = {
                selectedIndex = 2
                onDismiss()
            },
            modifier = Modifier
                .width(dialogWidth)
                .curtainUnfurl(visible = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "J2ME Games Folder (.jar)",
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = folderName,
                    color = theme.fgTextColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider(
                    color = theme.appActionColor.copy(alpha = 0.22f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                KawaiiDialogItem(
                    text = "Change folder",
                    docomoIndex = 102,
                    isFocused = selectedIndex == 0,
                    jumpNumber = 1,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        selectedIndex = 0
                        onChangeFolder()
                    }
                )
                KawaiiDialogItem(
                    text = "Clear folder",
                    docomoIndex = 17,
                    isDestructive = true,
                    isFocused = selectedIndex == 1,
                    jumpNumber = 2,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        selectedIndex = 1
                        onClearFolder()
                    }
                )
            }
        }
    }
}

private enum class J2meTemplateDialogMode {
    MENU,
    CAPTURE_LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun J2meTemplateSyncDialog(
    configsFolderUri: String?,
    hasTemplate: Boolean,
    autoSyncOnLaunch: Boolean,
    onPickConfigsFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalLauncherTheme.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val captureListState = rememberLazyListState()

    var mode by remember { mutableStateOf(J2meTemplateDialogMode.MENU) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    var detectedConfigs by remember {
        mutableStateOf<List<J2meGameRepository.DetectedJ2meConfig>>(emptyList())
    }
    var isScanningConfigs by remember { mutableStateOf(false) }

    val configsFolderLabel by produceState<String?>(initialValue = null, configsFolderUri) {
        value = withContext(Dispatchers.IO) {
            when {
                !configsFolderUri.isNullOrBlank() ->
                    J2meGameRepository.getFolderDisplayName(context, configsFolderUri)
                        ?: "Linked SAF folder"
                RootShell.isRootAvailable() -> "AbyssME root fallback"
                else -> null
            }
        }
    }

    LaunchedEffect(mode, configsFolderUri) {
        if (mode == J2meTemplateDialogMode.CAPTURE_LIST) {
            isScanningConfigs = true
            detectedConfigs = withContext(Dispatchers.IO) {
                J2meGameRepository.listDetectedGameConfigs(context)
            }
            isScanningConfigs = false
            selectedIndex = 0
        }
    }

    LaunchedEffect(mode, detectedConfigs.size) {
        if (mode == J2meTemplateDialogMode.CAPTURE_LIST) {
            selectedIndex = selectedIndex.coerceIn(0, detectedConfigs.size)
        } else {
            selectedIndex = selectedIndex.coerceIn(0, 5)
        }
    }

    LaunchedEffect(mode, selectedIndex, detectedConfigs.size) {
        if (mode == J2meTemplateDialogMode.CAPTURE_LIST &&
            selectedIndex in detectedConfigs.indices
        ) {
            val isVisible = captureListState.layoutInfo.visibleItemsInfo.any {
                it.index == selectedIndex
            }
            if (!isVisible) {
                captureListState.animateScrollToItem(selectedIndex)
            }
        }
    }

    fun captureFromEntry(entry: J2meGameRepository.DetectedJ2meConfig) {
        if (isBusy) return
        isBusy = true
        scope.launch {
            val readResult = J2meGameRepository.readDetectedGameConfig(context, entry)
            readResult.onSuccess { jsonText ->
                val valid = withContext(Dispatchers.Default) {
                    try {
                        JSONObject(jsonText)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (valid) {
                    PreferencesManager.setJ2meTemplateConfigJson(context, jsonText)
                    Toast.makeText(
                        context,
                        "Saved template from ${entry.gameId} ♡",
                        Toast.LENGTH_SHORT
                    ).show()
                    mode = J2meTemplateDialogMode.MENU
                    selectedIndex = 3
                } else {
                    Toast.makeText(
                        context,
                        "Invalid config.json in ${entry.gameId}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    err.message ?: "Failed reading ${entry.gameId}/config.json",
                    Toast.LENGTH_LONG
                ).show()
            }
            isBusy = false
        }
    }

    fun runSyncNow() {
        if (isBusy) return
        if (!hasTemplate) {
            Toast.makeText(context, "Capture a master template first ♡", Toast.LENGTH_SHORT).show()
            return
        }
        isBusy = true
        scope.launch {
            val result = J2meGameRepository.syncConfigTemplateToAllGames(context)
            result.onSuccess { count ->
                Toast.makeText(
                    context,
                    if (count == 0) {
                        "No game config.json files found to update"
                    } else {
                        "Synced template to $count game config(s) ♡"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    err.message ?: "Failed syncing J2ME templates",
                    Toast.LENGTH_LONG
                ).show()
            }
            isBusy = false
        }
    }

    fun runMenuAction(idx: Int) {
        when (idx) {
            0 -> onPickConfigsFolder()
            1 -> mode = J2meTemplateDialogMode.CAPTURE_LIST
            2 -> {
                val next = !autoSyncOnLaunch
                PreferencesManager.setJ2meAutoSyncOnLaunchEnabled(context, next)
            }
            3 -> runSyncNow()
            4 -> {
                J2meGameRepository.clearTemplateSync(context)
                Toast.makeText(context, "Cleared J2ME template", Toast.LENGTH_SHORT).show()
            }
            else -> onDismiss()
        }
    }

    requestFocusSafely(focusRequester)

    BasicAlertDialog(
        onDismissRequest = {
            if (mode == J2meTemplateDialogMode.CAPTURE_LIST) {
                mode = J2meTemplateDialogMode.MENU
                selectedIndex = 1
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (isBackDismissKey(native.keyCode)) {
                    if (event.type == KeyEventType.KeyUp) {
                        SoundEffectManager.play(context, SoundEffect.BACK)
                        if (mode == J2meTemplateDialogMode.CAPTURE_LIST) {
                            mode = J2meTemplateDialogMode.MENU
                            selectedIndex = 1
                        } else {
                            onDismiss()
                        }
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
                        if (mode == J2meTemplateDialogMode.MENU) {
                            runMenuAction(selectedIndex)
                        } else if (selectedIndex in detectedConfigs.indices) {
                            captureFromEntry(detectedConfigs[selectedIndex])
                        } else {
                            mode = J2meTemplateDialogMode.MENU
                            selectedIndex = 1
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null) {
                    if (mode == J2meTemplateDialogMode.MENU && digit in 1..5) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            selectedIndex = digit - 1
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                            runMenuAction(selectedIndex)
                        }
                        return@onPreviewKeyEvent true
                    } else if (mode == J2meTemplateDialogMode.CAPTURE_LIST &&
                        digit in 1..detectedConfigs.size.coerceAtMost(MAX_JUMP_ROWS)
                    ) {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                            selectedIndex = digit - 1
                            SoundEffectManager.play(context, SoundEffect.SELECT)
                            captureFromEntry(detectedConfigs[selectedIndex])
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val itemCount = if (mode == J2meTemplateDialogMode.MENU) {
                    6
                } else {
                    detectedConfigs.size + 1
                }
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        selectedIndex = (selectedIndex - 1 + itemCount) % itemCount
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        selectedIndex = (selectedIndex + 1) % itemCount
                        SoundEffectManager.play(context, SoundEffect.NAV)
                        true
                    }
                    else -> false
                }
            }
    ) {
        val isCaptureMode = mode == J2meTemplateDialogMode.CAPTURE_LIST
        val closeIndex = if (isCaptureMode) detectedConfigs.size else 5

        KeitaiLaceDialogWithClose(
            isCloseFocused = selectedIndex == closeIndex,
            closeLabel = if (isCaptureMode) "back" else "close",
            onClose = {
                selectedIndex = closeIndex
                if (isCaptureMode) {
                    mode = J2meTemplateDialogMode.MENU
                    selectedIndex = 1
                } else {
                    onDismiss()
                }
            },
            modifier = Modifier
                .width(dialogWidth)
                .heightIn(max = 420.dp)
                .curtainUnfurl(visible = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isCaptureMode) {
                        "Capture Master Template"
                    } else {
                        "J2ME Controls & Screen"
                    },
                    color = theme.fgTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isCaptureMode) {
                        "Pick a configured game (e.g. pending_...)"
                    } else {
                        val tplLabel = if (hasTemplate) "Template saved" else "No template"
                        val dirLabel = configsFolderLabel ?: "Configs not linked"
                        "$tplLabel · $dirLabel"
                    },
                    color = theme.fgTextColor.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                HorizontalDivider(
                    color = theme.appActionColor.copy(alpha = 0.22f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (!isCaptureMode) {
                    KawaiiDialogItem(
                        text = "Pick Emulator configs Folder",
                        docomoIndex = 102,
                        isFocused = selectedIndex == 0,
                        jumpNumber = 1,
                        enabled = !isBusy,
                        marqueeWhenFocused = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedIndex = 0
                            runMenuAction(0)
                        }
                    )
                    KawaiiDialogItem(
                        text = "Capture Master Template",
                        docomoIndex = 72,
                        isFocused = selectedIndex == 1,
                        jumpNumber = 2,
                        enabled = !isBusy,
                        marqueeWhenFocused = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedIndex = 1
                            runMenuAction(1)
                        }
                    )
                    KawaiiDialogItem(
                        text = "Auto-Sync on Launch: ${if (autoSyncOnLaunch) "On" else "Off"}",
                        docomoIndex = 72,
                        isFocused = selectedIndex == 2,
                        jumpNumber = 3,
                        enabled = !isBusy,
                        marqueeWhenFocused = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedIndex = 2
                            runMenuAction(2)
                        }
                    )
                    KawaiiDialogItem(
                        text = if (isBusy) "Syncing…" else "Sync Template to All Games Now",
                        docomoIndex = 14,
                        isFocused = selectedIndex == 3,
                        jumpNumber = 4,
                        enabled = !isBusy && hasTemplate,
                        marqueeWhenFocused = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedIndex = 3
                            runMenuAction(3)
                        }
                    )
                    KawaiiDialogItem(
                        text = "Clear Template",
                        docomoIndex = 17,
                        isDestructive = true,
                        isFocused = selectedIndex == 4,
                        jumpNumber = 5,
                        enabled = !isBusy,
                        marqueeWhenFocused = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedIndex = 4
                            runMenuAction(4)
                        }
                    )
                } else if (isScanningConfigs) {
                    Text(
                        text = "Scanning emulator configs…",
                        color = theme.fgTextColor.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else if (detectedConfigs.isEmpty()) {
                    Text(
                        text = "No config.json found. Link AbyssME's configs folder or launch a game once first.",
                        color = theme.fgTextColor.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        state = captureListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(
                            detectedConfigs,
                            key = { _, item -> item.documentUri ?: item.rootFilePath ?: item.gameId }
                        ) { index, entry ->
                            KawaiiDialogItem(
                                text = entry.gameId,
                                docomoIndex = 72,
                                isFocused = selectedIndex == index,
                                jumpNumber = (index + 1).takeIf { it <= MAX_JUMP_ROWS },
                                enabled = !isBusy,
                                marqueeWhenFocused = true,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    selectedIndex = index
                                    captureFromEntry(entry)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Behavior → Clamshell & Lid sub-screen: lid open/close behavior and outer AT-M120 clamshell LEDs.
 */
@Composable
internal fun ClamshellLidSettingsScreen(
    onBack: () -> Unit,
    onOpenLidLedStudio: () -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by rememberSettingsFocusIndex(initialFocusedIndex, onFocusedIndexChange)
    var sawCenterDown by remember { mutableStateOf(false) }
    var showLidLedColorPicker by remember { mutableStateOf(false) }

    val lidAction by PreferencesManager.lidOpenAction.collectAsState()
    val lidLed by PreferencesManager.isLidLedEnabled.collectAsState()
    val lidLedCharging by PreferencesManager.isLidLedChargingEnabled.collectAsState()
    val lidLedColor by PreferencesManager.lidLedColor.collectAsState()
    val showRootOptions by PreferencesManager.showRootOptions.collectAsState()
    val lidLedHardware by LidLedManager.hasLedHardware.collectAsState()
    val showLidLedRows = showRootOptions && lidLedHardware != false
    val lidDetectionActive = remember { PreferencesManager.isLidDetectionActive(context) }
    var screenOffReset by remember {
        mutableStateOf(PreferencesManager.isNonRootScreenOffResetEnabled(context, lidDetectionActive))
    }

    val items = remember(showRootOptions, lidDetectionActive, showLidLedRows) {
        clamshellLidRowIds(
            showRootOptions = showRootOptions,
            showScreenOffReset = showScreenOffResetRow(showRootOptions, lidDetectionActive),
            showLidLedRows = showLidLedRows
        )
    }
    val totalItems = items.size

    // Keep focus on a real row if the list shrinks
    LaunchedEffect(totalItems) {
        focusedIndex = focusedIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    }

    fun toggleScreenOffReset() {
        screenOffReset = !screenOffReset
        PreferencesManager.setNonRootScreenOffResetEnabled(context, screenOffReset)
    }

    fun isRowFocused(row: ClamshellLidRowId): Boolean = items.getOrNull(focusedIndex) == row.id
    fun jumpNumber(row: ClamshellLidRowId): Int? =
        (items.indexOf(row.id) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + totalItems) % totalItems
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun stepLidAction(direction: Int) {
        PreferencesManager.setLidOpenAction(
            context,
            if (direction > 0) lidAction.next() else lidAction.previous()
        )
    }

    fun stepLedColor(direction: Int) {
        val colors = LidLedManager.PRESET_COLORS
        val index = colors.indexOfFirst { it.id.equals(lidLedColor, ignoreCase = true) }
        val next = if (direction > 0) {
            if (index < 0 || index == colors.lastIndex) 0 else index + 1
        } else {
            if (index <= 0) colors.lastIndex else index - 1
        }
        PreferencesManager.setLidLedColor(context, colors[next].id)
    }

    fun activateRow(id: String?) {
        when (id) {
            ClamshellLidRowId.LID_OPEN.id -> stepLidAction(1)
            ClamshellLidRowId.SCREEN_OFF_RESET.id -> toggleScreenOffReset()
            ClamshellLidRowId.LID_LED.id -> {
                val enabled = !lidLed
                PreferencesManager.setLidLedEnabled(context, enabled)
                if (enabled) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
            }
            ClamshellLidRowId.LED_COLOR.id -> showLidLedColorPicker = true
            ClamshellLidRowId.LED_STUDIO.id -> onOpenLidLedStudio()
            ClamshellLidRowId.LED_CHARGING.id -> {
                PreferencesManager.setLidLedChargingEnabled(context, !lidLedCharging)
                LidLedManager.updateLedState()
            }
        }
    }

    fun adjustFocused(direction: Int): Boolean {
        when (items.getOrNull(focusedIndex)) {
            ClamshellLidRowId.LID_OPEN.id -> stepLidAction(direction)
            ClamshellLidRowId.LED_COLOR.id -> stepLedColor(direction)
            else -> return false
        }
        SoundEffectManager.play(context, SoundEffect.NAV)
        return true
    }

    ScrollToFocusedItem(listState) {
        when (items.getOrNull(focusedIndex)) {
            ClamshellLidRowId.LID_OPEN.id, ClamshellLidRowId.SCREEN_OFF_RESET.id -> 0
            else -> 1
        }
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Clamshell & Lid",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == totalItems - 1 },
        headerDocomoIndex = 72,
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
                        activateRow(items.getOrNull(focusedIndex))
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        activateRow(items.getOrNull(targetIndex))
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        if (!adjustFocused(direction)) stepIndex(direction)
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
            item(contentType = "behavior_clamshell_card") {
                SettingsCard(categoryTitle = "Clamshell Behavior") {
                    SettingsStepperRow(
                        title = "Lid Open Action",
                        subtitle = if (ClamshellManager.hasHardwareLidSource()) {
                            "Action when the clamshell opens"
                        } else {
                            "Action when opening or waking the screen"
                        },
                        valueDisplay = lidAction.label,
                        isFocused = isRowFocused(ClamshellLidRowId.LID_OPEN),
                        jumpNumber = jumpNumber(ClamshellLidRowId.LID_OPEN),
                        onDecrement = { stepLidAction(-1) },
                        onIncrement = { stepLidAction(1) }
                    )
                    if (ClamshellLidRowId.SCREEN_OFF_RESET.id in items) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Flip Reset on Sleep",
                            subtitle = "Return to Home and close popups when screen sleeps",
                            checked = screenOffReset,
                            isFocused = isRowFocused(ClamshellLidRowId.SCREEN_OFF_RESET),
                            jumpNumber = jumpNumber(ClamshellLidRowId.SCREEN_OFF_RESET),
                            onCheckedChange = { toggleScreenOffReset() }
                        )
                    }
                }
            }
            if (showLidLedRows) {
                item(contentType = "behavior_lid_led_card") {
                    SettingsCard(categoryTitle = "Outer Lid LEDs") {
                        SettingsToggleRow(
                            title = "Outer Lid LED",
                            subtitle = "Twinkle the exterior LED while the lid is closed ✦",
                            checked = lidLed,
                            isFocused = isRowFocused(ClamshellLidRowId.LID_LED),
                            jumpNumber = jumpNumber(ClamshellLidRowId.LID_LED),
                            onCheckedChange = {
                                PreferencesManager.setLidLedEnabled(context, it)
                                if (it) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Outer LED Color",
                            subtitle = "Pick the exterior notification light accent",
                            valueDisplay = LidLedManager.getColorLabel(lidLedColor),
                            isFocused = isRowFocused(ClamshellLidRowId.LED_COLOR),
                            jumpNumber = jumpNumber(ClamshellLidRowId.LED_COLOR),
                            onClick = { showLidLedColorPicker = true },
                            onDecrement = { stepLedColor(-1) },
                            onIncrement = { stepLedColor(1) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsNavigationRow(
                            title = "Lid LED Studio",
                            subtitle = "Custom light shows, .mochibunled import & step maker",
                            docomoIndex = 14,
                            isFocused = isRowFocused(ClamshellLidRowId.LED_STUDIO),
                            jumpNumber = jumpNumber(ClamshellLidRowId.LED_STUDIO),
                            onClick = onOpenLidLedStudio
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Charging LED",
                            subtitle = "Glow the exterior LED softly while charging",
                            checked = lidLedCharging,
                            isFocused = isRowFocused(ClamshellLidRowId.LED_CHARGING),
                            jumpNumber = jumpNumber(ClamshellLidRowId.LED_CHARGING),
                            onCheckedChange = {
                                PreferencesManager.setLidLedChargingEnabled(context, it)
                                LidLedManager.updateLedState()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showLidLedColorPicker) {
        LidLedColorPickerDialog(
            currentColor = lidLedColor,
            onColorSelected = {
                PreferencesManager.setLidLedColor(context, it)
                SoundEffectManager.play(context, SoundEffect.SELECT)
            },
            onDismiss = { showLidLedColorPicker = false }
        )
    }
}
