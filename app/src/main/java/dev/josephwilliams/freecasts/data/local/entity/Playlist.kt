package dev.josephwilliams.freecasts.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a user-created playlist of episodes.
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** Name of the playlist */
    val name: String,
    
    /** Optional description */
    val description: String? = null,
    
    /** Timestamp when the playlist was created */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Timestamp when the playlist was last modified */
    val updatedAt: Long = System.currentTimeMillis(),
    
    /** Optional artwork URL (could be auto-generated from episode artwork) */
    val artworkUrl: String? = null
)

