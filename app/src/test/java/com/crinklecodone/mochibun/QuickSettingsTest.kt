package com.crinklecodone.mochibun

import android.media.AudioManager
import com.crinklecodone.mochibun.ui.components.QuickSettingsNav
import com.crinklecodone.mochibun.ui.components.QuickSettingsNav.Move
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSettingsTest {

    @Test
    fun leftMovesWithinRowAndStopsAtLeftEdge() {
        assertEquals(Move.To(0), QuickSettingsNav.left(1))
        assertEquals(Move.To(2), QuickSettingsNav.left(3))
        assertEquals(Move.Boundary, QuickSettingsNav.left(0))
        assertEquals(Move.Boundary, QuickSettingsNav.left(2))
    }

    @Test
    fun rightMovesWithinRowAndExitsHomeAtRightEdge() {
        assertEquals(Move.To(1), QuickSettingsNav.right(0))
        assertEquals(Move.To(3), QuickSettingsNav.right(2))
        assertEquals(Move.ExitRight, QuickSettingsNav.right(1))
        assertEquals(Move.ExitRight, QuickSettingsNav.right(3))
    }

    @Test
    fun upMovesToTopRowAndStopsAtTop() {
        assertEquals(Move.To(0), QuickSettingsNav.up(2))
        assertEquals(Move.To(1), QuickSettingsNav.up(3))
        assertEquals(Move.Boundary, QuickSettingsNav.up(0))
        assertEquals(Move.Boundary, QuickSettingsNav.up(1))
    }

    @Test
    fun downMovesToBottomRowThenExits() {
        assertEquals(Move.To(2), QuickSettingsNav.down(0))
        assertEquals(Move.To(3), QuickSettingsNav.down(1))
        assertEquals(Move.ExitDown, QuickSettingsNav.down(2))
        assertEquals(Move.ExitDown, QuickSettingsNav.down(3))
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
}
