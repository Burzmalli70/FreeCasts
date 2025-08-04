package dev.josephwilliams.freecasts.network

import dev.josephwilliams.freecasts.repositories.ItunesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface iTunesAPI {
    @GET("/search?media=podcast")
    suspend fun searchITunes(
        @Query("term") terms: String,
        @Query("limit") pageSize: Int = 50
    ): Response<ItunesResponse>
}