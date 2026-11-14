package one.crinklecod.mochibun

import one.crinklecod.mochibun.service.VirtualCursorService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class VirtualCursorServiceLifecycleTest {

    private var controller: ServiceController<VirtualCursorService>? = null

    @After
    fun tearDown() {
        controller?.destroy()
    }

    @Test
    fun keepRunningOnlyWhileCursorVisible() {
        assertTrue(VirtualCursorService.shouldKeepRunning(cursorVisible = true))
        assertFalse(VirtualCursorService.shouldKeepRunning(cursorVisible = false))
    }

    @Test
    fun startWithHiddenCursorStopsForegroundAndSelf() {
        val context = RuntimeEnvironment.getApplication()
        val intent = VirtualCursorService.showIntent(context, showCursor = false)
        val ctrl = Robolectric.buildService(VirtualCursorService::class.java, intent).create()
        controller = ctrl
        ctrl.startCommand(0, 1)

        val shadow = shadowOf(ctrl.get())
        assertFalse(VirtualCursorService.isCursorVisible)
        assertTrue(shadow.isForegroundStopped)
        assertNull(shadow.lastForegroundNotification)
        assertTrue(shadow.isStoppedBySelf)
    }

    @Test
    fun hidingVisibleCursorStopsForegroundAndSelf() {
        val context = RuntimeEnvironment.getApplication()
        val ctrl = Robolectric.buildService(
            VirtualCursorService::class.java,
            VirtualCursorService.showIntent(context, showCursor = true),
        ).create()
        controller = ctrl
        val service = ctrl.get()
        val shadow = shadowOf(service)

        service.setCursorVisibility(true)
        assertTrue(VirtualCursorService.isCursorVisible)
        assertFalse(shadow.isStoppedBySelf)

        service.setCursorVisibility(false)
        assertFalse(VirtualCursorService.isCursorVisible)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
    }
}
