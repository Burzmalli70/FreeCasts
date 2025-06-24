package dev.josephwilliams.filedownloader.service

import android.content.Context
import dev.josephwilliams.filedownloader.model.DownloadInfo
import dev.josephwilliams.filedownloader.model.DownloadState
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FileDownloader(
    private val context: Context,
    private val persistenceManager: PersistenceManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val activeDownloads = ConcurrentHashMap<String, Call>()

    suspend fun download(downloadInfo: DownloadInfo, headers: Map<String, String> = emptyMap()) {
        val file = File(downloadInfo.destination, downloadInfo.fileName)
        val tempFile = File("${file.absolutePath}.tmp")

        file.parentFile?.mkdirs()

        var downloadedBytes = if (tempFile.exists() && downloadInfo.downloadedByteCount > 0) {
            downloadInfo.downloadedByteCount
        } else {
            0
        }

        val requestBuilder = Request.Builder().url(downloadInfo.url)
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        if (downloadedBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        val request = requestBuilder.build()
        val call = client.newCall(request)

        activeDownloads[downloadInfo.id] = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response code: ${response.code}")
                }

                val responseBody = response.body ?: throw IOException("Response body is null")
                val contentLength = responseBody.contentLength()
                val totalBytes = if (contentLength != -1L) {
                    contentLength + downloadedBytes
                } else {
                    -1L
                }

                if (totalBytes != -1L && downloadInfo.totalBytes != totalBytes) {
                    downloadInfo.totalBytes = totalBytes
                    persistenceManager.updateDownloadInfo(downloadInfo)
                }

                val outputStream = if (downloadedBytes > 0) {
                    FileOutputStream(tempFile, true)
                } else {
                    FileOutputStream(tempFile)
                }

                outputStream.use { output ->
                    val buffer = ByteArray(8192)
                    val inputStream = responseBody.byteStream()
                    var bytesRead: Int
                    var lastProgressUpdate = System.currentTimeMillis()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                            downloadInfo.state = DownloadState.DOWNLOADING
                            downloadInfo.downloadedByteCount = downloadedBytes
                            persistenceManager.updateDownloadInfo(downloadInfo)
                            lastProgressUpdate = now
                        }
                    }
                }

                if (tempFile.renameTo(file)) {
                    downloadInfo.state = DownloadState.COMPLETED
                    downloadInfo.downloadedByteCount = downloadedBytes
                    persistenceManager.updateDownloadInfo(downloadInfo)
                } else {
                    throw IOException("Failed to rename temp file")
                }
            }
        } catch (e: IOException) {
            downloadInfo.state = DownloadState.FAILED
            downloadInfo.downloadedByteCount = downloadedBytes
            downloadInfo.error = e.message
            persistenceManager.updateDownloadInfo(downloadInfo)
        } finally {
            activeDownloads.remove(downloadInfo.id)
        }
    }

    fun pause(downloadId: String) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)

        // Update download info state in persistence
    }

    fun cancel(downloadId: String) {
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)

        // Get download info from persistence and delete temp file
    }

    companion object {
        const val PROGRESS_UPDATE_INTERVAL = 500L
    }
}