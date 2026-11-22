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
import one.crinklecod.mochibun.data.ime.T9DictionaryEngine
import one.crinklecod.mochibun.data.ime.UserWordStats
import one.crinklecod.mochibun.data.ime.UserWordStore
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
 * Keyboard (MochibunImeService) settings: default-keyboard setup, hold-for-digit delay, T9
 * prediction and dictionary selection, and a key guide.
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
    var predictiveT9 by remember { mutableStateOf(PreferencesManager.isImePredictiveT9Enabled(context)) }
    var learnNewWords by remember { mutableStateOf(PreferencesManager.isImeT9LearnNewWordsEnabled(context)) }
    var learnedCount by remember { mutableIntStateOf(0) }
    var learnedRefreshTick by remember { mutableIntStateOf(0) }
    var clearArmed by remember { mutableStateOf(false) }
    var dictionaryLang by remember { mutableStateOf(PreferencesManager.getImeT9DictionaryLang(context)) }
    var dictionaries by remember {
        mutableStateOf(
            listOf(
                T9DictionaryEngine.DictionaryDescriptor(
                    id = T9DictionaryEngine.BUILT_IN_EN_ID,
                    displayName = T9DictionaryEngine.BUILT_IN_EN_DISPLAY_NAME
                )
            )
        )
    }
    var showKeyGuide by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    val snippets by PreferencesManager.quickSnippets.collectAsState()

    LaunchedEffect(learnedRefreshTick) {
        learnedCount = withContext(Dispatchers.IO) { countLearnedWordsForSettings(context) }
    }

    fun cycleDictionary(direction: Int): Boolean {
        if (dictionaries.size <= 1) return false
        val currentIdx = dictionaries.indexOfFirst { it.id == dictionaryLang }.coerceAtLeast(0)
        val nextIdx = (currentIdx + direction + dictionaries.size) % dictionaries.size
        val next = dictionaries[nextIdx]
        if (next.id == dictionaryLang) return false
        dictionaryLang = next.id
        PreferencesManager.setImeT9DictionaryLang(context, next.id)
        return true
    }

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
            6 -> {
                predictiveT9 = !predictiveT9
                PreferencesManager.setImePredictiveT9Enabled(context, predictiveT9)
            }
            7 -> cycleDictionary(1)
            8 -> showKeyGuide = true
            9 -> showSnippets = true
            10 -> {
                learnNewWords = !learnNewWords
                PreferencesManager.setImeT9LearnNewWordsEnabled(context, learnNewWords)
            }
        }
    }

    // Multi-tap Auto-Commit Delay stepper; inert while Auto-Commit Letters is off. Returns true if moved.
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

    // Runs on every resume / window-focus regain (incl. twice on open): keep IPC and disk scan off UI thread.
    fun refresh() {
        clearArmed = false
        learnedRefreshTick++
        scope.launch {
            val (nextImeState, nextDicts) = withContext(Dispatchers.IO) {
                MochibunImeStatus.state(context) to T9DictionaryEngine.availableDictionaries(context)
            }
            imeState = nextImeState
            dictionaries = nextDicts
            if (nextDicts.none { it.id == dictionaryLang }) {
                dictionaryLang = T9DictionaryEngine.BUILT_IN_EN_ID
                PreferencesManager.setImeT9DictionaryLang(context, dictionaryLang)
            }
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
    // 6 predictive T9, 7 T9 dictionary, 8 key guide, 9 quick snippets, 10 learn new words,
    // 11 clear learned words
    val totalItems = 12

    ScrollToFocusedItem(listState) {
        when {
            focusedIndex <= 1 -> 0
            focusedIndex <= 8 -> 1
            focusedIndex == 9 -> 2
            else -> 3
        }
    }

    fun activate(index: Int) {
        if (index != 11) clearArmed = false
        when (index) {
            0 -> activateMochibunKeyboard(context, scope, imeState, ::refresh)
            1 -> MochibunImeStatus.showPicker(context)
            4 -> stepAutoCommitMs(1, wrap = true)
            11 -> {
                if (!clearArmed) {
                    clearArmed = true
                } else {
                    clearAllLearnedWords(context)
                    clearArmed = false
                    learnedCount = 0
                    learnedRefreshTick++
                }
            }
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
                        clearArmed = false
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
                        clearArmed = false
                        focusedIndex = digit - 1
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activate(focusedIndex)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        clearArmed = false
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
                        clearArmed = false
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        when (focusedIndex) {
                            2, 3, 5, 6, 10 -> {
                                toggleSetting(focusedIndex)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            4 -> SoundEffectManager.play(
                                context,
                                if (stepAutoCommitMs(direction)) SoundEffect.NAV else SoundEffect.BOUNDARY
                            )
                            7 -> SoundEffectManager.play(
                                context,
                                if (cycleDictionary(direction)) SoundEffect.NAV else SoundEffect.BOUNDARY
                            )
                            11 -> Unit
                            else -> focusedIndex = (focusedIndex + direction + totalItems) % totalItems
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val activeDictIndex = dictionaries.indexOfFirst { it.id == dictionaryLang }.coerceAtLeast(0)
        val activeDictName = dictionaries.getOrNull(activeDictIndex)?.displayName
            ?: T9DictionaryEngine.BUILT_IN_EN_DISPLAY_NAME
        val dictSubtitle = if (dictionaries.size <= 1) {
            "$activeDictName • add .tsv/.txt to Android/data/one.crinklecod.mochibun/files/dictionaries"
        } else {
            "$activeDictName (${activeDictIndex + 1}/${dictionaries.size}) • ◀/▶ to switch"
        }

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
                    SettingsToggleRow(
                        title = "Predictive Text (T9)",
                        subtitle = "Cycle into offline T9 word prediction with *",
                        checked = predictiveT9,
                        isFocused = focusedIndex == 6,
                        jumpNumber = 7,
                        onCheckedChange = {
                            predictiveT9 = it
                            PreferencesManager.setImePredictiveT9Enabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "T9 Dictionary",
                        subtitle = dictSubtitle,
                        docomoIndex = 108,
                        isFocused = focusedIndex == 7,
                        jumpNumber = 8,
                        onClick = { activate(7) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Key Guide",
                        subtitle = "Cheatsheet for physical keypad & button shortcuts",
                        icon = "ⓘ",
                        isFocused = focusedIndex == 8,
                        jumpNumber = 9,
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
                        isFocused = focusedIndex == 9,
                        jumpNumber = null,
                        onClick = {
                            clearArmed = false
                            showSnippets = true
                        }
                    )
                }
            }

            item(contentType = "keyboard_learned_card") {
                LearnedWordsSettingsCard(
                    learnNewWords = learnNewWords,
                    learnedCount = learnedCount,
                    clearArmed = clearArmed,
                    isLearnFocused = focusedIndex == 10,
                    isClearFocused = focusedIndex == 11,
                    onLearnCheckedChange = { enabled ->
                        clearArmed = false
                        learnNewWords = enabled
                        PreferencesManager.setImeT9LearnNewWordsEnabled(context, enabled)
                    },
                    onClearClick = { activate(11) }
                )
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

private fun countLearnedWordsForSettings(context: android.content.Context): Int {
    val stats = UserWordStats().apply {
        loadFromJsonString(PreferencesManager.getImeT9LearnedWordsJson(context))
    }
    val userWords = UserWordStore().apply {
        loadFromJsonString(PreferencesManager.getImeT9UserWordsJson(context))
    }
    return stats.totalEntryCount() + userWords.visibleWordCount()
}

private fun clearAllLearnedWords(context: android.content.Context) {
    PreferencesManager.setImeT9LearnedWordsJson(context, PreferencesManager.DEFAULT_IME_T9_LEARNED_WORDS)
    PreferencesManager.setImeT9UserWordsJson(context, PreferencesManager.DEFAULT_IME_T9_USER_WORDS)
    PreferencesManager.bumpImeT9LearnedEpoch(context)
    Toast.makeText(context, "Learned words cleared ♡", Toast.LENGTH_SHORT).show()
}

internal fun learnedWordsClearSubtitle(count: Int, armed: Boolean): String = when {
    armed -> "Press again to clear"
    count <= 0 -> "Nothing learned yet"
    count == 1 -> "1 learned word"
    else -> "$count learned words"
}

@Composable
private fun LearnedWordsSettingsCard(
    learnNewWords: Boolean,
    learnedCount: Int,
    clearArmed: Boolean,
    isLearnFocused: Boolean,
    isClearFocused: Boolean,
    onLearnCheckedChange: (Boolean) -> Unit,
    onClearClick: () -> Unit
) {
    SettingsCard(categoryTitle = "Learned Words") {
        SettingsToggleRow(
            title = "Learn New Words",
            subtitle = "Remember words you type so T9 can suggest them",
            checked = learnNewWords,
            isFocused = isLearnFocused,
            jumpNumber = null,
            onCheckedChange = onLearnCheckedChange
        )
        Spacer(modifier = Modifier.height(4.dp))
        SettingsNavigationRow(
            title = "Clear Learned Words",
            subtitle = learnedWordsClearSubtitle(learnedCount, clearArmed),
            docomoIndex = 17,
            isFocused = isClearFocused,
            jumpNumber = null,
            onClick = onClearClick
        )
    }
}
