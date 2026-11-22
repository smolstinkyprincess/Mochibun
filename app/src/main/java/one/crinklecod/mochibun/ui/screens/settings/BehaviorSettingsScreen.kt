package one.crinklecod.mochibun.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.ClamshellManager
import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.LidLedColorPickerDialog
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely

internal enum class BehaviorRowId(val id: String) {
    CREATE_FOLDER("create_folder"),
    FOLDER_SCATTER("folder_scatter"),
    HIDDEN_APPS("hidden_apps"),
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
            BehaviorRowId.CLAMSHELL_LID.id -> onOpenClamshellLid()
        }
    }

    ScrollToFocusedItem(listState) {
        when (items.getOrNull(focusedIndex)) {
            BehaviorRowId.CREATE_FOLDER.id, BehaviorRowId.FOLDER_SCATTER.id -> 0
            BehaviorRowId.HIDDEN_APPS.id -> 1
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
