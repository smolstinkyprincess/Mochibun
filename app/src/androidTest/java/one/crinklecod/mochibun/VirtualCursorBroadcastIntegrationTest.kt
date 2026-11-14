package one.crinklecod.mochibun

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import one.crinklecod.mochibun.service.VirtualCursorService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualCursorBroadcastIntegrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var service: VirtualCursorService? = null

    @Before
    fun startCursorService() {
        HardwareRemapManager.init(context)
        // The service only runs while the cursor is visible, which needs the overlay window.
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"
        ).close()
        VirtualCursorService.start(context, showCursor = true)
        waitUntil { VirtualCursorService.isServiceRunning && VirtualCursorService.isCursorVisible }
        // The flags flip mid-way through the start command; let the main thread finish it.
        settleMainThread()
        service = getServiceInstance()
    }

    @After
    fun stopCursorService() {
        context.stopService(Intent(context, VirtualCursorService::class.java))
        waitUntil { !VirtualCursorService.isServiceRunning }
        service = null
    }

    @Test
    fun toggleBroadcastHandlesOverlayAvailabilityAndMalformedOrUnauthorizedInput() {
        val currentService = requireNotNull(service)
        assertEquals(
            "android.permission.INTERNAL_SYSTEM_WINDOW",
            MochibunSystemActions.SYSTEM_SERVER_BROADCAST_PERMISSION
        )

        // Started visible: foreground service running with the overlay attached.
        assertTrue(VirtualCursorService.isServiceRunning)
        assertTrue(VirtualCursorService.isCursorVisible)
        val isOverlayAttached = currentService.javaClass
            .getDeclaredField("isOverlayAttached")
            .apply { isAccessible = true }
            .getBoolean(currentService)
        assertTrue(isOverlayAttached)

        // Unauthorized senders cannot toggle the cursor.
        context.sendBroadcast(Intent(MochibunSystemActions.ACTION_TOGGLE_CURSOR).setPackage(context.packageName))
        context.sendBroadcast(
            Intent(MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER).setPackage(context.packageName)
        )
        settleMainThread()
        assertTrue(VirtualCursorService.isCursorVisible)

        // Malformed authorized input is ignored without killing the service.
        sendAuthorizedBroadcast(Intent(MochibunSystemActions.ACTION_CURSOR_KEY))
        sendAuthorizedBroadcast(Intent(MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER))
        assertTrue(VirtualCursorService.isServiceRunning)
        assertTrue(VirtualCursorService.isCursorVisible)

        // Hiding the cursor stops the foreground service (and removes its notification).
        sendAuthorizedBroadcast(Intent(MochibunSystemActions.ACTION_TOGGLE_CURSOR))
        assertFalse(VirtualCursorService.isCursorVisible)
        waitUntil { !VirtualCursorService.isServiceRunning }
        assertFalse(VirtualCursorService.isServiceRunning)

        // Toggling again starts it back up with the cursor shown.
        VirtualCursorService.toggleCursor(context)
        waitUntil { VirtualCursorService.isServiceRunning && VirtualCursorService.isCursorVisible }
        assertTrue(VirtualCursorService.isServiceRunning)
        assertTrue(VirtualCursorService.isCursorVisible)
    }

    @Test
    fun directionalBroadcastsMoveAndClampWithinAtM120Envelope() {
        val currentService = requireNotNull(service)
        val physics = currentService.javaClass.getDeclaredField("physics")
            .apply { isAccessible = true }
            .get(currentService)
        physics.javaClass.getMethod("setBounds", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            .invoke(physics, 480f, 800f)

        assertTrue(VirtualCursorService.isCursorVisible)
        physics.javaClass.getMethod("setPosition", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            .invoke(physics, 470f, 790f)

        sendCursorKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN)
        sendCursorKey(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN)
        val movedX = physics.javaClass.getMethod("getPosX").invoke(physics) as Float
        val movedY = physics.javaClass.getMethod("getPosY").invoke(physics) as Float
        assertTrue("Right key should move cursor", movedX > 470f)
        assertTrue("Down key should move cursor", movedY > 790f)

        sendCursorKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_UP)
        sendCursorKey(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP)
        physics.javaClass.getMethod("setPosition", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType)
            .invoke(physics, 480f, 800f)
        sendCursorKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN)
        sendCursorKey(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN)

        val clampedX = physics.javaClass.getMethod("getPosX").invoke(physics) as Float
        val clampedY = physics.javaClass.getMethod("getPosY").invoke(physics) as Float
        assertTrue(clampedX in 0f..480f)
        assertTrue(clampedY in 0f..800f)
        assertEquals(480f, clampedX)
        assertEquals(800f, clampedY)
    }

    private fun sendCursorKey(keyCode: Int, action: Int) {
        sendAuthorizedBroadcast(
            Intent(MochibunSystemActions.ACTION_CURSOR_KEY)
                .putExtra(MochibunSystemActions.EXTRA_KEY_CODE, keyCode)
                .putExtra(MochibunSystemActions.EXTRA_ACTION, action)
                .putExtra(MochibunSystemActions.EXTRA_REPEAT_COUNT, 0)
        )
    }

    private fun sendAuthorizedBroadcast(intent: Intent) {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            MochibunSystemActions.SYSTEM_SERVER_BROADCAST_PERMISSION
        )
        try {
            context.sendBroadcast(intent.setPackage(context.packageName))
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
        settleMainThread()
    }

    /**
     * Lets an async broadcast reach the main thread. Not waitForIdleSync(): the visible cursor
     * overlay animates continuously, so the main looper never goes idle.
     */
    private fun settleMainThread() {
        SystemClock.sleep(150)
        instrumentation.runOnMainSync {}
    }

    private fun getServiceInstance(): VirtualCursorService =
        requireNotNull(VirtualCursorService.runningInstance)

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(10)
        }
        assertTrue("Timed out waiting for cursor service state", condition())
    }
}
