package dev.josephwilliams.freecasts.data.export

import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import dev.josephwilliams.freecasts.tools.normalizeFeedUrl

class EpisodeStateImportSupport(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun applyEpisodeState(exportedState: ExportedEpisodeState): EpisodeStateApplyResult {
        val feedUrl = exportedState.feedUrl.normalizeFeedUrl()
        val podcast = podcastDao.getByFeedUrl(feedUrl) ?: return EpisodeStateApplyResult.PENDING
        if (!podcast.isSubscribed) return EpisodeStateApplyResult.PENDING

        val episode = episodeDao.getByGuid(exportedState.guid)
            ?: return EpisodeStateApplyResult.PENDING

        episodeDao.update(mergeEpisodeState(episode, exportedState))
        userPreferencesRepository.removePendingEpisodeState(feedUrl, exportedState.guid)
        return EpisodeStateApplyResult.APPLIED
    }

    suspend fun applyPendingStatesForPodcast(feedUrl: String, guids: Collection<String>) {
        if (guids.isEmpty()) return

        val normalizedFeedUrl = feedUrl.normalizeFeedUrl()
        val pendingForPodcast = userPreferencesRepository.getPendingEpisodeStates()
            .filter { it.feedUrl.normalizeFeedUrl() == normalizedFeedUrl && it.guid in guids }

        for (state in pendingForPodcast) {
            applyEpisodeState(state)
        }
    }

    suspend fun applyAllPendingStatesForPodcast(feedUrl: String) {
        val normalizedFeedUrl = feedUrl.normalizeFeedUrl()
        val pendingForPodcast = userPreferencesRepository.getPendingEpisodeStates()
            .filter { it.feedUrl.normalizeFeedUrl() == normalizedFeedUrl }

        for (state in pendingForPodcast) {
            applyEpisodeState(state)
        }
    }
}
