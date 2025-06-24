package dev.josephwilliams.filedownloader.service

import dev.josephwilliams.filedownloader.model.DownloadInfo

class PersistenceManager {
    suspend fun getAllDownloadStatuses(): List<DownloadInfo> {
        return emptyList()
    }

    suspend fun getDownloadInfo(id: String): DownloadInfo? {
        return null
    }

    suspend fun updateDownloadInfo(info: DownloadInfo) {

    }
}