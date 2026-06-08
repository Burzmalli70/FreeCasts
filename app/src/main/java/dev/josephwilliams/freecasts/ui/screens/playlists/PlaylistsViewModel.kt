package dev.josephwilliams.freecasts.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.playback.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/**
 * ViewModel for the main playlists screen.
 */
class PlaylistsViewModel(
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val playbackManager: PlaybackManager by inject(PlaybackManager::class.java)
    
    private val _state = MutableStateFlow(PlaylistsState())
    val state: StateFlow<PlaylistsState> = _state.asStateFlow()
    
    init {
        observePlaylists()
    }
    
    private fun observePlaylists() {
        viewModelScope.launch {
            playlistDao.observeAll().collect { playlists ->
                _state.update { it.copy(
                    playlists = playlists,
                    isLoading = false
                )}
            }
        }
    }
    
    /**
     * Delete a playlist.
     */
    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistDao.delete(playlist)
        }
    }

    fun playRandomFavorite() {
        playbackManager.playRandomFavorite()
    }
}

/**
 * State for the playlists screen.
 */
data class PlaylistsState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean
        get() = playlists.isEmpty() && !isLoading
    
    val hasPlaylists: Boolean
        get() = playlists.isNotEmpty()
}
