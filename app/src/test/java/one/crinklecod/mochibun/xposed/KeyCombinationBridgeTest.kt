package one.crinklecod.mochibun.xposed

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyCombinationBridgeTest {

    private class FakeKeyCombinationManager(private val resultOnDown: Boolean) {
        var callCount = 0
        var lastEvent: KeyEvent? = null
        var lastInteractive: Boolean? = null

        @Suppress("unused")
        fun interceptKey(event: KeyEvent, interactive: Boolean): Boolean {
            callCount++
            lastEvent = event
            lastInteractive = interactive
            return resultOnDown
        }
    }

    private open class BaseFakePhoneWindowManager(
        @Suppress("unused") private val mKeyCombinationManager: Any?
    )

    private class DerivedFakePhoneWindowManager(
        manager: Any?
    ) : BaseFakePhoneWindowManager(manager)

    private class MissingFieldPhoneWindowManager

    private class ThrowingKeyCombinationManager {
        @Suppress("unused")
        fun interceptKey(event: KeyEvent, interactive: Boolean): Boolean {
            throw IllegalStateException("boom")
        }
    }

    @Test
    fun interactiveFlagExtractionMatchesWindowManagerPolicy() {
        assertTrue(KeyCombinationBridge.isInteractive(0x20000000))
        assertTrue(KeyCombinationBridge.isInteractive(0x22000000))
        assertFalse(KeyCombinationBridge.isInteractive(0x00000000))
        assertFalse(KeyCombinationBridge.isInteractive(0x02000000))
    }

    @Test
    fun invokesSuperclassFieldAndPassesInteractiveFlag() {
        val manager = FakeKeyCombinationManager(resultOnDown = true)
        val pwm = DerivedFakePhoneWindowManager(manager)
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
        var error: Throwable? = null

        val consumed = KeyCombinationBridge.interceptKey(pwm, downEvent, 0x22000000) { error = it }

        assertTrue(consumed)
        assertEquals(1, manager.callCount)
        assertEquals(downEvent, manager.lastEvent)
        assertEquals(true, manager.lastInteractive)
        assertEquals(null, error)

        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN)
        KeyCombinationBridge.interceptKey(pwm, upEvent, 0x00000000) { error = it }
        assertEquals(2, manager.callCount)
        assertEquals(false, manager.lastInteractive)
        assertEquals(null, error)
    }

    @Test
    fun missingFieldReturnsFalseWithoutThrowing() {
        val pwm = MissingFieldPhoneWindowManager()
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
        var error: Throwable? = null

        val consumed = KeyCombinationBridge.interceptKey(pwm, event, 0x20000000) { error = it }

        assertFalse(consumed)
        assertNotNull(error)
    }

    @Test
    fun throwingInterceptKeyReturnsFalseWithoutThrowing() {
        val pwm = BaseFakePhoneWindowManager(ThrowingKeyCombinationManager())
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
        var error: Throwable? = null

        val consumed = KeyCombinationBridge.interceptKey(pwm, event, 0x20000000) { error = it }

        assertFalse(consumed)
        assertNotNull(error)
    }
}
