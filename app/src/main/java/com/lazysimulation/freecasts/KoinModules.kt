package com.lazysimulation.freecasts

import com.lazysimulation.freecasts.data.download.AutoDownloadHandler
import com.lazysimulation.freecasts.data.download.EpisodeDownloadManager
import com.lazysimulation.freecasts.data.download.FavoriteEpisodeDownloadCoordinator
import com.lazysimulation.freecasts.data.download.FavoriteEpisodeDownloadHandler
import com.lazysimulation.freecasts.data.download.PlayedEpisodeDownloadCleanup
import com.lazysimulation.freecasts.data.export.EpisodeStateImportSupport
import com.lazysimulation.freecasts.data.export.FreeCastsBackupBuilder
import com.lazysimulation.freecasts.data.export.FreeCastsBackupImportHandler
import com.lazysimulation.freecasts.data.export.PlaylistImportSupport
import com.lazysimulation.freecasts.data.export.PodcastSubscriptionsFileManager
import com.lazysimulation.freecasts.data.playback.PlaybackEpisodeCompletionHandler
import com.lazysimulation.freecasts.data.playback.PlaybackManager
import com.lazysimulation.freecasts.data.playback.auto.AutoMediaBrowser
import com.lazysimulation.freecasts.data.playback.auto.PackageValidator
import com.lazysimulation.freecasts.R
import com.lazysimulation.freecasts.data.playlist.PlaylistAutoAddHandler
import com.lazysimulation.freecasts.data.playlist.PlaylistAutoRemoveHandler
import com.lazysimulation.freecasts.data.playlist.PodcastEpisodeSyncHandler
import com.lazysimulation.freecasts.data.local.FreeCastsDatabase
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import com.lazysimulation.freecasts.data.remote.PodcastSearchApi
import com.lazysimulation.freecasts.data.repository.PodcastRepository
import com.lazysimulation.freecasts.ui.screens.playlists.CreateEditPlaylistViewModel
import com.lazysimulation.freecasts.ui.screens.playlists.PlaylistDetailViewModel
import com.lazysimulation.freecasts.ui.screens.playlists.PlaylistsViewModel
import com.lazysimulation.freecasts.ui.screens.podcasts.PodcastsViewModel
import com.lazysimulation.freecasts.ui.screens.podcasts.SubscribedPodcastDetailViewModel
import com.lazysimulation.freecasts.ui.screens.search.SearchPodcastDetailViewModel
import com.lazysimulation.freecasts.ui.screens.search.SearchViewModel
import com.lazysimulation.freecasts.ui.screens.settings.SettingsViewModel
import com.lazysimulation.freecasts.work.PodcastSyncWorker
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
    single { PlaylistAutoRemoveHandler(get(), get()) }
    single { EpisodeStateImportSupport(get(), get(), get(), get()) }
    single { PlaylistImportSupport(get(), get(), get(), get()) }
    single {
        PodcastEpisodeSyncHandler(
            episodeDao = get(),
            podcastDao = get(),
            playlistAutoAddHandler = get(),
            episodeStateImportSupport = get(),
            playlistImportSupport = get(),
        )
    }
    single { PodcastRepository(get(), get(), get(), get(), get()) }
    single { FreeCastsBackupBuilder(get(), get(), get(), get()) }
    single { FreeCastsBackupImportHandler(get(), get(), get(), get(), get(), get()) }
    single { UserPreferencesRepository(androidContext()) }
    single { PodcastSubscriptionsFileManager(androidContext()) }
}

val viewModelModule = module {
    viewModel { SearchViewModel(get(), get()) }
    viewModel { SearchPodcastDetailViewModel(get(), get()) }
    viewModel { PodcastsViewModel(get()) }
    viewModel { SubscribedPodcastDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PlaylistsViewModel(get()) }
    viewModel { CreateEditPlaylistViewModel(get(), get(), get()) }
    viewModel { PlaylistDetailViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
}

val downloadModule = module {
    single {
        EpisodeDownloadManager(
            context = androidContext(),
            downloadDao = get(),
            episodeDao = get(),
            resumeFavoriteDownloads = {
                org.koin.java.KoinJavaComponent.getKoin()
                    .get<FavoriteEpisodeDownloadHandler>()
                    .resumeFavoriteDownloadsIfNeeded()
            },
            onFavoriteDownloadsMayBeComplete = {
                org.koin.java.KoinJavaComponent.getKoin()
                    .get<FavoriteEpisodeDownloadHandler>()
                    .clearPendingIfAllFavoritesDownloaded()
            }
        )
    }
    single<FavoriteEpisodeDownloadHandler> {
        FavoriteEpisodeDownloadCoordinator(
            episodeDao = get(),
            downloadDao = get(),
            episodeDownloadEnqueuer = get<EpisodeDownloadManager>(),
            userPreferencesRepository = get()
        )
    }
    single {
        AutoDownloadHandler(
            userPreferencesRepository = get(),
            episodeDao = get(),
            downloadDao = get(),
            episodeDownloadEnqueuer = get<EpisodeDownloadManager>(),
        )
    }
    single {
        PlayedEpisodeDownloadCleanup(
            userPreferencesRepository = get(),
            episodeDao = get(),
            playlistDao = get(),
            downloadDao = get(),
        )
    }
    // Depends on AutoDownloadHandler from this module
    single { PlaylistAutoAddHandler(get(), get(), get()) }
}

val playbackModule = module {
    single { PlaybackManager(androidContext(), get()) }
    single { PlaybackEpisodeCompletionHandler(get(), get(), get()) }
    single { PackageValidator(androidContext(), R.xml.allowed_media_browser_callers) }
    single { AutoMediaBrowser(androidContext(), get(), get(), get(), get()) }
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