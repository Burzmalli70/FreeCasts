package dev.josephwilliams.freecasts.data.export

import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.tools.normalizeFeedUrl

internal suspend fun exportPlaylists(
    playlistDao: PlaylistDao,
    episodeDao: EpisodeDao,
    podcastDao: PodcastDao,
): List<ExportedPlaylist> {
    return playlistDao.getAllOrderedByName().map { playlist ->
        exportPlaylist(playlist, playlistDao, episodeDao, podcastDao)
    }
}

private suspend fun exportPlaylist(
    playlist: Playlist,
    playlistDao: PlaylistDao,
    episodeDao: EpisodeDao,
    podcastDao: PodcastDao,
): ExportedPlaylist {
    val episodes = playlistDao.getPlaylistEpisodesOrdered(playlist.id).mapNotNull { crossRef ->
        val episode = episodeDao.getById(crossRef.episodeId) ?: return@mapNotNull null
        val podcast = podcastDao.getById(episode.podcastId) ?: return@mapNotNull null
        ExportedPlaylistEpisode(
            feedUrl = podcast.feedUrl,
            guid = episode.guid,
            position = crossRef.position,
            addedAt = crossRef.addedAt,
        )
    }

    val autoAddFeedUrls = playlist.getAutoAddPodcastIdList()
        .mapNotNull { podcastDao.getById(it)?.feedUrl }

    return ExportedPlaylist(
        exportId = playlist.id,
        name = playlist.name,
        description = playlist.description,
        removeAfterListening = playlist.removeAfterListening,
        autoAddPodcastFeedUrls = autoAddFeedUrls,
        createdAt = playlist.createdAt,
        updatedAt = playlist.updatedAt,
        episodes = episodes,
    )
}

internal suspend fun resolveAutoAddPodcastFeedUrls(
    feedUrls: List<String>,
    podcastDao: PodcastDao,
): String? {
    val podcastIds = feedUrls
        .map { it.normalizeFeedUrl() }
        .filter { it.isNotBlank() }
        .mapNotNull { feedUrl -> podcastDao.getByFeedUrl(feedUrl)?.takeIf { it.isSubscribed }?.id }
        .distinct()
    return podcastIds.takeIf { it.isNotEmpty() }?.joinToString(",")
}

internal fun remapPlaylistIds(
    playlistIds: String?,
    playlistIdMap: Map<Long, Long>,
): String? {
    if (playlistIds.isNullOrBlank()) return null
    val remapped = playlistIds
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .mapNotNull { exportId -> playlistIdMap[exportId] ?: exportId }
    return remapped.takeIf { it.isNotEmpty() }?.joinToString(",")
}
