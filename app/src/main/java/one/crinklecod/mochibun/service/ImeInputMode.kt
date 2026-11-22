package one.crinklecod.mochibun.service

import android.text.InputType

internal enum class ShiftMode { LOWER, ONCE, CAPS }

internal data class StarModeState(
    val shift: ShiftMode,
    val predictiveInUse: Boolean
)

/**
 * Advances the input mode for a short `*` tap.
 *
 * - Predictive disabled: `abc -> Abc -> ABC -> abc` (with auto-cap `Abc` advancing to `ABC`).
 * - Predictive enabled: `abc -> Abc -> ABC -> T9 -> abc`. Entering `T9` sets [ShiftMode.LOWER]
 *   so sentence-start capitalisation comes from `autoCapNext`.
 */
internal fun nextStarMode(
    shift: ShiftMode,
    autoCapActive: Boolean,
    predictiveInUse: Boolean,
    predictiveEnabled: Boolean
): StarModeState {
    if (predictiveInUse) {
        return StarModeState(shift = ShiftMode.LOWER, predictiveInUse = false)
    }
    if (shift == ShiftMode.CAPS) {
        return StarModeState(shift = ShiftMode.LOWER, predictiveInUse = predictiveEnabled)
    }
    val nextShift = if (shift == ShiftMode.ONCE || autoCapActive) {
        ShiftMode.CAPS
    } else {
        ShiftMode.ONCE
    }
    return StarModeState(shift = nextShift, predictiveInUse = false)
}

/**
 * Returns whether T9 predictive text is eligible for the active editor.
 *
 * Disallowed for numeric/phone/datetime fields, `TYPE_NULL` / terminal editors, password / email /
 * URI variations, and fields with `TYPE_TEXT_FLAG_NO_SUGGESTIONS`.
 */
internal fun isPredictiveAllowed(
    inputType: Int,
    hasEditor: Boolean,
    isNumericField: Boolean,
    nullEditorEngaged: Boolean
): Boolean {
    if (!hasEditor || isNumericField || nullEditorEngaged || inputType == InputType.TYPE_NULL) {
        return false
    }
    if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
    if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
        variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
        variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
        variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
        variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
        variation != InputType.TYPE_TEXT_VARIATION_URI
}
