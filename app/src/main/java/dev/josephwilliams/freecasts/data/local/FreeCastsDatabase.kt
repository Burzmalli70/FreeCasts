package dev.josephwilliams.freecasts.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.josephwilliams.freecasts.data.local.converter.Converters
import dev.josephwilliams.freecasts.data.local.dao.DownloadDao
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PlaylistDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Download
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.local.entity.Podcast

@Database(
    entities = [
        Podcast::class,
        Episode::class,
        Playlist::class,
        PlaylistEpisodeCrossRef::class,
        Download::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FreeCastsDatabase : RoomDatabase() {
    
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    
    companion object {
        private const val DATABASE_NAME = "freecasts.db"
        
        @Volatile
        private var INSTANCE: FreeCastsDatabase? = null
        
        fun getInstance(context: Context): FreeCastsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): FreeCastsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                FreeCastsDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}

