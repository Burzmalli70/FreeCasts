package dev.josephwilliams.freecasts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.downloader.DownloadStatusInfo
import dev.josephwilliams.freecasts.downloader.SystemDownloader
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.repositories.PodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class PodcastViewModel(
    private val repository: PodcastRepository,
    private val systemDownloader: SystemDownloader
) : ViewModel() {

    val allPodcasts: Flow<List<Podcast>> = repository.getAllPodcasts()

    private val _downloadEvents = MutableSharedFlow<Pair<Long, DownloadStatusInfo?>>()
    val downloadEvents: SharedFlow<Pair<Long, DownloadStatusInfo?>> = _downloadEvents.asSharedFlow()

    private val downloadsInProgress = mutableMapOf<Long, String>() // downloadId to original URL or identifier

    fun downloadEpisode(episodeUrl: String, title: String, fileName: String) {
        val downloadId = systemDownloader.startDownload(
            url = episodeUrl,
            title = title,
            description = "Downloading $title",
            destinationFileName = fileName
        )

        if (downloadId != null) {
            downloadsInProgress[downloadId] = episodeUrl
            // You might want to store this downloadId in your Room database
            // associated with the episode to track its status later.
            Log.d("PodcastViewModel", "Download started with ID: $downloadId for URL: $episodeUrl")
            // Start monitoring progress if needed (see section 4)
        } else {
            Log.e("PodcastViewModel", "Failed to start download for URL: $episodeUrl")
        }
    }

    // Function to check status, perhaps periodically or when view is active
    fun checkDownloadProgress(downloadId: Long) {
        viewModelScope.launch {
            val statusInfo = systemDownloader.getDownloadStatus(downloadId)
            _downloadEvents.emit(downloadId to statusInfo)
            if (statusInfo?.isSuccessful == true || statusInfo?.isFailed == true) {
                downloadsInProgress.remove(downloadId)
            }
            // Update your UI or Room database based on statusInfo
            if (statusInfo != null) {
                Log.d("PodcastViewModel", "Download ID $downloadId: Status ${statusInfo.status}, Progress: ${statusInfo.downloadedBytes}/${statusInfo.totalBytes}")
                if (statusInfo.isSuccessful) {
                    Log.d("PodcastViewModel", "File downloaded to: ${statusInfo.localUri}")
                    // TODO: Update your Room entity with the local file path from statusInfo.localUri
                    // You might need to convert the content URI to a file path if you need direct file access,
                    // but often using the URI with a ContentResolver is better.
                }
            }
        }
    }

    fun addPodcast(title: String, author: String, description: String, imageUrl: String, feedUrl: String) = viewModelScope.launch {
        val podcast = Podcast(
            title = title,
            author = author,
            description = description,
            imageUrl = imageUrl,
            feedUrl = feedUrl
        )
        repository.addPodcast(podcast)
    }

    fun addEpisode(
        podcastId: Int,
        title: String,
        description: String,
        audioUrl: String,
        duration: Long,
        publicationDate: Long
    ) = viewModelScope.launch {
        val episode = Episode(
            podcastId = podcastId,
            title = title,
            description = description,
            audioUrl = audioUrl,
            duration = duration,
            publicationDate = publicationDate
        )
        repository.addEpisode(episode)
    }

    fun createPlaylist(name: String) = viewModelScope.launch {
        val playlist = Playlist(name = name)
        repository.createPlaylist(playlist)
    }
}