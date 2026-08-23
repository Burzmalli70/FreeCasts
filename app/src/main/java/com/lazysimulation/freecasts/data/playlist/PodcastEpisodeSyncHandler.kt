package com.lazysimulation.freecasts.data.playlist

import com.lazysimulation.freecasts.data.export.EpisodeStateImportSupport
import com.lazysimulation.freecasts.data.export.PlaylistImportSupport
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.local.dao.PodcastDao
import com.lazysimulation.freecasts.data.local.entity.Episode

/**
 * Merges RSS feed episodes into the local database during podcast sync.
 */
class PodcastEpisodeSyncHandler(
    private val episodeDao: EpisodeDao,
    private val podcastDao: PodcastDao,
    private val playlistAutoAddHandler: PlaylistAutoAddHandler,
    private val episodeStateImportSupport: EpisodeStateImportSupport,
    private val playlistImportSupport: PlaylistImportSupport,
) {
    /**
     * Inserts episodes from [feedEpisodes] that are not already stored for [podcastId],
     * updates podcast metadata, and auto-adds new episodes to configured playlists.
     * Existing playlist memberships are never removed.
     *
     * @return Episodes that were newly inserted during this sync.
     */
    suspend fun syncEpisodesFromFeed(
        podcastId: Long,
        feedEpisodes: List<Episode>
    ): List<Episode> {
        val currentEpisodeGuids = episodeDao.getAllByPodcastId(podcastId).map { it.guid }

        val newEpisodes = feedEpisodes
            .filter { !currentEpisodeGuids.contains(it.guid) }
            .map { episode -> episode.copy(podcastId = podcastId) }

        val insertedEpisodes = if (newEpisodes.isEmpty()) {
            emptyList()
        } else {
            val insertedIds = episodeDao.insertAll(newEpisodes)
            newEpisodes.mapIndexed { index, episode ->
                episode.copy(id = insertedIds[index])
            }
        }

        podcastDao.updateLastFetchedAt(podcastId)
        podcastDao.updateEpisodeCount(podcastId, feedEpisodes.size)

        playlistAutoAddHandler.addNewEpisodesToAutoAddPlaylists(
            podcastId = podcastId,
            newEpisodes = insertedEpisodes
        )

        val podcast = podcastDao.getById(podcastId)
        if (podcast != null) {
            episodeStateImportSupport.applyPendingStatesForPodcast(
                feedUrl = podcast.feedUrl,
                guids = insertedEpisodes.map { it.guid }
            )
            episodeStateImportSupport.applyAllPendingStatesForPodcast(podcast.feedUrl)
            playlistImportSupport.applyPendingPlaylistEpisodesForPodcast(
                feedUrl = podcast.feedUrl,
                guids = insertedEpisodes.map { it.guid }
            )
            playlistImportSupport.applyAllPendingPlaylistEpisodesForPodcast(podcast.feedUrl)
        }

        return insertedEpisodes
    }
}
