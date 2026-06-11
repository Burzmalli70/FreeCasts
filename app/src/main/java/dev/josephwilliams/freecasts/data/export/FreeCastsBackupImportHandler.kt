package dev.josephwilliams.freecasts.data.export

import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.repository.PodcastRepository
import dev.josephwilliams.freecasts.tools.normalizeFeedUrl

class FreeCastsBackupImportHandler(
    private val podcastDao: PodcastDao,
    private val playlistDao: PlaylistDao,
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val episodeStateImportSupport: EpisodeStateImportSupport
) {
    suspend fun importBackup(
        backup: FreeCastsBackup,
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): BackupImportResult {
        val podcasts = backup.podcasts
        val episodeStates = backup.episodeStates
        val totalSteps = podcasts.size + episodeStates.size.coerceAtLeast(1)
        var currentStep = 0

        var importedPodcastCount = 0
        var skippedPodcastCount = 0
        var failedPodcastCount = 0
        var appliedEpisodeStateCount = 0
        var pendingEpisodeStateCount = 0
        var failedEpisodeStateCount = 0

        val pendingStates = mutableListOf<ExportedEpisodeState>()

        backup.appSettings?.let { appSettings ->
            onProgress(0, totalSteps, "Restoring app settings…")
            applyExportedAppSettings(userPreferencesRepository, podcastDao, appSettings)
        }

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
                    )
                }
            } else {
                failedPodcastCount++
            }
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
