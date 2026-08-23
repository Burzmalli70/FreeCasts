package com.lazysimulation.freecasts.data.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lazysimulation.freecasts.data.local.FreeCastsDatabase
import com.lazysimulation.freecasts.data.local.entity.Download
import com.lazysimulation.freecasts.data.local.entity.DownloadStatus
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoDownloadHandlerTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var recordingEnqueuer: RecordingEpisodeDownloadEnqueuer
    private lateinit var handler: AutoDownloadHandler
    private var podcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferencesRepository(context)
        recordingEnqueuer = RecordingEpisodeDownloadEnqueuer()
        handler = AutoDownloadHandler(
            userPreferencesRepository = preferences,
            episodeDao = database.episodeDao(),
            downloadDao = database.downloadDao(),
            episodeDownloadEnqueuer = recordingEnqueuer,
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
        publishedAt: Long,
        audioUrl: String = "https://example.com/$guid.mp3"
    ): Episode {
        val id = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = guid,
                audioUrl = audioUrl,
                publishedAt = publishedAt
            )
        )
        return database.episodeDao().getById(id)!!
    }

    @Test
    fun downloadEpisodeIfEnabled_enqueuesWhenSettingOn() = runTest {
        preferences.setAutoDownloadOnSubscribe(true)
        val episode = insertEpisode("ep-1", publishedAt = 1_000L)

        assertTrue(handler.downloadEpisodeIfEnabled(episode.id))
        assertEquals(listOf(episode.id), recordingEnqueuer.enqueuedEpisodeIds)
    }

    @Test
    fun downloadEpisodeIfEnabled_skipsWhenSettingOff() = runTest {
        preferences.setAutoDownloadOnSubscribe(false)
        val episode = insertEpisode("ep-1", publishedAt = 1_000L)

        assertFalse(handler.downloadEpisodeIfEnabled(episode.id))
        assertTrue(recordingEnqueuer.enqueuedEpisodeIds.isEmpty())
    }

    @Test
    fun downloadLatestEpisodeIfEnabled_usesMostRecentEpisode() = runTest {
        preferences.setAutoDownloadOnSubscribe(true)
        insertEpisode("older", publishedAt = 1_000L)
        val latest = insertEpisode("latest", publishedAt = 3_000L)

        assertTrue(handler.downloadLatestEpisodeIfEnabled(podcastId))
        assertEquals(listOf(latest.id), recordingEnqueuer.enqueuedEpisodeIds)
    }

    @Test
    fun downloadEpisodeIfEnabled_skipsAlreadyCompletedDownload() = runTest {
        preferences.setAutoDownloadOnSubscribe(true)
        val episode = insertEpisode("ep-1", publishedAt = 1_000L)
        database.downloadDao().insert(
            Download(episodeId = episode.id, status = DownloadStatus.COMPLETED)
        )

        assertFalse(handler.downloadEpisodeIfEnabled(episode.id))
        assertTrue(recordingEnqueuer.enqueuedEpisodeIds.isEmpty())
    }

    @Test
    fun downloadEpisodeIfEnabled_skipsBlankAudioUrl() = runTest {
        preferences.setAutoDownloadOnSubscribe(true)
        val episode = insertEpisode("ep-1", publishedAt = 1_000L, audioUrl = "  ")

        assertFalse(handler.downloadEpisodeIfEnabled(episode.id))
        assertTrue(recordingEnqueuer.enqueuedEpisodeIds.isEmpty())
    }
}
