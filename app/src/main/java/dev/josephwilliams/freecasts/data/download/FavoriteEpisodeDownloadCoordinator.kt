package dev.josephwilliams.freecasts.data.download

import dev.josephwilliams.freecasts.data.local.dao.DownloadDao
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.entity.DownloadStatus as DbDownloadStatus
import dev.josephwilliams.freecasts.data.local.relation.EpisodeWithPodcast
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository

class FavoriteEpisodeDownloadCoordinator(
    private val episodeDao: EpisodeDao,
    private val downloadDao: DownloadDao,
    private val episodeDownloadEnqueuer: EpisodeDownloadEnqueuer,
    private val userPreferencesRepository: UserPreferencesRepository
) : FavoriteEpisodeDownloadHandler {
    override suspend fun markFavoriteDownloadsPendingAfterImport() {
        userPreferencesRepository.setFavoriteDownloadsAfterImportPending(true)
    }

    override suspend fun resumeFavoriteDownloadsIfNeeded(
        onProgress: (current: Int, total: Int, label: String) -> Unit
    ): Int {
        if (!userPreferencesRepository.isFavoriteDownloadsAfterImportPending()) {
            return 0
        }
        return enqueueDownloadsForAllFavorites(onProgress)
    }

    override suspend fun enqueueDownloadsForAllFavorites(
        onProgress: (current: Int, total: Int, label: String) -> Unit
    ): Int {
        val favorites = downloadableFavorites()
        if (favorites.isEmpty()) {
            userPreferencesRepository.setFavoriteDownloadsAfterImportPending(false)
            return 0
        }

        var enqueuedCount = 0
        favorites.forEachIndexed { index, favorite ->
            onProgress(
                index + 1,
                favorites.size,
                "Queueing ${favorite.episode.title.ifBlank { "favorite episode" }}…"
            )
            if (enqueueFavoriteDownload(favorite)) {
                enqueuedCount++
            }
        }

        clearPendingIfAllFavoritesDownloaded()
        return enqueuedCount
    }

    override suspend fun enqueueFavoriteDownloadIfNeeded(episodeId: Long): Boolean {
        val episodeWithPodcast = episodeDao.getEpisodeWithPodcast(episodeId) ?: return false
        if (!episodeWithPodcast.episode.isFavorite) return false
        return enqueueFavoriteDownload(episodeWithPodcast)
    }

    override suspend fun clearPendingIfAllFavoritesDownloaded() {
        if (!userPreferencesRepository.isFavoriteDownloadsAfterImportPending()) {
            return
        }

        val favorites = downloadableFavorites()
        if (favorites.isEmpty()) {
            userPreferencesRepository.setFavoriteDownloadsAfterImportPending(false)
            return
        }

        val allDownloaded = favorites.all { favorite ->
            downloadDao.getByEpisodeId(favorite.episode.id)?.status == DbDownloadStatus.COMPLETED
        }
        if (allDownloaded) {
            userPreferencesRepository.setFavoriteDownloadsAfterImportPending(false)
        }
    }

    private suspend fun downloadableFavorites(): List<EpisodeWithPodcast> {
        return episodeDao.getFavoritesWithPodcast()
            .filter { it.episode.audioUrl.isNotBlank() }
    }

    private suspend fun enqueueFavoriteDownload(favorite: EpisodeWithPodcast): Boolean {
        val episode = favorite.episode
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
                podcastName = favorite.podcast.title,
                downloadUrl = episode.audioUrl,
                mimeType = episode.mimeType
            )
        )
    }
}
