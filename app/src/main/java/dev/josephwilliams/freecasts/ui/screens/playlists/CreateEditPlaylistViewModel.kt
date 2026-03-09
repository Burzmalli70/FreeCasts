package dev.josephwilliams.freecasts.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for creating or editing a playlist.
 */
class CreateEditPlaylistViewModel(
    private val playlistDao: PlaylistDao,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao
) : ViewModel() {
    
    private val _state = MutableStateFlow(CreateEditPlaylistState())
    val state: StateFlow<CreateEditPlaylistState> = _state.asStateFlow()
    
    private var existingPlaylistId: Long? = null
    
    init {
        loadSubscribedPodcasts()
    }
    
    private fun loadSubscribedPodcasts() {
        viewModelScope.launch {
            podcastDao.observeSubscribed().collect { podcasts ->
                _state.update { it.copy(subscribedPodcasts = podcasts) }
            }
        }
    }
    
    /**
     * Load an existing playlist for editing.
     */
    fun loadPlaylist(playlistId: Long) {
        existingPlaylistId = playlistId
        _state.update { it.copy(isLoading = true, isEditMode = true) }
        
        viewModelScope.launch {
            val playlist = playlistDao.getById(playlistId)
            if (playlist != null) {
                _state.update { it.copy(
                    name = playlist.name,
                    removeAfterListening = playlist.removeAfterListening,
                    selectedPodcastIds = playlist.getAutoAddPodcastIdList().toSet(),
                    isLoading = false
                )}
            } else {
                _state.update { it.copy(
                    isLoading = false,
                    error = "Playlist not found"
                )}
            }
        }
    }
    
    /**
     * Reset state for creating a new playlist.
     */
    fun resetForCreate() {
        existingPlaylistId = null
        _state.update { 
            CreateEditPlaylistState(subscribedPodcasts = it.subscribedPodcasts)
        }
    }
    
    /**
     * Update the playlist name.
     */
    fun onNameChange(name: String) {
        _state.update { it.copy(
            name = name,
            error = null
        )}
    }
    
    /**
     * Toggle the remove after listening setting.
     */
    fun onRemoveAfterListeningChange(enabled: Boolean) {
        _state.update { it.copy(removeAfterListening = enabled) }
    }
    
    /**
     * Toggle podcast selection for auto-add.
     */
    fun togglePodcastSelection(podcastId: Long) {
        _state.update { state ->
            val newSelection = if (state.selectedPodcastIds.contains(podcastId)) {
                state.selectedPodcastIds - podcastId
            } else {
                state.selectedPodcastIds + podcastId
            }
            state.copy(selectedPodcastIds = newSelection)
        }
    }
    
    /**
     * Save the playlist (create or update).
     * Also adds the latest unlistened episode from each selected auto-add podcast.
     */
    fun save(onSuccess: () -> Unit) {
        val currentState = _state.value
        
        // Validate
        if (currentState.name.isBlank()) {
            _state.update { it.copy(error = "Please enter a name") }
            return
        }
        
        _state.update { it.copy(isSaving = true) }
        
        viewModelScope.launch {
            try {
                val autoAddPodcastIds = if (currentState.selectedPodcastIds.isEmpty()) {
                    null
                } else {
                    currentState.selectedPodcastIds.joinToString(",")
                }
                
                val playlistId: Long
                
                if (existingPlaylistId != null) {
                    // Update existing playlist
                    val existing = playlistDao.getById(existingPlaylistId!!)
                    if (existing != null) {
                        playlistDao.update(existing.copy(
                            name = currentState.name.trim(),
                            removeAfterListening = currentState.removeAfterListening,
                            autoAddPodcastIds = autoAddPodcastIds,
                            updatedAt = System.currentTimeMillis()
                        ))
                    }
                    playlistId = existingPlaylistId!!
                } else {
                    // Create new playlist
                    playlistId = playlistDao.insert(Playlist(
                        name = currentState.name.trim(),
                        removeAfterListening = currentState.removeAfterListening,
                        autoAddPodcastIds = autoAddPodcastIds
                    ))
                }
                
                // Auto-add latest unlistened episodes from selected podcasts
                if (currentState.selectedPodcastIds.isNotEmpty()) {
                    addLatestEpisodesFromPodcasts(playlistId, currentState.selectedPodcastIds)
                }
                
                _state.update { it.copy(isSaving = false, saveSuccess = true) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save playlist"
                )}
            }
        }
    }
    
    /**
     * Add the latest unlistened episode from each podcast to the playlist.
     * Skips episodes that are already in the playlist or have been listened to.
     */
    private suspend fun addLatestEpisodesFromPodcasts(playlistId: Long, podcastIds: Set<Long>) {
        var currentPosition = playlistDao.getMaxPosition(playlistId) ?: -1
        
        for (podcastId in podcastIds) {
            // Get unplayed episodes for this podcast, ordered by publishedAt DESC
            val unplayedEpisodes = episodeDao.getUnplayedByPodcastId(podcastId)
            
            // Get the latest unplayed episode (first in the list)
            val latestEpisode = unplayedEpisodes.firstOrNull() ?: continue
            
            // Check if episode is already in playlist
            val alreadyInPlaylist = playlistDao.isEpisodeInPlaylist(playlistId, latestEpisode.id)
            if (alreadyInPlaylist) continue
            
            // Add episode to playlist
            currentPosition++
            playlistDao.insertPlaylistEpisode(
                PlaylistEpisodeCrossRef(
                    playlistId = playlistId,
                    episodeId = latestEpisode.id,
                    position = currentPosition,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
        
        // Update playlist timestamp
        playlistDao.updateTimestamp(playlistId)
    }
}

/**
 * State for the create/edit playlist screen.
 */
data class CreateEditPlaylistState(
    val name: String = "",
    val removeAfterListening: Boolean = false,
    val subscribedPodcasts: List<Podcast> = emptyList(),
    val selectedPodcastIds: Set<Long> = emptySet(),
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank()
    
    val title: String
        get() = if (isEditMode) "Edit Playlist" else "Create Playlist"
    
    val hasSubscribedPodcasts: Boolean
        get() = subscribedPodcasts.isNotEmpty()
}
