package one.crinklecod.mochibun

import one.crinklecod.mochibun.ui.components.COZY_FORTUNES_BY_DAY
import one.crinklecod.mochibun.ui.components.buildSubDisplayFeed
import one.crinklecod.mochibun.ui.components.formatNextAlarmTime
import one.crinklecod.mochibun.ui.components.dailyFortuneLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone
import java.time.DayOfWeek

/** Feed text and daily fortune of the Home greeting sub-display marquee. */
class SubDisplayTickerFeedTest {

    @Test
    fun fullFeedIsJoinedInOrder() {
        val feed = buildSubDisplayFeed(
            isMediaPlaying = true,
            artist = "Mochi",
            title = "Bun Song",
            unreadCount = 3,
            batteryLevel = 87,
            isCharging = false,
            fortune = "good vibes ahead ♡",
            pinnedMemo = "water\nthe plants ",
            nextAlarm = "7:30 AM",
            stepsToday = 4_210L
        )
        assertEquals(
            "✎ water the plants  ✦  ♫ Mochi - Bun Song  ✦  ✉ 3 new  ✦  ⏰ 7:30 AM  ✦  " +
                "🐾 4210 steps  ✦  ♥ 87%  ✦  good vibes ahead ♡",
            feed
        )
    }

    @Test
    fun pinnedMemoLeadsTheFeed() {
        val feed = buildSubDisplayFeed(true, "A", "T", 1, 50, false, "", pinnedMemo = "call mom")
        assertTrue(feed.startsWith("✎ call mom  ✦  "))
    }

    @Test
    fun blankMemoAlarmAndMissingStepSensorAreOmitted() {
        val feed = buildSubDisplayFeed(
            false, "", "", 0, 60, false, "",
            pinnedMemo = " \n ", nextAlarm = null, stepsToday = null
        )
        assertEquals("♥ 60%", feed)
    }

    @Test
    fun feedHasNoDateSegment() {
        val feed = buildSubDisplayFeed(true, "A", "T", 2, 50, true, dailyFortuneLabel(2026, 318),
            pinnedMemo = "memo", nextAlarm = "7:30 AM", stepsToday = 10L)
        val dateLike = Regex("""\b(MON|TUE|WED|THU|FRI|SAT|SUN)\b|\b\d{1,2}/\d{1,2}\b""", RegexOption.IGNORE_CASE)
        assertFalse(dateLike.containsMatchIn(feed))
    }

    @Test
    fun nextAlarmFollowsTwelveOrTwentyFourHourFormat() {
        val utc = TimeZone.getTimeZone("UTC")
        val sevenThirty = (7 * 60 + 30) * 60_000L
        val evening = (19 * 60 + 5) * 60_000L
        assertEquals("7:30 AM", formatNextAlarmTime(sevenThirty, is24Hour = false, locale = Locale.US, timeZone = utc))
        assertEquals("07:30", formatNextAlarmTime(sevenThirty, is24Hour = true, locale = Locale.US, timeZone = utc))
        assertEquals("19:05", formatNextAlarmTime(evening, is24Hour = true, locale = Locale.US, timeZone = utc))
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
        val feed = buildSubDisplayFeed(false, "A", "T", 0, -1, false, "small steps still count ♡")
        assertEquals("small steps still count ♡", feed)
    }

    @Test
    fun chargingShowsBolt() {
        val feed = buildSubDisplayFeed(false, "", "", 0, 40, true, "")
        assertEquals("⚡ 40%", feed)
    }

    @Test
    fun fortuneIsStablePerDayAndVaries() {
        val allFortunes = COZY_FORTUNES_BY_DAY.values.flatten()
        assertEquals(dailyFortuneLabel(2026, 318), dailyFortuneLabel(2026, 318))
        assertTrue(dailyFortuneLabel(2026, 318) in allFortunes)
        val week = (318..324).map { dailyFortuneLabel(2026, it) }.toSet()
        assertTrue(week.size > 1)
        assertNotEquals(dailyFortuneLabel(2026, 318), dailyFortuneLabel(2026, 319))
    }

    @Test
    fun cozyFortunesArePlainText() {
        assertEquals(DayOfWeek.entries.toSet(), COZY_FORTUNES_BY_DAY.keys)
        for ((day, fortunes) in COZY_FORTUNES_BY_DAY) {
            assertTrue("Expected fortunes for $day", fortunes.isNotEmpty())
            // No numbers and no kaomoji brackets: just short cozy text
            for (fortune in fortunes) {
                assertFalse(fortune, fortune.any { it.isDigit() })
                assertFalse(fortune, fortune.any { it in "()（）" })
                assertTrue(fortune, fortune.length <= 40)
            }
        }
    }

    @Test
    fun greetingLeadsTheMarquee() {
        val feed = buildSubDisplayFeed(
            true, "Mochi", "Bun Song", 2, 80, false, "good vibes ahead ♡",
            pinnedMemo = "feed the cat",
            greeting = "♡ Welcome home, emii ♡"
        )
        assertEquals(
            "♡ Welcome home, emii ♡  ✦  ✎ feed the cat  ✦  ♫ Mochi - Bun Song  ✦  ✉ 2 new  ✦  " +
                "♥ 80%  ✦  good vibes ahead ♡",
            feed
        )
    }
}
