package dev.josephwilliams.freecasts

import dev.joewilliams.downloadmanager.DownloadManager
import dev.josephwilliams.freecasts.model.PodcastDatabase
import dev.josephwilliams.freecasts.repositories.DownloadRepository
import dev.josephwilliams.freecasts.repositories.PodcastRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    single { PodcastDatabase.getDatabase(androidApplication()) }
    single { get<PodcastDatabase>().podcastDao() }
    single { get<PodcastDatabase>().episodeDao() }
    single { get<PodcastDatabase>().playlistDao() }
    single { get<PodcastDatabase>().downloadDao() }
}

val repositoryModule = module {
    single { PodcastRepository(get(), get(), get()) }
    single { DownloadRepository(get()) }
}

val viewModelModule = module {
    viewModel { PodcastViewModel(get()) }
}

val downloadModule = module {
    single { DownloadManager(get(), get()) }
}

val appModules = listOf(databaseModule, repositoryModule, viewModelModule)