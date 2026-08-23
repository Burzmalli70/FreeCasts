package com.lazysimulation.freecasts.ui.screens.search

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lazysimulation.freecasts.data.download.DownloadRequest
import com.lazysimulation.freecasts.data.download.EpisodeDownloadManager
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import com.lazysimulation.freecasts.data.remote.model.ItunesPodcast
import com.lazysimulation.freecasts.data.repository.PodcastRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ViewModel for the podcast search screen.
 */
class SearchViewModel(
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val episodeDownloadManager: EpisodeDownloadManager,
    private val episodeDao: EpisodeDao
) : ViewModel() {
    
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    
    private var searchJob: Job? = null
    
    init {
        observeSubscribedPodcasts()
    }
    
    private fun observeSubscribedPodcasts() {
        viewModelScope.launch {
            podcastRepository.observeSubscribedPodcasts().collect { podcasts ->
                val subscribedFeedUrls = podcasts.mapNotNull { it.feedUrl }.toSet()
                _state.update { it.copy(subscribedFeedUrls = subscribedFeedUrls) }
            }
        }
    }
    
    /**
     * Subscribe to a podcast directly from search results.
     * If auto-download setting is enabled, downloads the most recent episode.
     */
    fun subscribeToPodcast(podcast: ItunesPodcast) {
        val feedUrl = podcast.feedUrl ?: return
        
        // Mark as subscribing
        _state.update { it.copy(
            subscribingPodcastIds = it.subscribingPodcastIds + podcast.collectionId
        )}
        
        viewModelScope.launch {
            val result = podcastRepository.subscribeToPodcast(feedUrl, podcast)
            
            // Remove from subscribing set (success or failure)
            _state.update { it.copy(
                subscribingPodcastIds = it.subscribingPodcastIds - podcast.collectionId
            )}
            
            result.onSuccess { podcastId ->
                // Check if auto-download is enabled
                val autoDownload = userPreferencesRepository.autoDownloadOnSubscribe.first()
                if (autoDownload) {
                    downloadLatestEpisode(podcastId, podcast.collectionName)
                }
            }
            
            result.onFailure { exception ->
                _state.update { it.copy(
                    subscriptionError = "Failed to subscribe: ${exception.message}"
                )}
            }
        }
    }
    
    /**
     * Download the latest episode for a podcast.
     */
    private suspend fun downloadLatestEpisode(podcastId: Long, podcastName: String) {
        // Get the most recent episode for this podcast
        val episodes = episodeDao.observeByPodcastIdLimited(podcastId, 1).first()
        val latestEpisode = episodes.firstOrNull() ?: return
        
        // Skip if no audio URL
        if (latestEpisode.audioUrl.isBlank()) return
        
        // Create download request
        val downloadRequest = DownloadRequest(
            episodeId = latestEpisode.id,
            episodeName = latestEpisode.title,
            podcastName = podcastName,
            downloadUrl = latestEpisode.audioUrl,
            mimeType = latestEpisode.mimeType
        )
        
        // Enqueue the download
        episodeDownloadManager.enqueueDownload(downloadRequest)
    }

    fun unsubscribeFromPodcast(podcast: ItunesPodcast) {
        val feedUrl = podcast.feedUrl ?: return

        // Mark as subscribing
        _state.update { it.copy(
            subscribingPodcastIds = it.subscribingPodcastIds + podcast.collectionId
        )}

        viewModelScope.launch {
            podcastRepository.unsubscribeFromPodcast(feedUrl)

            // Remove from subscribing set (success or failure)
            _state.update { it.copy(
                subscribingPodcastIds = it.subscribingPodcastIds - podcast.collectionId
            )}
        }
    }
    
    /**
     * Clear the subscription error.
     */
    fun clearSubscriptionError() {
        _state.update { it.copy(subscriptionError = null) }
    }
    
    /**
     * Update the search query and trigger a debounced search.
     */
    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        
        // Cancel previous search job
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _state.update { it.copy(
                searchResults = emptyList(),
                isLoading = false,
                error = null
            )}
            return
        }
        
        // Use longer debounce for URLs to avoid unnecessary requests while typing
        val debounceTime = if (isUrl(query)) 500L else 300L
        
        // Debounce search
        searchJob = viewModelScope.launch {
            delay(debounceTime)
            performSearch(query)
        }
    }
    
    /**
     * Perform search immediately (e.g., when user presses search button).
     */
    fun search() {
        val query = _state.value.searchQuery
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(query)
            }
        }
    }
    
    private suspend fun performSearch(query: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        
        // Check if the query is a URL (RSS feed)
        if (isUrl(query)) {
            fetchRssFeed(query)
        } else {
            searchItunes(query)
        }
    }
    
    private fun isUrl(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.startsWith("http://") || 
               trimmed.startsWith("https://") ||
               Patterns.WEB_URL.matcher(trimmed).matches()
    }
    
    private suspend fun fetchRssFeed(url: String) {
        val result = podcastRepository.fetchPodcastFeed(url.trim())
        
        result.fold(
            onSuccess = { parseResult ->
                val podcast = parseResult.podcast
                if (podcast != null) {
                    // Convert the parsed podcast to ItunesPodcast format
                    val itunesPodcast = ItunesPodcast(
                        collectionId = abs(url.hashCode().toLong()),
                        collectionName = podcast.title,
                        artistName = podcast.author,
                        artworkUrl100 = podcast.artworkUrl,
                        artworkUrl600 = podcast.artworkUrl,
                        feedUrl = url,
                        trackCount = parseResult.episodes.size,
                        genre = podcast.categories,
                        collectionViewUrl = podcast.websiteUrl
                    )
                    
                    _state.update { it.copy(
                        searchResults = listOf(itunesPodcast),
                        isLoading = false,
                        error = null,
                        isRssFeedResult = true
                    )}
                } else {
                    _state.update { it.copy(
                        searchResults = emptyList(),
                        isLoading = false,
                        error = "Could not parse RSS feed"
                    )}
                }
            },
            onFailure = { exception ->
                _state.update { it.copy(
                    searchResults = emptyList(),
                    isLoading = false,
                    error = "Failed to fetch RSS feed: ${exception.message}"
                )}
            }
        )
    }
    
    private suspend fun searchItunes(query: String) {
        val result = podcastRepository.searchPodcasts(query)
        
        result.fold(
            onSuccess = { podcasts ->
                _state.update { it.copy(
                    searchResults = podcasts,
                    isLoading = false,
                    error = null,
                    isRssFeedResult = false
                )}
            },
            onFailure = { exception ->
                _state.update { it.copy(
                    searchResults = emptyList(),
                    isLoading = false,
                    error = exception.message ?: "Search failed"
                )}
            }
        )
    }
    
    /**
     * Clear search results and query.
     */
    fun clearSearch() {
        searchJob?.cancel()
        _state.update { SearchState() }
    }
}

/**
 * State for the search screen.
 */
data class SearchState(
    val searchQuery: String = "",
    val searchResults: List<ItunesPodcast> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRssFeedResult: Boolean = false,
    val subscribedFeedUrls: Set<String> = emptySet(),
    val subscribingPodcastIds: Set<Long> = emptySet(),
    val subscriptionError: String? = null
) {
    val hasResults: Boolean
        get() = searchResults.isNotEmpty()
    
    val showEmptyState: Boolean
        get() = searchQuery.isNotBlank() && !isLoading && searchResults.isEmpty() && error == null
    
    fun isSubscribed(podcast: ItunesPodcast): Boolean {
        return podcast.feedUrl != null && podcast.feedUrl in subscribedFeedUrls
    }
    
    fun isSubscribing(podcast: ItunesPodcast): Boolean {
        return podcast.collectionId in subscribingPodcastIds
    }
}
