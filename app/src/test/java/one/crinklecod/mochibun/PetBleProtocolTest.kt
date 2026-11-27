package one.crinklecod.mochibun

import kotlinx.coroutines.Dispatchers
import one.crinklecod.mochibun.data.model.BundleMascotDialogue
import one.crinklecod.mochibun.pet.FeedResult
import one.crinklecod.mochibun.pet.PetBleActions
import one.crinklecod.mochibun.pet.PetBleCommand
import one.crinklecod.mochibun.pet.PetBleDialogue
import one.crinklecod.mochibun.pet.PetBleProtocol
import one.crinklecod.mochibun.pet.PetBleReaction
import one.crinklecod.mochibun.pet.PetBleSnapshot
import one.crinklecod.mochibun.pet.PetManager
import one.crinklecod.mochibun.pet.PetMood
import one.crinklecod.mochibun.pet.PetState
import one.crinklecod.mochibun.pet.dispatchPetBleCommand
import one.crinklecod.mochibun.ui.components.PET_ALREADY_FULL_LINES
import one.crinklecod.mochibun.ui.components.PET_FED_LINES
import one.crinklecod.mochibun.ui.components.PET_PETTED_LINES
import one.crinklecod.mochibun.ui.components.STEP_NO_TREATS_HINT
import one.crinklecod.mochibun.ui.components.petMoodLines
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetBleProtocolTest {

    private val snapshot = PetBleSnapshot(
        fullnessPct = 82, joyPct = 75, treats = 3, mood = PetMood.HAPPY, lastUpdatedMs = 1_732_612_345_678L,
    )

    private fun ByteArray.text() = toString(Charsets.UTF_8)

    // ------------------------------------------------------------------ payload

    @Test
    fun encodesCompactJsonWithReaction() {
        val json = PetBleProtocol.encodeState(snapshot, PetBleReaction.FED, "nom nom! ♡").text()
        assertEquals(
            "{\"v\":1,\"f\":82,\"j\":75,\"t\":3,\"m\":\"happy\",\"s\":0,\"u\":1732612345678,\"r\":\"fed\",\"l\":\"nom nom! ♡\"}",
            json,
        )
    }

    @Test
    fun omitsReactionAndFlagsSleeping() {
        val sleepy = snapshot.copy(mood = PetMood.SLEEPY)
        val json = PetBleProtocol.encodeState(sleepy, null, "eepy... zzZ").text()
        assertTrue(json, json.contains("\"m\":\"sleepy\",\"s\":1,"))
        assertTrue(json, !json.contains("\"r\""))
    }

    @Test
    fun reactionWireNamesMatchFirmwareSpec() {
        assertEquals(listOf("fed", "noTreats", "full", "pet"), PetBleReaction.entries.map { it.wire })
    }

    @Test
    fun escapesQuotesBackslashesAndControlChars() {
        val json = PetBleProtocol.encodeState(snapshot, null, "say \"hi\" \\ o/\n\u0001").text()
        assertTrue(json, json.endsWith("\"l\":\"say \\\"hi\\\" \\\\ o/\\n\\u0001\"}"))
    }

    @Test
    fun snapshotRoundsAndClampsPercentages() {
        val s = PetBleSnapshot.of(PetState(fullness = 0.826f, joy = 1.4f, treats = 9, lastUpdatedMs = 5L), PetMood.CONTENT)
        assertEquals(83, s.fullnessPct)
        assertEquals(100, s.joyPct)
        assertEquals(0, PetBleSnapshot.of(PetState(fullness = Float.NaN), PetMood.HUNGRY).fullnessPct)
    }

    @Test
    fun visibleKeyIgnoresOnlyTimestamp() {
        assertEquals(snapshot.visibleKey(), snapshot.copy(lastUpdatedMs = 99L).visibleKey())
        assertNotEquals(snapshot.visibleKey(), snapshot.copy(treats = 2).visibleKey())
        assertNotEquals(snapshot.visibleKey(), snapshot.copy(mood = PetMood.SLEEPY).visibleKey())
    }

    @Test
    fun longKaomojiLineIsTrimmedToFitMtuOnCodePointBoundary() {
        val longLine = "(˶ᵔ ᵕ ᵔ˶) ♡ 🍡✨ ".repeat(40)
        val worst = snapshot.copy(fullnessPct = 100, joyPct = 100, mood = PetMood.CONTENT)
        val bytes = PetBleProtocol.encodeState(worst, PetBleReaction.NO_TREATS, longLine)
        assertTrue("${bytes.size} bytes", bytes.size <= PetBleProtocol.MAX_PAYLOAD_BYTES)
        assertEquals(244, PetBleProtocol.MAX_PAYLOAD_BYTES)
        val text = bytes.text()
        // Re-encoding proves no split surrogate / multi-byte sequence (would become U+FFFD)
        assertArrayEquals(bytes, text.toByteArray(Charsets.UTF_8))
        assertTrue(text, text.endsWith("…\"}"))
    }

    @Test
    fun trimmingNeverSplitsAnEscapeSequence() {
        val line = "\"".repeat(200)
        val text = PetBleProtocol.encodeState(snapshot, null, line, maxBytes = 120).text()
        assertTrue(text.toByteArray().size <= 120)
        val body = text.substringAfter("\"l\":\"").removeSuffix("…\"}")
        assertTrue(body, body.length % 2 == 0 && body.chunked(2).all { it == "\\\"" })
    }

    @Test
    fun tinyBudgetDropsLineButKeepsValidShape() {
        val text = PetBleProtocol.encodeState(snapshot, null, "hello", maxBytes = 10).text()
        assertTrue(text, text.endsWith("\"l\":\"\"}"))
    }

    @Test
    fun mtuBudget() {
        assertEquals(20, PetBleProtocol.payloadBudgetForMtu(23))
        assertEquals(244, PetBleProtocol.payloadBudgetForMtu(247))
        assertEquals(244, PetBleProtocol.payloadBudgetForMtu(517))
    }

    // ------------------------------------------------------------------ commands

    @Test
    fun commandBytesDecode() {
        assertEquals(PetBleCommand.FEED, PetBleCommand.fromBytes(byteArrayOf(0x01)))
        assertEquals(PetBleCommand.PET, PetBleCommand.fromBytes(byteArrayOf(0x02)))
        assertEquals(PetBleCommand.REFRESH, PetBleCommand.fromBytes(byteArrayOf(0x03)))
        assertNull(PetBleCommand.fromBytes(byteArrayOf(0x00)))
        assertNull(PetBleCommand.fromBytes(byteArrayOf(0x04)))
        assertNull(PetBleCommand.fromBytes(byteArrayOf(0x01, 0x02)))
        assertNull(PetBleCommand.fromBytes(byteArrayOf()))
        assertNull(PetBleCommand.fromBytes(null))
    }

    private class FakeActions(var feedResult: FeedResult = FeedResult.FED) : PetBleActions {
        val calls = mutableListOf<String>()
        override fun tryFeed(): FeedResult { calls += "feed"; return feedResult }
        override fun pet() { calls += "pet" }
        override fun refresh() { calls += "refresh" }
    }

    @Test
    fun dispatchCallsExactlyOneActionAndMapsReaction() {
        val cases = listOf(
            Triple(PetBleCommand.FEED, FeedResult.FED, PetBleReaction.FED),
            Triple(PetBleCommand.FEED, FeedResult.NO_TREATS, PetBleReaction.NO_TREATS),
            Triple(PetBleCommand.FEED, FeedResult.ALREADY_FULL, PetBleReaction.FULL),
        )
        for ((cmd, result, expected) in cases) {
            val fake = FakeActions(result)
            assertEquals(expected, dispatchPetBleCommand(cmd, fake))
            assertEquals(listOf("feed"), fake.calls)
        }
        FakeActions().let {
            assertEquals(PetBleReaction.PET, dispatchPetBleCommand(PetBleCommand.PET, it))
            assertEquals(listOf("pet"), it.calls)
        }
        FakeActions().let {
            assertNull(dispatchPetBleCommand(PetBleCommand.REFRESH, it))
            assertEquals(listOf("refresh"), it.calls)
        }
    }

    @Test
    fun liveDispatchSpendsTreatsThroughPetManager() {
        val clock = FakePetClock(now = 1_000_000_000L, day = 100L)
        PetManager.resetForTests(clock, Dispatchers.Unconfined)
        assertEquals(2, PetManager.state.value.treats)
        assertEquals(PetBleReaction.FED, dispatchPetBleCommand(PetBleCommand.FEED, PetBleActions.Live))
        assertEquals(1, PetManager.state.value.treats)
        // 0.8 + 0.3 -> full: the next FEED is refused without spending a treat
        assertEquals(PetBleReaction.FULL, dispatchPetBleCommand(PetBleCommand.FEED, PetBleActions.Live))
        assertEquals(1, PetManager.state.value.treats)
        assertEquals(PetBleReaction.PET, dispatchPetBleCommand(PetBleCommand.PET, PetBleActions.Live))
        assertEquals(clock.now, PetManager.state.value.lastPetMs)
    }

    // ------------------------------------------------------------------ dialogue

    @Test
    fun dialogueUsesReactionAndMoodPools() {
        repeat(20) {
            assertTrue(PetBleDialogue.line(PetBleReaction.FED, PetMood.HAPPY, null, true, null) in PET_FED_LINES)
            assertTrue(PetBleDialogue.line(PetBleReaction.FULL, PetMood.HAPPY, null, true, null) in PET_ALREADY_FULL_LINES)
            assertTrue(PetBleDialogue.line(PetBleReaction.PET, PetMood.HAPPY, null, true, null) in PET_PETTED_LINES)
            assertTrue(PetBleDialogue.line(null, PetMood.SLEEPY, null, true, null) in petMoodLines(PetMood.SLEEPY, null))
            assertNotEquals(STEP_NO_TREATS_HINT, PetBleDialogue.line(PetBleReaction.NO_TREATS, PetMood.HUNGRY, null, false, null))
        }
    }

    @Test
    fun dialoguePrefersCustomMascotPoolsAndAvoidsRepeat() {
        val custom = BundleMascotDialogue(fed = listOf("crunch!", "yum!"), hungry = listOf("feed me"))
        assertEquals("yum!", PetBleDialogue.line(PetBleReaction.FED, PetMood.HAPPY, "crunch!", true, custom))
        assertEquals("feed me", PetBleDialogue.line(null, PetMood.HUNGRY, null, true, custom))
    }
}
