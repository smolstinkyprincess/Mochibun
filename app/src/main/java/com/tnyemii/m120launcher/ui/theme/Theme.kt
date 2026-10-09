
package com.tnyemii.m120launcher.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

private val PinkDarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    secondary = LavenderSecondary,
    onSecondary = LavenderOnSecondary,
    tertiary = PinkTertiary,
    background = ThemeOnBackground,
    onBackground = ThemeBackground,
    surface = ThemeOnBackground,
    onSurface = ThemeBackground
)

@Composable
fun M120LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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