package dev.josephwilliams.freecasts.data.local.converter

import androidx.room.TypeConverter
import dev.josephwilliams.freecasts.data.local.entity.DownloadStatus

/**
 * Type converters for Room database.
 */
class Converters {
    
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus {
        return DownloadStatus.valueOf(value)
    }
    
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(separator = "|||")
    }
    
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split("|||")?.filter { it.isNotEmpty() }
    }
}

