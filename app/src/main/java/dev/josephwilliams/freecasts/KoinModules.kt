package dev.josephwilliams.freecasts

import dev.josephwilliams.freecasts.data.download.EpisodeDownloadManager
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.remote.PodcastSearchApi
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single { FreeCastsDatabase.getInstance(androidApplication()) }
    single { get<FreeCastsDatabase>().podcastDao() }
    single { get<FreeCastsDatabase>().episodeDao() }
    single { get<FreeCastsDatabase>().playlistDao() }
    single { get<FreeCastsDatabase>().downloadDao() }
}

val downloadModule = module {
    single { EpisodeDownloadManager(androidContext(), get()) }
}

val searchApi = module {
    single {
        PodcastSearchApi()
    }
}

val appModules = listOf(
    databaseModule,
    downloadModule,
    searchApi
)