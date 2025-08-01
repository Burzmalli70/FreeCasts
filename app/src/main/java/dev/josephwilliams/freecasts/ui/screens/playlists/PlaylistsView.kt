package dev.josephwilliams.freecasts.ui.screens.playlists

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.relationships.PlaylistWithEpisodes

@Composable
fun PlaylistsView(
    modifier: Modifier = Modifier,
    playlistsViewModel: PlaylistsViewModel
) {
    val playlists by playlistsViewModel.playlists.collectAsState(emptyList())
    val selectedPlaylist by playlistsViewModel.selectedPlaylist.collectAsState()

    if (selectedPlaylist == null) {

    } else {

    }
}

@Composable
fun PlaylistDetail(
    modifier: Modifier = Modifier,
    playlist: PlaylistWithEpisodes
) {
    LazyColumn(modifier = modifier) {

    }
}

@Composable
fun PlaylistList(
    modifier: Modifier = Modifier,
    playlists: List<PlaylistWithEpisodes>
) {
    LazyColumn(modifier = modifier) {

    }
}

@Composable
fun PlaylistItem(
    modifier: Modifier = Modifier,
    playlist: PlaylistWithEpisodes
) {

}

@Composable
fun EpisodeItem(
    modifier: Modifier = Modifier,
    episode: Episode
) {

}