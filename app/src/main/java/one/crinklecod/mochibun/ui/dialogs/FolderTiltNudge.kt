package one.crinklecod.mochibun.ui.dialogs

import kotlin.math.abs

/**
 * Pure filter behind the folder tray's "shaker charm" parallax: turns raw accelerometer
 * gravity (x, y in m/s²) into a smoothed tilt in -1..1 relative to how the phone was held when
 * the folder opened. Free of Android/Compose so `FolderTiltNudgeTest` covers it on the JVM.
 *
 * - The first reading becomes the neutral angle, so the stickers start at rest however the
 *   phone is held.
 * - Exponential smoothing ([alpha]) soaks up hand tremor.
 * - [fullScale] m/s² of change (~20° of tilt) maps to the full ±1 swing; beyond that it clamps.
 * - Changes smaller than [deadband] are dropped, so a phone lying still stops producing
 *   updates (and therefore frames) once the filter settles.
 */
internal class TiltNudgeFilter(
    private val alpha: Float = 0.15f,
    private val fullScale: Float = 3.5f,
    private val deadband: Float = 0.02f
) {
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    private var hasNeutral = false
    private var neutralX = 0f
    private var neutralY = 0f
    private var smoothX = 0f
    private var smoothY = 0f

    /** Feeds one reading; returns true when [tiltX]/[tiltY] moved enough to be worth drawing. */
    fun update(rawX: Float, rawY: Float): Boolean {
        if (!hasNeutral) {
            neutralX = rawX
            neutralY = rawY
            smoothX = rawX
            smoothY = rawY
            hasNeutral = true
            return false
        }
        smoothX += alpha * (rawX - smoothX)
        smoothY += alpha * (rawY - smoothY)
        val nextX = ((smoothX - neutralX) / fullScale).coerceIn(-1f, 1f)
        val nextY = ((smoothY - neutralY) / fullScale).coerceIn(-1f, 1f)
        if (abs(nextX - tiltX) < deadband && abs(nextY - tiltY) < deadband) return false
        tiltX = nextX
        tiltY = nextY
        return true
    }

    /** Forget the neutral angle; the next reading becomes the new rest position. */
    fun reset() {
        hasNeutral = false
        tiltX = 0f
        tiltY = 0f
    }

    companion object {
        /** Max sticker slide at full tilt, in dp. */
        const val MAX_SHIFT_DP = 10f
        /** Max extra sticker rotation at full tilt, in degrees. */
        const val MAX_ROTATION_DEG = 4f

        /**
         * Parallax depth for slot [index]: 0.7, 0.85 or 1.0, so neighbouring stickers drift by
         * slightly different amounts and the page reads as layered.
         */
        fun depthFor(index: Int): Float = 0.7f + 0.15f * (index % 3)
    }
}
