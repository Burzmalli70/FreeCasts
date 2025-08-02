package dev.josephwilliams.freecasts.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.repositories.PodcastRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class SearchViewModel(private val podcastRepository: PodcastRepository): ViewModel() {
    private val mutableSearchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = mutableSearchQuery.asStateFlow()

    private val mutableActiveState = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = mutableActiveState.asStateFlow()

    private val mutableSearchResults = MutableStateFlow<List<Podcast>?>(null)
    val searchResults: StateFlow<List<Podcast>?> = mutableSearchResults.asStateFlow()

    private val mutableSelectedPodcast = MutableStateFlow<Podcast?>(null)
    val selectedPodcast: StateFlow<Podcast?> = mutableSelectedPodcast.asStateFlow()

    private val fetchedEpisodes: MutableMap<Podcast, List<Episode>> = mutableMapOf()
    private val mutablePodcastEpisodes: MutableStateFlow<List<Episode>> = MutableStateFlow(emptyList())
    val podcastEpisodes: StateFlow<List<Episode>> = mutablePodcastEpisodes.asStateFlow()

    fun onQueryChange(query: String) {
        mutableSearchQuery.value = query
        if (query.isBlank()) {
            mutableSearchResults.value = null
        } else {
            viewModelScope.launch {
                mutableSearchResults.value = podcastRepository.findNewPodcasts(query)
            }
        }
    }

    fun executeSearch(query: String) {
        if (query.isBlank()) {
            mutableSearchResults.value = null
        } else {
            viewModelScope.launch {
                mutableSearchResults.value = podcastRepository.findNewPodcasts(query)
            }
        }
    }

    fun onActiveChange(active: Boolean) {
        mutableActiveState.value = active
        if (!active) {
            // Optionally clear query when search bar is closed
            // _searchQuery.value = ""
        }
    }

    fun clearSearchQuery() {
        mutableSearchQuery.value = ""
    }

    fun selectPodcast(podcast: Podcast?) {
        viewModelScope.launch(Dispatchers.IO) {
            mutableSelectedPodcast.value = podcast
            if (podcast == null) {
                mutablePodcastEpisodes.emit(emptyList())
                return@launch
            }
            fetchedEpisodes[podcast]?.let { eps ->
                mutablePodcastEpisodes.emit(eps)
            }
            val episodes = podcastRepository.fetchPodcastEpisodes(podcast)
            if (episodes.isNotEmpty()) {
                fetchedEpisodes[podcast] = episodes
                mutablePodcastEpisodes.emit(episodes)
            }
        }
    }
}