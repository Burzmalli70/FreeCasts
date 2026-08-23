package com.lazysimulation.freecasts.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.lazysimulation.freecasts.data.local.entity.Download
import com.lazysimulation.freecasts.data.local.entity.Episode

/**
 * Represents an episode with its download status.
 */
data class EpisodeWithDownload(
    @Embedded
    val episode: Episode,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "episodeId"
    )
    val download: Download?
)

