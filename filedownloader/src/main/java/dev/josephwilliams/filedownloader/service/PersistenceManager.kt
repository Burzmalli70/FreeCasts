package dev.josephwilliams.filedownloader.service

import dev.josephwilliams.filedownloader.model.DownloadInfo
import dev.josephwilliams.filedownloader.model.DownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class PersistenceManager {
    fun getDownloadsByStateAsFlow(state: DownloadState): Flow<List<DownloadInfo>> {
        return emptyFlow()
    }

    fun getAllDownloadsAsFlow(): Flow<List<DownloadInfo>> {
        return emptyFlow()
    }

    suspend fun getAllDownloadStatuses(): List<DownloadInfo> {
        return emptyList()
    }

    suspend fun getDownloadInfo(id: String): DownloadInfo? {
        return null
    }

    suspend fun updateDownloadInfo(info: DownloadInfo) {

    }

    suspend fun deleteDownload(id: String) {

    }
}