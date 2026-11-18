package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.model.BundleMascotConfig
import one.crinklecod.mochibun.data.model.BundleMascotDialogue
import one.crinklecod.mochibun.data.model.BundleMascotSlot
import one.crinklecod.mochibun.data.model.MascotAtlasValidator
import one.crinklecod.mochibun.data.model.MascotAtlasValidator.Result
import one.crinklecod.mochibun.data.model.sanitizeMascotDialogue
import one.crinklecod.mochibun.ui.components.MascotAnimSlot
import one.crinklecod.mochibun.ui.components.MascotAtlasLayout
import one.crinklecod.mochibun.ui.components.MascotAtlasMath
import one.crinklecod.mochibun.ui.components.MascotSlotLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class MascotAtlasValidatorTest {

    private fun valid(r: Result): MascotAtlasLayout {
        assertTrue("expected valid, got $r", r is Result.Valid)
        return (r as Result.Valid).layout
    }

    private fun invalid(r: Result) = assertTrue("expected invalid, got $r", r is Result.Invalid)

    // ── Inference (a plain PNG with no layout) ─────────────────────────────

    @Test
    fun infer_builtInSizedAtlas() {
        val layout = valid(MascotAtlasValidator.inferLayout(1024, 2048))
        assertEquals(256, layout.frameWidth)
        assertEquals(256, layout.frameHeight)
        assertEquals(8, layout.slots.size)
        MascotAnimSlot.entries.forEach { assertEquals(MascotSlotLayout(it.defaultRow, 4), layout.slots[it]) }
    }

    @Test
    fun infer_smallAndWideAtlases() {
        assertEquals(16, valid(MascotAtlasValidator.inferLayout(16 * 16, 16 * 8)).slots.getValue(MascotAnimSlot.IDLE).frames)
        assertEquals(1, valid(MascotAtlasValidator.inferLayout(48, 384)).slots.getValue(MascotAnimSlot.EAT).frames)
        assertEquals(8, valid(MascotAtlasValidator.inferLayout(2048, 2048)).slots.getValue(MascotAnimSlot.PET).frames)
    }

    @Test
    fun infer_rejectsNonDivisibleSizes() {
        invalid(MascotAtlasValidator.inferLayout(192, 380)) // 380 isn't 8 equal rows
        invalid(MascotAtlasValidator.inferLayout(200, 384)) // 200 isn't a whole number of 48 px frames
    }

    @Test
    fun infer_rejectsTooManyFrames_andOutOfRangeFrameSizes() {
        invalid(MascotAtlasValidator.inferLayout(16 * 17, 16 * 8)) // 17 frames
        invalid(MascotAtlasValidator.inferLayout(64, 8 * 8))       // 8 px frames
        invalid(MascotAtlasValidator.inferLayout(264, 264 * 8))    // 264 px frames, and > 2048 tall
        invalid(MascotAtlasValidator.inferLayout(0, 0))
    }

    // ── Declared layouts (bundles) ──────────────────────────────────────────

    private fun config(vararg slots: Pair<String, BundleMascotSlot>, fw: Int = 48, fh: Int = 48) =
        BundleMascotConfig(frameWidth = fw, frameHeight = fh, slots = slots.toMap())

    @Test
    fun declared_validLayout() {
        val layout = valid(
            MascotAtlasValidator.layoutFromConfig(
                config("idle" to BundleMascotSlot(0, 4), "EAT" to BundleMascotSlot(6, 2)), 192, 384
            )
        )
        assertEquals(MascotSlotLayout(0, 4), layout.slots[MascotAnimSlot.IDLE])
        assertEquals(MascotSlotLayout(6, 2), layout.slots[MascotAnimSlot.EAT])
    }

    @Test
    fun declared_rowOutOfRangeIsRejected() {
        invalid(MascotAtlasValidator.layoutFromConfig(config("idle" to BundleMascotSlot(8, 4)), 192, 384))
        invalid(MascotAtlasValidator.layoutFromConfig(config("idle" to BundleMascotSlot(-1, 4)), 192, 384))
        // Row 7 doesn't exist in an image only 5 rows tall
        invalid(MascotAtlasValidator.layoutFromConfig(config("pet" to BundleMascotSlot(7, 4)), 192, 240))
    }

    @Test
    fun declared_framesOutOfRangeOrPastTheEdgeAreRejected() {
        invalid(MascotAtlasValidator.layoutFromConfig(config("idle" to BundleMascotSlot(0, 17)), 192, 384))
        invalid(MascotAtlasValidator.layoutFromConfig(config("idle" to BundleMascotSlot(0, 0)), 192, 384))
        invalid(MascotAtlasValidator.layoutFromConfig(config("idle" to BundleMascotSlot(0, 5)), 192, 384))
    }

    @Test
    fun declared_frameSizeLimits() {
        invalid(MascotAtlasValidator.layoutFromConfig(config(fw = 15, fh = 48), 192, 384))
        invalid(MascotAtlasValidator.layoutFromConfig(config(fw = 257, fh = 48), 1028, 384))
        // 256 px frames are allowed up to a 2048 x 2048 atlas
        valid(MascotAtlasValidator.layoutFromConfig(config(fw = 256, fh = 256), 2048, 2048))
        invalid(MascotAtlasValidator.layoutFromConfig(config(fw = 256, fh = 256), 2304, 2048))
        // Bigger than 8 rows of frames
        invalid(MascotAtlasValidator.layoutFromConfig(config(fw = 16, fh = 16), 64, 16 * 9))
    }

    @Test
    fun declared_unknownSlotKeysAreIgnored() {
        val layout = valid(
            MascotAtlasValidator.layoutFromConfig(
                config("idle" to BundleMascotSlot(0, 4), "dance" to BundleMascotSlot(99, 99)), 192, 384
            )
        )
        assertEquals(setOf(MascotAnimSlot.IDLE), layout.slots.keys)
    }

    @Test
    fun declared_missingSlotsFallBackToIdleWhenDrawn() {
        val layout = valid(MascotAtlasValidator.layoutFromConfig(config("idle" to BundleMascotSlot(2, 3)), 192, 384))
        val hungry = MascotAtlasMath.frameFor(layout, MascotAnimSlot.HUNGRY, null, 0, 1)
        assertEquals(2 * 48, hungry.srcOffset.y) // drawn from the idle row
    }

    @Test
    fun declared_noSlotsUsesDefaultRows() {
        val layout = valid(MascotAtlasValidator.layoutFromConfig(config(), 192, 384))
        MascotAnimSlot.entries.forEach { assertEquals(MascotSlotLayout(it.defaultRow, 4), layout.slots[it]) }
    }

    @Test
    fun toConfig_roundTripsThroughLayoutFromConfig() {
        val layout = valid(MascotAtlasValidator.inferLayout(192, 384))
        val again = valid(MascotAtlasValidator.layoutFromConfig(MascotAtlasValidator.toConfig(layout), 192, 384))
        assertEquals(layout, again)
    }

    // ── Custom dialogue sanitization ────────────────────────────────────────

    @Test
    fun sanitizeMascotDialogue_stripsBlanksControlChars_clampsLength_andCapsPoolSize() {
        assertNull(sanitizeMascotDialogue(null))
        assertNull(
            sanitizeMascotDialogue(
                BundleMascotDialogue(
                    happy = listOf("", "   ", "\r\n\t", "\u0000\u0007")
                )
            )
        )

        val overlong = "a".repeat(64)
        val many = (1..25).map { "line $it" }
        val sanitized = sanitizeMascotDialogue(
            BundleMascotDialogue(
                happy = listOf("  hello\r\nworld\t!  ", " \n ", "ribbit\u0000~", overlong),
                fed = many
            )
        )
        assertNotNull(sanitized!!)
        assertEquals(3, sanitized.happy.size)
        assertEquals("hello world !", sanitized.happy[0])
        assertEquals("ribbit~", sanitized.happy[1])
        assertEquals(48, sanitized.happy[2].length)
        assertEquals("a".repeat(48), sanitized.happy[2])

        assertEquals(20, sanitized.fed.size)
        assertEquals((1..20).map { "line $it" }, sanitized.fed)
        assertTrue(sanitized.content.isEmpty())
        assertTrue(sanitized.hungry.isEmpty())
        assertTrue(sanitized.sulky.isEmpty())
        assertTrue(sanitized.sleepy.isEmpty())
        assertTrue(sanitized.noTreats.isEmpty())
    }

    // ── PNG header & byte limit ─────────────────────────────────────────────

    @Test
    fun readPngSize_readsIhdr_andRejectsNonPng() {
        assertEquals(192 to 384, MascotAtlasValidator.readPngSize(fakePng(192, 384)))
        assertNull(MascotAtlasValidator.readPngSize("not a png at all, just some text".toByteArray()))
        assertNull(MascotAtlasValidator.readPngSize(fakePng(192, 384).copyOf(20)))
    }

    @Test
    fun copyAtMost_rejectsOversizeInput() {
        val limit = MascotAtlasValidator.MAX_ATLAS_BYTES
        val out = ByteArrayOutputStream()
        assertTrue(MascotAtlasValidator.copyAtMost(ByteArrayInputStream(ByteArray(limit.toInt())), out, limit))
        assertEquals(limit.toInt(), out.size())
        assertFalse(MascotAtlasValidator.copyAtMost(ByteArrayInputStream(ByteArray(limit.toInt() + 1)), ByteArrayOutputStream(), limit))
    }

    @Test
    fun copyAtMost_countsRealBytes_notADeclaredSize() {
        // A stream that "declares" it is tiny (available() = 1) but really holds 2 MB
        val liar = object : InputStream() {
            var left = 2 * 1024 * 1024
            override fun available() = 1
            override fun read(): Int = if (left-- > 0) 0 else -1
        }
        assertFalse(MascotAtlasValidator.copyAtMost(liar, ByteArrayOutputStream(), MascotAtlasValidator.MAX_ATLAS_BYTES))
    }
}
