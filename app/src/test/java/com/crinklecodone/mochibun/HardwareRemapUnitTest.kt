package com.tnyemii.mochibun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareRemapUnitTest {

    @Test
    fun testActionTypeSerialization() {
        val none = ActionType.None
        assertEquals("NONE", none.serialize())
        assertEquals(ActionType.None, ActionType.deserialize(none.serialize()))

        val launch = ActionType.LaunchApp("com.android.chrome", "Chrome")
        assertEquals("LAUNCH_APP:com.android.chrome:Chrome", launch.serialize())
        val deserializedLaunch = ActionType.deserialize(launch.serialize()) as ActionType.LaunchApp
        assertEquals("com.android.chrome", deserializedLaunch.packageName)
        assertEquals("Chrome", deserializedLaunch.appLabel)

        val aiChat = ActionType.OpenAiChat
        assertEquals("OPEN_AI_CHAT", aiChat.serialize())
        assertEquals(ActionType.OpenAiChat, ActionType.deserialize(aiChat.serialize()))

        val torch = ActionType.ToggleFlashlight
        assertEquals("TOGGLE_FLASHLIGHT", torch.serialize())
        assertEquals(ActionType.ToggleFlashlight, ActionType.deserialize(torch.serialize()))

        val back = ActionType.GlobalBack
        assertEquals("GLOBAL_BACK", back.serialize())
        assertEquals(ActionType.GlobalBack, ActionType.deserialize(back.serialize()))

        val recentsSystem = ActionType.OpenRecentsSystem
        assertEquals("OPEN_RECENTS_SYSTEM", recentsSystem.serialize())
        assertEquals(ActionType.OpenRecentsSystem, ActionType.deserialize(recentsSystem.serialize()))
        assertEquals("Open Recents (System)", recentsSystem.displayName())
    }

    @Test
    fun testAppSwitchPhysicalKey() {
        val appSwitch = PhysicalKey.APP_SWITCH
        assertEquals(580, appSwitch.scancode)
        assertEquals(android.view.KeyEvent.KEYCODE_APP_SWITCH, appSwitch.defaultKeyCode)
        assertEquals(PhysicalKey.APP_SWITCH, PhysicalKey.fromKeyCode(android.view.KeyEvent.KEYCODE_APP_SWITCH))
        assertEquals(PhysicalKey.APP_SWITCH, PhysicalKey.fromScancode(580))
        assertFalse("APP_SWITCH must not be enabled for static Magisk .kl remapping", appSwitch.isStaticRemapSafe)
    }

    @Test
    fun testFivePhysicalButtonsIdentityAndHexScancodes() {
        // Upper-left: KEY_F1 (0x003B)
        val f1 = PhysicalKey.F1
        assertEquals(59, f1.scancode)
        assertEquals("0x003B", f1.hexScancode)
        assertEquals(android.view.KeyEvent.KEYCODE_F1, f1.defaultKeyCode)
        assertEquals("KEY_F1", f1.linuxKeyName)
        assertTrue(f1.keyName.contains("Upper-left"))
        assertTrue("F1 is safe for static Magisk remapping", f1.isStaticRemapSafe)

        // Upper-right: KEY_CAMERA (0x00D4)
        val camera = PhysicalKey.CAMERA
        assertEquals(212, camera.scancode)
        assertEquals("0x00D4", camera.hexScancode)
        assertEquals(android.view.KeyEvent.KEYCODE_CAMERA, camera.defaultKeyCode)
        assertEquals("KEY_CAMERA", camera.linuxKeyName)
        assertTrue(camera.keyName.contains("Upper-right"))
        assertTrue("CAMERA is safe for static Magisk remapping", camera.isStaticRemapSafe)

        // Center / select: KEY_ENTER (0x001C)
        val center = PhysicalKey.KEY_ENTER
        assertEquals(28, center.scancode)
        assertEquals("0x001C", center.hexScancode)
        assertEquals(android.view.KeyEvent.KEYCODE_ENTER, center.defaultKeyCode)
        assertEquals("KEY_ENTER", center.linuxKeyName)
        assertEquals("Center / Enter", center.keyName)
        assertFalse("KEY_ENTER must not be statically remapped in .kl to protect system navigation", center.isStaticRemapSafe)

        // Lower-left: KEY_F2 (0x003C)
        val f2 = PhysicalKey.F2
        assertEquals(60, f2.scancode)
        assertEquals("0x003C", f2.hexScancode)
        assertEquals(android.view.KeyEvent.KEYCODE_F2, f2.defaultKeyCode)
        assertEquals("KEY_F2", f2.linuxKeyName)
        assertTrue(f2.keyName.contains("Lower-left"))
        assertTrue("F2 is safe for static Magisk remapping", f2.isStaticRemapSafe)

        // Lower-right: KEY_F3 (0x003D)
        val f3 = PhysicalKey.F3
        assertEquals(61, f3.scancode)
        assertEquals("0x003D", f3.hexScancode)
        assertEquals(android.view.KeyEvent.KEYCODE_F3, f3.defaultKeyCode)
        assertEquals("KEY_F3", f3.linuxKeyName)
        assertTrue(f3.keyName.contains("Lower-right"))
        assertTrue("F3 is safe for static Magisk remapping", f3.isStaticRemapSafe)
    }

    @Test
    fun testCenterAndKeyEnterResolveToOnePhysicalButton() {
        // Direct aliases resolve to KEY_ENTER
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.CENTER)
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.ENTER)

        // Android KeyCode resolution (both ENTER and DPAD_CENTER resolve to KEY_ENTER)
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.fromKeyCode(android.view.KeyEvent.KEYCODE_ENTER))
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.fromKeyCode(android.view.KeyEvent.KEYCODE_DPAD_CENTER))

        // Scancode resolution (decimal 28 and hex 0x001C)
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.fromScancode(28))
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.fromScancode(0x001C))

        // Linux key name resolution
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.fromLinuxName("KEY_ENTER"))
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.fromLinuxName("CENTER"))
        assertEquals(PhysicalKey.KEY_ENTER, PhysicalKey.fromLinuxName("ENTER"))

        // Ensure exactly ONE center button entry exists in PhysicalKey.entries
        val centerCount = PhysicalKey.entries.count { it == PhysicalKey.KEY_ENTER || it.scancode == 28 }
        assertEquals("CENTER and KEY_ENTER must not exist as two separate enum entries", 1, centerCount)
    }

    @Test
    fun testExistingPhysicalKeysPreserved() {
        val scancodes = PhysicalKey.entries.map { it.scancode }
        assertTrue("CALL key must be preserved", scancodes.contains(169))
        assertTrue("APP_SWITCH key must be preserved", scancodes.contains(580))
        assertTrue("F4 key must be preserved", scancodes.contains(62))
        assertTrue("STAR key must be preserved", scancodes.contains(522))
        assertTrue("POUND key must be preserved", scancodes.contains(523))
    }

    @Test
    fun testDirectionalKeysPreservedAndProtected() {
        assertEquals("DPAD_UP", BASELINE_MTK_KPD_MAPPINGS[103])
        assertEquals("DPAD_LEFT", BASELINE_MTK_KPD_MAPPINGS[105])
        assertEquals("DPAD_RIGHT", BASELINE_MTK_KPD_MAPPINGS[106])
        assertEquals("DPAD_DOWN", BASELINE_MTK_KPD_MAPPINGS[108])
        assertEquals("ENTER", BASELINE_MTK_KPD_MAPPINGS[28])

        val content = HardwareRemapManager.generateKeyLayoutContent(emptyMap())
        assertTrue(content.contains("key 103     DPAD_UP"))
        assertTrue(content.contains("key 105     DPAD_LEFT"))
        assertTrue(content.contains("key 106     DPAD_RIGHT"))
        assertTrue(content.contains("key 108     DPAD_DOWN"))
        assertTrue(content.contains("key 28     ENTER"))

        // Validation rejects corrupting DPAD_UP
        val overwrittenDpad = content.replace("key 103     DPAD_UP", "key 103     CAMERA")
        assertFalse(HardwareRemapManager.validateKeyLayoutContent(overwrittenDpad))

        // Validation rejects corrupting ENTER (scancode 28)
        val overwrittenEnter = content.replace("key 28     ENTER", "key 28     CAMERA")
        assertFalse(HardwareRemapManager.validateKeyLayoutContent(overwrittenEnter))
    }

    @Test
    fun testGenerateAndValidateKeyLayoutContent() {
        val customMappings = mapOf(169 to "VOLUME_MUTE", 59 to "TAB")
        val content = HardwareRemapManager.generateKeyLayoutContent(customMappings)

        assertTrue(HardwareRemapManager.validateKeyLayoutContent(content))
        assertTrue(content.contains("key 169     VOLUME_MUTE"))
        assertTrue(content.contains("key 59     TAB"))
        assertTrue(content.contains("key 28     ENTER"))
        assertTrue(content.contains("key 172     HOME"))
        assertTrue(content.contains("key 158     BACK"))
        assertTrue(content.contains("key 103     DPAD_UP"))
    }

    @Test
    fun testValidationRejectsCorruptedKeyLayout() {
        // Missing critical keys (e.g. HOME key 172 missing)
        val corruptedContent = "key 169 CALL\nkey 28 ENTER"
        assertFalse(HardwareRemapManager.validateKeyLayoutContent(corruptedContent))

        // Attempting to overwrite HOME with custom key
        val overwrittenHome = HardwareRemapManager.generateKeyLayoutContent(emptyMap())
            .replace("key 172     HOME", "key 172     CAMERA")
        assertFalse(HardwareRemapManager.validateKeyLayoutContent(overwrittenHome))
    }
}
