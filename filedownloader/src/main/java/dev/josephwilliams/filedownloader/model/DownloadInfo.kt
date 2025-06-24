package dev.josephwilliams.filedownloader.model

import java.time.Clock
import java.time.Instant

data class DownloadInfo(
    val id: String,
    val url: String,
    val fileName: String,
    val destination: String,
    val totalBytes: Long,
    val downloadedByteCount: Long = 0,
    val state: DownloadState = DownloadState.QUEUED,
    val created: Instant = Instant.now(),
    val lastModified: Instant = Instant.now(),
    val error: String? = null,
    val headers: String? = null
) {
    val progress: Float
        get() {
            if (totalBytes < 1f) return 0f

            return downloadedByteCount.toFloat() / totalBytes
        }
}

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}
