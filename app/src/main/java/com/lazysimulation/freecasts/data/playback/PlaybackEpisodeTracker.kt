package com.lazysimulation.freecasts.data.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Tracks which media item is playing so the correct episode is marked complete
 * when the player auto-advances to the next queue item.
 */
internal class PlaybackEpisodeTracker {
    private var trackedMediaItem: MediaItem? = null

    fun onMediaItemTransition(newMediaItem: MediaItem?, reason: Int): Long? {
        val completedEpisodeId = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            episodeIdFrom(trackedMediaItem)
        } else {
            null
        }
        trackedMediaItem = newMediaItem
        return completedEpisodeId
    }

    fun onPlaybackEnded(hasNextMediaItem: Boolean): Long? {
        if (hasNextMediaItem) return null
        return episodeIdFrom(trackedMediaItem)
    }

    private fun episodeIdFrom(mediaItem: MediaItem?): Long? {
        val episodeId = mediaItem?.mediaMetadata?.extras?.getLong(
            PlaybackService.EXTRA_EPISODE_ID,
            -1L
        ) ?: -1L
        return episodeId.takeIf { it > 0 }
    }
}
