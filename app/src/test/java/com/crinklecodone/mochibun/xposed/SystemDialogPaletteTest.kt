package com.crinklecodone.mochibun.xposed

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the Stage H palette tokens shared by the Power Menu, SystemUI dialogs and screenshot shelf.
 */
class SystemDialogPaletteTest {

    @After
    fun restoreDefaults() {
        ActiveHookTheme.updateColors(
            newPrimary = ActiveHookTheme.DEFAULT_PRIMARY,
            newContainer = ActiveHookTheme.DEFAULT_CONTAINER,
            newAccentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP,
            newTrack = ActiveHookTheme.DEFAULT_TRACK,
            newScrim = ActiveHookTheme.DEFAULT_SCRIM
        )
    }

    @Test
    fun dialogStrokeIsAccentDeepAtThirtyPercent() {
        restoreDefaults()
        // Deep Plum #4A2E56 at 30% alpha (0x4D)
        assertEquals(0x4D4A2E56, ActiveHookTheme.dialogStroke)
    }

    @Test
    fun dialogStrokeFollowsThemeBroadcasts() {
        ActiveHookTheme.accentDeep = 0xFFE8D5FF.toInt() // light accentDeep, as in dark palettes
        assertEquals(0x4DE8D5FF, ActiveHookTheme.dialogStroke)
    }

    @Test
    fun cornerRadiiMatchKeitaiGrammar() {
        // Containers: 22dp squircle; interactive tiles / buttons: 14dp-18dp squircle.
        assertEquals(22f, MochibunSystemUiHook.DIALOG_CORNER_DP)
        assertEquals(16f, MochibunSystemUiHook.CONTROL_CORNER_DP)
    }
}
