package dev.josephwilliams.freecasts

import dev.josephwilliams.freecasts.data.download.EpisodeDownloadManager
import dev.josephwilliams.freecasts.data.playback.PlaybackManager
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.remote.PodcastSearchApi
import dev.josephwilliams.freecasts.data.repository.PodcastRepository
import dev.josephwilliams.freecasts.ui.screens.playlists.CreateEditPlaylistViewModel
import dev.josephwilliams.freecasts.ui.screens.playlists.PlaylistDetailViewModel
import dev.josephwilliams.freecasts.ui.screens.playlists.PlaylistsViewModel
import dev.josephwilliams.freecasts.ui.screens.podcasts.PodcastsViewModel
import dev.josephwilliams.freecasts.ui.screens.podcasts.SubscribedPodcastDetailViewModel
import dev.josephwilliams.freecasts.ui.screens.search.SearchPodcastDetailViewModel
import dev.josephwilliams.freecasts.ui.screens.search.SearchViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    single { FreeCastsDatabase.getInstance(androidApplication()) }
    single { get<FreeCastsDatabase>().podcastDao() }
    single { get<FreeCastsDatabase>().episodeDao() }
    single { get<FreeCastsDatabase>().playlistDao() }
    single { get<FreeCastsDatabase>().downloadDao() }
}

val repositoryModule = module {
    single { PodcastRepository(get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { SearchViewModel(get()) }
    viewModel { SearchPodcastDetailViewModel(get()) }
    viewModel { PodcastsViewModel(get()) }
    viewModel { SubscribedPodcastDetailViewModel(get(), get(), get(), get(), get()) }
    viewModel { PlaylistsViewModel(get()) }
    viewModel { CreateEditPlaylistViewModel(get()) }
    viewModel { PlaylistDetailViewModel(get(), get()) }
}

val downloadModule = module {
    single { EpisodeDownloadManager(androidContext(), get()) }
}

val playbackModule = module {
    single { PlaybackManager(androidContext(), get()) }
}

val searchApi = module {
    single {
        PodcastSearchApi()
    }
}

val appModules = listOf(
    databaseModule,
    repositoryModule,
    viewModelModule,
    downloadModule,
    playbackModule,
    searchApi
)