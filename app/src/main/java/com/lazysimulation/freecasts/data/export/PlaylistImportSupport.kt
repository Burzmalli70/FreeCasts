package com.lazysimulation.freecasts.data.export

import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.local.dao.PodcastDao
import com.lazysimulation.freecasts.data.local.entity.Playlist
import com.lazysimulation.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import com.lazysimulation.freecasts.tools.normalizeFeedUrl

data class PlaylistImportResult(
    val playlistIdMap: Map<Long, Long>,
    val importedPlaylistCount: Int,
    val updatedPlaylistCount: Int,
    val appliedPlaylistEpisodeCount: Int,
    val pendingPlaylistEpisodeCount: Int,
    val failedPlaylistEpisodeCount: Int,
)

enum class PlaylistEpisodeApplyResult {
    APPLIED,
    PENDING,
    FAILED,
}

class PlaylistImportSupport(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend fun importPlaylists(playlists: List<ExportedPlaylist>): PlaylistImportResult {
        val playlistIdMap = mutableMapOf<Long, Long>()
        var importedPlaylistCount = 0

        for (exported in playlists) {
            // Never treat exportId as a local primary key. Matching getById(exportId) on another
            // device can overwrite an unrelated playlist and clearPlaylist its episodes.
            val localId = playlistDao.insert(
                Playlist(
                    name = exported.name,
                    description = exported.description,
                    removeAfterListening = exported.removeAfterListening,
                    sortEpisodesAscending = exported.sortEpisodesAscending,
                    createdAt = exported.createdAt ?: System.currentTimeMillis(),
                    updatedAt = exported.updatedAt ?: System.currentTimeMillis(),
                )
            )
            playlistIdMap[exported.exportId] = localId
            importedPlaylistCount++
        }

        return PlaylistImportResult(
            playlistIdMap = playlistIdMap,
            importedPlaylistCount = importedPlaylistCount,
            updatedPlaylistCount = 0,
            appliedPlaylistEpisodeCount = 0,
            pendingPlaylistEpisodeCount = 0,
            failedPlaylistEpisodeCount = 0,
        )
    }

    suspend fun finalizePlaylistAutoAddSettings(
        playlists: List<ExportedPlaylist>,
        playlistIdMap: Map<Long, Long>,
    ) {
        for (exported in playlists) {
            val localId = playlistIdMap[exported.exportId] ?: continue
            val playlist = playlistDao.getById(localId) ?: continue
            playlistDao.update(
                playlist.copy(
                    autoAddPodcastIds = resolveAutoAddPodcastFeedUrls(
                        exported.autoAddPodcastFeedUrls,
                        podcastDao,
                    )
                )
            )
        }
    }

    suspend fun applyPlaylistEpisodes(
        playlists: List<ExportedPlaylist>,
        playlistIdMap: Map<Long, Long>,
    ): PlaylistImportResult {
        var appliedPlaylistEpisodeCount = 0
        var pendingPlaylistEpisodeCount = 0
        var failedPlaylistEpisodeCount = 0
        val pendingEpisodes = mutableListOf<PendingPlaylistEpisode>()

        for (exported in playlists) {
            val localPlaylistId = playlistIdMap[exported.exportId] ?: continue
            playlistDao.clearPlaylist(localPlaylistId)

            for (exportedEpisode in exported.episodes) {
                when (applyPlaylistEpisode(localPlaylistId, exportedEpisode)) {
                    PlaylistEpisodeApplyResult.APPLIED -> appliedPlaylistEpisodeCount++
                    PlaylistEpisodeApplyResult.PENDING -> {
                        pendingPlaylistEpisodeCount++
                        pendingEpisodes.add(
                            PendingPlaylistEpisode(
                                playlistId = localPlaylistId,
                                feedUrl = exportedEpisode.feedUrl.normalizeFeedUrl(),
                                guid = exportedEpisode.guid,
                                position = exportedEpisode.position,
                                addedAt = exportedEpisode.addedAt,
                            )
                        )
                    }
                    PlaylistEpisodeApplyResult.FAILED -> failedPlaylistEpisodeCount++
                }
            }
        }

        val existingPending = userPreferencesRepository.getPendingPlaylistEpisodes()
        userPreferencesRepository.setPendingPlaylistEpisodes(existingPending + pendingEpisodes)

        return PlaylistImportResult(
            playlistIdMap = playlistIdMap,
            importedPlaylistCount = 0,
            updatedPlaylistCount = 0,
            appliedPlaylistEpisodeCount = appliedPlaylistEpisodeCount,
            pendingPlaylistEpisodeCount = pendingPlaylistEpisodeCount,
            failedPlaylistEpisodeCount = failedPlaylistEpisodeCount,
        )
    }

    suspend fun applyPlaylistEpisode(
        playlistId: Long,
        exportedEpisode: ExportedPlaylistEpisode,
    ): PlaylistEpisodeApplyResult {
        val feedUrl = exportedEpisode.feedUrl.normalizeFeedUrl()
        if (feedUrl.isBlank() || exportedEpisode.guid.isBlank()) {
            return PlaylistEpisodeApplyResult.FAILED
        }

        val podcast = podcastDao.getByFeedUrl(feedUrl)
            ?: return PlaylistEpisodeApplyResult.PENDING
        if (!podcast.isSubscribed) return PlaylistEpisodeApplyResult.PENDING

        val episode = episodeDao.getByGuid(exportedEpisode.guid)
            ?: return PlaylistEpisodeApplyResult.PENDING

        playlistDao.insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(
                playlistId = playlistId,
                episodeId = episode.id,
                position = exportedEpisode.position,
                addedAt = exportedEpisode.addedAt ?: System.currentTimeMillis(),
            )
        )
        userPreferencesRepository.removePendingPlaylistEpisode(
            playlistId = playlistId,
            feedUrl = feedUrl,
            guid = exportedEpisode.guid,
        )
        return PlaylistEpisodeApplyResult.APPLIED
    }

    suspend fun applyPendingPlaylistEpisodesForPodcast(
        feedUrl: String,
        guids: Collection<String>,
    ) {
        if (guids.isEmpty()) return

        val normalizedFeedUrl = feedUrl.normalizeFeedUrl()
        val pendingForPodcast = userPreferencesRepository.getPendingPlaylistEpisodes()
            .filter { it.feedUrl.normalizeFeedUrl() == normalizedFeedUrl && it.guid in guids }

        for (pending in pendingForPodcast) {
            applyPlaylistEpisode(
                playlistId = pending.playlistId,
                exportedEpisode = ExportedPlaylistEpisode(
                    feedUrl = pending.feedUrl,
                    guid = pending.guid,
                    position = pending.position,
                    addedAt = pending.addedAt,
                )
            )
        }
    }

    suspend fun applyAllPendingPlaylistEpisodesForPodcast(feedUrl: String) {
        val normalizedFeedUrl = feedUrl.normalizeFeedUrl()
        val pendingForPodcast = userPreferencesRepository.getPendingPlaylistEpisodes()
            .filter { it.feedUrl.normalizeFeedUrl() == normalizedFeedUrl }

        for (pending in pendingForPodcast) {
            applyPlaylistEpisode(
                playlistId = pending.playlistId,
                exportedEpisode = ExportedPlaylistEpisode(
                    feedUrl = pending.feedUrl,
                    guid = pending.guid,
                    position = pending.position,
                    addedAt = pending.addedAt,
                )
            )
        }
    }
}
