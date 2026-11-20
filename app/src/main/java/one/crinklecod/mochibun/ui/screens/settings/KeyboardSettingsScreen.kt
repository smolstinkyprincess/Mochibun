package one.crinklecod.mochibun.ui.screens.settings

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
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.RootShell
import one.crinklecod.mochibun.data.model.AppInfo
import one.crinklecod.mochibun.data.model.LauncherApp
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.service.MochibunImeStatus
import one.crinklecod.mochibun.ui.components.getDigitFromKeyCode
import one.crinklecod.mochibun.ui.dialogs.KeyGuideTab
import one.crinklecod.mochibun.ui.dialogs.MochibunKeyGuideDialog
import one.crinklecod.mochibun.ui.dialogs.QuickSnippetsDialog
import one.crinklecod.mochibun.ui.dialogs.isCenterActivateKey
import one.crinklecod.mochibun.ui.dialogs.requestFocusSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
internal fun KeyboardSettingsScreen(
    onBack: () -> Unit,
    installedApps: () -> List<LauncherApp> = { emptyList() },
    onNestedBackHandlerChanged: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var sawCenterDown by remember { mutableStateOf(false) }

    // Seeded from the last-known state; only the very first open in a process reads it inline
    // (Settings.Secure + an InputMethodManager binder call when we are not the default).
    var imeState by remember {
        mutableStateOf(MochibunImeStatus.lastKnownState ?: MochibunImeStatus.state(context))
    }
    var holdForDigit by remember { mutableStateOf(PreferencesManager.isImeHoldForDigitEnabled(context)) }
    var autoCommit by remember { mutableStateOf(PreferencesManager.isImeAutoCommitEnabled(context)) }
    var autoCommitMs by remember { mutableIntStateOf(PreferencesManager.getImeIdleCommitMs(context)) }
    var holdVibration by remember { mutableStateOf(PreferencesManager.isImeHoldVibrationEnabled(context)) }
    var showKeyGuide by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    val snippets by PreferencesManager.quickSnippets.collectAsState()

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
            5 -> {
                holdVibration = !holdVibration
                PreferencesManager.setImeHoldVibrationEnabled(context, holdVibration)
            }
            6 -> showKeyGuide = true
            7 -> showSnippets = true
        }
    }

    // T9 Auto-Commit Delay stepper; inert while Letter Auto-commit is off. Returns true if it moved.
    fun stepAutoCommitMs(direction: Int, wrap: Boolean = false): Boolean {
        if (!autoCommit) return false
        val next = if (wrap && direction > 0 && autoCommitMs >= PreferencesManager.MAX_IME_IDLE_COMMIT_MS) {
            PreferencesManager.MIN_IME_IDLE_COMMIT_MS
        } else {
            PreferencesManager.stepImeIdleCommitMs(autoCommitMs, direction)
        }
        if (next == autoCommitMs) return false
        autoCommitMs = next
        PreferencesManager.setImeIdleCommitMs(context, next)
        return true
    }

    // Runs on every resume / window-focus regain (incl. twice on open): keep the IPC off the UI thread.
    fun refresh() {
        scope.launch {
            imeState = withContext(Dispatchers.IO) { MochibunImeStatus.state(context) }
        }
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

    // 0-1 keyboard setup, 2 hold for digit, 3 auto-commit, 4 auto-commit delay, 5 haptic,
    // 6 key guide, 7 quick snippets
    val totalItems = 8

    ScrollToFocusedItem(listState) {
        when {
            focusedIndex <= 1 -> 0
            focusedIndex <= 6 -> 1
            else -> 2
        }
    }

    fun activate(index: Int) {
        when (index) {
            0 -> activateMochibunKeyboard(context, scope, imeState, ::refresh)
            1 -> MochibunImeStatus.showPicker(context)
            4 -> stepAutoCommitMs(1, wrap = true)
            else -> toggleSetting(index)
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Keyboard",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == totalItems - 1 },
        headerDocomoIndex = 153,
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
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        when (focusedIndex) {
                            2, 3, 5 -> {
                                toggleSetting(focusedIndex)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            4 -> SoundEffectManager.play(
                                context,
                                if (stepAutoCommitMs(direction)) SoundEffect.NAV else SoundEffect.BOUNDARY
                            )
                            else -> focusedIndex = (focusedIndex + direction + totalItems) % totalItems
                        }
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
            item(contentType = "keyboard_ime_card") {
                SettingsCard(categoryTitle = "Mochibun Keyboard") {
                    SettingsNavigationRow(
                        title = "Keitai Keyboard",
                        subtitle = keyboardStatusSubtitle(imeState),
                        icon = "✓",
                        docomoIndex = if (imeState == MochibunImeStatus.State.DEFAULT) null else 153,
                        isFocused = focusedIndex == 0,
                        jumpNumber = 1,
                        onClick = { activate(0) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Switch Keyboard",
                        subtitle = "Pick another installed input method",
                        icon = "⇄",
                        isFocused = focusedIndex == 1,
                        jumpNumber = 2,
                        onClick = { activate(1) }
                    )
                }
            }

            item(contentType = "keyboard_input_card") {
                SettingsCard(categoryTitle = "Keypad Feel") {
                    SettingsToggleRow(
                        title = "Hold for Digit",
                        subtitle = "Long-press a number key to type its digit",
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
                        title = "Auto-Commit Letters",
                        subtitle = "Lock in multi-tap letters after a tiny pause",
                        checked = autoCommit,
                        isFocused = focusedIndex == 3,
                        jumpNumber = 4,
                        onCheckedChange = {
                            autoCommit = it
                            PreferencesManager.setImeAutoCommitEnabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsStepperRow(
                        title = "Auto-Commit Delay",
                        subtitle = if (autoCommit) {
                            "Pause before a multi-tap letter is committed"
                        } else {
                            "Turn on Auto-Commit Letters to use"
                        },
                        valueDisplay = if (autoCommit) "$autoCommitMs ms" else "Off",
                        isFocused = focusedIndex == 4,
                        jumpNumber = 5,
                        onDecrement = { stepAutoCommitMs(-1) },
                        onIncrement = { stepAutoCommitMs(1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Key Hold Haptic",
                        subtitle = "Gentle buzz when holding a key types a number",
                        checked = holdVibration,
                        isFocused = focusedIndex == 5,
                        jumpNumber = 6,
                        onCheckedChange = {
                            holdVibration = it
                            PreferencesManager.setImeHoldVibrationEnabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Key Guide",
                        subtitle = "Cheatsheet for physical keypad & button shortcuts",
                        icon = "ⓘ",
                        isFocused = focusedIndex == 6,
                        jumpNumber = 7,
                        onClick = { showKeyGuide = true }
                    )
                }
            }

            item(contentType = "keyboard_snippets_card") {
                SettingsCard(categoryTitle = "Clips & Snippets") {
                    SettingsNavigationRow(
                        title = "Quick Snippets",
                        subtitle = "${snippets.size}/${PreferencesManager.QUICK_SNIPPET_MAX_COUNT} pinned • hold #, then ✱ or 0",
                        icon = "★",
                        isFocused = focusedIndex == 7,
                        jumpNumber = 8,
                        onClick = { showSnippets = true }
                    )
                }
            }
        }
    }

    if (showSnippets) {
        QuickSnippetsDialog(onDismiss = { showSnippets = false })
    }

    if (showKeyGuide) {
        val appOptions = remember(installedApps) {
            installedApps().map { AppInfo(it.packageName, it.name) }
        }
        MochibunKeyGuideDialog(
            initialTab = KeyGuideTab.LAUNCHER,
            installedApps = appOptions,
            onNestedBackHandlerChanged = onNestedBackHandlerChanged,
            onDismiss = { showKeyGuide = false }
        )
    }
}
