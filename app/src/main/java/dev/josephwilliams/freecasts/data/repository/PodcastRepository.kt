package dev.josephwilliams.freecasts.data.repository

import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import dev.josephwilliams.freecasts.data.remote.PodcastSearchApi
import dev.josephwilliams.freecasts.data.remote.model.ItunesPodcast
import dev.josephwilliams.freecasts.tools.RssParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for managing podcast data including search, RSS feed fetching,
 * and local database operations.
 */
class PodcastRepository(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao,
    private val searchApi: PodcastSearchApi
) {
    private val httpClient = HttpClient(OkHttp)
    
    // === Search ===
    
    /**
     * Search for podcasts using the iTunes API.
     */
    suspend fun searchPodcasts(query: String, limit: Int = 25): Result<List<ItunesPodcast>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = searchApi.searchPodcasts(query, limit)
                Result.success(response.results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    // === RSS Feed Fetching ===
    
    /**
     * Fetch and parse a podcast's RSS feed.
     * Returns the podcast info and list of episodes.
     */
    suspend fun fetchPodcastFeed(feedUrl: String): Result<RssParser.ParseResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get(feedUrl)
                val inputStream = response.bodyAsChannel().toInputStream()
                val result = RssParser.parsePodcastFeed(inputStream)
                if (result != null) {
                    // Set the feed URL on the parsed podcast
                    val podcastWithFeedUrl = result.podcast?.copy(feedUrl = feedUrl)
                    Result.success(RssParser.ParseResult(podcastWithFeedUrl, result.episodes))
                } else {
                    Result.failure(Exception("Failed to parse RSS feed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Fetch episodes for a podcast from its RSS feed.
     * Useful for getting latest episodes without full re-parse.
     */
    suspend fun fetchEpisodes(feedUrl: String, limit: Int = 50): Result<List<Episode>> {
        return fetchPodcastFeed(feedUrl).map { result ->
            result.episodes.take(limit)
        }
    }
    
    // === Subscription Management ===
    
    /**
     * Subscribe to a podcast. 
     * Saves the podcast and its episodes to the database.
     */
    suspend fun subscribeToPodcast(
        feedUrl: String,
        itunesPodcast: ItunesPodcast? = null
    ): Result<Long> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if already subscribed
                val existing = podcastDao.getByFeedUrl(feedUrl)
                if (existing?.isSubscribed == true) {
                    return@withContext Result.success(existing.id)
                }
                
                // Fetch the RSS feed to get full podcast info and episodes
                val feedResult = fetchPodcastFeed(feedUrl)
                if (feedResult.isFailure) {
                    return@withContext Result.failure(feedResult.exceptionOrNull()!!)
                }
                
                val parseResult = feedResult.getOrThrow()
                val parsedPodcast = parseResult.podcast ?: return@withContext Result.failure(
                    Exception("Could not parse podcast from feed")
                )
                
                // Merge iTunes data with RSS data if available
                val podcast = if (itunesPodcast != null) {
                    parsedPodcast.copy(
                        artworkUrl = itunesPodcast.artworkUrl600 
                            ?: itunesPodcast.artworkUrl100 
                            ?: parsedPodcast.artworkUrl,
                        author = itunesPodcast.artistName ?: parsedPodcast.author,
                        isSubscribed = true,
                        subscribedAt = System.currentTimeMillis(),
                        cached = false
                    )
                } else {
                    parsedPodcast.copy(
                        isSubscribed = true,
                        subscribedAt = System.currentTimeMillis(),
                        cached = false
                    )
                }
                
                // Save podcast
                val podcastId = if (existing != null) {
                    podcastDao.update(podcast.copy(id = existing.id))
                    existing.id
                } else {
                    podcastDao.insert(podcast)
                }
                
                // Save episodes
                val episodesWithPodcastId = parseResult.episodes.map { episode ->
                    episode.copy(podcastId = podcastId, cached = false)
                }
                episodeDao.insertAll(episodesWithPodcastId)
                
                Result.success(podcastId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Unsubscribe from a podcast.
     */
    suspend fun unsubscribeFromPodcast(podcastId: Long) {
        withContext(Dispatchers.IO) {
            podcastDao.unsubscribe(podcastId)
        }
    }

    /**
     * Unsubscribe from a podcast.
     */
    suspend fun unsubscribeFromPodcast(feedUrl: String) {
        withContext(Dispatchers.IO) {
            podcastDao.unsubscribe(feedUrl)
        }
    }
    
    /**
     * Check if a podcast is subscribed by feed URL.
     */
    suspend fun isSubscribed(feedUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            podcastDao.getByFeedUrl(feedUrl)?.isSubscribed == true
        }
    }
    
    // === Database Queries ===
    
    /**
     * Get all subscribed podcasts as a Flow.
     */
    fun observeSubscribedPodcasts(): Flow<List<Podcast>> {
        return podcastDao.observeSubscribed()
    }
    
    /**
     * Get a podcast by ID.
     */
    suspend fun getPodcast(podcastId: Long): Podcast? {
        return podcastDao.getById(podcastId)
    }
    
    /**
     * Get a podcast by feed URL.
     */
    suspend fun getPodcastByFeedUrl(feedUrl: String): Podcast? {
        return podcastDao.getByFeedUrl(feedUrl)
    }
    
    /**
     * Observe episodes for a podcast.
     */
    fun observeEpisodes(podcastId: Long): Flow<List<Episode>> {
        return episodeDao.observeByPodcastId(podcastId)
    }
    
    /**
     * Refresh a podcast's episodes from its RSS feed.
     */
    suspend fun refreshPodcast(podcastId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val podcast = podcastDao.getById(podcastId) 
                    ?: return@withContext Result.failure(Exception("Podcast not found"))
                
                val feedResult = fetchPodcastFeed(podcast.feedUrl)
                if (feedResult.isFailure) {
                    return@withContext Result.failure(feedResult.exceptionOrNull()!!)
                }
                
                val parseResult = feedResult.getOrThrow()

                val currentEpisodeGuids = episodeDao.getAllByPodcastId(podcastId).map { it.guid }
                
                // Update new episodes
                val newEpisodes = parseResult.episodes.filter { !currentEpisodeGuids.contains(it.guid) }.map { episode ->
                    episode.copy(podcastId = podcastId)
                }
                episodeDao.insertAll(newEpisodes)
                
                // Update last fetched timestamp
                podcastDao.updateLastFetchedAt(podcastId)
                podcastDao.updateEpisodeCount(podcastId, parseResult.episodes.size)

                // Add episodes to related playlists
                val playlists = playlistDao.getPlaylistsWithAutoAddForPodcast(podcast.id.toString())
                playlists.forEach { playlist ->
                    newEpisodes.forEach { episode ->
                        if (!playlistDao.isEpisodeInPlaylist(playlist.id, episode.id)) {
                            val maxPosition = playlistDao.getMaxPosition(playlist.id) ?: -1
                            val newPosition = maxPosition + 1

                            // Add episode to playlist
                            playlistDao.insertPlaylistEpisode(
                                PlaylistEpisodeCrossRef(
                                    playlistId = playlist.id,
                                    episodeId = episode.id,
                                    position = newPosition,
                                    addedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
