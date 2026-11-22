package one.crinklecod.mochibun.service

import android.text.InputType

internal enum class ShiftMode { LOWER, ONCE, CAPS }

internal enum class TextInputMode { MULTITAP, T9, NUMBERS }

internal data class InputModeState(
    val mode: TextInputMode,
    val shift: ShiftMode,
    val lastTextMode: TextInputMode = if (mode == TextInputMode.NUMBERS) TextInputMode.MULTITAP else mode
)

/**
 * Advances the input mode for a short `*` tap while preserving [shift]:
 *
 * - Predictive enabled: `MULTITAP (abc) -> T9 -> NUMBERS (123) -> MULTITAP (abc)`
 * - Predictive disabled: `MULTITAP (abc) -> NUMBERS (123) -> MULTITAP (abc)`
 */
internal fun nextInputMode(
    mode: TextInputMode,
    shift: ShiftMode,
    lastTextMode: TextInputMode,
    predictiveEnabled: Boolean
): InputModeState {
    val nextMode = when (mode) {
        TextInputMode.MULTITAP -> if (predictiveEnabled) TextInputMode.T9 else TextInputMode.NUMBERS
        TextInputMode.T9 -> TextInputMode.NUMBERS
        TextInputMode.NUMBERS -> TextInputMode.MULTITAP
    }
    val nextLastTextMode = when (nextMode) {
        TextInputMode.NUMBERS -> if (mode == TextInputMode.NUMBERS) lastTextMode else mode
        TextInputMode.MULTITAP, TextInputMode.T9 -> nextMode
    }
    return InputModeState(mode = nextMode, shift = shift, lastTextMode = nextLastTextMode)
}

/**
 * Cycles letter casing for a long `*` press (`Abc -> ABC -> abc -> Abc`):
 *
 * - In `NUMBERS` (`123`) mode, returns to the previous text mode (`MULTITAP` or `T9` if still
 *   enabled) with `ShiftMode.ONCE` (`Abc`) active.
 * - In `MULTITAP` or `T9`, cycles `ONCE -> CAPS -> LOWER -> ONCE`, treating an active
 *   [autoCapActive] or T9 composing [composingCapitalizeFirst] as `Abc` so holding `*` from a
 *   displayed `Abc` / `T9↑` state advances to `CAPS` (`ABC`) instead of repeating `Abc`.
 */
internal fun nextShiftOnHold(
    mode: TextInputMode,
    shift: ShiftMode,
    autoCapActive: Boolean,
    composingCapitalizeFirst: Boolean,
    lastTextMode: TextInputMode,
    predictiveEnabled: Boolean
): InputModeState {
    if (mode == TextInputMode.NUMBERS) {
        val targetMode = if (lastTextMode == TextInputMode.T9 && predictiveEnabled) {
            TextInputMode.T9
        } else {
            TextInputMode.MULTITAP
        }
        return InputModeState(
            mode = targetMode,
            shift = ShiftMode.ONCE,
            lastTextMode = targetMode
        )
    }
    val nextShift = when {
        shift == ShiftMode.CAPS -> ShiftMode.LOWER
        shift == ShiftMode.ONCE || autoCapActive || (mode == TextInputMode.T9 && composingCapitalizeFirst) ->
            ShiftMode.CAPS
        else -> ShiftMode.ONCE
    }
    return InputModeState(mode = mode, shift = nextShift, lastTextMode = mode)
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
