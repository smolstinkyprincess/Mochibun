package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import one.crinklecod.mochibun.data.model.PhysicalKey
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_DPAD_BOUNDARY_HAPTICS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_DPAD_WRAP_AROUND
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_AUTO_COMMIT_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_HOLD_DELAY_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_HOLD_FOR_DIGIT_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_HOLD_VIBRATION
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_IDLE_COMMIT_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_LAST_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_PREDICTIVE_T9_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.IME_LAST_MODE_MULTITAP
import one.crinklecod.mochibun.data.prefs.PreferencesManager.IME_LAST_MODE_T9
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_T9_DICTIONARY_LANG
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_T9_LEARNED_EPOCH
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_T9_LEARNED_WORDS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_T9_LEARN_NEW_WORDS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_IME_T9_USER_WORDS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_LID_OPEN_ACTION
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_SOFT_KEY_NAV_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.GLOBAL_SOFT_KEY_NAV_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_DPAD_BOUNDARY_HAPTICS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_DPAD_WRAP_AROUND
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_AUTO_COMMIT_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_HOLD_DELAY_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_HOLD_FOR_DIGIT_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_HOLD_VIBRATION
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_IDLE_COMMIT_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_LAST_MODE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_PREDICTIVE_T9_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_T9_DICTIONARY_LANG
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_T9_LEARNED_EPOCH
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_T9_LEARNED_WORDS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_T9_LEARN_NEW_WORDS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_IME_T9_USER_WORDS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LAST_LAUNCHED_PACKAGE
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_OPEN_ACTION
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_SOFT_KEY_NAV_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.MAX_IME_HOLD_DELAY_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.MAX_IME_IDLE_COMMIT_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.MIN_IME_HOLD_DELAY_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.MIN_IME_IDLE_COMMIT_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.STEP_IME_IDLE_COMMIT_MS
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_SPEED_DIAL_PREFIX
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import one.crinklecod.mochibun.data.prefs.PreferencesManager.initialized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * D-pad behaviour, soft-key navigation (mirrored to Settings.Global for MochibunKeyHook), IME hold
 * / auto-commit timing, speed dials, lid-open action, and key name formatting.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object HardwareInputPrefs {
    private val _isDpadWrapAround = MutableStateFlow(DEFAULT_DPAD_WRAP_AROUND)
    val isDpadWrapAround: StateFlow<Boolean> = _isDpadWrapAround.asStateFlow()

    private val _isDpadBoundaryHaptics = MutableStateFlow(DEFAULT_DPAD_BOUNDARY_HAPTICS)
    val isDpadBoundaryHaptics: StateFlow<Boolean> = _isDpadBoundaryHaptics.asStateFlow()

    private val _lidOpenAction = MutableStateFlow(LidOpenAction.HOME)
    val lidOpenAction: StateFlow<LidOpenAction> = _lidOpenAction.asStateFlow()

    private val _isSoftKeyNavEnabled = MutableStateFlow(DEFAULT_SOFT_KEY_NAV_ENABLED)
    val isSoftKeyNavEnabled: StateFlow<Boolean> = _isSoftKeyNavEnabled.asStateFlow()

    fun reload(prefs: SharedPreferences) {
        _isDpadWrapAround.value = prefs.getBoolean(
            KEY_DPAD_WRAP_AROUND,
            DEFAULT_DPAD_WRAP_AROUND
        )
        _lidOpenAction.value = LidOpenAction.fromId(
            prefs.getString(KEY_LID_OPEN_ACTION, DEFAULT_LID_OPEN_ACTION)
        )
        _isDpadBoundaryHaptics.value = prefs.getBoolean(
            KEY_DPAD_BOUNDARY_HAPTICS,
            DEFAULT_DPAD_BOUNDARY_HAPTICS
        )
        _isSoftKeyNavEnabled.value = prefs.getBoolean(
            KEY_SOFT_KEY_NAV_ENABLED,
            DEFAULT_SOFT_KEY_NAV_ENABLED
        )
    }

    /** `#` is the fixed, always-on Kaomoji Wheel trigger (hold). */
    fun matchesKaomojiTrigger(eventKeyCode: Int): Boolean = eventKeyCode == KeyEvent.KEYCODE_POUND

    fun formatKeyName(keyCode: Int, scanCode: Int = 0): String {
        val physical = PhysicalKey.fromKeyCode(keyCode)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null
        if (physical != null) {
            return physical.keyName
        }
        return when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "Key ${keyCode - KeyEvent.KEYCODE_0}"
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "Numpad ${keyCode - KeyEvent.KEYCODE_NUMPAD_0}"
            KeyEvent.KEYCODE_MENU -> "Menu Key"
            KeyEvent.KEYCODE_VOLUME_UP -> "Volume Up"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume Down"
            KeyEvent.KEYCODE_VOLUME_MUTE -> "Volume Mute"
            KeyEvent.KEYCODE_DEL -> "Delete / Clear"
            KeyEvent.KEYCODE_CLEAR -> "Clear Key"
            KeyEvent.KEYCODE_SEARCH -> "Search Key"
            KeyEvent.KEYCODE_FOCUS -> "Camera Focus"
            KeyEvent.KEYCODE_HEADSETHOOK -> "Headset Button"
            KeyEvent.KEYCODE_UNKNOWN -> if (scanCode > 0) "ScanCode $scanCode" else "Unknown Key"
            else -> KeyEvent.keyCodeToString(keyCode)
                .removePrefix("KEYCODE_")
                .replace('_', ' ')
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }
    }

    fun formatKeyBadge(keyCode: Int, scanCode: Int = 0): String {
        val physical = PhysicalKey.fromKeyCode(keyCode)
            ?: if (scanCode > 0) PhysicalKey.fromScancode(scanCode) else null
        return when (physical) {
            PhysicalKey.POUND -> "#"
            PhysicalKey.STAR -> "*"
            PhysicalKey.F1 -> "F1"
            PhysicalKey.F2 -> "F2"
            PhysicalKey.F3 -> "F3"
            PhysicalKey.F4 -> "F4"
            PhysicalKey.CAMERA -> "CAM"
            PhysicalKey.CALL -> "CALL"
            PhysicalKey.APP_SWITCH -> "REC"
            PhysicalKey.KEY_ENTER -> "OK"
            null -> when (keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "${keyCode - KeyEvent.KEYCODE_0}"
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "${keyCode - KeyEvent.KEYCODE_NUMPAD_0}"
                KeyEvent.KEYCODE_MENU -> "MENU"
                KeyEvent.KEYCODE_VOLUME_UP -> "VOL+"
                KeyEvent.KEYCODE_VOLUME_DOWN -> "VOL-"
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_CLEAR -> "CLR"
                KeyEvent.KEYCODE_UNKNOWN -> if (scanCode > 0) "SC$scanCode" else "KEY"
                else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").take(6)
            }
        }
    }

    fun getKaomojiTriggerBadge(): String = "#"

    fun isDpadWrapAround(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_DPAD_WRAP_AROUND,
            DEFAULT_DPAD_WRAP_AROUND
        )
    }

    fun setDpadWrapAround(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_WRAP_AROUND, enabled).apply()
        _isDpadWrapAround.value = enabled
    }

    fun isDpadBoundaryHaptics(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_DPAD_BOUNDARY_HAPTICS,
            DEFAULT_DPAD_BOUNDARY_HAPTICS
        )
    }

    fun getLidOpenAction(context: Context): LidOpenAction =
        LidOpenAction.fromId(getPrefs(context).getString(KEY_LID_OPEN_ACTION, DEFAULT_LID_OPEN_ACTION))

    fun setLidOpenAction(context: Context, action: LidOpenAction) {
        getPrefs(context).edit().putString(KEY_LID_OPEN_ACTION, action.id).apply()
        _lidOpenAction.value = action
    }

    fun getLastLaunchedPackage(context: Context): String? {
        val pkg = getPrefs(context).getString(KEY_LAST_LAUNCHED_PACKAGE, null)?.trim()
        return if (pkg.isNullOrEmpty() || pkg == context.packageName) null else pkg
    }

    fun setLastLaunchedPackage(context: Context, packageName: String?) {
        val pkg = packageName?.trim()
        if (pkg.isNullOrEmpty() || pkg == context.packageName) return
        getPrefs(context).edit().putString(KEY_LAST_LAUNCHED_PACKAGE, pkg).apply()
    }

    fun setDpadBoundaryHaptics(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DPAD_BOUNDARY_HAPTICS, enabled).apply()
        _isDpadBoundaryHaptics.value = enabled
    }

    fun getSpeedDialPackage(context: Context, digit: Int): String? {
        if (digit !in 2..9) return null
        return getPrefs(context).getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
    }

    fun setSpeedDialPackage(context: Context, digit: Int, packageName: String?) {
        if (digit !in 2..9) return
        val prefs = getPrefs(context)
        if (packageName == null) {
            prefs.edit().remove("$KEY_SPEED_DIAL_PREFIX$digit").apply()
        } else {
            prefs.edit().putString("$KEY_SPEED_DIAL_PREFIX$digit", packageName).apply()
        }
    }

    fun getAllSpeedDials(context: Context): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        val prefs = getPrefs(context)
        for (digit in 2..9) {
            val pkg = prefs.getString("$KEY_SPEED_DIAL_PREFIX$digit", null)
            if (pkg != null) {
                map[digit] = pkg
            }
        }
        return map
    }

    /** How long a keypad key must be held in MochibunImeService to type its digit. */
    fun getImeHoldDelayMs(context: Context): Int =
        getPrefs(context).getInt(KEY_IME_HOLD_DELAY_MS, DEFAULT_IME_HOLD_DELAY_MS)
            .coerceIn(MIN_IME_HOLD_DELAY_MS, MAX_IME_HOLD_DELAY_MS)

    fun setImeHoldDelayMs(context: Context, delayMs: Int) {
        getPrefs(context).edit()
            .putInt(KEY_IME_HOLD_DELAY_MS, delayMs.coerceIn(MIN_IME_HOLD_DELAY_MS, MAX_IME_HOLD_DELAY_MS))
            .apply()
    }

    /** Idle time after which MochibunImeService commits a pending multi-tap letter. */
    fun getImeIdleCommitMs(context: Context): Int =
        getPrefs(context).getInt(KEY_IME_IDLE_COMMIT_MS, DEFAULT_IME_IDLE_COMMIT_MS)
            .coerceIn(MIN_IME_IDLE_COMMIT_MS, MAX_IME_IDLE_COMMIT_MS)

    /** Next auto-commit delay one [STEP_IME_IDLE_COMMIT_MS] notch in [direction] (-1 / +1), clamped. */
    fun stepImeIdleCommitMs(currentMs: Int, direction: Int): Int =
        (currentMs + direction * STEP_IME_IDLE_COMMIT_MS)
            .coerceIn(MIN_IME_IDLE_COMMIT_MS, MAX_IME_IDLE_COMMIT_MS)

    fun setImeIdleCommitMs(context: Context, delayMs: Int) {
        getPrefs(context).edit()
            .putInt(KEY_IME_IDLE_COMMIT_MS, delayMs.coerceIn(MIN_IME_IDLE_COMMIT_MS, MAX_IME_IDLE_COMMIT_MS))
            .apply()
    }

    /** Short vibration when a held key in MochibunImeService types its digit. */
    fun isImeHoldVibrationEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_HOLD_VIBRATION, DEFAULT_IME_HOLD_VIBRATION)

    fun isImeHoldForDigitEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_HOLD_FOR_DIGIT_ENABLED, DEFAULT_IME_HOLD_FOR_DIGIT_ENABLED)

    fun setImeHoldForDigitEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_HOLD_FOR_DIGIT_ENABLED, enabled).apply()
    }

    fun isImeAutoCommitEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_AUTO_COMMIT_ENABLED, DEFAULT_IME_AUTO_COMMIT_ENABLED)

    fun setImeAutoCommitEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_AUTO_COMMIT_ENABLED, enabled).apply()
    }

    fun setImeHoldVibrationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_HOLD_VIBRATION, enabled).apply()
    }

    fun isImePredictiveT9Enabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_PREDICTIVE_T9_ENABLED, DEFAULT_IME_PREDICTIVE_T9_ENABLED)

    fun setImePredictiveT9Enabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_PREDICTIVE_T9_ENABLED, enabled).apply()
    }

    fun getImeLastMode(context: Context): String =
        normalizeImeLastMode(getPrefs(context).getString(KEY_IME_LAST_MODE, DEFAULT_IME_LAST_MODE))

    fun setImeLastMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_IME_LAST_MODE, normalizeImeLastMode(mode)).apply()
    }

    private fun normalizeImeLastMode(mode: String?): String =
        if (mode.equals(IME_LAST_MODE_T9, ignoreCase = true)) IME_LAST_MODE_T9 else IME_LAST_MODE_MULTITAP

    fun getImeT9DictionaryLang(context: Context): String =
        getPrefs(context).getString(KEY_IME_T9_DICTIONARY_LANG, DEFAULT_IME_T9_DICTIONARY_LANG)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_IME_T9_DICTIONARY_LANG

    fun setImeT9DictionaryLang(context: Context, lang: String) {
        val clean = lang.takeIf { it.isNotBlank() } ?: DEFAULT_IME_T9_DICTIONARY_LANG
        getPrefs(context).edit().putString(KEY_IME_T9_DICTIONARY_LANG, clean).apply()
    }

    fun getImeT9LearnedWordsJson(context: Context): String =
        getPrefs(context).getString(KEY_IME_T9_LEARNED_WORDS, DEFAULT_IME_T9_LEARNED_WORDS)
            ?: DEFAULT_IME_T9_LEARNED_WORDS

    fun setImeT9LearnedWordsJson(context: Context, json: String) {
        getPrefs(context).edit().putString(KEY_IME_T9_LEARNED_WORDS, json).apply()
    }

    fun isImeT9LearnNewWordsEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_IME_T9_LEARN_NEW_WORDS, DEFAULT_IME_T9_LEARN_NEW_WORDS)

    fun setImeT9LearnNewWordsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IME_T9_LEARN_NEW_WORDS, enabled).apply()
    }

    fun getImeT9UserWordsJson(context: Context): String =
        getPrefs(context).getString(KEY_IME_T9_USER_WORDS, DEFAULT_IME_T9_USER_WORDS)
            ?: DEFAULT_IME_T9_USER_WORDS

    fun setImeT9UserWordsJson(context: Context, json: String) {
        getPrefs(context).edit().putString(KEY_IME_T9_USER_WORDS, json).apply()
    }

    fun getImeT9LearnedEpoch(context: Context): Int =
        getPrefs(context).getInt(KEY_IME_T9_LEARNED_EPOCH, DEFAULT_IME_T9_LEARNED_EPOCH)

    fun bumpImeT9LearnedEpoch(context: Context): Int {
        val prefs = getPrefs(context)
        val next = prefs.getInt(KEY_IME_T9_LEARNED_EPOCH, DEFAULT_IME_T9_LEARNED_EPOCH) + 1
        prefs.edit().putInt(KEY_IME_T9_LEARNED_EPOCH, next).apply()
        return next
    }

    fun isSoftKeyNavEnabled(context: Context): Boolean =
        if (initialized) {
            _isSoftKeyNavEnabled.value
        } else {
            getPrefs(context).getBoolean(KEY_SOFT_KEY_NAV_ENABLED, DEFAULT_SOFT_KEY_NAV_ENABLED)
        }

    fun setSoftKeyNavEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SOFT_KEY_NAV_ENABLED, enabled).apply()
        _isSoftKeyNavEnabled.value = enabled
        syncSoftKeyNavToGlobal(context)
    }

    /**
     * Mirrors the Soft-Key Navigation pref into Settings.Global for MochibunKeyHook. Writes only
     * when the stored global differs. Tries a direct write first (works once WRITE_SECURE_SETTINGS
     * is granted), then falls back to root. Without either, the in-app handler still honours the pref.
     */
    fun syncSoftKeyNavToGlobal(context: Context) {
        mirrorIntToGlobal(context, GLOBAL_SOFT_KEY_NAV_ENABLED, if (isSoftKeyNavEnabled(context)) 1 else 0, 1)
    }

    /**
     * Writes [value] to Settings.Global [name] for MochibunKeyHook, only when it differs from the
     * stored value ([defaultValue] when unset). Direct write first (needs WRITE_SECURE_SETTINGS),
     * then root. Without either the hook keeps reading [defaultValue].
     */
    internal fun mirrorIntToGlobal(context: Context, name: String, value: Int, defaultValue: Int) {
        val resolver = context.contentResolver
        val current = try {
            android.provider.Settings.Global.getInt(resolver, name, defaultValue)
        } catch (_: Exception) {
            Int.MIN_VALUE
        }
        if (current == value) return
        val written = try {
            android.provider.Settings.Global.putInt(resolver, name, value)
        } catch (_: SecurityException) {
            false
        }
        if (!written) {
            one.crinklecod.mochibun.core.system.RootShell.submit("settings put global $name $value")
        }
    }
}
