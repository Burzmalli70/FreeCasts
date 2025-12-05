package dev.josephwilliams.freecasts.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Podcast

/**
 * Represents an episode with its parent podcast information.
 */
data class EpisodeWithPodcast(
    @Embedded
    val episode: Episode,
    
    @Relation(
        parentColumn = "podcastId",
        entityColumn = "id"
    )
    val podcast: Podcast
)

