package one.crinklecod.mochibun.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** How one app's notifications drive the outer lid LEDs. */
@Serializable
enum class AppLedRuleMode(val label: String, val badge: String) {
    /** Global notification animation / color (same as having no rule). */
    DEFAULT("Default", ""),
    /** Classic shimmer in the rule's own color. */
    CUSTOM_COLOR("Custom Color", "Color"),
    /** A Lid LED Studio step animation. */
    CUSTOM_ANIMATION("Custom Animation", "Anim"),
    /** Never lights the lid (incoming calls still do). */
    MUTED("Muted", "Muted")
}

/**
 * Per-app lid LED override. [colorHex] is used by [AppLedRuleMode.CUSTOM_COLOR] (`#RRGGBB`),
 * [animationId] by [AppLedRuleMode.CUSTOM_ANIMATION] (a `LidLedAnimationRepository` id).
 */
@Serializable
data class LidAppLedRule(
    val packageName: String,
    val mode: AppLedRuleMode,
    val colorHex: String? = null,
    val animationId: String? = null
) {
    /** Drops fields the mode doesn't use and normalizes the color, so stored JSON stays tidy. */
    fun sanitized(): LidAppLedRule = LidAppLedRule(
        packageName = packageName.trim(),
        mode = mode,
        colorHex = if (mode == AppLedRuleMode.CUSTOM_COLOR) normalizeLidHex(colorHex ?: LID_OFF_HEX) else null,
        animationId = if (mode == AppLedRuleMode.CUSTOM_ANIMATION) animationId?.trim()?.takeIf { it.isNotEmpty() } else null
    )

    /** False for rules that can't do anything (blank package, custom mode missing its value). */
    val isUsable: Boolean
        get() = packageName.isNotBlank() && packageName.length <= MAX_PACKAGE_LENGTH && when (mode) {
            AppLedRuleMode.DEFAULT -> false
            AppLedRuleMode.CUSTOM_COLOR -> colorHex != null && !isLidHexOff(colorHex)
            AppLedRuleMode.CUSTOM_ANIMATION -> !animationId.isNullOrBlank()
            AppLedRuleMode.MUTED -> true
        }

    companion object {
        const val MAX_PACKAGE_LENGTH = 255
        /** Generous cap so a hand-edited backup can't bloat prefs. */
        const val MAX_RULES = 500
    }
}

/** Lenient JSON codec for the stored rule list (pref value and backup section). */
object LidAppLedRuleCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
    }
    private val listSerializer = ListSerializer(LidAppLedRule.serializer())

    fun encode(rules: Collection<LidAppLedRule>): String =
        json.encodeToString(listSerializer, rules.toList())

    /** Unreadable text decodes to no rules; unusable entries are dropped; last rule per package wins. */
    fun decode(text: String?): Map<String, LidAppLedRule> {
        if (text.isNullOrBlank()) return emptyMap()
        val parsed = runCatching { json.decodeFromString(listSerializer, text) }.getOrNull() ?: return emptyMap()
        return toRuleMap(parsed)
    }

    fun toRuleMap(rules: Iterable<LidAppLedRule>): Map<String, LidAppLedRule> {
        val map = LinkedHashMap<String, LidAppLedRule>()
        for (rule in rules) {
            val clean = rule.sanitized()
            if (clean.isUsable) map[clean.packageName] = clean
            if (map.size >= LidAppLedRule.MAX_RULES) break
        }
        return map
    }
}
