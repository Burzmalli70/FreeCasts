package com.lazysimulation.freecasts.data.download

import com.lazysimulation.freecasts.data.local.dao.DownloadDao
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Deletes a played episode's download when the user preference allows it.
 *
 * Keeps the download when:
 * - "Delete played episode downloads" is off
 * - "Keep downloaded favorite episodes" is on and the episode is a favorite
 * - The episode is still in any playlist
 */
class PlayedEpisodeDownloadCleanup(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
) {
    suspend fun cleanupIfNeeded(episodeId: Long): Boolean {
        if (!userPreferencesRepository.deletePlayedDownloads.first()) return false

        val episode = episodeDao.getById(episodeId) ?: return false
        if (userPreferencesRepository.keepFavoriteDownloads.first() && episode.isFavorite) {
            return false
        }

        if (playlistDao.isEpisodeInAnyPlaylist(episodeId)) {
            return false
        }

        downloadDao.deleteByEpisodeId(episodeId)
        return true
    }
}
