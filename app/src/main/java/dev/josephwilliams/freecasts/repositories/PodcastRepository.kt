package dev.josephwilliams.freecasts.repositories

import android.util.Log
import dev.josephwilliams.freecasts.model.daos.EpisodeDao
import dev.josephwilliams.freecasts.model.daos.PlaylistDao
import dev.josephwilliams.freecasts.model.daos.PodcastDao
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.model.relationships.PlaylistEpisode
import dev.josephwilliams.freecasts.model.relationships.PlaylistWithEpisodes
import dev.josephwilliams.freecasts.model.relationships.PodcastWithEpisodes
import dev.josephwilliams.freecasts.network.iTunesAPI
import kotlinx.coroutines.flow.Flow

class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao,
    private val iTunesAPI: iTunesAPI
) {

    suspend fun addPodcast(podcast: Podcast): Long {
        return podcastDao.insert(podcast)
    }

    fun getAllPodcasts(): Flow<List<Podcast>> {
        return podcastDao.getAllPodcasts()
    }

    fun getPodcastWithEpisodes(podcastId: Long): Flow<PodcastWithEpisodes> {
        return podcastDao.getPodcastWithEpisodes(podcastId)
    }

    suspend fun addEpisode(episode: Episode): Long {
        return episodeDao.insert(episode)
    }

    fun getEpisodesForPodcast(podcastId: Long): Flow<List<Episode>> {
        return episodeDao.getEpisodesForPodcast(podcastId)
    }

    suspend fun updatePlaybackPosition(episodeId: Long, position: Long) {
        episodeDao.updatePlayedPosition(episodeId, position)
    }

    suspend fun createPlaylist(playlist: Playlist): Long {
        return playlistDao.insert(playlist)
    }

    suspend fun getPlaylistById(playlistId: Long): Playlist? {
        return playlistDao.getPlaylistById(playlistId)
    }

    fun getAllPlaylists(): Flow<List<PlaylistWithEpisodes>> {
        return playlistDao.getAllPlaylistsWithEpisodes()
    }

    suspend fun addEpisodeToPlaylist(playlistId: Long, episodeId: Long, position: Int) {
        val playlistEpisode = PlaylistEpisode(playlistId, episodeId, position)
        playlistDao.addEpisodeToPlaylist(playlistEpisode)
    }

    suspend fun getEpisodeById(episodeId: Long): Episode? {
        return episodeDao.getEpisodeById(episodeId)
    }

    suspend fun getPlaylistWithEpisodes(playlistId: Int): PlaylistWithEpisodes {
        return playlistDao.getPlaylistWithEpisodes(playlistId)
    }

    suspend fun findNewPodcasts(query: String? = null): List<Podcast> {
        return try {
            if (query?.startsWith(URL_PREFIX) == true) {
//                parseUrlForPodcast(query)?.let {
//                    listOf(it)
//                } ?: emptyList()
                emptyList()
            } else {
                val result = iTunesAPI.searchITunes(query ?: "")

                if (result.isSuccessful) {
                    result.body()?.results ?: emptyList()
                } else {
                    emptyList()
                }
            }
        } catch(ex: Exception) {
            Log.e("Pod Search", "Search failed: ${ex.cause}")
            emptyList()
        }
    }

    companion object {
        const val URL_PREFIX = "http"
    }
}