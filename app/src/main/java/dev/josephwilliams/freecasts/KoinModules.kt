package dev.josephwilliams.freecasts

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.josephwilliams.freecasts.downloader.SystemDownloader
import dev.josephwilliams.freecasts.model.PodcastDatabase
import dev.josephwilliams.freecasts.network.iTunesAPI
import dev.josephwilliams.freecasts.repositories.PodcastRepository
import dev.josephwilliams.freecasts.ui.screens.search.SearchViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

val databaseModule = module {
    single { PodcastDatabase.getDatabase(androidApplication()) }
    single { get<PodcastDatabase>().podcastDao() }
    single { get<PodcastDatabase>().episodeDao() }
    single { get<PodcastDatabase>().playlistDao() }
}

val downloaderModule = module {
    single { SystemDownloader(androidApplication()) }
}

val itunesModule = module {
    single {
        setupItunesApi()
    }
}

val viewModelModule = module {
    viewModel { PodcastViewModel(get(), get()) }
    viewModel { SearchViewModel(get()) }
}

val repositoryModule = module {
    single { PodcastRepository(get(), get(), get(), get()) }
}

val appModules = listOf(
    databaseModule,
    downloaderModule,
    viewModelModule,
    itunesModule,
    repositoryModule
)

fun setupItunesApi(): iTunesAPI {
    val converter = Json { ignoreUnknownKeys = true }
    val retrofit = Retrofit.Builder()
        .baseUrl(ITUNES_URL)
        .addConverterFactory(converter.asConverterFactory("application/json".toMediaType()))
        .build()
    return retrofit.create(iTunesAPI::class.java)
}

const val ITUNES_URL = "https://itunes.apple.com"