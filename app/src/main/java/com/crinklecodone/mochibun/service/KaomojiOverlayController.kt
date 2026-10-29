package com.crinklecodone.mochibun.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
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

/**
 * Hosts the Kaomoji Wheel as a TYPE_ACCESSIBILITY_OVERLAY over any app.
 *
 * The window is deliberately NOT focusable: keeping input focus on the underlying text field is
 * what makes ACTION_PASTE land there. Keys reach the wheel through the service's key filter
 * ([handleKey]) instead of window focus.
 */
class KaomojiOverlayController(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var state: KaomojiWheelState? = null

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

        try {
            overlayOwner.start()
            windowManager.addView(composeView, params)
        } catch (_: Exception) {
            overlayOwner.destroy()
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
        dismiss()
        if (kaomoji.isBlank()) return
        try {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Kaomoji", kaomoji))
        } catch (_: Exception) {}
        SoundEffectManager.play(service, SoundEffect.SELECT)
        val inputNode = try {
            service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (_: Exception) {
            null
        }
        inputNode?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    fun dismiss() {
        val v = view ?: return
        view = null
        state = null
        try {
            windowManager.removeViewImmediate(v)
        } catch (_: Exception) {}
        owner?.destroy()
        owner = null
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
