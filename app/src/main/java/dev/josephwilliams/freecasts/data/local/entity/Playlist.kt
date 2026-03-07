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
    val artworkUrl: String? = null,
    
    /** Whether to automatically remove episodes from this playlist after they are played */
    val removeAfterListening: Boolean = false,
    
    /** 
     * Comma-separated list of podcast IDs whose new episodes should be 
     * automatically added to this playlist when synced.
     */
    val autoAddPodcastIds: String? = null
) {
    /** Get the list of podcast IDs that auto-add to this playlist */
    fun getAutoAddPodcastIdList(): List<Long> {
        return autoAddPodcastIds
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()
    }
    
    /** Create a copy with updated auto-add podcast IDs */
    fun withAutoAddPodcastIds(podcastIds: List<Long>): Playlist {
        return copy(autoAddPodcastIds = if (podcastIds.isEmpty()) null else podcastIds.joinToString(","))
    }
}

