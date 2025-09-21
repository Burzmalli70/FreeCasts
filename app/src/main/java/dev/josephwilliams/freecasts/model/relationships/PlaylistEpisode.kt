package dev.josephwilliams.freecasts.model.relationships

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist

@Entity(
    tableName = "playlist_episodes",
    primaryKeys = ["playlist_id", "episode_id"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlist_id"),
        Index("episode_id")
    ]
)
data class PlaylistEpisode(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "episode_id")
    val episodeId: Long,

    val position: Int
)
