package dev.josephwilliams.freecasts.data.export

import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.relation.EpisodeWithPodcast
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository

class FreeCastsBackupBuilder(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend fun buildBackup(
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): FreeCastsBackup {
        onProgress(0, 4, "Loading subscriptions…")
        val podcasts = podcastDao.getSubscribed()

        onProgress(1, 4, "Loading playlists…")
        val playlists = exportPlaylists(playlistDao, episodeDao, podcastDao)

        onProgress(2, 4, "Loading listening state…")
        val episodeStates = episodeDao.getStatefulEpisodesForExport()
            .map { it.toExportedEpisodeState() }

        onProgress(3, 4, "Loading settings…")
        val appSettings = userPreferencesRepository.toExportedAppSettings(podcastDao)

        onProgress(4, 4, "Preparing backup…")
        return FreeCastsBackup(
            version = BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            podcasts = podcasts.map { it.toExportedPodcast() },
            episodeStates = episodeStates,
            appSettings = appSettings,
            playlists = playlists,
        )
    }
}

internal fun EpisodeWithPodcast.toExportedEpisodeState(): ExportedEpisodeState {
    return ExportedEpisodeState(
        feedUrl = podcast.feedUrl,
        guid = episode.guid,
        isFavorite = episode.isFavorite,
        favoritedAt = episode.favoritedAt,
        isPlayed = episode.isPlayed,
        playbackPositionMs = episode.playbackPositionMs,
        lastPlayedAt = episode.lastPlayedAt,
        listenCount = episode.listenCount,
        replayPriority = episode.replayPriority
    )
}

internal fun mergeEpisodeState(local: Episode, imported: ExportedEpisodeState): Episode {
    val isPlayed = local.isPlayed || imported.isPlayed
    val isFavorite = local.isFavorite || imported.isFavorite
    val playbackPositionMs = when {
        isPlayed -> 0L
        else -> maxOf(local.playbackPositionMs, imported.playbackPositionMs)
    }
    val favoritedAt = when {
        local.isFavorite && imported.isFavorite -> maxOf(
            local.favoritedAt ?: 0L,
            imported.favoritedAt ?: 0L
        ).takeIf { it > 0 }
        local.isFavorite -> local.favoritedAt
        imported.isFavorite -> imported.favoritedAt
        else -> null
    }
    val lastPlayedAt = maxOf(
        local.lastPlayedAt ?: 0L,
        imported.lastPlayedAt ?: 0L
    ).takeIf { it > 0 }

    return local.copy(
        isFavorite = isFavorite,
        favoritedAt = favoritedAt,
        isPlayed = isPlayed,
        playbackPositionMs = playbackPositionMs,
        lastPlayedAt = lastPlayedAt,
        listenCount = maxOf(local.listenCount, imported.listenCount),
        replayPriority = if (isFavorite) {
            maxOf(local.replayPriority, imported.replayPriority)
        } else {
            local.replayPriority
        }
    )
}
