package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.ui.components.DEFAULT_BOOP_LINES
import com.crinklecodone.mochibun.ui.components.LATE_NIGHT_LINES
import com.crinklecodone.mochibun.ui.components.LOW_BATTERY_LINES
import com.crinklecodone.mochibun.ui.components.MUSIC_LINES
import com.crinklecodone.mochibun.ui.components.MascotMood
import com.crinklecodone.mochibun.ui.components.getDefaultTimeOfDayMood
import com.crinklecodone.mochibun.ui.components.selectSpeechBubbleDialogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBubbleReactionTest {

    @Test
    fun testDialoguePoolsAreNonEmptyAndSingleLine() {
        val allLines = DEFAULT_BOOP_LINES + LATE_NIGHT_LINES + LOW_BATTERY_LINES + MUSIC_LINES
        assertTrue("All pools should contain dialogue lines", allLines.isNotEmpty())
        for (line in allLines) {
            assertTrue("Line should not be empty: $line", line.isNotBlank())
            assertTrue("Line should be single line: $line", !line.contains("\n"))
            assertTrue("Line should be compact (1-4 words + kaomoji): $line", line.length <= 32)
        }
    }

    @Test
    fun testDefaultBoopSelection() {
        val line = selectSpeechBubbleDialogue(
            hour = 12,
            isLowBattery = false,
            isMusicPlaying = false
        )
        assertTrue("Selected line should be from DEFAULT_BOOP_LINES: $line", DEFAULT_BOOP_LINES.contains(line))
    }

    @Test
    fun testLateNightContextSelection() {
        // Run several samples to verify late night pool is selected
        var lateNightCount = 0
        val sampleSize = 50
        for (i in 0 until sampleSize) {
            val line = selectSpeechBubbleDialogue(
                hour = 23,
                isLowBattery = false,
                isMusicPlaying = false
            )
            if (LATE_NIGHT_LINES.contains(line)) {
                lateNightCount++
            }
        }
        assertTrue("Late night dialogue should be selected at least occasionally at 23:00", lateNightCount > 10)
    }

    @Test
    fun testLowBatteryContextSelection() {
        var lowBatteryCount = 0
        val sampleSize = 50
        for (i in 0 until sampleSize) {
            val line = selectSpeechBubbleDialogue(
                hour = 12,
                isLowBattery = true,
                isMusicPlaying = false
            )
            if (LOW_BATTERY_LINES.contains(line)) {
                lowBatteryCount++
            }
        }
        assertTrue("Low battery dialogue should be selected when battery is low", lowBatteryCount > 10)
    }

    @Test
    fun testMusicContextSelection() {
        var musicCount = 0
        val sampleSize = 50
        for (i in 0 until sampleSize) {
            val line = selectSpeechBubbleDialogue(
                hour = 12,
                isLowBattery = false,
                isMusicPlaying = true
            )
            if (MUSIC_LINES.contains(line)) {
                musicCount++
            }
        }
        assertTrue("Music dialogue should be selected when music is playing", musicCount > 10)
    }

    @Test
    fun testPredecessorAvoidance() {
        // When previousLine is set, the selector should avoid picking that same line
        val firstLine = DEFAULT_BOOP_LINES[0]
        for (i in 0 until 40) {
            val nextLine = selectSpeechBubbleDialogue(
                previousLine = firstLine,
                hour = 12,
                isLowBattery = false,
                isMusicPlaying = false
            )
            assertNotEquals("Should never repeat the immediate predecessor: $firstLine", firstLine, nextLine)
        }
    }

    @Test
    fun testSpamBoopSelectionVariety() {
        var prev: String? = null
        val seen = mutableSetOf<String>()
        for (i in 0 until 30) {
            val line = selectSpeechBubbleDialogue(
                previousLine = prev,
                hour = 12,
                isLowBattery = false,
                isMusicPlaying = false
            )
            if (prev != null) {
                assertNotEquals("Successive lines must never be identical", prev, line)
            }
            seen.add(line)
            prev = line
        }
        assertTrue("Repeated boops should show variety of lines", seen.size >= 3)
    }
}
