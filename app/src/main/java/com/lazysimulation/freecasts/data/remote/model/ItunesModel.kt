package com.lazysimulation.freecasts.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItunesSearchResponse(
    val resultCount: Int,
    val results: List<ItunesPodcast>
)

@Serializable
data class ItunesPodcast(
    val collectionId: Long,
    val collectionName: String,
    val artistName: String? = null,
    val artworkUrl100: String? = null,
    val artworkUrl600: String? = null,
    val feedUrl: String? = null,
    val trackCount: Int? = null,
    @SerialName("primaryGenreName")
    val genre: String? = null,
    val collectionViewUrl: String? = null
)