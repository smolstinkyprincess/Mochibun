package one.crinklecod.mochibun

import android.text.InputType
import one.crinklecod.mochibun.service.ShiftMode
import one.crinklecod.mochibun.service.StarModeState
import one.crinklecod.mochibun.service.isPredictiveAllowed
import one.crinklecod.mochibun.service.nextStarMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeInputModeTest {

    @Test
    fun cycleWithPredictiveDisabledWrapsAbcToCaps() {
        var state = StarModeState(ShiftMode.LOWER, predictiveInUse = false)

        state = nextStarMode(state.shift, autoCapActive = false, state.predictiveInUse, predictiveEnabled = false)
        assertEquals(StarModeState(ShiftMode.ONCE, predictiveInUse = false), state)

        state = nextStarMode(state.shift, autoCapActive = false, state.predictiveInUse, predictiveEnabled = false)
        assertEquals(StarModeState(ShiftMode.CAPS, predictiveInUse = false), state)

        state = nextStarMode(state.shift, autoCapActive = false, state.predictiveInUse, predictiveEnabled = false)
        assertEquals(StarModeState(ShiftMode.LOWER, predictiveInUse = false), state)
    }

    @Test
    fun cycleWithPredictiveEnabledIncludesT9AndWraps() {
        var state = StarModeState(ShiftMode.LOWER, predictiveInUse = false)

        // abc -> Abc
        state = nextStarMode(state.shift, autoCapActive = false, state.predictiveInUse, predictiveEnabled = true)
        assertEquals(StarModeState(ShiftMode.ONCE, predictiveInUse = false), state)

        // Abc -> ABC
        state = nextStarMode(state.shift, autoCapActive = false, state.predictiveInUse, predictiveEnabled = true)
        assertEquals(StarModeState(ShiftMode.CAPS, predictiveInUse = false), state)

        // ABC -> T9 (shift resets to LOWER for autoCapNext)
        state = nextStarMode(state.shift, autoCapActive = false, state.predictiveInUse, predictiveEnabled = true)
        assertEquals(StarModeState(ShiftMode.LOWER, predictiveInUse = true), state)

        // T9 -> abc
        state = nextStarMode(state.shift, autoCapActive = false, state.predictiveInUse, predictiveEnabled = true)
        assertEquals(StarModeState(ShiftMode.LOWER, predictiveInUse = false), state)
    }

    @Test
    fun autoCapActiveAdvancesDirectlyToCapsInBothModes() {
        val disabledNext = nextStarMode(
            shift = ShiftMode.LOWER,
            autoCapActive = true,
            predictiveInUse = false,
            predictiveEnabled = false
        )
        assertEquals(StarModeState(ShiftMode.CAPS, predictiveInUse = false), disabledNext)

        val enabledNext = nextStarMode(
            shift = ShiftMode.LOWER,
            autoCapActive = true,
            predictiveInUse = false,
            predictiveEnabled = true
        )
        assertEquals(StarModeState(ShiftMode.CAPS, predictiveInUse = false), enabledNext)

        // Leaving T9 when autoCapActive is true still returns to abc (LOWER, predictiveInUse = false)
        val leavingT9 = nextStarMode(
            shift = ShiftMode.LOWER,
            autoCapActive = true,
            predictiveInUse = true,
            predictiveEnabled = true
        )
        assertEquals(StarModeState(ShiftMode.LOWER, predictiveInUse = false), leavingT9)
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
