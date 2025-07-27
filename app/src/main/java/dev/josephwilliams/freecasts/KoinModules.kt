package dev.josephwilliams.freecasts

import dev.josephwilliams.freecasts.downloader.SystemDownloader
import dev.josephwilliams.freecasts.model.PodcastDatabase
import dev.josephwilliams.freecasts.repositories.PodcastRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    single { PodcastDatabase.getDatabase(androidApplication()) }
    single { get<PodcastDatabase>().podcastDao() }
    single { get<PodcastDatabase>().episodeDao() }
    single { get<PodcastDatabase>().playlistDao() }
}

val repositoryModule = module {
    single { PodcastRepository(get(), get(), get()) }
}

val downloaderModule = module {
    single { SystemDownloader(androidApplication()) }
}

val viewModelModule = module {
    viewModel { PodcastViewModel(get(), get()) }
}

val appModules = listOf(
    databaseModule,
    repositoryModule,
    downloaderModule,
    viewModelModule
)