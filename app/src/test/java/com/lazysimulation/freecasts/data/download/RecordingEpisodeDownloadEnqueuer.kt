package com.lazysimulation.freecasts.data.download

/**
 * Test double that records enqueue requests without performing downloads.
 */
class RecordingEpisodeDownloadEnqueuer : EpisodeDownloadEnqueuer {
    val requests = mutableListOf<DownloadRequest>()
    val enqueuedEpisodeIds: List<Long>
        get() = requests.map { it.episodeId }

    override suspend fun enqueueDownload(request: DownloadRequest): Boolean {
        requests += request
        return true
    }

    fun clear() {
        requests.clear()
    }
}

object NoOpEpisodeDownloadEnqueuer : EpisodeDownloadEnqueuer {
    override suspend fun enqueueDownload(request: DownloadRequest): Boolean = false
}
