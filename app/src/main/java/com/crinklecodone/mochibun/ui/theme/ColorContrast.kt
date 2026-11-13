package com.crinklecodone.mochibun.ui.theme

import kotlin.math.pow

/**
 * Tiny WCAG contrast helpers on packed ARGB ints (no Android / Compose dependencies), so the
 * same math can run inside the launcher and inside the SystemUI LSPosed hook.
 * Alpha is ignored: colors are treated as opaque.
 */
object ColorContrast {

    /** WCAG 2.x minimum contrast for normal-size text. */
    const val MIN_TEXT_CONTRAST = 4.5

    /** Relative luminance (0.0 = black, 1.0 = white) of an ARGB color. */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Contrast ratio between two colors, from 1.0 (identical) to 21.0 (black on white). */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Returns [preferred] when it is already legible on [background], otherwise whichever of
     * [preferred] / [alternate] has the higher contrast. Light palettes keep their usual ink;
     * dark palettes (light ink + light accent fill) fall back to the dark surface color.
     */
    fun legibleInk(background: Int, preferred: Int, alternate: Int): Int {
        if (ratio(preferred, background) >= MIN_TEXT_CONTRAST) return preferred
        return if (ratio(alternate, background) > ratio(preferred, background)) alternate else preferred
    }

    fun darkerOf(a: Int, b: Int): Int = if (luminance(a) <= luminance(b)) a else b

    fun lighterOf(a: Int, b: Int): Int = if (luminance(a) > luminance(b)) a else b
}

/** Replaces the alpha byte of a packed ARGB color. */
fun Int.withAlpha(alpha: Int): Int = (alpha shl 24) or (this and 0x00FFFFFF)
