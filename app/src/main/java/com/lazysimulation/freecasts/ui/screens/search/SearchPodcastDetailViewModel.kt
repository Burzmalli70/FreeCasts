package com.lazysimulation.freecasts.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lazysimulation.freecasts.data.download.AutoDownloadHandler
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.remote.model.ItunesPodcast
import com.lazysimulation.freecasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the podcast detail screen when coming from search results.
 * Handles fetching podcast data from RSS feed and subscription management.
 */
class SearchPodcastDetailViewModel(
    private val podcastRepository: PodcastRepository,
    private val autoDownloadHandler: AutoDownloadHandler,
) : ViewModel() {
    
    private val _state = MutableStateFlow(SearchPodcastDetailState())
    val state: StateFlow<SearchPodcastDetailState> = _state.asStateFlow()
    
    private var currentItunesPodcast: ItunesPodcast? = null
    
    /**
     * Load podcast details from an iTunes search result.
     * Fetches the RSS feed to get full podcast info and episodes.
     */
    fun loadPodcast(itunesPodcast: ItunesPodcast) {
        currentItunesPodcast = itunesPodcast
        
        val feedUrl = itunesPodcast.feedUrl
        if (feedUrl.isNullOrBlank()) {
            _state.update { it.copy(
                error = "This podcast doesn't have an RSS feed URL",
                isLoading = false
            )}
            return
        }
        
        _state.update { it.copy(
            isLoading = true,
            error = null,
            // Show iTunes data immediately while loading RSS feed
            podcast = Podcast(
                title = itunesPodcast.collectionName,
                author = itunesPodcast.artistName,
                artworkUrl = itunesPodcast.artworkUrl600 ?: itunesPodcast.artworkUrl100,
                feedUrl = feedUrl,
                episodeCount = itunesPodcast.trackCount ?: 0,
                categories = itunesPodcast.genre
            )
        )}
        
        viewModelScope.launch {
            // Check if already subscribed
            val isSubscribed = podcastRepository.isSubscribed(feedUrl)
            _state.update { it.copy(isSubscribed = isSubscribed) }
            
            // Fetch RSS feed for full details
            val result = podcastRepository.fetchPodcastFeed(feedUrl)
            
            result.fold(
                onSuccess = { parseResult ->
                    val podcast = parseResult.podcast?.copy(
                        // Prefer iTunes artwork as it's usually higher quality
                        artworkUrl = itunesPodcast.artworkUrl600 
                            ?: itunesPodcast.artworkUrl100 
                            ?: parseResult.podcast.artworkUrl,
                        isSubscribed = isSubscribed
                    )
                    
                    _state.update { it.copy(
                        podcast = podcast,
                        episodes = parseResult.episodes,
                        isLoading = false,
                        error = null
                    )}
                },
                onFailure = { exception ->
                    _state.update { it.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to load podcast"
                    )}
                }
            )
        }
    }
    
    /**
     * Subscribe to the current podcast.
     */
    fun subscribe() {
        val itunesPodcast = currentItunesPodcast ?: return
        val feedUrl = itunesPodcast.feedUrl ?: return
        
        _state.update { it.copy(isSubscribing = true) }
        
        viewModelScope.launch {
            val result = podcastRepository.subscribeToPodcast(feedUrl, itunesPodcast)
            
            result.fold(
                onSuccess = { podcastId ->
                    autoDownloadHandler.downloadLatestEpisodeIfEnabled(podcastId)
                    _state.update { state ->
                        state.copy(
                            isSubscribed = true,
                            isSubscribing = false,
                            podcast = state.podcast?.copy(isSubscribed = true)
                        )
                    }
                },
                onFailure = { exception ->
                    _state.update { it.copy(
                        isSubscribing = false,
                        error = "Failed to subscribe: ${exception.message}"
                    )}
                }
            )
        }
    }
    
    /**
     * Unsubscribe from the current podcast.
     */
    fun unsubscribe() {
        val feedUrl = currentItunesPodcast?.feedUrl ?: return
        
        viewModelScope.launch {
            val podcast = podcastRepository.getPodcastByFeedUrl(feedUrl)
            if (podcast != null) {
                podcastRepository.unsubscribeFromPodcast(podcast.id)
                _state.update { state ->
                    state.copy(
                        isSubscribed = false,
                        podcast = state.podcast?.copy(isSubscribed = false)
                    )
                }
            }
        }
    }
    
    /**
     * Toggle subscription status.
     */
    fun toggleSubscription() {
        if (_state.value.isSubscribed) {
            unsubscribe()
        } else {
            subscribe()
        }
    }
}

/**
 * State for the search podcast detail screen.
 */
data class SearchPodcastDetailState(
    val podcast: Podcast? = null,
    val episodes: List<Episode> = emptyList(),
    val isLoading: Boolean = false,
    val isSubscribed: Boolean = false,
    val isSubscribing: Boolean = false,
    val error: String? = null
) {
    val hasEpisodes: Boolean
        get() = episodes.isNotEmpty()
}
