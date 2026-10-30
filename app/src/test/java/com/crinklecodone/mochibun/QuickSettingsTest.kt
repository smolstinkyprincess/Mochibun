package com.crinklecodone.mochibun

import android.media.AudioManager
import com.crinklecodone.mochibun.data.prefs.PreferencesManager
import com.crinklecodone.mochibun.ui.components.QuickSettingsNav
import com.crinklecodone.mochibun.ui.components.QuickSettingsNav.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsTest {

    @Test
    fun leftMovesAlongRowAndStopsAtLeftEdge() {
        assertEquals(Move.To(0), QuickSettingsNav.left(1))
        assertEquals(Move.To(1), QuickSettingsNav.left(2))
        assertEquals(Move.To(2), QuickSettingsNav.left(3))
        assertEquals(Move.Boundary, QuickSettingsNav.left(0))
    }

    @Test
    fun rightMovesAlongRowAndExitsHomeAtRightEdge() {
        assertEquals(Move.To(1), QuickSettingsNav.right(0))
        assertEquals(Move.To(2), QuickSettingsNav.right(1))
        assertEquals(Move.To(3), QuickSettingsNav.right(2))
        assertEquals(Move.ExitRight, QuickSettingsNav.right(3))
    }

    @Test
    fun upAlwaysHitsBoundary() {
        for (i in 0..3) assertEquals(Move.Boundary, QuickSettingsNav.up(i))
    }

    @Test
    fun downAlwaysExits() {
        for (i in 0..3) assertEquals(Move.ExitDown, QuickSettingsNav.down(i))
    }

    @Test
    fun ringerCyclesNormalVibrateSilent() {
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, QuickSettingsNav.nextRingerMode(AudioManager.RINGER_MODE_NORMAL))
        assertEquals(AudioManager.RINGER_MODE_SILENT, QuickSettingsNav.nextRingerMode(AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, QuickSettingsNav.nextRingerMode(AudioManager.RINGER_MODE_SILENT))
    }

    @Test
    fun ringerLabels() {
        assertEquals("🔔 Normal", QuickSettingsNav.ringerLabel(AudioManager.RINGER_MODE_NORMAL))
        assertEquals("📳 Vibrate", QuickSettingsNav.ringerLabel(AudioManager.RINGER_MODE_VIBRATE))
        assertEquals("🔇 Silent", QuickSettingsNav.ringerLabel(AudioManager.RINGER_MODE_SILENT))
    }

    @Test
    fun rootQuickSettingsPrefDefaultsOn() {
        assertEquals("pref_root_quick_settings_enabled", PreferencesManager.KEY_ROOT_QUICK_SETTINGS_ENABLED)
        assertTrue(PreferencesManager.DEFAULT_ROOT_QUICK_SETTINGS_ENABLED)
        assertTrue(PreferencesManager.isRootQuickSettingsEnabled.value)
    }
}
