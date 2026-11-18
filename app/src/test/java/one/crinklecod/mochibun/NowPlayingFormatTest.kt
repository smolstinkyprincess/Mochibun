package one.crinklecod.mochibun

import one.crinklecod.mochibun.data.repository.MochibunMediaState
import one.crinklecod.mochibun.ui.components.computeDisplayPositionMs
import one.crinklecod.mochibun.ui.components.formatMediaDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingFormatTest {

    @Test
    fun formatMediaDuration_formatsMinutesAndHours() {
        assertEquals("0:00", formatMediaDuration(-500L))
        assertEquals("0:00", formatMediaDuration(0L))
        assertEquals("0:09", formatMediaDuration(9_500L))
        assertEquals("1:23", formatMediaDuration(83_000L))
        assertEquals("1:02:03", formatMediaDuration(3_723_000L))
    }

    @Test
    fun computeDisplayPositionMs_advancesOnlyWhilePlayingAndClampsToDuration() {
        val paused = MochibunMediaState(
            isPlaying = false,
            durationMs = 180_000L,
            positionMs = 45_000L,
            lastPositionUpdateTime = 1_000L,
            playbackSpeed = 1.0f
        )
        assertEquals(45_000L, computeDisplayPositionMs(paused, elapsedRealtimeMs = 11_000L))

        val playing = paused.copy(isPlaying = true, playbackSpeed = 1.5f)
        assertEquals(60_000L, computeDisplayPositionMs(playing, elapsedRealtimeMs = 11_000L))

        val pastEnd = paused.copy(isPlaying = true)
        assertEquals(180_000L, computeDisplayPositionMs(pastEnd, elapsedRealtimeMs = 500_000L))

        val noDuration = paused.copy(isPlaying = true, durationMs = 0L)
        assertEquals(0L, computeDisplayPositionMs(noDuration, elapsedRealtimeMs = 11_000L))
    }
}
