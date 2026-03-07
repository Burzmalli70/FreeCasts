package dev.josephwilliams.freecasts.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.local.relation.PlaylistWithEpisodes
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long
    
    @Update
    suspend fun update(playlist: Playlist)
    
    @Delete
    suspend fun delete(playlist: Playlist)
    
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deleteById(playlistId: Long)
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): Playlist?
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<Playlist?>
    
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Playlist>>
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun observeAllByName(): Flow<List<Playlist>>
    
    @Query("SELECT * FROM playlists WHERE name LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Playlist>>
    
    @Query("UPDATE playlists SET updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updateTimestamp(playlistId: Long, timestamp: Long = System.currentTimeMillis())
    
    // Playlist-Episode relationship management
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistEpisode(crossRef: PlaylistEpisodeCrossRef)
    
    @Delete
    suspend fun deletePlaylistEpisode(crossRef: PlaylistEpisodeCrossRef)
    
    @Query("DELETE FROM playlist_episode_cross_ref WHERE playlistId = :playlistId AND episodeId = :episodeId")
    suspend fun removeEpisodeFromPlaylist(playlistId: Long, episodeId: Long)
    
    @Query("DELETE FROM playlist_episode_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
    
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_episode_cross_ref WHERE playlistId = :playlistId AND episodeId = :episodeId)")
    suspend fun isEpisodeInPlaylist(playlistId: Long, episodeId: Long): Boolean
    
    @Query("SELECT MAX(position) FROM playlist_episode_cross_ref WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?
    
    @Query("UPDATE playlist_episode_cross_ref SET position = :position WHERE playlistId = :playlistId AND episodeId = :episodeId")
    suspend fun updateEpisodePosition(playlistId: Long, episodeId: Long, position: Int)
    
    @Query("SELECT COUNT(*) FROM playlist_episode_cross_ref WHERE playlistId = :playlistId")
    suspend fun getEpisodeCount(playlistId: Long): Int
    
    @Query("SELECT COUNT(*) FROM playlist_episode_cross_ref WHERE playlistId = :playlistId")
    fun observeEpisodeCount(playlistId: Long): Flow<Int>
    
    // Relations
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithEpisodes(playlistId: Long): PlaylistWithEpisodes?
    
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylistWithEpisodes(playlistId: Long): Flow<PlaylistWithEpisodes?>
    
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAllWithEpisodes(): Flow<List<PlaylistWithEpisodes>>
    
    @Query("SELECT playlistId FROM playlist_episode_cross_ref WHERE episodeId = :episodeId")
    fun observePlaylistsContainingEpisode(episodeId: Long): Flow<List<Long>>
    
    // === Auto-Remove Settings ===
    
    @Query("UPDATE playlists SET removeAfterListening = :enabled WHERE id = :playlistId")
    suspend fun setRemoveAfterListening(playlistId: Long, enabled: Boolean)
    
    @Query("SELECT * FROM playlists WHERE removeAfterListening = 1")
    suspend fun getPlaylistsWithAutoRemove(): List<Playlist>
    
    // === Auto-Add Settings ===
    
    @Query("SELECT * FROM playlists WHERE autoAddPodcastIds IS NOT NULL AND autoAddPodcastIds != ''")
    suspend fun getPlaylistsWithAutoAdd(): List<Playlist>
    
    @Query("UPDATE playlists SET autoAddPodcastIds = :podcastIds WHERE id = :playlistId")
    suspend fun setAutoAddPodcastIds(playlistId: Long, podcastIds: String?)
}

