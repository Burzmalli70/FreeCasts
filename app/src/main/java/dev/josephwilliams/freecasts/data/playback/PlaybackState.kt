package dev.josephwilliams.freecasts.data.playback

/**
 * Represents the current state of audio playback.
 */
data class PlaybackState(
    val currentEpisode: PlayingEpisode? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val queue: List<PlayingEpisode> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isRandomFavoriteMode: Boolean = false
) {
    val progressPercent: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs) else 0f
    
    val hasMedia: Boolean
        get() = currentEpisode != null
    
    val hasQueue: Boolean
        get() = queue.isNotEmpty()
    
    val hasNextInQueue: Boolean
        get() = currentQueueIndex >= 0 && currentQueueIndex < queue.size - 1
    
    val canPlayRandomFavoriteNext: Boolean
        get() = isRandomFavoriteMode && hasMedia && !hasNextInQueue
    
    val hasPreviousInQueue: Boolean
        get() = currentQueueIndex > 0
    
    val queueSize: Int
        get() = queue.size
    
    val queuePosition: Int
        get() = if (currentQueueIndex >= 0) currentQueueIndex + 1 else 0
}

/**
 * Information about the currently playing episode.
 */
data class PlayingEpisode(
    val episodeId: Long,
    val episodeTitle: String,
    val podcastId: Long,
    val podcastName: String,
    val artworkUrl: String?,
    val audioUrl: String,
    val localFilePath: String? = null
)
