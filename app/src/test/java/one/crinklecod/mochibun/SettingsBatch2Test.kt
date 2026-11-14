package one.crinklecod.mochibun

import android.view.KeyEvent
import one.crinklecod.mochibun.core.system.KeyHookStatus
import one.crinklecod.mochibun.core.system.RootTouchInjector
import one.crinklecod.mochibun.core.system.RootTouchInjector.TouchRoute
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.BackupRestoreManager
import one.crinklecod.mochibun.service.MochibunAccessibilityService
import one.crinklecod.mochibun.service.MochibunAccessibilityService.KeyRouting
import one.crinklecod.mochibun.service.MochibunAccessibilityService.KeyRouting.Route
import one.crinklecod.mochibun.ui.screens.settings.ROOT_PERMISSION_ROWS
import one.crinklecod.mochibun.ui.screens.settings.behaviorRowIds
import one.crinklecod.mochibun.ui.screens.settings.permissionsRowIds
import one.crinklecod.mochibun.ui.screens.settings.settingsJumpNumber
import one.crinklecod.mochibun.ui.screens.settings.showScreenOffResetRow
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** v2.1.0 Settings & Polish, batch 2: Non-Root Mode. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsBatch2Test {

    private val context get() = RuntimeEnvironment.getApplication()

    private val defaultSuProbe = RootTouchInjector.suPresentProbe
    private val defaultGrantProbe = RootTouchInjector.rootGrantedProbe
    private val defaultFallback = RootTouchInjector.gestureFallbackProvider

    private class FakeGestures : RootTouchInjector.GestureFallback {
        val taps = mutableListOf<Pair<Float, Float>>()
        val swipes = mutableListOf<List<Float>>()
        override fun dispatchTap(x: Float, y: Float): Boolean {
            taps += x to y
            return true
        }
        override fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
            swipes += listOf(x1, y1, x2, y2, durationMs.toFloat())
            return true
        }
    }

    @Before
    fun clearPrefs() {
        PreferencesManager.getPrefs(context).edit().clear().commit()
    }

    @After
    fun restoreProbes() {
        RootTouchInjector.suPresentProbe = defaultSuProbe
        RootTouchInjector.rootGrantedProbe = defaultGrantProbe
        RootTouchInjector.gestureFallbackProvider = defaultFallback
        RootTouchInjector.release()
    }

    // ---------------------------------------------------------------- pref_show_root_options

    @Test
    fun showRootOptionsDefaultsFollowSuBinary() {
        assertEquals("pref_show_root_options", PreferencesManager.KEY_SHOW_ROOT_OPTIONS)
        assertFalse(PreferencesManager.defaultShowRootOptions(suPresent = false))
        assertTrue(PreferencesManager.defaultShowRootOptions(suPresent = true))

        // Unset: hidden on an unrooted phone, visible on a rooted one
        assertFalse(PreferencesManager.isShowRootOptions(context, suPresent = false))
        assertTrue(PreferencesManager.isShowRootOptions(context, suPresent = true))

        // Once chosen, the user's choice wins over detection
        PreferencesManager.setShowRootOptions(context, true)
        assertTrue(PreferencesManager.isShowRootOptions(context, suPresent = false))
        assertTrue(PreferencesManager.showRootOptions.value)
        PreferencesManager.setShowRootOptions(context, false)
        assertFalse(PreferencesManager.isShowRootOptions(context, suPresent = true))
        assertFalse(PreferencesManager.showRootOptions.value)
    }

    @Test
    fun screenOffResetDefaultsOnOnlyWithoutLidDetection() {
        assertEquals("pref_non_root_screen_off_reset", PreferencesManager.KEY_NON_ROOT_SCREEN_OFF_RESET)
        assertTrue(PreferencesManager.isNonRootScreenOffResetEnabled(context, lidDetectionActive = false))
        assertFalse(PreferencesManager.isNonRootScreenOffResetEnabled(context, lidDetectionActive = true))

        PreferencesManager.setNonRootScreenOffResetEnabled(context, true)
        assertTrue(PreferencesManager.isNonRootScreenOffResetEnabled(context, lidDetectionActive = true))
        PreferencesManager.setNonRootScreenOffResetEnabled(context, false)
        assertFalse(PreferencesManager.isNonRootScreenOffResetEnabled(context, lidDetectionActive = false))
    }

    // ---------------------------------------------------------------- contiguous row indices

    private fun assertContiguous(rows: List<String>) {
        assertEquals("row ids must be unique", rows.size, rows.toSet().size)
        rows.forEachIndexed { index, id ->
            val expectedJump = (index + 1).takeIf { it <= 10 }
            assertEquals("jump for $id", expectedJump, settingsJumpNumber(rows, id))
        }
        // Every 1-9 key in range lands on a visible row
        (1..rows.size.coerceAtMost(9)).forEach { digit -> assertTrue(rows.getOrNull(digit - 1) != null) }
    }

    @Test
    fun permissionsRowsHideRootCardAndStayContiguous() {
        val nonRoot = permissionsRowIds(showRootOptions = false)
        assertEquals(
            listOf("home", "ime", "overlay", "a11y", "notifications", "cursor", "show_root"),
            nonRoot
        )
        assertTrue(ROOT_PERMISSION_ROWS.none { it in nonRoot })
        assertContiguous(nonRoot)
        assertEquals(7, settingsJumpNumber(nonRoot, "show_root"))

        val rooted = permissionsRowIds(showRootOptions = true)
        assertTrue(rooted.containsAll(ROOT_PERMISSION_ROWS))
        assertEquals("show_root", rooted.last())
        assertContiguous(rooted)
        // Rows past the tenth have no keypad jump
        assertNull(settingsJumpNumber(rooted, "show_root"))
        assertNull(settingsJumpNumber(rooted, "missing"))
    }

    @Test
    fun behaviorRowsHideLedRowsAndStayContiguous() {
        val nonRoot = behaviorRowIds(hasCreateFolder = true, showRootOptions = false, showScreenOffReset = true)
        assertEquals(
            listOf("create_folder", "folder_grid", "hidden_apps", "lid_open", "screen_off_reset"),
            nonRoot
        )
        assertContiguous(nonRoot)

        val rooted = behaviorRowIds(hasCreateFolder = false, showRootOptions = true, showScreenOffReset = false)
        assertEquals(
            listOf("folder_grid", "hidden_apps", "lid_open", "lid_led", "led_color", "led_charging"),
            rooted
        )
        assertContiguous(rooted)

        val both = behaviorRowIds(hasCreateFolder = true, showRootOptions = true, showScreenOffReset = true)
        assertEquals(8, both.size)
        assertContiguous(both)
    }

    @Test
    fun screenOffResetRowHiddenOnlyForRootedLidDetection() {
        assertTrue(showScreenOffResetRow(showRootOptions = false, lidDetectionActive = false))
        assertTrue(showScreenOffResetRow(showRootOptions = false, lidDetectionActive = true))
        assertTrue(showScreenOffResetRow(showRootOptions = true, lidDetectionActive = false))
        assertFalse(showScreenOffResetRow(showRootOptions = true, lidDetectionActive = true))
    }

    // ---------------------------------------------------------------- touch routing

    @Test
    fun touchRouteKeepsRootFirstAndFallsBackToAccessibility() {
        assertEquals(TouchRoute.ROOT, RootTouchInjector.chooseRoute(true, true, true))
        assertEquals(TouchRoute.ROOT, RootTouchInjector.chooseRoute(true, null, true))
        assertEquals(TouchRoute.ACCESSIBILITY, RootTouchInjector.chooseRoute(true, false, true))
        assertEquals(TouchRoute.ROOT, RootTouchInjector.chooseRoute(true, false, false))
        assertEquals(TouchRoute.ACCESSIBILITY, RootTouchInjector.chooseRoute(false, null, true))
        assertEquals(TouchRoute.NONE, RootTouchInjector.chooseRoute(false, null, false))
    }

    @Test
    fun cursorTapAndScrollUseAccessibilityWhenRootIsUnavailable() {
        val fake = FakeGestures()
        RootTouchInjector.suPresentProbe = { false }
        RootTouchInjector.rootGrantedProbe = { null }
        RootTouchInjector.gestureFallbackProvider = { fake }

        RootTouchInjector.tapAsync(120, 340)
        RootTouchInjector.swipeAsync(240, 400, 240, 560, 200L)

        assertEquals(listOf(120f to 340f), fake.taps)
        assertEquals(listOf(listOf(240f, 400f, 240f, 560f, 200f)), fake.swipes)
    }

    @Test
    fun cursorTapStaysOnRootPathWhenRooted() {
        val fake = FakeGestures()
        RootTouchInjector.suPresentProbe = { true }
        RootTouchInjector.rootGrantedProbe = { true }
        RootTouchInjector.gestureFallbackProvider = { fake }

        RootTouchInjector.tapAsync(10, 20)
        RootTouchInjector.swipeAsync(1, 2, 3, 4, 100L)

        assertTrue(fake.taps.isEmpty())
        assertTrue(fake.swipes.isEmpty())
    }

    // ---------------------------------------------------------------- accessibility service helpers

    @Test
    fun accessibilityEnabledSettingParsing() {
        val full = "one.crinklecod.mochibun/one.crinklecod.mochibun.service.MochibunAccessibilityService"
        val short = "one.crinklecod.mochibun/.service.MochibunAccessibilityService"
        assertFalse(MochibunAccessibilityService.isComponentListed(null, full, short))
        assertFalse(MochibunAccessibilityService.isComponentListed("", full, short))
        assertTrue(MochibunAccessibilityService.isComponentListed("com.other/.A11y:$full", full, short))
        assertTrue(MochibunAccessibilityService.isComponentListed(short, full, short))
        assertFalse(MochibunAccessibilityService.isComponentListed("com.other/.A11y", full, short))
        assertFalse(MochibunAccessibilityService.isEnabled(context))
    }

    @Test
    fun nonRootKeyRoutingRespectsCursorAndTyping() {
        val toggle = KeyEvent.KEYCODE_F4
        assertEquals(Route.TOGGLE_CURSOR, KeyRouting.route(KeyEvent.KEYCODE_F4, 0, toggle, false, false))
        assertEquals(Route.TOGGLE_CURSOR, KeyRouting.route(KeyEvent.KEYCODE_UNKNOWN, 62, toggle, false, true))

        // Cursor hidden: everything else passes through
        assertEquals(Route.PASS, KeyRouting.route(KeyEvent.KEYCODE_DPAD_UP, 0, toggle, false, false))

        // Cursor visible: D-pad / center / scroll digits drive it
        assertEquals(Route.CURSOR_KEY, KeyRouting.route(KeyEvent.KEYCODE_DPAD_UP, 0, toggle, true, false))
        assertEquals(Route.CURSOR_KEY, KeyRouting.route(KeyEvent.KEYCODE_DPAD_CENTER, 0, toggle, true, false))
        assertEquals(Route.CURSOR_KEY, KeyRouting.route(KeyEvent.KEYCODE_2, 0, toggle, true, false))
        assertEquals(Route.CURSOR_KEY, KeyRouting.route(KeyEvent.KEYCODE_8, 0, toggle, true, false))

        // Text field focused: typing keys are never swallowed, D-pad still moves the cursor
        assertEquals(Route.PASS, KeyRouting.route(KeyEvent.KEYCODE_2, 0, toggle, true, true))
        assertEquals(Route.PASS, KeyRouting.route(KeyEvent.KEYCODE_8, 0, toggle, true, true))
        assertEquals(Route.CURSOR_KEY, KeyRouting.route(KeyEvent.KEYCODE_DPAD_LEFT, 0, toggle, true, true))
        assertEquals(Route.PASS, KeyRouting.route(KeyEvent.KEYCODE_STAR, 0, toggle, true, false))
        assertEquals(Route.PASS, KeyRouting.route(KeyEvent.KEYCODE_DEL, 0, toggle, true, false))
    }

    @Test
    fun keyHookHeartbeatValidation() {
        assertTrue(KeyHookStatus.isHeartbeatValid(1_000L, 5_000L, 7, 7, true))
        assertFalse(KeyHookStatus.isHeartbeatValid(1_000L, 5_000L, 7, 7, false))
        assertFalse(KeyHookStatus.isHeartbeatValid(0L, 5_000L, 7, 7, true))
        assertFalse(KeyHookStatus.isHeartbeatValid(9_000L, 5_000L, 7, 7, true)) // from a previous boot
        assertFalse(KeyHookStatus.isHeartbeatValid(1_000L, 5_000L, 6, 7, true))
        assertFalse(KeyHookStatus.isHeartbeatValid(1_000L, 5_000L, -1, -1, true))
    }

    // ---------------------------------------------------------------- backup round trip

    @Test
    fun backupRoundTripPersistsNonRootSwitches() {
        PreferencesManager.setShowRootOptions(context, true)
        PreferencesManager.setNonRootScreenOffResetEnabled(context, false)

        val json = BackupRestoreManager.createBackupJson(context)
        val behavior = JSONObject(json).getJSONObject("behavior")
        assertTrue(behavior.getBoolean("show_root_options"))
        assertFalse(behavior.getBoolean("non_root_screen_off_reset"))

        PreferencesManager.setShowRootOptions(context, false)
        PreferencesManager.setNonRootScreenOffResetEnabled(context, true)

        BackupRestoreManager.restoreBackupJson(context, json).getOrThrow()
        assertTrue(PreferencesManager.isShowRootOptions(context, suPresent = false))
        assertTrue(PreferencesManager.showRootOptions.value)
        assertFalse(PreferencesManager.isNonRootScreenOffResetEnabled(context, lidDetectionActive = false))
    }

    @Test
    fun olderBackupWithoutNonRootSwitchesStillRestores() {
        val json = JSONObject(BackupRestoreManager.createBackupJson(context))
        json.getJSONObject("behavior").apply {
            remove("show_root_options")
            remove("non_root_screen_off_reset")
        }
        assertTrue(BackupRestoreManager.restoreBackupJson(context, json.toString()).isSuccess)
        // Untouched: detection-based defaults still apply
        assertFalse(PreferencesManager.isShowRootOptions(context, suPresent = false))
        assertTrue(PreferencesManager.isNonRootScreenOffResetEnabled(context, lidDetectionActive = false))
    }

    @Test
    fun malformedNonRootSwitchFailsValidation() {
        val json = JSONObject(BackupRestoreManager.createBackupJson(context))
        json.getJSONObject("behavior").put("show_root_options", "yes")
        assertFalse(BackupRestoreManager.restoreBackupJson(context, json.toString()).isSuccess)
    }
}
