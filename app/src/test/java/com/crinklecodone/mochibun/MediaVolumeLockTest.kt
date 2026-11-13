package com.crinklecodone.mochibun

import android.media.AudioManager
import android.view.KeyEvent
import com.crinklecodone.mochibun.core.util.MochibunSystemActions
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaVolumeLockTest {

    @Test
    fun testPreferencesDefaults() {
        assertEquals("pref_media_volume_lock_enabled", PreferencesManager.KEY_MEDIA_VOLUME_LOCK_ENABLED)
        assertFalse(PreferencesManager.DEFAULT_MEDIA_VOLUME_LOCK_ENABLED)
        assertFalse(PreferencesManager.isMediaVolumeLockEnabled.value)
    }

    @Test
    fun testVolumeKeyDirectionMapping() {
        assertEquals(AudioManager.ADJUST_RAISE, MochibunSystemActions.mediaVolumeDirection(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(AudioManager.ADJUST_LOWER, MochibunSystemActions.mediaVolumeDirection(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(MochibunSystemActions.mediaVolumeDirection(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertNull(MochibunSystemActions.mediaVolumeDirection(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun testCallsKeepStockVolumeHandling() {
        assertTrue(MochibunSystemActions.shouldRouteVolumeToMedia(AudioManager.MODE_NORMAL))
        assertFalse(MochibunSystemActions.shouldRouteVolumeToMedia(AudioManager.MODE_IN_CALL))
        assertFalse(MochibunSystemActions.shouldRouteVolumeToMedia(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(MochibunSystemActions.shouldRouteVolumeToMedia(AudioManager.MODE_RINGTONE))
        assertFalse(MochibunSystemActions.shouldRouteVolumeToMedia(AudioManager.MODE_CALL_SCREENING))
    }
}
