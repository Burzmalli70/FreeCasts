package com.lazysimulation.freecasts.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lazysimulation.freecasts.data.local.converter.Converters
import com.lazysimulation.freecasts.data.local.dao.DownloadDao
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.local.dao.PodcastDao
import com.lazysimulation.freecasts.data.local.entity.Download
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Playlist
import com.lazysimulation.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import com.lazysimulation.freecasts.data.local.entity.Podcast

@Database(
    entities = [
        Podcast::class,
        Episode::class,
        Playlist::class,
        PlaylistEpisodeCrossRef::class,
        Download::class
    ],
    version = 5,
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

