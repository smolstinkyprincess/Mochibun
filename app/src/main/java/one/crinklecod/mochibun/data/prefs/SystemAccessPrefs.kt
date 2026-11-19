package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_CURSOR_WATCHDOG_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_HOOK_KEY_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_HOOK_SYSUI_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_ROOT_QUICK_SETTINGS_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_CURSOR_WATCHDOG_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_HOOK_KEY_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_HOOK_SYSUI_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_NON_ROOT_SCREEN_OFF_RESET
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_ROOT_QUICK_SETTINGS_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_SHOW_ROOT_OPTIONS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Root / LSPosed feature switches: root quick settings, root option visibility, non-root screen-
 * off reset, hook kill switches, and the cursor watchdog.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object SystemAccessPrefs {
    private val _isRootQuickSettingsEnabled = MutableStateFlow(DEFAULT_ROOT_QUICK_SETTINGS_ENABLED)
    val isRootQuickSettingsEnabled: StateFlow<Boolean> = _isRootQuickSettingsEnabled.asStateFlow()

    private val _showRootOptions = MutableStateFlow(false)
    /** Whether root / LSPosed rows are shown in settings. Loaded in [PreferencesManager.init]. */
    val showRootOptions: StateFlow<Boolean> = _showRootOptions.asStateFlow()

    fun reload(prefs: SharedPreferences) {
        _isRootQuickSettingsEnabled.value = prefs.getBoolean(
            KEY_ROOT_QUICK_SETTINGS_ENABLED,
            DEFAULT_ROOT_QUICK_SETTINGS_ENABLED
        )
    }

    /** Root option visibility is re-read on every [PreferencesManager.init] (restore / reset call it again). */
    fun refreshShowRootOptions(context: Context) {
        _showRootOptions.value = isShowRootOptions(context)
    }

    fun isRootQuickSettingsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ROOT_QUICK_SETTINGS_ENABLED, DEFAULT_ROOT_QUICK_SETTINGS_ENABLED)
    }

    fun setRootQuickSettingsEnabled(context: Context, enabled: Boolean) {
        _isRootQuickSettingsEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_ROOT_QUICK_SETTINGS_ENABLED, enabled).apply()
    }

    /** Root options start visible only when a su binary is present. */
    fun defaultShowRootOptions(suPresent: Boolean): Boolean = suPresent

    fun isShowRootOptions(
        context: Context,
        suPresent: Boolean = one.crinklecod.mochibun.core.system.RootShell.isSuBinaryPresent()
    ): Boolean = getPrefs(context).getBoolean(KEY_SHOW_ROOT_OPTIONS, defaultShowRootOptions(suPresent))

    fun setShowRootOptions(context: Context, show: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_ROOT_OPTIONS, show).apply()
        _showRootOptions.value = show
    }

    /**
     * Root's hall-switch reader or the LSPosed key hook already resets the UI on a real flip
     * close, so the screen-off reset defaults on only when neither is around.
     */
    fun defaultNonRootScreenOffReset(lidDetectionActive: Boolean): Boolean = !lidDetectionActive

    /** True when root (hall-switch reader) or the LSPosed key hook can see the flip close. */
    fun isLidDetectionActive(context: Context): Boolean =
        one.crinklecod.mochibun.core.system.RootShell.isSuBinaryPresent() ||
            one.crinklecod.mochibun.core.system.KeyHookStatus.isActive(context)

    /** [lidDetectionActive] overrides the device probe (tests); it is only consulted while unset. */
    fun isNonRootScreenOffResetEnabled(context: Context, lidDetectionActive: Boolean? = null): Boolean {
        val prefs = getPrefs(context)
        if (prefs.contains(KEY_NON_ROOT_SCREEN_OFF_RESET)) {
            return prefs.getBoolean(KEY_NON_ROOT_SCREEN_OFF_RESET, true)
        }
        return defaultNonRootScreenOffReset(lidDetectionActive ?: isLidDetectionActive(context))
    }

    fun setNonRootScreenOffResetEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NON_ROOT_SCREEN_OFF_RESET, enabled).apply()
    }

    fun isKeyHookEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_HOOK_KEY_ENABLED, DEFAULT_HOOK_KEY_ENABLED)

    fun setKeyHookEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_KEY_ENABLED, enabled).apply()
    }

    fun isSystemUiHookEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_HOOK_SYSUI_ENABLED, DEFAULT_HOOK_SYSUI_ENABLED)

    fun setSystemUiHookEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HOOK_SYSUI_ENABLED, enabled).apply()
    }

    fun isCursorWatchdogEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CURSOR_WATCHDOG_ENABLED, DEFAULT_CURSOR_WATCHDOG_ENABLED)
    }
}
