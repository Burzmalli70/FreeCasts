package com.lazysimulation.freecasts.data.download

/**
 * Represents a request to download an episode.
 */
data class DownloadRequest(
    /** The episode ID in the local database */
    val episodeId: Long,
    
    /** The episode title (used for notification) */
    val episodeName: String,
    
    /** The podcast name (used for notification and folder organization) */
    val podcastName: String,
    
    /** The URL to download the episode audio from */
    val downloadUrl: String,
    
    /** Optional MIME type of the audio file */
    val mimeType: String? = null
)

/**
 * Represents the current state of a download in the queue or in progress.
 */
data class DownloadState(
    /** The episode ID */
    val episodeId: Long,
    
    /** The episode name */
    val episodeName: String,
    
    /** The podcast name */
    val podcastName: String,
    
    /** Current status of the download */
    val status: DownloadStatus,
    
    /** Progress percentage (0-100), null if not started or queued */
    val progressPercent: Int? = null,
    
    /** Bytes downloaded so far */
    val downloadedBytes: Long = 0,
    
    /** Total bytes to download, null if unknown */
    val totalBytes: Long? = null,
    
    /** Error message if status is FAILED */
    val errorMessage: String? = null,
    
    /** Android DownloadManager download ID, null if queued */
    val downloadManagerId: Long? = null
)

/**
 * Status of a download in the EpisodeDownloadManager.
 */
enum class DownloadStatus {
    /** Download is waiting in the queue */
    QUEUED,
    
    /** Download is currently in progress */
    DOWNLOADING,
    
    /** Download is paused */
    PAUSED,
    
    /** Download completed successfully */
    COMPLETED,
    
    /** Download failed */
    FAILED,
    
    /** Download was cancelled */
    CANCELLED
}

/**
 * Overall state of the download manager, observable by ViewModels.
 */
data class DownloadManagerState(
    /** List of downloads currently in progress */
    val activeDownloads: List<DownloadState> = emptyList(),
    
    /** List of downloads waiting in the queue */
    val queuedDownloads: List<DownloadState> = emptyList(),
    
    /** Maximum number of concurrent downloads allowed */
    val maxConcurrentDownloads: Int = 3,
    
    /** Whether downloads are allowed on metered connections */
    val allowMeteredDownloads: Boolean = false
) {
    /** Total number of downloads (active + queued) */
    val totalPendingCount: Int
        get() = activeDownloads.size + queuedDownloads.size
    
    /** Whether there are any active downloads */
    val hasActiveDownloads: Boolean
        get() = activeDownloads.isNotEmpty()
    
    /** Whether there are any queued downloads */
    val hasQueuedDownloads: Boolean
        get() = queuedDownloads.isNotEmpty()
}
