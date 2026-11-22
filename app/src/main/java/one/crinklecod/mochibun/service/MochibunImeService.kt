package one.crinklecod.mochibun.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import one.crinklecod.mochibun.core.haptic.HapticManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.ime.LearnedEpochAction
import one.crinklecod.mochibun.data.ime.LearnedWordExtractor
import one.crinklecod.mochibun.data.ime.T9DictionaryEngine
import one.crinklecod.mochibun.data.ime.T9Keymap
import one.crinklecod.mochibun.data.ime.decideLearnedEpochAction
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.ClipboardRingRepository
import one.crinklecod.mochibun.data.repository.LocalCardBorderState
import one.crinklecod.mochibun.data.repository.TypographyManager
import one.crinklecod.mochibun.data.repository.rememberLiveCardBorderState
import one.crinklecod.mochibun.ui.dialogs.KaomojiWheelState
import one.crinklecod.mochibun.ui.dialogs.KaomojiWheelCloudStage
import one.crinklecod.mochibun.ui.dialogs.handleKaomojiWheelNavKey
import one.crinklecod.mochibun.ui.components.KeitaiCandidatePools
import one.crinklecod.mochibun.ui.components.KeitaiCandidateState
import one.crinklecod.mochibun.ui.components.KeitaiCandidateStrip
import one.crinklecod.mochibun.ui.theme.LocalLauncherTheme
import one.crinklecod.mochibun.ui.theme.SakuraDreamTheme
import one.crinklecod.mochibun.ui.theme.isThemePrefKey
import one.crinklecod.mochibun.ui.theme.loadSavedTheme

/**
 * System-wide keypad IME, Mochibun's own text fields included.
 *
 * Multi-tap and T9 predictive candidates show in [KeitaiCandidateStrip] and as underlined composing
 * text in the editor. Pending text commits on an action (center / Enter, '#' (space), up / down
 * navigation, or the input view closing); multi-tap letters also commit on another keypad key or
 * after the configurable idle delay without a key press (T9 words wait for an explicit commit).
 *
 * TYPE_NULL editors (terminals like Termux) get the keyboard too once they ask for it (an explicit
 * show request), so windows without any text field still get raw keys. In those editors, DEL and
 * Enter with nothing pending go to the app as raw keys, since they handle text only via commits.
 *
 * Keypad keys `2–9` act on release: in multi-tap mode a tap cycles the key's pool, while in T9
 * mode it appends a digit to the active word; holding a key past the hold delay commits any
 * pending T9 word and types the key's own digit (or '*') straight into the editor. Tapping '*'
 * cycles the mode: abc -> Abc (next letter) -> ABC (caps lock) -> T9 (when predictive text is
 * enabled and allowed by the field) -> abc. Sentence starts are capitalised automatically in
 * fields that ask for it.
 */
class MochibunImeService : InputMethodService() {

    private val candidateState = KeitaiCandidateState(poolProvider = { poolFor(it, forNewCandidate = true) })
    private val predictiveInput = PredictiveInput()
    private val dictionaryEngine: T9DictionaryEngine get() = sharedDictionaryEngine
    private val owner = ServiceLifecycleOwner()

    // Keys whose DOWN this IME consumed: their repeats and UP are swallowed too
    private val consumedDown = BooleanArray(KEYCODE_TABLE_SIZE)

    // Caret and overlap checks use screen coordinates; pill touches pass through to the app.
    private val pillBoundsOnScreen = Rect()
    private val topPillBoundsOnScreen = Rect()
    private val cursorAnchorPoint = FloatArray(2)
    private var hasCursorAnchor = false
    private var displayDensity = 1f
    private var dockedAtTop = false
    private var pillTranslationY by mutableFloatStateOf(0f)
    private var pillAlpha by mutableFloatStateOf(1f)
    private var moveAnimator: ValueAnimator? = null
    private val moveInterpolator = DecelerateInterpolator()

    // Letter case & predictive mode. Read by the strip label, so Compose state.
    private var shiftMode by mutableStateOf(ShiftMode.LOWER)
    private var predictiveInUse by mutableStateOf(false)
    private var predictiveEnabled = PreferencesManager.DEFAULT_IME_PREDICTIVE_T9_ENABLED
    private var learnNewWordsEnabled = PreferencesManager.DEFAULT_IME_T9_LEARN_NEW_WORDS
    private var predictiveAllowedForField by mutableStateOf(false)
    private var engineReadyState by mutableStateOf(false)
    private var requestedDictId = PreferencesManager.DEFAULT_IME_T9_DICTIONARY_LANG
    private var loadedLearnedEpoch = PreferencesManager.DEFAULT_IME_T9_LEARNED_EPOCH
    private var lastLoadedOrFlushedLearnedJson = PreferencesManager.DEFAULT_IME_T9_LEARNED_WORDS
    private var lastLoadedOrFlushedUserWordsJson = PreferencesManager.DEFAULT_IME_T9_USER_WORDS
    private var autoCapNext by mutableStateOf(false)
    private var autoCapSuppressed by mutableStateOf(false)
    private var isNumericField by mutableStateOf(false)

    // '#' auto-repeated (held): its release types nothing
    private var poundRepeated = false
    private var poundWheelTriggered = false
    private var kaomojiWheelState by mutableStateOf<KaomojiWheelState?>(null)

    // True while our pending candidate is shown as composing text in the editor
    private var composingShown = false
    private var manualSelection = false

    // getCursorCapsMode is a blocking Binder call. Sentence/word caps only depend on what sits
    // right before the caret, and a letter or digit there never starts a sentence or word, so
    // re-tapping a letter key (a -> b -> c) can't change the answer. Set when caps were read with
    // such a candidate composing; cleared by any commit or shift change.
    private var autoCapFreshForComposing = false

    // True once the editor has told us the selection is collapsed, so DEL can skip the blocking
    // getSelectedText(0) round-trip. False means "unknown or non-empty": query as before.
    private var selectionKnownEmpty = false

    private val commit: (String) -> Unit = { text ->
        currentInputConnection?.commitText(text, 1)
        composingShown = false
        autoCapFreshForComposing = false
        if (text.any { it.isLetter() }) {
            if (shiftMode == ShiftMode.ONCE) shiftMode = ShiftMode.LOWER
            autoCapSuppressed = false
        }
    }

    // Keypad key currently held down, waiting to become a tap (on UP) or a digit (on timer)
    private val handler = Handler(Looper.getMainLooper())
    private var holdKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private var holdFired = false
    private var holdDelayMs = PreferencesManager.DEFAULT_IME_HOLD_DELAY_MS.toLong()
    private val poundHoldRunnable = Runnable { openKaomojiWheel() }
    private var holdForDigitEnabled = PreferencesManager.DEFAULT_IME_HOLD_FOR_DIGIT_ENABLED
    private var holdVibrates = PreferencesManager.DEFAULT_IME_HOLD_VIBRATION
    private var autoCommitEnabled = PreferencesManager.DEFAULT_IME_AUTO_COMMIT_ENABLED
    private val holdRunnable = Runnable { fireHold() }
    private var idleCommitMs = PreferencesManager.DEFAULT_IME_IDLE_COMMIT_MS.toLong()
    private val pillIdleFadeRunnable = Runnable {
        if (!manualSelection) pillAlpha = PILL_IDLE_ALPHA
    }

    // A TYPE_NULL editor (terminal) that asked for the keyboard: it gets multi-tap like any field
    private var nullEditorEngaged = false

    // Idle timeout: keeps the typed letter and clears the strip (never runs for T9 words)
    private val idleCommitRunnable = Runnable {
        if (manualSelection ||
            holdKeyCode != KeyEvent.KEYCODE_UNKNOWN ||
            !candidateState.isActive ||
            !predictiveInput.isEmpty
        ) {
            return@Runnable
        }
        batchEdit {
            candidateState.commitActive(commit)
            syncComposing()
        }
    }

    // Active launcher palette for the pill / Kaomoji Wheel. Reloaded on every onStartInputView and
    // whenever a theme pref changes, so the IME never keeps showing a previous theme.
    private var imeTheme by mutableStateOf(SakuraDreamTheme)
    private val themePrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || isThemePrefKey(key)) refreshTheme()
    }

    private fun refreshTheme() {
        val latest = loadSavedTheme(PreferencesManager.getPrefs(this))
        if (latest != imeTheme) imeTheme = latest
    }

    override fun onCreate() {
        super.onCreate()
        displayDensity = resources.displayMetrics.density
        owner.create()
        refreshTheme()
        PreferencesManager.getPrefs(this).registerOnSharedPreferenceChangeListener(themePrefListener)
        reloadLearnedDataFromPrefs()
        loadDictionaryAsync(PreferencesManager.getImeT9DictionaryLang(this))
        // As the default keyboard we may read the clipboard in the background (Android 10+),
        // so this is where copies from other apps reach the Clips & Snippets wheel.
        ClipboardRingRepository.register(this)
    }

    override fun onDestroy() {
        flushLearnedWordsIfDirty()
        cancelHold()
        manualSelection = false
        handler.removeCallbacks(pillIdleFadeRunnable)
        handler.removeCallbacks(idleCommitRunnable)
        handler.removeCallbacks(poundHoldRunnable)
        kaomojiWheelState = null
        moveAnimator?.cancel()
        moveAnimator = null
        PreferencesManager.getPrefs(this).unregisterOnSharedPreferenceChangeListener(themePrefListener)
        ClipboardRingRepository.unregister()
        owner.destroy()
        super.onDestroy()
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return hasTextEditor()
    }

    /**
     * False for the placeholder connection Android hands us when a window has focus but no text
     * field. TYPE_NULL editors (terminals) count only after they requested the keyboard.
     */
    private fun hasTextEditor(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if (currentInputConnection == null) return false
        return info.inputType != InputType.TYPE_NULL || nullEditorEngaged
    }

    private fun isNullEditor(): Boolean = currentInputEditorInfo?.inputType == InputType.TYPE_NULL

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        manualSelection = false
        autoCapFreshForComposing = false
        selectionKnownEmpty = attribute != null && attribute.initialSelStart >= 0 &&
            attribute.initialSelStart == attribute.initialSelEnd
        if (!restarting) nullEditorEngaged = false
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        val info = currentInputEditorInfo
        // Mochibun's own windows keep their raw-key handling (T9 search, speed dial)
        if (info != null && info.inputType == InputType.TYPE_NULL && info.packageName != packageName &&
            currentInputConnection != null
        ) {
            nullEditorEngaged = true
        }
        return super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        applyFloatingWindowStyle(win)
    }

    @Suppress("DEPRECATION")
    private fun applyFloatingWindowStyle(win: Window) {
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        win.navigationBarColor = Color.TRANSPARENT
        win.statusBarColor = Color.TRANSPARENT
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.setDimAmount(0f)
        win.decorView.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (!isInputViewShown) return

        val fullViewHeight = window?.window?.decorView?.height ?: 0
        if (fullViewHeight > 0) {
            // Keep underlying app content at full height without resizing or panning
            outInsets.contentTopInsets = fullViewHeight
            outInsets.visibleTopInsets = fullViewHeight
        }
        if (kaomojiWheelState != null) {
            // The wheel's cloud stage takes taps (petals, hub, scrim-to-dismiss)
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
        } else {
            // Strip only: every touch passes through to the app underneath
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.setEmpty()
        }
    }

    override fun onCreateInputView(): View {
        // Compose resolves its owners from the root view, so the IME window's decor needs them too
        window?.window?.decorView?.let { attachOwners(it) }
        window?.window?.let { applyFloatingWindowStyle(it) }
        refreshTheme()
        return ComposeView(this).apply {
            attachOwners(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            clipChildren = false
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    var parent = v.parent as? View
                    while (parent != null) {
                        parent.setBackgroundColor(Color.TRANSPARENT)
                        parent = parent.parent as? View
                    }
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
            setContent {
                val density = LocalDensity.current.density
                displayDensity = density
                // Same font as the launcher (incl. a .mochibundle font); both flows are shared
                // in-process, so a font change or bundle import re-renders the pill immediately.
                val fontId by PreferencesManager.themeFontFamily.collectAsState()
                val fontOptions by TypographyManager.options.collectAsState()
                val imeFontFamily = remember(fontId, fontOptions) {
                    TypographyManager.resolveFontFamily(fontId)
                }
                val baseTextStyle = LocalTextStyle.current
                val imeTextStyle = remember(baseTextStyle, imeFontFamily) {
                    baseTextStyle.copy(fontFamily = imeFontFamily)
                }
                CompositionLocalProvider(
                    LocalLauncherTheme provides imeTheme,
                    LocalTextStyle provides imeTextStyle
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Full-height spike for validating IME window sizing and insets.
                            .height(resources.configuration.screenHeightDp.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val wheelState = kaomojiWheelState
                        if (wheelState != null) {
                            CompositionLocalProvider(
                                LocalCardBorderState provides rememberLiveCardBorderState()
                            ) {
                                // Edge to edge: the cloud shelf and scrim span the whole window
                                KaomojiWheelCloudStage(
                                    state = wheelState,
                                    onConfirm = ::confirmKaomojiWheel,
                                    onDismiss = ::closeKaomojiWheel,
                                    confirmVerb = "Insert"
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 2.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                KeitaiCandidateStrip(
                                    state = candidateState,
                                    leadingLabel = modeLabel(),
                                    modifier = Modifier
                                        .onGloballyPositioned { coords ->
                                            val screenPos = coords.positionOnScreen()
                                            val size = coords.size
                                            val touchMargin = (4 * density).toInt()
                                            val screenTop = screenPos.y.toInt() - touchMargin
                                            val screenBottom = (screenPos.y + size.height).toInt() + touchMargin
                                            val changed = pillBoundsOnScreen.setIfChanged(
                                                (screenPos.x.toInt() - touchMargin).coerceAtLeast(0),
                                                screenTop,
                                                (screenPos.x + size.width).toInt() + touchMargin,
                                                screenBottom
                                            )
                                            if (changed) updatePillDodgeForCurrentCaret()
                                        }
                                        .graphicsLayer {
                                            translationY = pillTranslationY
                                            alpha = pillAlpha
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        }
                                    // No elevation shadow: the cloud strip draws its own flat
                                    // drop shadow (its scalloped outline isn't convex).
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        owner.resume()
        refreshTheme()
        cancelHold()
        handler.removeCallbacks(poundHoldRunnable)
        kaomojiWheelState = null
        handler.removeCallbacks(pillIdleFadeRunnable)
        pillAlpha = 1f
        moveAnimator?.cancel()
        moveAnimator = null
        pillTranslationY = 0f
        dockedAtTop = false
        hasCursorAnchor = false
        requestCursorAnchorUpdates()
        schedulePillIdleFade()
        manualSelection = false
        candidateState.reset()
        predictiveInput.clear()
        composingShown = false
        consumedDown.fill(false)
        holdDelayMs = PreferencesManager.getImeHoldDelayMs(this).toLong()
        holdForDigitEnabled = PreferencesManager.isImeHoldForDigitEnabled(this)
        holdVibrates = PreferencesManager.isImeHoldVibrationEnabled(this)
        autoCommitEnabled = PreferencesManager.isImeAutoCommitEnabled(this)
        idleCommitMs = PreferencesManager.getImeIdleCommitMs(this).toLong()
        predictiveEnabled = PreferencesManager.isImePredictiveT9Enabled(this)
        learnNewWordsEnabled = PreferencesManager.isImeT9LearnNewWordsEnabled(this)
        val prefEpoch = PreferencesManager.getImeT9LearnedEpoch(this)
        if (decideLearnedEpochAction(loadedLearnedEpoch, prefEpoch) == LearnedEpochAction.RELOAD_FROM_PREFS) {
            reloadLearnedDataFromPrefs()
        } else if (!dictionaryEngine.userStats.dirty && !dictionaryEngine.userWords.dirty) {
            val savedLearnedJson = PreferencesManager.getImeT9LearnedWordsJson(this)
            val savedUserWordsJson = PreferencesManager.getImeT9UserWordsJson(this)
            if (savedLearnedJson != lastLoadedOrFlushedLearnedJson ||
                savedUserWordsJson != lastLoadedOrFlushedUserWordsJson
            ) {
                dictionaryEngine.reloadLearnedData(savedLearnedJson, savedUserWordsJson)
                lastLoadedOrFlushedLearnedJson = savedLearnedJson
                lastLoadedOrFlushedUserWordsJson = savedUserWordsJson
            }
        }
        val desiredDictId = PreferencesManager.getImeT9DictionaryLang(this)
        if (desiredDictId != requestedDictId || (!dictionaryEngine.ready && engineReadyState)) {
            flushLearnedWordsIfDirty()
            loadDictionaryAsync(desiredDictId)
        } else {
            engineReadyState = dictionaryEngine.ready
        }
        isNumericField = when ((info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
        predictiveAllowedForField = predictiveAllowed(info)
        if (!restarting) {
            shiftMode = ShiftMode.LOWER
            autoCapSuppressed = false
            predictiveInUse = false
        } else if (!predictiveEnabled || !predictiveAllowedForField) {
            predictiveInUse = false
        }
        refreshAutoCap()
        if (hasTextEditor()) InputArbiter.updateInputActive(this, true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacks(poundHoldRunnable)
        closeKaomojiWheel()
        currentInputConnection?.requestCursorUpdates(0)
        handler.removeCallbacks(pillIdleFadeRunnable)
        moveAnimator?.cancel()
        moveAnimator = null
        dockedAtTop = false
        pillTranslationY = 0f
        pillAlpha = 1f
        pillBoundsOnScreen.setEmpty()
        hasCursorAnchor = false
        manualSelection = false
        resolvePendingTap(applyTap = false)
        commitPending()
        flushLearnedWordsIfDirty()
        InputArbiter.updateInputActive(this, false)
        owner.pause()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        // Covers editors that never showed the input view (e.g. focus moved while hidden)
        manualSelection = false
        resolvePendingTap(applyTap = false)
        commitPending()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selectionKnownEmpty = newSelStart >= 0 && newSelStart == newSelEnd
        if (candidateState.isActive) {
            // The caret left our composing text (touch, app-driven move): keep the letter as typed
            if (candidatesEnd != -1 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
                currentInputConnection?.finishComposingText()
                composingShown = false
                manualSelection = false
                candidateState.reset()
                predictiveInput.clear()
                refreshAutoCap()
            }
        } else if (candidatesStart == -1 && holdKeyCode == KeyEvent.KEYCODE_UNKNOWN) {
            refreshAutoCap()
        }
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        val anchor = cursorAnchorInfo
        val matrix = anchor?.matrix
        if (anchor == null ||
            anchor.insertionMarkerBottom.isNaN() ||
            anchor.insertionMarkerHorizontal.isNaN() ||
            matrix == null ||
            anchor.insertionMarkerFlags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION == 0
        ) {
            hasCursorAnchor = false
            setDockedAtTop(false)
            return
        }

        cursorAnchorPoint[0] = anchor.insertionMarkerHorizontal
        cursorAnchorPoint[1] = anchor.insertionMarkerBottom
        matrix.mapPoints(cursorAnchorPoint)
        if (!cursorAnchorPoint[0].isFinite() || !cursorAnchorPoint[1].isFinite()) {
            hasCursorAnchor = false
            setDockedAtTop(false)
            return
        }
        hasCursorAnchor = true
        updatePillDodgeForCurrentCaret()
    }

    private fun updatePillDodgeForCurrentCaret() {
        if (!hasCursorAnchor || pillBoundsOnScreen.isEmpty) {
            setDockedAtTop(false)
            return
        }
        val caretY = cursorAnchorPoint[1]
        val enterMargin = dpToPx(DOCK_ENTER_MARGIN_DP)
        updateTopPillBoundsOnScreen()
        val topOverlap = isWithinVerticalZone(topPillBoundsOnScreen, caretY, enterMargin)
        val shouldDock = if (topOverlap) {
            false
        } else if (dockedAtTop) {
            caretY >= pillBoundsOnScreen.top - dpToPx(DOCK_EXIT_MARGIN_DP) &&
                caretY <= pillBoundsOnScreen.bottom + dpToPx(DOCK_EXIT_MARGIN_DP)
        } else {
            isWithinVerticalZone(pillBoundsOnScreen, caretY, enterMargin)
        }
        setDockedAtTop(shouldDock)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        showPillForKeyPress()
        if (keyCode !in consumedDown.indices) return super.onKeyDown(keyCode, event)
        kaomojiWheelState?.let { wheelState ->
            consumedDown[keyCode] = true
            if (keyCode == KeyEvent.KEYCODE_POUND) {
                if (event.repeatCount > 0) poundRepeated = true
                // A '#' tap that closes the wheel must not also type its space on release
                else poundWheelTriggered = true
            }
            if (event.repeatCount == 0) handleKaomojiWheelKey(wheelState, keyCode)
            return true
        }
        if (event.repeatCount > 0) {
            if (!consumedDown[keyCode]) return super.onKeyDown(keyCode, event)
            handleRepeat(keyCode, event)
            return true
        }
        handler.removeCallbacks(idleCommitRunnable)
        // Rolling onto another key before releasing the first counts the first as a tap
        resolvePendingTap()
        val consumed = hasTextEditor() && batchEdit { handleNewPress(keyCode) }
        consumedDown[keyCode] = consumed
        // BACK we don't use goes to the app: the default would spend it hiding the keyboard
        if (keyCode == KeyEvent.KEYCODE_BACK) return consumed
        return consumed || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in consumedDown.indices && consumedDown[keyCode]) {
            consumedDown[keyCode] = false
            if (keyCode == holdKeyCode) resolvePendingTap()
            if (keyCode == KeyEvent.KEYCODE_POUND) {
                handler.removeCallbacks(poundHoldRunnable)
                if (!poundWheelTriggered && !poundRepeated) {
                    batchEdit {
                        manualSelection = false
                        commitPending()
                        maybeLearnWordBeforeCursor()
                        commit(" ")
                        syncComposing()
                    }
                }
                poundWheelTriggered = false
                poundRepeated = false
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return false
        return super.onKeyUp(keyCode, event)
    }

    /** Returns true if this press is consumed. */
    private fun handleNewPress(keyCode: Int): Boolean {
        val active = candidateState.isActive
        when (keyCode) {
            KeyEvent.KEYCODE_POUND -> {
                // Space, typed on release; a hold belongs to the Kaomoji Wheel
                if (isNumericField) return false
                poundRepeated = false
                poundWheelTriggered = false
                handler.removeCallbacks(poundHoldRunnable)
                handler.postDelayed(poundHoldRunnable, POUND_HOLD_MS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!active) return false
                manualSelection = true
                candidateState.navigateLeft()
                syncComposing()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!active) return false
                manualSelection = true
                candidateState.navigateRight()
                syncComposing()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Navigation commits, then the editor (or our dialog) still gets the key
                manualSelection = false
                commitPending()
                syncComposing()
                return false
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                manualSelection = false
                if (active) {
                    commitPending()
                    syncComposing()
                    return true
                }
                return performEditorActionIfAny()
            }
            KeyEvent.KEYCODE_DEL -> {
                // Terminals delete on the raw key (their editable is always empty)
                manualSelection = false
                if (!active && predictiveInput.isEmpty && isNullEditor()) return false
                if (!predictiveInput.isEmpty) {
                    predictiveInput.pop()
                    if (predictiveInput.isEmpty) {
                        candidateState.reset()
                    } else {
                        updatePredictiveCandidates()
                    }
                    syncComposing()
                } else if (active) {
                    candidateState.handleBackspace()
                    syncComposing()
                } else {
                    deleteBeforeCursor()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!predictiveInput.isEmpty || active) {
                    manualSelection = false
                    predictiveInput.clear()
                    candidateState.reset()
                    syncComposing()
                    return true
                }
                return false // the app handles BACK; never a "hide keyboard" step
            }
        }
        // Number / phone / date fields take raw digit keys (and '*' for dialling) as they are
        val isMultiTapKey = keyCode == KeyEvent.KEYCODE_STAR || KeitaiCandidatePools.forKey(keyCode) != null
        if (isMultiTapKey) manualSelection = false
        if (isNumericField) return false
        if (isMultiTapKey) {
            // Typing after the strip was hidden (e.g. BACK) would leave candidates invisible
            if (!isInputViewShown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) requestShowSelf(0)
            handler.removeCallbacks(holdRunnable)
            holdKeyCode = keyCode
            holdFired = false
            handler.postDelayed(holdRunnable, holdDelayMs)
            val t9 = t9Active()
            if (t9 && keyCode in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9) {
                // Digits 2..9 in T9 act on release (resolvePendingTap) or hold (fireHold)
                return true
            }
            if (t9 && (keyCode == KeyEvent.KEYCODE_1 || keyCode == KeyEvent.KEYCODE_0) && !predictiveInput.isEmpty) {
                commitPending()
            }
            // '*' cycles mode on release (resolvePendingTap) so holding '*' never flashes/alters mode
            if (keyCode != KeyEvent.KEYCODE_STAR) candidateState.handleKey(keyCode, commit)
            syncComposing()
            return true
        }
        return false
    }

    /** Auto-repeat of a consumed key: held arrows keep cycling, held DEL keeps deleting. */
    private fun handleRepeat(keyCode: Int, event: KeyEvent) {
        // A repeat already past the hold delay (or flagged long-press) is a hold, even if the timer
        // hasn't run yet: injected and some hardware long-presses arrive faster than real time
        if (keyCode == holdKeyCode && !holdFired &&
            (event.isLongPress || (event.repeatCount >= 2 && event.eventTime - event.downTime >= holdDelayMs))
        ) {
            handler.removeCallbacks(holdRunnable)
            fireHold()
            return
        }
        when (keyCode) {
            KeyEvent.KEYCODE_POUND -> poundRepeated = true
            KeyEvent.KEYCODE_DPAD_LEFT -> { candidateState.navigateLeft(); syncComposing() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { candidateState.navigateRight(); syncComposing() }
            KeyEvent.KEYCODE_DEL -> if (!candidateState.isActive) deleteBeforeCursor()
        }
    }

    /** Hold timer: commit pending T9 word (or drop multi-tap letter) and type the literal digit / '*'. */
    private fun fireHold() {
        val keyCode = holdKeyCode
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN || holdFired || !holdForDigitEnabled) return
        holdFired = true
        manualSelection = false
        batchEdit {
            if (t9Active() && (keyCode == KeyEvent.KEYCODE_STAR || keyCode in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9)) {
                commitPending()
            } else {
                candidateState.reset()
                predictiveInput.clear()
            }
            literalFor(keyCode)?.let(commit)
            syncComposing()
        }
        if (holdVibrates) HapticManager.vibrateTick(this)
    }

    private fun openKaomojiWheel() {
        if (kaomojiWheelState != null) return
        poundWheelTriggered = true
        batchEdit {
            commitPending()
            syncComposing()
        }
        kaomojiWheelState = KaomojiWheelState()
        refreshTouchableInsets()
        HapticManager.vibrateTick(this)
        SoundEffectManager.play(this, SoundEffect.SELECT)
    }

    private fun handleKaomojiWheelKey(state: KaomojiWheelState, keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_POUND -> closeKaomojiWheel()
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> confirmKaomojiWheel()
            else -> handleKaomojiWheelNavKey(state, keyCode, this, onConfirm = ::confirmKaomojiWheel)
        }
    }

    private fun confirmKaomojiWheel() {
        // Kaomoji, or the full clip / snippet text in Clips & Snippets mode
        val text = kaomojiWheelState?.currentText.orEmpty()
        closeKaomojiWheel()
        if (text.isNotBlank()) {
            batchEdit {
                commitPending()
                commit(text)
                syncComposing()
            }
            HapticManager.vibrateTick(this)
            SoundEffectManager.play(this, SoundEffect.SELECT)
        }
    }

    /** Back to the T9 cloud strip, with touches passing through to the app again. */
    private fun closeKaomojiWheel() {
        if (kaomojiWheelState == null) return
        kaomojiWheelState = null
        refreshTouchableInsets()
    }

    /**
     * The wheel swap keeps the root's size, so no View layout (and hence no onComputeInsets)
     * would otherwise follow it; request one so the touchable region flips with the wheel.
     */
    private fun refreshTouchableInsets() {
        window?.window?.decorView?.requestLayout()
    }

    /** Ends the pending keypad press, applying `*` mode cycle or T9 digit tap on release / roll-over. */
    private fun resolvePendingTap(applyTap: Boolean = true) {
        handler.removeCallbacks(holdRunnable)
        val tapKey = holdKeyCode
        val wasNotHeld = !holdFired
        val starTap = tapKey == KeyEvent.KEYCODE_STAR && wasNotHeld
        val digitTap = t9Active() && tapKey in KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9 && wasNotHeld
        holdKeyCode = KeyEvent.KEYCODE_UNKNOWN
        holdFired = false
        if (!applyTap) return
        if (starTap) {
            batchEdit {
                cycleShift()
                syncComposing()
            }
        } else if (digitTap) {
            val digit = T9Keymap.digitForKeyCode(tapKey) ?: return
            batchEdit {
                if (candidateState.isActive && predictiveInput.isEmpty) {
                    commitPending()
                }
                predictiveInput.push(digit, autoCapActive = isAutoCapActive())
                updatePredictiveCandidates()
                syncComposing()
            }
        }
    }

    private fun cancelHold() {
        handler.removeCallbacks(idleCommitRunnable)
        handler.removeCallbacks(holdRunnable)
        holdKeyCode = KeyEvent.KEYCODE_UNKNOWN
        holdFired = false
    }

    private fun requestCursorAnchorUpdates() {
        currentInputConnection?.requestCursorUpdates(
            InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
        )
    }

    private fun showPillForKeyPress() {
        handler.removeCallbacks(pillIdleFadeRunnable)
        pillAlpha = 1f
        schedulePillIdleFade()
    }

    private fun schedulePillIdleFade() {
        handler.removeCallbacks(pillIdleFadeRunnable)
        handler.postDelayed(pillIdleFadeRunnable, PILL_IDLE_TIMEOUT_MS)
    }

    private fun setDockedAtTop(dock: Boolean) {
        if (dockedAtTop == dock) return
        dockedAtTop = dock
        val targetTranslation = if (dock) {
            (dpToPx(TOP_SAFE_MARGIN_DP) - pillBoundsOnScreen.top).toFloat()
        } else {
            0f
        }
        val animator = ValueAnimator.ofFloat(pillTranslationY, targetTranslation).apply {
            duration = PILL_MOVE_DURATION_MS
            interpolator = moveInterpolator
            addUpdateListener {
                pillTranslationY = it.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (moveAnimator !== animation) return
                    moveAnimator = null
                    updatePillDodgeForCurrentCaret()
                }
            })
        }
        moveAnimator?.cancel()
        moveAnimator = animator
        animator.start()
    }

    private fun updateTopPillBoundsOnScreen() {
        topPillBoundsOnScreen.set(pillBoundsOnScreen)
        topPillBoundsOnScreen.offset(
            0,
            dpToPx(TOP_SAFE_MARGIN_DP) - topPillBoundsOnScreen.top
        )
    }

    private fun isWithinVerticalZone(bounds: Rect, caretY: Float, margin: Int): Boolean =
        caretY >= bounds.top - margin && caretY <= bounds.bottom + margin

    private fun dpToPx(dp: Float): Int = (dp * displayDensity + 0.5f).toInt()

    private fun Rect.setIfChanged(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        if (this.left == left && this.top == top && this.right == right && this.bottom == bottom) return false
        set(left, top, right, bottom)
        return true
    }

    private fun predictiveAllowed(info: EditorInfo? = currentInputEditorInfo): Boolean = isPredictiveAllowed(
        inputType = info?.inputType ?: InputType.TYPE_NULL,
        hasEditor = info != null && currentInputConnection != null,
        isNumericField = isNumericField,
        nullEditorEngaged = isNullEditor() || nullEditorEngaged
    )

    private fun loadDictionaryAsync(dictId: String) {
        requestedDictId = dictId
        engineReadyState = dictionaryEngine.ready && dictionaryEngine.activeDictionaryId == dictId
        dictionaryEngine.loadAsync(this, dictId) { loadedId ->
            handler.post {
                if (requestedDictId != dictId) return@post
                engineReadyState = dictionaryEngine.ready
                if (loadedId != dictId && dictId != T9DictionaryEngine.BUILT_IN_EN_ID) {
                    requestedDictId = loadedId
                    PreferencesManager.setImeT9DictionaryLang(this, loadedId)
                }
            }
        }
    }

    private fun reloadLearnedDataFromPrefs() {
        val epoch = PreferencesManager.getImeT9LearnedEpoch(this)
        val statsJson = PreferencesManager.getImeT9LearnedWordsJson(this)
        val userWordsJson = PreferencesManager.getImeT9UserWordsJson(this)
        dictionaryEngine.reloadLearnedData(statsJson, userWordsJson)
        loadedLearnedEpoch = epoch
        lastLoadedOrFlushedLearnedJson = statsJson
        lastLoadedOrFlushedUserWordsJson = userWordsJson
    }

    private fun flushLearnedWordsIfDirty() {
        val prefEpoch = PreferencesManager.getImeT9LearnedEpoch(this)
        if (decideLearnedEpochAction(loadedLearnedEpoch, prefEpoch) == LearnedEpochAction.RELOAD_FROM_PREFS) {
            reloadLearnedDataFromPrefs()
            return
        }
        if (dictionaryEngine.userStats.dirty) {
            val json = dictionaryEngine.userStats.toJsonString()
            PreferencesManager.setImeT9LearnedWordsJson(this, json)
            lastLoadedOrFlushedLearnedJson = json
            dictionaryEngine.userStats.markClean()
        }
        if (dictionaryEngine.userWords.dirty) {
            val json = dictionaryEngine.userWords.toJsonString()
            PreferencesManager.setImeT9UserWordsJson(this, json)
            lastLoadedOrFlushedUserWordsJson = json
            dictionaryEngine.userWords.markClean()
        }
    }

    private fun isPersonalizedLearningAllowed(): Boolean {
        if (!predictiveAllowedForField) return false
        val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
        return (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
    }

    private fun maybeLearnWordBeforeCursor() {
        if (!learnNewWordsEnabled || !isPersonalizedLearningAllowed() || !dictionaryEngine.ready) return
        val ic = currentInputConnection ?: return
        if (!selectionKnownEmpty && !ic.getSelectedText(0).isNullOrEmpty()) return
        val before = ic.getTextBeforeCursor(LEARN_WORD_LOOKBACK_CHARS, 0)
        val word = LearnedWordExtractor.lastWord(before) ?: return
        if (dictionaryEngine.hasDictionaryWord(word)) return
        dictionaryEngine.recordUserWordSighting(word)
    }

    private fun t9Active(): Boolean =
        predictiveInUse && predictiveEnabled && predictiveAllowedForField && engineReadyState && dictionaryEngine.ready

    private fun commitPending() {
        val selectedIdx = candidateState.selectedIndex
        val learnedWord = if (
            candidateState.isActive &&
            isPersonalizedLearningAllowed() &&
            !predictiveInput.isEmpty &&
            !predictiveInput.rawDigitFallback
        ) {
            predictiveInput.lowercaseWordAt(selectedIdx)
        } else {
            null
        }
        candidateState.commitActive(commit)
        predictiveInput.clear()
        if (learnedWord != null && !dictionaryEngine.isUserWord(learnedWord)) {
            dictionaryEngine.userStats.recordCommit(dictionaryEngine.activeDictionaryId, learnedWord)
        }
    }

    private fun updatePredictiveCandidates() {
        val seq = predictiveInput.digits
        if (seq.isEmpty()) {
            predictiveInput.clear()
            candidateState.reset()
            return
        }
        val result = dictionaryEngine.candidatesFor(seq)
        predictiveInput.setCandidates(
            words = result.words,
            rawDigitFallback = !result.hasDictionaryMatch
        )
        val upperAll = shiftMode == ShiftMode.CAPS
        val display = Array(result.words.size) { i ->
            PredictiveInput.applyCase(
                word = result.words[i],
                upperAll = upperAll,
                capitalizeFirst = predictiveInput.capitalizeFirst
            )
        }
        candidateState.setCandidates(display)
    }

    /** '*' tap: abc -> Abc -> ABC (-> T9 when predictive enabled and allowed) -> abc. */
    private fun cycleShift() {
        autoCapFreshForComposing = false
        val allowed = predictiveEnabled && predictiveAllowedForField
        val wasPredictive = predictiveInUse && predictiveAllowedForField
        val next = nextStarMode(
            shift = shiftMode,
            autoCapActive = isAutoCapActive(),
            predictiveInUse = wasPredictive,
            predictiveEnabled = allowed
        )
        if (wasPredictive != next.predictiveInUse) {
            commitPending()
        }
        shiftMode = next.shift
        predictiveInUse = next.predictiveInUse
        // Entering T9 keeps autoCapNext active for the first word; choosing 'abc' suppresses it
        autoCapSuppressed = if (!wasPredictive && next.predictiveInUse) {
            false
        } else {
            next.shift == ShiftMode.LOWER
        }
        // Re-case the pending letter in place
        if (candidateState.isActive) {
            poolFor(candidateState.lastKeyCode)?.let { candidateState.replaceCandidates(it) }
        }
    }

    private fun isAutoCapActive(): Boolean =
        shiftMode == ShiftMode.LOWER && autoCapNext && !autoCapSuppressed

    private fun isUpperCase(): Boolean = shiftMode != ShiftMode.LOWER || isAutoCapActive()

    private fun modeLabel(): String = when {
        isNumericField -> "123"
        t9Active() -> "T9"
        shiftMode == ShiftMode.CAPS -> "ABC"
        isUpperCase() -> "Abc"
        else -> "abc"
    }

    /**
     * Candidate pool for [keyCode] in the current letter case. For a new candidate, a pending
     * letter commits first and uses up a one-shot shift, so the new one is lowercase.
     */
    private fun poolFor(keyCode: Int, forNewCandidate: Boolean = false): Array<String>? {
        val letterIndex = keyCode - KeyEvent.KEYCODE_2
        if (letterIndex in LOWER_POOLS.indices) {
            val oneShotUsedUp = forNewCandidate && shiftMode == ShiftMode.ONCE && pendingIsLetter()
            val upper = isUpperCase() && !oneShotUsedUp
            return if (upper) UPPER_POOLS[letterIndex] else LOWER_POOLS[letterIndex]
        }
        return KeitaiCandidatePools.forKey(keyCode)
    }

    private fun pendingIsLetter(): Boolean =
        candidateState.isActive && candidateState.candidates[candidateState.selectedIndex].any { it.isLetter() }

    /** Asks the editor whether the caret sits at a sentence start (only if the field wants caps). */
    private fun refreshAutoCap() {
        val info = currentInputEditorInfo
        val type = info?.inputType ?: 0
        val wantsCaps = (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (type and CAP_FLAGS) != 0
        autoCapNext = wantsCaps && shiftMode == ShiftMode.LOWER &&
            (currentInputConnection?.getCursorCapsMode(type) ?: 0) != 0
        autoCapFreshForComposing = composingIsLetterOrDigit()
    }

    private fun composingIsLetterOrDigit(): Boolean =
        candidateState.isActive && candidateState.candidates[candidateState.selectedIndex].all { it.isLetterOrDigit() }

    /** Mirrors the pending candidate into the editor as composing (underlined) text. */
    private fun syncComposing() {
        handler.removeCallbacks(idleCommitRunnable)
        val ic = currentInputConnection ?: return
        if (candidateState.isActive) {
            if (autoCommitEnabled && !manualSelection && predictiveInput.isEmpty) {
                handler.postDelayed(idleCommitRunnable, idleCommitMs)
            }
            ic.setComposingText(candidateState.candidates[candidateState.selectedIndex], 1)
            composingShown = true
        } else if (composingShown) {
            // Candidate dropped (BACK / DEL): remove its composing text
            ic.setComposingText("", 1)
            ic.finishComposingText()
            composingShown = false
        }
        // Cycling a letter key keeps the same caps answer; skip the Binder round-trip
        if (!(autoCapFreshForComposing && composingIsLetterOrDigit())) refreshAutoCap()
    }

    private inline fun <T> batchEdit(block: () -> T): T {
        val ic: InputConnection? = currentInputConnection
        ic?.beginBatchEdit()
        try {
            return block()
        } finally {
            ic?.endBatchEdit()
        }
    }

    private fun deleteBeforeCursor() {
        val ic = currentInputConnection ?: return
        if (!selectionKnownEmpty && !ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    /**
     * Center / Enter with nothing pending: run the editor's action (Send, Search, Go...) for
     * single-line fields that declare one. Multi-line fields, fields without an action, and
     * Mochibun's own dialogs (which move between their focus zones on Enter) get the key itself.
     */
    private fun performEditorActionIfAny(): Boolean {
        val info = currentInputEditorInfo ?: return false
        if (info.packageName == packageName) return false
        if (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return false
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return false
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) return false
        return currentInputConnection?.performEditorAction(action) ?: false
    }

    private fun literalFor(keyCode: Int): String? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (keyCode - KeyEvent.KEYCODE_0)).toString()
        KeyEvent.KEYCODE_STAR -> "*"
        else -> null
    }

    private fun attachOwners(view: View) {
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
    }

    private class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun create() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }

        fun resume() {
            if (registry.currentState != Lifecycle.State.DESTROYED) {
                registry.currentState = Lifecycle.State.RESUMED
            }
        }

        fun pause() {
            if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                registry.currentState = Lifecycle.State.CREATED
            }
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    private companion object {
        val sharedDictionaryEngine by lazy { T9DictionaryEngine() }

        // Covers every Android keycode (288 on API 31), same as InputArbiter's table
        const val KEYCODE_TABLE_SIZE = 512

        const val PILL_IDLE_TIMEOUT_MS = 1_500L
        const val POUND_HOLD_MS = 400L
        const val PILL_IDLE_ALPHA = 0.35f
        const val PILL_MOVE_DURATION_MS = 220L
        const val DOCK_ENTER_MARGIN_DP = 12f
        const val DOCK_EXIT_MARGIN_DP = 28f
        const val TOP_SAFE_MARGIN_DP = 32f
        const val LEARN_WORD_LOOKBACK_CHARS = 48

        const val CAP_FLAGS = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        // Keys 2..9 split into one-case pools (letters + the key's digit). Same size per key, so
        // a pending candidate can switch case in place.
        private val LETTER_POOLS = (KeyEvent.KEYCODE_2..KeyEvent.KEYCODE_9).map {
            KeitaiCandidatePools.forKey(it)!!
        }
        val LOWER_POOLS: Array<Array<String>> = LETTER_POOLS.map { pool ->
            pool.filter { c -> !c[0].isLetter() || c[0].isLowerCase() }.toTypedArray()
        }.toTypedArray()
        val UPPER_POOLS: Array<Array<String>> = LETTER_POOLS.map { pool ->
            pool.filter { c -> !c[0].isLetter() || c[0].isUpperCase() }.toTypedArray()
        }.toTypedArray()
    }
}
