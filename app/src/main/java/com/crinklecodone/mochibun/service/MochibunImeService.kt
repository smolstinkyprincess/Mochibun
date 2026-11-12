package com.crinklecodone.mochibun.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
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
import com.crinklecodone.mochibun.core.haptic.HapticManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.components.KeitaiCandidatePools
import com.crinklecodone.mochibun.ui.components.KeitaiCandidateState
import com.crinklecodone.mochibun.ui.components.KeitaiCandidateStrip
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.loadSavedTheme

/**
 * System-wide keypad IME, Mochibun's own text fields included.
 *
 * Multi-tap candidates show in [KeitaiCandidateStrip] and as underlined composing text in the
 * editor. It commits on an action (another keypad key, center / Enter, '#' (space), up / down
 * navigation, the input view closing) or after the configurable idle delay without a key press.
 *
 * TYPE_NULL editors (terminals like Termux) get the keyboard too once they ask for it (an explicit
 * show request), so windows without any text field still get raw keys. In those editors, DEL and
 * Enter with nothing pending go to the app as raw keys, since they handle text only via commits.
 *
 * A keypad key acts on release: a tap cycles its candidates, while holding it past the hold delay
 * types the key's own digit (or '*') straight into the editor. Tapping '*' is shift:
 * abc -> Abc (next letter) -> ABC (caps lock) -> abc. Sentence starts are capitalised
 * automatically in fields that ask for it.
 */
class MochibunImeService : InputMethodService() {

    private enum class ShiftMode { LOWER, ONCE, CAPS }

    private val candidateState = KeitaiCandidateState(poolProvider = { poolFor(it, forNewCandidate = true) })
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

    // Letter case. Read by the strip label, so Compose state.
    private var shiftMode by mutableStateOf(ShiftMode.LOWER)
    private var autoCapNext by mutableStateOf(false)
    private var autoCapSuppressed by mutableStateOf(false)
    private var isNumericField by mutableStateOf(false)

    // '#' auto-repeated (held): its release types nothing
    private var poundRepeated = false

    // True while our pending candidate is shown as composing text in the editor
    private var composingShown = false

    private val commit: (String) -> Unit = { text ->
        currentInputConnection?.commitText(text, 1)
        composingShown = false
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
    private var holdForDigitEnabled = PreferencesManager.DEFAULT_IME_HOLD_FOR_DIGIT_ENABLED
    private var holdVibrates = PreferencesManager.DEFAULT_IME_HOLD_VIBRATION
    private var autoCommitEnabled = PreferencesManager.DEFAULT_IME_AUTO_COMMIT_ENABLED
    private val holdRunnable = Runnable { fireHold() }
    private var idleCommitMs = PreferencesManager.DEFAULT_IME_IDLE_COMMIT_MS.toLong()
    private val pillIdleFadeRunnable = Runnable {
        pillAlpha = PILL_IDLE_ALPHA
    }

    // A TYPE_NULL editor (terminal) that asked for the keyboard: it gets multi-tap like any field
    private var nullEditorEngaged = false

    // Idle timeout: keeps the typed letter and clears the strip
    private val idleCommitRunnable = Runnable {
        if (holdKeyCode != KeyEvent.KEYCODE_UNKNOWN || !candidateState.isActive) return@Runnable
        batchEdit {
            candidateState.commitActive(commit)
            syncComposing()
        }
    }

    override fun onCreate() {
        super.onCreate()
        displayDensity = resources.displayMetrics.density
        owner.start()
    }

    override fun onDestroy() {
        cancelHold()
        handler.removeCallbacks(pillIdleFadeRunnable)
        moveAnimator?.cancel()
        moveAnimator = null
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
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.setEmpty()
    }

    override fun onCreateInputView(): View {
        // Compose resolves its owners from the root view, so the IME window's decor needs them too
        window?.window?.decorView?.let { attachOwners(it) }
        window?.window?.let { applyFloatingWindowStyle(it) }
        val theme = loadSavedTheme(PreferencesManager.getPrefs(this))
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
                CompositionLocalProvider(LocalLauncherTheme provides theme) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Full-height spike for validating IME window sizing and insets.
                            .height(resources.configuration.screenHeightDp.dp)
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
                                .shadow(elevation = 4.dp, shape = CircleShape)
                        )
                    }
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        cancelHold()
        handler.removeCallbacks(pillIdleFadeRunnable)
        pillAlpha = 1f
        moveAnimator?.cancel()
        moveAnimator = null
        pillTranslationY = 0f
        dockedAtTop = false
        hasCursorAnchor = false
        requestCursorAnchorUpdates()
        schedulePillIdleFade()
        candidateState.reset()
        composingShown = false
        consumedDown.fill(false)
        holdDelayMs = PreferencesManager.getImeHoldDelayMs(this).toLong()
        holdForDigitEnabled = PreferencesManager.isImeHoldForDigitEnabled(this)
        holdVibrates = PreferencesManager.isImeHoldVibrationEnabled(this)
        autoCommitEnabled = PreferencesManager.isImeAutoCommitEnabled(this)
        idleCommitMs = PreferencesManager.getImeIdleCommitMs(this).toLong()
        isNumericField = when ((info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
        if (!restarting) {
            shiftMode = ShiftMode.LOWER
            autoCapSuppressed = false
        }
        refreshAutoCap()
        if (hasTextEditor()) InputArbiter.isInputActive = true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        currentInputConnection?.requestCursorUpdates(0)
        handler.removeCallbacks(pillIdleFadeRunnable)
        moveAnimator?.cancel()
        moveAnimator = null
        dockedAtTop = false
        pillTranslationY = 0f
        pillAlpha = 1f
        pillBoundsOnScreen.setEmpty()
        hasCursorAnchor = false
        resolvePendingTap()
        candidateState.commitActive(commit)
        InputArbiter.isInputActive = false
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        // Covers editors that never showed the input view (e.g. focus moved while hidden)
        resolvePendingTap()
        candidateState.commitActive(commit)
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (candidateState.isActive) {
            // The caret left our composing text (touch, app-driven move): keep the letter as typed
            if (candidatesEnd != -1 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
                currentInputConnection?.finishComposingText()
                composingShown = false
                candidateState.reset()
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
            if (keyCode == KeyEvent.KEYCODE_POUND && !poundRepeated) {
                batchEdit {
                    candidateState.commitActive(commit)
                    commit(" ")
                    syncComposing()
                }
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
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!active) return false
                candidateState.navigateLeft()
                syncComposing()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!active) return false
                candidateState.navigateRight()
                syncComposing()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Navigation commits, then the editor (or our dialog) still gets the key
                candidateState.commitActive(commit)
                syncComposing()
                return false
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (active) {
                    candidateState.commitActive(commit)
                    syncComposing()
                    return true
                }
                return performEditorActionIfAny()
            }
            KeyEvent.KEYCODE_DEL -> {
                // Terminals delete on the raw key (their editable is always empty)
                if (!active && isNullEditor()) return false
                if (active) {
                    candidateState.handleBackspace()
                    syncComposing()
                } else {
                    deleteBeforeCursor()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!active) return false // the app handles BACK; never a "hide keyboard" step
                candidateState.reset()
                syncComposing()
                return true
            }
        }
        // Number / phone / date fields take raw digit keys (and '*' for dialling) as they are
        if (isNumericField) return false
        if (keyCode == KeyEvent.KEYCODE_STAR || KeitaiCandidatePools.forKey(keyCode) != null) {
            // Typing after the strip was hidden (e.g. BACK) would leave candidates invisible
            if (!isInputViewShown) requestShowSelf(0)
            handler.removeCallbacks(holdRunnable)
            holdKeyCode = keyCode
            holdFired = false
            handler.postDelayed(holdRunnable, holdDelayMs)
            if (keyCode == KeyEvent.KEYCODE_STAR) cycleShift() else candidateState.handleKey(keyCode, commit)
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

    /** Hold timer: type the held key's digit (or '*'), replacing the pending letter typed on DOWN. */
    private fun fireHold() {
        val keyCode = holdKeyCode
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN || holdFired || !holdForDigitEnabled) return
        holdFired = true
        batchEdit {
            if (keyCode == KeyEvent.KEYCODE_STAR) {
                // Tapping STAR cycled shift on DOWN; held STAR is typing a literal '*'
                shiftMode = ShiftMode.LOWER
                autoCapSuppressed = false
                candidateState.reset()
                commit("*")
            } else {
                candidateState.reset()
                literalFor(keyCode)?.let(commit)
            }
            syncComposing()
        }
        if (holdVibrates) HapticManager.vibrateTick(this)
    }

    /** Ends the pending keypad press: key already acted on DOWN, so just clear hold timer state. */
    private fun resolvePendingTap() {
        handler.removeCallbacks(holdRunnable)
        holdKeyCode = KeyEvent.KEYCODE_UNKNOWN
        holdFired = false
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

    /** '*' tap: abc -> Abc -> ABC -> abc, starting from what the label currently shows. */
    private fun cycleShift() {
        shiftMode = when {
            shiftMode == ShiftMode.CAPS -> ShiftMode.LOWER
            shiftMode == ShiftMode.ONCE || isAutoCapActive() -> ShiftMode.CAPS
            else -> ShiftMode.ONCE
        }
        // Choosing lowercase overrides automatic sentence capitals until the next letter
        autoCapSuppressed = shiftMode == ShiftMode.LOWER
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
    }

    /** Mirrors the pending candidate into the editor as composing (underlined) text. */
    private fun syncComposing() {
        handler.removeCallbacks(idleCommitRunnable)
        val ic = currentInputConnection ?: return
        if (candidateState.isActive) {
            if (autoCommitEnabled) handler.postDelayed(idleCommitRunnable, idleCommitMs)
            ic.setComposingText(candidateState.candidates[candidateState.selectedIndex], 1)
            composingShown = true
        } else if (composingShown) {
            // Candidate dropped (BACK / DEL): remove its composing text
            ic.setComposingText("", 1)
            ic.finishComposingText()
            composingShown = false
        }
        refreshAutoCap()
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
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            // Code points, so one press removes a whole ♡ / emoji surrogate pair
            ic.deleteSurroundingTextInCodePoints(1, 0)
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

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    private companion object {
        // Covers every Android keycode (288 on API 31), same as InputArbiter's table
        const val KEYCODE_TABLE_SIZE = 512

        const val PILL_IDLE_TIMEOUT_MS = 1_500L
        const val PILL_IDLE_ALPHA = 0.35f
        const val PILL_MOVE_DURATION_MS = 220L
        const val DOCK_ENTER_MARGIN_DP = 12f
        const val DOCK_EXIT_MARGIN_DP = 28f
        const val TOP_SAFE_MARGIN_DP = 32f

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
