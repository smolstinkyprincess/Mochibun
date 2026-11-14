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
import androidx.compose.runtime.mutableIntStateOf
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
import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.data.prefs.LidOpenAction
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.LidLedColorPickerDialog
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely

/**
 * Visible rows of the Layout & Features screen, in on-screen order. Focus index = list index,
 * jump number = index + 1, so hiding rows never leaves a gap.
 */
internal fun behaviorRowIds(
    hasCreateFolder: Boolean,
    showRootOptions: Boolean,
    showScreenOffReset: Boolean,
    showLidLedRows: Boolean = showRootOptions
): List<String> = buildList {
    if (hasCreateFolder) add("create_folder")
    addAll(listOf("folder_grid", "hidden_apps", "lid_open"))
    if (showScreenOffReset) add("screen_off_reset")
    // Outer LED rows write vendor sysfs nodes, which needs root and the AT-M120 LED chip
    if (showRootOptions && showLidLedRows) addAll(listOf("lid_led", "led_color", "led_studio", "led_charging"))
}

/**
 * Screen-Off Flip Reset is offered unless root options are shown and the flip close is already
 * detected (root hall reader / LSPosed key hook).
 */
internal fun showScreenOffResetRow(showRootOptions: Boolean, lidDetectionActive: Boolean): Boolean =
    !showRootOptions || !lidDetectionActive

@Composable
internal fun BehaviorSettingsSubScreen(
    onBack: () -> Unit,
    onOpenHiddenApps: (() -> Unit)?,
    onCreateFolderClick: (() -> Unit)?,
    onOpenLidLedStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }
    var showLidLedColorPicker by remember { mutableStateOf(false) }

    val folderGrid by PreferencesManager.isFolderGridMode.collectAsState()
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

    val items = remember(onCreateFolderClick, showRootOptions, lidDetectionActive, showLidLedRows) {
        behaviorRowIds(
            hasCreateFolder = onCreateFolderClick != null,
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

    fun jumpNumber(id: String): Int? =
        (items.indexOf(id) + 1).takeIf { it in 1..9 }

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

    fun activate(index: Int) {
        when (items.getOrNull(index)) {
            "create_folder" -> onCreateFolderClick?.invoke()
            "folder_grid" -> PreferencesManager.setFolderGridMode(context, !folderGrid)
            "hidden_apps" -> onOpenHiddenApps?.invoke()
            "lid_open" -> stepLidAction(1)
            "screen_off_reset" -> toggleScreenOffReset()
            "lid_led" -> {
                val enabled = !lidLed
                PreferencesManager.setLidLedEnabled(context, enabled)
                if (enabled) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
            }
            "led_color" -> showLidLedColorPicker = true
            "led_studio" -> onOpenLidLedStudio()
            "led_charging" -> {
                PreferencesManager.setLidLedChargingEnabled(context, !lidLedCharging)
                LidLedManager.updateLedState()
            }
        }
    }

    fun adjustFocused(direction: Int): Boolean {
        when (items.getOrNull(focusedIndex)) {
            "lid_open" -> stepLidAction(direction)
            "led_color" -> stepLedColor(direction)
            else -> return false
        }
        SoundEffectManager.play(context, SoundEffect.NAV)
        return true
    }

    LaunchedEffect(focusedIndex, items) {
        val target = when (items.getOrNull(focusedIndex)) {
            "create_folder", "folder_grid" -> 0
            "hidden_apps" -> 1
            else -> 2
        }
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || target in visible) return@LaunchedEffect
        listState.animateScrollToItem(target)
    }

    requestFocusSafely(focusRequester)
    SettingsScaffold(
        title = "Layout & Features",
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
                        activate(focusedIndex)
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems.coerceAtMost(9)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        focusedIndex = digit - 1
                        activate(focusedIndex)
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
            contentPadding = PaddingValues(top = 44.dp, bottom = 64.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                SettingsCard(categoryTitle = "Folder Options") {
                    if (onCreateFolderClick != null) {
                        SettingsNavigationRow(
                            title = "Create New Folder",
                            subtitle = "Group favorite apps into a folder",
                            icon = "📁",
                            isFocused = items.getOrNull(focusedIndex) == "create_folder",
                            jumpNumber = jumpNumber("create_folder"),
                            onClick = { activate(items.indexOf("create_folder")) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    SettingsToggleRow(
                        title = "2×2 Folder Grid",
                        subtitle = "Use a compact two-by-two folder layout",
                        checked = folderGrid,
                        isFocused = items.getOrNull(focusedIndex) == "folder_grid",
                        jumpNumber = jumpNumber("folder_grid"),
                        onCheckedChange = { PreferencesManager.setFolderGridMode(context, it) }
                    )
                }
            }
            item {
                SettingsCard(categoryTitle = "App Drawer") {
                    SettingsNavigationRow(
                        title = "Hidden Apps",
                        subtitle = "Manage hidden launcher applications",
                        icon = "🙈",
                        isFocused = items.getOrNull(focusedIndex) == "hidden_apps",
                        jumpNumber = jumpNumber("hidden_apps"),
                        onClick = { onOpenHiddenApps?.invoke() }
                    )
                }
            }
            item {
                SettingsCard(categoryTitle = "Clamshell & Outer LEDs") {
                    SettingsStepperRow(
                        title = "Lid Open Action",
                        subtitle = "Action when the clamshell opens",
                        valueDisplay = lidAction.label,
                        isFocused = items.getOrNull(focusedIndex) == "lid_open",
                        jumpNumber = jumpNumber("lid_open"),
                        onDecrement = { stepLidAction(-1) },
                        onIncrement = { stepLidAction(1) }
                    )
                    if ("screen_off_reset" in items) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Screen-Off Flip Reset",
                            subtitle = "Return to Home and close menus when the screen turns off",
                            checked = screenOffReset,
                            isFocused = items.getOrNull(focusedIndex) == "screen_off_reset",
                            jumpNumber = jumpNumber("screen_off_reset"),
                            onCheckedChange = { toggleScreenOffReset() }
                        )
                    }
                    if (showLidLedRows) {
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Outer Notification LED",
                            subtitle = "Pulse the exterior LED while the lid is closed",
                            checked = lidLed,
                            isFocused = items.getOrNull(focusedIndex) == "lid_led",
                            jumpNumber = jumpNumber("lid_led"),
                            onCheckedChange = {
                                PreferencesManager.setLidLedEnabled(context, it)
                                if (it) LidLedManager.updateLedState() else LidLedManager.clearAllLeds()
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsStepperRow(
                            title = "Outer LED Color",
                            subtitle = "Choose the exterior light accent",
                            valueDisplay = LidLedManager.getColorLabel(lidLedColor),
                            isFocused = items.getOrNull(focusedIndex) == "led_color",
                            jumpNumber = jumpNumber("led_color"),
                            onClick = { showLidLedColorPicker = true },
                            onDecrement = { stepLedColor(-1) },
                            onIncrement = { stepLedColor(1) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsNavigationRow(
                            title = "Lid LED Studio",
                            subtitle = "Notification animations, import & step maker",
                            icon = "✨",
                            isFocused = items.getOrNull(focusedIndex) == "led_studio",
                            jumpNumber = jumpNumber("led_studio"),
                            onClick = onOpenLidLedStudio
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsToggleRow(
                            title = "Outer Charging Indicator",
                            subtitle = "Light the exterior LED when charging",
                            checked = lidLedCharging,
                            isFocused = items.getOrNull(focusedIndex) == "led_charging",
                            jumpNumber = jumpNumber("led_charging"),
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
