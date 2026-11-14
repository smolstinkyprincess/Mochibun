package one.crinklecod.mochibun

import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.data.model.LID_MAX_STEPS
import one.crinklecod.mochibun.data.model.LID_MAX_STEP_MS
import one.crinklecod.mochibun.data.model.LID_OFF_HEX
import one.crinklecod.mochibun.data.model.LidLedAnimation
import one.crinklecod.mochibun.data.model.LidLedAnimationCodec
import one.crinklecod.mochibun.data.model.LidLedStep
import one.crinklecod.mochibun.data.model.LidLedSwatch
import one.crinklecod.mochibun.data.model.normalizeLidHex
import one.crinklecod.mochibun.data.repository.LidLedAnimationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LidLedAnimationTest {

    private val helper = "/fake/lidled"
    private val pink = LidLedSwatch.HOT_PINK.hex
    private val violet = LidLedSwatch.VIOLET.hex
    private val off = LID_OFF_HEX

    private val sakuraYaml = """
        name: "Sakura Chase ♡"
        author: "emii"
        step_ms: 500
        loop: true
        steps:
          - windows: ["#FF1493", "#000000", "#000000", "#000000", "#000000"]
          - windows: ["#000000", "#FF1493", "#000000", "#000000", "#000000"]
          - windows: ["#000000", "#000000", "#FF1493", "#000000", "#000000"]
          - windows: ["#000000", "#000000", "#000000", "#FF1493", "#000000"]
          - windows: ["#000000", "#000000", "#000000", "#000000", "#FF1493"]
    """.trimIndent()

    // ---- Serialization ----

    @Test
    fun decodesDocumentedYamlExample() {
        val anim = LidLedAnimationCodec.decode(sakuraYaml).getOrThrow()
        assertEquals("Sakura Chase ♡", anim.name)
        assertEquals("emii", anim.author)
        assertEquals(500, anim.stepMs)
        assertTrue(anim.loop)
        assertEquals(5, anim.steps.size)
        assertEquals(listOf(off, off, pink, off, off), anim.steps[2].windows)
        assertEquals("5 steps • 2.5s", anim.summary())
    }

    @Test
    fun decodesJsonWithEitherStepMsSpelling() {
        val snake = """{"name":"J","step_ms":250,"steps":[{"windows":["#ff1493","#000000","#000000","#000000","#000000"]}]}"""
        val camel = """{"name":"J","stepMs":250,"extra":1,"steps":[{"windows":["#ff1493","#000000","#000000","#000000","#000000"]}]}"""
        for (text in listOf(snake, camel)) {
            val anim = LidLedAnimationCodec.decode(text).getOrThrow()
            assertEquals(250, anim.stepMs)
            assertEquals(pink, anim.steps[0].windows[0]) // normalized to upper case
            assertEquals("Mochibun", anim.author) // default
        }
    }

    @Test
    fun lenientYamlIsSanitized() {
        val manySteps = (1..20).joinToString("\n") { "  - windows: [\"#9D00FF\"]" }
        val yaml = "name: \"  \"\nstep_ms: 99999\nunknown_key: hi\nsteps:\n$manySteps\n  - windows: [\"nope\"]"
        val anim = LidLedAnimationCodec.decode(yaml).getOrThrow()
        assertEquals("Untitled ♡", anim.name)
        assertEquals(LID_MAX_STEP_MS, anim.stepMs)
        assertEquals(LID_MAX_STEPS, anim.steps.size)
        assertEquals(listOf(violet, off, off, off, off), anim.steps[0].windows) // padded to 5
    }

    @Test
    fun rejectsAnimationsWithoutSteps() {
        assertTrue(LidLedAnimationCodec.decode("name: \"Empty\"\nsteps: []").isFailure)
        assertTrue(LidLedAnimationCodec.decode("this is: [not valid").isFailure)
    }

    @Test
    fun yamlAndJsonRoundTrip() {
        val original = LidLedAnimation(
            id = "custom_x_1",
            name = "Quote \"me\" \\ ♡",
            author = "emii",
            stepMs = 300,
            loop = false,
            steps = listOf(LidLedStep(listOf(pink, off, violet, off, pink)), LidLedAnimation.blankStep())
        )
        val yaml = LidLedAnimationCodec.encodeYaml(original)
        assertTrue(yaml.contains("  - windows: [\"#FF1493\", \"#000000\", \"#9D00FF\", \"#000000\", \"#FF1493\"]"))
        assertEquals(original, LidLedAnimationCodec.decode(yaml).getOrThrow())
        assertEquals(original, LidLedAnimationCodec.decode(LidLedAnimationCodec.encodeJson(original)).getOrThrow())
    }

    @Test
    fun hexNormalization() {
        assertEquals("#FF1493", normalizeLidHex("0xff1493"))
        assertEquals("#FF1493", normalizeLidHex("ff1493"))
        assertEquals("#FF1493", normalizeLidHex("#80FF1493"))
        assertEquals(off, normalizeLidHex("#12"))
        assertEquals(off, normalizeLidHex("#GGGGGG"))
    }

    @Test
    fun swatchesAreTheSevenSaturatedColors() {
        assertEquals(
            listOf("#000000", "#FF1493", "#9D00FF", "#00BFFF", "#00FF7F", "#FF5E00", "#FFD000"),
            LidLedSwatch.entries.map { it.hex }
        )
        assertEquals(LidLedSwatch.OFF, LidLedSwatch.LEMON_GOLD.next())
        assertEquals(LidLedSwatch.TANGERINE, LidLedSwatch.fromHex("#ff5e00"))
    }

    @Test
    fun builtInsAreValidAndSingleColorPerStep() {
        val builtIns = LidLedAnimationRepository.BUILT_INS
        assertTrue(builtIns.size in 4..5)
        assertEquals(builtIns.size, builtIns.map { it.id }.toSet().size)
        for (anim in builtIns) {
            assertTrue(LidLedAnimationRepository.isBuiltIn(anim.id))
            assertEquals(anim, anim.sanitized())
            for (step in anim.steps) {
                // The lid helper shows one color per frame, so presets must look the same on the lid
                assertTrue(anim.name, step.windows.filter { it != off }.distinct().size <= 1)
            }
        }
        // Only .mochibunled opens from a file manager; theme / other .yaml files stay with their own apps
        assertTrue(LidLedAnimationRepository.isAnimationFileName("wave.MochibunLED"))
        assertFalse(LidLedAnimationRepository.isAnimationFileName("wave.yaml"))
        assertFalse(LidLedAnimationRepository.isAnimationFileName("sweet_sakura.yml"))
        assertFalse(LidLedAnimationRepository.isAnimationFileName("wave.json"))
        assertFalse(LidLedAnimationRepository.isAnimationFileName("setup.mochibun"))
    }

    // ---- LidLedManager step playback ----

    @Test
    fun stepFrameDerivesMaskAndDominantColor() {
        assertEquals(
            LidLedManager.StepFrame("10101", pink),
            LidLedManager.stepFrame(LidLedStep(listOf(pink, off, pink, off, pink)))
        )
        // Mixed colors: the most used wins
        assertEquals(
            LidLedManager.StepFrame("11011", violet),
            LidLedManager.stepFrame(LidLedStep(listOf(pink, violet, off, violet, violet)))
        )
        // Tie: leftmost lit window wins
        assertEquals("#FF1493", LidLedManager.stepFrame(LidLedStep(listOf(pink, violet, off, off, off))).hex)
        assertEquals(LidLedManager.StepFrame("00000", null), LidLedManager.stepFrame(LidLedAnimation.blankStep()))
    }

    @Test
    fun frameArgsUseMaskAndHelperHex() {
        assertEquals("10101:0xff1493", LidLedManager.frameArg(LidLedManager.StepFrame("10101", pink)))
        assertEquals("00000:0x000000", LidLedManager.frameArg(LidLedManager.StepFrame("00000", null)))
    }

    @Test
    fun wholeAnimationIsOneFramesDaemon() {
        val anim = LidLedAnimationCodec.decode(sakuraYaml).getOrThrow()
        assertEquals(
            "'$helper' frames 500 2 off " +
                "10000:0xff1493 01000:0xff1493 00100:0xff1493 00010:0xff1493 00001:0xff1493",
            LidLedManager.buildFramesCommand(anim, loops = 2, settle = false, helperPath = helper)
        )
        // Notification / charging rest on the last frame
        assertTrue(LidLedManager.buildFramesCommand(anim, 24, settle = true, helperPath = helper).contains(" frames 500 24 last "))
        // Loops are capped like lidled.c
        assertTrue(LidLedManager.buildFramesCommand(anim, 999, settle = true, helperPath = helper).contains(" frames 500 120 last "))
    }

    @Test
    fun programResetsChipWithExistingOffThenPlays() {
        val anim = LidLedAnimationCodec.decode(sakuraYaml).getOrThrow()
        val program = LidLedManager.buildStepAnimationCommand(anim, 1, settle = false, helperPath = helper)
        assertEquals(
            "${LidLedManager.buildOffCommand(helper)}; ${LidLedManager.buildFramesCommand(anim, 1, false, helper)}",
            program
        )
        // One helper call per animation, no per-step pattern calls or bursts
        assertFalse(program.contains(" pattern "))
        assertFalse(program.contains("while"))
    }

    @Test
    fun restingFrameIsTheLastStep() {
        val anim = LidLedAnimation(
            stepMs = 400,
            steps = listOf(
                LidLedStep(listOf(pink, off, off, off, off)),
                LidLedStep(listOf(violet, off, violet, off, violet))
            )
        )
        assertEquals(LidLedManager.StepFrame("10101", violet), LidLedManager.settleFrame(anim))
        val darkEnd = anim.copy(steps = anim.steps + LidLedAnimation.blankStep())
        assertEquals(LidLedManager.StepFrame("00000", null), LidLedManager.settleFrame(darkEnd))
        assertTrue(LidLedManager.buildFramesCommand(darkEnd, 1, true, helper).endsWith(" 00000:0x000000"))
    }

    @Test
    fun loopCountsForNotificationChargingAndPreview() {
        val anim = LidLedAnimationCodec.decode(sakuraYaml).getOrThrow() // 2.5 s per cycle
        assertEquals(24, LidLedManager.notificationStepLoops(anim)) // ~60 s
        assertEquals(1, LidLedManager.notificationStepLoops(anim.copy(loop = false)))
        assertEquals(2, LidLedManager.previewStepLoops(anim)) // ~5 s
        assertEquals(2, LidLedManager.previewStepLoops(anim.copy(loop = false)))
        assertEquals(1, LidLedManager.previewStepLoops(anim.copy(stepMs = 2_000))) // 10 s cycle: one pass
        assertEquals(5_000L, LidLedManager.stepAnimationDurationMs(anim, loops = 2))
    }
}
