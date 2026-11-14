package one.crinklecod.mochibun

import android.provider.Settings
import android.view.KeyEvent
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.prefs.CursorPreferences
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.service.MochibunAccessibilityService.KeyRouting
import one.crinklecod.mochibun.service.MochibunAccessibilityService.KeyRouting.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** v2.2.1: configurable cursor toggle key (pref, Settings.Global mirror, shared key matching). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CursorToggleKeyTest {

    /** What MochibunKeyHook sees: an unset global means F4, so F4 itself needn't be written. */
    private fun globalToggleKey(): Int = Settings.Global.getInt(
        RuntimeEnvironment.getApplication().contentResolver,
        PreferencesManager.GLOBAL_CURSOR_TOGGLE_KEYCODE,
        KeyEvent.KEYCODE_F4
    )

    @Test
    fun defaultIsSideKeyF4() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(KeyEvent.KEYCODE_F4, CursorPreferences.getToggleKeyCode(context))
        assertEquals("Side Key (F4)", CursorPreferences.toggleKeyLabel(KeyEvent.KEYCODE_F4))
    }

    @Test
    fun setToggleKeyPersistsAndMirrorsToGlobal() {
        val context = RuntimeEnvironment.getApplication()
        CursorPreferences.setToggleKeyCode(context, KeyEvent.KEYCODE_STAR)
        assertEquals(KeyEvent.KEYCODE_STAR, CursorPreferences.getToggleKeyCode(context))
        assertEquals(KeyEvent.KEYCODE_STAR, globalToggleKey())

        CursorPreferences.setToggleKeyCode(context, KeyEvent.KEYCODE_CAMERA)
        assertEquals(KeyEvent.KEYCODE_CAMERA, globalToggleKey())
    }

    @Test
    fun startupSyncRepairsAStaleGlobal() {
        val context = RuntimeEnvironment.getApplication()
        CursorPreferences.setToggleKeyCode(context, KeyEvent.KEYCODE_MENU)
        Settings.Global.putInt(
            context.contentResolver,
            PreferencesManager.GLOBAL_CURSOR_TOGGLE_KEYCODE,
            KeyEvent.KEYCODE_F4
        )
        CursorPreferences.syncToggleKeyToGlobal(context)
        assertEquals(KeyEvent.KEYCODE_MENU, globalToggleKey())
    }

    @Test
    fun unsupportedKeyFallsBackToF4() {
        val context = RuntimeEnvironment.getApplication()
        CursorPreferences.setToggleKeyCode(context, KeyEvent.KEYCODE_VOLUME_UP)
        assertEquals(KeyEvent.KEYCODE_F4, CursorPreferences.getToggleKeyCode(context))
        assertEquals(KeyEvent.KEYCODE_F4, globalToggleKey())
    }

    @Test
    fun pickerCyclesThroughAllOptionsAndWraps() {
        val codes = CursorPreferences.TOGGLE_KEY_OPTIONS.map { it.keyCode }
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_F4,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SEARCH,
                KeyEvent.KEYCODE_CAMERA,
                KeyEvent.KEYCODE_STAR
            ),
            codes
        )
        assertEquals(KeyEvent.KEYCODE_MENU, CursorPreferences.stepToggleKeyCode(KeyEvent.KEYCODE_F4, 1))
        assertEquals(KeyEvent.KEYCODE_STAR, CursorPreferences.stepToggleKeyCode(KeyEvent.KEYCODE_F4, -1))
        assertEquals(KeyEvent.KEYCODE_F4, CursorPreferences.stepToggleKeyCode(KeyEvent.KEYCODE_STAR, 1))
    }

    @Test
    fun nonF4ToggleKeyMatches() {
        val menu = KeyEvent.KEYCODE_MENU
        assertTrue(MochibunSystemActions.isCursorToggleKey(menu, 0, menu, inputActive = false))
        assertTrue(MochibunSystemActions.isCursorToggleKey(menu, 0, menu, inputActive = true))
        assertFalse(MochibunSystemActions.isCursorToggleKey(KeyEvent.KEYCODE_SEARCH, 0, menu, false))
        assertEquals(Route.TOGGLE_CURSOR, KeyRouting.route(menu, 0, menu, false, false))
        assertEquals(Route.PASS, KeyRouting.route(KeyEvent.KEYCODE_CAMERA, 0, menu, false, false))
    }

    @Test
    fun sideKeyF4AlwaysToggles() {
        val camera = KeyEvent.KEYCODE_CAMERA
        assertTrue(MochibunSystemActions.isCursorToggleKey(KeyEvent.KEYCODE_F4, 0, camera, false))
        // AT-M120 keylayouts that report the side key only by scan code
        assertTrue(MochibunSystemActions.isCursorToggleKey(KeyEvent.KEYCODE_UNKNOWN, 62, camera, true))
        assertFalse(MochibunSystemActions.isCursorToggleKey(KeyEvent.KEYCODE_UNKNOWN, 0, KeyEvent.KEYCODE_UNKNOWN, false))
    }

    @Test
    fun starTogglesOnlyWhenNotTyping() {
        val star = KeyEvent.KEYCODE_STAR
        assertTrue(MochibunSystemActions.isCursorToggleKey(star, 0, star, inputActive = false))
        assertFalse(MochibunSystemActions.isCursorToggleKey(star, 0, star, inputActive = true))
        assertEquals(Route.TOGGLE_CURSOR, KeyRouting.route(star, 0, star, false, false))
        assertEquals(Route.PASS, KeyRouting.route(star, 0, star, false, true))
        // `*` isn't a toggle unless chosen
        assertFalse(MochibunSystemActions.isCursorToggleKey(star, 0, KeyEvent.KEYCODE_F4, false))
    }
}
