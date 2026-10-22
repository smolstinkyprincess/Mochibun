/**
 * Core Material 3 theming entry point for Mochibun.
 * Configures the primary pastel color schemes, typography, and edge-to-edge system bar
 * appearance tailored for the Chinoe AT-M120's 480x800 display.
 */
package com.crinklecodone.mochibun.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Baseline pastel pink palette used across launcher surfaces and interactive cards
private val PinkLightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    secondary = LavenderSecondary,
    onSecondary = LavenderOnSecondary,
    tertiary = PinkTertiary,
    background = ThemeBackground,
    onBackground = ThemeOnBackground,
    surface = ThemeSurface,
    onSurface = ThemeOnSurface
)

// Cohesive pastel scheme for dark mode contexts (maintains pastel background instead of dark purple)
private val PinkDarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    secondary = LavenderSecondary,
    onSecondary = LavenderOnSecondary,
    tertiary = PinkTertiary,
    background = ThemeBackground,
    onBackground = ThemeOnBackground,
    surface = ThemeSurface,
    onSurface = ThemeOnSurface
)

/**
 * Root theme composable wrapping all launcher activities and dialogs.
 *
 * Note: Dynamic Material You wallpaper coloring is disabled by default (dynamicColor = false).
 * System wallpaper extraction on Android 12 frequently generates muddy brown/olive tones that
 * clash with Mochibun's bespoke pastel pinks and break focus ring visibility on low-contrast screens.
 * Defaults to light/pastel scheme to preserve the launcher's signature aesthetic.
 */
@Composable
fun MochibunTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) PinkDarkColorScheme else PinkLightColorScheme
        }
        darkTheme -> PinkDarkColorScheme
        else -> PinkLightColorScheme
    }

    val view = LocalView.current

    // Synchronize the window status bar and navigation bar with the launcher surface.
    // On the AT-M120's 480x800 panel, keeping system bar backgrounds unified with ThemeBackground
    // eliminates awkward letterbox seams while light foreground icons ensure time/battery legibility.
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ThemeBackground.toArgb()
            window.navigationBarColor = ThemeBackground.toArgb()

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
