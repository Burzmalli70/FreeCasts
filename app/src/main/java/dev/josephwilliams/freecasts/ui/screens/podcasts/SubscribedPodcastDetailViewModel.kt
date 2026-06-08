package dev.josephwilliams.freecasts.ui.screens.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.download.DownloadRequest
import dev.josephwilliams.freecasts.data.download.DownloadStatus
import dev.josephwilliams.freecasts.data.download.EpisodeDownloadManager
import dev.josephwilliams.freecasts.data.local.dao.DownloadDao
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import dev.josephwilliams.freecasts.data.local.relation.EpisodeWithDownload
import dev.josephwilliams.freecasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.josephwilliams.freecasts.data.local.entity.DownloadStatus as DbDownloadStatus

/**
 * ViewModel for viewing a subscribed podcast's details.
 */
class SubscribedPodcastDetailViewModel(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val downloadDao: DownloadDao,
    private val podcastRepository: PodcastRepository,
    private val downloadManager: EpisodeDownloadManager
) : ViewModel() {
    
    private val _state = MutableStateFlow(SubscribedPodcastDetailState())
    val state: StateFlow<SubscribedPodcastDetailState> = _state.asStateFlow()
    
    private var currentPodcastId: Long = -1
    
    /**
     * Load a podcast by its database ID.
     */
    fun loadPodcast(podcastId: Long) {
        if (currentPodcastId == podcastId) return
        currentPodcastId = podcastId
        
        _state.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            // Observe podcast
            podcastDao.observeById(podcastId).collect { podcast ->
                _state.update { it.copy(podcast = podcast) }
            }
        }
        
        viewModelScope.launch {
            // Observe episodes with download status
            combine(
                episodeDao.observeEpisodesWithDownloadByPodcastId(podcastId),
                downloadManager.state
            ) { episodesWithDownload, downloadManagerState ->
                // Merge database download status with live download manager status
                episodesWithDownload.map { ewd ->
                    val liveDownloadState = downloadManagerState.activeDownloads
                        .find { it.episodeId == ewd.episode.id }
                        ?: downloadManagerState.queuedDownloads
                            .find { it.episodeId == ewd.episode.id }
                    
                    EpisodeDisplayState(
                        episode = ewd.episode,
                        downloadStatus = when {
                            liveDownloadState?.status == DownloadStatus.DOWNLOADING -> EpisodeDownloadDisplayStatus.DOWNLOADING
                            liveDownloadState?.status == DownloadStatus.QUEUED -> EpisodeDownloadDisplayStatus.QUEUED
                            ewd.download?.status == DbDownloadStatus.COMPLETED -> EpisodeDownloadDisplayStatus.DOWNLOADED
                            ewd.download?.status == DbDownloadStatus.FAILED -> EpisodeDownloadDisplayStatus.FAILED
                            else -> EpisodeDownloadDisplayStatus.NOT_DOWNLOADED
                        },
                        downloadProgress = liveDownloadState?.progressPercent,
                        isPlayed = ewd.episode.isPlayed,
                        playbackPositionMs = ewd.episode.playbackPositionMs,
                        listenCount = ewd.episode.listenCount,
                        localFilePath = ewd.download?.localFilePath
                    )
                }
            }.collect { episodes ->
                _state.update { it.copy(
                    episodes = episodes,
                    isLoading = false
                )}
            }
        }
    }
    
    /**
     * Refresh the podcast's episodes from its RSS feed.
     */
    fun refresh() {
        if (currentPodcastId == -1L) return
        
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            podcastRepository.refreshPodcast(currentPodcastId)
            _state.update { it.copy(isRefreshing = false) }
        }
    }
    
    /**
     * Unsubscribe from the current podcast.
     */
    fun unsubscribe() {
        if (currentPodcastId == -1L) return
        
        viewModelScope.launch {
            podcastRepository.unsubscribeFromPodcast(currentPodcastId)
        }
    }
    
    /**
     * Download an episode.
     */
    fun downloadEpisode(episode: Episode) {
        val podcast = _state.value.podcast ?: return
        
        viewModelScope.launch {
            downloadManager.enqueueDownload(
                DownloadRequest(
                    episodeId = episode.id,
                    episodeName = episode.title,
                    podcastName = podcast.title,
                    downloadUrl = episode.audioUrl,
                    mimeType = episode.mimeType
                )
            )
        }
    }
    
    /**
     * Cancel a download.
     */
    fun cancelDownload(episodeId: Long) {
        viewModelScope.launch {
            downloadManager.cancelDownload(episodeId)
        }
    }
    
    /**
     * Delete a downloaded episode.
     */
    fun deleteDownload(episodeId: Long) {
        viewModelScope.launch {
            downloadDao.deleteByEpisodeId(episodeId)
            // TODO: Also delete the actual file from storage
        }
    }
    
    /**
     * Mark an episode as played.
     */
    fun markAsPlayed(episodeId: Long) {
        viewModelScope.launch {
            episodeDao.markAsPlayed(episodeId)
        }
    }
    
    /**
     * Mark an episode as unplayed.
     */
    fun markAsUnplayed(episodeId: Long) {
        viewModelScope.launch {
            episodeDao.markAsUnplayed(episodeId)
        }
    }
    
    /**
     * Toggle favorite status for an episode.
     * When marking as favorite, sets replayPriority to max+1 among favorites.
     */
    fun toggleFavorite(episode: Episode) {
        viewModelScope.launch {
            val willBeFavorite = !episode.isFavorite
            
            if (willBeFavorite) {
                // Set replayPriority to max+1 so new favorites start at the end
                val maxPriority = episodeDao.getMaxReplayPriorityAmongFavorites() ?: 0
                episodeDao.setReplayPriority(episode.id, maxPriority + 1)
            }
            
            episodeDao.toggleFavorite(episode.id, willBeFavorite)
        }
    }
}

/**
 * State for the subscribed podcast detail screen.
 */
data class SubscribedPodcastDetailState(
    val podcast: Podcast? = null,
    val episodes: List<EpisodeDisplayState> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
) {
    val hasEpisodes: Boolean
        get() = episodes.isNotEmpty()
}

/**
 * Display state for an episode including download and playback status.
 */
data class EpisodeDisplayState(
    val episode: Episode,
    val downloadStatus: EpisodeDownloadDisplayStatus,
    val downloadProgress: Int? = null,
    val isPlayed: Boolean,
    val playbackPositionMs: Long,
    val listenCount: Int,
    val localFilePath: String? = null
) {
    val hasProgress: Boolean
        get() = playbackPositionMs > 0 && !isPlayed
    
    val progressPercent: Float
        get() {
            val duration = episode.durationSeconds ?: return 0f
            if (duration <= 0) return 0f
            return (playbackPositionMs / 1000f) / duration
        }
}

/**
 * Download status for display purposes.
 */
enum class EpisodeDownloadDisplayStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}
