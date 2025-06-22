package dev.josephwilliams.freecasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.repositories.PodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PodcastViewModel(
    private val repository: PodcastRepository
) : ViewModel() {

    val allPodcasts: Flow<List<Podcast>> = repository.getAllPodcasts()

    fun addPodcast(title: String, author: String, description: String, imageUrl: String, feedUrl: String) = viewModelScope.launch {
        val podcast = Podcast(
            title = title,
            author = author,
            description = description,
            imageUrl = imageUrl,
            feedUrl = feedUrl
        )
        repository.addPodcast(podcast)
    }

    fun addEpisode(
        podcastId: Int,
        title: String,
        description: String,
        audioUrl: String,
        duration: Long,
        publicationDate: Long
    ) = viewModelScope.launch {
        val episode = Episode(
            podcastId = podcastId,
            title = title,
            description = description,
            audioUrl = audioUrl,
            duration = duration,
            publicationDate = publicationDate
        )
        repository.addEpisode(episode)
    }

    fun createPlaylist(name: String) = viewModelScope.launch {
        val playlist = Playlist(name = name)
        repository.createPlaylist(playlist)
    }
}