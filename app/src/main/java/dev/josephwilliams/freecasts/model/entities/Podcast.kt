package dev.josephwilliams.freecasts.model.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "podcasts")
data class Podcast(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @SerialName("collectionName")
    val title: String = "",

    @SerialName("trackId")
    val podcastApiId: Long = 0,

    @SerialName("artistName")
    val author: String? = null,

    @SerialName("trackName")
    val description: String? = null,

    @SerialName("artworkUrl60")
    @ColumnInfo(name = "small_image_url")
    val smallImageUrl: String? = null,

    @SerialName("artworkUrl600")
    @ColumnInfo(name = "large_image_url")
    val largeImageUrl: String? = null,

    @ColumnInfo(name = "feed_url")
    val feedUrl: String? = null,

    val subscribed: Boolean = false
)