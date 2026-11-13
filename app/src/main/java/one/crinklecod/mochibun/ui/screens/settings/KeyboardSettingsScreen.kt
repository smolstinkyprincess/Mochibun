package one.crinklecod.mochibun.ui.screens.settings

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.service.MochibunImeStatus
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import kotlinx.coroutines.launch

/** Status label shared with the Permissions & Root row. */
internal fun keyboardStatusSubtitle(state: MochibunImeStatus.State): String = when (state) {
    MochibunImeStatus.State.DEFAULT -> "✔ Default keyboard • active"
    MochibunImeStatus.State.ENABLED -> "Enabled • OK to make default"
    MochibunImeStatus.State.NOT_ENABLED -> "✖ Off • OK to turn it on"
}

/**
 * Makes Mochibun the default keyboard in as few steps as possible: one root command when root is
 * granted, otherwise Android's keyboard list (not enabled yet) or the keyboard picker (enabled).
 */
internal fun activateMochibunKeyboard(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    state: MochibunImeStatus.State,
    onDone: () -> Unit
) {
    if (state == MochibunImeStatus.State.DEFAULT) {
        MochibunImeStatus.showPicker(context)
        return
    }
    if (RootShell.isGranted() == true) {
        scope.launch {
            val res = MochibunImeStatus.enableAndSelectViaRoot(context)
            if (res.isSuccess) {
                Toast.makeText(context, "Mochibun keyboard is now the default ♡", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Couldn't switch keyboard via root", Toast.LENGTH_SHORT).show()
            }
            onDone()
        }
        return
    }
    if (state == MochibunImeStatus.State.NOT_ENABLED) {
        MochibunImeStatus.openKeyboardSettings(context)
    } else {
        MochibunImeStatus.showPicker(context)
    }
}

/**
 * Keyboard (MochibunImeService) settings: default-keyboard setup, hold-for-digit delay and a key
 * guide.
 */
@Composable
internal fun KeyboardSettingsSubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    var imeState by remember { mutableStateOf(MochibunImeStatus.state(context)) }
    var holdForDigit by remember { mutableStateOf(PreferencesManager.isImeHoldForDigitEnabled(context)) }
    var autoCommit by remember { mutableStateOf(PreferencesManager.isImeAutoCommitEnabled(context)) }
    var holdVibration by remember { mutableStateOf(PreferencesManager.isImeHoldVibrationEnabled(context)) }
    var showKeyGuide by remember { mutableStateOf(false) }

    fun toggleSetting(index: Int) {
        when (index) {
            2 -> {
                holdForDigit = !holdForDigit
                PreferencesManager.setImeHoldForDigitEnabled(context, holdForDigit)
            }
            3 -> {
                autoCommit = !autoCommit
                PreferencesManager.setImeAutoCommitEnabled(context, autoCommit)
            }
            4 -> {
                holdVibration = !holdVibration
                PreferencesManager.setImeHoldVibrationEnabled(context, holdVibration)
            }
            5 -> showKeyGuide = true
        }
    }

    fun refresh() {
        imeState = MochibunImeStatus.state(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The keyboard picker is a system dialog over us, so closing it never fires ON_RESUME.
    // Refresh when our window gets focus back instead.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused -> if (focused) refresh() }
    }

    val totalItems = 6

    LaunchedEffect(focusedIndex) {
        val target = if (focusedIndex <= 1) 0 else 1
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || target in visible) return@LaunchedEffect
        listState.animateScrollToItem(target)
    }

    fun activate(index: Int) {
        when (index) {
            0 -> activateMochibunKeyboard(context, scope, imeState, ::refresh)
            1 -> MochibunImeStatus.showPicker(context)
            else -> toggleSetting(index)
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Typing",
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
                        activate(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..totalItems) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        focusedIndex = digit - 1
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activate(focusedIndex)
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
                        if (focusedIndex in 2..4) {
                            toggleSetting(focusedIndex)
                            SoundEffectManager.play(context, SoundEffect.NAV)
                        } else {
                            val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                            focusedIndex = (focusedIndex + direction + totalItems) % totalItems
                        }
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
                SettingsCard(categoryTitle = "Mochibun Keyboard") {
                    SettingsNavigationRow(
                        title = "Keitai Multi-tap Keyboard",
                        subtitle = keyboardStatusSubtitle(imeState),
                        icon = if (imeState == MochibunImeStatus.State.DEFAULT) "✓" else "⌨",
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = { activate(0) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Switch Keyboard",
                        subtitle = "Choose another installed keyboard",
                        icon = "⇄",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = { activate(1) }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Input Behavior") {
                    SettingsToggleRow(
                        title = "Hold for Digit",
                        subtitle = "Hold a keypad key to type its number",
                        checked = holdForDigit,
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onCheckedChange = {
                            holdForDigit = it
                            PreferencesManager.setImeHoldForDigitEnabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Letter Auto-commit",
                        subtitle = "Commit the selected letter after a short pause",
                        checked = autoCommit,
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onCheckedChange = {
                            autoCommit = it
                            PreferencesManager.setImeAutoCommitEnabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Key Hold Haptic",
                        subtitle = "Buzz when a hold types a number",
                        checked = holdVibration,
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onCheckedChange = {
                            holdVibration = it
                            PreferencesManager.setImeHoldVibrationEnabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Key Guide",
                        subtitle = "View physical keypad and hardware key mappings",
                        icon = "ⓘ",
                        isFocused = focusedIndex == 5,
                        jumpNumber = 6,
                        onClick = { showKeyGuide = true }
                    )
                }
            }
        }
    }

    if (showKeyGuide) {
        AlertDialog(
            onDismissRequest = { showKeyGuide = false },
            title = { Text("Mochibun Key Guide") },
            text = {
                Text(
                    "LSK — Left soft key action\n" +
                        "RSK — Right soft key / Back action\n" +
                        "F4 side key — Toggle the virtual cursor\n" +
                        "Hold # — Open the Kaomoji Wheel\n\n" +
                        "Tap 2–9 — Cycle letters\n" +
                        "Tap 1 — Symbols\n" +
                        "Tap 0 / # — Space\n" +
                        "Tap * — Cycle letter case\n" +
                        "Hold 0–9 / * — Type the key value\n" +
                        "◀ / ▶ — Choose a candidate · OK — Commit · DEL — Delete"
                )
            },
            confirmButton = {
                TextButton(onClick = { showKeyGuide = false }) {
                    Text("Close")
                }
            }
        )
    }
}
