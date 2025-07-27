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
        title: String, // Title for the download notification
        description: String, // Description for the download notification
        destinationFileName: String // e.g., "episode_audio.mp3"
    ): Long? {
        if (downloadManager == null) {
            Toast.makeText(context, "DownloadManager not available", Toast.LENGTH_LONG).show()
            return null
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(description)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) // Show notification during and after download
            .setAllowedOverMetered(true) // Allow download over mobile data (configurable)
            .setAllowedOverRoaming(false) // Disallow download over roaming (configurable)

        // --- Choose Destination ---
        // Option 1: App-specific directory (Recommended for most cases, no extra permissions needed post API 18)
        // Files are private to your app and are removed when the app is uninstalled.
        val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) // Or DIRECTORY_MUSIC, DIRECTORY_DOWNLOADS etc.
        if (destinationDir != null) {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PODCASTS, destinationFileName)
        } else {
            // Fallback or error handling if external files dir is not available
            Toast.makeText(context, "Cannot access app-specific storage", Toast.LENGTH_LONG).show()
            return null
        }

        // Option 2: Public Downloads directory (Requires more careful handling with Scoped Storage on API 29+)
        // If you use this, you might need to handle MediaStore for files to be visible to other apps.
        // request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationFileName)

        try {
            return downloadManager.enqueue(request) // Returns a unique download ID
        } catch (e: Exception) {
            // Handle potential exceptions, e.g., SecurityException if permissions are missing for public dirs on older APIs
            Toast.makeText(context, "Error starting download: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }

    suspend fun getDownloadStatus(downloadId: Long): DownloadStatusInfo? {
        if (downloadManager == null) return null

        return withContext(Dispatchers.IO) { // Querying DownloadManager can be slow
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
                    null // Download ID not found
                }
            } catch (e: Exception) {
                // Handle cursor exceptions
                null
            } finally {
                cursor?.close()
            }
        }
    }

    fun cancelDownload(downloadId: Long): Int {
        return downloadManager?.remove(downloadId) ?: 0
    }

    // You can also get the MIME type of a downloaded file
    fun getMimeTypeForDownloadedFile(downloadId: Long): String? {
        return downloadManager?.getMimeTypeForDownloadedFile(downloadId)
    }
}

data class DownloadStatusInfo(
    val downloadId: Long,
    val status: Int, // e.g., DownloadManager.STATUS_SUCCESSFUL, STATUS_FAILED, etc.
    val reason: Int, // Reason for failure, if applicable
    val totalBytes: Long,
    val downloadedBytes: Long,
    val localUri: Uri? // URI to the downloaded file if successful
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