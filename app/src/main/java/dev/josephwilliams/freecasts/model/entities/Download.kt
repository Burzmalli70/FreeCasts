package dev.josephwilliams.freecasts.model.entities

import androidx.room.Entity

@Entity(tableName = "downloads")
data class Download(
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
)