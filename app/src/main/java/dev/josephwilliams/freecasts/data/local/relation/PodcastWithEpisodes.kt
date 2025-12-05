package dev.josephwilliams.freecasts.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Podcast

/**
 * Represents a podcast with all its episodes.
 */
data class PodcastWithEpisodes(
    @Embedded
    val podcast: Podcast,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "podcastId"
    )
    val episodes: List<Episode>
)

