package com.lazysimulation.freecasts.data.download

import com.lazysimulation.freecasts.data.local.dao.DownloadDao
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.entity.DownloadStatus as DbDownloadStatus
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Enqueues episode downloads when the global auto-download setting is enabled
 * (subscribe + playlist add).
 */
class AutoDownloadHandler(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val episodeDao: EpisodeDao,
    private val downloadDao: DownloadDao,
    private val episodeDownloadEnqueuer: EpisodeDownloadEnqueuer,
) {
    /**
     * Downloads [episodeId] when auto-download is enabled and the episode has audio.
     * Skips episodes that are already downloaded or in progress.
     */
    suspend fun downloadEpisodeIfEnabled(episodeId: Long): Boolean {
        if (!userPreferencesRepository.autoDownloadOnSubscribe.first()) return false
        return enqueueEpisodeDownload(episodeId)
    }

    /**
     * Downloads the most recent episode for [podcastId] when auto-download is enabled.
     */
    suspend fun downloadLatestEpisodeIfEnabled(podcastId: Long): Boolean {
        if (!userPreferencesRepository.autoDownloadOnSubscribe.first()) return false
        val latest = episodeDao.getAllByPodcastId(podcastId).firstOrNull() ?: return false
        return enqueueEpisodeDownload(latest.id)
    }

    private suspend fun enqueueEpisodeDownload(episodeId: Long): Boolean {
        val episodeWithPodcast = episodeDao.getEpisodeWithPodcast(episodeId) ?: return false
        val episode = episodeWithPodcast.episode
        if (episode.audioUrl.isBlank()) return false

        val existingDownload = downloadDao.getByEpisodeId(episode.id)
        if (existingDownload?.status == DbDownloadStatus.COMPLETED ||
            existingDownload?.status == DbDownloadStatus.PENDING ||
            existingDownload?.status == DbDownloadStatus.DOWNLOADING
        ) {
            return false
        }

        if (existingDownload?.status == DbDownloadStatus.FAILED ||
            existingDownload?.status == DbDownloadStatus.CANCELLED
        ) {
            downloadDao.deleteByEpisodeId(episode.id)
        }

        return episodeDownloadEnqueuer.enqueueDownload(
            DownloadRequest(
                episodeId = episode.id,
                episodeName = episode.title,
                podcastName = episodeWithPodcast.podcast.title,
                downloadUrl = episode.audioUrl,
                mimeType = episode.mimeType
            )
        )
    }
}
