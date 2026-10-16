package com.tnyemii.mochibun

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testSaveAndReadOpenRecentsSystemAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = ButtonActionConfig(
            singlePressAction = ActionType.OpenRecentsSystem,
            doublePressAction = ActionType.None,
            longPressAction = ActionType.None
        )
        HardwareRemapManager.saveButtonActionConfig(context, PhysicalKey.APP_SWITCH, config)
        val readBack = HardwareRemapManager.getButtonActionConfig(context, PhysicalKey.APP_SWITCH)
        assertEquals("Single press action must be OpenRecentsSystem", ActionType.OpenRecentsSystem, readBack.singlePressAction)
        println("MOCHIBUN_DEVICE_TEST: Successfully configured and read back OpenRecentsSystem on APP_SWITCH")
    }

    @Test
    fun testRootRecentsCommandExecution() = runBlocking {
        println("MOCHIBUN_DEVICE_TEST: Testing am start RecentsActivity root command...")
        val result = RootShell.execute("am start -n com.tnyemii.mochibun/com.android.quickstep.RecentsActivity")
        println("MOCHIBUN_DEVICE_TEST: Command exitCode=${result.exitCode}, stdout=${result.stdout}, stderr=${result.stderr}")
        assertTrue("am start command should return exit code 0", result.isSuccess)
        assertFalse("am start output should not report error", result.stdout.any { it.startsWith("Error:") })

        // Verify debounce logic
        val firstAllowed = !com.android.quickstep.RecentsActivity.shouldThrottleLaunch()
        val secondThrottled = com.android.quickstep.RecentsActivity.shouldThrottleLaunch()
        println("MOCHIBUN_DEVICE_TEST: First allowed = $firstAllowed, Second throttled = $secondThrottled")
        assertTrue("Immediate successive launch must be throttled", secondThrottled)
    }

    @Test
    fun testRootRecentsFallbackPreferencePersistence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Ensure default is false
        HardwareRemapManager.getPrefs(context).edit().remove(HardwareRemapManager.KEY_USE_ROOT_RECENTS_FALLBACK).commit()
        val defaultVal = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        assertFalse("Fallback toggle must default to false (OFF)", defaultVal)

        // Set to true and verify persistence
        HardwareRemapManager.setRootRecentsFallbackEnabled(context, true)
        val enabledVal = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        assertTrue("Fallback toggle must be true after enabling", enabledVal)

        // Set back to false and verify persistence
        HardwareRemapManager.setRootRecentsFallbackEnabled(context, false)
        val disabledVal = HardwareRemapManager.isRootRecentsFallbackEnabled(context)
        assertFalse("Fallback toggle must be false after disabling", disabledVal)
        println("MOCHIBUN_DEVICE_TEST: Verified fallback preference persistence across updates")
    }

    @Test
    fun testAppSwitchDefaultActionIsGlobalRecents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Remove explicit mapping to test default
        HardwareRemapManager.getPrefs(context).edit().remove("action_single_${PhysicalKey.APP_SWITCH.scancode}").commit()

        val config = HardwareRemapManager.getButtonActionConfig(context, PhysicalKey.APP_SWITCH)
        assertEquals(
            "Default single press action for APP_SWITCH must be GlobalRecents",
            ActionType.GlobalRecents,
            config.singlePressAction
        )
        println("MOCHIBUN_DEVICE_TEST: Verified APP_SWITCH defaults to GlobalRecents")
    }
}
