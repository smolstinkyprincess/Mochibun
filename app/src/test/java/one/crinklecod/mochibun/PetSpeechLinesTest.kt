package one.crinklecod.mochibun

import one.crinklecod.mochibun.pet.PetMood
import one.crinklecod.mochibun.ui.components.LOW_BATTERY_LINES
import one.crinklecod.mochibun.ui.components.MUSIC_LINES
import one.crinklecod.mochibun.ui.components.PET_FED_LINES
import one.crinklecod.mochibun.ui.components.MascotAnimSlot
import one.crinklecod.mochibun.ui.components.MascotReactionKind
import one.crinklecod.mochibun.ui.components.PET_ALREADY_FULL_LINES
import one.crinklecod.mochibun.ui.components.PET_NO_TREATS_LINES
import one.crinklecod.mochibun.ui.components.PET_PETTED_LINES
import one.crinklecod.mochibun.ui.components.STEP_NO_TREATS_HINT
import one.crinklecod.mochibun.ui.components.noTreatsLines
import one.crinklecod.mochibun.ui.components.selectReactionLine
import one.crinklecod.mochibun.ui.components.pickLineAvoiding
import one.crinklecod.mochibun.ui.components.petMoodLines
import one.crinklecod.mochibun.ui.components.selectSpeechBubbleDialogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSpeechLinesTest {

    private val allPools: Map<String, List<String>> =
        PetMood.entries.associate { it.name to petMoodLines(it) } + mapOf(
            "FED" to PET_FED_LINES,
            "ALREADY_FULL" to PET_ALREADY_FULL_LINES,
            "NO_TREATS" to PET_NO_TREATS_LINES,
            "PETTED" to PET_PETTED_LINES,
        )

    @Test
    fun everyPoolHasFourOrMoreShortSingleLines() {
        for ((name, pool) in allPools) {
            assertTrue("$name needs 4+ lines", pool.size >= 4)
            assertEquals("$name has duplicate lines", pool.size, pool.toSet().size)
            for (line in pool) {
                assertTrue("$name line blank", line.isNotBlank())
                assertTrue("$name line has a newline: $line", !line.contains("\n"))
                assertTrue("$name line too long for the bubble: $line", line.length <= 32)
            }
        }
    }

    @Test
    fun pickLineAvoiding_neverRepeatsThePreviousLine() {
        for ((_, pool) in allPools) {
            var prev: String? = null
            repeat(40) {
                val line = pickLineAvoiding(pool, prev)
                assertTrue(line in pool)
                assertNotEquals(prev, line)
                prev = line
            }
        }
        // A one-line pool can only repeat itself
        assertEquals("solo", pickLineAvoiding(listOf("solo"), "solo"))
    }

    @Test
    fun needsAndBedtime_alwaysSpeakFromTheirMoodPool() {
        for (mood in listOf(PetMood.HUNGRY, PetMood.SULKY, PetMood.SLEEPY)) {
            repeat(40) {
                val line = selectSpeechBubbleDialogue(
                    hour = 23, isLowBattery = true, isMusicPlaying = true, petMood = mood
                )
                assertTrue("$mood said '$line'", line in petMoodLines(mood))
            }
        }
    }

    @Test
    fun happyAndContent_useTheirPool_whenNothingElseIsGoingOn() {
        for (mood in listOf(PetMood.HAPPY, PetMood.CONTENT)) {
            repeat(40) {
                val line = selectSpeechBubbleDialogue(
                    hour = 23, isLowBattery = false, isMusicPlaying = false, petMood = mood
                )
                assertTrue("$mood said '$line'", line in petMoodLines(mood))
            }
        }
    }

    @Test
    fun happyMood_stillMixesInMusicAndLowBatteryLines() {
        var contextual = 0
        var mood = 0
        repeat(200) {
            val line = selectSpeechBubbleDialogue(
                hour = 12, isLowBattery = true, isMusicPlaying = true, petMood = PetMood.HAPPY
            )
            when (line) {
                in MUSIC_LINES, in LOW_BATTERY_LINES -> contextual++
                in petMoodLines(PetMood.HAPPY) -> mood++
                else -> throw AssertionError("unexpected line '$line'")
            }
        }
        assertTrue("contextual lines should lead (~70%): $contextual", contextual > 100)
        assertTrue("mood lines should still appear: $mood", mood > 20)
    }

    @Test
    fun moodSelection_neverRepeatsThePreviousLine() {
        var prev: String? = null
        repeat(40) {
            val line = selectSpeechBubbleDialogue(
                previousLine = prev, hour = 12, isLowBattery = false, isMusicPlaying = false,
                petMood = PetMood.CONTENT
            )
            assertNotEquals(prev, line)
            prev = line
        }
    }

    @Test
    fun pillReactions_speakFromTheRightPool_andPlayTheRightSlot() {
        val chat = "chat line"
        repeat(20) {
            assertTrue(selectReactionLine(MascotReactionKind.FED, null) { chat } in PET_FED_LINES)
            assertTrue(selectReactionLine(MascotReactionKind.ALREADY_FULL, null) { chat } in PET_ALREADY_FULL_LINES)
            assertTrue(selectReactionLine(MascotReactionKind.NO_TREATS, null) { chat } in PET_NO_TREATS_LINES)
            assertTrue(selectReactionLine(MascotReactionKind.PETTED, null) { chat } in PET_PETTED_LINES)
        }
        assertEquals(chat, selectReactionLine(MascotReactionKind.CHAT, null) { chat })
        assertEquals("+1 🍡 treat earned! ♡", selectReactionLine(MascotReactionKind.TREATS_EARNED, null, earnedCount = 1) { chat })
        assertEquals("+2 🍡 treats earned! ♡", selectReactionLine(MascotReactionKind.TREATS_EARNED, null, earnedCount = 2) { chat })

        val previous = PET_FED_LINES[0]
        repeat(20) { assertNotEquals(previous, selectReactionLine(MascotReactionKind.FED, previous) { chat }) }

        assertEquals(MascotAnimSlot.EAT, MascotReactionKind.FED.slot)
        assertEquals(MascotAnimSlot.PET, MascotReactionKind.PETTED.slot)
        assertEquals(MascotAnimSlot.TALK, MascotReactionKind.CHAT.slot)
        assertEquals(MascotAnimSlot.TALK, MascotReactionKind.ALREADY_FULL.slot)
        assertEquals(MascotAnimSlot.TALK, MascotReactionKind.NO_TREATS.slot)
        assertEquals(MascotAnimSlot.HAPPY, MascotReactionKind.TREATS_EARNED.slot)
        // Built-in atlas row order
        assertEquals((0..7).toList(), MascotAnimSlot.entries.map { it.defaultRow })
    }

    @Test
    fun noTreatsLines_excludesWalkHintWhenStepSensorMissing() {
        assertTrue(STEP_NO_TREATS_HINT in noTreatsLines(hasStepSensor = true))
        val noSteps = noTreatsLines(hasStepSensor = false)
        assertFalse(STEP_NO_TREATS_HINT in noSteps)
        assertTrue(noSteps.size >= 4)
        repeat(25) {
            assertNotEquals(
                STEP_NO_TREATS_HINT,
                selectReactionLine(MascotReactionKind.NO_TREATS, null, hasStepSensor = false) { "" }
            )
        }
    }
}
