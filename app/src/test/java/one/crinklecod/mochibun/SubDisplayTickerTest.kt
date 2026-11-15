package one.crinklecod.mochibun

import one.crinklecod.mochibun.ui.components.SUB_DISPLAY_PAGE_NOTIFICATIONS
import one.crinklecod.mochibun.ui.components.SUB_DISPLAY_PAGE_WIDGETS
import one.crinklecod.mochibun.ui.components.buildSubDisplayFeed
import one.crinklecod.mochibun.ui.components.dailyFortuneLabel
import one.crinklecod.mochibun.ui.components.subDisplayTickerTargetPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feed text, daily fortune and tap target of the Home outer sub-display ticker. */
class SubDisplayTickerTest {

    @Test
    fun fullFeedIsJoinedInOrder() {
        val feed = buildSubDisplayFeed(
            isMediaPlaying = true,
            artist = "Mochi",
            title = "Bun Song",
            unreadCount = 3,
            batteryLevel = 87,
            isCharging = false,
            fortune = "Lucky: Sakura Tea"
        )
        assertEquals(
            "♫ Mochi - Bun Song  ✦  ✉ 3 new  ✦  ♥ 87%  ✦  Lucky: Sakura Tea",
            feed
        )
    }

    @Test
    fun blankOrPlaceholderArtistIsOmitted() {
        for (artist in listOf("", "  ", "Unknown Artist")) {
            val feed = buildSubDisplayFeed(true, artist, "Track", 0, 50, false, "")
            assertEquals("♫ Track  ✦  ♥ 50%", feed)
        }
    }

    @Test
    fun pausedMediaZeroUnreadAndUnknownBatteryAreOmitted() {
        val feed = buildSubDisplayFeed(false, "A", "T", 0, -1, false, "Lucky: Pudding")
        assertEquals("Lucky: Pudding", feed)
    }

    @Test
    fun chargingShowsBolt() {
        val feed = buildSubDisplayFeed(false, "", "", 0, 40, true, "")
        assertEquals("⚡ 40%", feed)
    }

    @Test
    fun fortuneIsStablePerDayAndVaries() {
        assertEquals(dailyFortuneLabel(2026, 318), dailyFortuneLabel(2026, 318))
        assertTrue(dailyFortuneLabel(2026, 318).startsWith("Lucky: "))
        val week = (318..324).map { dailyFortuneLabel(2026, it) }.toSet()
        assertTrue(week.size > 1)
        assertNotEquals(dailyFortuneLabel(2026, 318), dailyFortuneLabel(2026, 319))
    }

    @Test
    fun tapTargetPrefersNotifications() {
        assertEquals(SUB_DISPLAY_PAGE_NOTIFICATIONS, subDisplayTickerTargetPage(2))
        assertEquals(SUB_DISPLAY_PAGE_WIDGETS, subDisplayTickerTargetPage(0))
    }
}
