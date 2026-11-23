package one.crinklecod.mochibun.ui.screens.settings

import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

internal enum class KeyboardRowId(val id: String) {
    DEFAULT_KEYBOARD("default_keyboard"),
    SWITCH_KEYBOARD("switch_keyboard"),
    HOLD_FOR_DIGIT("hold_for_digit"),
    AUTO_COMMIT("auto_commit"),
    AUTO_COMMIT_DELAY("auto_commit_delay"),
    HOLD_VIBRATION("hold_vibration"),
    PREDICTIVE_TEXT("predictive_text"),
    KEY_GUIDE("key_guide"),
    QUICK_SNIPPETS("quick_snippets")
}

internal val KEYBOARD_ROW_IDS: List<String> = KeyboardRowId.entries.map { it.id }

internal enum class KeyboardPredictiveRowId(val id: String) {
    PREDICTIVE_T9("predictive_t9"),
    T9_DICTIONARY("t9_dictionary"),
    LEARN_NEW_WORDS("learn_new_words"),
    CLEAR_LEARNED_WORDS("clear_learned_words")
}

internal val KEYBOARD_PREDICTIVE_ROW_IDS: List<String> = KeyboardPredictiveRowId.entries.map { it.id }

internal fun predictiveTextSummarySubtitle(enabled: Boolean, dictName: String): String =
    "${if (enabled) "On" else "Off"} · $dictName"

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
 * Keyboard (MochibunImeService) settings: default-keyboard setup, multi-tap feel, Predictive Text
 * sub-screen, key guide, and quick snippets.
 */
@Composable
internal fun KeyboardSettingsScreen(
    onBack: () -> Unit,
    onOpenPredictiveText: () -> Unit = {},
    installedApps: () -> List<LauncherApp> = { emptyList() },
    onNestedBackHandlerChanged: ((() -> Unit)?) -> Unit = {},
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val rows = KeyboardRowId.entries
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
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

    fun isRowFocused(row: KeyboardRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: KeyboardRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

    fun toggleRow(row: KeyboardRowId): Boolean {
        when (row) {
            KeyboardRowId.HOLD_FOR_DIGIT -> {
                holdForDigit = !holdForDigit
                PreferencesManager.setImeHoldForDigitEnabled(context, holdForDigit)
            }
            KeyboardRowId.AUTO_COMMIT -> {
                autoCommit = !autoCommit
                PreferencesManager.setImeAutoCommitEnabled(context, autoCommit)
            }
            KeyboardRowId.HOLD_VIBRATION -> {
                holdVibration = !holdVibration
                PreferencesManager.setImeHoldVibrationEnabled(context, holdVibration)
            }
            else -> return false
        }
        return true
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
        predictiveT9 = PreferencesManager.isImePredictiveT9Enabled(context)
        dictionaryLang = PreferencesManager.getImeT9DictionaryLang(context)
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

    fun targetCardIndex(): Int = when (rows.getOrNull(focusedIndex)) {
        KeyboardRowId.DEFAULT_KEYBOARD, KeyboardRowId.SWITCH_KEYBOARD -> 0
        KeyboardRowId.HOLD_FOR_DIGIT,
        KeyboardRowId.AUTO_COMMIT,
        KeyboardRowId.AUTO_COMMIT_DELAY,
        KeyboardRowId.HOLD_VIBRATION,
        KeyboardRowId.PREDICTIVE_TEXT,
        KeyboardRowId.KEY_GUIDE -> 1
        KeyboardRowId.QUICK_SNIPPETS -> 2
        null -> 0
    }

    ScrollToFocusedItem(listState) { targetCardIndex() }

    fun stepIndex(direction: Int) {
        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
        SoundEffectManager.play(context, SoundEffect.NAV)
    }

    fun activateRow(row: KeyboardRowId) {
        when (row) {
            KeyboardRowId.DEFAULT_KEYBOARD -> activateMochibunKeyboard(context, scope, imeState, ::refresh)
            KeyboardRowId.SWITCH_KEYBOARD -> MochibunImeStatus.showPicker(context)
            KeyboardRowId.AUTO_COMMIT_DELAY -> stepAutoCommitMs(1, wrap = true)
            KeyboardRowId.PREDICTIVE_TEXT -> onOpenPredictiveText()
            KeyboardRowId.KEY_GUIDE -> showKeyGuide = true
            KeyboardRowId.QUICK_SNIPPETS -> showSnippets = true
            KeyboardRowId.HOLD_FOR_DIGIT,
            KeyboardRowId.AUTO_COMMIT,
            KeyboardRowId.HOLD_VIBRATION -> toggleRow(row)
        }
    }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Keyboard",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
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
                        when (val current = rows.getOrNull(focusedIndex)) {
                            KeyboardRowId.HOLD_FOR_DIGIT,
                            KeyboardRowId.AUTO_COMMIT,
                            KeyboardRowId.HOLD_VIBRATION -> {
                                toggleRow(current)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            KeyboardRowId.AUTO_COMMIT_DELAY -> SoundEffectManager.play(
                                context,
                                if (stepAutoCommitMs(direction)) SoundEffect.NAV else SoundEffect.BOUNDARY
                            )
                            else -> stepIndex(direction)
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        val activeDictName = dictionaries.firstOrNull { it.id == dictionaryLang }?.displayName
            ?: T9DictionaryEngine.BUILT_IN_EN_DISPLAY_NAME

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
                        isFocused = isRowFocused(KeyboardRowId.DEFAULT_KEYBOARD),
                        jumpNumber = jumpNumber(KeyboardRowId.DEFAULT_KEYBOARD),
                        onClick = { activateRow(KeyboardRowId.DEFAULT_KEYBOARD) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Switch Keyboard",
                        subtitle = "Pick another installed input method",
                        icon = "⇄",
                        isFocused = isRowFocused(KeyboardRowId.SWITCH_KEYBOARD),
                        jumpNumber = jumpNumber(KeyboardRowId.SWITCH_KEYBOARD),
                        onClick = { activateRow(KeyboardRowId.SWITCH_KEYBOARD) }
                    )
                }
            }

            item(contentType = "keyboard_input_card") {
                SettingsCard(categoryTitle = "Keypad Feel") {
                    SettingsToggleRow(
                        title = "Hold for Digit",
                        subtitle = "Long-press a number key to type its digit",
                        checked = holdForDigit,
                        isFocused = isRowFocused(KeyboardRowId.HOLD_FOR_DIGIT),
                        jumpNumber = jumpNumber(KeyboardRowId.HOLD_FOR_DIGIT),
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
                        isFocused = isRowFocused(KeyboardRowId.AUTO_COMMIT),
                        jumpNumber = jumpNumber(KeyboardRowId.AUTO_COMMIT),
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
                        isFocused = isRowFocused(KeyboardRowId.AUTO_COMMIT_DELAY),
                        jumpNumber = jumpNumber(KeyboardRowId.AUTO_COMMIT_DELAY),
                        onDecrement = { stepAutoCommitMs(-1) },
                        onIncrement = { stepAutoCommitMs(1) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsToggleRow(
                        title = "Key Hold Haptic",
                        subtitle = "Gentle buzz when holding a key types a number",
                        checked = holdVibration,
                        isFocused = isRowFocused(KeyboardRowId.HOLD_VIBRATION),
                        jumpNumber = jumpNumber(KeyboardRowId.HOLD_VIBRATION),
                        onCheckedChange = {
                            holdVibration = it
                            PreferencesManager.setImeHoldVibrationEnabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Predictive Text",
                        subtitle = predictiveTextSummarySubtitle(predictiveT9, activeDictName),
                        docomoIndex = 108,
                        isFocused = isRowFocused(KeyboardRowId.PREDICTIVE_TEXT),
                        jumpNumber = jumpNumber(KeyboardRowId.PREDICTIVE_TEXT),
                        onClick = { activateRow(KeyboardRowId.PREDICTIVE_TEXT) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Key Guide",
                        subtitle = "Cheatsheet for physical keypad & button shortcuts",
                        icon = "ⓘ",
                        isFocused = isRowFocused(KeyboardRowId.KEY_GUIDE),
                        jumpNumber = jumpNumber(KeyboardRowId.KEY_GUIDE),
                        onClick = { activateRow(KeyboardRowId.KEY_GUIDE) }
                    )
                }
            }

            item(contentType = "keyboard_snippets_card") {
                SettingsCard(categoryTitle = "Clips & Snippets") {
                    SettingsNavigationRow(
                        title = "Quick Snippets",
                        subtitle = "${snippets.size}/${PreferencesManager.QUICK_SNIPPET_MAX_COUNT} pinned • hold #, then ✱ or 0",
                        icon = "★",
                        isFocused = isRowFocused(KeyboardRowId.QUICK_SNIPPETS),
                        jumpNumber = jumpNumber(KeyboardRowId.QUICK_SNIPPETS),
                        onClick = { activateRow(KeyboardRowId.QUICK_SNIPPETS) }
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
            installedApps().filterNot { it.isJ2meGame }.map { AppInfo(it.packageName, it.name) }
        }
        MochibunKeyGuideDialog(
            initialTab = KeyGuideTab.LAUNCHER,
            installedApps = appOptions,
            onNestedBackHandlerChanged = onNestedBackHandlerChanged,
            onDismiss = { showKeyGuide = false }
        )
    }
}

/**
 * Keyboard → Predictive Text sub-screen: offline T9 toggle, dictionary selection, and learned words.
 */
@Composable
internal fun KeyboardPredictiveTextSettingsScreen(
    onBack: () -> Unit,
    initialFocusedIndex: Int = 0,
    onFocusedIndexChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val rows = KeyboardPredictiveRowId.entries
    var focusedIndex by rememberSettingsFocusIndex(
        initialFocusedIndex.coerceIn(0, rows.lastIndex),
        onFocusedIndexChange
    )
    var sawCenterDown by remember { mutableStateOf(false) }

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

    LaunchedEffect(learnedRefreshTick) {
        learnedCount = withContext(Dispatchers.IO) { countLearnedWordsForSettings(context) }
    }

    fun refresh() {
        clearArmed = false
        learnedRefreshTick++
        scope.launch {
            val nextDicts = withContext(Dispatchers.IO) {
                T9DictionaryEngine.availableDictionaries(context)
            }
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

    fun isRowFocused(row: KeyboardPredictiveRowId): Boolean = rows.getOrNull(focusedIndex) == row
    fun jumpNumber(row: KeyboardPredictiveRowId): Int? =
        (rows.indexOf(row) + 1).takeIf { it in 1..MAX_JUMP_ROWS }

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

    fun toggleRow(row: KeyboardPredictiveRowId): Boolean {
        when (row) {
            KeyboardPredictiveRowId.PREDICTIVE_T9 -> {
                predictiveT9 = !predictiveT9
                PreferencesManager.setImePredictiveT9Enabled(context, predictiveT9)
            }
            KeyboardPredictiveRowId.LEARN_NEW_WORDS -> {
                learnNewWords = !learnNewWords
                PreferencesManager.setImeT9LearnNewWordsEnabled(context, learnNewWords)
            }
            else -> return false
        }
        return true
    }

    fun activateRow(row: KeyboardPredictiveRowId) {
        if (row != KeyboardPredictiveRowId.CLEAR_LEARNED_WORDS) clearArmed = false
        when (row) {
            KeyboardPredictiveRowId.PREDICTIVE_T9,
            KeyboardPredictiveRowId.LEARN_NEW_WORDS -> toggleRow(row)
            KeyboardPredictiveRowId.T9_DICTIONARY -> cycleDictionary(1)
            KeyboardPredictiveRowId.CLEAR_LEARNED_WORDS -> {
                if (!clearArmed) {
                    clearArmed = true
                } else {
                    clearAllLearnedWords(context)
                    clearArmed = false
                    learnedCount = 0
                    learnedRefreshTick++
                }
            }
        }
    }

    fun targetCardIndex(): Int = when (rows.getOrNull(focusedIndex)) {
        KeyboardPredictiveRowId.PREDICTIVE_T9, KeyboardPredictiveRowId.T9_DICTIONARY -> 0
        KeyboardPredictiveRowId.LEARN_NEW_WORDS, KeyboardPredictiveRowId.CLEAR_LEARNED_WORDS -> 1
        null -> 0
    }

    ScrollToFocusedItem(listState) { targetCardIndex() }

    requestFocusSafely(focusRequester)

    SettingsScaffold(
        title = "Predictive Text",
        showBack = true,
        onBack = onBack,
        isLastItemFocused = { focusedIndex == rows.lastIndex },
        headerDocomoIndex = 108,
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
                        rows.getOrNull(focusedIndex)?.let(::activateRow)
                    }
                    return@onPreviewKeyEvent true
                }
                val digit = getDigitFromKeyCode(native.keyCode)
                if (digit != null && digit in 1..rows.size.coerceAtMost(MAX_JUMP_ROWS)) {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        clearArmed = false
                        val targetIndex = digit - 1
                        focusedIndex = targetIndex
                        SoundEffectManager.play(context, SoundEffect.SELECT)
                        activateRow(rows[targetIndex])
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP || native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.type == KeyEventType.KeyDown) {
                        clearArmed = false
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                        focusedIndex = (focusedIndex + direction + rows.size) % rows.size
                        SoundEffectManager.play(context, SoundEffect.NAV)
                    }
                    return@onPreviewKeyEvent true
                }
                if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.type == KeyEventType.KeyDown) {
                        clearArmed = false
                        val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        when (val current = rows.getOrNull(focusedIndex)) {
                            KeyboardPredictiveRowId.PREDICTIVE_T9,
                            KeyboardPredictiveRowId.LEARN_NEW_WORDS -> {
                                toggleRow(current)
                                SoundEffectManager.play(context, SoundEffect.NAV)
                            }
                            KeyboardPredictiveRowId.T9_DICTIONARY -> SoundEffectManager.play(
                                context,
                                if (cycleDictionary(direction)) SoundEffect.NAV else SoundEffect.BOUNDARY
                            )
                            KeyboardPredictiveRowId.CLEAR_LEARNED_WORDS -> Unit
                            null -> Unit
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
            item(contentType = "keyboard_t9_card") {
                SettingsCard(categoryTitle = "Word Prediction") {
                    SettingsToggleRow(
                        title = "Predictive Text (T9)",
                        subtitle = "Cycle into offline T9 word prediction with *",
                        checked = predictiveT9,
                        isFocused = isRowFocused(KeyboardPredictiveRowId.PREDICTIVE_T9),
                        jumpNumber = jumpNumber(KeyboardPredictiveRowId.PREDICTIVE_T9),
                        onCheckedChange = {
                            clearArmed = false
                            predictiveT9 = it
                            PreferencesManager.setImePredictiveT9Enabled(context, it)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "T9 Dictionary",
                        subtitle = dictSubtitle,
                        docomoIndex = 108,
                        isFocused = isRowFocused(KeyboardPredictiveRowId.T9_DICTIONARY),
                        jumpNumber = jumpNumber(KeyboardPredictiveRowId.T9_DICTIONARY),
                        onClick = { activateRow(KeyboardPredictiveRowId.T9_DICTIONARY) }
                    )
                }
            }

            item(contentType = "keyboard_learned_card") {
                SettingsCard(categoryTitle = "Learned Words") {
                    SettingsToggleRow(
                        title = "Learn New Words",
                        subtitle = "Remember words you type so T9 can suggest them",
                        checked = learnNewWords,
                        isFocused = isRowFocused(KeyboardPredictiveRowId.LEARN_NEW_WORDS),
                        jumpNumber = jumpNumber(KeyboardPredictiveRowId.LEARN_NEW_WORDS),
                        onCheckedChange = { enabled ->
                            clearArmed = false
                            learnNewWords = enabled
                            PreferencesManager.setImeT9LearnNewWordsEnabled(context, enabled)
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsNavigationRow(
                        title = "Clear Learned Words",
                        subtitle = learnedWordsClearSubtitle(learnedCount, clearArmed),
                        docomoIndex = 17,
                        isFocused = isRowFocused(KeyboardPredictiveRowId.CLEAR_LEARNED_WORDS),
                        jumpNumber = jumpNumber(KeyboardPredictiveRowId.CLEAR_LEARNED_WORDS),
                        onClick = { activateRow(KeyboardPredictiveRowId.CLEAR_LEARNED_WORDS) }
                    )
                }
            }
        }
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
