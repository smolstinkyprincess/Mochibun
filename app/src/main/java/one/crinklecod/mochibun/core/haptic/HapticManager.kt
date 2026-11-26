package one.crinklecod.mochibun.core.haptic

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import one.crinklecod.mochibun.core.sound.SoundEffect
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager

/**
 * Tactile haptic feedback coordinator for the Chinoe AT-M120 / ALT Mive Style Folder (Android 12 / API 31).
 *
 * Uses Android's [VibratorManager] (API 31+, falling back to [Context.VIBRATOR_SERVICE] on API 27–30)
 * directly with pre-allocated [VibrationEffect] instances and explicit [AudioAttributes] so that
 * rapid physical multi-tap keypresses on the MT6761 ERM motor never drop, coalesce, or get silenced
 * by global system touch-vibration settings or lack of IME ComposeView window focus.
 */
object HapticManager {

    /** Crisp 24 ms full-amplitude (255) one-shot for standard keypad taps (`0`–`9`, `*`, `#`, `DPAD`). */
    const val IME_KEY_PRESS_DURATION_MS = 24L
    const val IME_KEY_PRESS_AMPLITUDE = 255

    /** Slightly deeper 32 ms full-amplitude (255) pulse for confirm, commit, and delete (`ENTER`, `DPAD_CENTER`, `DEL`). */
    const val IME_CONFIRM_DELETE_DURATION_MS = 32L
    const val IME_CONFIRM_DELETE_AMPLITUDE = 255

    /** Rate-limit floor for held `DEL` backspace repeats (~15 Hz ratchet without choking the ERM motor). */
    const val IME_DELETE_REPEAT_INTERVAL_MS = 65L

    /** Distinct double-tick waveform for long-press triggers (`#` Kaomoji Wheel open, `*` case cycle, digit hold). */
    val IME_LONG_PRESS_TIMINGS = longArrayOf(0L, 18L, 35L, 26L)
    val IME_LONG_PRESS_AMPLITUDES = intArrayOf(0, 220, 0, 255)

    private const val BOUNDARY_FALLBACK_DURATION_MS = 14L

    // Pre-allocated VibrationEffects and attributes: zero allocations on the MT6761 keypress hot path.
    private val keyPressEffect: VibrationEffect by lazy(LazyThreadSafetyMode.PUBLICATION) {
        VibrationEffect.createOneShot(IME_KEY_PRESS_DURATION_MS, IME_KEY_PRESS_AMPLITUDE)
    }

    private val confirmDeleteEffect: VibrationEffect by lazy(LazyThreadSafetyMode.PUBLICATION) {
        VibrationEffect.createOneShot(IME_CONFIRM_DELETE_DURATION_MS, IME_CONFIRM_DELETE_AMPLITUDE)
    }

    private val longPressEffect: VibrationEffect by lazy(LazyThreadSafetyMode.PUBLICATION) {
        VibrationEffect.createWaveform(IME_LONG_PRESS_TIMINGS, IME_LONG_PRESS_AMPLITUDES, -1)
    }

    private val boundaryTickEffect: VibrationEffect by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } catch (_: Exception) {
                VibrationEffect.createOneShot(BOUNDARY_FALLBACK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        } else {
            VibrationEffect.createOneShot(BOUNDARY_FALLBACK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    private val imeSonificationAudioAttrs: AudioAttributes by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private val mediaAudioAttrs: AudioAttributes by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_MEDIA) // Bypasses notification mute on API 31
            .build()
    }

    @Volatile
    private var cachedVibrator: Vibrator? = null

    @Volatile
    private var lastDeleteRepeatUptimeMs: Long = 0L

    /** Resets rate-limiter state (used by unit tests and new delete press starts). */
    internal fun resetDeleteRepeatGate() {
        lastDeleteRepeatUptimeMs = 0L
    }

    /**
     * Resolves the hardware [Vibrator] via [VibratorManager] on API 31+ with fallback to
     * [Context.VIBRATOR_SERVICE] on API 27–30.
     */
    @Suppress("DEPRECATION")
    internal fun resolveVibrator(context: Context): Vibrator? {
        cachedVibrator?.let { return it }
        val appContext = context.applicationContext ?: context
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator ?: (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return resolved?.also { cachedVibrator = it }
    }

    /**
     * Triggers tactile boundary feedback (14ms) when hitting edge limits of lists/carousels.
     * Respects the D-Pad boundary haptics preference.
     */
    fun performBoundaryHaptic(context: Context) {
        SoundEffectManager.play(context, SoundEffect.BOUNDARY)
        if (!PreferencesManager.isDpadBoundaryHaptics(context)) return
        vibrateTick(context, fallbackDurationMs = BOUNDARY_FALLBACK_DURATION_MS)
    }

    /**
     * Crisp 24 ms @ 255 pulse for standard IME keypresses (`0`–`9`, `*`, `#`, `DPAD`).
     * Cancels any in-flight vibration before dispatching so rapid 80–150 ms multi-taps never drop.
     */
    fun vibrateKeyPress(context: Context) {
        dispatchImeEffect(context, keyPressEffect)
    }

    /**
     * Deeper 32 ms @ 255 pulse for IME confirm, commit, or delete (`ENTER`, `DPAD_CENTER`, `DEL`, `BACK` clear).
     */
    fun vibrateKeyConfirmOrDelete(context: Context) {
        lastDeleteRepeatUptimeMs = SystemClock.uptimeMillis()
        dispatchImeEffect(context, confirmDeleteEffect)
    }

    /**
     * Rate-limited 32 ms @ 255 pulse for held `DEL` key auto-repeats (fires at most once every 65 ms
     * so holding backspace feels like a smooth mechanical ratchet instead of choking the motor).
     */
    fun vibrateKeyDeleteRepeat(context: Context, nowUptimeMs: Long = SystemClock.uptimeMillis()): Boolean {
        val last = lastDeleteRepeatUptimeMs
        if (last != 0L && (nowUptimeMs - last) < IME_DELETE_REPEAT_INTERVAL_MS) {
            return false
        }
        lastDeleteRepeatUptimeMs = nowUptimeMs
        dispatchImeEffect(context, confirmDeleteEffect)
        return true
    }

    /**
     * Distinct double-tick (`[0, 18, 35, 26]` @ `[0, 220, 0, 255]`) for IME long-press triggers
     * (`#` Kaomoji Wheel open, `*` case cycle, held digit commit).
     */
    fun vibrateKeyLongPress(context: Context) {
        dispatchImeEffect(context, longPressEffect)
    }

    @Suppress("DEPRECATION")
    private fun dispatchImeEffect(context: Context, effect: VibrationEffect) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            // Cancel any in-flight vibration first so Android 12's VibratorService never drops or
            // coalesces rapid multi-tap keypresses (80–150 ms apart) on the AT-M120 ERM motor.
            vibrator.cancel()
            vibrator.vibrate(effect, imeSonificationAudioAttrs)
        } catch (_: Exception) {
            // Low-end MT6761 hardware failsafe
        }
    }

    /**
     * Vibrates with a short tactile tick effect (subtle D-pad / button haptic).
     * Bypasses Android's Silent mode ringer policy by setting USAGE_MEDIA.
     */
    fun vibrateTick(context: Context, fallbackDurationMs: Long = BOUNDARY_FALLBACK_DURATION_MS) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            val effect = if (fallbackDurationMs == BOUNDARY_FALLBACK_DURATION_MS) {
                boundaryTickEffect
            } else {
                VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA) // Bypasses notification mute
                    .build()
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, mediaAudioAttrs)
            }
        } catch (_: Exception) {
            // Low-end MT6761 hardware failsafe
        }
    }
}
