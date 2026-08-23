package com.lazysimulation.freecasts.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.local.relation.PodcastWithEpisodes
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(podcast: Podcast): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(podcasts: List<Podcast>): List<Long>
    
    @Update
    suspend fun update(podcast: Podcast)
    
    @Delete
    suspend fun delete(podcast: Podcast)
    
    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deleteById(podcastId: Long)
    
    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getById(id: Long): Podcast?
    
    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observeById(id: Long): Flow<Podcast?>
    
    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): Podcast?
    
    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun observeAll(): Flow<List<Podcast>>
    
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title ASC")
    fun observeSubscribed(): Flow<List<Podcast>>
    
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY subscribedAt DESC")
    fun observeSubscribedByDate(): Flow<List<Podcast>>
    
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1")
    suspend fun getSubscribed(): List<Podcast>
    
    @Query("UPDATE podcasts SET isSubscribed = 1, subscribedAt = :timestamp WHERE id = :podcastId")
    suspend fun subscribe(podcastId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE podcasts SET isSubscribed = 0, subscribedAt = NULL WHERE id = :podcastId")
    suspend fun unsubscribe(podcastId: Long)

    @Query("UPDATE podcasts SET isSubscribed = 0, subscribedAt = NULL WHERE feedUrl = :feedUrl")
    suspend fun unsubscribe(feedUrl: String)
    
    @Query("SELECT * FROM podcasts WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Podcast>>
    
    @Query("UPDATE podcasts SET lastFetchedAt = :timestamp WHERE id = :podcastId")
    suspend fun updateLastFetchedAt(podcastId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE podcasts SET episodeCount = :count WHERE id = :podcastId")
    suspend fun updateEpisodeCount(podcastId: Long, count: Int)
    
    @Transaction
    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    suspend fun getPodcastWithEpisodes(podcastId: Long): PodcastWithEpisodes?
    
    @Transaction
    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun observePodcastWithEpisodes(podcastId: Long): Flow<PodcastWithEpisodes?>
    
    @Transaction
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1")
    fun observeSubscribedWithEpisodes(): Flow<List<PodcastWithEpisodes>>
    
    // === Subscription Settings ===
    
    @Query("UPDATE podcasts SET autoDownloadNewEpisodes = :enabled WHERE id = :podcastId")
    suspend fun setAutoDownloadNewEpisodes(podcastId: Long, enabled: Boolean)
    
    @Query("UPDATE podcasts SET episodeFilterPattern = :pattern WHERE id = :podcastId")
    suspend fun setEpisodeFilterPattern(podcastId: Long, pattern: String?)
    
    @Query("UPDATE podcasts SET autoAddToPlaylistIds = :playlistIds WHERE id = :podcastId")
    suspend fun setAutoAddToPlaylistIds(podcastId: Long, playlistIds: String?)
    
    @Query("UPDATE podcasts SET deleteAfterListening = :enabled WHERE id = :podcastId")
    suspend fun setDeleteAfterListening(podcastId: Long, enabled: Boolean)
    
    @Query("UPDATE podcasts SET keepFavoritesFromDeletion = :enabled WHERE id = :podcastId")
    suspend fun setKeepFavoritesFromDeletion(podcastId: Long, enabled: Boolean)
    
    @Query("UPDATE podcasts SET keepInPlaylistsFromDeletion = :enabled WHERE id = :podcastId")
    suspend fun setKeepInPlaylistsFromDeletion(podcastId: Long, enabled: Boolean)
    
    @Query("UPDATE podcasts SET maxDownloadsToKeep = :maxDownloads WHERE id = :podcastId")
    suspend fun setMaxDownloadsToKeep(podcastId: Long, maxDownloads: Int?)
    
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 AND autoDownloadNewEpisodes = 1")
    suspend fun getSubscribedWithAutoDownload(): List<Podcast>
}

