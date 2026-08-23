package com.lazysimulation.freecasts.ui.screens.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the main podcasts screen showing subscribed podcasts.
 */
class PodcastsViewModel(
    private val podcastRepository: PodcastRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(PodcastsState())
    val state: StateFlow<PodcastsState> = _state.asStateFlow()
    
    init {
        observeSubscribedPodcasts()
    }
    
    private fun observeSubscribedPodcasts() {
        viewModelScope.launch {
            podcastRepository.observeSubscribedPodcasts().collect { podcasts ->
                _state.update { it.copy(
                    podcasts = podcasts,
                    isLoading = false
                )}
            }
        }
    }
    
    /**
     * Refresh podcasts from their RSS feeds.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            
            val podcasts = _state.value.podcasts
            for (podcast in podcasts) {
                podcastRepository.refreshPodcast(podcast.id)
            }
            
            _state.update { it.copy(isRefreshing = false) }
        }
    }
}

/**
 * State for the podcasts screen.
 */
data class PodcastsState(
    val podcasts: List<Podcast> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
) {
    val isEmpty: Boolean
        get() = podcasts.isEmpty() && !isLoading
    
    val hasPodcasts: Boolean
        get() = podcasts.isNotEmpty()
}
