package one.crinklecod.mochibun

import one.crinklecod.mochibun.core.system.LidLedManager
import one.crinklecod.mochibun.core.system.LidLedMode
import one.crinklecod.mochibun.data.model.AppLedRuleMode
import one.crinklecod.mochibun.data.model.LidAppLedRule
import one.crinklecod.mochibun.data.model.LidAppLedRuleCodec
import one.crinklecod.mochibun.data.model.LidLedAnimation
import one.crinklecod.mochibun.data.model.LidLedStep
import one.crinklecod.mochibun.data.model.MochibunNotificationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-app lid LED rules: codec, MUTED filtering, CUSTOM_COLOR / CUSTOM_ANIMATION resolution. */
class LidAppLedRuleTest {

    private val globalHex = "0xff1493"

    private val wave = LidLedAnimation(
        id = "wave",
        name = "Wave",
        steps = listOf(LidLedStep(listOf("#00BFFF", "#000000", "#000000", "#000000", "#000000")))
    )

    private fun item(pkg: String, postTime: Long, call: Boolean = false) = MochibunNotificationItem(
        key = "$pkg|$postTime",
        packageName = pkg,
        appName = pkg,
        title = "t",
        text = "x",
        postTime = postTime,
        contentIntent = null,
        appIcon = null,
        isIncomingCall = call
    )

    private fun rules(vararg r: LidAppLedRule) = r.associateBy { it.packageName }

    // --- Codec ---

    @Test
    fun codecRoundTripsAndDropsUnusableRules() {
        val encoded = LidAppLedRuleCodec.encode(
            listOf(
                LidAppLedRule("a.color", AppLedRuleMode.CUSTOM_COLOR, colorHex = "ff7f50", animationId = "ignored"),
                LidAppLedRule("b.mute", AppLedRuleMode.MUTED),
                LidAppLedRule("c.default", AppLedRuleMode.DEFAULT),
                LidAppLedRule("d.anim", AppLedRuleMode.CUSTOM_ANIMATION, animationId = " ")
            )
        )
        val decoded = LidAppLedRuleCodec.decode(encoded)
        assertEquals(setOf("a.color", "b.mute"), decoded.keys)
        assertEquals("#FF7F50", decoded.getValue("a.color").colorHex)
        assertNull(decoded.getValue("a.color").animationId)
    }

    @Test
    fun codecToleratesGarbage() {
        assertTrue(LidAppLedRuleCodec.decode("not json").isEmpty())
        assertTrue(LidAppLedRuleCodec.decode(null).isEmpty())
        // Unknown extra keys are ignored
        val text = """[{"packageName":"x.y","mode":"MUTED","extra":1}]"""
        assertEquals(AppLedRuleMode.MUTED, LidAppLedRuleCodec.decode(text)["x.y"]?.mode)
    }

    // --- MUTED filtering & priority ---

    @Test
    fun mutedAppsDoNotTriggerTheNotificationLight() {
        val items = listOf(item("spam", 100), item("chat", 50))
        val eligible = LidLedManager.eligibleNotifications(items, rules(LidAppLedRule("spam", AppLedRuleMode.MUTED)))
        assertEquals(listOf("chat"), eligible.map { it.packageName })

        val onlyMuted = LidLedManager.eligibleNotifications(listOf(item("spam", 100)), rules(LidAppLedRule("spam", AppLedRuleMode.MUTED)))
        assertTrue(onlyMuted.isEmpty())
        // Only muted notifications left: the ladder falls through to charging / off
        assertEquals(LidLedMode.CHARGING, LidLedManager.resolveMode(true, true, false, onlyMuted.isNotEmpty(), true))
        assertEquals(LidLedMode.OFF, LidLedManager.resolveMode(true, true, false, onlyMuted.isNotEmpty(), false))
    }

    @Test
    fun incomingCallsAreNeverMuted() {
        val muted = rules(LidAppLedRule("dialer", AppLedRuleMode.MUTED))
        val items = listOf(item("dialer", 10, call = true))
        assertEquals(1, LidLedManager.eligibleNotifications(items, muted).size)
        assertEquals(
            LidLedMode.CALL,
            LidLedManager.resolveMode(true, true, items.any { it.isIncomingCall }, true, true)
        )
    }

    @Test
    fun newestEligibleNotificationPicksTheRule() {
        val r = rules(
            LidAppLedRule("spam", AppLedRuleMode.MUTED),
            LidAppLedRule("chat", AppLedRuleMode.CUSTOM_COLOR, colorHex = "#00FF7F")
        )
        // spam is newest, but muted: chat (older) drives the light
        val items = listOf(item("chat", 100), item("mail", 50), item("spam", 300))
        val newest = LidLedManager.eligibleNotifications(items, r).maxByOrNull { it.postTime }
        assertEquals("chat", newest?.packageName)
        val look = LidLedManager.resolveNotificationLook(r[newest?.packageName], globalHex) { null }
        assertEquals(AppLedRuleMode.CUSTOM_COLOR, look.kind)
        assertEquals("0x00ff7f", look.hex)
    }

    // --- Resolution ---

    @Test
    fun noRuleOrDefaultUsesGlobalBehavior() {
        val none = LidLedManager.resolveNotificationLook(null, globalHex) { wave }
        assertEquals(AppLedRuleMode.DEFAULT, none.kind)
        assertEquals(globalHex, none.hex)
        assertNull(none.animation)
        val def = LidLedManager.resolveNotificationLook(LidAppLedRule("x", AppLedRuleMode.DEFAULT), globalHex) { wave }
        assertEquals(none, def)
    }

    @Test
    fun customAnimationResolvesAndFallsBackWhenDeleted() {
        val rule = LidAppLedRule("game", AppLedRuleMode.CUSTOM_ANIMATION, animationId = "wave")
        val found = LidLedManager.resolveNotificationLook(rule, globalHex) { id -> if (id == "wave") wave else null }
        assertEquals(AppLedRuleMode.CUSTOM_ANIMATION, found.kind)
        assertSame(wave, found.animation)

        val deleted = LidLedManager.resolveNotificationLook(rule, globalHex) { null }
        assertEquals(LidLedManager.NotificationLook.global(globalHex), deleted)

        val empty = LidLedManager.resolveNotificationLook(rule, globalHex) { wave.copy(steps = emptyList()) }
        assertEquals(AppLedRuleMode.DEFAULT, empty.kind)
    }

    @Test
    fun differentRulesHaveDifferentSignaturesAndRetrigger() {
        val pink = LidLedManager.resolveNotificationLook(
            LidAppLedRule("a", AppLedRuleMode.CUSTOM_COLOR, colorHex = "#FF1493"), globalHex
        ) { null }
        val blue = LidLedManager.resolveNotificationLook(
            LidAppLedRule("b", AppLedRuleMode.CUSTOM_COLOR, colorHex = "#00BFFF"), globalHex
        ) { null }
        val anim = LidLedManager.resolveNotificationLook(
            LidAppLedRule("c", AppLedRuleMode.CUSTOM_ANIMATION, animationId = "wave"), globalHex
        ) { wave }
        val global = LidLedManager.NotificationLook.global(globalHex)
        // A custom color equal to the global color is still a different program (shimmer vs slot animation)
        assertNotEquals(global.signature, pink.signature)
        assertNotEquals(pink.signature, blue.signature)
        assertNotEquals(global.signature, anim.signature)

        // App A's pink shimmer is running; app B's blue post inside the debounce window must not be skipped
        LidLedManager.setMockAppliedState(LidLedMode.NOTIFICATION, pink.signature, 1_000L)
        LidLedManager.setMockShimmerState(5_000L, 65_000L, 1_000L)
        assertFalse(LidLedManager.shouldSkipShimmer(LidLedMode.NOTIFICATION, blue.signature, 1_050L, now = 5_200L))
        // Same app/look during the debounce window still skips
        assertTrue(LidLedManager.shouldSkipShimmer(LidLedMode.NOTIFICATION, pink.signature, 1_050L, now = 5_200L))
    }
}
