package dev.josephwilliams.freecasts.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.remote.model.ItunesPodcast
import dev.josephwilliams.freecasts.data.repository.PodcastRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the podcast search screen.
 */
class SearchViewModel(
    private val podcastRepository: PodcastRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    
    private var searchJob: Job? = null
    
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
        
        // Debounce search
        searchJob = viewModelScope.launch {
            delay(300) // Wait for user to stop typing
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
        
        val result = podcastRepository.searchPodcasts(query)
        
        result.fold(
            onSuccess = { podcasts ->
                _state.update { it.copy(
                    searchResults = podcasts,
                    isLoading = false,
                    error = null
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
    val error: String? = null
) {
    val hasResults: Boolean
        get() = searchResults.isNotEmpty()
    
    val showEmptyState: Boolean
        get() = searchQuery.isNotBlank() && !isLoading && searchResults.isEmpty() && error == null
}
