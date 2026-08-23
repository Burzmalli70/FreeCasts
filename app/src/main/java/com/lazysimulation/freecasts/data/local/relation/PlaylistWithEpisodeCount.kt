package com.lazysimulation.freecasts.data.local.relation

import androidx.room.Embedded
import com.lazysimulation.freecasts.data.local.entity.Playlist

/**
 * Represents a playlist with the number of episodes it contains.
 */
data class PlaylistWithEpisodeCount(
    @Embedded
    val playlist: Playlist,
    val episodeCount: Int,
)
