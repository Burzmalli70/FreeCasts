package dev.josephwilliams.freecasts.data.export

import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import dev.josephwilliams.freecasts.tools.normalizeFeedUrl
import kotlinx.coroutines.flow.first

internal fun Podcast.toExportedPodcast(): ExportedPodcast {
    return ExportedPodcast(
        name = title,
        feedUrl = feedUrl,
        autoDownloadNewEpisodes = autoDownloadNewEpisodes,
        episodeFilterPattern = episodeFilterPattern,
        autoAddToPlaylistIds = autoAddToPlaylistIds,
        deleteAfterListening = deleteAfterListening,
        keepFavoritesFromDeletion = keepFavoritesFromDeletion,
        keepInPlaylistsFromDeletion = keepInPlaylistsFromDeletion,
        maxDownloadsToKeep = maxDownloadsToKeep,
    )
}

internal suspend fun UserPreferencesRepository.toExportedAppSettings(
    podcastDao: PodcastDao,
): ExportedAppSettings {
    val preferences = userPreferences.first()
    val randomFeedUrl = preferences.randomPodcastFavoriteId
        .takeIf { it >= 0 }
        ?.let { podcastDao.getById(it)?.feedUrl }

    return ExportedAppSettings(
        autoDownloadOnSubscribe = preferences.autoDownloadOnSubscribe,
        keepFavoriteDownloads = preferences.keepFavoriteDownloads,
        deletePlayedDownloads = preferences.deletePlayedDownloads,
        randomPodcastFavoriteFeedUrl = randomFeedUrl,
    )
}

internal suspend fun applyExportedPodcastSettings(
    podcastDao: PodcastDao,
    playlistDao: PlaylistDao,
    podcastId: Long,
    exported: ExportedPodcast,
) {
    val podcast = podcastDao.getById(podcastId) ?: return
    val filteredPlaylistIds = filterValidPlaylistIds(exported.autoAddToPlaylistIds, playlistDao)
    podcastDao.update(
        podcast.copy(
            autoDownloadNewEpisodes = exported.autoDownloadNewEpisodes,
            episodeFilterPattern = exported.episodeFilterPattern,
            autoAddToPlaylistIds = filteredPlaylistIds,
            deleteAfterListening = exported.deleteAfterListening,
            keepFavoritesFromDeletion = exported.keepFavoritesFromDeletion,
            keepInPlaylistsFromDeletion = exported.keepInPlaylistsFromDeletion,
            maxDownloadsToKeep = exported.maxDownloadsToKeep,
        )
    )
}

internal suspend fun applyExportedAppSettings(
    userPreferencesRepository: UserPreferencesRepository,
    podcastDao: PodcastDao,
    settings: ExportedAppSettings,
) {
    userPreferencesRepository.setAutoDownloadOnSubscribe(settings.autoDownloadOnSubscribe)
    userPreferencesRepository.setKeepFavoriteDownloads(settings.keepFavoriteDownloads)
    userPreferencesRepository.setDeletePlayedDownloads(settings.deletePlayedDownloads)

    val randomFeedUrl = settings.randomPodcastFavoriteFeedUrl?.normalizeFeedUrl()
    if (randomFeedUrl.isNullOrBlank()) {
        userPreferencesRepository.clearRandomPodcastId()
        return
    }

    val podcast = podcastDao.getByFeedUrl(randomFeedUrl)
    if (podcast?.isSubscribed == true) {
        userPreferencesRepository.setRandomPodcastId(podcast.id)
    } else {
        userPreferencesRepository.clearRandomPodcastId()
    }
}

private suspend fun filterValidPlaylistIds(
    playlistIds: String?,
    playlistDao: PlaylistDao,
): String? {
    if (playlistIds.isNullOrBlank()) return null
    val validIds = playlistIds
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .filter { playlistDao.getById(it) != null }
    return validIds.takeIf { it.isNotEmpty() }?.joinToString(",")
}
