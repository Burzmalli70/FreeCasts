package dev.joewilliams.downloadmanager

data class Download(
    val id: Int,
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
    FAILED;

    companion object {
        fun safeFromString(value: String): DownloadStatus {
            return DownloadStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FAILED
        }
    }
}