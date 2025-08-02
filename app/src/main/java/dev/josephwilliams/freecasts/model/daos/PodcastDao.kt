package dev.josephwilliams.freecasts.model.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.model.relationships.PodcastWithEpisodes
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Insert
    suspend fun insert(podcast: Podcast): Long

    @Update
    suspend fun update(podcast: Podcast)

    @Delete
    suspend fun delete(podcast: Podcast)

    @Query("SELECT * FROM podcasts")
    fun getAllPodcasts(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getPodcastById(id: Long): Podcast?

    @Transaction
    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun getPodcastFlowWithEpisodes(podcastId: Long): Flow<PodcastWithEpisodes>

    @Transaction
    @Query("SELECT * FROM podcasts WHERE id = :podcastId")
    fun getPodcastWithEpisodes(podcastId: Long): PodcastWithEpisodes
}