package dev.josephwilliams.freecasts.data.playlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.local.entity.Podcast
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
        syncHandler = PodcastEpisodeSyncHandler(
            episodeDao = database.episodeDao(),
            podcastDao = database.podcastDao(),
            playlistAutoAddHandler = PlaylistAutoAddHandler(playlistDao)
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
        isPlayed: Boolean = false
    ): Episode {
        val id = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = title,
                audioUrl = "https://example.com/$guid.mp3",
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

    private fun feedEpisode(guid: String, title: String) = Episode(
        guid = guid,
        title = title,
        audioUrl = "https://example.com/$guid.mp3"
    )

    @Test
    fun preservesExistingPlaylistEpisodesWhenNoNewEpisodesFoundDuringSync() = runTest {
        val playlistId = createPlaylist("Manual Mix")
        val firstEpisode = insertEpisode(podcastId, "ep-1", "Episode One")
        val secondEpisode = insertEpisode(podcastId, "ep-2", "Episode Two")
        addEpisodeToPlaylist(playlistId, firstEpisode.id, position = 0)
        addEpisodeToPlaylist(playlistId, secondEpisode.id, position = 1)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("ep-1", "Episode One"),
                feedEpisode("ep-2", "Episode Two")
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
        val existingEpisode = insertEpisode(podcastId, "ep-1", "Episode One")
        addEpisodeToPlaylist(playlistId, existingEpisode.id, position = 0)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("ep-1", "Episode One"),
                feedEpisode("ep-2", "Episode Two")
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
        val existingEpisode = insertEpisode(podcastId, "ep-1", "Episode One")
        addEpisodeToPlaylist(playlistId, existingEpisode.id, position = 0)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(feedEpisode("ep-1", "Episode One"))
        )

        assertEquals(listOf(existingEpisode.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun preservesEpisodesFromOtherPodcastsWhenOnePodcastSyncsWithNewEpisodes() = runTest {
        val playlistId = createPlaylist(
            name = "Mixed",
            autoAddPodcastIds = podcastId.toString()
        )
        val syncedPodcastEpisode = insertEpisode(podcastId, "ep-1", "Synced Episode")
        val otherPodcastEpisode = insertEpisode(otherPodcastId, "other-1", "Other Episode")
        addEpisodeToPlaylist(playlistId, syncedPodcastEpisode.id, position = 0)
        addEpisodeToPlaylist(playlistId, otherPodcastEpisode.id, position = 1)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("ep-1", "Synced Episode"),
                feedEpisode("ep-2", "New Synced Episode")
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
            isPlayed = true
        )
        addEpisodeToPlaylist(playlistId, playedEpisode.id, position = 0)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                feedEpisode("played-ep", "Played Episode"),
                feedEpisode("new-ep", "New Episode")
            )
        )

        val episodeIds = playlistEpisodeIds(playlistId)
        assertTrue(episodeIds.contains(playedEpisode.id))
        assertEquals(2, episodeIds.size)
    }
}
