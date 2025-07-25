package dev.joewilliams.downloadmanager

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

class DownloadManager(
    private val applicationContext: Context,
    private val persistenceRepository: DownloadPersistenceRepository
) {
    private val mutableActiveDownloads: MutableStateFlow<List<Download>> = MutableStateFlow(emptyList())
    val activeDownloads: MutableStateFlow<List<Download>> = mutableActiveDownloads

    fun startDownload(context: Context = applicationContext, download: Download) {
        val intent = Intent(context, DownloaderService::class.java).apply {
            action = DownloaderService.ACTION_START_DOWNLOAD
            putExtra(DownloaderService.EXTRA_DOWNLOAD_URL, download.url)
            putExtra(DownloaderService.EXTRA_OUTPUT_FILE_PATH, download.fullPath)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}