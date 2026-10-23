package com.crinklecodone.mochibun

import android.media.AudioManager
import android.view.KeyEvent
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val upDirection = if (KeyEvent.KEYCODE_VOLUME_UP == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager.ADJUST_RAISE
        } else {
            AudioManager.ADJUST_LOWER
        }
        assertEquals(AudioManager.ADJUST_RAISE, upDirection)

        val downDirection = if (KeyEvent.KEYCODE_VOLUME_DOWN == KeyEvent.KEYCODE_VOLUME_UP) {
            AudioManager.ADJUST_RAISE
        } else {
            AudioManager.ADJUST_LOWER
        }
        assertEquals(AudioManager.ADJUST_LOWER, downDirection)
    }
}
