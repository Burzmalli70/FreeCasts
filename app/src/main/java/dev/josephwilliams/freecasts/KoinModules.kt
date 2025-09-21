package dev.josephwilliams.freecasts

import dev.josephwilliams.freecasts.model.PodcastDatabase
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single { PodcastDatabase.getDatabase(androidApplication()) }
    single { get<PodcastDatabase>().podcastDao() }
    single { get<PodcastDatabase>().episodeDao() }
    single { get<PodcastDatabase>().playlistDao() }
    single { get<PodcastDatabase>().downloadDao() }
}