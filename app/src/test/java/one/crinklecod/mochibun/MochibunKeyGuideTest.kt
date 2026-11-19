package one.crinklecod.mochibun

import android.view.KeyEvent
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.model.ActionType
import one.crinklecod.mochibun.data.model.ButtonActionConfig
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import one.crinklecod.mochibun.ui.dialogs.DpadDirection
import one.crinklecod.mochibun.ui.dialogs.KeyGuideKey
import one.crinklecod.mochibun.ui.dialogs.KeyGuideNavState
import one.crinklecod.mochibun.ui.dialogs.KeyGuideTab
import one.crinklecod.mochibun.ui.dialogs.KeyGuideZone
import one.crinklecod.mochibun.ui.dialogs.KeyRemapState
import one.crinklecod.mochibun.ui.dialogs.RemapSlot
import one.crinklecod.mochibun.ui.dialogs.applyPrimaryRemapAction
import one.crinklecod.mochibun.ui.dialogs.clearRemapForKey
import one.crinklecod.mochibun.ui.dialogs.formatRemapCalloutLines
import one.crinklecod.mochibun.ui.dialogs.isCustomRemapped
import one.crinklecod.mochibun.ui.dialogs.mapHardwareKeyToGuideKey
import one.crinklecod.mochibun.ui.dialogs.readKeyRemapSnapshot
import one.crinklecod.mochibun.ui.dialogs.stepKeyGuideFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MochibunKeyGuideTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        PreferencesManager.init(context)
    }

    @Test
    fun `3 tabs have expected labels and order`() {
        assertEquals(
            listOf(KeyGuideTab.LAUNCHER, KeyGuideTab.TYPING, KeyGuideTab.REMAP),
            KeyGuideTab.entries
        )
        assertEquals("♥ Launcher", KeyGuideTab.LAUNCHER.label)
        assertEquals("✎ Typing", KeyGuideTab.TYPING.label)
        assertEquals("✦ Remap", KeyGuideTab.REMAP.label)
    }

    @Test
    fun `every key has non-blank descriptions across all 3 tabs and never uses F4 label`() {
        for (key in KeyGuideKey.entries) {
            assertTrue("${key.name} displayName blank", key.displayName.isNotBlank())
            assertFalse("${key.name} mentions F4", key.displayName.contains("F4"))
            for (tab in KeyGuideTab.entries) {
                val tap = key.tapText(tab)
                val hold = key.holdText(tab)
                assertTrue("${key.name} tap blank in $tab", tap.isNotBlank())
                assertTrue("${key.name} hold blank in $tab", hold.isNotBlank())
                assertFalse("${key.name} tap mentions F4 in $tab", tap.contains("F4"))
                assertFalse("${key.name} hold mentions F4 in $tab", hold.contains("F4"))
            }
        }
        assertEquals("Side Key", KeyGuideKey.SIDE_KEY.displayName)
    }

    @Test
    fun `protected navigation keys are non-remappable and show System Protected callout`() {
        val protectedKeys = setOf(
            KeyGuideKey.HOME,
            KeyGuideKey.RSK,
            KeyGuideKey.DPAD_RING,
            KeyGuideKey.OK,
            KeyGuideKey.DEL,
            KeyGuideKey.END
        )
        for (key in KeyGuideKey.entries) {
            if (key in protectedKeys) {
                assertFalse("${key.name} should be protected", key.isRemappable)
                assertNull("${key.name} must not expose remapKeyCode", key.remapKeyCode)
                val (tapDesc, _) = formatRemapCalloutLines(key, null)
                assertTrue("${key.name} callout should note System / Protected", tapDesc.contains("System / Protected"))
            } else {
                assertTrue("${key.name} should be remappable", key.isRemappable)
                assertNotNull("${key.name} should have remapKeyCode", key.remapKeyCode)
            }
        }
        assertEquals(RemapSlot.SINGLE, KeyGuideKey.CONTACTS.primaryRemapSlot)
        assertEquals(RemapSlot.SINGLE, KeyGuideKey.SIDE_KEY.primaryRemapSlot)
        assertEquals(RemapSlot.LONG, KeyGuideKey.KEY_2.primaryRemapSlot)
        assertEquals(RemapSlot.LONG, KeyGuideKey.KEY_0.primaryRemapSlot)
    }

    @Test
    fun `custom remap detection ignores default bindings and reflects overrides`() {
        val recentsDefault = ButtonActionConfig(singlePressAction = ActionType.GlobalRecents)
        assertFalse(isCustomRemapped(recentsDefault, recentsDefault))

        val empty = ButtonActionConfig()
        assertFalse(isCustomRemapped(empty, empty))

        val customLaunch = ButtonActionConfig(singlePressAction = ActionType.LaunchApp("com.example.cam", "MochiCam"))
        assertTrue(isCustomRemapped(customLaunch, empty))

        val (tapLine, _) = formatRemapCalloutLines(
            KeyGuideKey.CAMERA,
            KeyRemapState(config = customLaunch, defaultConfig = empty, hasCustomRemap = true)
        )
        assertTrue(tapLine.contains("MochiCam"))
    }

    @Test
    fun `applyPrimaryRemapAction and clearRemapForKey persist through HardwareRemapManager`() {
        clearRemapForKey(context, KeyGuideKey.SIDE_KEY)
        clearRemapForKey(context, KeyGuideKey.KEY_5)

        val before = readKeyRemapSnapshot(context)
        assertFalse(before.getValue(KeyGuideKey.SIDE_KEY).hasCustomRemap)
        assertFalse(before.getValue(KeyGuideKey.KEY_5).hasCustomRemap)

        applyPrimaryRemapAction(context, KeyGuideKey.SIDE_KEY, ActionType.ToggleFlashlight)
        applyPrimaryRemapAction(context, KeyGuideKey.KEY_5, ActionType.LaunchApp("one.test.notes", "MochiNotes"))

        val afterAssign = readKeyRemapSnapshot(context)
        assertTrue(afterAssign.getValue(KeyGuideKey.SIDE_KEY).hasCustomRemap)
        assertEquals(ActionType.ToggleFlashlight, afterAssign.getValue(KeyGuideKey.SIDE_KEY).config.singlePressAction)
        assertTrue(afterAssign.getValue(KeyGuideKey.KEY_5).hasCustomRemap)
        assertEquals(
            ActionType.LaunchApp("one.test.notes", "MochiNotes"),
            afterAssign.getValue(KeyGuideKey.KEY_5).config.longPressAction
        )
        assertEquals(
            "one.test.notes",
            PreferencesManager.getSpeedDialPackage(context, 5)
        )

        clearRemapForKey(context, KeyGuideKey.SIDE_KEY)
        clearRemapForKey(context, KeyGuideKey.KEY_5)
        val afterClear = readKeyRemapSnapshot(context)
        assertFalse(afterClear.getValue(KeyGuideKey.SIDE_KEY).hasCustomRemap)
        assertFalse(afterClear.getValue(KeyGuideKey.KEY_5).hasCustomRemap)
        assertNull(PreferencesManager.getSpeedDialPackage(context, 5))
        assertEquals(
            ButtonActionConfig(),
            HardwareRemapManager.getButtonActionConfigForKey(context, KeyEvent.KEYCODE_F4, MochibunSystemActions.SIDE_KEY_SCAN_CODE)
        )
    }

    @Test
    fun `hardware key mapping covers all physical AT-M120 keys`() {
        val expected = mapOf(
            KeyEvent.KEYCODE_0 to KeyGuideKey.KEY_0,
            KeyEvent.KEYCODE_1 to KeyGuideKey.KEY_1,
            KeyEvent.KEYCODE_2 to KeyGuideKey.KEY_2,
            KeyEvent.KEYCODE_3 to KeyGuideKey.KEY_3,
            KeyEvent.KEYCODE_4 to KeyGuideKey.KEY_4,
            KeyEvent.KEYCODE_5 to KeyGuideKey.KEY_5,
            KeyEvent.KEYCODE_6 to KeyGuideKey.KEY_6,
            KeyEvent.KEYCODE_7 to KeyGuideKey.KEY_7,
            KeyEvent.KEYCODE_8 to KeyGuideKey.KEY_8,
            KeyEvent.KEYCODE_9 to KeyGuideKey.KEY_9,
            KeyEvent.KEYCODE_STAR to KeyGuideKey.KEY_STAR,
            KeyEvent.KEYCODE_POUND to KeyGuideKey.KEY_POUND,
            KeyEvent.KEYCODE_F4 to KeyGuideKey.SIDE_KEY,
            KeyEvent.KEYCODE_DEL to KeyGuideKey.DEL,
            KeyEvent.KEYCODE_APP_SWITCH to KeyGuideKey.LSK,
            KeyEvent.KEYCODE_HOME to KeyGuideKey.HOME,
            KeyEvent.KEYCODE_CALL to KeyGuideKey.CALL,
            KeyEvent.KEYCODE_ENDCALL to KeyGuideKey.END,
            KeyEvent.KEYCODE_CONTACTS to KeyGuideKey.CONTACTS,
            KeyEvent.KEYCODE_F1 to KeyGuideKey.CONTACTS,
            KeyEvent.KEYCODE_F2 to KeyGuideKey.MESSAGES,
            KeyEvent.KEYCODE_CAMERA to KeyGuideKey.CAMERA,
            KeyEvent.KEYCODE_F3 to KeyGuideKey.STAR_FN,
            KeyEvent.KEYCODE_DPAD_CENTER to KeyGuideKey.OK
        )
        for ((keyCode, expectedKey) in expected) {
            assertEquals("keyCode $keyCode", expectedKey, mapHardwareKeyToGuideKey(keyCode))
        }
        assertEquals(
            KeyGuideKey.SIDE_KEY,
            mapHardwareKeyToGuideKey(KeyEvent.KEYCODE_UNKNOWN, MochibunSystemActions.SIDE_KEY_SCAN_CODE)
        )
    }

    @Test
    fun `dpad navigation reaches every key, tabs, remap actions, and close pill`() {
        fun exploreAll(isRemapActionsVisible: Boolean): Set<KeyGuideNavState> {
            val visited = mutableSetOf<KeyGuideNavState>()
            val queue = ArrayDeque<KeyGuideNavState>()
            val start = KeyGuideNavState(KeyGuideZone.KEYPAD, KeyGuideKey.HOME)
            visited.add(start)
            queue.add(start)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (dir in DpadDirection.entries) {
                    val next = stepKeyGuideFocus(
                        zone = current.zone,
                        key = current.key,
                        direction = dir,
                        isRemapActionsVisible = isRemapActionsVisible
                    )
                    if (visited.add(next)) {
                        queue.add(next)
                    }
                }
            }
            return visited
        }

        val guideVisited = exploreAll(isRemapActionsVisible = false)
        assertEquals(KeyGuideKey.entries.toSet(), guideVisited.filter { it.zone == KeyGuideZone.KEYPAD }.map { it.key }.toSet())
        assertTrue(guideVisited.any { it.zone == KeyGuideZone.TABS })
        assertTrue(guideVisited.any { it.zone == KeyGuideZone.CLOSE })
        assertFalse(guideVisited.any { it.zone == KeyGuideZone.REMAP_ACTIONS })

        val remapVisited = exploreAll(isRemapActionsVisible = true)
        assertEquals(KeyGuideKey.entries.toSet(), remapVisited.filter { it.zone == KeyGuideZone.KEYPAD }.map { it.key }.toSet())
        assertTrue(remapVisited.any { it.zone == KeyGuideZone.TABS })
        assertTrue(remapVisited.any { it.zone == KeyGuideZone.REMAP_ACTIONS })
        assertTrue(remapVisited.any { it.zone == KeyGuideZone.CLOSE })
    }
}
