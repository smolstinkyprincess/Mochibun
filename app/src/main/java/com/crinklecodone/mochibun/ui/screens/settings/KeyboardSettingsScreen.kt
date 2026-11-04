package com.crinklecodone.mochibun.ui.screens.settings

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.service.MochibunImeStatus
import com.crinklecodone.mochibun.ui.components.getDigitFromKeyCode
import com.crinklecodone.mochibun.ui.dialogs.isBackDismissKey
import com.crinklecodone.mochibun.ui.dialogs.isCenterActivateKey
import com.crinklecodone.mochibun.ui.dialogs.requestFocusSafely
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
    var holdDelayMs by remember { mutableIntStateOf(PreferencesManager.getImeHoldDelayMs(context)) }
    var holdVibration by remember { mutableStateOf(PreferencesManager.isImeHoldVibrationEnabled(context)) }

    fun toggleHoldVibration() {
        holdVibration = !holdVibration
        PreferencesManager.setImeHoldVibrationEnabled(context, holdVibration)
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

    val totalItems = 4 // 0: Keyboard status, 1: Switch keyboard, 2: Hold delay, 3: Hold vibration

    LaunchedEffect(focusedIndex) {
        val target = if (focusedIndex <= 1) 0 else 1
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visible.isEmpty() || target in visible) return@LaunchedEffect
        listState.animateScrollToItem(target)
    }

    fun stepHoldDelay(delta: Int) {
        val next = (holdDelayMs + delta).coerceIn(
            PreferencesManager.MIN_IME_HOLD_DELAY_MS,
            PreferencesManager.MAX_IME_HOLD_DELAY_MS
        )
        if (next == holdDelayMs) return
        holdDelayMs = next
        PreferencesManager.setImeHoldDelayMs(context, next)
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun activate(index: Int) {
        when (index) {
            0 -> activateMochibunKeyboard(context, scope, imeState, ::refresh)
            1 -> MochibunImeStatus.showPicker(context)
            3 -> toggleHoldVibration()
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Keyboard",
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
                    if (event.type == KeyEventType.KeyDown && focusedIndex == 2) {
                        stepHoldDelay(if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -HOLD_STEP_MS else HOLD_STEP_MS)
                    } else if (event.type == KeyEventType.KeyDown && focusedIndex == 3) {
                        toggleHoldVibration()
                        SoundEffectManager.play(context, SoundEffect.NAV)
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
                SettingsCard(categoryTitle = "Typing") {
                    SettingsStepperRow(
                        title = "Hold for Digit",
                        subtitle = "Hold time to type a number",
                        valueDisplay = "$holdDelayMs ms",
                        isFocused = focusedIndex == 2,
                        jumpNumber = 3,
                        onDecrement = { stepHoldDelay(-HOLD_STEP_MS) },
                        onIncrement = { stepHoldDelay(HOLD_STEP_MS) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Hold Vibration",
                        subtitle = "Buzz when a hold types a number",
                        checked = holdVibration,
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onCheckedChange = { toggleHoldVibration() }
                    )
                }
            }

            item {
                SettingsCard(categoryTitle = "Key Guide") {
                    SettingsInfoRow(label = "Tap 2–9", value = "cycle letters")
                    SettingsInfoRow(label = "Tap 1", value = "symbols  ♡ ★ ♪")
                    SettingsInfoRow(label = "Tap 0", value = "space")
                    SettingsInfoRow(label = "Tap *", value = "shift  abc › Abc › ABC")
                    SettingsInfoRow(label = "Hold 0–9 / *", value = "type the number / *")
                    SettingsInfoRow(label = "◀ ▶", value = "pick a candidate")
                    SettingsInfoRow(label = "Tap #", value = "space")
                    SettingsInfoRow(label = "OK", value = "commit")
                    SettingsInfoRow(label = "DEL", value = "delete")
                    SettingsInfoRow(label = "BACK", value = "cancel candidate")
                    SettingsInfoRow(label = "Hold #", value = "kaomoji wheel")
                }
            }
        }
    }
}

private const val HOLD_STEP_MS = 50
