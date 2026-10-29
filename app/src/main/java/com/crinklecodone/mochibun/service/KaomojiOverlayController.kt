package com.crinklecodone.mochibun.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.dialogs.KaomojiWheelCard
import com.crinklecodone.mochibun.ui.dialogs.KaomojiWheelState
import com.crinklecodone.mochibun.ui.dialogs.handleKaomojiWheelNavKey
import com.crinklecodone.mochibun.ui.performBoundaryHaptic
import com.crinklecodone.mochibun.ui.theme.LocalLauncherTheme
import com.crinklecodone.mochibun.ui.theme.loadSavedTheme
import com.topjohnwu.superuser.Shell

/**
 * Hosts the Kaomoji Wheel as a TYPE_ACCESSIBILITY_OVERLAY over any app.
 *
 * The window is deliberately NOT focusable: keeping input focus on the underlying text field is
 * what makes ACTION_PASTE land there. Keys reach the wheel through the service's key filter
 * ([handleKey]) instead of window focus.
 *
 * The focused input node is captured before the overlay attaches, then on confirm the kaomoji is
 * injected through a fallback chain: ACTION_PASTE -> ACTION_SET_TEXT -> root `input text`.
 */
class KaomojiOverlayController(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var state: KaomojiWheelState? = null
    private val handler = Handler(Looper.getMainLooper())

    // Input field focused when the wheel was summoned; owned here and recycled after use
    private var targetInputNode: AccessibilityNodeInfo? = null
    private var pendingInject: Runnable? = null
    private var pendingTarget: AccessibilityNodeInfo? = null

    // Swallow the release of the key that summoned / was handled by the wheel
    private var swallowUpKeyCode = KeyEvent.KEYCODE_UNKNOWN

    val isShowing: Boolean get() = view != null

    fun show(summonKeyCode: Int) {
        if (view != null) return
        val wheelState = KaomojiWheelState()
        val overlayOwner = OverlayOwner()
        val theme = loadSavedTheme(PreferencesManager.getPrefs(service))
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            clipChildren = false
            setContent {
                CompositionLocalProvider(LocalLauncherTheme provides theme) {
                    KaomojiWheelCard(
                        state = wheelState,
                        onConfirm = ::confirm,
                        onDismiss = ::dismiss,
                        hintText = "◀▶ Mood  •  ▲▼ Kaomoji  •  Center: Paste"
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        cancelPendingInject()
        replaceTarget(captureInputNode())
        try {
            overlayOwner.start()
            windowManager.addView(composeView, params)
        } catch (_: Exception) {
            overlayOwner.destroy()
            replaceTarget(null)
            return
        }
        view = composeView
        owner = overlayOwner
        state = wheelState
        swallowUpKeyCode = summonKeyCode
        performBoundaryHaptic(service)
    }

    /** Key filter while the overlay is showing. Consumes everything so the app underneath stays still. */
    fun handleKey(event: KeyEvent): Boolean {
        val wheelState = state ?: return false
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) {
            if (code == swallowUpKeyCode) {
                swallowUpKeyCode = KeyEvent.KEYCODE_UNKNOWN
                return true
            }
            when (code) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_POUND -> {
                    SoundEffectManager.play(service, SoundEffect.BACK)
                    dismiss()
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> confirm()
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Ignore auto-repeat of the summoning key while it is still held
            if (code == swallowUpKeyCode) return true
            handleKaomojiWheelNavKey(wheelState, code, service)
        }
        return true
    }

    private fun confirm() {
        val kaomoji = state?.currentKaomoji.orEmpty()
        // Take ownership of the target before dismiss() recycles it
        val target = targetInputNode
        targetInputNode = null
        dismiss()
        if (kaomoji.isBlank()) {
            target?.recycle()
            return
        }
        try {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Kaomoji", kaomoji))
        } catch (_: Exception) {}
        SoundEffectManager.play(service, SoundEffect.SELECT)
        // Give window focus a moment to settle back on the app after the overlay is removed
        val runnable = Runnable {
            val node = pendingTarget
            pendingTarget = null
            pendingInject = null
            inject(kaomoji, node)
        }
        pendingTarget = target
        pendingInject = runnable
        handler.postDelayed(runnable, INJECT_DELAY_MS)
    }

    private fun inject(kaomoji: String, captured: AccessibilityNodeInfo?) {
        // A stale capture (view gone) falls back to whatever holds input focus now
        val node = if (captured != null && safeRefresh(captured)) captured else {
            captured?.recycle()
            captureInputNode()
        }
        try {
            if (node != null) {
                if (tryAction { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }) return
                if (node.isEditable && trySetText(node, kaomoji)) return
            }
            if (Shell.isAppGrantedRoot() == true) {
                // `input text` treats %s as space; quote everything else for the shell
                val escaped = kaomoji.replace(" ", "%s").replace("'", "'\\''")
                Shell.cmd("input text '$escaped'").submit()
            }
        } finally {
            node?.recycle()
        }
    }

    private fun trySetText(node: AccessibilityNodeInfo, kaomoji: String): Boolean {
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        val newText = if (start in 0..current.length && end in start..current.length) {
            current.substring(0, start) + kaomoji + current.substring(end)
        } else {
            current + kaomoji
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        if (!tryAction { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }) return false
        // Move the caret to just after the inserted kaomoji
        val caret = (if (start in 0..current.length) start else current.length) + kaomoji.length
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
        }
        tryAction { node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs) }
        return true
    }

    /** Focused input node of the app underneath; never our own overlay. Caller owns the result. */
    private fun captureInputNode(): AccessibilityNodeInfo? {
        val direct = try { service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Exception) { null }
        if (isUsableTarget(direct)) return direct
        direct?.recycle()
        val root = try { service.rootInActiveWindow } catch (_: Exception) { null } ?: return null
        val fromRoot = try { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Exception) { null }
        root.recycle()
        if (isUsableTarget(fromRoot)) return fromRoot
        fromRoot?.recycle()
        return null
    }

    private fun isUsableTarget(node: AccessibilityNodeInfo?): Boolean =
        node != null && node.packageName?.toString() != service.packageName

    private fun safeRefresh(node: AccessibilityNodeInfo): Boolean =
        try { node.refresh() } catch (_: Exception) { false }

    private inline fun tryAction(block: () -> Boolean): Boolean =
        try { block() } catch (_: Exception) { false }

    private fun replaceTarget(node: AccessibilityNodeInfo?) {
        targetInputNode?.recycle()
        targetInputNode = node
    }

    private fun cancelPendingInject() {
        pendingInject?.let { handler.removeCallbacks(it) }
        pendingInject = null
        pendingTarget?.recycle()
        pendingTarget = null
    }

    /** Service teardown: drop the overlay and any queued injection. */
    fun release() {
        cancelPendingInject()
        dismiss()
    }

    fun dismiss() {
        replaceTarget(null)
        val v = view ?: return
        view = null
        state = null
        try {
            windowManager.removeViewImmediate(v)
        } catch (_: Exception) {}
        owner?.destroy()
        owner = null
    }

    private companion object {
        const val INJECT_DELAY_MS = 50L
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
