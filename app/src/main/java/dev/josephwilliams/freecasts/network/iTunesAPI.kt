package dev.josephwilliams.freecasts.network

import dev.josephwilliams.freecasts.repositories.ItunesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface iTunesAPI {
    @GET("/search?media=podcast&limit=25")
    suspend fun searchITunes(
        @Query("term") terms: String
    ): Response<ItunesResponse>
}