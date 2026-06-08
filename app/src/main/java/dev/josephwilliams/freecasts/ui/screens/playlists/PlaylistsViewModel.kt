package dev.josephwilliams.freecasts.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.playback.PlaybackManager
import dev.josephwilliams.freecasts.data.playback.PlayingEpisode
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

    private val episodeDao: EpisodeDao by inject(EpisodeDao::class.java)

    private val podcastDao: PodcastDao by inject(PodcastDao::class.java)
    
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
        viewModelScope.launch {
            val favorites = episodeDao.getFavoriteEpisodes()

            if (favorites.isNotEmpty()) {
                val minCount = favorites.minOf { it.listenCount }
                val filteredFavorites = favorites.filter { it.listenCount <= minCount }

                // 2. Pick a random one
                val randomEpisode = filteredFavorites.random()
                val podcast = podcastDao.getById(randomEpisode.podcastId)
                playbackManager.play(
                    PlayingEpisode(
                        episodeId = randomEpisode.id,
                        episodeTitle = randomEpisode.title,
                        podcastId = randomEpisode.podcastId,
                        podcastName = podcast?.title ?: "",
                        artworkUrl = randomEpisode.artworkUrl ?: podcast?.artworkUrl,
                        audioUrl = randomEpisode.audioUrl,
                        localFilePath = null
                    )
                )
            }
        }
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
