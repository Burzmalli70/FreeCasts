package com.lazysimulation.freecasts.data.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lazysimulation.freecasts.data.local.FreeCastsDatabase
import com.lazysimulation.freecasts.data.local.entity.Download
import com.lazysimulation.freecasts.data.local.entity.DownloadStatus
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Playlist
import com.lazysimulation.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayedEpisodeDownloadCleanupTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var cleanup: PlayedEpisodeDownloadCleanup
    private var podcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferencesRepository(context)
        cleanup = PlayedEpisodeDownloadCleanup(
            userPreferencesRepository = preferences,
            episodeDao = database.episodeDao(),
            playlistDao = database.playlistDao(),
            downloadDao = database.downloadDao(),
        )
        podcastId = database.podcastDao().insert(
            Podcast(feedUrl = "https://example.com/feed.xml", title = "Podcast")
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertEpisode(
        guid: String,
        isFavorite: Boolean = false
    ): Episode {
        val id = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = guid,
                audioUrl = "https://example.com/$guid.mp3",
                isFavorite = isFavorite,
                isPlayed = true
            )
        )
        return database.episodeDao().getById(id)!!
    }

    private suspend fun insertCompletedDownload(episodeId: Long) {
        database.downloadDao().insert(
            Download(episodeId = episodeId, status = DownloadStatus.COMPLETED)
        )
    }

    private suspend fun addToPlaylist(episodeId: Long): Long {
        val playlistId = database.playlistDao().insert(Playlist(name = "Queue"))
        database.playlistDao().insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(playlistId = playlistId, episodeId = episodeId, position = 0)
        )
        return playlistId
    }

    @Test
    fun doesNotDeleteWhenDeletePlayedDownloadsDisabled() = runTest {
        preferences.setDeletePlayedDownloads(false)
        val episode = insertEpisode("ep-1")
        insertCompletedDownload(episode.id)

        assertFalse(cleanup.cleanupIfNeeded(episode.id))
        assertNotNull(database.downloadDao().getByEpisodeId(episode.id))
    }

    @Test
    fun deletesDownloadWhenPlayedAndNotOnPlaylist() = runTest {
        preferences.setDeletePlayedDownloads(true)
        preferences.setKeepFavoriteDownloads(false)
        val episode = insertEpisode("ep-1")
        insertCompletedDownload(episode.id)

        assertTrue(cleanup.cleanupIfNeeded(episode.id))
        assertNull(database.downloadDao().getByEpisodeId(episode.id))
    }

    @Test
    fun keepsDownloadWhenEpisodeIsStillOnAPlaylist() = runTest {
        preferences.setDeletePlayedDownloads(true)
        val episode = insertEpisode("ep-1")
        insertCompletedDownload(episode.id)
        addToPlaylist(episode.id)

        assertFalse(cleanup.cleanupIfNeeded(episode.id))
        assertNotNull(database.downloadDao().getByEpisodeId(episode.id))
    }

    @Test
    fun keepsFavoriteDownloadWhenKeepFavoriteDownloadsEnabled() = runTest {
        preferences.setDeletePlayedDownloads(true)
        preferences.setKeepFavoriteDownloads(true)
        val episode = insertEpisode("ep-1", isFavorite = true)
        insertCompletedDownload(episode.id)

        assertFalse(cleanup.cleanupIfNeeded(episode.id))
        assertNotNull(database.downloadDao().getByEpisodeId(episode.id))
    }

    @Test
    fun deletesNonFavoriteWhenKeepFavoriteDownloadsEnabled() = runTest {
        preferences.setDeletePlayedDownloads(true)
        preferences.setKeepFavoriteDownloads(true)
        val episode = insertEpisode("ep-1", isFavorite = false)
        insertCompletedDownload(episode.id)

        assertTrue(cleanup.cleanupIfNeeded(episode.id))
        assertNull(database.downloadDao().getByEpisodeId(episode.id))
    }

    @Test
    fun keepsFavoriteEvenWhenAlsoOnPlaylistCheckWouldPass() = runTest {
        preferences.setDeletePlayedDownloads(true)
        preferences.setKeepFavoriteDownloads(true)
        val episode = insertEpisode("ep-1", isFavorite = true)
        insertCompletedDownload(episode.id)
        // Not on a playlist — favorite protection alone should keep it
        assertFalse(cleanup.cleanupIfNeeded(episode.id))
        assertNotNull(database.downloadDao().getByEpisodeId(episode.id))
    }

    @Test
    fun keepsDownloadWhenOnPlaylistEvenIfNotFavorite() = runTest {
        preferences.setDeletePlayedDownloads(true)
        preferences.setKeepFavoriteDownloads(true)
        val episode = insertEpisode("ep-1", isFavorite = false)
        insertCompletedDownload(episode.id)
        addToPlaylist(episode.id)

        assertFalse(cleanup.cleanupIfNeeded(episode.id))
        assertEquals(episode.id, database.downloadDao().getByEpisodeId(episode.id)?.episodeId)
    }
}
