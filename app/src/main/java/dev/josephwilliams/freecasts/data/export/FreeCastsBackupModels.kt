package dev.josephwilliams.freecasts.data.export

import kotlinx.serialization.Serializable

const val PODCASTS_EXPORT_FILENAME = "podcasts.json"
const val BACKUP_VERSION = 3

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
    val appliedEpisodeStateCount: Int,
    val pendingEpisodeStateCount: Int,
    val failedEpisodeStateCount: Int
) {
    fun toMessage(): String = buildString {
        append("Imported $importedPodcastCount podcast${if (importedPodcastCount == 1) "" else "s"}")
        if (skippedPodcastCount > 0) {
            append(", $skippedPodcastCount already subscribed")
        }
        if (failedPodcastCount > 0) {
            append(", $failedPodcastCount podcast${if (failedPodcastCount == 1) "" else "s"} failed")
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
