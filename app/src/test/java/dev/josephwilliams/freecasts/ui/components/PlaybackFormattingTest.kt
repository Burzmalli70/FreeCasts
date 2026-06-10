package dev.josephwilliams.freecasts.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackFormattingTest {

    @Test
    fun formatRemainingTime_returnsNullWhenNotStarted() {
        assertNull(formatRemainingTime(0, 3600))
    }

    @Test
    fun formatRemainingTime_returnsNullWhenUnderSixtySeconds() {
        assertNull(formatRemainingTime(59_000, 3600))
        assertEquals("59 min left", formatRemainingTime(60_000, 3600))
    }

    @Test
    fun formatRemainingTime_returnsNullWhenPlayed() {
        assertNull(formatRemainingTime(60_000, 3600, isPlayed = true))
    }

    @Test
    fun formatRemainingTime_formatsMinutesLeft() {
        assertEquals("45 min left", formatRemainingTime(15 * 60 * 1000L, 60 * 60))
    }

    @Test
    fun formatRemainingTime_formatsHoursLeft() {
        assertEquals("1h 30m left", formatRemainingTime(30 * 60 * 1000L, 2 * 60 * 60))
    }

    @Test
    fun formatEpisodeListDuration_showsRemainingWhenInProgress() {
        assertEquals(
            "45 min left",
            formatEpisodeListDuration(15 * 60 * 1000L, 60 * 60, isPlayed = false)
        )
    }

    @Test
    fun formatEpisodeListDuration_showsTotalWhenNotStarted() {
        assertEquals("1h 0m", formatEpisodeListDuration(0, 60 * 60, isPlayed = false))
        assertEquals("23 min", formatEpisodeListDuration(0, 23 * 60, isPlayed = false))
        assertEquals("1h 0m", formatEpisodeListDuration(30_000, 60 * 60, isPlayed = false))
    }
}
