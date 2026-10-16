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
