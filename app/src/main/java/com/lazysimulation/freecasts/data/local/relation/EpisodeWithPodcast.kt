package com.lazysimulation.freecasts.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Podcast

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

