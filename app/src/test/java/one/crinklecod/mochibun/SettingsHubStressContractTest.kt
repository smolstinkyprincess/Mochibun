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
import one.crinklecod.mochibun.ui.screens.settingsBackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
            "folderGrid" to PreferencesManager.isFolderGridMode,
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
            "folderGrid" to {
                PreferencesManager.setFolderGridMode(context, !PreferencesManager.isFolderGridMode.value)
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
        val toggles = setOf("photoFrame", "wallpaperOverlay", "subDisplayTicker", "folderGrid", "dpadWrap")
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
        assertSame(SettingsDestination.Behavior, settingsBackTarget(SettingsDestination.LidLedStudio))
    }

    @Test
    fun rapidDestinationSwitchingTakesOneStepPerBack() {
        // Jump 1..8 then Back, many times over, including the Lid LED Studio detour.
        var destination: SettingsDestination? = SettingsDestination.Hub
        repeat(50) { round ->
            val target = SETTINGS_HUB_DESTINATIONS[round % SETTINGS_HUB_DESTINATIONS.size]
            destination = target
            if (target == SettingsDestination.Behavior && round % 2 == 0) {
                destination = SettingsDestination.LidLedStudio
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
}
