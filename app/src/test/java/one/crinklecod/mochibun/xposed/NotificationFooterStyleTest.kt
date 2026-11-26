package one.crinklecod.mochibun.xposed

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import one.crinklecod.mochibun.xposed.systemui.QuickSettingsHooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class NotificationFooterStyleTest {

    @Test
    fun styleNotificationFooterPillAppliesPaddingBorderAndClearAllLabel() {
        val context = RuntimeEnvironment.getApplication()
        val dismissButton = TextView(context).apply {
            text = "Clear all"
            backgroundTintList = ColorStateList.valueOf(Color.BLACK)
            clipToOutline = true
        }
        val manageButton = TextView(context).apply {
            text = "Manage"
            backgroundTintList = ColorStateList.valueOf(Color.BLACK)
            clipToOutline = true
        }

        QuickSettingsHooks.styleNotificationFooterPill(dismissButton, "♡ Clear All ♡")
        QuickSettingsHooks.styleNotificationFooterPill(manageButton)

        assertEquals("♡ Clear All ♡", dismissButton.text.toString())
        assertEquals("Manage", manageButton.text.toString())
        assertNull(dismissButton.backgroundTintList)
        assertNull(manageButton.backgroundTintList)
        assertFalse(dismissButton.clipToOutline)
        assertFalse(manageButton.clipToOutline)
        assertTrue(dismissButton.background is GradientDrawable)
        assertTrue(manageButton.background is GradientDrawable)
        assertTrue(dismissButton.paddingLeft > 0)
        assertEquals(dismissButton.paddingLeft, dismissButton.paddingRight)
        assertTrue(dismissButton.paddingTop > 0)
        assertEquals(dismissButton.paddingTop, dismissButton.paddingBottom)
        assertTrue(manageButton.paddingLeft > 0)
        assertTrue(manageButton.paddingTop > 0)
    }
}
