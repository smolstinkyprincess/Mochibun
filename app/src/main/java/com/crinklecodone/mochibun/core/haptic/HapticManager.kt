package com.crinklecodone.mochibun.core.haptic

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.crinklecodone.mochibun.core.sound.SoundEffect
import com.crinklecodone.mochibun.core.sound.SoundEffectManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager

/**
 * Tactile haptic feedback coordinator for the Chinoe AT-M120 / ALT Mive Style Folder (Android 12 / API 31).
 * Routes vibration via USAGE_MEDIA to guarantee tactile response even when
 * the device is locked in Silent / Vibrate ringer mode.
 */
object HapticManager {

    /**
     * Triggers tactile boundary feedback (14ms) when hitting edge limits of lists/carousels.
     * Respects the D-Pad boundary haptics preference.
     */
    fun performBoundaryHaptic(context: Context) {
        SoundEffectManager.play(context, SoundEffect.BOUNDARY)
        if (!PreferencesManager.isDpadBoundaryHaptics(context)) return
        vibrateTick(context, fallbackDurationMs = 14)
    }

    /**
     * Vibrates with a short tactile tick effect (subtle D-pad / button haptic).
     * Bypasses Android's Silent mode ringer policy by setting USAGE_MEDIA.
     */
    fun vibrateTick(context: Context, fallbackDurationMs: Long = 14) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                } catch (_: Exception) {
                    VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
            } else {
                VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA) // Bypasses notification mute
                    .build()
                vibrator.vibrate(effect, attrs)
            } else {
                val attrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_MEDIA) // Bypasses notification mute on API 31
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, attrs)
            }
        } catch (_: Exception) {
            // Low-end MT6761 hardware failsafe
        }
    }
}
