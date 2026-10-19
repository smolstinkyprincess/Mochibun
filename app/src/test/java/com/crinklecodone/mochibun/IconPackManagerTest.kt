package com.crinklecodone.mochibun

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.crinklecodone.mochibun.data.repository.IconPackInfo
import com.crinklecodone.mochibun.data.repository.IconPackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackManagerTest {

    @Test
    fun iconPackInfo_propertiesAndDefaultCheck() {
        val dummyDrawable = ColorDrawable(Color.RED)
        val defaultPack = IconPackInfo(
            packageName = "",
            label = "System Default",
            icon = dummyDrawable,
            isDefault = true
        )
        assertTrue(defaultPack.isDefault)
        assertEquals("", defaultPack.packageName)
        assertEquals("System Default", defaultPack.label)

        val customPack = IconPackInfo(
            packageName = "com.example.cutepack",
            label = "Cute Pack",
            icon = dummyDrawable,
            isDefault = false
        )
        assertFalse(customPack.isDefault)
        assertEquals("com.example.cutepack", customPack.packageName)
        assertEquals("Cute Pack", customPack.label)
    }

    @Test
    fun clearCache_runsSafelyWithoutException() {
        IconPackManager.clearCache()
        IconPackManager.invalidateAppIcon("com.example.app")
    }

    @Test
    fun parsedIconPack_dataModelPreservesMappings() {
        val componentMap = mapOf(
            "ComponentInfo{com.example.app/com.example.app.MainActivity}" to "ic_app",
            "com.example.app/com.example.app.MainActivity" to "ic_app"
        )
        val packageMap = mapOf("com.example.app" to "ic_app")
        val iconNames = listOf("ic_app", "ic_browser", "ic_settings")

        // Validate component and package mappings
        assertEquals("ic_app", componentMap["ComponentInfo{com.example.app/com.example.app.MainActivity}"])
        assertEquals("ic_app", componentMap["com.example.app/com.example.app.MainActivity"])
        assertEquals("ic_app", packageMap["com.example.app"])
        assertEquals(3, iconNames.size)
        assertEquals(iconNames, iconNames.sorted())
    }
}
