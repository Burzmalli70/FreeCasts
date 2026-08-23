package com.lazysimulation.freecasts.data.playlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lazysimulation.freecasts.data.download.AutoDownloadHandler
import com.lazysimulation.freecasts.data.download.NoOpEpisodeDownloadEnqueuer
import com.lazysimulation.freecasts.data.download.NoOpFavoriteEpisodeDownloadHandler
import com.lazysimulation.freecasts.data.export.EpisodeStateImportSupport
import com.lazysimulation.freecasts.data.export.ExportedPlaylist
import com.lazysimulation.freecasts.data.export.ExportedPlaylistEpisode
import com.lazysimulation.freecasts.data.export.PlaylistImportSupport
import com.lazysimulation.freecasts.data.local.FreeCastsDatabase
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Playlist
import com.lazysimulation.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards against playlist membership being wiped by sync, re-insert, or import.
 *
 * Episode/playlist cross-refs CASCADE when an episode row is deleted. Insert strategies
 * that REPLACE on GUID conflict delete the old row and therefore clear playlist links.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistMembershipPreservationTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var syncHandler: PodcastEpisodeSyncHandler
    private lateinit var playlistImportSupport: PlaylistImportSupport
    private var podcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val playlistDao = database.playlistDao()
        playlistImportSupport = PlaylistImportSupport(
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            playlistDao = playlistDao,
            userPreferencesRepository = UserPreferencesRepository(context),
        )
        syncHandler = PodcastEpisodeSyncHandler(
            episodeDao = database.episodeDao(),
            podcastDao = database.podcastDao(),
            playlistAutoAddHandler = PlaylistAutoAddHandler(
                playlistDao,
                database.episodeDao(),
                AutoDownloadHandler(
                    userPreferencesRepository = UserPreferencesRepository(context),
                    episodeDao = database.episodeDao(),
                    downloadDao = database.downloadDao(),
                    episodeDownloadEnqueuer = NoOpEpisodeDownloadEnqueuer,
                )
            ),
            episodeStateImportSupport = EpisodeStateImportSupport(
                podcastDao = database.podcastDao(),
                episodeDao = database.episodeDao(),
                userPreferencesRepository = UserPreferencesRepository(context),
                favoriteEpisodeDownloadHandler = NoOpFavoriteEpisodeDownloadHandler
            ),
            playlistImportSupport = playlistImportSupport,
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
        title: String = "Episode",
        publishedAt: Long = 1_000L,
        podcastId: Long = this.podcastId
    ): Episode {
        val id = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = title,
                audioUrl = "https://example.com/$guid.mp3",
                publishedAt = publishedAt
            )
        )
        return database.episodeDao().getById(id)!!
    }

    private suspend fun createPlaylist(name: String): Long {
        return database.playlistDao().insert(Playlist(name = name))
    }

    private suspend fun addToPlaylist(playlistId: Long, episodeId: Long, position: Int = 0) {
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

    /**
     * Mirrors [com.lazysimulation.freecasts.data.repository.PodcastRepository.subscribeToPodcast]
     * episode insertion: only insert GUIDs not already stored.
     */
    private suspend fun insertOnlyMissingEpisodesFromFeed(feedEpisodes: List<Episode>) {
        val existingGuids = database.episodeDao().getAllByPodcastId(podcastId).map { it.guid }.toSet()
        val newEpisodes = feedEpisodes
            .filter { it.guid !in existingGuids }
            .map { it.copy(podcastId = podcastId) }
        if (newEpisodes.isNotEmpty()) {
            database.episodeDao().insertAll(newEpisodes)
        }
    }

    @Test
    fun insertAll_conflictingGuid_preservesEpisodeIdAndPlaylistMembership() = runTest {
        val playlistId = createPlaylist("Queue")
        val original = insertEpisode(guid = "same-guid", title = "Original")
        addToPlaylist(playlistId, original.id)

        val conflictId = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = "same-guid",
                title = "Replacement Attempt",
                audioUrl = "https://example.com/other.mp3",
                publishedAt = 2_000L
            )
        )

        assertEquals(-1L, conflictId)
        val stillThere = database.episodeDao().getByGuid("same-guid")
        assertNotNull(stillThere)
        assertEquals(original.id, stillThere!!.id)
        assertEquals("Original", stillThere.title)
        assertEquals(listOf(original.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun reSubscribeStyleInsert_preservesPlaylistMembershipsAndEpisodeIds() = runTest {
        val playlistId = createPlaylist("Queue")
        val first = insertEpisode(guid = "ep-1", title = "One", publishedAt = 1_000L)
        val second = insertEpisode(guid = "ep-2", title = "Two", publishedAt = 2_000L)
        addToPlaylist(playlistId, first.id, position = 0)
        addToPlaylist(playlistId, second.id, position = 1)

        // Simulate unsubscribe (episodes remain) then re-subscribe feed insert
        database.podcastDao().unsubscribe(podcastId)
        database.podcastDao().subscribe(podcastId)

        insertOnlyMissingEpisodesFromFeed(
            listOf(
                Episode(guid = "ep-1", title = "One", audioUrl = "https://example.com/ep-1.mp3", publishedAt = 1_000L),
                Episode(guid = "ep-2", title = "Two", audioUrl = "https://example.com/ep-2.mp3", publishedAt = 2_000L),
                Episode(guid = "ep-3", title = "Three", audioUrl = "https://example.com/ep-3.mp3", publishedAt = 3_000L),
            )
        )

        assertEquals(listOf(first.id, second.id), playlistEpisodeIds(playlistId))
        assertEquals(first.id, database.episodeDao().getByGuid("ep-1")!!.id)
        assertEquals(second.id, database.episodeDao().getByGuid("ep-2")!!.id)
        assertNotNull(database.episodeDao().getByGuid("ep-3"))
        assertEquals(3, database.episodeDao().getEpisodeCountForPodcast(podcastId))
    }

    @Test
    fun syncEpisodesFromFeed_doesNotDeleteEpisodesMissingFromFeed() = runTest {
        val playlistId = createPlaylist("Queue")
        val kept = insertEpisode(guid = "still-in-feed", publishedAt = 2_000L)
        val removedFromFeed = insertEpisode(guid = "gone-from-feed", publishedAt = 1_000L)
        addToPlaylist(playlistId, kept.id, position = 0)
        addToPlaylist(playlistId, removedFromFeed.id, position = 1)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                Episode(
                    guid = "still-in-feed",
                    title = "Still Here",
                    audioUrl = "https://example.com/still-in-feed.mp3",
                    publishedAt = 2_000L
                )
            )
        )

        assertNotNull(database.episodeDao().getByGuid("gone-from-feed"))
        assertEquals(listOf(kept.id, removedFromFeed.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun syncEpisodesFromFeed_preservesEpisodeIdsWhenGuidAlreadyExists() = runTest {
        val playlistId = createPlaylist("Queue")
        val existing = insertEpisode(guid = "ep-1", title = "Original Title", publishedAt = 1_000L)
        addToPlaylist(playlistId, existing.id)

        syncHandler.syncEpisodesFromFeed(
            podcastId = podcastId,
            feedEpisodes = listOf(
                Episode(
                    guid = "ep-1",
                    title = "Updated Title From Feed",
                    audioUrl = "https://example.com/ep-1.mp3",
                    publishedAt = 1_000L
                ),
                Episode(
                    guid = "ep-2",
                    title = "Brand New",
                    audioUrl = "https://example.com/ep-2.mp3",
                    publishedAt = 2_000L
                )
            )
        )

        val afterSync = database.episodeDao().getByGuid("ep-1")!!
        assertEquals(existing.id, afterSync.id)
        assertEquals("Original Title", afterSync.title)
        assertTrue(database.playlistDao().isEpisodeInPlaylist(playlistId, existing.id))
    }

    @Test
    fun insertAll_blankGuidConflict_doesNotClearPlaylistMembership() = runTest {
        val playlistId = createPlaylist("Queue")
        val firstId = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = "",
                title = "First Blank Guid",
                audioUrl = "https://example.com/a.mp3",
                publishedAt = 1_000L
            )
        )
        addToPlaylist(playlistId, firstId)

        val secondId = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = "",
                title = "Second Blank Guid",
                audioUrl = "https://example.com/b.mp3",
                publishedAt = 2_000L
            )
        )

        assertEquals(-1L, secondId)
        assertEquals(listOf(firstId), playlistEpisodeIds(playlistId))
        assertEquals(1, database.episodeDao().getEpisodeCountForPodcast(podcastId))
    }

    @Test
    fun importPlaylists_matchingExportId_doesNotClearUnrelatedLocalPlaylist() = runTest {
        val localEpisode = insertEpisode(guid = "local-ep")
        val localPlaylistId = createPlaylist("Local Workout")
        addToPlaylist(localPlaylistId, localEpisode.id)

        // Backup exportId intentionally equals the local playlist id (cross-device collision).
        val exported = ExportedPlaylist(
            exportId = localPlaylistId,
            name = "Imported Morning Queue",
            episodes = listOf(
                ExportedPlaylistEpisode(
                    feedUrl = "https://example.com/feed.xml",
                    guid = "local-ep",
                    position = 0,
                )
            ),
        )

        val metadataResult = playlistImportSupport.importPlaylists(listOf(exported))
        playlistImportSupport.applyPlaylistEpisodes(listOf(exported), metadataResult.playlistIdMap)

        val importedPlaylistId = metadataResult.playlistIdMap.getValue(localPlaylistId)
        assertNotEquals(localPlaylistId, importedPlaylistId)
        assertEquals(listOf(localEpisode.id), playlistEpisodeIds(localPlaylistId))
        assertEquals("Local Workout", database.playlistDao().getById(localPlaylistId)!!.name)
        assertTrue(database.playlistDao().isEpisodeInPlaylist(importedPlaylistId, localEpisode.id))
        assertEquals("Imported Morning Queue", database.playlistDao().getById(importedPlaylistId)!!.name)
    }

    @Test
    fun deletePodcast_cascadesEpisodesOutOfPlaylists() = runTest {
        val playlistId = createPlaylist("Queue")
        val episode = insertEpisode(guid = "ep-1")
        addToPlaylist(playlistId, episode.id)

        database.podcastDao().deleteById(podcastId)

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, episode.id))
    }
}
