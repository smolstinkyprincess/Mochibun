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
