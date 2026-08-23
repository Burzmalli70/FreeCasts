package com.lazysimulation.freecasts.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.local.dao.PodcastDao
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Playlist
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.playlist.PlaylistAutoRemoveHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for viewing a playlist's details and episodes.
 */
class PlaylistDetailViewModel(
    private val playlistDao: PlaylistDao,
    private val podcastDao: PodcastDao,
    private val playlistAutoRemoveHandler: PlaylistAutoRemoveHandler
) : ViewModel() {
    
    private val _state = MutableStateFlow(PlaylistDetailState())
    val state: StateFlow<PlaylistDetailState> = _state.asStateFlow()
    
    private var currentPlaylistId: Long = -1
    
    // Cache of podcast info for episodes
    private val podcastCache = mutableMapOf<Long, Podcast?>()
    
    /**
     * Load a playlist by ID.
     */
    fun loadPlaylist(playlistId: Long) {
        if (currentPlaylistId == playlistId) return
        currentPlaylistId = playlistId
        
        _state.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            playlistDao.observePlaylistWithEpisodes(playlistId).collect { playlistWithEpisodes ->
                if (playlistWithEpisodes != null) {
                    // Get podcast info for each episode
                    val episodesWithPodcast = playlistWithEpisodes.episodes.map { episode ->
                        val podcast = getPodcast(episode.podcastId)
                        PlaylistEpisodeItem(
                            episode = episode,
                            podcastName = podcast?.title ?: "Unknown Podcast",
                            podcastArtworkUrl = podcast?.artworkUrl
                        )
                    }
                    
                    _state.update { it.copy(
                        playlist = playlistWithEpisodes.playlist,
                        episodes = episodesWithPodcast,
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
    }
    
    private suspend fun getPodcast(podcastId: Long): Podcast? {
        return podcastCache.getOrPut(podcastId) {
            podcastDao.getById(podcastId)
        }
    }
    
    /**
     * Mark an episode as played.
     */
    fun markAsPlayed(episodeId: Long) {
        viewModelScope.launch {
            playlistAutoRemoveHandler.markEpisodeAsPlayed(episodeId)
        }
    }
    
    /**
     * Remove an episode from the playlist.
     */
    fun removeEpisode(episodeId: Long) {
        if (currentPlaylistId == -1L) return
        
        viewModelScope.launch {
            playlistDao.removeEpisodeFromPlaylist(currentPlaylistId, episodeId)
            playlistDao.updateTimestamp(currentPlaylistId)
        }
    }
    
    /**
     * Delete the entire playlist.
     */
    fun deletePlaylist(onDeleted: () -> Unit) {
        if (currentPlaylistId == -1L) return
        
        viewModelScope.launch {
            playlistDao.deleteById(currentPlaylistId)
            onDeleted()
        }
    }
}

/**
 * State for the playlist detail screen.
 */
data class PlaylistDetailState(
    val playlist: Playlist? = null,
    val episodes: List<PlaylistEpisodeItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val hasEpisodes: Boolean
        get() = episodes.isNotEmpty()
    
    val episodeCount: Int
        get() = episodes.size
}

/**
 * Represents an episode in a playlist with additional podcast info.
 */
data class PlaylistEpisodeItem(
    val episode: Episode,
    val podcastName: String,
    val podcastArtworkUrl: String?
)
