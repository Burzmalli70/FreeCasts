package dev.josephwilliams.freecasts.ui.screens.playlists

import androidx.lifecycle.ViewModel
import dev.josephwilliams.freecasts.model.relationships.PlaylistWithEpisodes
import dev.josephwilliams.freecasts.repositories.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaylistsViewModel(
    private val podcastRepository: PodcastRepository
): ViewModel() {

    val playlists = podcastRepository.getAllPlaylists()

    private val mutableSelectedPlaylist: MutableStateFlow<PlaylistWithEpisodes?> = MutableStateFlow(null)
    val selectedPlaylist: StateFlow<PlaylistWithEpisodes?> = mutableSelectedPlaylist.asStateFlow()
}