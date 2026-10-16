package com.tnyemii.mochibun

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwareRemapperInstrumentedTest {

    @Test
    fun testSuBinaryPresence() {
        val present = RootShell.isSuBinaryPresent()
        println("MOCHIBUN_DEVICE_TEST: su binary present = $present")
        assertTrue("su binary should be present on rooted device", present)
    }

    @Test
    fun testRootRequestAndExecution() = runBlocking {
        println("MOCHIBUN_DEVICE_TEST: Requesting root access...")
        val status = RootShell.requestRoot()
        println("MOCHIBUN_DEVICE_TEST: Root status = $status")
        assertTrue("Root must be granted for root-capable features", status is RootStatus.Granted)

        val idResult = RootShell.execute("id")
        println("MOCHIBUN_DEVICE_TEST: id command stdout: ${idResult.stdout}")
        assertTrue("id command must return uid=0", idResult.stdout.any { it.contains("uid=0") })
    }

    @Test
    fun testMagiskModuleStagingAndDisablingLifecycle() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Stage a test mapping (e.g. Call key 169 -> VOLUME_MUTE)
        val testMappings = mapOf(169 to "VOLUME_MUTE")
        val stageResult = HardwareRemapManager.stageAndApplyStaticMapping(context, testMappings)
        println("MOCHIBUN_DEVICE_TEST: Staging result: $stageResult")
        assertTrue("Staging should succeed", stageResult.isSuccess)

        // 2. Reconcile module status (should be StagedPendingReboot or Active)
        val status1 = HardwareRemapManager.reconcileModuleStatus()
        println("MOCHIBUN_DEVICE_TEST: Status after stage: ${status1.description()}")
        assertTrue(status1 is StaticModuleStatus.StagedPendingReboot || status1 is StaticModuleStatus.Active)

        // 3. Test Disabling the module via /disable flag
        val disableResult = HardwareRemapManager.disableModule()
        println("MOCHIBUN_DEVICE_TEST: Disable result: $disableResult")
        assertTrue(disableResult.isSuccess)

        val status2 = HardwareRemapManager.reconcileModuleStatus()
        println("MOCHIBUN_DEVICE_TEST: Status after disable: ${status2.description()}")
        assertTrue("Status must be Disabled", status2 is StaticModuleStatus.Disabled)

        // 4. Test Cleaning up / Removing the test module
        val removeResult = HardwareRemapManager.removeModule()
        println("MOCHIBUN_DEVICE_TEST: Remove result: $removeResult")
        assertTrue(removeResult.isSuccess)

        val status3 = HardwareRemapManager.reconcileModuleStatus()
        println("MOCHIBUN_DEVICE_TEST: Status after remove: ${status3.description()}")
        assertTrue("Status must be NotInstalled", status3 is StaticModuleStatus.NotInstalled)
    }

    @Test
    fun testSetF1KeyAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = ButtonActionConfig(
            singlePressAction = ActionType.LaunchApp("com.android.settings", "Settings"),
            doublePressAction = ActionType.None,
            longPressAction = ActionType.None
        )
        HardwareRemapManager.saveButtonActionConfig(context, PhysicalKey.F1, config)
        println("MOCHIBUN_DEVICE_TEST: Saved F1 key action -> Launch Settings")
    }
}
