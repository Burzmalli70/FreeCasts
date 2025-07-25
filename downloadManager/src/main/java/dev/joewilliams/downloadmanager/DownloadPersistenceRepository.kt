package dev.joewilliams.downloadmanager

abstract class DownloadPersistenceRepository {
    abstract suspend fun getDownloadsByStatus(status: DownloadStatus): List<Download>
    abstract suspend fun saveDownload(download: Download)
    abstract suspend fun deleteDownload(download: Download)
}