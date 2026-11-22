package one.crinklecod.mochibun

import android.content.Intent
import android.view.KeyEvent
import kotlinx.coroutines.flow.StateFlow
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.prefs.HomeMascotSize
import one.crinklecod.mochibun.data.prefs.LidOpenAction
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.ui.screens.SETTINGS_HUB_DESTINATIONS
import one.crinklecod.mochibun.ui.screens.SettingsDestination
import one.crinklecod.mochibun.ui.screens.SettingsNavigationState
import one.crinklecod.mochibun.ui.screens.buildInitialSettingsStack
import one.crinklecod.mochibun.ui.screens.settingsBackTarget
import one.crinklecod.mochibun.ui.screens.settings.APPEARANCE_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.AppearanceRowId
import one.crinklecod.mochibun.ui.screens.settings.BehaviorRowId
import one.crinklecod.mochibun.ui.screens.settings.CURSOR_HARDWARE_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.CURSOR_MOTION_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.ClamshellLidRowId
import one.crinklecod.mochibun.ui.screens.settings.CursorHardwareRowId
import one.crinklecod.mochibun.ui.screens.settings.KEYBOARD_PREDICTIVE_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.KEYBOARD_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.KeyboardRowId
import one.crinklecod.mochibun.ui.screens.settings.MAGISK_ROOT_LSPOSED_ROW_IDS
import one.crinklecod.mochibun.ui.screens.settings.PermissionsRootRowId
import one.crinklecod.mochibun.ui.screens.settings.appearanceGreetingTickerRowIds
import one.crinklecod.mochibun.ui.screens.settings.appearanceTypographyBordersRowIds
import one.crinklecod.mochibun.ui.screens.settings.appearanceWallpaperDecoRowIds
import one.crinklecod.mochibun.ui.screens.settings.appearanceWidgetsCardsRowIds
import one.crinklecod.mochibun.ui.screens.settings.permissionsRowIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Contracts the Settings Hub relies on to stay cheap and predictable under D-pad stress
 * (`tools/benchmark_m120.sh --stress-settings`):
 *  - flipping one setting changes only that setting's StateFlow, so `collectAsState` invalidates
 *    only the rows that read it (StateFlow emits only on a non-equal value), and an even number of
 *    flips restores every value (the stress harness depends on that);
 *  - rapid destination switching always takes exactly one step per Back;
 *  - a hook broadcast whose parceled KeyEvent was overwritten by the next event is still read
 *    with its send-time action, so one BACK press can't run Back twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsHubStressContractTest {

    private val context get() = RuntimeEnvironment.getApplication()

    /** Every StateFlow a settings sub-screen collects, by name. */
    private val settingsFlows: Map<String, StateFlow<*>> by lazy {
        mapOf(
            "wallpaperOverlay" to PreferencesManager.isWallpaperOverlayEnabled,
            "systemMonitor" to PreferencesManager.isSystemMonitorEnabled,
            "systemMonitorInterval" to PreferencesManager.systemMonitorInterval,
            "photoFrame" to PreferencesManager.isPhotoFrameEnabled,
            "subDisplayTicker" to PreferencesManager.isSubDisplayTickerEnabled,
            "subDisplayTickerDark" to PreferencesManager.isSubDisplayTickerDark,
            "homeMascotSize" to PreferencesManager.homeMascotSize,
            "fontFamily" to PreferencesManager.themeFontFamily,
            "folderScatter" to PreferencesManager.isFolderScatterLayout,
            "lidOpenAction" to PreferencesManager.lidOpenAction,
            "lidLed" to PreferencesManager.isLidLedEnabled,
            "lidLedCharging" to PreferencesManager.isLidLedChargingEnabled,
            "lidLedColor" to PreferencesManager.lidLedColor,
            "showRootOptions" to PreferencesManager.showRootOptions,
            "softKeyNav" to PreferencesManager.isSoftKeyNavEnabled,
            "dpadWrap" to PreferencesManager.isDpadWrapAround,
            "dpadHaptics" to PreferencesManager.isDpadBoundaryHaptics,
            "audioStream" to PreferencesManager.launcherAudioStream,
            "soundVolume" to PreferencesManager.launcherSoundVolume,
            "keypadSoundMode" to PreferencesManager.keypadSoundMode,
            "mediaVolumeLock" to PreferencesManager.isMediaVolumeLockEnabled,
            "lidSnapSound" to PreferencesManager.isLidCloseSnapSoundEnabled,
            "quickSnippets" to PreferencesManager.quickSnippets,
            "lidAppLedRules" to PreferencesManager.lidAppLedRules
        )
    }

    private fun snapshot(): Map<String, Any?> = settingsFlows.mapValues { it.value.value }

    @Before
    fun setUp() {
        PreferencesManager.init(context)
    }

    /** One flip per row type the Settings screens offer (toggle, cyclic stepper, value stepper). */
    private val flips: List<Pair<String, () -> Unit>> by lazy {
        listOf(
            "photoFrame" to {
                PreferencesManager.setPhotoFrameEnabled(context, !PreferencesManager.isPhotoFrameEnabled.value)
            },
            "wallpaperOverlay" to {
                PreferencesManager.setWallpaperOverlayEnabled(
                    context, !PreferencesManager.isWallpaperOverlayEnabled.value
                )
            },
            "subDisplayTicker" to {
                PreferencesManager.setSubDisplayTickerEnabled(
                    context, !PreferencesManager.isSubDisplayTickerEnabled.value
                )
            },
            "folderScatter" to {
                PreferencesManager.setFolderScatterLayout(context, !PreferencesManager.isFolderScatterLayout.value)
            },
            "dpadWrap" to {
                PreferencesManager.setDpadWrapAround(context, !PreferencesManager.isDpadWrapAround.value)
            },
            "homeMascotSize" to {
                PreferencesManager.setHomeMascotSize(context, PreferencesManager.homeMascotSize.value.next())
            },
            "lidOpenAction" to {
                PreferencesManager.setLidOpenAction(context, PreferencesManager.lidOpenAction.value.next())
            },
            "soundVolume" to {
                val v = PreferencesManager.launcherSoundVolume.value
                PreferencesManager.setLauncherSoundVolume(context, if (v >= 50) v - 10 else v + 10)
            }
        )
    }

    @Test
    fun flippingOneSettingChangesOnlyThatSettingsFlow() {
        for ((name, flip) in flips) {
            val before = snapshot()
            flip()
            val after = snapshot()
            assertNotEquals("$name should change", before[name], after[name])
            for (other in settingsFlows.keys - name) {
                assertEquals("flipping $name must not touch $other", before[other], after[other])
            }
        }
    }

    @Test
    fun evenToggleFlipsRestoreEverySetting() {
        val toggles = setOf("photoFrame", "wallpaperOverlay", "subDisplayTicker", "folderScatter", "dpadWrap")
        val original = snapshot()
        repeat(10) { // like the harness: "Hold for digit" gets 10 flips
            flips.filter { it.first in toggles }.forEach { it.second() }
        }
        assertEquals(original, snapshot())
    }

    @Test
    fun imeHoldForDigitEvenFlipsRestore() {
        val original = PreferencesManager.isImeHoldForDigitEnabled(context)
        var value = original
        repeat(10) {
            value = !value
            PreferencesManager.setImeHoldForDigitEnabled(context, value)
        }
        assertEquals(original, PreferencesManager.isImeHoldForDigitEnabled(context))
    }

    @Test
    fun hubJumpKeysMapToDistinctDestinationsAndBackReturnsToHub() {
        assertEquals(8, SETTINGS_HUB_DESTINATIONS.size)
        assertEquals(SETTINGS_HUB_DESTINATIONS.size, SETTINGS_HUB_DESTINATIONS.toSet().size)
        assertEquals(false, SettingsDestination.Hub in SETTINGS_HUB_DESTINATIONS)
        for (destination in SETTINGS_HUB_DESTINATIONS) {
            assertSame(SettingsDestination.Hub, settingsBackTarget(destination))
        }
        assertNull("Back on the Hub closes it", settingsBackTarget(SettingsDestination.Hub))
        assertSame(SettingsDestination.CursorHardware, settingsBackTarget(SettingsDestination.CursorMotion))
        assertSame(SettingsDestination.Behavior, settingsBackTarget(SettingsDestination.ClamshellLid))
        assertSame(SettingsDestination.ClamshellLid, settingsBackTarget(SettingsDestination.LidLedStudio))
        assertSame(SettingsDestination.Keyboard, settingsBackTarget(SettingsDestination.KeyboardPredictiveText))
        assertSame(SettingsDestination.PermissionsRoot, settingsBackTarget(SettingsDestination.MagiskRootLsposed))
        assertSame(SettingsDestination.Appearance, settingsBackTarget(SettingsDestination.AppearanceTypographyBorders))
        assertSame(SettingsDestination.Appearance, settingsBackTarget(SettingsDestination.AppearanceGreetingTicker))
        assertSame(SettingsDestination.Appearance, settingsBackTarget(SettingsDestination.AppearanceWallpaperDeco))
        assertSame(SettingsDestination.Appearance, settingsBackTarget(SettingsDestination.AppearanceWidgetsCards))
    }

    @Test
    fun rapidDestinationSwitchingTakesOneStepPerBack() {
        // Jump 1..8 then Back, many times over, including the Clamshell -> Lid LED Studio detour.
        var destination: SettingsDestination? = SettingsDestination.Hub
        repeat(50) { round ->
            val target = SETTINGS_HUB_DESTINATIONS[round % SETTINGS_HUB_DESTINATIONS.size]
            destination = target
            if (target == SettingsDestination.Behavior && round % 2 == 0) {
                destination = SettingsDestination.ClamshellLid
                assertSame(SettingsDestination.Behavior, settingsBackTarget(destination))
                destination = SettingsDestination.LidLedStudio
                destination = settingsBackTarget(destination)
                assertSame(SettingsDestination.ClamshellLid, destination)
                destination = settingsBackTarget(destination!!)
                assertSame(SettingsDestination.Behavior, destination)
            }
            destination = settingsBackTarget(destination!!)
            assertSame("one Back from $target must land on the Hub", SettingsDestination.Hub, destination)
        }
        assertNull(settingsBackTarget(destination!!))
    }

    @Test
    fun hookKeyEventUsesSendTimeActionWhenParceledEventWasOverwritten() {
        // The hook captured DOWN in the int extras, but the shared KeyEvent object it attached was
        // recycled into the following UP before the broadcast was parceled.
        val overwritten = KeyEvent(1_000L, 1_050L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0)
        val intent = Intent(MochibunSystemActions.ACTION_BACK_TRIGGER)
            .putExtra(MochibunSystemActions.EXTRA_KEY_EVENT, overwritten)
            .putExtra(MochibunSystemActions.EXTRA_KEY_CODE, KeyEvent.KEYCODE_BACK)
            .putExtra(MochibunSystemActions.EXTRA_ACTION, KeyEvent.ACTION_DOWN)
            .putExtra(MochibunSystemActions.EXTRA_REPEAT_COUNT, 0)

        val event = MochibunSystemActions.hookKeyEvent(intent)!!
        assertEquals(KeyEvent.ACTION_DOWN, event.action)
        assertEquals(KeyEvent.KEYCODE_BACK, event.keyCode)
        assertEquals(0, event.repeatCount)
        assertEquals(1_000L, event.downTime)
    }

    @Test
    fun hookKeyEventKeepsConsistentEventsAndHandlesMissingExtras() {
        val down = KeyEvent(1_000L, 1_000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_APP_SWITCH, 2)
        val consistent = Intent(MochibunSystemActions.ACTION_LSK_TRIGGER)
            .putExtra(MochibunSystemActions.EXTRA_KEY_EVENT, down)
            .putExtra(MochibunSystemActions.EXTRA_ACTION, KeyEvent.ACTION_DOWN)
            .putExtra(MochibunSystemActions.EXTRA_REPEAT_COUNT, 2)
        val event = MochibunSystemActions.hookKeyEvent(consistent)!!
        assertEquals(KeyEvent.ACTION_DOWN, event.action)
        assertEquals(2, event.repeatCount)
        assertEquals(KeyEvent.KEYCODE_APP_SWITCH, event.keyCode)

        // Without int extras the parceled event is used as-is
        val bare = Intent(MochibunSystemActions.ACTION_BACK_TRIGGER)
            .putExtra(MochibunSystemActions.EXTRA_KEY_EVENT, down)
        assertEquals(KeyEvent.ACTION_DOWN, MochibunSystemActions.hookKeyEvent(bare)!!.action)

        assertNull(MochibunSystemActions.hookKeyEvent(Intent(MochibunSystemActions.ACTION_BACK_TRIGGER)))
    }

    @Test
    fun homeMascotSizeAndLidActionSteppersCycle() {
        // Cyclic steppers (D-pad Left/Right) must come back around, so paired RIGHT/LEFT presses
        // in the stress harness leave the value unchanged.
        HomeMascotSize.entries.forEach { assertEquals(it, it.next().previous()) }
        LidOpenAction.entries.forEach { assertEquals(it, it.next().previous()) }
    }

    @Test
    fun buildInitialSettingsStackFollowsBackTargets() {
        assertEquals(listOf(SettingsDestination.Hub), buildInitialSettingsStack(SettingsDestination.Hub))
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.Appearance),
            buildInitialSettingsStack(SettingsDestination.Appearance)
        )
        assertEquals(
            listOf(
                SettingsDestination.Hub,
                SettingsDestination.Behavior,
                SettingsDestination.ClamshellLid,
                SettingsDestination.LidLedStudio
            ),
            buildInitialSettingsStack(SettingsDestination.LidLedStudio)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.Behavior, SettingsDestination.ClamshellLid),
            buildInitialSettingsStack(SettingsDestination.ClamshellLid)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.Keyboard, SettingsDestination.KeyboardPredictiveText),
            buildInitialSettingsStack(SettingsDestination.KeyboardPredictiveText)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.CursorHardware, SettingsDestination.CursorMotion),
            buildInitialSettingsStack(SettingsDestination.CursorMotion)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.PermissionsRoot, SettingsDestination.MagiskRootLsposed),
            buildInitialSettingsStack(SettingsDestination.MagiskRootLsposed)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.Appearance, SettingsDestination.AppearanceTypographyBorders),
            buildInitialSettingsStack(SettingsDestination.AppearanceTypographyBorders)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.Appearance, SettingsDestination.AppearanceGreetingTicker),
            buildInitialSettingsStack(SettingsDestination.AppearanceGreetingTicker)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.Appearance, SettingsDestination.AppearanceWallpaperDeco),
            buildInitialSettingsStack(SettingsDestination.AppearanceWallpaperDeco)
        )
        assertEquals(
            listOf(SettingsDestination.Hub, SettingsDestination.Appearance, SettingsDestination.AppearanceWidgetsCards),
            buildInitialSettingsStack(SettingsDestination.AppearanceWidgetsCards)
        )
    }

    @Test
    fun settingsNavigationStateRestoresParentFocusAndPopsModalsFirst() {
        val nav = SettingsNavigationState(SettingsDestination.Hub)
        var closed = false

        // Navigate Hub(row 4) -> Behavior(row 3: Clamshell & Lid) -> ClamshellLid(row 4: Lid LED Studio) -> LidLedStudio
        val clamshellRowIndex = BehaviorRowId.CLAMSHELL_LID.ordinal
        val ledStudioRowIndex = ClamshellLidRowId.LED_STUDIO.ordinal

        nav.navigateTo(SettingsDestination.Behavior, fromFocusIndex = 4)
        assertSame(SettingsDestination.Behavior, nav.currentDestination)
        assertEquals(0, nav.initialFocusFor(SettingsDestination.Behavior))

        nav.saveFocusIfActive(SettingsDestination.Behavior, clamshellRowIndex)
        nav.navigateTo(SettingsDestination.ClamshellLid)
        assertSame(SettingsDestination.ClamshellLid, nav.currentDestination)
        assertEquals(0, nav.initialFocusFor(SettingsDestination.ClamshellLid))

        nav.saveFocusIfActive(SettingsDestination.ClamshellLid, ledStudioRowIndex)
        nav.navigateTo(SettingsDestination.LidLedStudio)
        assertSame(SettingsDestination.LidLedStudio, nav.currentDestination)
        assertEquals(4, nav.stackDepth)

        // Nested modal stack invokes topmost handler before popping destinations
        val dismissed = mutableListOf<String>()
        nav.setNestedBackHandler("lid_studio") { dismissed += "studio"; nav.setNestedBackHandler("lid_studio", null) }
        nav.setNestedBackHandler("picker") { dismissed += "picker"; nav.setNestedBackHandler("picker", null) }

        assertTrue(nav.handleBack { closed = true })
        assertEquals(listOf("picker"), dismissed)
        assertSame(SettingsDestination.LidLedStudio, nav.currentDestination)

        assertTrue(nav.handleBack { closed = true })
        assertEquals(listOf("picker", "studio"), dismissed)
        assertSame(SettingsDestination.LidLedStudio, nav.currentDestination)

        // Back pops LidLedStudio -> ClamshellLid with focus restored on Lid LED Studio
        assertTrue(nav.handleBack { closed = true })
        assertSame(SettingsDestination.ClamshellLid, nav.currentDestination)
        assertEquals(ledStudioRowIndex, nav.initialFocusFor(SettingsDestination.ClamshellLid))

        // Late dispose from popped LidLedStudio must not overwrite saved focus
        nav.saveFocusIfActive(SettingsDestination.LidLedStudio, 2)
        assertEquals(0, nav.initialFocusFor(SettingsDestination.LidLedStudio))

        // Back pops ClamshellLid -> Behavior with focus restored on Clamshell & Lid
        assertTrue(nav.handleBack { closed = true })
        assertSame(SettingsDestination.Behavior, nav.currentDestination)
        assertEquals(clamshellRowIndex, nav.initialFocusFor(SettingsDestination.Behavior))

        // Re-entering ClamshellLid starts at 0; popping Behavior -> Hub restores 4; last Back closes
        nav.navigateTo(SettingsDestination.ClamshellLid)
        assertEquals(0, nav.initialFocusFor(SettingsDestination.ClamshellLid))
        assertTrue(nav.popDestination { closed = true })
        assertTrue(nav.popDestination { closed = true })
        assertSame(SettingsDestination.Hub, nav.currentDestination)
        assertEquals(4, nav.initialFocusFor(SettingsDestination.Hub))
        assertFalse(closed)

        assertFalse(nav.handleBack { closed = true })
        assertTrue(closed)
    }

    @Test
    fun keyboardPredictiveTextSubScreenRestoresPredictiveTextRowFocus() {
        assertEquals(9, KEYBOARD_ROW_IDS.size)
        assertEquals(4, KEYBOARD_PREDICTIVE_ROW_IDS.size)
        val predictiveRowIndex = KeyboardRowId.PREDICTIVE_TEXT.ordinal
        assertEquals(6, predictiveRowIndex) // 7th row (jump key 7)

        val nav = SettingsNavigationState(SettingsDestination.Hub)
        nav.navigateTo(SettingsDestination.Keyboard, fromFocusIndex = 1)
        nav.saveFocusIfActive(SettingsDestination.Keyboard, predictiveRowIndex)
        nav.navigateTo(SettingsDestination.KeyboardPredictiveText)
        assertSame(SettingsDestination.KeyboardPredictiveText, nav.currentDestination)
        assertEquals(0, nav.initialFocusFor(SettingsDestination.KeyboardPredictiveText))

        var closed = false
        assertTrue(nav.handleBack { closed = true })
        assertSame(SettingsDestination.Keyboard, nav.currentDestination)
        assertEquals(predictiveRowIndex, nav.initialFocusFor(SettingsDestination.Keyboard))
        assertFalse(closed)
    }

    @Test
    fun cursorMotionSubScreenRestoresCursorMotionRowFocus() {
        assertEquals(8, CURSOR_HARDWARE_ROW_IDS.size)
        assertEquals(5, CURSOR_MOTION_ROW_IDS.size)
        val cursorMotionRowIndex = CursorHardwareRowId.CURSOR_MOTION.ordinal
        assertEquals(0, cursorMotionRowIndex) // 1st row (jump key 1)

        val nav = SettingsNavigationState(SettingsDestination.Hub)
        nav.navigateTo(SettingsDestination.CursorHardware, fromFocusIndex = 2)
        nav.saveFocusIfActive(SettingsDestination.CursorHardware, cursorMotionRowIndex)
        nav.navigateTo(SettingsDestination.CursorMotion)
        assertSame(SettingsDestination.CursorMotion, nav.currentDestination)
        assertEquals(0, nav.initialFocusFor(SettingsDestination.CursorMotion))

        var closed = false
        assertTrue(nav.handleBack { closed = true })
        assertSame(SettingsDestination.CursorHardware, nav.currentDestination)
        assertEquals(cursorMotionRowIndex, nav.initialFocusFor(SettingsDestination.CursorHardware))
        assertFalse(closed)
    }

    @Test
    fun magiskRootLsposedSubScreenRestoresParentRowFocus() {
        assertEquals(7, permissionsRowIds(showRootOptions = false).size)
        assertEquals(8, permissionsRowIds(showRootOptions = true).size)
        assertEquals(7, MAGISK_ROOT_LSPOSED_ROW_IDS.size)
        val magiskRowIndex = PermissionsRootRowId.MAGISK_ROOT_LSPOSED.ordinal
        assertEquals(7, magiskRowIndex) // 8th row (jump key 8)

        val nav = SettingsNavigationState(SettingsDestination.Hub)
        nav.navigateTo(SettingsDestination.PermissionsRoot, fromFocusIndex = 5)
        nav.saveFocusIfActive(SettingsDestination.PermissionsRoot, magiskRowIndex)
        nav.navigateTo(SettingsDestination.MagiskRootLsposed)
        assertSame(SettingsDestination.MagiskRootLsposed, nav.currentDestination)
        assertEquals(0, nav.initialFocusFor(SettingsDestination.MagiskRootLsposed))

        var closed = false
        assertTrue(nav.handleBack { closed = true })
        assertSame(SettingsDestination.PermissionsRoot, nav.currentDestination)
        assertEquals(magiskRowIndex, nav.initialFocusFor(SettingsDestination.PermissionsRoot))
        assertFalse(closed)
    }

    @Test
    fun appearanceSubScreensRestoreParentRowFocus() {
        assertEquals(6, APPEARANCE_ROW_IDS.size)
        assertEquals(
            7,
            appearanceTypographyBordersRowIds(
                hasBundleFont = true,
                showRootOptions = true,
                isSystemFontInstalled = true,
                hasCustomBorder = true
            ).size
        )
        assertEquals(3, appearanceGreetingTickerRowIds(isSubDisplayTickerEnabled = true).size)
        assertEquals(7, appearanceWallpaperDecoRowIds(hasCustomWallpaper = true, hasCustomMascot = true).size)
        assertEquals(3, appearanceWidgetsCardsRowIds(isSystemMonitorEnabled = true).size)

        val nav = SettingsNavigationState(SettingsDestination.Hub)
        nav.navigateTo(SettingsDestination.Appearance, fromFocusIndex = 0)

        val routes = listOf(
            AppearanceRowId.TYPOGRAPHY_BORDERS to SettingsDestination.AppearanceTypographyBorders,
            AppearanceRowId.GREETING_TICKER to SettingsDestination.AppearanceGreetingTicker,
            AppearanceRowId.WALLPAPER_DECO to SettingsDestination.AppearanceWallpaperDeco,
            AppearanceRowId.WIDGETS_CARDS to SettingsDestination.AppearanceWidgetsCards
        )
        var closed = false
        for ((row, target) in routes) {
            nav.saveFocusIfActive(SettingsDestination.Appearance, row.ordinal)
            nav.navigateTo(target)
            assertSame(target, nav.currentDestination)
            assertEquals(0, nav.initialFocusFor(target))

            assertTrue(nav.handleBack { closed = true })
            assertSame(SettingsDestination.Appearance, nav.currentDestination)
            assertEquals(row.ordinal, nav.initialFocusFor(SettingsDestination.Appearance))
            assertFalse(closed)
        }
    }
}
