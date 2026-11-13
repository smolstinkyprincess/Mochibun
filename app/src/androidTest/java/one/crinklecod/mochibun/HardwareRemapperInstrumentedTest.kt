package one.crinklecod.mochibun

import android.content.Intent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import one.crinklecod.mochibun.core.util.MochibunSystemActions
import one.crinklecod.mochibun.data.model.ActionType
import one.crinklecod.mochibun.data.model.ButtonActionConfig
import one.crinklecod.mochibun.data.model.PhysicalKey
import one.crinklecod.mochibun.data.repository.HardwareRemapManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwareRemapperInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs by lazy { HardwareRemapManager.getPrefs(context) }
    private val physicalKeys = listOf(PhysicalKey.F1, PhysicalKey.F2, PhysicalKey.CAMERA)
    private var originalKeyPreferences: Map<String, String?> = emptyMap()

    @Before
    fun initializeManagerAndSnapshotMappings() {
        HardwareRemapManager.init(context)
        originalKeyPreferences = physicalKeys
            .flatMap { key ->
                listOf(
                    "action_single_${key.scancode}",
                    "action_double_${key.scancode}",
                    "action_long_${key.scancode}"
                )
            }
            .associateWith { key -> prefs.getString(key, null) }
    }

    @After
    fun restoreMappingsAndCaptureListener() {
        HardwareRemapManager.keyCaptureListener = null
        val editor = prefs.edit()
        for ((key, value) in originalKeyPreferences) {
            if (value == null) editor.remove(key) else editor.putString(key, value)
        }
        editor.commit()
        HardwareRemapManager.reloadActiveConfig(context)
    }

    @Test
    fun physicalKeyActionsCanBeSavedRetrievedAndCleared() {
        val configs = mapOf(
            PhysicalKey.F1 to ButtonActionConfig(singlePressAction = ActionType.OpenWidgetsPage),
            PhysicalKey.F2 to ButtonActionConfig(singlePressAction = ActionType.GlobalBack),
            PhysicalKey.CAMERA to ButtonActionConfig(singlePressAction = ActionType.ToggleFlashlight)
        )

        for ((key, config) in configs) {
            HardwareRemapManager.saveButtonActionConfig(context, key, config)
            assertEquals(config, HardwareRemapManager.getButtonActionConfig(context, key))
        }

        for (key in configs.keys) {
            HardwareRemapManager.resetButtonActionConfigForKey(
                context,
                key.defaultKeyCode,
                key.scancode
            )
            assertEquals(
                ButtonActionConfig(),
                HardwareRemapManager.getButtonActionConfig(context, key)
            )
            assertFalse(prefs.contains("action_single_${key.scancode}"))
            assertFalse(prefs.contains("action_double_${key.scancode}"))
            assertFalse(prefs.contains("action_long_${key.scancode}"))
        }
    }

    @Test
    fun keyCaptureListenerReceivesAndConsumesHardwareEvents() {
        val captured = mutableListOf<KeyEvent>()
        HardwareRemapManager.keyCaptureListener = { event ->
            captured += event
            true
        }

        val event = KeyEvent(
            1L,
            2L,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_F2,
            0
        )
        val consumed = HardwareRemapManager.onKeyEvent(context, event)

        assertTrue(consumed)
        assertEquals(1, captured.size)
        assertEquals(KeyEvent.KEYCODE_F2, captured.single().keyCode)
        assertEquals(KeyEvent.ACTION_DOWN, captured.single().action)
    }

    @Test
    fun hardwareKeyBroadcastReachesCaptureListenerAndMalformedExtrasAreSafe() {
        val captured = mutableListOf<KeyEvent>()
        HardwareRemapManager.keyCaptureListener = { event ->
            captured += event
            true
        }

        sendHardwareKeyBroadcast(
            Intent(MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER)
                .putExtra(MochibunSystemActions.EXTRA_KEY_CODE, KeyEvent.KEYCODE_CAMERA)
                .putExtra(MochibunSystemActions.EXTRA_ACTION, KeyEvent.ACTION_DOWN)
                .putExtra(MochibunSystemActions.EXTRA_REPEAT_COUNT, 0)
        )
        sendHardwareKeyBroadcast(Intent(MochibunSystemActions.ACTION_HARDWARE_KEY_TRIGGER))

        assertEquals(2, captured.size)
        assertEquals(KeyEvent.KEYCODE_CAMERA, captured[0].keyCode)
        assertEquals(KeyEvent.ACTION_DOWN, captured[0].action)
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, captured[1].keyCode)
    }

    private fun sendHardwareKeyBroadcast(intent: Intent) {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            MochibunSystemActions.SYSTEM_SERVER_BROADCAST_PERMISSION
        )
        try {
            context.sendBroadcast(intent.setPackage(context.packageName))
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
        instrumentation.waitForIdleSync()
    }
}
