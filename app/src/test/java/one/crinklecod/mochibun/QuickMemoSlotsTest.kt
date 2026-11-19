package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.model.AppLedRuleMode
import one.crinklecod.mochibun.data.model.LidAppLedRule
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BackupRestoreManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Quick Memo slots 1..5: persistence, legacy single-memo migration, ticker pick and backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class QuickMemoSlotsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearPrefs() {
        PreferencesManager.getPrefs(context).edit().clear().commit()
        PreferencesManager.reload(context)
    }

    @Test
    fun fiveSlotsPersistAcrossReload() {
        for (slot in 1..5) PreferencesManager.setQuickMemoSlotText(context, slot, "memo $slot\n")
        PreferencesManager.setPinnedQuickMemoSlot(context, 4)
        PreferencesManager.setLastOpenedQuickMemoSlot(context, 3)

        PreferencesManager.reload(context)
        assertEquals((1..5).map { "memo $it" }, PreferencesManager.quickMemoSlots.value)
        assertEquals(4, PreferencesManager.pinnedQuickMemoSlot.value)
        assertEquals(3, PreferencesManager.lastOpenedQuickMemoSlot.value)
        assertEquals("memo 5", PreferencesManager.getQuickMemoSlotText(5))
    }

    @Test
    fun slotsAreCappedAndSlotIndexesClamped() {
        PreferencesManager.setQuickMemoSlotText(context, 2, "b".repeat(300))
        assertEquals(PreferencesManager.QUICK_MEMO_MAX_CHARS, PreferencesManager.getQuickMemoSlotText(2).length)

        PreferencesManager.setPinnedQuickMemoSlot(context, 9)
        assertEquals(5, PreferencesManager.pinnedQuickMemoSlot.value)
        PreferencesManager.setLastOpenedQuickMemoSlot(context, 0)
        assertEquals(1, PreferencesManager.lastOpenedQuickMemoSlot.value)

        // Out-of-range values written by hand still load clamped
        PreferencesManager.getPrefs(context).edit()
            .putInt(PreferencesManager.KEY_QUICK_MEMO_PINNED_SLOT, 42)
            .commit()
        PreferencesManager.reload(context)
        assertEquals(5, PreferencesManager.pinnedQuickMemoSlot.value)
    }

    @Test
    fun legacySingleMemoMigratesIntoSlotOne() {
        // A pre-slot install only ever wrote the single memo key
        PreferencesManager.getPrefs(context).edit()
            .putString(PreferencesManager.KEY_QUICK_MEMO_TEXT, "old sticky note ♡")
            .commit()
        PreferencesManager.reload(context)

        assertEquals("old sticky note ♡", PreferencesManager.getQuickMemoSlotText(1))
        assertEquals(listOf("old sticky note ♡", "", "", "", ""), PreferencesManager.quickMemoSlots.value)
        assertEquals(1, PreferencesManager.pinnedQuickMemoSlot.value)
        assertEquals(1, PreferencesManager.lastOpenedQuickMemoSlot.value)
        assertEquals(
            "old sticky note ♡",
            PreferencesManager.tickerQuickMemo(PreferencesManager.quickMemoSlots.value, PreferencesManager.pinnedQuickMemoSlot.value)
        )
        // The legacy API keeps reading / writing slot 1
        assertEquals("old sticky note ♡", PreferencesManager.quickMemoText.value)
        PreferencesManager.setQuickMemoText(context, "new")
        assertEquals("new", PreferencesManager.getQuickMemoSlotText(1))
    }

    @Test
    fun tickerUsesPinnedSlotThenFirstNonBlankThenNothing() {
        val slots = listOf("", "second", "  ", "fourth", "")
        assertEquals("fourth", PreferencesManager.tickerQuickMemo(slots, 4))
        assertEquals("second", PreferencesManager.tickerQuickMemo(slots, 3)) // pinned blank
        assertEquals("second", PreferencesManager.tickerQuickMemo(slots, 1))
        assertNull(PreferencesManager.tickerQuickMemo(List(5) { "" }, 1))
    }

    @Test
    fun backupExportsAndRestoresAllSlotsPinnedSlotAndLedRules() {
        for (slot in 1..5) PreferencesManager.setQuickMemoSlotText(context, slot, "slot $slot")
        PreferencesManager.setPinnedQuickMemoSlot(context, 5)
        PreferencesManager.setLastOpenedQuickMemoSlot(context, 2)
        PreferencesManager.setLidAppLedRule(context, LidAppLedRule("com.example.chat", AppLedRuleMode.CUSTOM_COLOR, colorHex = "#00bfff"))
        PreferencesManager.setLidAppLedRule(context, LidAppLedRule("com.example.spam", AppLedRuleMode.MUTED))
        PreferencesManager.setLidAppLedRule(context, LidAppLedRule("com.example.game", AppLedRuleMode.CUSTOM_ANIMATION, animationId = "builtin_wave"))

        val json = BackupRestoreManager.createBackupJson(context)
        val memo = JSONObject(json).getJSONObject(BackupRestoreManager.KEY_QUICK_MEMO)
        assertEquals(5, memo.getJSONArray("slots").length())
        assertEquals("slot 3", memo.getJSONArray("slots").getString(2))
        assertEquals(5, memo.getInt("pinned_slot"))
        assertEquals(3, JSONObject(json).getJSONArray(BackupRestoreManager.KEY_LID_LED_APP_RULES).length())

        // Wipe, then restore
        PreferencesManager.getPrefs(context).edit().clear().commit()
        PreferencesManager.reload(context)
        assertEquals(List(5) { "" }, PreferencesManager.quickMemoSlots.value)
        assertTrue(PreferencesManager.getLidAppLedRules().isEmpty())

        BackupRestoreManager.restoreBackupJson(context, json).getOrThrow()
        assertEquals((1..5).map { "slot $it" }, PreferencesManager.quickMemoSlots.value)
        assertEquals(5, PreferencesManager.pinnedQuickMemoSlot.value)
        assertEquals(2, PreferencesManager.lastOpenedQuickMemoSlot.value)
        val rules = PreferencesManager.getLidAppLedRules()
        assertEquals("#00BFFF", rules.getValue("com.example.chat").colorHex)
        assertEquals(AppLedRuleMode.MUTED, rules.getValue("com.example.spam").mode)
        assertEquals("builtin_wave", rules.getValue("com.example.game").animationId)
    }

    @Test
    fun backupWithoutMemoOrRulesKeepsCurrentValues() {
        PreferencesManager.setQuickMemoSlotText(context, 3, "keep me")
        PreferencesManager.setLidAppLedRule(context, LidAppLedRule("com.example.spam", AppLedRuleMode.MUTED))
        val json = JSONObject(BackupRestoreManager.createBackupJson(context)).apply {
            remove(BackupRestoreManager.KEY_QUICK_MEMO)
            remove(BackupRestoreManager.KEY_LID_LED_APP_RULES)
        }
        BackupRestoreManager.restoreBackupJson(context, json.toString()).getOrThrow()
        assertEquals("keep me", PreferencesManager.getQuickMemoSlotText(3))
        assertEquals(AppLedRuleMode.MUTED, PreferencesManager.getLidAppLedRules()["com.example.spam"]?.mode)
    }

    @Test
    fun backupWithBadRuleModeIsRejected() {
        val json = JSONObject(BackupRestoreManager.createBackupJson(context))
        json.put(
            BackupRestoreManager.KEY_LID_LED_APP_RULES,
            org.json.JSONArray().put(JSONObject().put("packageName", "com.x").put("mode", "DISCO"))
        )
        assertTrue(BackupRestoreManager.previewBackupJson(json.toString()).isFailure)
    }
}
