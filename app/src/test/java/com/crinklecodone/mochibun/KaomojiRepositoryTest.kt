package com.crinklecodone.mochibun

import com.crinklecodone.mochibun.data.repository.KaomojiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaomojiRepositoryTest {

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
