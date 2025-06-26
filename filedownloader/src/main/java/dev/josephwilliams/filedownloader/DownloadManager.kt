package dev.josephwilliams.filedownloader

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.Gson
import dev.josephwilliams.filedownloader.model.DownloadInfo
import dev.josephwilliams.filedownloader.model.DownloadRequest
import dev.josephwilliams.filedownloader.model.DownloadState
import dev.josephwilliams.filedownloader.service.DownloadService
import dev.josephwilliams.filedownloader.service.PersistenceManager
import kotlinx.coroutines.flow.Flow
import java.io.File

class DownloadManager(private val context: Context) {

    private val persistenceManager by lazy {
        PersistenceManager()
    }

    private val workManager by lazy {
        WorkManager.getInstance(context)
    }

    // Get all downloads as Flow
    fun getAllDownloads(): Flow<List<DownloadInfo>> {
        return persistenceManager.getAllDownloadsAsFlow()
    }

    // Get download by ID
    suspend fun getDownload(downloadId: String): DownloadInfo? {
        return persistenceManager.getDownloadInfo(downloadId)
    }

    // Get downloads by state
    fun getDownloadsByState(state: DownloadState): Flow<List<DownloadInfo>> {
        return persistenceManager.getDownloadsByStateAsFlow(state)
    }

    // Enqueue a new download
    suspend fun enqueue(
        url: String,
        fileName: String,
        destinationPath: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        // Create download request
        val downloadRequest = DownloadRequest(
            url = url,
            fileName = fileName,
            destinationPath = destinationPath,
            headers = headers
        )

        // Insert download into database
        val downloadEntity = DownloadInfo(
            id = downloadRequest.id,
            url = downloadRequest.url,
            fileName = downloadRequest.fileName,
            destination = downloadRequest.destinationPath,
            headers = Gson().toJson(downloadRequest.headers),
            totalBytes = -1
        )

        persistenceManager.updateDownloadInfo(downloadEntity)

        // Start download service
        DownloadService.startDownload(context, downloadRequest.id)

        return downloadRequest.id
    }

    // Start a download
    fun start(downloadId: String) {
        DownloadService.startDownload(context, downloadId)
    }

    // Pause a download
    fun pause(downloadId: String) {
        DownloadService.pauseDownload(context, downloadId)
    }

    // Resume a download
    fun resume(downloadId: String) {
        DownloadService.resumeDownload(context, downloadId)
    }

    // Cancel a download
    fun cancel(downloadId: String) {
        DownloadService.cancelDownload(context, downloadId)

        // Cancel any WorkManager work
        workManager.cancelUniqueWork(downloadId)
    }

    // Delete a download and its files
    suspend fun delete(downloadId: String, deleteFile: Boolean = true) {
        // Cancel first
        cancel(downloadId)

        if (deleteFile) {
            // Delete associated files
            val download = persistenceManager.getDownloadInfo(downloadId)
            download?.let {
                val file = File(it.destination, it.fileName)
                val tempFile = File("${file.absolutePath}.tmp")

                if (file.exists()) {
                    file.delete()
                }

                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

        // Delete from database
        persistenceManager.deleteDownload(downloadId)
    }
}