package com.lazysimulation.freecasts.data.remote

import com.lazysimulation.freecasts.data.remote.model.ItunesSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class PodcastSearchApi {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            // iTunes API returns text/javascript content type, so we need to accept it as JSON
            json(
                json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
                contentType = ContentType.Application.Json
            )
            json(
                json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
                contentType = ContentType.Text.JavaScript
            )
        }
    }

    suspend fun searchPodcasts(term: String, limit: Int = 25): ItunesSearchResponse {
        return client.get("https://itunes.apple.com/search") {
            parameter("media", "podcast")
            parameter("term", term)
            parameter("limit", limit)
        }.body()
    }

    fun close() {
        client.close()
    }
}