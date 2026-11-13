/**
 * Core Material 3 theming entry point for Mochibun.
 * Configures the primary pastel color schemes, typography, and edge-to-edge system bar
 * appearance tailored for the Chinoe AT-M120's 480x800 display.
 */
package one.crinklecod.mochibun.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import one.crinklecod.mochibun.core.util.findActivity
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.TypographyManager

// Baseline pastel pink palette used across launcher surfaces and interactive cards
private val PinkColorScheme = lightColorScheme(
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
 * Dynamically resolves active color schemes and authentic Keitai/pixel typography.
 */
@Composable
fun MochibunTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val fontId by PreferencesManager.themeFontFamily.collectAsState()
    val resolvedFontFamily = remember(fontId) {
        TypographyManager.resolveFontFamily(fontId)
    }
    val typography = remember(resolvedFontFamily) {
        getLauncherTypography(resolvedFontFamily)
    }

    val view = LocalView.current

    // System bar colors/icon appearance follow the active launcher palette; see
    // [MochibunSystemBarsEffect], which must run inside the LocalLauncherTheme provider.
    if (!view.isInEditMode) {
        SideEffect {
            view.isSoundEffectsEnabled = false
            view.context.findActivity()?.window?.decorView?.isSoundEffectsEnabled = false
        }
    }

    CompositionLocalProvider(
        LocalLauncherFontFamily provides resolvedFontFamily,
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = resolvedFontFamily)
    ) {
        MaterialTheme(
            colorScheme = PinkColorScheme,
            typography = typography,
            content = content
        )
    }
}

/**
 * Syncs the window's status and navigation bars with the active launcher background.
 * On the AT-M120's 480x800 panel, matching the bar background to the launcher surface avoids
 * letterbox seams, and the icon appearance flips with the background luminance: light themes get
 * dark bar icons, dark themes (Midnight Mochi, dark YAML / custom presets) get light bar icons.
 *
 * Call this inside the `CompositionLocalProvider(LocalLauncherTheme provides ...)` block,
 * because [MochibunTheme] sits above that provider and would only ever see the default theme.
 * Uses the theme's actual `bgColor` (equal to `palette.background` for built-in presets) so
 * user-edited and custom preset backgrounds are judged by what is really drawn.
 */
@Composable
fun MochibunSystemBarsEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val background = ActiveTheme.current.bgColor
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val isLightBackground = background.luminance() > 0.5f
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = isLightBackground
            isAppearanceLightNavigationBars = isLightBackground
        }
    }
}
