package dev.josephwilliams.freecasts.model.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.josephwilliams.freecasts.model.entities.Playlist
import dev.josephwilliams.freecasts.model.relationships.PlaylistEpisode
import dev.josephwilliams.freecasts.model.relationships.PlaylistWithEpisodes
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Update
    suspend fun update(playlist: Playlist)

    @Delete
    suspend fun delete(playlist: Playlist)

    @Query("SELECT * FROM playlists ORDER BY name")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert
    suspend fun addEpisodeToPlaylist(playlistEpisode: PlaylistEpisode)

    @Delete
    suspend fun removeEpisodeFromPlaylist(playlistEpisode: PlaylistEpisode)

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithEpisodes(playlistId: Int): PlaylistWithEpisodes

    @Transaction
    @Query("SELECT * FROM playlists")
    fun getAllPlaylistsWithEpisodes(): Flow<List<PlaylistWithEpisodes>>
}