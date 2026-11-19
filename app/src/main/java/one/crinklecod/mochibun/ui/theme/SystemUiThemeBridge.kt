package one.crinklecod.mochibun.ui.theme

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import one.crinklecod.mochibun.BuildConfig
import one.crinklecod.mochibun.data.prefs.PreferencesManager

/**
 * Bridges Mochibun's active theme palette to the LSPosed hooks running inside `com.android.systemui`.
 * Dispatches explicit directed broadcasts with ARGB primitive colors so hooked SystemUI surfaces
 * (Status Bar, 4-Heart Battery, Volume Dialog, Quick Settings) re-tint dynamically at runtime.
 */
object SystemUiThemeBridge {
    private const val TAG = "SystemUiThemeBridge"

    const val ACTION_THEME_CHANGED = "one.crinklecod.mochibun.action.THEME_CHANGED"
    const val EXTRA_PRIMARY = "EXTRA_PRIMARY"
    const val EXTRA_CONTAINER = "EXTRA_CONTAINER"
    const val EXTRA_ACCENT_DEEP = "EXTRA_ACCENT_DEEP"
    const val EXTRA_TRACK = "EXTRA_TRACK"
    const val EXTRA_SCRIM = "EXTRA_SCRIM"

    /**
     * The hook has no way to pull the theme, so a SystemUI restart is only healed by our next
     * broadcast. Unchanged colors are still re-sent once this old, keeping that recovery path.
     */
    private const val UNCHANGED_RESEND_MS = 5 * 60_000L

    @Volatile
    private var lastSentColors: SystemUiColors? = null

    @Volatile
    private var lastSentAtMs = 0L

    /** ARGB colors sent to the SystemUI hook for one palette. */
    data class SystemUiColors(
        val primary: Int,
        val container: Int,
        val accentDeep: Int,
        val track: Int,
        val scrim: Int
    )

    /**
     * Maps a palette to the SystemUI broadcast colors (pure, unit-testable).
     * The hook derives contrast-safe inks from these (see ActiveHookTheme.onPrimary), so dark
     * palettes like Midnight Mochi can send their light accentDeep unchanged.
     */
    fun systemUiColors(palette: MochibunThemePalette): SystemUiColors {
        val trackInt = if (palette.squircleSecondary != palette.primary) {
            palette.squircleSecondary.toArgb()
        } else {
            palette.outline.toArgb()
        }
        return SystemUiColors(
            primary = palette.primary.toArgb(),
            container = palette.container.toArgb(),
            accentDeep = palette.deepAccent.toArgb(),
            track = trackInt,
            scrim = palette.background.toArgb().withAlpha(0xEB)
        )
    }

    fun broadcastTheme(context: Context, palette: MochibunThemePalette, onlyIfChanged: Boolean = false) {
        val colors = systemUiColors(palette)
        if (onlyIfChanged && colors == lastSentColors &&
            SystemClock.elapsedRealtime() - lastSentAtMs < UNCHANGED_RESEND_MS
        ) {
            return
        }
        broadcastColors(
            context = context,
            primary = colors.primary,
            container = colors.container,
            accentDeep = colors.accentDeep,
            track = colors.track,
            scrim = colors.scrim
        )
    }

    /**
     * Broadcasts the palette resolved from [LauncherTheme] to SystemUI.
     */
    fun broadcastTheme(context: Context, theme: LauncherTheme) {
        broadcastTheme(context, theme.palette)
    }

    /**
     * Loads the active theme from SharedPreferences and broadcasts it to SystemUI immediately.
     * With [onlyIfChanged] (Home resumes), skips the FLAG_INCLUDE_STOPPED_PACKAGES broadcast when
     * the colors match the last one sent recently.
     */
    fun broadcastSavedTheme(context: Context, onlyIfChanged: Boolean = false) {
        try {
            val prefs = PreferencesManager.getPrefs(context)
            val theme = loadSavedTheme(prefs)
            broadcastTheme(context, theme.palette, onlyIfChanged)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load and broadcast saved theme", t)
        }
    }

    /**
     * Sends the broadcast with ARGB color tokens without package targeting
     * (Android 12 drops package-targeted cross-process broadcasts across mismatched UIDs).
     */
    fun broadcastColors(
        context: Context,
        primary: Int,
        container: Int,
        accentDeep: Int,
        track: Int,
        scrim: Int
    ) {
        try {
            val intent = Intent(ACTION_THEME_CHANGED).apply {
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(EXTRA_PRIMARY, primary)
                putExtra(EXTRA_CONTAINER, container)
                putExtra(EXTRA_ACCENT_DEEP, accentDeep)
                putExtra(EXTRA_TRACK, track)
                putExtra(EXTRA_SCRIM, scrim)
            }
            context.sendBroadcast(intent)
            lastSentColors = SystemUiColors(primary, container, accentDeep, track, scrim)
            lastSentAtMs = SystemClock.elapsedRealtime()
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Broadcasted theme colors: primary=#%08X container=#%08X accentDeep=#%08X track=#%08X scrim=#%08X".format(
                        primary, container, accentDeep, track, scrim
                    )
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to send $ACTION_THEME_CHANGED broadcast", t)
        }
    }
}
