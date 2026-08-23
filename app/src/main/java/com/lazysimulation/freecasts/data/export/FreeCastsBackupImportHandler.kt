package com.lazysimulation.freecasts.data.export

import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import com.lazysimulation.freecasts.data.local.dao.PodcastDao
import com.lazysimulation.freecasts.data.repository.PodcastRepository
import com.lazysimulation.freecasts.tools.normalizeFeedUrl

class FreeCastsBackupImportHandler(
    private val podcastDao: PodcastDao,
    private val playlistDao: PlaylistDao,
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val episodeStateImportSupport: EpisodeStateImportSupport,
    private val playlistImportSupport: PlaylistImportSupport,
) {
    suspend fun importBackup(
        backup: FreeCastsBackup,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): BackupImportResult {
        val podcasts = backup.podcasts
        val playlists = backup.playlists
        val episodeStates = backup.episodeStates
        val totalSteps = playlists.size + podcasts.size + episodeStates.size.coerceAtLeast(1)
        var currentStep = 0

        var importedPodcastCount = 0
        var skippedPodcastCount = 0
        var failedPodcastCount = 0
        var importedPlaylistCount = 0
        var updatedPlaylistCount = 0
        var appliedPlaylistEpisodeCount = 0
        var pendingPlaylistEpisodeCount = 0
        var failedPlaylistEpisodeCount = 0
        var appliedEpisodeStateCount = 0
        var pendingEpisodeStateCount = 0
        var failedEpisodeStateCount = 0

        val pendingStates = mutableListOf<ExportedEpisodeState>()

        backup.appSettings?.let { appSettings ->
            onProgress(0, totalSteps, "Restoring app settings…")
            applyExportedAppSettings(userPreferencesRepository, podcastDao, appSettings)
        }

        val playlistImportResult = if (playlists.isNotEmpty()) {
            onProgress(0, totalSteps, "Importing playlists…")
            playlistImportSupport.importPlaylists(playlists)
        } else {
            PlaylistImportResult(emptyMap(), 0, 0, 0, 0, 0)
        }
        val playlistIdMap = playlistImportResult.playlistIdMap
        importedPlaylistCount = playlistImportResult.importedPlaylistCount
        updatedPlaylistCount = playlistImportResult.updatedPlaylistCount

        for (exportedPodcast in podcasts) {
            currentStep++
            onProgress(currentStep, totalSteps, "Importing ${exportedPodcast.name.ifBlank { "podcast" }}…")

            val feedUrl = exportedPodcast.feedUrl.normalizeFeedUrl()
            if (feedUrl.isBlank()) {
                failedPodcastCount++
                continue
            }

            val existing = podcastDao.getByFeedUrl(feedUrl)
            if (existing?.isSubscribed == true) {
                skippedPodcastCount++
                applyExportedPodcastSettings(
                    podcastDao = podcastDao,
                    playlistDao = playlistDao,
                    podcastId = existing.id,
                    exported = exportedPodcast,
                    playlistIdMap = playlistIdMap,
                )
                val refreshResult = podcastRepository.refreshPodcast(existing.id)
                if (refreshResult.isFailure) {
                    failedPodcastCount++
                }
                continue
            }

            val subscribeResult = podcastRepository.subscribeToPodcast(feedUrl)
            if (subscribeResult.isSuccess) {
                importedPodcastCount++
                subscribeResult.getOrNull()?.let { podcastId ->
                    applyExportedPodcastSettings(
                        podcastDao = podcastDao,
                        playlistDao = playlistDao,
                        podcastId = podcastId,
                        exported = exportedPodcast,
                        playlistIdMap = playlistIdMap,
                    )
                }
            } else {
                failedPodcastCount++
            }
        }

        if (playlists.isNotEmpty()) {
            onProgress(currentStep, totalSteps, "Finalizing playlist settings…")
            playlistImportSupport.finalizePlaylistAutoAddSettings(playlists, playlistIdMap)

            val episodeResult = playlistImportSupport.applyPlaylistEpisodes(playlists, playlistIdMap)
            appliedPlaylistEpisodeCount = episodeResult.appliedPlaylistEpisodeCount
            pendingPlaylistEpisodeCount = episodeResult.pendingPlaylistEpisodeCount
            failedPlaylistEpisodeCount = episodeResult.failedPlaylistEpisodeCount
        }

        for (exportedState in episodeStates) {
            currentStep++
            onProgress(currentStep, totalSteps, "Restoring episode state…")

            val feedUrl = exportedState.feedUrl.normalizeFeedUrl()
            if (feedUrl.isBlank() || exportedState.guid.isBlank()) {
                failedEpisodeStateCount++
                continue
            }

            when (episodeStateImportSupport.applyEpisodeState(exportedState.copy(feedUrl = feedUrl))) {
                EpisodeStateApplyResult.APPLIED -> appliedEpisodeStateCount++
                EpisodeStateApplyResult.PENDING -> {
                    pendingEpisodeStateCount++
                    pendingStates.add(exportedState.copy(feedUrl = feedUrl))
                }
                EpisodeStateApplyResult.FAILED -> failedEpisodeStateCount++
            }
        }

        val existingPending = userPreferencesRepository.getPendingEpisodeStates()
        userPreferencesRepository.setPendingEpisodeStates(existingPending + pendingStates)

        return BackupImportResult(
            importedPodcastCount = importedPodcastCount,
            skippedPodcastCount = skippedPodcastCount,
            failedPodcastCount = failedPodcastCount,
            importedPlaylistCount = importedPlaylistCount,
            updatedPlaylistCount = updatedPlaylistCount,
            appliedPlaylistEpisodeCount = appliedPlaylistEpisodeCount,
            pendingPlaylistEpisodeCount = pendingPlaylistEpisodeCount,
            failedPlaylistEpisodeCount = failedPlaylistEpisodeCount,
            appliedEpisodeStateCount = appliedEpisodeStateCount,
            pendingEpisodeStateCount = pendingEpisodeStateCount,
            failedEpisodeStateCount = failedEpisodeStateCount
        )
    }
}

enum class EpisodeStateApplyResult {
    APPLIED,
    PENDING,
    FAILED
}
