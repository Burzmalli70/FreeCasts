package dev.josephwilliams.freecasts.model.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.josephwilliams.freecasts.model.entities.Download
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(download: Download)

    @Query("SELECT * FROM downloads ORDER BY lastModified DESC")
    fun getAllDownloads(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE completed = 0")
    suspend fun getActiveDownloads(): List<Download>

    @Query("UPDATE downloads SET completed = 1 WHERE id = :downloadId")
    suspend fun markDownloadAsCompleted(downloadId: Long)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes WHERE id = :downloadId")
    suspend fun updateDownloadedBytes(downloadId: Long, downloadedBytes: Long)
}