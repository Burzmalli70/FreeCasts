package dev.josephwilliams.freecasts.data.playlist

import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef

/**
 * Adds newly synced episodes to playlists that have auto-add enabled for the podcast.
 */
class PlaylistAutoAddHandler(
    private val playlistDao: PlaylistDao
) {
    /**
     * Adds [newEpisodes] to every playlist configured to auto-add episodes from [podcastId].
     * Only unplayed episodes are added, and episodes already in a playlist are skipped.
     */
    suspend fun addNewEpisodesToAutoAddPlaylists(
        podcastId: Long,
        newEpisodes: List<Episode>
    ) {
        if (newEpisodes.isEmpty()) return

        val unplayedNewEpisodes = newEpisodes.filter { !it.isPlayed && it.id > 0 }
        if (unplayedNewEpisodes.isEmpty()) return

        val playlists = playlistDao.getPlaylistsWithAutoAddForPodcast(podcastId.toString())
        if (playlists.isEmpty()) return

        for (playlist in playlists) {
            var maxPosition = playlistDao.getMaxPosition(playlist.id) ?: -1

            for (episode in unplayedNewEpisodes) {
                if (playlistDao.isEpisodeInPlaylist(playlist.id, episode.id)) continue

                maxPosition++
                playlistDao.insertPlaylistEpisode(
                    PlaylistEpisodeCrossRef(
                        playlistId = playlist.id,
                        episodeId = episode.id,
                        position = maxPosition,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }

            playlistDao.updateTimestamp(playlist.id)
        }
    }
}
