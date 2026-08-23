package dev.josephwilliams.freecasts.data.export

import kotlinx.serialization.Serializable

const val PODCASTS_EXPORT_FILENAME = "podcasts.json"
const val BACKUP_VERSION = 4

@Serializable
data class ExportedPodcast(
    val name: String,
    val feedUrl: String,
    val autoDownloadNewEpisodes: Boolean = false,
    val episodeFilterPattern: String? = null,
    val autoAddToPlaylistIds: String? = null,
    val deleteAfterListening: Boolean = false,
    val keepFavoritesFromDeletion: Boolean = true,
    val keepInPlaylistsFromDeletion: Boolean = true,
    val maxDownloadsToKeep: Int? = null,
)

@Serializable
data class ExportedAppSettings(
    val autoDownloadOnSubscribe: Boolean = false,
    val keepFavoriteDownloads: Boolean = false,
    val deletePlayedDownloads: Boolean = false,
    val randomPodcastFavoriteFeedUrl: String? = null,
    val skipForwardIntervalSeconds: Int = 30,
    val skipBackwardIntervalSeconds: Int = 30,
    val externalPrevNextUsesSkipIntervals: Boolean = false,
)

@Serializable
data class ExportedPlaylistEpisode(
    val feedUrl: String,
    val guid: String,
    val position: Int = 0,
    val addedAt: Long? = null,
)

@Serializable
data class ExportedPlaylist(
    val exportId: Long,
    val name: String,
    val description: String? = null,
    val removeAfterListening: Boolean = false,
    val autoAddPodcastFeedUrls: List<String> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val episodes: List<ExportedPlaylistEpisode> = emptyList(),
)

@Serializable
data class PendingPlaylistEpisode(
    val playlistId: Long,
    val feedUrl: String,
    val guid: String,
    val position: Int = 0,
    val addedAt: Long? = null,
)

@Serializable
data class ExportedEpisodeState(
    val feedUrl: String,
    val guid: String,
    val isFavorite: Boolean = false,
    val favoritedAt: Long? = null,
    val isPlayed: Boolean = false,
    val playbackPositionMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val listenCount: Int = 0,
    val replayPriority: Int = 0
)

@Serializable
data class FreeCastsBackup(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val podcasts: List<ExportedPodcast> = emptyList(),
    val episodeStates: List<ExportedEpisodeState> = emptyList(),
    val appSettings: ExportedAppSettings? = null,
    val playlists: List<ExportedPlaylist> = emptyList(),
)

/** @deprecated Use [FreeCastsBackup]. Kept for decoding v1 files. */
@Serializable
data class PodcastSubscriptionsExport(
    val version: Int = 1,
    val podcasts: List<ExportedPodcast>
)

data class BackupImportResult(
    val importedPodcastCount: Int,
    val skippedPodcastCount: Int,
    val failedPodcastCount: Int,
    val importedPlaylistCount: Int = 0,
    val updatedPlaylistCount: Int = 0,
    val appliedPlaylistEpisodeCount: Int = 0,
    val pendingPlaylistEpisodeCount: Int = 0,
    val failedPlaylistEpisodeCount: Int = 0,
    val appliedEpisodeStateCount: Int,
    val pendingEpisodeStateCount: Int,
    val failedEpisodeStateCount: Int
) {
    fun toMessage(): String = buildString {
        val playlistCount = importedPlaylistCount + updatedPlaylistCount
        if (playlistCount > 0) {
            append("Imported $playlistCount playlist${if (playlistCount == 1) "" else "s"}")
        }
        if (importedPodcastCount > 0 || skippedPodcastCount > 0 || failedPodcastCount > 0) {
            if (isNotEmpty()) append(", ")
            append("imported $importedPodcastCount podcast${if (importedPodcastCount == 1) "" else "s"}")
            if (skippedPodcastCount > 0) {
                append(", $skippedPodcastCount already subscribed")
            }
            if (failedPodcastCount > 0) {
                append(", $failedPodcastCount podcast${if (failedPodcastCount == 1) "" else "s"} failed")
            }
        } else if (isEmpty()) {
            append("Imported $importedPodcastCount podcast${if (importedPodcastCount == 1) "" else "s"}")
        }
        if (appliedPlaylistEpisodeCount > 0) {
            append(", restored $appliedPlaylistEpisodeCount playlist episode${if (appliedPlaylistEpisodeCount == 1) "" else "s"}")
        }
        if (pendingPlaylistEpisodeCount > 0) {
            append(", $pendingPlaylistEpisodeCount playlist episode${if (pendingPlaylistEpisodeCount == 1) "" else "s"} pending until sync")
        }
        if (failedPlaylistEpisodeCount > 0) {
            append(", $failedPlaylistEpisodeCount playlist episode${if (failedPlaylistEpisodeCount == 1) "" else "s"} failed")
        }
        if (appliedEpisodeStateCount > 0) {
            append(", restored $appliedEpisodeStateCount episode state${if (appliedEpisodeStateCount == 1) "" else "s"}")
        }
        if (pendingEpisodeStateCount > 0) {
            append(", $pendingEpisodeStateCount pending until sync")
        }
        if (failedEpisodeStateCount > 0) {
            append(", $failedEpisodeStateCount episode state${if (failedEpisodeStateCount == 1) "" else "s"} failed")
        }
    }
}
