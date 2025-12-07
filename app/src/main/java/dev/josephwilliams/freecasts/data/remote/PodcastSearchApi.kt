package dev.josephwilliams.freecasts.data.remote

import dev.josephwilliams.freecasts.data.remote.model.ItunesSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class PodcastSearchApi {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
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