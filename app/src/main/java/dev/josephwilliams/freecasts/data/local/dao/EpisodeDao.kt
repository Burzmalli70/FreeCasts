package dev.josephwilliams.freecasts.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.relation.EpisodeWithDownload
import dev.josephwilliams.freecasts.data.local.relation.EpisodeWithPodcast
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: Episode): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<Episode>): List<Long>
    
    @Update
    suspend fun update(episode: Episode)
    
    @Delete
    suspend fun delete(episode: Episode)
    
    @Query("DELETE FROM episodes WHERE id = :episodeId")
    suspend fun deleteById(episodeId: Long)
    
    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteByPodcastId(podcastId: Long)

    @Query("SELECT * FROM episodes WHERE isFavorite = 1 ORDER BY listenCount ASC")
    suspend fun getFavoriteEpisodes(): List<Episode>

    @Query("SELECT * FROM episodes WHERE isFavorite = 1 AND podcastId = :podcastId ORDER BY listenCount ASC")
    suspend fun getFavoriteEpisodesForPodcast(podcastId: Long): List<Episode>
    
    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): Episode?
    
    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observeById(id: Long): Flow<Episode?>
    
    @Query("SELECT * FROM episodes WHERE guid = :guid")
    suspend fun getByGuid(guid: String): Episode?
    
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun observeByPodcastId(podcastId: Long): Flow<List<Episode>>
    
    @Query("SELECT COUNT(*) FROM episodes WHERE podcastId = :podcastId")
    suspend fun getEpisodeCountForPodcast(podcastId: Long): Int
    
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC LIMIT :limit")
    fun observeByPodcastIdLimited(podcastId: Long, limit: Int): Flow<List<Episode>>
    
    @Query("SELECT * FROM episodes ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<Episode>>
    
    @Query("SELECT * FROM episodes ORDER BY publishedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Episode>>
    
    // Favorites
    @Query("SELECT * FROM episodes WHERE isFavorite = 1 ORDER BY favoritedAt DESC")
    fun observeFavorites(): Flow<List<Episode>>
    
    @Query("UPDATE episodes SET isFavorite = 1, favoritedAt = :timestamp WHERE id = :episodeId")
    suspend fun addToFavorites(episodeId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE episodes SET isFavorite = 0, favoritedAt = NULL WHERE id = :episodeId")
    suspend fun removeFromFavorites(episodeId: Long)
    
    @Query("UPDATE episodes SET isFavorite = :isFavorite, favoritedAt = CASE WHEN :isFavorite THEN :timestamp ELSE NULL END WHERE id = :episodeId")
    suspend fun toggleFavorite(episodeId: Long, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())
    
    // Playback tracking
    @Query("UPDATE episodes SET playbackPositionMs = :positionMs, lastPlayedAt = :timestamp WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE episodes SET playbackPositionMs = :positionMs WHERE id = :episodeId")
    suspend fun setPlaybackPosition(episodeId: Long, positionMs: Long)
    
    @Query("SELECT playbackPositionMs FROM episodes WHERE id = :episodeId")
    suspend fun getPlaybackPosition(episodeId: Long): Long
    
    @Query("UPDATE episodes SET lastPlayedAt = :timestamp WHERE id = :episodeId")
    suspend fun setLastPlayedAt(episodeId: Long, timestamp: Long)
    
    @Query("UPDATE episodes SET isPlayed = 1, playbackPositionMs = 0 WHERE id = :episodeId")
    suspend fun markAsPlayed(episodeId: Long)
    
    @Query("UPDATE episodes SET isPlayed = 0 WHERE id = :episodeId")
    suspend fun markAsUnplayed(episodeId: Long)
    
    @Query("SELECT * FROM episodes WHERE isPlayed = 0 AND playbackPositionMs > 0 ORDER BY lastPlayedAt DESC")
    fun observeInProgress(): Flow<List<Episode>>
    
    @Query("SELECT * FROM episodes WHERE isPlayed = 0 ORDER BY publishedAt DESC")
    fun observeUnplayed(): Flow<List<Episode>>
    
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND isPlayed = 0 ORDER BY publishedAt DESC")
    suspend fun getUnplayedByPodcastId(podcastId: Long): List<Episode>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    suspend fun getAllByPodcastId(podcastId: Long): List<Episode>
    
    // Search
    @Query("SELECT * FROM episodes WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY publishedAt DESC")
    fun search(query: String): Flow<List<Episode>>
    
    // Relations
    @Transaction
    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisodeWithPodcast(episodeId: Long): EpisodeWithPodcast?
    
    @Transaction
    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun observeEpisodeWithPodcast(episodeId: Long): Flow<EpisodeWithPodcast?>
    
    @Transaction
    @Query("SELECT * FROM episodes WHERE isFavorite = 1 ORDER BY favoritedAt DESC")
    fun observeFavoritesWithPodcast(): Flow<List<EpisodeWithPodcast>>
    
    @Transaction
    @Query("SELECT * FROM episodes ORDER BY publishedAt DESC LIMIT :limit")
    fun observeRecentWithPodcast(limit: Int): Flow<List<EpisodeWithPodcast>>
    
    @Transaction
    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun observeEpisodeWithDownload(episodeId: Long): Flow<EpisodeWithDownload?>
    
    @Transaction
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun observeEpisodesWithDownloadByPodcastId(podcastId: Long): Flow<List<EpisodeWithDownload>>
    
    // === Listen Count ===
    
    @Query("UPDATE episodes SET listenCount = listenCount + 1 WHERE id = :episodeId")
    suspend fun incrementListenCount(episodeId: Long)
    
    @Query("UPDATE episodes SET listenCount = :count WHERE id = :episodeId")
    suspend fun setListenCount(episodeId: Long, count: Int)
    
    @Query("SELECT listenCount FROM episodes WHERE id = :episodeId")
    suspend fun getListenCount(episodeId: Long): Int?
    
    @Query("SELECT * FROM episodes WHERE listenCount > 0 ORDER BY listenCount DESC")
    fun observeMostListened(): Flow<List<Episode>>
}

