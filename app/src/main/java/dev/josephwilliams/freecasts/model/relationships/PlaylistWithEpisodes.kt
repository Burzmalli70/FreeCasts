package dev.josephwilliams.freecasts.model.relationships

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist

data class PlaylistWithEpisodes(
    @Embedded
    val playlist: Playlist,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistEpisode::class,
            parentColumn = "playlist_id",
            entityColumn = "episode_id"
        )
    )
    val episodes: List<Episode>
)