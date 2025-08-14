package dev.josephwilliams.freecasts.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.downloader.SystemDownloader
import dev.josephwilliams.freecasts.model.daos.DownloadDao
import dev.josephwilliams.freecasts.model.entities.Download
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class DownloadsViewModel(private val downloadDao: DownloadDao): ViewModel() {
    val downloads: Flow<List<Download>> = downloadDao.getAllDownloads()

    val systemDownloader: SystemDownloader by inject(SystemDownloader::class.java)

    private var downloadStatusJob: Job? = null

    init {
        checkDownloadStatus()
    }

    private fun checkDownloadStatus() {
        downloadStatusJob?.cancel()
        downloadStatusJob = viewModelScope.launch {
            while(isActive) {
                val incompleteDownloads = downloadDao.getActiveDownloads()
                incompleteDownloads.forEach { download ->
                    try {
                        val status = systemDownloader.getDownloadStatus(download.id)
                        when {
                            status?.isSuccessful == true -> {
                                downloadDao.markDownloadAsCompleted(download.id)
                            }

                            status?.isRunningOrPending == true -> {
                                downloadDao.updateDownloadedBytes(
                                    download.id,
                                    status.downloadedBytes
                                )
                            }

                            status?.isFailed == true -> {
                                systemDownloader.startDownload(download.episodeId)
                            }
                        }
                    } catch (ex: Exception) {

                    }
                }
                delay(10_000L)
            }
        }
    }
}