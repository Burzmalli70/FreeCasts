package dev.josephwilliams.freecasts.model.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey(autoGenerate = false)
    var id: Long = 0,
    val url: String,
    val completed: Boolean = false,
    val downloadedBytes: Long = 0,
    val targetBytes: Long = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val episodeId: Long? = null,
    val podcastId: Long? = null,
    val podcastImgUrl: String? = null,
    val podcastTitle: String? = null,
    val episodeTitle: String? = null,
    val publishedDate: Long? = null
) {
    val progress: Float
        get() = downloadedBytes.toFloat() / targetBytes.toFloat()
}