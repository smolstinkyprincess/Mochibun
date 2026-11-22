package one.crinklecod.mochibun

import android.text.InputType
import one.crinklecod.mochibun.service.InputModeState
import one.crinklecod.mochibun.service.ShiftMode
import one.crinklecod.mochibun.service.TextInputMode
import one.crinklecod.mochibun.service.isPredictiveAllowed
import one.crinklecod.mochibun.service.nextInputMode
import one.crinklecod.mochibun.service.nextShiftOnHold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeInputModeTest {

    @Test
    fun shortStarCyclesModesWithPredictiveEnabledAndPreservesShift() {
        var state = InputModeState(TextInputMode.MULTITAP, ShiftMode.ONCE)

        // abc (Abc) -> T9 (T9↑)
        state = nextInputMode(state.mode, state.shift, state.lastTextMode, predictiveEnabled = true)
        assertEquals(InputModeState(TextInputMode.T9, ShiftMode.ONCE, TextInputMode.T9), state)

        // T9 -> 123 (lastTextMode remembers T9)
        state = nextInputMode(state.mode, state.shift, state.lastTextMode, predictiveEnabled = true)
        assertEquals(InputModeState(TextInputMode.NUMBERS, ShiftMode.ONCE, TextInputMode.T9), state)

        // 123 -> abc
        state = nextInputMode(state.mode, state.shift, state.lastTextMode, predictiveEnabled = true)
        assertEquals(InputModeState(TextInputMode.MULTITAP, ShiftMode.ONCE, TextInputMode.MULTITAP), state)

        // Preserves CAPS and LOWER as well
        val capsToT9 = nextInputMode(TextInputMode.MULTITAP, ShiftMode.CAPS, TextInputMode.MULTITAP, predictiveEnabled = true)
        assertEquals(InputModeState(TextInputMode.T9, ShiftMode.CAPS, TextInputMode.T9), capsToT9)
        val lowerToNumbers = nextInputMode(TextInputMode.T9, ShiftMode.LOWER, TextInputMode.T9, predictiveEnabled = true)
        assertEquals(InputModeState(TextInputMode.NUMBERS, ShiftMode.LOWER, TextInputMode.T9), lowerToNumbers)
    }

    @Test
    fun shortStarSkipsT9WhenPredictiveDisabledAndPreservesShift() {
        var state = InputModeState(TextInputMode.MULTITAP, ShiftMode.CAPS)

        // abc -> 123
        state = nextInputMode(state.mode, state.shift, state.lastTextMode, predictiveEnabled = false)
        assertEquals(InputModeState(TextInputMode.NUMBERS, ShiftMode.CAPS, TextInputMode.MULTITAP), state)

        // 123 -> abc
        state = nextInputMode(state.mode, state.shift, state.lastTextMode, predictiveEnabled = false)
        assertEquals(InputModeState(TextInputMode.MULTITAP, ShiftMode.CAPS, TextInputMode.MULTITAP), state)
    }

    @Test
    fun holdStarCyclesCaseAbcCapsLowerInMultitapAndT9() {
        for (mode in listOf(TextInputMode.MULTITAP, TextInputMode.T9)) {
            var shift = ShiftMode.LOWER

            // abc -> Abc
            var state = nextShiftOnHold(
                mode = mode,
                shift = shift,
                autoCapActive = false,
                composingCapitalizeFirst = false,
                lastTextMode = mode,
                predictiveEnabled = true
            )
            assertEquals(InputModeState(mode, ShiftMode.ONCE, mode), state)
            shift = state.shift

            // Abc -> ABC
            state = nextShiftOnHold(
                mode = mode,
                shift = shift,
                autoCapActive = false,
                composingCapitalizeFirst = false,
                lastTextMode = mode,
                predictiveEnabled = true
            )
            assertEquals(InputModeState(mode, ShiftMode.CAPS, mode), state)
            shift = state.shift

            // ABC -> abc
            state = nextShiftOnHold(
                mode = mode,
                shift = shift,
                autoCapActive = false,
                composingCapitalizeFirst = false,
                lastTextMode = mode,
                predictiveEnabled = true
            )
            assertEquals(InputModeState(mode, ShiftMode.LOWER, mode), state)
        }
    }

    @Test
    fun holdStarTreatsAutoCapAndT9ComposingCapAsAbcAndAdvancesToCaps() {
        val multitapAutoCap = nextShiftOnHold(
            mode = TextInputMode.MULTITAP,
            shift = ShiftMode.LOWER,
            autoCapActive = true,
            composingCapitalizeFirst = false,
            lastTextMode = TextInputMode.MULTITAP,
            predictiveEnabled = true
        )
        assertEquals(InputModeState(TextInputMode.MULTITAP, ShiftMode.CAPS, TextInputMode.MULTITAP), multitapAutoCap)

        val t9ComposingCap = nextShiftOnHold(
            mode = TextInputMode.T9,
            shift = ShiftMode.LOWER,
            autoCapActive = false,
            composingCapitalizeFirst = true,
            lastTextMode = TextInputMode.T9,
            predictiveEnabled = true
        )
        assertEquals(InputModeState(TextInputMode.T9, ShiftMode.CAPS, TextInputMode.T9), t9ComposingCap)
    }

    @Test
    fun holdStarInNumbersReturnsToLastAllowedTextModeWithOnceShift() {
        val fromMultitap = nextShiftOnHold(
            mode = TextInputMode.NUMBERS,
            shift = ShiftMode.LOWER,
            autoCapActive = false,
            composingCapitalizeFirst = false,
            lastTextMode = TextInputMode.MULTITAP,
            predictiveEnabled = true
        )
        assertEquals(InputModeState(TextInputMode.MULTITAP, ShiftMode.ONCE, TextInputMode.MULTITAP), fromMultitap)

        val fromT9Allowed = nextShiftOnHold(
            mode = TextInputMode.NUMBERS,
            shift = ShiftMode.CAPS,
            autoCapActive = false,
            composingCapitalizeFirst = false,
            lastTextMode = TextInputMode.T9,
            predictiveEnabled = true
        )
        assertEquals(InputModeState(TextInputMode.T9, ShiftMode.ONCE, TextInputMode.T9), fromT9Allowed)

        val fromT9Disallowed = nextShiftOnHold(
            mode = TextInputMode.NUMBERS,
            shift = ShiftMode.LOWER,
            autoCapActive = false,
            composingCapitalizeFirst = false,
            lastTextMode = TextInputMode.T9,
            predictiveEnabled = false
        )
        assertEquals(InputModeState(TextInputMode.MULTITAP, ShiftMode.ONCE, TextInputMode.MULTITAP), fromT9Disallowed)
    }

    @Test
    fun isPredictiveAllowedFiltersNumericNullPasswordEmailUriAndNoSuggestions() {
        val plainText = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertTrue(isPredictiveAllowed(plainText, hasEditor = true, isNumericField = false, nullEditorEngaged = false))
        assertFalse(isPredictiveAllowed(plainText, hasEditor = false, isNumericField = false, nullEditorEngaged = false))
        assertFalse(isPredictiveAllowed(plainText, hasEditor = true, isNumericField = true, nullEditorEngaged = false))
        assertFalse(isPredictiveAllowed(InputType.TYPE_NULL, hasEditor = true, isNumericField = false, nullEditorEngaged = true))
        assertFalse(isPredictiveAllowed(InputType.TYPE_NULL, hasEditor = true, isNumericField = false, nullEditorEngaged = false))
        assertFalse(isPredictiveAllowed(InputType.TYPE_CLASS_NUMBER, hasEditor = true, isNumericField = true, nullEditorEngaged = false))

        val disallowedVariations = intArrayOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
        for (variation in disallowedVariations) {
            assertFalse(
                "Expected variation $variation to disallow predictive text",
                isPredictiveAllowed(
                    InputType.TYPE_CLASS_TEXT or variation,
                    hasEditor = true,
                    isNumericField = false,
                    nullEditorEngaged = false
                )
            )
        }

        assertFalse(
            isPredictiveAllowed(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                hasEditor = true,
                isNumericField = false,
                nullEditorEngaged = false
            )
        )
    }
}
