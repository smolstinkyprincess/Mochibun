package one.crinklecod.mochibun.data.prefs

import android.content.Context
import android.content.SharedPreferences
import one.crinklecod.mochibun.data.model.LidAppLedRule
import one.crinklecod.mochibun.data.model.LidAppLedRuleCodec
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_LID_LED_CHARGING
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_LID_LED_COLOR
import one.crinklecod.mochibun.data.prefs.PreferencesManager.DEFAULT_LID_LED_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_LED_APP_RULES
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_LED_CHARGING
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_LED_CHARGING_ANIMATION_ID
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_LED_COLOR
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_LED_ENABLED
import one.crinklecod.mochibun.data.prefs.PreferencesManager.KEY_LID_LED_NOTIFICATION_ANIMATION_ID
import one.crinklecod.mochibun.data.prefs.PreferencesManager.getPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Outer lid LED switches, colour, and the notification / charging animation slots.
 *
 * Internal store behind [PreferencesManager]; callers keep using the PreferencesManager API.
 * Keys and defaults stay declared on PreferencesManager so stored names never change.
 */
internal object LidLedPrefs {
    private val _isLidLedEnabled = MutableStateFlow(DEFAULT_LID_LED_ENABLED)
    val isLidLedEnabled: StateFlow<Boolean> = _isLidLedEnabled.asStateFlow()

    private val _isLidLedChargingEnabled = MutableStateFlow(DEFAULT_LID_LED_CHARGING)
    val isLidLedChargingEnabled: StateFlow<Boolean> = _isLidLedChargingEnabled.asStateFlow()

    private val _lidLedColor = MutableStateFlow(DEFAULT_LID_LED_COLOR)
    val lidLedColor: StateFlow<String> = _lidLedColor.asStateFlow()

    private val _appLedRules = MutableStateFlow<Map<String, LidAppLedRule>>(emptyMap())
    /** Per-app notification LED rules keyed by package (decoded once, not per notification). */
    val appLedRules: StateFlow<Map<String, LidAppLedRule>> = _appLedRules.asStateFlow()

    fun reload(prefs: SharedPreferences) {
        _appLedRules.value = LidAppLedRuleCodec.decode(prefs.getString(KEY_LID_LED_APP_RULES, null))
        _isLidLedEnabled.value = prefs.getBoolean(
            KEY_LID_LED_ENABLED,
            DEFAULT_LID_LED_ENABLED
        )
        _isLidLedChargingEnabled.value = prefs.getBoolean(
            KEY_LID_LED_CHARGING,
            DEFAULT_LID_LED_CHARGING
        )
        _lidLedColor.value = prefs.getString(
            KEY_LID_LED_COLOR,
            DEFAULT_LID_LED_COLOR
        ) ?: DEFAULT_LID_LED_COLOR
    }

    fun isLidLedEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_LID_LED_ENABLED,
            DEFAULT_LID_LED_ENABLED
        )
    }

    fun setLidLedEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LID_LED_ENABLED, enabled).apply()
        _isLidLedEnabled.value = enabled
    }

    fun isLidLedChargingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(
            KEY_LID_LED_CHARGING,
            DEFAULT_LID_LED_CHARGING
        )
    }

    fun setLidLedChargingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LID_LED_CHARGING, enabled).apply()
        _isLidLedChargingEnabled.value = enabled
    }

    fun getLidLedColor(context: Context): String {
        return getPrefs(context).getString(
            KEY_LID_LED_COLOR,
            DEFAULT_LID_LED_COLOR
        ) ?: DEFAULT_LID_LED_COLOR
    }

    fun setLidLedColor(context: Context, color: String) {
        getPrefs(context).edit().putString(KEY_LID_LED_COLOR, color).apply()
        _lidLedColor.value = color
    }

    fun getLidLedNotificationAnimationId(context: Context): String =
        getPrefs(context).getString(KEY_LID_LED_NOTIFICATION_ANIMATION_ID, "") ?: ""

    fun setLidLedNotificationAnimationId(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_LID_LED_NOTIFICATION_ANIMATION_ID, id).apply()
    }

    fun getLidLedChargingAnimationId(context: Context): String =
        getPrefs(context).getString(KEY_LID_LED_CHARGING_ANIMATION_ID, "") ?: ""

    fun setLidLedChargingAnimationId(context: Context, id: String) {
        getPrefs(context).edit().putString(KEY_LID_LED_CHARGING_ANIMATION_ID, id).apply()
    }

    fun setAppLedRule(context: Context, rule: LidAppLedRule) {
        val updated = _appLedRules.value.toMutableMap()
        updated.remove(rule.packageName)
        val next = LidAppLedRuleCodec.toRuleMap(updated.values + rule)
        writeAppLedRules(context, next)
    }

    fun setAppLedRules(context: Context, rules: Collection<LidAppLedRule>) {
        writeAppLedRules(context, LidAppLedRuleCodec.toRuleMap(rules))
    }

    private fun writeAppLedRules(context: Context, rules: Map<String, LidAppLedRule>) {
        val editor = getPrefs(context).edit()
        if (rules.isEmpty()) {
            editor.remove(KEY_LID_LED_APP_RULES)
        } else {
            editor.putString(KEY_LID_LED_APP_RULES, LidAppLedRuleCodec.encode(rules.values))
        }
        editor.apply()
        _appLedRules.value = rules
    }
}
