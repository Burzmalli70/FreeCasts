package dev.josephwilliams.freecasts.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an individual podcast episode.
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["guid"], unique = true),
        Index(value = ["isFavorite"]),
        Index(value = ["publishedAt"])
    ]
)
data class Episode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Foreign key to the parent podcast */
    val podcastId: Long,
    
    /** Unique identifier from the RSS feed (guid) */
    val guid: String,
    
    /** Episode title */
    val title: String,
    
    /** Episode description/show notes */
    val description: String? = null,
    
    /** URL to the audio file */
    val audioUrl: String,
    
    /** URL to episode-specific artwork (falls back to podcast artwork) */
    val artworkUrl: String? = null,
    
    /** Duration in seconds */
    val durationSeconds: Int? = null,
    
    /** File size in bytes */
    val fileSizeBytes: Long? = null,
    
    /** MIME type of the audio file */
    val mimeType: String? = null,
    
    /** Publication timestamp */
    val publishedAt: Long? = null,
    
    /** Episode number (if available) */
    val episodeNumber: Int? = null,
    
    /** Season number (if available) */
    val seasonNumber: Int? = null,
    
    /** Whether this episode is marked as favorite */
    val isFavorite: Boolean = false,
    
    /** Timestamp when marked as favorite */
    val favoritedAt: Long? = null,
    
    /** Playback position in milliseconds (for resume) */
    val playbackPositionMs: Long = 0,
    
    /** Whether the episode has been fully played */
    val isPlayed: Boolean = false,
    
    /** Timestamp when the episode was last played */
    val lastPlayedAt: Long? = null
)

