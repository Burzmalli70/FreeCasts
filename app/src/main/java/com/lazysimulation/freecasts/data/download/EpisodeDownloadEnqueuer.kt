package com.lazysimulation.freecasts.data.download

interface EpisodeDownloadEnqueuer {
    suspend fun enqueueDownload(request: DownloadRequest): Boolean
}
