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
class FavoriteEpisodeDownloadCoordinatorTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var recordingEnqueuer: RecordingEpisodeDownloadEnqueuer
    private lateinit var coordinator: FavoriteEpisodeDownloadCoordinator
    private var podcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = UserPreferencesRepository(context)
        recordingEnqueuer = RecordingEpisodeDownloadEnqueuer()
        coordinator = FavoriteEpisodeDownloadCoordinator(
            episodeDao = database.episodeDao(),
            downloadDao = database.downloadDao(),
            episodeDownloadEnqueuer = recordingEnqueuer,
            userPreferencesRepository = preferences
        )

        podcastId = database.podcastDao().insert(
            Podcast(feedUrl = "https://example.com/feed.xml", title = "Test Podcast")
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertFavorite(guid: String): Long {
        return database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = "Favorite $guid",
                audioUrl = "https://example.com/$guid.mp3",
                isFavorite = true
            )
        )
    }

    @Test
    fun clearPendingIfAllFavoritesDownloaded_clearsFlagWhenEveryFavoriteIsComplete() = runTest {
        val episodeId = insertFavorite("fav-1")
        database.downloadDao().insert(
            Download(episodeId = episodeId, status = DownloadStatus.COMPLETED)
        )
        preferences.setFavoriteDownloadsAfterImportPending(true)

        coordinator.clearPendingIfAllFavoritesDownloaded()

        assertFalse(preferences.isFavoriteDownloadsAfterImportPending())
    }

    @Test
    fun clearPendingIfAllFavoritesDownloaded_keepsFlagWhileDownloadsRemain() = runTest {
        insertFavorite("fav-1")
        preferences.setFavoriteDownloadsAfterImportPending(true)

        coordinator.clearPendingIfAllFavoritesDownloaded()

        assertTrue(preferences.isFavoriteDownloadsAfterImportPending())
    }

    @Test
    fun enqueueDownloadsForAllFavorites_skipsAlreadyQueuedOrCompleted() = runTest {
        val completedId = insertFavorite("done")
        val queuedId = insertFavorite("queued")
        database.downloadDao().insert(
            Download(episodeId = completedId, status = DownloadStatus.COMPLETED)
        )
        database.downloadDao().insert(
            Download(episodeId = queuedId, status = DownloadStatus.PENDING)
        )
        insertFavorite("new")

        val enqueued = coordinator.enqueueDownloadsForAllFavorites()

        assertEquals(1, enqueued)
        assertEquals(1, recordingEnqueuer.enqueuedEpisodeIds.size)
    }
}
