package dev.josephwilliams.freecasts.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.josephwilliams.freecasts.data.local.entity.Download
import dev.josephwilliams.freecasts.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: Download): Long
    
    @Update
    suspend fun update(download: Download)
    
    @Delete
    suspend fun delete(download: Download)
    
    @Query("DELETE FROM downloads WHERE id = :downloadId")
    suspend fun deleteById(downloadId: Long)
    
    @Query("DELETE FROM downloads WHERE episodeId = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: Long)
    
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): Download?
    
    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<Download?>
    
    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    suspend fun getByEpisodeId(episodeId: Long): Download?
    
    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    fun observeByEpisodeId(episodeId: Long): Flow<Download?>
    
    @Query("SELECT * FROM downloads ORDER BY requestedAt DESC")
    fun observeAll(): Flow<List<Download>>
    
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY requestedAt ASC")
    fun observeByStatus(status: DownloadStatus): Flow<List<Download>>
    
    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY requestedAt ASC")
    fun observeByStatuses(statuses: List<DownloadStatus>): Flow<List<Download>>
    
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT :limit")
    suspend fun getCompleted(limit: Int): List<Download>
    
    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'DOWNLOADING') ORDER BY requestedAt ASC")
    fun observeActive(): Flow<List<Download>>
    
    @Query("SELECT * FROM downloads WHERE status = 'FAILED' ORDER BY requestedAt DESC")
    fun observeFailed(): Flow<List<Download>>
    
    // Status updates
    @Query("UPDATE downloads SET status = :status WHERE id = :downloadId")
    suspend fun updateStatus(downloadId: Long, status: DownloadStatus)
    
    @Query("UPDATE downloads SET status = :status WHERE episodeId = :episodeId")
    suspend fun updateStatusByEpisodeId(episodeId: Long, status: DownloadStatus)
    
    @Query("UPDATE downloads SET status = 'DOWNLOADING', startedAt = :timestamp WHERE id = :downloadId")
    suspend fun markAsDownloading(downloadId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE downloads SET status = 'COMPLETED', completedAt = :timestamp, progressPercent = 100 WHERE id = :downloadId")
    suspend fun markAsCompleted(downloadId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :errorMessage WHERE id = :downloadId")
    suspend fun markAsFailed(downloadId: Long, errorMessage: String?)
    
    @Query("UPDATE downloads SET status = 'PAUSED' WHERE id = :downloadId")
    suspend fun markAsPaused(downloadId: Long)
    
    @Query("UPDATE downloads SET status = 'CANCELLED' WHERE id = :downloadId")
    suspend fun markAsCancelled(downloadId: Long)
    
    // Progress updates
    @Query("UPDATE downloads SET progressPercent = :percent, downloadedBytes = :downloadedBytes WHERE id = :downloadId")
    suspend fun updateProgress(downloadId: Long, percent: Int, downloadedBytes: Long)
    
    @Query("UPDATE downloads SET localFilePath = :path WHERE id = :downloadId")
    suspend fun updateLocalFilePath(downloadId: Long, path: String)

    @Query("UPDATE downloads SET androidDownloadManagerId = :downloadManagerId WHERE id = :downloadId")
    suspend fun updateAndroidDownloadManagerId(downloadId: Long, downloadManagerId: Long)

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'DOWNLOADING') ORDER BY requestedAt ASC")
    suspend fun getPendingAndDownloading(): List<Download>
    
    @Query("UPDATE downloads SET retryCount = retryCount + 1 WHERE id = :downloadId")
    suspend fun incrementRetryCount(downloadId: Long)
    
    // Queries
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE episodeId = :episodeId AND status = 'COMPLETED')")
    suspend fun isEpisodeDownloaded(episodeId: Long): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE episodeId = :episodeId AND status = 'COMPLETED')")
    fun observeIsEpisodeDownloaded(episodeId: Long): Flow<Boolean>
    
    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun observeDownloadedCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('PENDING', 'DOWNLOADING')")
    fun observeActiveDownloadCount(): Flow<Int>
    
    @Query("SELECT SUM(downloadedBytes) FROM downloads WHERE status = 'COMPLETED'")
    fun observeTotalDownloadedBytes(): Flow<Long?>
}

