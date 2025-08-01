package dev.josephwilliams.freecasts.model.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.josephwilliams.freecasts.model.entities.Episode
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Insert
    suspend fun insert(episode: Episode): Long

    @Update
    suspend fun update(episode: Episode)

    @Delete
    suspend fun delete(episode: Episode)

    @Query("SELECT * FROM episodes WHERE podcast_id = :podcastId ORDER BY publication_date DESC")
    fun getEpisodesForPodcast(podcastId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Long): Episode?

    @Query("UPDATE episodes SET played_position = :position WHERE id = :episodeId")
    suspend fun updatePlayedPosition(episodeId: Long, position: Long)

    @Query("UPDATE episodes SET played_count = played_count + 1 WHERE id = :episodeId")
    suspend fun incrementPlayedCount(episodeId: Long)

    @Query("UPDATE episodes SET favorite = :isFavorite WHERE id = :episodeId")
    suspend fun updateFavoriteStatus(episodeId: Int, isFavorite: Boolean)
}