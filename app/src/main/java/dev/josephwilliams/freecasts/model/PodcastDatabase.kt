package dev.josephwilliams.freecasts.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.josephwilliams.freecasts.model.daos.EpisodeDao
import dev.josephwilliams.freecasts.model.daos.PlaylistDao
import dev.josephwilliams.freecasts.model.daos.PodcastDao
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.model.relationships.PlaylistEpisode

@Database(
    entities = [
        Podcast::class,
        Episode::class,
        Playlist::class,
        PlaylistEpisode::class
    ],
    version = 4
)
abstract class PodcastDatabase : RoomDatabase() {

    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: PodcastDatabase? = null

        fun getDatabase(context: Context): PodcastDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PodcastDatabase::class.java,
                    "podcast_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}