package one.crinklecod.mochibun

import android.view.KeyEvent
import one.crinklecod.mochibun.data.prefs.PreferencesManager
import one.crinklecod.mochibun.data.repository.KaomojiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaomojiRepositoryTest {

    @Test
    fun testKaomojiTriggerIsPoundAndKeyFormatting() {
        // Trigger is permanently bound to '#'
        assertTrue(PreferencesManager.matchesKaomojiTrigger(KeyEvent.KEYCODE_POUND))
        assertFalse(PreferencesManager.matchesKaomojiTrigger(KeyEvent.KEYCODE_STAR))
        assertEquals("#", PreferencesManager.getKaomojiTriggerBadge())

        // Verify badge and label formatting for physical and arbitrary keys
        assertEquals("#", PreferencesManager.formatKeyBadge(KeyEvent.KEYCODE_POUND))
        assertEquals("F1", PreferencesManager.formatKeyBadge(KeyEvent.KEYCODE_F1))
        assertEquals("F4", PreferencesManager.formatKeyBadge(KeyEvent.KEYCODE_F4, 62))
        assertEquals("5", PreferencesManager.formatKeyBadge(KeyEvent.KEYCODE_5))
        assertEquals("Key 5", PreferencesManager.formatKeyName(KeyEvent.KEYCODE_5))
        assertEquals("Upper-left (F1)", PreferencesManager.formatKeyName(KeyEvent.KEYCODE_F1))
    }

    @Test
    fun testKaomojiCategoriesIntegrity() {
        val categories = KaomojiRepository.categories
        assertEquals(8, categories.size)

        val expectedOrder = listOf("happy", "hype", "love", "cute", "sleepy", "shy", "surprised", "silly")
        assertEquals(expectedOrder, categories.map { it.id })

        val shocked = categories.first { it.id == "surprised" }
        assertEquals("Shock", shocked.shortLabel)

        val hype = categories.first { it.id == "hype" }
        assertEquals("Hype / Sparkle", hype.title)
        assertEquals("Hype", hype.shortLabel)
        assertEquals("٩(ˊᗜˋ*)و", hype.icon)

        val shy = categories.first { it.id == "shy" }
        assertEquals("Shy / Blush", shy.title)
        assertEquals("Shy", shy.shortLabel)
        assertEquals("⁄ ⁄•⁄ω⁄•⁄ ⁄", shy.icon)

        categories.forEach { category ->
            assertTrue("Category ${category.id} has empty title", category.title.isNotBlank())
            assertTrue("Category ${category.id} has empty shortLabel", category.shortLabel.isNotBlank())
            assertTrue(
                "Category ${category.id} shortLabel '${category.shortLabel}' exceeds 6 chars",
                category.shortLabel.length <= 6
            )
            assertFalse(
                "Category ${category.id} shortLabel '${category.shortLabel}' should be single word",
                category.shortLabel.any { it.isWhitespace() }
            )
            assertTrue("Category ${category.id} has empty icon", category.icon.isNotBlank())
            assertTrue("Category ${category.id} has no items", category.items.isNotEmpty())
            assertTrue(
                "Category ${category.id} has ${category.items.size} items (max ${KaomojiRepository.MAX_ITEMS_PER_CATEGORY})",
                category.items.size <= KaomojiRepository.MAX_ITEMS_PER_CATEGORY
            )
            assertEquals(
                "Category ${category.id} has duplicate items",
                category.items.distinct().size,
                category.items.size
            )
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

        val hype = KaomojiRepository.categories.first { it.id == "hype" }
        assertTrue(hype.items.contains("٩(ˊᗜˋ*)و"))

        val love = KaomojiRepository.categories.first { it.id == "love" }
        assertTrue(love.items.contains("( ˘ ³˘)♥"))
        assertTrue(love.items.contains("(づ๑•ᴗ•๑)づ"))

        val cute = KaomojiRepository.categories.first { it.id == "cute" }
        assertTrue(cute.items.contains("૮₍ ˶•⤙•˶ ₎ა"))

        val sleepy = KaomojiRepository.categories.first { it.id == "sleepy" }
        assertTrue(sleepy.items.contains("( ᴗ͈ˬᴗ͈ )zzZ"))

        val shy = KaomojiRepository.categories.first { it.id == "shy" }
        assertTrue(shy.items.contains("(⁄ ⁄•⁄ω⁄•⁄ ⁄)"))

        val surprised = KaomojiRepository.categories.first { it.id == "surprised" }
        assertTrue(surprised.items.contains("(•ิ_•ิ)?"))

        val silly = KaomojiRepository.categories.first { it.id == "silly" }
        assertTrue(silly.items.contains("ദ്ദി(˵ •̀ ᴗ - ˵ )"))
    }
}
