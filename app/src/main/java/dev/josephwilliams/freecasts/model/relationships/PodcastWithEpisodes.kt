package dev.josephwilliams.freecasts.model.relationships

import androidx.room.Embedded
import androidx.room.Relation
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Podcast

data class PodcastWithEpisodes(
    @Embedded
    val podcast: Podcast,

    @Relation(
        parentColumn = "id",
        entityColumn = "podcast_id"
    )
    val episodes: List<Episode>
)
