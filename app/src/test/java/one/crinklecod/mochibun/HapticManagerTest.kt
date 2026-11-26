package one.crinklecod.mochibun

import android.content.Context
import android.os.Vibrator
import one.crinklecod.mochibun.core.haptic.HapticManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class HapticManagerTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        HapticManager.resetDeleteRepeatGate()
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        shadowOf(vibrator).setHasVibrator(true)
    }

    @Test
    fun tunedPulseConstantsMatchM120ErmMotorSpec() {
        assertEquals(24L, HapticManager.IME_KEY_PRESS_DURATION_MS)
        assertEquals(255, HapticManager.IME_KEY_PRESS_AMPLITUDE)
        assertEquals(32L, HapticManager.IME_CONFIRM_DELETE_DURATION_MS)
        assertEquals(255, HapticManager.IME_CONFIRM_DELETE_AMPLITUDE)
        assertEquals(65L, HapticManager.IME_DELETE_REPEAT_INTERVAL_MS)
        assertArrayEquals(longArrayOf(0L, 18L, 35L, 26L), HapticManager.IME_LONG_PRESS_TIMINGS)
        assertArrayEquals(intArrayOf(0, 220, 0, 255), HapticManager.IME_LONG_PRESS_AMPLITUDES)
    }

    @Test
    fun rapidKeyPressesAlwaysFireAt24msWithoutDropping() {
        val vibrator = HapticManager.resolveVibrator(context)!!
        val shadow = shadowOf(vibrator)

        repeat(5) {
            HapticManager.vibrateKeyPress(context)
            assertTrue(shadow.isVibrating)
            assertEquals(24L, shadow.milliseconds)
        }
    }

    @Test
    fun confirmOrDeleteFires32msAndSeedsDeleteRepeatRatchet() {
        val vibrator = HapticManager.resolveVibrator(context)!!
        val shadow = shadowOf(vibrator)

        HapticManager.vibrateKeyConfirmOrDelete(context)
        assertTrue(shadow.isVibrating)
        assertEquals(32L, shadow.milliseconds)

        HapticManager.resetDeleteRepeatGate()
        assertTrue(HapticManager.vibrateKeyDeleteRepeat(context, nowUptimeMs = 1_000L))
        assertFalse(HapticManager.vibrateKeyDeleteRepeat(context, nowUptimeMs = 1_030L))
        assertFalse(HapticManager.vibrateKeyDeleteRepeat(context, nowUptimeMs = 1_064L))
        assertTrue(HapticManager.vibrateKeyDeleteRepeat(context, nowUptimeMs = 1_065L))
        assertEquals(32L, shadow.milliseconds)
    }

    @Test
    fun longPressDispatchesDoubleTickWaveform() {
        val vibrator = HapticManager.resolveVibrator(context)!!
        val shadow = shadowOf(vibrator)

        HapticManager.vibrateKeyLongPress(context)
        assertTrue(shadow.isVibrating)
        assertArrayEquals(longArrayOf(0L, 18L, 35L, 26L), shadow.pattern)
    }

    @Test
    fun noCrashOrVibrationWhenDeviceLacksVibrator() {
        val vibrator = HapticManager.resolveVibrator(context)!!
        val shadow = shadowOf(vibrator)
        vibrator.cancel()
        shadow.setHasVibrator(false)

        HapticManager.vibrateKeyPress(context)
        HapticManager.vibrateKeyConfirmOrDelete(context)
        HapticManager.vibrateKeyDeleteRepeat(context, nowUptimeMs = 2_000L)
        HapticManager.vibrateKeyLongPress(context)
        HapticManager.vibrateTick(context)

        assertFalse(shadow.isVibrating)
    }
}
