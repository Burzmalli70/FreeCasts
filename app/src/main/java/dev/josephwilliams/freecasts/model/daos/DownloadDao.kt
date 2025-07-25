package dev.josephwilliams.freecasts.model.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.josephwilliams.freecasts.model.entities.Download
import dev.josephwilliams.freecasts.model.entities.DownloadStatus

@Dao
interface DownloadDao {
    @Insert
    suspend fun insertDownload(download: Download): Long

    @Update
    suspend fun update(download: Download)

    @Delete
    suspend fun delete(download: Download)

    @Query("SELECT * FROM downloads WHERE status = :status")
    suspend fun getDownloadsByStatus(status: DownloadStatus): List<Download>

}