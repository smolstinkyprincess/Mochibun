package one.crinklecod.mochibun

import android.provider.Settings
import android.view.KeyEvent
import one.crinklecod.mochibun.core.sound.SoundEffectManager
import one.crinklecod.mochibun.core.system.TorchManager
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.theme.isThemePrefKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** v2.1.0 Settings & Polish, batch 1: new / changed preferences and their helpers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsPreferencesTest {

    @Test
    fun t9AutoCommitDelayRangeAndStep() {
        assertEquals(700, PreferencesManager.DEFAULT_IME_IDLE_COMMIT_MS)
        assertEquals(300, PreferencesManager.MIN_IME_IDLE_COMMIT_MS)
        assertEquals(1500, PreferencesManager.MAX_IME_IDLE_COMMIT_MS)
        assertEquals(100, PreferencesManager.STEP_IME_IDLE_COMMIT_MS)

        assertEquals(800, PreferencesManager.stepImeIdleCommitMs(700, 1))
        assertEquals(600, PreferencesManager.stepImeIdleCommitMs(700, -1))
        assertEquals(1500, PreferencesManager.stepImeIdleCommitMs(1500, 1))
        assertEquals(300, PreferencesManager.stepImeIdleCommitMs(300, -1))
    }

    @Test
    fun t9AutoCommitDelayPersistsAndClamps() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.setImeIdleCommitMs(context, 900)
        assertEquals(900, PreferencesManager.getImeIdleCommitMs(context))
        PreferencesManager.setImeIdleCommitMs(context, 5000)
        assertEquals(1500, PreferencesManager.getImeIdleCommitMs(context))
        // Values saved under the old 500–3000 ms range are clamped on read
        PreferencesManager.getPrefs(context).edit()
            .putInt(PreferencesManager.KEY_IME_IDLE_COMMIT_MS, 3000).commit()
        assertEquals(1500, PreferencesManager.getImeIdleCommitMs(context))
    }

    @Test
    fun softKeyNavPrefPersistsAndMirrorsToSettingsGlobal() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(PreferencesManager.DEFAULT_SOFT_KEY_NAV_ENABLED)

        PreferencesManager.setSoftKeyNavEnabled(context, false)
        assertFalse(PreferencesManager.isSoftKeyNavEnabled(context))
        assertFalse(PreferencesManager.isSoftKeyNavEnabled.value)
        assertEquals(
            0,
            Settings.Global.getInt(context.contentResolver, PreferencesManager.GLOBAL_SOFT_KEY_NAV_ENABLED, 1)
        )

        PreferencesManager.setSoftKeyNavEnabled(context, true)
        assertTrue(PreferencesManager.isSoftKeyNavEnabled(context))
        assertEquals(
            1,
            Settings.Global.getInt(context.contentResolver, PreferencesManager.GLOBAL_SOFT_KEY_NAV_ENABLED, 0)
        )
    }

    @Test
    fun kaomojiTriggerIsAlwaysPound() {
        assertTrue(PreferencesManager.matchesKaomojiTrigger(KeyEvent.KEYCODE_POUND))
        assertFalse(PreferencesManager.matchesKaomojiTrigger(KeyEvent.KEYCODE_STAR))
        assertFalse(PreferencesManager.matchesKaomojiTrigger(KeyEvent.KEYCODE_0))
    }

    @Test
    fun soundProfileCyclesEveryThemeAndWraps() {
        val builtInIds = SoundEffectManager.SoundThemes
            .filterNot { it.id == SoundEffectManager.BUNDLE_THEME_ID }
            .map { it.id }
        var id = builtInIds.first()
        val visitedBuiltIn = mutableListOf(id)
        repeat(builtInIds.size - 1) {
            id = SoundEffectManager.nextThemeId(id, 1)
            visitedBuiltIn += id
        }
        assertEquals(builtInIds, visitedBuiltIn)
        assertEquals(builtInIds.first(), SoundEffectManager.nextThemeId(builtInIds.last(), 1))
        assertEquals(builtInIds.last(), SoundEffectManager.nextThemeId(builtInIds.first(), -1))
        // Unknown / legacy ids recover to the first theme instead of sticking
        assertEquals(builtInIds.first(), SoundEffectManager.nextThemeId("bogus", 1))

        val allIds = SoundEffectManager.SoundThemes.map { it.id }
        var bundleCycleId = allIds.first()
        val visitedAll = mutableListOf(bundleCycleId)
        repeat(allIds.size - 1) {
            bundleCycleId = SoundEffectManager.nextThemeId(bundleCycleId, 1, includeBundle = true)
            visitedAll += bundleCycleId
        }
        assertEquals(allIds, visitedAll)
        assertEquals(allIds.first(), SoundEffectManager.nextThemeId(allIds.last(), 1, includeBundle = true))
        assertEquals(allIds.last(), SoundEffectManager.nextThemeId(allIds.first(), -1, includeBundle = true))
    }

    @Test
    fun soundProfileLabelsMatchThemes() {
        assertEquals("Retro Keitai", SoundEffectManager.themeName("retro"))
        assertEquals("Water Drop", SoundEffectManager.themeName("water"))
        assertEquals("Muted / Off", SoundEffectManager.themeName("off"))
        assertEquals("Kawaii Pop", SoundEffectManager.themeName("bogus"))
    }

    @Test
    fun soundProfileSelectionPersists() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.setKeypadSoundMode(context, "pager")
        assertEquals("pager", PreferencesManager.getKeypadSoundMode(context))
        assertEquals("pager", PreferencesManager.keypadSoundMode.value)
        PreferencesManager.setKeypadSoundMode(context, PreferencesManager.DEFAULT_KEYPAD_SOUND_MODE)
    }

    @Test
    fun themePrefKeysTriggerImeRefresh() {
        assertTrue(isThemePrefKey(PreferencesManager.KEY_THEME_PRESET))
        assertTrue(isThemePrefKey(PreferencesManager.KEY_THEME_BG_COLOR))
        assertTrue(isThemePrefKey("theme_card_roundness"))
        assertTrue(isThemePrefKey("preset_custom_abc_bg"))
        assertTrue(isThemePrefKey("custom_preset_ids"))
        assertTrue(isThemePrefKey(PreferencesManager.KEY_THEME_FONT_FAMILY))
        assertFalse(isThemePrefKey(PreferencesManager.KEY_KEYPAD_SOUND_MODE))
        assertFalse(isThemePrefKey(PreferencesManager.KEY_IME_IDLE_COMMIT_MS))
        assertFalse(isThemePrefKey(PreferencesManager.KEY_IME_LAST_MODE))
    }

    @Test
    fun imeLastModeDefaultsToMultitapAndPersists() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(PreferencesManager.DEFAULT_IME_LAST_MODE, PreferencesManager.getImeLastMode(context))
        assertEquals(PreferencesManager.IME_LAST_MODE_MULTITAP, PreferencesManager.getImeLastMode(context))

        PreferencesManager.setImeLastMode(context, PreferencesManager.IME_LAST_MODE_T9)
        assertEquals(PreferencesManager.IME_LAST_MODE_T9, PreferencesManager.getImeLastMode(context))

        PreferencesManager.setImeLastMode(context, PreferencesManager.IME_LAST_MODE_MULTITAP)
        assertEquals(PreferencesManager.IME_LAST_MODE_MULTITAP, PreferencesManager.getImeLastMode(context))

        // Unknown / legacy values (e.g. "123") normalize cleanly back to multi-tap
        PreferencesManager.setImeLastMode(context, "T9")
        assertEquals(PreferencesManager.IME_LAST_MODE_T9, PreferencesManager.getImeLastMode(context))
        PreferencesManager.setImeLastMode(context, "123")
        assertEquals(PreferencesManager.IME_LAST_MODE_MULTITAP, PreferencesManager.getImeLastMode(context))
    }

    @Test
    fun flashlightToastsAreKawaii() {
        assertEquals("♡ Flashlight On ♡", TorchManager.toastText(true))
        assertEquals("♡ Flashlight Off ♡", TorchManager.toastText(false))
    }
}
