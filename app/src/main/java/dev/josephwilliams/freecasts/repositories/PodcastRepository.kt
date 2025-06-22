package dev.josephwilliams.freecasts.repositories

import dev.josephwilliams.freecasts.model.daos.EpisodeDao
import dev.josephwilliams.freecasts.model.daos.PlaylistDao
import dev.josephwilliams.freecasts.model.daos.PodcastDao
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.model.relationships.PlaylistEpisode
import dev.josephwilliams.freecasts.model.relationships.PlaylistWithEpisodes
import dev.josephwilliams.freecasts.model.relationships.PodcastWithEpisodes
import kotlinx.coroutines.flow.Flow

class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao
) {

    suspend fun addPodcast(podcast: Podcast): Long {
        return podcastDao.insert(podcast)
    }

    fun getAllPodcasts(): Flow<List<Podcast>> {
        return podcastDao.getAllPodcasts()
    }

    fun getPodcastWithEpisodes(podcastId: Int): Flow<PodcastWithEpisodes> {
        return podcastDao.getPodcastWithEpisodes(podcastId)
    }

    suspend fun addEpisode(episode: Episode): Long {
        return episodeDao.insert(episode)
    }

    fun getEpisodesForPodcast(podcastId: Int): Flow<List<Episode>> {
        return episodeDao.getEpisodesForPodcast(podcastId)
    }

    suspend fun updatePlaybackPosition(episodeId: Int, position: Long) {
        episodeDao.updatePlayedPosition(episodeId, position)
    }

    suspend fun createPlaylist(playlist: Playlist): Long {
        return playlistDao.insert(playlist)
    }

    suspend fun addEpisodeToPlaylist(playlistId: Int, episodeId: Int, position: Int) {
        val playlistEpisode = PlaylistEpisode(playlistId, episodeId, position)
        playlistDao.addEpisodeToPlaylist(playlistEpisode)
    }

    fun getPlaylistWithEpisodes(playlistId: Int): Flow<PlaylistWithEpisodes> {
        return playlistDao.getPlaylistWithEpisodes(playlistId)
    }
}