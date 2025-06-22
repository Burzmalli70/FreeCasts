package dev.josephwilliams.freecasts.model.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class Podcast(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,

    val author: String,

    val description: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String,

    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,

    @ColumnInfo(name = "feed_url")
    val feedUrl: String
)
