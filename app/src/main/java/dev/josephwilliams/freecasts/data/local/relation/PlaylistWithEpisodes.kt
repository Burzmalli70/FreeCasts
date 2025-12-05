package dev.josephwilliams.freecasts.data.local.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef

/**
 * Represents a playlist with all its episodes.
 */
data class PlaylistWithEpisodes(
    @Embedded
    val playlist: Playlist,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistEpisodeCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "episodeId"
        )
    )
    val episodes: List<Episode>
)

