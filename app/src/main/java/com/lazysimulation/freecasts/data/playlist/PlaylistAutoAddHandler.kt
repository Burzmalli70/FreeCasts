package com.lazysimulation.freecasts.data.playlist

import com.lazysimulation.freecasts.data.download.AutoDownloadHandler
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.PlaylistEpisodeCrossRef

/**
 * Adds episodes to playlists that have auto-add enabled for a podcast.
 *
 * On sync, every newly discovered unplayed episode is added to matching playlists.
 * When auto-add is first enabled on a playlist, only the most recent unplayed episode
 * is seeded (if the podcast's latest episode is already played, nothing is added).
 *
 * Insertion order follows each playlist's [com.lazysimulation.freecasts.data.local.entity.Playlist.sortEpisodesAscending]
 * setting: ascending (default) appends so newer episodes end up at the end; descending
 * prepends so newer episodes appear at the start.
 *
 * When an episode is newly added to a playlist and auto-download is enabled,
 * a download is enqueued for that episode.
 */
class PlaylistAutoAddHandler(
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao,
    private val autoDownloadHandler: AutoDownloadHandler,
) {
    /**
     * Adds newly synced episodes to every playlist configured to auto-add from [podcastId].
     * Unplayed episodes in [newEpisodes] are added oldest-first so append/prepend yields
     * the correct date order for each playlist's sort preference.
     */
    suspend fun addNewEpisodesToAutoAddPlaylists(
        podcastId: Long,
        newEpisodes: List<Episode>
    ) {
        if (newEpisodes.isEmpty()) return

        val episodesToAdd = newEpisodes
            .filter { !it.isPlayed && it.id > 0 }
            .sortedBy { it.publishedAt ?: 0L }

        for (episode in episodesToAdd) {
            addEpisodeToAutoAddPlaylists(podcastId, episode)
        }
    }

    /**
     * Adds the most recent unplayed episode for [podcastId] to [playlistId], if eligible.
     * Used when enabling auto-add on a playlist (e.g. on create/edit save).
     */
    suspend fun addMostRecentUnplayedEpisodeToPlaylist(playlistId: Long, podcastId: Long) {
        val mostRecentEpisode = episodeDao.getAllByPodcastId(podcastId).firstOrNull() ?: return
        if (mostRecentEpisode.isPlayed || mostRecentEpisode.id <= 0) return

        addEpisodeToPlaylist(playlistId, mostRecentEpisode)
    }

    /**
     * Adds [episodeId] to [playlistId] if not already present, and triggers auto-download
     * when the episode is newly added.
     */
    suspend fun addEpisodeToPlaylist(playlistId: Long, episodeId: Long): Boolean {
        val episode = episodeDao.getById(episodeId) ?: return false
        return addEpisodeToPlaylist(playlistId, episode)
    }

    private suspend fun addEpisodeToAutoAddPlaylists(podcastId: Long, episode: Episode) {
        val playlists = playlistDao.getPlaylistsWithAutoAddForPodcast(podcastId.toString())
        if (playlists.isEmpty()) return

        var addedToAnyPlaylist = false
        for (playlist in playlists) {
            if (insertEpisodeIntoPlaylist(playlist.id, episode, append = playlist.sortEpisodesAscending)) {
                addedToAnyPlaylist = true
            }
        }
        if (addedToAnyPlaylist) {
            autoDownloadHandler.downloadEpisodeIfEnabled(episode.id)
        }
    }

    private suspend fun addEpisodeToPlaylist(playlistId: Long, episode: Episode): Boolean {
        val playlist = playlistDao.getById(playlistId) ?: return false
        val added = insertEpisodeIntoPlaylist(
            playlistId = playlistId,
            episode = episode,
            append = playlist.sortEpisodesAscending
        )
        if (added) {
            autoDownloadHandler.downloadEpisodeIfEnabled(episode.id)
        }
        return added
    }

    /**
     * @param append when true, insert after the last episode (newer at end for ascending sort);
     * when false, insert at the start (newer at start for descending sort).
     */
    private suspend fun insertEpisodeIntoPlaylist(
        playlistId: Long,
        episode: Episode,
        append: Boolean
    ): Boolean {
        if (playlistDao.isEpisodeInPlaylist(playlistId, episode.id)) return false

        val position = if (append) {
            (playlistDao.getMaxPosition(playlistId) ?: -1) + 1
        } else {
            playlistDao.incrementAllPositions(playlistId)
            0
        }

        playlistDao.insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(
                playlistId = playlistId,
                episodeId = episode.id,
                position = position,
                addedAt = System.currentTimeMillis()
            )
        )
        playlistDao.updateTimestamp(playlistId)
        return true
    }
}
