package dev.josephwilliams.freecasts.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents the download status and metadata for an episode.
 */
@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episodeId"], unique = true),
        Index(value = ["status"])
    ]
)
data class Download(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Foreign key to the episode being downloaded */
    val episodeId: Long,
    
    /** Current download status */
    val status: DownloadStatus = DownloadStatus.PENDING,
    
    /** Local file path where the episode is/will be stored */
    val localFilePath: String? = null,
    
    /** Download progress (0-100) */
    val progressPercent: Int = 0,
    
    /** Bytes downloaded so far */
    val downloadedBytes: Long = 0,
    
    /** Total file size in bytes */
    val totalBytes: Long = 0,
    
    /** Timestamp when download was requested */
    val requestedAt: Long = System.currentTimeMillis(),
    
    /** Timestamp when download started */
    val startedAt: Long? = null,
    
    /** Timestamp when download completed */
    val completedAt: Long? = null,
    
    /** Error message if download failed */
    val errorMessage: String? = null,
    
    /** Number of retry attempts */
    val retryCount: Int = 0
)

/**
 * Enum representing possible download states.
 */
enum class DownloadStatus {
    /** Download is queued but not started */
    PENDING,
    
    /** Download is currently in progress */
    DOWNLOADING,
    
    /** Download is paused */
    PAUSED,
    
    /** Download completed successfully */
    COMPLETED,
    
    /** Download failed */
    FAILED,
    
    /** Download was cancelled by user */
    CANCELLED
}

