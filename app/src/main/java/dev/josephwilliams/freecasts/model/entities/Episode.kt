package dev.josephwilliams.freecasts.model.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["id"],
            childColumns = ["podcast_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("podcast_id")]
)
data class Episode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "podcast_id")
    val podcastId: Long? = null,

    val title: String? = null,

    val description: String? = null,

    @ColumnInfo(name = "audio_url")
    val audioUrl: String? = null,

    @ColumnInfo(name = "local_uri")
    val localUri: String? = null,

    val duration: Long? = null,

    @ColumnInfo(name = "publication_date")
    val publicationDate: Long? = null,

    @ColumnInfo(name = "played_position")
    val playedPosition: Long = 0,

    @ColumnInfo(name = "played_count")
    val playedCount: Int = 0,

    val favorite: Boolean = false,

    val downloaded: Boolean = false
)
