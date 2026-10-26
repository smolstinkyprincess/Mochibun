package com.crinklecodone.mochibun.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.crinklecodone.mochibun.R

/**
 * Authentic Japanese 12x12 retro keitai pixel font (MaruMinyaM / まるみにゃむ).
 */
val MaruMinyaMFontFamily = FontFamily(
    Font(R.font.maruminyam)
)

/**
 * CompositionLocal providing the currently active launcher font family.
 */
val LocalLauncherFontFamily = staticCompositionLocalOf<FontFamily> { MaruMinyaMFontFamily }

/**
 * Authentic micro-pixel font (Daniel Linssen's m3x6) for retro keitai speech bubbles and pixel UI.
 */
val M3x6FontFamily = FontFamily(
    Font(R.font.m3x6)
)

/**
 * Generates an optimized Material Design 3 Typography configuration tuned for
 * the Chinoe AT-M120's 480x800 display, supporting authentic Japanese pixel/bitmap,
 * Gothic, and Mincho typefaces.
 */
fun getLauncherTypography(fontFamily: FontFamily): Typography {
    val isPixel = fontFamily == FontFamily.Monospace || fontFamily == MaruMinyaMFontFamily
    val tracking = if (isPixel) 0.sp else 0.2.sp

    return Typography(
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = tracking
        ),
        titleMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = tracking
        ),
        titleSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = tracking
        ),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = tracking
        ),
        bodyMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            letterSpacing = tracking
        ),
        bodySmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = tracking
        ),
        labelLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = tracking
        ),
        labelMedium = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = tracking
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = tracking
        )
    )
}

val Typography = getLauncherTypography(MaruMinyaMFontFamily)
