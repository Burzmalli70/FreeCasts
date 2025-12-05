package dev.josephwilliams.freecasts.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import dev.josephwilliams.freecasts.data.local.relation.PodcastWithEpisodes
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
    
    @Query("UPDATE podcasts SET isSubscribed = 1, subscribedAt = :timestamp WHERE id = :podcastId")
    suspend fun subscribe(podcastId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE podcasts SET isSubscribed = 0, subscribedAt = NULL WHERE id = :podcastId")
    suspend fun unsubscribe(podcastId: Long)
    
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
}

