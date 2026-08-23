package com.lazysimulation.freecasts.data.playback

import androidx.media3.common.MediaItem
import com.lazysimulation.freecasts.data.download.PlayedEpisodeDownloadCleanup
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.playlist.PlaylistAutoRemoveHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Marks episodes as played (and applies auto-remove playlist cleanup) when queue
 * playback completes an item naturally.
 */
class PlaybackEpisodeCompletionHandler(
    private val playlistAutoRemoveHandler: PlaylistAutoRemoveHandler,
    private val episodeDao: EpisodeDao,
    private val playedEpisodeDownloadCleanup: PlayedEpisodeDownloadCleanup,
) {
    private val tracker = PlaybackEpisodeTracker()

    fun onMediaItemTransition(
        scope: CoroutineScope,
        newMediaItem: MediaItem?,
        reason: Int
    ) {
        tracker.onMediaItemTransition(newMediaItem, reason)?.let { episodeId ->
            completeEpisode(scope, episodeId)
        }
    }

    fun onPlaybackEnded(
        scope: CoroutineScope,
        hasNextMediaItem: Boolean
    ) {
        tracker.onPlaybackEnded(hasNextMediaItem)?.let { episodeId ->
            completeEpisode(scope, episodeId)
        }
    }

    private fun completeEpisode(scope: CoroutineScope, episodeId: Long) {
        scope.launch(Dispatchers.IO) {
            episodeDao.setPlaybackPosition(episodeId, 0)
            // Auto-remove from playlists before download cleanup so membership reflects
            // post-listen playlist state (keep download if still on another playlist).
            playlistAutoRemoveHandler.markEpisodeAsPlayed(episodeId)
            episodeDao.incrementListenCount(episodeId)
            episodeDao.incrementReplayPriority(episodeId)
            playedEpisodeDownloadCleanup.cleanupIfNeeded(episodeId)
        }
    }
}
