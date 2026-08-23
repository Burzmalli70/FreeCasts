package com.lazysimulation.freecasts.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Podcast

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

