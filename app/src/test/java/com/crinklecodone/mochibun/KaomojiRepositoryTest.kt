package com.crinklecodone.mochibun

import android.view.KeyEvent
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.data.repository.KaomojiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaomojiRepositoryTest {

    @Test
    fun testKaomojiTriggerDefaultsAndArbitraryKeyMatching() {
        assertEquals("pref_kaomoji_trigger_keycode", PreferencesManager.KEY_KAOMOJI_TRIGGER_KEYCODE)
        assertEquals(KeyEvent.KEYCODE_POUND, PreferencesManager.DEFAULT_KAOMOJI_TRIGGER_KEYCODE)
        assertTrue(PreferencesManager.DEFAULT_KAOMOJI_TRIGGER_LONG_PRESS)

        // Default matches pound key
        assertTrue(PreferencesManager.matchesKaomojiTrigger(KeyEvent.KEYCODE_POUND, 0))
        assertFalse(PreferencesManager.matchesKaomojiTrigger(KeyEvent.KEYCODE_STAR, 0))

        // Verify badge and label formatting for physical and arbitrary keys
        assertEquals("#", PreferencesManager.formatKaomojiKeyBadge(KeyEvent.KEYCODE_POUND))
        assertEquals("F1", PreferencesManager.formatKaomojiKeyBadge(KeyEvent.KEYCODE_F1))
        assertEquals("F4", PreferencesManager.formatKaomojiKeyBadge(KeyEvent.KEYCODE_F4, 62))
        assertEquals("5", PreferencesManager.formatKaomojiKeyBadge(KeyEvent.KEYCODE_5))
        assertEquals("Key 5", PreferencesManager.formatKaomojiKeyName(KeyEvent.KEYCODE_5))
        assertEquals("Upper-left (F1)", PreferencesManager.formatKaomojiKeyName(KeyEvent.KEYCODE_F1))
    }

    @Test
    fun testKaomojiCategoriesIntegrity() {
        val categories = KaomojiRepository.categories
        assertEquals(6, categories.size)

        val ids = categories.map { it.id }
        assertTrue(ids.contains("happy"))
        assertTrue(ids.contains("love"))
        assertTrue(ids.contains("cute"))
        assertTrue(ids.contains("sleepy"))
        assertTrue(ids.contains("surprised"))
        assertTrue(ids.contains("silly"))

        categories.forEach { category ->
            assertTrue("Category ${category.id} has empty title", category.title.isNotBlank())
            assertTrue("Category ${category.id} has empty shortLabel", category.shortLabel.isNotBlank())
            assertTrue("Category ${category.id} has empty icon", category.icon.isNotBlank())
            assertTrue("Category ${category.id} has no items", category.items.isNotEmpty())
            category.items.forEach { kaomoji ->
                assertTrue("Kaomoji item in ${category.id} is blank", kaomoji.isNotBlank())
            }
        }
    }

    @Test
    fun testRequiredKaomojiEntriesPresent() {
        val happy = KaomojiRepository.categories.first { it.id == "happy" }
        assertTrue(happy.items.contains("( ˶ˆᗜˆ˵ )"))
        assertTrue(happy.items.contains("(๑ > ᴗ < ๑)"))

        val love = KaomojiRepository.categories.first { it.id == "love" }
        assertTrue(love.items.contains("( ˘ ³˘)♥"))
        assertTrue(love.items.contains("(づ๑•ᴗ•๑)づ"))

        val cute = KaomojiRepository.categories.first { it.id == "cute" }
        assertTrue(cute.items.contains("૮₍ ˶•⤙•˶ ₎ა"))

        val sleepy = KaomojiRepository.categories.first { it.id == "sleepy" }
        assertTrue(sleepy.items.contains("( ᴗ͈ˬᴗ͈ )zzZ"))

        val surprised = KaomojiRepository.categories.first { it.id == "surprised" }
        assertTrue(surprised.items.contains("(•ิ_•ิ)?"))

        val silly = KaomojiRepository.categories.first { it.id == "silly" }
        assertTrue(silly.items.contains("ദ്ദി(˵ •̀ ᴗ - ˵ )"))
    }
}
