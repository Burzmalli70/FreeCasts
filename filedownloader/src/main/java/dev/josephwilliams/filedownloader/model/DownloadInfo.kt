package dev.josephwilliams.filedownloader.model

import java.time.Clock
import java.time.Instant

data class DownloadInfo(
    val id: String,
    val url: String,
    val fileName: String,
    val destination: String,
    var totalBytes: Long,
    var downloadedByteCount: Long = 0,
    var state: DownloadState = DownloadState.QUEUED,
    val created: Instant = Instant.now(),
    val lastModified: Instant = Instant.now(),
    var error: String? = null,
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
