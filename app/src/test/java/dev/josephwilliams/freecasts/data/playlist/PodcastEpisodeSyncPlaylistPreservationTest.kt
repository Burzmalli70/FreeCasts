package dev.josephwilliams.freecasts.data.playlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.download.NoOpFavoriteEpisodeDownloadHandler
import dev.josephwilliams.freecasts.data.export.EpisodeStateImportSupport
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PodcastEpisodeSyncPlaylistPreservationTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var syncHandler: PodcastEpisodeSyncHandler
    private var podcastId: Long = 0
    private var otherPodcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val playlistDao = database.playlistDao()
        val episodeStateImportSupport = EpisodeStateImportSupport(
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            userPreferencesRepository = UserPreferencesRepository(context),
            favoriteEpisodeDownloadHandler = NoOpFavoriteEpisodeDownloadHandler
        )
        syncHandler = PodcastEpisodeSyncHandler(
            episodeDao = database.episodeDao(),
            podcastDao = database.podcastDao(),
            playlistAutoAddHandler = PlaylistAutoAddHandler(playlistDao, database.episodeDao()),
            episodeStateImportSupport = episodeStateImportSupport
        )

        podcastId = database.podcastDao().insert(
            Podcast(feedUrl = "https://example.com/feed.xml", title = "Synced Podcast")
        )
        otherPodcastId = database.podcastDao().insert(
            Podcast(feedUrl = "https://example.com/other-feed.xml", title = "Other Podcast")
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertEpisode(
        podcastId: Long,
        guid: String,
        title: String,
        publishedAt: Long,
        isPlayed: Boolean = false
    ): Episode {
        val id = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = title,
                audioUrl = "https://example.com/$guid.mp3",
                publishedAt = publishedAt,
                isPlayed = isPlayed
            )
        )
        return database.episodeDao().getById(id)!!
    }

    private suspend fun createPlaylist(
        name: String,
        autoAddPodcastIds: String? = null
    ): Long {
        return database.playlistDao().insert(
            Playlist(name = name, autoAddPodcastIds = autoAddPodcastIds)
        )
    }

    private suspend fun addEpisodeToPlaylist(playlistId: Long, episodeId: Long, position: Int) {
        database.playlistDao().insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(
                playlistId = playlistId,
                episodeId = episodeId,
                position = position
            )
        )
    }

    private suspend fun playlistEpisodeIds(playlistId: Long): List<Long> {
        return database.playlistDao()
            .getPlaylistWithEpisodes(playlistId)
            ?.episodes
            ?.map { it.id }
            ?: emptyList()
    }

    private fun feedEpisode(guid: String, title: String, publishedAt: Long) = Episode(
        guid = guid,
        title = title,
        audioUrl = "https://example.com/$guid.mp3",
        publishedAt = publishedAt
    )

    @Test
    fun preservesExistingPlaylistEpisodesWhenNoNewEpisodesFoundDuringSync() = runTest {
        val playlistId = createPlaylist("Manual Mix")
        val firstEpisode = insertEpisode(podcastId, "ep-1", "Episode One", publishedAt = 1_000L)
        val secondEpisode = insertEpisode(podcastId, "ep-2", "Episode Two", publishedAt = 2_000L)
        addEpisodeToPlaylist(playlistId, firstEpisode.id, position = 0)
        addEpisodeToPlaylist(playlistId, secondEpisode.id, position = 1)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("ep-1", "Episode One", publishedAt = 1_000L),
                feedEpisode("ep-2", "Episode Two", publishedAt = 2_000L)
            )
        )

        assertEquals(
            listOf(firstEpisode.id, secondEpisode.id),
            playlistEpisodeIds(playlistId)
        )
        assertEquals(2, database.playlistDao().getEpisodeCount(playlistId))
    }

    @Test
    fun preservesExistingPlaylistEpisodesWhenNewEpisodesFoundDuringSync() = runTest {
        val playlistId = createPlaylist(
            name = "Auto Add",
            autoAddPodcastIds = podcastId.toString()
        )
        val existingEpisode = insertEpisode(podcastId, "ep-1", "Episode One", publishedAt = 1_000L)
        addEpisodeToPlaylist(playlistId, existingEpisode.id, position = 0)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("ep-1", "Episode One", publishedAt = 1_000L),
                feedEpisode("ep-2", "Episode Two", publishedAt = 2_000L)
            )
        )

        val episodeIds = playlistEpisodeIds(playlistId)
        assertEquals(2, episodeIds.size)
        assertTrue(episodeIds.contains(existingEpisode.id))
        assertTrue(database.playlistDao().isEpisodeInPlaylist(playlistId, existingEpisode.id))
    }

    @Test
    fun preservesManuallyAddedEpisodesWhenSyncFindsNoNewEpisodesAndAutoAddIsDisabled() = runTest {
        val playlistId = createPlaylist(name = "Manual Only", autoAddPodcastIds = null)
        val existingEpisode = insertEpisode(podcastId, "ep-1", "Episode One", publishedAt = 1_000L)
        addEpisodeToPlaylist(playlistId, existingEpisode.id, position = 0)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(feedEpisode("ep-1", "Episode One", publishedAt = 1_000L))
        )

        assertEquals(listOf(existingEpisode.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun preservesEpisodesFromOtherPodcastsWhenOnePodcastSyncsWithNewEpisodes() = runTest {
        val playlistId = createPlaylist(
            name = "Mixed",
            autoAddPodcastIds = podcastId.toString()
        )
        val syncedPodcastEpisode = insertEpisode(
            podcastId, "ep-1", "Synced Episode", publishedAt = 1_000L
        )
        val otherPodcastEpisode = insertEpisode(
            otherPodcastId, "other-1", "Other Episode", publishedAt = 1_000L
        )
        addEpisodeToPlaylist(playlistId, syncedPodcastEpisode.id, position = 0)
        addEpisodeToPlaylist(playlistId, otherPodcastEpisode.id, position = 1)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("ep-1", "Synced Episode", publishedAt = 1_000L),
                feedEpisode("ep-2", "New Synced Episode", publishedAt = 2_000L)
            )
        )

        val episodeIds = playlistEpisodeIds(playlistId)
        assertEquals(3, episodeIds.size)
        assertTrue(episodeIds.contains(syncedPodcastEpisode.id))
        assertTrue(episodeIds.contains(otherPodcastEpisode.id))
        assertTrue(database.playlistDao().isEpisodeInPlaylist(playlistId, otherPodcastEpisode.id))
    }

    @Test
    fun preservesPlayedEpisodesInPlaylistWhenNewEpisodesAreSynced() = runTest {
        val playlistId = createPlaylist(
            name = "Played Keepers",
            autoAddPodcastIds = podcastId.toString()
        )
        val playedEpisode = insertEpisode(
            podcastId = podcastId,
            guid = "played-ep",
            title = "Played Episode",
            publishedAt = 1_000L,
            isPlayed = true
        )
        addEpisodeToPlaylist(playlistId, playedEpisode.id, position = 0)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("played-ep", "Played Episode", publishedAt = 1_000L),
                feedEpisode("new-ep", "New Episode", publishedAt = 2_000L)
            )
        )

        val episodeIds = playlistEpisodeIds(playlistId)
        assertTrue(episodeIds.contains(playedEpisode.id))
        assertEquals(2, episodeIds.size)
    }
}
