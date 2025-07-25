package dev.joewilliams.downloadmanager

data class Download(
    val id: String,
    val currentBytes: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val url: String,
    val filePath: String,
    val fileName: String
) {
    val fullPath
        get() = "$filePath/$fileName"
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}