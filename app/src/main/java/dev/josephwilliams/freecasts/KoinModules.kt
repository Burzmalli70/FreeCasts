package dev.josephwilliams.freecasts

import dev.josephwilliams.freecasts.data.download.EpisodeDownloadManager
import dev.josephwilliams.freecasts.data.export.EpisodeStateImportSupport
import dev.josephwilliams.freecasts.data.export.FreeCastsBackupBuilder
import dev.josephwilliams.freecasts.data.export.FreeCastsBackupImportHandler
import dev.josephwilliams.freecasts.data.export.PodcastSubscriptionsFileManager
import dev.josephwilliams.freecasts.data.playback.PlaybackEpisodeCompletionHandler
import dev.josephwilliams.freecasts.data.playback.PlaybackManager
import dev.josephwilliams.freecasts.data.playlist.PlaylistAutoAddHandler
import dev.josephwilliams.freecasts.data.playlist.PlaylistAutoRemoveHandler
import dev.josephwilliams.freecasts.data.playlist.PodcastEpisodeSyncHandler
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import dev.josephwilliams.freecasts.data.remote.PodcastSearchApi
import dev.josephwilliams.freecasts.data.repository.PodcastRepository
import dev.josephwilliams.freecasts.ui.screens.playlists.CreateEditPlaylistViewModel
import dev.josephwilliams.freecasts.ui.screens.playlists.PlaylistDetailViewModel
import dev.josephwilliams.freecasts.ui.screens.playlists.PlaylistsViewModel
import dev.josephwilliams.freecasts.ui.screens.podcasts.PodcastsViewModel
import dev.josephwilliams.freecasts.ui.screens.podcasts.SubscribedPodcastDetailViewModel
import dev.josephwilliams.freecasts.ui.screens.search.SearchPodcastDetailViewModel
import dev.josephwilliams.freecasts.ui.screens.search.SearchViewModel
import dev.josephwilliams.freecasts.ui.screens.settings.SettingsViewModel
import dev.josephwilliams.freecasts.work.PodcastSyncWorker
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.worker
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
    single { PlaylistAutoAddHandler(get(), get()) }
    single { PlaylistAutoRemoveHandler(get(), get()) }
    single { EpisodeStateImportSupport(get(), get(), get()) }
    single {
        PodcastEpisodeSyncHandler(
            episodeDao = get(),
            podcastDao = get(),
            playlistAutoAddHandler = get(),
            episodeStateImportSupport = get()
        )
    }
    single { PodcastRepository(get(), get(), get(), get(), get()) }
    single { FreeCastsBackupBuilder(get(), get()) }
    single { FreeCastsBackupImportHandler(get(), get(), get(), get()) }
    single { UserPreferencesRepository(androidContext()) }
    single { PodcastSubscriptionsFileManager(androidContext()) }
}

val viewModelModule = module {
    viewModel { SearchViewModel(get(), get(), get(), get()) }
    viewModel { SearchPodcastDetailViewModel(get()) }
    viewModel { PodcastsViewModel(get()) }
    viewModel { SubscribedPodcastDetailViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PlaylistsViewModel(get()) }
    viewModel { CreateEditPlaylistViewModel(get(), get(), get()) }
    viewModel { PlaylistDetailViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
}

val downloadModule = module {
    single { EpisodeDownloadManager(androidContext(), get()) }
}

val playbackModule = module {
    single { PlaybackManager(androidContext(), get()) }
    single { PlaybackEpisodeCompletionHandler(get(), get(), get(), get()) }
}

val searchApi = module {
    single {
        PodcastSearchApi()
    }
}

val workModule = module {
    worker { PodcastSyncWorker(get(), get(), get(), get(), get()) }
}

val appModules = listOf(
    databaseModule,
    repositoryModule,
    viewModelModule,
    downloadModule,
    playbackModule,
    searchApi,
    workModule
)