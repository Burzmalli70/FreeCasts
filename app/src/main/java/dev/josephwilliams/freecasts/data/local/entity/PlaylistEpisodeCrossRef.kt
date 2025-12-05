package dev.josephwilliams.freecasts.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table for the many-to-many relationship between Playlist and Episode.
 * Allows episodes to be in multiple playlists and playlists to contain multiple episodes.
 */
@Entity(
    tableName = "playlist_episode_cross_ref",
    primaryKeys = ["playlistId", "episodeId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["episodeId"])
    ]
)
data class PlaylistEpisodeCrossRef(
    val playlistId: Long,
    val episodeId: Long,
    
    /** Position of the episode within the playlist (for ordering) */
    val position: Int = 0,
    
    /** Timestamp when the episode was added to the playlist */
    val addedAt: Long = System.currentTimeMillis()
)

