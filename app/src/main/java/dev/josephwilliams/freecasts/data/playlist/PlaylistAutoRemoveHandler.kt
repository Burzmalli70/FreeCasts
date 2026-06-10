package dev.josephwilliams.freecasts.data.playlist

import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao

/**
 * Handles side effects when an episode is marked as played, including removal
 * from playlists that have auto-remove enabled.
 */
class PlaylistAutoRemoveHandler(
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao
) {
    suspend fun markEpisodeAsPlayed(episodeId: Long) {
        episodeDao.markAsPlayed(episodeId)
        removeFromAutoRemovePlaylists(episodeId)
    }

    suspend fun removeFromAutoRemovePlaylists(episodeId: Long) {
        for (playlist in playlistDao.getPlaylistsWithAutoRemove()) {
            if (playlistDao.isEpisodeInPlaylist(playlist.id, episodeId)) {
                playlistDao.removeEpisodeFromPlaylist(playlist.id, episodeId)
                playlistDao.updateTimestamp(playlist.id)
            }
        }
    }
}
