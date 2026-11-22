package one.crinklecod.mochibun

import one.crinklecod.mochibun.service.PredictiveInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveInputTest {

    @Test
    fun pushAndPopMaintainDigitsAndCapitalizeFirstFlag() {
        val input = PredictiveInput()
        assertTrue(input.isEmpty)
        assertFalse(input.capitalizeFirst)

        // First digit captures autoCapActive = true
        input.push('4', autoCapActive = true)
        assertFalse(input.isEmpty)
        assertEquals("4", input.digits)
        assertTrue(input.capitalizeFirst)

        // Subsequent digits preserve the initial capitalizeFirst even when autoCap becomes false
        input.push('3', autoCapActive = false)
        input.push('5', autoCapActive = false)
        assertEquals("435", input.digits)
        assertTrue(input.capitalizeFirst)

        assertTrue(input.pop())
        assertEquals("43", input.digits)
        assertTrue(input.capitalizeFirst)

        assertTrue(input.pop())
        assertEquals("4", input.digits)
        assertTrue(input.capitalizeFirst)

        // Popping the final digit clears everything
        assertTrue(input.pop())
        assertTrue(input.isEmpty)
        assertEquals("", input.digits)
        assertFalse(input.capitalizeFirst)

        // Popping when already empty is a no-op
        assertFalse(input.pop())
    }

    @Test
    fun clearResetsDigitsCandidatesAndFlags() {
        val input = PredictiveInput()
        input.push('4', autoCapActive = true)
        input.push('6', autoCapActive = false)
        input.setCandidates(listOf("in", "i'm", "go"), rawDigitFallback = false)
        assertEquals("i'm", input.lowercaseWordAt(1))

        input.clear()
        assertTrue(input.isEmpty)
        assertFalse(input.capitalizeFirst)
        assertFalse(input.rawDigitFallback)
        assertTrue(input.lowercaseCandidates.isEmpty())
        assertNull(input.lowercaseWordAt(0))
    }

    @Test
    fun rawDigitFallbackReturnsNullForLearnedWord() {
        val input = PredictiveInput()
        input.push('2')
        input.push('2')
        input.setCandidates(listOf("22"), rawDigitFallback = true)
        assertTrue(input.rawDigitFallback)
        assertNull(input.lowercaseWordAt(0))
    }

    @Test
    fun applyCaseHandlesLowerSentenceCapFullCapsAndIForms() {
        assertEquals("", PredictiveInput.applyCase("", upperAll = false, capitalizeFirst = true))
        assertEquals("hello", PredictiveInput.applyCase("hello", upperAll = false, capitalizeFirst = false))
        assertEquals("Hello", PredictiveInput.applyCase("hello", upperAll = false, capitalizeFirst = true))
        assertEquals("HELLO", PredictiveInput.applyCase("hello", upperAll = true, capitalizeFirst = false))
        assertEquals("HELLO", PredictiveInput.applyCase("hello", upperAll = true, capitalizeFirst = true))

        // Standalone "i" and "i'*" contractions always show capitalised
        assertEquals("I", PredictiveInput.applyCase("i", upperAll = false, capitalizeFirst = false))
        assertEquals("I'm", PredictiveInput.applyCase("i'm", upperAll = false, capitalizeFirst = false))
        assertEquals("I'll", PredictiveInput.applyCase("i'll", upperAll = false, capitalizeFirst = false))
        assertEquals("I've", PredictiveInput.applyCase("i've", upperAll = false, capitalizeFirst = false))
        assertEquals("I'd", PredictiveInput.applyCase("i'd", upperAll = false, capitalizeFirst = false))
        assertEquals("I'M", PredictiveInput.applyCase("i'm", upperAll = true, capitalizeFirst = false))

        // Non-contraction words starting with 'i' stay lowercase when not capitalising
        assertEquals("in", PredictiveInput.applyCase("in", upperAll = false, capitalizeFirst = false))
        assertEquals("ice", PredictiveInput.applyCase("ice", upperAll = false, capitalizeFirst = false))
        assertEquals("Ice", PredictiveInput.applyCase("ice", upperAll = false, capitalizeFirst = true))

        // Raw digits remain untouched
        assertEquals("46", PredictiveInput.applyCase("46", upperAll = false, capitalizeFirst = true))
    }
}
