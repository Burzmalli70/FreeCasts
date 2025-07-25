package dev.josephwilliams.freecasts.model.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "current_bytes")
    val currentBytes: Long,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,

    val status: DownloadStatus,
    val url: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_name")
    val fileName: String
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED;

    fun toModel(): dev.joewilliams.downloadmanager.DownloadStatus {
        return dev.joewilliams.downloadmanager.DownloadStatus.safeFromString(this.name)
    }

    companion object {
        fun safeFromString(value: String): DownloadStatus {
            return DownloadStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: FAILED
        }
    }
}

class DownloadStatusOrdinalConverter {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus?): Int? {
        return status?.ordinal
    }

    @TypeConverter
    fun toDownloadStatus(ordinal: Int?): DownloadStatus? {
        return ordinal?.let { DownloadStatus.entries[it] }
    }
}