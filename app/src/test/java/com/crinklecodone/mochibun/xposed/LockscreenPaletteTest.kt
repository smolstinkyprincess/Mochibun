package com.crinklecodone.mochibun.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Mochibun Dreamy Keitai Lockscreen palette, carrier greeting, and pearl affordance.
 */
class LockscreenPaletteTest {

    @Test
    fun testCarrierGreetingString() {
        assertEquals("— ♡ Welcome home, emii ♡ —", MochibunSystemUiHook.CARRIER_GREETING)
    }

    @Test
    fun testLockPearlStrokeDynamicCalculation() {
        // Reset to default theme
        ActiveHookTheme.updateColors(
            newPrimary = ActiveHookTheme.DEFAULT_PRIMARY,
            newContainer = ActiveHookTheme.DEFAULT_CONTAINER,
            newAccentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP,
            newTrack = ActiveHookTheme.DEFAULT_TRACK,
            newScrim = ActiveHookTheme.DEFAULT_SCRIM
        )

        // Verify default stroke is 30% alpha (0x4D) of DEFAULT_ACCENT_DEEP (#4A2E56)
        val expectedDefaultStroke = (0x4D shl 24) or (0x4A2E56)
        assertEquals(expectedDefaultStroke, ActiveHookTheme.lockPearlStroke)

        // Update accent deep color (e.g. customized theme with lavender/grape accent #3D1C4E)
        ActiveHookTheme.accentDeep = 0xFF3D1C4E.toInt()
        val expectedCustomStroke = (0x4D shl 24) or (0x3D1C4E)
        assertEquals(expectedCustomStroke, ActiveHookTheme.lockPearlStroke)

        // Restore default
        ActiveHookTheme.accentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP
    }

    @Test
    fun testLockscreenPaletteDefaults() {
        // Clock & Greeting: Deep Plum #4A2E56
        assertEquals(0xFF4A2E56.toInt(), ActiveHookTheme.DEFAULT_ACCENT_DEEP)

        // Lock pearl container background fill: Soft Cream #FFF9FB
        assertEquals(0xFFFFF9FB.toInt(), ActiveHookTheme.DEFAULT_CONTAINER)

        // Lock icon foreground tint: Sakura Pink #FFB7D5
        assertEquals(0xFFFFB7D5.toInt(), ActiveHookTheme.DEFAULT_PRIMARY)

        // Scrim backdrop: ~92% alpha translucent lavender #F6F0FC
        assertEquals(0xEBF6F0FC.toInt(), ActiveHookTheme.DEFAULT_SCRIM)
    }

    @Test
    fun testPinBouncerPaletteDefaults() {
        // Reset to default theme
        ActiveHookTheme.updateColors(
            newPrimary = ActiveHookTheme.DEFAULT_PRIMARY,
            newContainer = ActiveHookTheme.DEFAULT_CONTAINER,
            newAccentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP,
            newTrack = ActiveHookTheme.DEFAULT_TRACK,
            newScrim = ActiveHookTheme.DEFAULT_SCRIM
        )

        // PIN pearl key container fill: Soft Cream #FFF9FB
        assertEquals(ActiveHookTheme.DEFAULT_CONTAINER, ActiveHookTheme.container)

        // PIN pearl key border stroke: 30% alpha of Deep Plum #4A2E56
        val expectedPearlStroke = (0x4D shl 24) or (0x4A2E56)
        assertEquals(expectedPearlStroke, ActiveHookTheme.lockPearlStroke)

        // PIN key digits & action icons: Deep Plum #4A2E56
        assertEquals(ActiveHookTheme.DEFAULT_ACCENT_DEEP, ActiveHookTheme.accentDeep)

        // PIN key letters (Klondike): Deep Plum 60% alpha
        val expectedKlondikeColor = (0x99 shl 24) or (0x4A2E56)
        assertEquals(expectedKlondikeColor, ActiveHookTheme.qsTileInactiveFg)

        // PIN key highlight ripple & PasswordTextView heart dot fill: Sakura Pink #FFB7D5
        assertEquals(ActiveHookTheme.DEFAULT_PRIMARY, ActiveHookTheme.primary)
    }

    @Test
    fun testPinBouncerPaletteDynamicThemeUpdate() {
        // Switch to custom Matcha theme
        val matchaPrimary = 0xFFA5D6A7.toInt()       // Soft Mint Green
        val matchaContainer = 0xFFF1F8E9.toInt()     // Pale Tea Cream
        val matchaAccentDeep = 0xFF2E4C2E.toInt()    // Forest Pine
        val matchaTrack = 0xFFDCEDC8.toInt()
        val matchaScrim = 0xEBF1F8E9.toInt()

        ActiveHookTheme.updateColors(
            newPrimary = matchaPrimary,
            newContainer = matchaContainer,
            newAccentDeep = matchaAccentDeep,
            newTrack = matchaTrack,
            newScrim = matchaScrim
        )

        // Verify all PIN bouncer elements reflect custom theme dynamically
        assertEquals(matchaContainer, ActiveHookTheme.container)
        assertEquals(matchaPrimary, ActiveHookTheme.primary)
        assertEquals(matchaAccentDeep, ActiveHookTheme.accentDeep)

        val expectedMatchaStroke = (0x4D shl 24) or (matchaAccentDeep and 0x00FFFFFF)
        assertEquals(expectedMatchaStroke, ActiveHookTheme.lockPearlStroke)

        val expectedMatchaKlondike = (0x99 shl 24) or (matchaAccentDeep and 0x00FFFFFF)
        assertEquals(expectedMatchaKlondike, ActiveHookTheme.qsTileInactiveFg)

        // Restore default theme
        ActiveHookTheme.updateColors(
            newPrimary = ActiveHookTheme.DEFAULT_PRIMARY,
            newContainer = ActiveHookTheme.DEFAULT_CONTAINER,
            newAccentDeep = ActiveHookTheme.DEFAULT_ACCENT_DEEP,
            newTrack = ActiveHookTheme.DEFAULT_TRACK,
            newScrim = ActiveHookTheme.DEFAULT_SCRIM
        )
    }

    @Test
    fun testBouncerFadeAlphaCalculation() {
        // Bouncer fully hidden (expansion = 1.0f): lockscreen content fully visible (alpha = 1.0f)
        assertEquals(1.0f, ActiveHookTheme.calculateBouncerFadeAlpha(1.0f), 0.001f)

        // Bouncer fully open (expansion = 0.0f): lockscreen content completely hidden (alpha = 0.0f)
        assertEquals(0.0f, ActiveHookTheme.calculateBouncerFadeAlpha(0.0f), 0.001f)

        // Mid-expansion / swiping gesture: smoothly proportional
        assertEquals(0.5f, ActiveHookTheme.calculateBouncerFadeAlpha(0.5f), 0.001f)
        assertEquals(0.25f, ActiveHookTheme.calculateBouncerFadeAlpha(0.25f), 0.001f)

        // Out of bounds safety
        assertEquals(0.0f, ActiveHookTheme.calculateBouncerFadeAlpha(-0.5f), 0.001f)
        assertEquals(1.0f, ActiveHookTheme.calculateBouncerFadeAlpha(1.5f), 0.001f)
    }
}
