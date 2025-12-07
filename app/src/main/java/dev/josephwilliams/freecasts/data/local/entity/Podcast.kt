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
    val cached: Boolean = true
)

