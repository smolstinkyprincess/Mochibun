package one.crinklecod.mochibun

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import one.crinklecod.mochibun.pet.PetMood
import one.crinklecod.mochibun.ui.components.MascotAnimSlot
import one.crinklecod.mochibun.ui.components.MascotAtlasLayout
import one.crinklecod.mochibun.ui.components.MascotAtlasMath
import one.crinklecod.mochibun.ui.components.MascotSlotLayout
import one.crinklecod.mochibun.ui.components.MascotSpriteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotAtlasMathTest {

    /** The built-in atlas as decoded at half size: 512 x 1024, 128 px frames. */
    private val builtIn = MascotAtlasLayout.BUILT_IN.downsampled(MascotSpriteStore.BUILT_IN_SAMPLE_SIZE)

    private fun offsetOf(slot: MascotAnimSlot, frame: Int) =
        MascotAtlasMath.frameRect(builtIn, MascotAtlasMath.slotLayout(builtIn, slot), frame).srcOffset

    @Test
    fun builtInLayout_isEightRowsOfFour256pxFrames_decodedAt128px() {
        val full = MascotAtlasLayout.BUILT_IN
        assertEquals(256, full.frameWidth)
        assertEquals(256, full.frameHeight)
        assertTrue(full.fitsIn(1024, 2048))
        assertEquals(128, builtIn.frameWidth)
        assertEquals(128, builtIn.frameHeight)
        assertTrue(builtIn.fitsIn(512, 1024))
        assertEquals(MascotAnimSlot.entries.size, builtIn.slots.size)
        builtIn.slots.values.forEach { assertEquals(4, it.frames) }
    }

    @Test
    fun rows_followTheSlotOrder() {
        val expected = listOf(
            MascotAnimSlot.IDLE to 0, MascotAnimSlot.TALK to 1, MascotAnimSlot.HAPPY to 2,
            MascotAnimSlot.HUNGRY to 3, MascotAnimSlot.SULKY to 4, MascotAnimSlot.SLEEPY to 5,
            MascotAnimSlot.EAT to 6, MascotAnimSlot.PET to 7
        )
        for ((slot, row) in expected) {
            assertEquals(slot.name, IntOffset(0, row * 128), offsetOf(slot, 0))
        }
    }

    @Test
    fun columns_stepAcrossTheRow_andWrap() {
        assertEquals(IntOffset(0, 6 * 128), offsetOf(MascotAnimSlot.EAT, 0))
        assertEquals(IntOffset(128, 6 * 128), offsetOf(MascotAnimSlot.EAT, 1))
        assertEquals(IntOffset(384, 6 * 128), offsetOf(MascotAnimSlot.EAT, 3))
        assertEquals(IntOffset(0, 6 * 128), offsetOf(MascotAnimSlot.EAT, 4))
        assertEquals(IntOffset(384, 6 * 128), offsetOf(MascotAnimSlot.EAT, -1))
    }

    @Test
    fun everyFrame_staysInsideTheDecodedAtlas() {
        for (slot in MascotAnimSlot.entries) {
            for (frame in 0 until 4) {
                val f = MascotAtlasMath.frameRect(builtIn, MascotAtlasMath.slotLayout(builtIn, slot), frame)
                assertEquals(IntSize(128, 128), f.srcSize)
                assertTrue(f.srcOffset.x >= 0 && f.srcOffset.x + f.srcSize.width <= 512)
                assertTrue(f.srcOffset.y >= 0 && f.srcOffset.y + f.srcSize.height <= 1024)
            }
        }
    }

    @Test
    fun moodSlots() {
        assertEquals(MascotAnimSlot.HAPPY, MascotAtlasMath.moodSlot(PetMood.HAPPY))
        assertEquals(MascotAnimSlot.IDLE, MascotAtlasMath.moodSlot(PetMood.CONTENT))
        assertEquals(MascotAnimSlot.HUNGRY, MascotAtlasMath.moodSlot(PetMood.HUNGRY))
        assertEquals(MascotAnimSlot.SULKY, MascotAtlasMath.moodSlot(PetMood.SULKY))
        assertEquals(MascotAnimSlot.SLEEPY, MascotAtlasMath.moodSlot(PetMood.SLEEPY))
    }

    @Test
    fun baseSlot_loopsWithTheClock() {
        val frames = (0 until 9).map { tick ->
            MascotAtlasMath.frameFor(builtIn, MascotAnimSlot.SLEEPY, null, 0, tick).srcOffset
        }
        assertEquals((0 until 9).map { IntOffset((it % 4) * 128, 5 * 128) }, frames)
    }

    @Test
    fun reaction_playsItsFramesTwice_thenReturnsToTheMoodRow() {
        val start = 37
        val shown = (start until start + 10).map { tick ->
            MascotAtlasMath.frameFor(builtIn, MascotAnimSlot.HUNGRY, MascotAnimSlot.EAT, start, tick).srcOffset
        }
        val eatLoop = listOf(IntOffset(0, 6 * 128), IntOffset(128, 6 * 128), IntOffset(256, 6 * 128), IntOffset(384, 6 * 128))
        assertEquals(
            eatLoop + eatLoop + listOf(
                // back to HUNGRY (row 3), on the clock's own column
                IntOffset(((start + 8) % 4) * 128, 3 * 128), IntOffset(((start + 9) % 4) * 128, 3 * 128)
            ),
            shown
        )
        assertEquals(2_000L, MascotAtlasMath.REACTION_MS)
    }

    @Test
    fun reaction_onTheSameRowAsTheMood_stillStartsFromItsFirstFrame() {
        val start = 2
        val first = MascotAtlasMath.frameFor(builtIn, MascotAnimSlot.HAPPY, MascotAnimSlot.HAPPY, start, start)
        assertEquals(IntOffset(0, 2 * 128), first.srcOffset)
    }

    @Test
    fun reaction_survivesTheClockWrapping() {
        val start = MascotAtlasMath.TICK_WRAP - 2
        val third = MascotAtlasMath.frameFor(builtIn, MascotAnimSlot.IDLE, MascotAnimSlot.PET, start, 0)
        assertEquals(IntOffset(256, 7 * 128), third.srcOffset) // 2 ticks after start, across the wrap
        for (frames in 1..16) assertEquals(0, MascotAtlasMath.TICK_WRAP % frames)
    }

    @Test
    fun staticFrames_alwaysShowTheFirstColumn() {
        for (tick in 0 until 8) {
            assertEquals(IntOffset(0, 5 * 128), MascotAtlasMath.frameFor(builtIn, MascotAnimSlot.SLEEPY, null, 0, tick, staticFrames = true).srcOffset)
            assertEquals(IntOffset(0, 1 * 128), MascotAtlasMath.frameFor(builtIn, MascotAnimSlot.IDLE, MascotAnimSlot.TALK, tick, tick, staticFrames = true).srcOffset)
        }
    }

    @Test
    fun missingSlot_fallsBackToIdle() {
        val partial = MascotAtlasLayout(
            frameWidth = 32, frameHeight = 32,
            slots = mapOf(MascotAnimSlot.IDLE to MascotSlotLayout(row = 0, frames = 2))
        )
        val f = MascotAtlasMath.frameFor(partial, MascotAnimSlot.HUNGRY, null, 0, 3)
        assertEquals(IntOffset(32, 0), f.srcOffset)
        assertEquals(IntSize(32, 32), f.srcSize)
    }

    @Test
    fun fitsIn_rejectsFramesOutsideTheImage() {
        val layout = MascotAtlasLayout.uniform(frameWidth = 48, frameHeight = 48, frames = 4)
        assertTrue(layout.fitsIn(192, 384))
        assertFalse(layout.fitsIn(191, 384)) // last column cut off
        assertFalse(layout.fitsIn(192, 383)) // last row cut off
        assertFalse(MascotAtlasLayout.uniform(0, 48, 4).fitsIn(192, 384))
    }
}
