package dev.josephwilliams.freecasts.data.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackEpisodeTrackerTest {

    private val tracker = PlaybackEpisodeTracker()

    @Test
    fun autoTransitionFromFirstQueueItem_marksPreviousEpisodeNotNewEpisode() {
        val episodeOne = testMediaItem(episodeId = 1L)
        val episodeTwo = testMediaItem(episodeId = 2L)

        assertNull(
            tracker.onMediaItemTransition(episodeOne, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        )

        assertEquals(
            1L,
            tracker.onMediaItemTransition(episodeTwo, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        )
    }

    @Test
    fun autoTransitionFromMiddleQueueItem_marksFinishedEpisode() {
        val episodeTwo = testMediaItem(episodeId = 2L)
        val episodeThree = testMediaItem(episodeId = 3L)

        tracker.onMediaItemTransition(episodeTwo, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

        assertEquals(
            2L,
            tracker.onMediaItemTransition(episodeThree, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        )
    }

    @Test
    fun manualSeekToNext_doesNotMarkEpisodeAsCompleted() {
        val episodeOne = testMediaItem(episodeId = 1L)
        val episodeTwo = testMediaItem(episodeId = 2L)

        tracker.onMediaItemTransition(episodeOne, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

        assertNull(
            tracker.onMediaItemTransition(episodeTwo, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        )
    }

    @Test
    fun playbackEndedWithNextItem_doesNotMarkEpisode() {
        val episodeOne = testMediaItem(episodeId = 1L)
        tracker.onMediaItemTransition(episodeOne, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

        assertNull(tracker.onPlaybackEnded(hasNextMediaItem = true))
    }

    @Test
    fun playbackEndedWithoutNextItem_marksCurrentEpisode() {
        val episodeThree = testMediaItem(episodeId = 3L)
        tracker.onMediaItemTransition(episodeThree, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

        assertEquals(3L, tracker.onPlaybackEnded(hasNextMediaItem = false))
    }

    @Test
    fun playlistQueueCompletionSequence_marksEachEpisodeAsItFinishes() {
        val episodeOne = testMediaItem(episodeId = 1L)
        val episodeTwo = testMediaItem(episodeId = 2L)
        val episodeThree = testMediaItem(episodeId = 3L)

        assertNull(
            tracker.onMediaItemTransition(episodeOne, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        )
        assertEquals(
            1L,
            tracker.onMediaItemTransition(episodeTwo, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        )
        assertEquals(
            2L,
            tracker.onMediaItemTransition(episodeThree, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        )
        assertEquals(3L, tracker.onPlaybackEnded(hasNextMediaItem = false))
    }

    private fun testMediaItem(episodeId: Long): MediaItem {
        val extras = Bundle().apply {
            putLong(PlaybackService.EXTRA_EPISODE_ID, episodeId)
            putLong(PlaybackService.EXTRA_PODCAST_ID, 10L)
        }
        return MediaItem.Builder()
            .setMediaId(episodeId.toString())
            .setUri("https://example.com/$episodeId.mp3")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Episode $episodeId")
                    .setExtras(extras)
                    .build()
            )
            .build()
    }
}
