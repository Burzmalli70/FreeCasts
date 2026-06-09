package dev.josephwilliams.freecasts.data.playlist

import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef

/**
 * Adds episodes to playlists that have auto-add enabled for a podcast.
 *
 * Per design: only the most recent episode for a podcast is eligible, and it must be unplayed.
 * If the most recent episode is already played, nothing is added for that podcast — even when
 * older unplayed episodes exist.
 */
class PlaylistAutoAddHandler(
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao
) {
    /**
     * Adds newly synced episodes to every playlist configured to auto-add from [podcastId].
     * Only the most recent unplayed episode is considered, and it must be among [newEpisodes].
     */
    suspend fun addNewEpisodesToAutoAddPlaylists(
        podcastId: Long,
        newEpisodes: List<Episode>
    ) {
        if (newEpisodes.isEmpty()) return

        val episodeToAdd = resolveAutoAddEpisode(
            podcastId = podcastId,
            restrictToNewEpisodes = newEpisodes
        ) ?: return

        addEpisodeToAutoAddPlaylists(podcastId, episodeToAdd)
    }

    /**
     * Adds the most recent unplayed episode for [podcastId] to [playlistId], if eligible.
     * Used when enabling auto-add on a playlist (e.g. on create/edit save).
     */
    suspend fun addMostRecentUnplayedEpisodeToPlaylist(playlistId: Long, podcastId: Long) {
        val episodeToAdd = resolveAutoAddEpisode(
            podcastId = podcastId,
            restrictToNewEpisodes = null
        ) ?: return

        addEpisodeToPlaylist(playlistId, episodeToAdd)
    }

    private suspend fun resolveAutoAddEpisode(
        podcastId: Long,
        restrictToNewEpisodes: List<Episode>?
    ): Episode? {
        val mostRecentEpisode = episodeDao.getAllByPodcastId(podcastId).firstOrNull() ?: return null
        if (mostRecentEpisode.isPlayed || mostRecentEpisode.id <= 0) return null

        if (restrictToNewEpisodes != null) {
            val newEpisodeIds = restrictToNewEpisodes.map { it.id }.toSet()
            if (mostRecentEpisode.id !in newEpisodeIds) return null
        }

        return mostRecentEpisode
    }

    private suspend fun addEpisodeToAutoAddPlaylists(podcastId: Long, episode: Episode) {
        val playlists = playlistDao.getPlaylistsWithAutoAddForPodcast(podcastId.toString())
        if (playlists.isEmpty()) return

        for (playlist in playlists) {
            addEpisodeToPlaylist(playlist.id, episode)
        }
    }

    private suspend fun addEpisodeToPlaylist(playlistId: Long, episode: Episode) {
        if (playlistDao.isEpisodeInPlaylist(playlistId, episode.id)) return

        val maxPosition = playlistDao.getMaxPosition(playlistId) ?: -1
        playlistDao.insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(
                playlistId = playlistId,
                episodeId = episode.id,
                position = maxPosition + 1,
                addedAt = System.currentTimeMillis()
            )
        )
        playlistDao.updateTimestamp(playlistId)
    }
}
