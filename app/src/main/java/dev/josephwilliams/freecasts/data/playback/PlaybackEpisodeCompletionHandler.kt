package dev.josephwilliams.freecasts.data.playback

import androidx.media3.common.MediaItem
import dev.josephwilliams.freecasts.data.local.dao.DownloadDao
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.playlist.PlaylistAutoRemoveHandler
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Marks episodes as played (and applies auto-remove playlist cleanup) when queue
 * playback completes an item naturally.
 */
class PlaybackEpisodeCompletionHandler(
    private val playlistAutoRemoveHandler: PlaylistAutoRemoveHandler,
    private val episodeDao: EpisodeDao,
    private val downloadDao: DownloadDao,
    private val userPreferencesRepository: UserPreferencesRepository
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
            playlistAutoRemoveHandler.markEpisodeAsPlayed(episodeId)
            episodeDao.incrementListenCount(episodeId)
            episodeDao.incrementReplayPriority(episodeId)
            if (userPreferencesRepository.deletePlayedDownloads.first() &&
                (!userPreferencesRepository.keepFavoriteDownloads.first() ||
                    episodeDao.getById(episodeId)?.isFavorite == false)
            ) {
                downloadDao.deleteByEpisodeId(episodeId)
            }
        }
    }
}
