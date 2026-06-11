package dev.josephwilliams.freecasts.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.josephwilliams.freecasts.data.download.NoOpFavoriteEpisodeDownloadHandler
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
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
class FreeCastsBackupTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var backupBuilder: FreeCastsBackupBuilder
    private lateinit var episodeStateImportSupport: EpisodeStateImportSupport
    private var podcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        backupBuilder = FreeCastsBackupBuilder(
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao()
        )
        episodeStateImportSupport = EpisodeStateImportSupport(
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            userPreferencesRepository = UserPreferencesRepository(context),
            favoriteEpisodeDownloadHandler = NoOpFavoriteEpisodeDownloadHandler
        )

        podcastId = database.podcastDao().insert(
            Podcast(
                feedUrl = "https://example.com/feed.xml",
                title = "Test Podcast",
                isSubscribed = true
            )
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertEpisode(
        guid: String,
        isFavorite: Boolean = false,
        isPlayed: Boolean = false,
        playbackPositionMs: Long = 0
    ): Episode {
        val id = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = "Episode $guid",
                audioUrl = "https://example.com/$guid.mp3",
                isFavorite = isFavorite,
                isPlayed = isPlayed,
                playbackPositionMs = playbackPositionMs
            )
        )
        return database.episodeDao().getById(id)!!
    }

    @Test
    fun buildBackup_exportsSubscriptionsAndStatefulEpisodesOnly() = runTest {
        insertEpisode("played", isPlayed = true)
        insertEpisode("favorite", isFavorite = true)
        insertEpisode("in-progress", playbackPositionMs = 60_000)
        insertEpisode("untouched")

        val backup = backupBuilder.buildBackup()

        assertEquals(1, backup.podcasts.size)
        assertEquals("https://example.com/feed.xml", backup.podcasts.first().feedUrl)
        assertEquals(3, backup.episodeStates.size)
        assertEquals(BACKUP_VERSION, backup.version)
    }

    @Test
    fun applyEpisodeState_restoresFavoritePlayedAndPlaybackPosition() = runTest {
        val episode = insertEpisode("ep-1")

        val result = episodeStateImportSupport.applyEpisodeState(
            ExportedEpisodeState(
                feedUrl = "https://example.com/feed.xml",
                guid = "ep-1",
                isFavorite = true,
                favoritedAt = 1000L,
                isPlayed = false,
                playbackPositionMs = 125_000,
                lastPlayedAt = 2000L,
                listenCount = 2,
                replayPriority = 3
            )
        )

        assertEquals(EpisodeStateApplyResult.APPLIED, result)
        val updated = database.episodeDao().getById(episode.id)!!
        assertTrue(updated.isFavorite)
        assertEquals(125_000L, updated.playbackPositionMs)
        assertEquals(2, updated.listenCount)
    }

    @Test
    fun mergeEpisodeState_playedWinsAndClearsPlaybackPosition() {
        val local = Episode(
            podcastId = 1,
            guid = "ep-1",
            title = "Episode",
            audioUrl = "https://example.com/ep.mp3",
            playbackPositionMs = 30_000
        )
        val imported = ExportedEpisodeState(
            feedUrl = "https://example.com/feed.xml",
            guid = "ep-1",
            isPlayed = true
        )

        val merged = mergeEpisodeState(local, imported)

        assertTrue(merged.isPlayed)
        assertEquals(0L, merged.playbackPositionMs)
    }

    @Test
    fun applyEpisodeState_pendingWhenEpisodeNotYetSynced() = runTest {
        val result = episodeStateImportSupport.applyEpisodeState(
            ExportedEpisodeState(
                feedUrl = "https://example.com/feed.xml",
                guid = "missing-episode",
                isFavorite = true
            )
        )

        assertEquals(EpisodeStateApplyResult.PENDING, result)
    }

    @Test
    fun applyPendingStatesForPodcast_appliesAfterEpisodeAppears() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = UserPreferencesRepository(context)
        preferences.setPendingEpisodeStates(
            listOf(
                ExportedEpisodeState(
                    feedUrl = "https://example.com/feed.xml",
                    guid = "new-ep",
                    isPlayed = true
                )
            )
        )

        val support = EpisodeStateImportSupport(
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            userPreferencesRepository = preferences,
            favoriteEpisodeDownloadHandler = NoOpFavoriteEpisodeDownloadHandler
        )

        insertEpisode("new-ep")
        support.applyPendingStatesForPodcast(
            feedUrl = "https://example.com/feed.xml",
            guids = listOf("new-ep")
        )

        val episode = database.episodeDao().getByGuid("new-ep")!!
        assertTrue(episode.isPlayed)
        assertTrue(preferences.getPendingEpisodeStates().isEmpty())
    }
}
