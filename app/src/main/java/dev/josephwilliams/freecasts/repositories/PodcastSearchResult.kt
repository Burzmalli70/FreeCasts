package dev.josephwilliams.freecasts.repositories

import dev.josephwilliams.freecasts.model.entities.Podcast
import kotlinx.serialization.Serializable

@Serializable
data class ItunesResponse(
    val resultCount: Int,
    val results: List<Podcast>
)
