package dev.josephwilliams.freecasts.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a podcast that the user can subscribe to.
 * Contains metadata about the podcast feed.
 */
@Entity(
    tableName = "podcasts",
    indices = [Index(value = ["feedUrl"], unique = true)]
)
data class Podcast(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** RSS feed URL - unique identifier for the podcast */
    val feedUrl: String = "",
    
    /** Title of the podcast */
    val title: String = "",
    
    /** Author/creator of the podcast */
    val author: String? = null,
    
    /** Description of the podcast */
    val description: String? = null,
    
    /** URL to the podcast artwork */
    val artworkUrl: String? = null,
    
    /** Website URL */
    val websiteUrl: String? = null,
    
    /** Whether the user is subscribed to this podcast */
    val isSubscribed: Boolean = false,
    
    /** Timestamp when the user subscribed */
    val subscribedAt: Long? = null,
    
    /** Timestamp when the feed was last fetched */
    val lastFetchedAt: Long? = null,
    
    /** Total episode count */
    val episodeCount: Int = 0,
    
    /** Categories/genres */
    val categories: String? = null,

    /** Indicates whether the podcast is a cached entry from the RSS feed */
    val cached: Boolean = true,
    
    // === Subscription Settings ===
    
    /** Whether to automatically download new episodes */
    val autoDownloadNewEpisodes: Boolean = false,
    
    /** 
     * Pattern for filtering episodes to download or skip.
     * Episodes matching this pattern will be skipped and marked as listened.
     * Stored as a simple string pattern (e.g., "bonus|trailer|preview")
     */
    val episodeFilterPattern: String? = null,
    
    /**
     * Comma-separated list of playlist IDs to automatically add new episodes to.
     * Example: "1,5,12"
     */
    val autoAddToPlaylistIds: String? = null,
    
    /** Whether to delete downloaded episodes after listening */
    val deleteAfterListening: Boolean = false,
    
    /** Whether to keep favorite episodes from auto-deletion */
    val keepFavoritesFromDeletion: Boolean = true,
    
    /** Whether to keep episodes that are in playlists from auto-deletion */
    val keepInPlaylistsFromDeletion: Boolean = true,
    
    /** Maximum number of downloaded episodes to keep. Oldest will be deleted first. Null means unlimited. */
    val maxDownloadsToKeep: Int? = null
)

