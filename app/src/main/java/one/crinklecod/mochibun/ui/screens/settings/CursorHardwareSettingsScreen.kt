package one.crinklecod.mochibun.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import one.crinklecod.mochibun.data.model.AppInfo
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.prefs.CursorPreferences
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import one.crinklecod.mochibun.service.InputArbiter
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.KeyGuideTab
import one.crinklecod.mochibun.ui.dialogs.MochibunKeyGuideDialog
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import java.util.Locale

internal enum class CursorHardwareRowId(val id: String) {
    CURSOR_MOTION("cursor_motion"),
    CURSOR_GRAPHIC("cursor_graphic"),
    RESET_CURSOR("reset_cursor"),
    KEYPAD_REMAPPER("keypad_remapper"),
    SOFT_KEY_NAV("soft_key_nav"),
    CURSOR_TOGGLE_KEY("cursor_toggle_key"),
    DPAD_WRAP("dpad_wrap"),
    BOUNDARY_HAPTICS("boundary_haptics")
}

internal val CURSOR_HARDWARE_ROW_IDS: List<String> = CursorHardwareRowId.entries.map { it.id }

internal enum class CursorMotionRowId(val id: String) {
    BASE_SPEED("base_speed"),
    MAX_SPEED("max_speed"),
    ACCELERATION("acceleration"),
    CURSOR_SIZE("cursor_size"),
    SCROLL_STEP("scroll_step")
}

internal val CURSOR_MOTION_ROW_IDS: List<String> = CursorMotionRowId.entries.map { it.id }

internal fun cursorMotionSummarySubtitle(
    tapStepPx: Int,
    maxSpeed: Int,
    cursorSizeDp: Int
): String = "$tapStepPx–$maxSpeed px/s · ${cursorSizeDp}dp"

/**
 * Tier 2: Virtual Cursor Sub-Screen
 */
@Composable
internal fun CursorHardwareSettingsScreen(
    onBack: () -> Unit,
    onOpenCursorMotion: () -> Unit = {},
    onOpenHardwareButtons: (() -> Unit)? = null,
    installedApps: () -> List<LauncherApp> = { emptyList() },
    onNestedBackHandlerChanged: ((() -> Unit)?) -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val rows = CursorHardwareRowId.entries
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    val tapStepPx = remember { CursorPreferences.getTapStepPx(context) }
    val maxSpeed = remember { CursorPreferences.getMaxSpeed(context) }
    val cursorSizeDp = remember { CursorPreferences.getCursorSizeDp(context) }
    var customUri by remember { mutableStateOf(CursorPreferences.getCustomCursorUri(context)) }
    var toggleKeyCode by remember { mutableIntStateOf(CursorPreferences.getToggleKeyCode(context)) }
    val softKeyNav by PreferencesManager.isSoftKeyNavEnabled.collectAsState()
    val dpadWrapAround by PreferencesManager.isDpadWrapAround.collectAsState()
    val boundaryHaptics by PreferencesManager.isDpadBoundaryHaptics.collectAsState()
    var lastKeyPressedReadout by remember { mutableStateOf("Press any key...") }
    var showKeypadRemapper by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val observer: (KeyEvent) -> Unit = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                lastKeyPressedReadout = formatKeyDiagnosticReadout(event.keyCode, event.scanCode)
            }
        }
        HardwareRemapManager.keyDiagnosticListener = observer
        onDispose {
            if (HardwareRemapManager.keyDiagnosticListener === observer) {
                HardwareRemapManager.keyDiagnosticListener = null
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            CursorPreferences.setCustomCursorUri(context, uri)
            customUri = uri
        }
    }

    fun isRowFocused(row: CursorHardwareRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: CursorHardwareRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    // Card that holds the focused row; read lazily by ScrollToFocusedItem (not in composition).
    fun targetCardIndex(): Int =
        when (rows.getOrNull(focusedIndex)) {
            CursorHardwareRowId.CURSOR_MOTION,
            CursorHardwareRowId.CURSOR_GRAPHIC,
            CursorHardwareRowId.RESET_CURSOR -> 0
            CursorHardwareRowId.KEYPAD_REMAPPER,
            CursorHardwareRowId.SOFT_KEY_NAV,
            CursorHardwareRowId.CURSOR_TOGGLE_KEY -> 1
            CursorHardwareRowId.DPAD_WRAP,
            CursorHardwareRowId.BOUNDARY_HAPTICS -> 2
            null -> 0
        }

    fun stepToggleKey(direction: Int) {
        toggleKeyCode = CursorPreferences.stepToggleKeyCode(toggleKeyCode, direction)
        CursorPreferences.setToggleKeyCode(context, toggleKeyCode)
        // The key hook may hold a stale typing state from before `*` was chosen
        if (toggleKeyCode == KeyEvent.KEYCODE_STAR) InputArbiter.pushInputActiveToKeyHook(context)
    }

    fun adjustRow(row: CursorHardwareRowId?, direction: Int): Boolean {
        if (row != CursorHardwareRowId.CURSOR_TOGGLE_KEY) return false
        stepToggleKey(direction)
        return true
    }

    fun activateRow(row: CursorHardwareRowId) {
        when (row) {
            CursorHardwareRowId.CURSOR_MOTION -> onOpenCursorMotion()
            CursorHardwareRowId.CURSOR_GRAPHIC -> filePicker.launch(arrayOf("image/gif", "image/png"))
            CursorHardwareRowId.RESET_CURSOR -> {
                CursorPreferences.setCustomCursorUri(context, null)
                customUri = null
                focusedIndex = rows.indexOf(CursorHardwareRowId.CURSOR_GRAPHIC)
            }
            CursorHardwareRowId.KEYPAD_REMAPPER -> showKeypadRemapper = true
            CursorHardwareRowId.SOFT_KEY_NAV -> PreferencesManager.setSoftKeyNavEnabled(context, !softKeyNav)
            CursorHardwareRowId.CURSOR_TOGGLE_KEY -> stepToggleKey(1)
            CursorHardwareRowId.DPAD_WRAP -> PreferencesManager.setDpadWrapAround(context, !dpadWrapAround)
            CursorHardwareRowId.BOUNDARY_HAPTICS -> PreferencesManager.setDpadBoundaryHaptics(context, !boundaryHaptics)
        }
    }

    fun stepIndex(direction: Int) {
        val next = focusedIndex + direction
        focusedIndex = if (dpadWrapAround) {
            (next + rows.size) % rows.size
        } else {
            next.coerceIn(0, rows.lastIndex)
        }
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    ScrollToFocusedItem(listState) { targetCardIndex() }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Virtual Cursor",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 108,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (event.type == KeyEventType.KeyDown) {
                    lastKeyPressedReadout = formatKeyDiagnosticReadout(native.keyCode, native.scanCode)
                }
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activateRow(rows[targetIndex])
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        val handled = adjustRow(rows.getOrNull(focusedIndex), direction)
                        if (handled) {
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else {
                            stepIndex(direction)
                        }
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
            item(contentType = "cursor_sprite_card") {
                SettingsCard(categoryTitle = "Pointer & Sprite") {
                    SettingsNavigationRow(
                        title = "Cursor Motion",
                        subtitle = cursorMotionSummarySubtitle(tapStepPx, maxSpeed, cursorSizeDp),
                        docomoIndex = 121,
                        isFocused = isRowFocused(CursorHardwareRowId.CURSOR_MOTION),
                        jumpNumber = jumpNumber(CursorHardwareRowId.CURSOR_MOTION),
                        onClick = { activateRow(CursorHardwareRowId.CURSOR_MOTION) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Cursor Graphic",
                        subtitle = if (customUri != null) "Custom sprite active • choose a .png or .gif" else "Default paw • choose a .png or .gif",
                        docomoIndex = 121,
                        isFocused = isRowFocused(CursorHardwareRowId.CURSOR_GRAPHIC),
                        jumpNumber = jumpNumber(CursorHardwareRowId.CURSOR_GRAPHIC),
                        onClick = { activateRow(CursorHardwareRowId.CURSOR_GRAPHIC) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsActionRow(
                        title = "Reset Cursor Sprite",
                        isFocused = isRowFocused(CursorHardwareRowId.RESET_CURSOR),
                        isDestructive = true,
                        jumpNumber = jumpNumber(CursorHardwareRowId.RESET_CURSOR),
                        onClick = { activateRow(CursorHardwareRowId.RESET_CURSOR) }
                    )
                }
            }

            item(contentType = "cursor_keypad_card") {
                SettingsCard(categoryTitle = "Physical Keypad") {
                    SettingsNavigationRow(
                        title = "Keypad Remapper",
                        subtitle = "Bind custom actions to your physical buttons",
                        docomoIndex = 153,
                        isFocused = isRowFocused(CursorHardwareRowId.KEYPAD_REMAPPER),
                        jumpNumber = jumpNumber(CursorHardwareRowId.KEYPAD_REMAPPER),
                        onClick = { activateRow(CursorHardwareRowId.KEYPAD_REMAPPER) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Soft-Key Navigation",
                        subtitle = "Use Recents & Back as LSK / RSK on Home and Drawer",
                        checked = softKeyNav,
                        isFocused = isRowFocused(CursorHardwareRowId.SOFT_KEY_NAV),
                        jumpNumber = jumpNumber(CursorHardwareRowId.SOFT_KEY_NAV),
                        onCheckedChange = { PreferencesManager.setSoftKeyNavEnabled(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Cursor Toggle Key",
                        subtitle = if (toggleKeyCode == KeyEvent.KEYCODE_STAR) {
                            "Shows / hides the cursor (types * in text fields)"
                        } else {
                            "Shows / hides the cursor (the F4 side key always works)"
                        },
                        valueDisplay = CursorPreferences.toggleKeyLabel(toggleKeyCode),
                        isFocused = isRowFocused(CursorHardwareRowId.CURSOR_TOGGLE_KEY),
                        jumpNumber = jumpNumber(CursorHardwareRowId.CURSOR_TOGGLE_KEY),
                        onDecrement = { stepToggleKey(-1) },
                        onIncrement = { stepToggleKey(1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsInfoRow(
                        label = "Last Key Pressed",
                        value = lastKeyPressedReadout
                    )
                }
            }

            item(contentType = "cursor_dpad_card") {
                SettingsCard(categoryTitle = "D-Pad Navigation") {
                    SettingsToggleRow(
                        title = "D-Pad Wrap Around",
                        subtitle = "Loop selection smoothly around menu edges",
                        checked = dpadWrapAround,
                        isFocused = isRowFocused(CursorHardwareRowId.DPAD_WRAP),
                        jumpNumber = jumpNumber(CursorHardwareRowId.DPAD_WRAP),
                        onCheckedChange = { PreferencesManager.setDpadWrapAround(context, it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Boundary Haptics",
                        subtitle = "Tiny tactile tick when focus hits an edge",
                        checked = boundaryHaptics,
                        isFocused = isRowFocused(CursorHardwareRowId.BOUNDARY_HAPTICS),
                        jumpNumber = jumpNumber(CursorHardwareRowId.BOUNDARY_HAPTICS),
                        onCheckedChange = { PreferencesManager.setDpadBoundaryHaptics(context, it) }
                    )
                }
            }
        }
    }

    if (showKeypadRemapper) {
        val appOptions = remember(installedApps) {
            installedApps().map { AppInfo(it.packageName, it.name) }
        }
        MochibunKeyGuideDialog(
            initialTab = KeyGuideTab.REMAP,
            installedApps = appOptions,
            onNestedBackHandlerChanged = onNestedBackHandlerChanged,
            onDismiss = { showKeypadRemapper = false }
        )
    }
}

/**
 * Virtual Cursor → Cursor Motion sub-screen: speed, acceleration, cursor size, and scroll step.
 */
@Composable
internal fun CursorMotionSettingsScreen(
    onBack: () -> Unit,
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val rows = CursorMotionRowId.entries
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

    var tapStepPx by remember { mutableIntStateOf(CursorPreferences.getTapStepPx(context)) }
    var maxSpeed by remember { mutableIntStateOf(CursorPreferences.getMaxSpeed(context)) }
    var accelRampMs by remember { mutableIntStateOf(CursorPreferences.getAccelRampMs(context)) }
    var cursorSizeDp by remember { mutableIntStateOf(CursorPreferences.getCursorSizeDp(context)) }
    var scrollStepPx by remember { mutableIntStateOf(CursorPreferences.getScrollStepPx(context)) }
    val dpadWrapAround by PreferencesManager.isDpadWrapAround.collectAsState()

    fun isRowFocused(row: CursorMotionRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: CursorMotionRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun adjustRow(row: CursorMotionRowId, direction: Int) {
        when (row) {
            CursorMotionRowId.BASE_SPEED -> {
                tapStepPx = (tapStepPx + direction)
                    .coerceIn(CursorPreferences.MIN_TAP_STEP_PX, CursorPreferences.MAX_TAP_STEP_PX)
                CursorPreferences.setTapStepPx(context, tapStepPx)
            }
            CursorMotionRowId.MAX_SPEED -> {
                maxSpeed = (maxSpeed + direction * CursorPreferences.MAX_SPEED_INCREMENT)
                    .coerceIn(CursorPreferences.MIN_MAX_SPEED, CursorPreferences.MAX_MAX_SPEED)
                CursorPreferences.setMaxSpeed(context, maxSpeed)
            }
            CursorMotionRowId.ACCELERATION -> {
                accelRampMs = (accelRampMs + direction * CursorPreferences.ACCEL_RAMP_INCREMENT_MS)
                    .coerceIn(CursorPreferences.MIN_ACCEL_RAMP_MS, CursorPreferences.MAX_ACCEL_RAMP_MS)
                CursorPreferences.setAccelRampMs(context, accelRampMs)
            }
            CursorMotionRowId.CURSOR_SIZE -> {
                cursorSizeDp = (cursorSizeDp + direction * 4).coerceIn(20, 52)
                CursorPreferences.setCursorSizeDp(context, cursorSizeDp)
            }
            CursorMotionRowId.SCROLL_STEP -> {
                scrollStepPx = (scrollStepPx + direction * CursorPreferences.SCROLL_STEP_INCREMENT_PX)
                    .coerceIn(CursorPreferences.MIN_SCROLL_STEP_PX, CursorPreferences.MAX_SCROLL_STEP_PX)
                CursorPreferences.setScrollStepPx(context, scrollStepPx)
            }
        }
    }

    fun stepIndex(direction: Int) {
        val next = focusedIndex + direction
        focusedIndex = if (dpadWrapAround) {
            (next + rows.size) % rows.size
        } else {
            next.coerceIn(0, rows.lastIndex)
        }
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    ScrollToFocusedItem(listState) { 0 }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Cursor Motion",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 121,
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
                        rows.getOrNull(focusedIndex)?.let { adjustRow(it, 1) }
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        adjustRow(rows[targetIndex], 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        stepIndex(if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        rows.getOrNull(focusedIndex)?.let { adjustRow(it, direction) }
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
            item(contentType = "cursor_physics_card") {
                SettingsCard(categoryTitle = "Pointer Movement") {
                    SettingsStepperRow(
                        title = "Base Speed",
                        subtitle = "Fine pointer step on each tap",
                        valueDisplay = "$tapStepPx px",
                        isFocused = isRowFocused(CursorMotionRowId.BASE_SPEED),
                        jumpNumber = jumpNumber(CursorMotionRowId.BASE_SPEED),
                        onDecrement = { adjustRow(CursorMotionRowId.BASE_SPEED, -1) },
                        onIncrement = { adjustRow(CursorMotionRowId.BASE_SPEED, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Max Speed",
                        subtitle = "Top pointer glide speed when holding a direction",
                        valueDisplay = "$maxSpeed px/s",
                        isFocused = isRowFocused(CursorMotionRowId.MAX_SPEED),
                        jumpNumber = jumpNumber(CursorMotionRowId.MAX_SPEED),
                        onDecrement = { adjustRow(CursorMotionRowId.MAX_SPEED, -1) },
                        onIncrement = { adjustRow(CursorMotionRowId.MAX_SPEED, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Acceleration",
                        subtitle = "Ramp-up time to top speed (shorter = snappier)",
                        valueDisplay = String.format(Locale.US, "%.2f s", accelRampMs / 1000f),
                        isFocused = isRowFocused(CursorMotionRowId.ACCELERATION),
                        jumpNumber = jumpNumber(CursorMotionRowId.ACCELERATION),
                        onDecrement = { adjustRow(CursorMotionRowId.ACCELERATION, -1) },
                        onIncrement = { adjustRow(CursorMotionRowId.ACCELERATION, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Cursor Size",
                        subtitle = "Size of your on-screen pointer sprite",
                        valueDisplay = "$cursorSizeDp dp",
                        isFocused = isRowFocused(CursorMotionRowId.CURSOR_SIZE),
                        jumpNumber = jumpNumber(CursorMotionRowId.CURSOR_SIZE),
                        onDecrement = { adjustRow(CursorMotionRowId.CURSOR_SIZE, -1) },
                        onIncrement = { adjustRow(CursorMotionRowId.CURSOR_SIZE, 1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Scroll Step",
                        subtitle = "Keys 2 (Up) & 8 (Down) page scroll distance",
                        valueDisplay = "$scrollStepPx px",
                        isFocused = isRowFocused(CursorMotionRowId.SCROLL_STEP),
                        jumpNumber = jumpNumber(CursorMotionRowId.SCROLL_STEP),
                        onDecrement = { adjustRow(CursorMotionRowId.SCROLL_STEP, -1) },
                        onIncrement = { adjustRow(CursorMotionRowId.SCROLL_STEP, 1) }
                    )
                }
            }
        }
    }
}

internal fun formatKeyDiagnosticReadout(keyCode: Int, scanCode: Int): String {
    val name = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    return "$name ($keyCode / scan $scanCode)"
}
