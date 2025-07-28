package dev.josephwilliams.freecasts.downloader

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemDownloader(private val context: Context) {

    private val downloadManager = ContextCompat.getSystemService(context, DownloadManager::class.java)

    fun startDownload(
        url: String,
        title: String,
        description: String,
        destinationFileName: String
    ): Long? {
        if (downloadManager == null) {
            Toast.makeText(context, "DownloadManager not available", Toast.LENGTH_LONG).show()
            return null
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(description)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
        if (destinationDir != null) {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PODCASTS, destinationFileName)
        } else {
            Toast.makeText(context, "Cannot access app-specific storage", Toast.LENGTH_LONG).show()
            return null
        }

        try {
            return downloadManager.enqueue(request)
        } catch (e: Exception) {
            Toast.makeText(context, "Error starting download: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    suspend fun getDownloadStatus(downloadId: Long): DownloadStatusInfo? {
        if (downloadManager == null) return null

        return withContext(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            var cursor: Cursor? = null
            try {
                cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val reasonColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val totalBytesColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val downloadedBytesColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val localUriColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI) // Uri to the downloaded file

                    val status = cursor.getInt(statusColumnIndex)
                    val reason = cursor.getInt(reasonColumnIndex)
                    val totalBytes = cursor.getLong(totalBytesColumnIndex)
                    val downloadedBytes = cursor.getLong(downloadedBytesColumnIndex)
                    val localUri = cursor.getString(localUriColumnIndex)

                    DownloadStatusInfo(
                        downloadId = downloadId,
                        status = status,
                        reason = reason,
                        totalBytes = totalBytes,
                        downloadedBytes = downloadedBytes,
                        localUri = localUri?.let { Uri.parse(it) }
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            } finally {
                cursor?.close()
            }
        }
    }

    fun cancelDownload(downloadId: Long): Int {
        return downloadManager?.remove(downloadId) ?: 0
    }

    fun getMimeTypeForDownloadedFile(downloadId: Long): String? {
        return downloadManager?.getMimeTypeForDownloadedFile(downloadId)
    }
}

data class DownloadStatusInfo(
    val downloadId: Long,
    val status: Int,
    val reason: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val localUri: Uri?
) {
    val isSuccessful: Boolean
        get() = status == DownloadManager.STATUS_SUCCESSFUL

    val isFailed: Boolean
        get() = status == DownloadManager.STATUS_FAILED

    val isPaused: Boolean
        get() = status == DownloadManager.STATUS_PAUSED

    val isRunningOrPending: Boolean
        get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
}