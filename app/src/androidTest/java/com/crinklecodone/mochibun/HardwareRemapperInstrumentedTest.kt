package com.crinklecodone.mochibun

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.crinklecodone.mochibun.core.system.RootShell
import com.crinklecodone.mochibun.core.shell.RootStatus
import com.crinklecodone.mochibun.core.system.RootTouchInjector
import com.crinklecodone.mochibun.data.model.ActionType
import com.crinklecodone.mochibun.data.model.ButtonActionConfig
import com.crinklecodone.mochibun.data.model.PhysicalKey
import com.crinklecodone.mochibun.data.model.StaticModuleStatus
import com.crinklecodone.mochibun.data.repository.HardwareRemapManager
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
        Log.i("MOCHIBUN_TEST", "su binary present = $present")
        assertTrue("su binary should be present on rooted device", present)
    }

    @Test
    fun testRootRequestAndExecution() = runBlocking {
        Log.i("MOCHIBUN_TEST", "Requesting root access...")
        val status = RootShell.requestRoot()
        Log.i("MOCHIBUN_TEST", "Root status = $status")
        assertTrue("Root must be granted for root-capable features", status is RootStatus.Granted)

        val idResult = RootShell.execute("id")
        Log.i("MOCHIBUN_TEST", "id command stdout: ${idResult.stdout}")
        assertTrue("id command must return uid=0", idResult.stdout.any { it.contains("uid=0") })
    }

    @Test
    fun testPersistentDaemonReuseAndLatency() = runBlocking {
        // Warm up: initial shell spawn
        val warmUpStart = System.currentTimeMillis()
        val warmUpRes = RootShell.execute("echo 'warmup'")
        val warmUpDuration = System.currentTimeMillis() - warmUpStart
        Log.i("MOCHIBUN_TEST", "Initial shell connection (cold spawn) took: ${warmUpDuration}ms, success=${warmUpRes.isSuccess}")

        // Test subsequent commands on the multiplexed persistent daemon
        val iterations = 5
        val times = mutableListOf<Long>()

        for (i in 1..iterations) {
            val start = System.currentTimeMillis()
            val res = RootShell.execute("echo 'ping-$i'")
            val durationMs = System.currentTimeMillis() - start
            times.add(durationMs)
            assertTrue("Ping $i must succeed", res.isSuccess)
            assertEquals("ping-$i", res.stdout.firstOrNull())
        }

        val avgDurationMs = times.average()
        Log.i("MOCHIBUN_TEST", "Multiplexed subsequent execution times: $times (Average = $avgDurationMs ms)")
        assertTrue("Multiplexed persistent shell commands must be fast (< 100ms)", avgDurationMs < 100.0)
    }

    @Test
    fun testRootTouchInjectorAsync() {
        Log.i("MOCHIBUN_TEST", "Testing RootTouchInjector.tapAsync...")
        // Non-blocking tap at off-screen coordinate (safe coordinate)
        RootTouchInjector.tapAsync(240, 400)
        Log.i("MOCHIBUN_TEST", "tapAsync dispatched successfully without blocking")
    }

    @Test
    fun testMagiskModuleStagingAndDisablingLifecycle() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Stage a test mapping (e.g. Call key 169 -> VOLUME_MUTE)
        val testMappings = mapOf(169 to "VOLUME_MUTE")
        val stageResult = HardwareRemapManager.stageAndApplyStaticMapping(context, testMappings)
        Log.i("MOCHIBUN_TEST", "Staging result: $stageResult")
        assertTrue("Staging should succeed", stageResult.isSuccess)

        // 2. Reconcile module status (should be StagedPendingReboot or Active)
        val status1 = HardwareRemapManager.reconcileModuleStatus()
        Log.i("MOCHIBUN_TEST", "Status after stage: ${status1.description()}")
        assertTrue(status1 is StaticModuleStatus.StagedPendingReboot || status1 is StaticModuleStatus.Active)

        // 3. Test Disabling the module via /disable flag
        val disableResult = HardwareRemapManager.disableModule()
        Log.i("MOCHIBUN_TEST", "Disable result: $disableResult")
        assertTrue(disableResult.isSuccess)

        val status2 = HardwareRemapManager.reconcileModuleStatus()
        Log.i("MOCHIBUN_TEST", "Status after disable: ${status2.description()}")
        assertTrue("Status must be Disabled", status2 is StaticModuleStatus.Disabled)

        // 4. Test Cleaning up / Removing the test module
        val removeResult = HardwareRemapManager.removeModule()
        Log.i("MOCHIBUN_TEST", "Remove result: $removeResult")
        assertTrue(removeResult.isSuccess)

        val status3 = HardwareRemapManager.reconcileModuleStatus()
        Log.i("MOCHIBUN_TEST", "Status after remove: ${status3.description()}")
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
        Log.i("MOCHIBUN_TEST", "Saved F1 key action -> Launch Settings")
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
        Log.i("MOCHIBUN_TEST", "Successfully configured and read back OpenRecentsSystem on APP_SWITCH")
    }

    @Test
    fun testRootRecentsCommandExecution() = runBlocking {
        Log.i("MOCHIBUN_TEST", "Testing am start RecentsActivity root command...")
        val result = RootShell.execute("am start -n com.crinklecodone.mochibun/com.android.quickstep.RecentsActivity")
        Log.i("MOCHIBUN_TEST", "Command exitCode=${result.exitCode}, stdout=${result.stdout}, stderr=${result.stderr}")
        assertTrue("am start command should return exit code 0", result.isSuccess)
        assertFalse("am start output should not report error", result.stdout.any { it.startsWith("Error:") })

        // Verify debounce logic
        val firstAllowed = !com.android.quickstep.RecentsActivity.shouldThrottleLaunch()
        val secondThrottled = com.android.quickstep.RecentsActivity.shouldThrottleLaunch()
        Log.i("MOCHIBUN_TEST", "First allowed = $firstAllowed, Second throttled = $secondThrottled")
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
        Log.i("MOCHIBUN_TEST", "Verified fallback preference persistence across updates")
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
        Log.i("MOCHIBUN_TEST", "Verified APP_SWITCH defaults to GlobalRecents")
    }

    @Test
    fun testCenterEnterActionPersistence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enterKey = PhysicalKey.KEY_ENTER
        assertEquals("Center / Enter", enterKey.keyName)
        assertEquals(28, enterKey.scancode)
        assertEquals("0x001C", enterKey.hexScancode)

        // Save a test action configuration on KEY_ENTER (e.g. double press flashlight)
        val config = ButtonActionConfig(
            singlePressAction = ActionType.None,
            doublePressAction = ActionType.ToggleFlashlight,
            longPressAction = ActionType.GlobalBack
        )
        HardwareRemapManager.saveButtonActionConfig(context, enterKey, config)

        val readBack = HardwareRemapManager.getButtonActionConfig(context, enterKey)
        assertEquals(ActionType.None, readBack.singlePressAction)
        assertEquals(ActionType.ToggleFlashlight, readBack.doublePressAction)
        assertEquals(ActionType.GlobalBack, readBack.longPressAction)

        // Clean up preference
        val emptyConfig = ButtonActionConfig()
        HardwareRemapManager.saveButtonActionConfig(context, enterKey, emptyConfig)
        Log.i("MOCHIBUN_TEST", "Verified KEY_ENTER configuration and restoration")
    }
}
