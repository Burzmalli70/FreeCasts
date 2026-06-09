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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies auto-add playlist behavior from [docs/design.md]:
 * - Only unplayed episodes are added
 * - Only the most recent episode per podcast is eligible
 * - If the most recent episode is played, nothing is added for that podcast
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistAutoAddHandlerTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var handler: PlaylistAutoAddHandler
    private var podcastId1: Long = 0
    private var podcastId2: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        handler = PlaylistAutoAddHandler(
            playlistDao = database.playlistDao(),
            episodeDao = database.episodeDao()
        )

        podcastId1 = database.podcastDao().insert(
            Podcast(feedUrl = "https://example.com/feed1.xml", title = "Podcast One")
        )
        podcastId2 = database.podcastDao().insert(
            Podcast(feedUrl = "https://example.com/feed2.xml", title = "Podcast Two")
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

    private suspend fun createAutoAddPlaylist(
        name: String,
        autoAddPodcastIds: String
    ): Long {
        return database.playlistDao().insert(
            Playlist(
                name = name,
                autoAddPodcastIds = autoAddPodcastIds
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

    // === Sync auto-add: only unplayed, most recent, newly synced ===

    @Test
    fun addsMostRecentUnplayedNewEpisodeToAutoAddPlaylist() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val newEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "new-ep-1",
            title = "New Episode",
            publishedAt = 3_000L
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newEpisode))

        assertEquals(listOf(newEpisode.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun doesNotAddPlayedEpisodesDuringSync() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val playedEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "played-ep",
            title = "Already Played",
            publishedAt = 3_000L,
            isPlayed = true
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(playedEpisode))

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
    }

    @Test
    fun doesNotAddWhenMostRecentEpisodeIsPlayedEvenIfOlderUnplayedEpisodesExist() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val olderUnplayed = insertEpisode(
            podcastId = podcastId1,
            guid = "older-unplayed",
            title = "Older Unplayed",
            publishedAt = 1_000L,
            isPlayed = false
        )
        val mostRecentPlayed = insertEpisode(
            podcastId = podcastId1,
            guid = "latest-played",
            title = "Latest Played",
            publishedAt = 3_000L,
            isPlayed = true
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(mostRecentPlayed))

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, olderUnplayed.id))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, mostRecentPlayed.id))
    }

    @Test
    fun addsOnlyMostRecentUnplayedWhenMultipleNewUnplayedEpisodesAreSynced() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val olderNewEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "older-new",
            title = "Older New",
            publishedAt = 1_000L
        )
        val mostRecentNewEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "latest-new",
            title = "Latest New",
            publishedAt = 3_000L
        )

        handler.addNewEpisodesToAutoAddPlaylists(
            podcastId1,
            listOf(olderNewEpisode, mostRecentNewEpisode)
        )

        assertEquals(listOf(mostRecentNewEpisode.id), playlistEpisodeIds(playlistId))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, olderNewEpisode.id))
    }

    @Test
    fun doesNotAddOlderUnplayedBacklogWhenSyncingLessRecentEpisode() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val existingMostRecentUnplayed = insertEpisode(
            podcastId = podcastId1,
            guid = "existing-latest",
            title = "Existing Latest",
            publishedAt = 3_000L
        )
        val newlySyncedOlderEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "new-older",
            title = "Newly Synced Older",
            publishedAt = 2_000L
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newlySyncedOlderEpisode))

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, existingMostRecentUnplayed.id))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, newlySyncedOlderEpisode.id))
    }

    @Test
    fun doesNotAddWhenNewlySyncedEpisodeIsUnplayedButNotMostRecentBecauseLatestIsPlayed() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        insertEpisode(
            podcastId = podcastId1,
            guid = "latest-played",
            title = "Latest Played",
            publishedAt = 3_000L,
            isPlayed = true
        )
        val newlySyncedOlderUnplayed = insertEpisode(
            podcastId = podcastId1,
            guid = "new-older-unplayed",
            title = "New Older Unplayed",
            publishedAt = 2_000L
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newlySyncedOlderUnplayed))

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
    }

    @Test
    fun addsNewEpisodeToMultipleAutoAddPlaylistsForSamePodcast() = runTest {
        val playlistOneId = createAutoAddPlaylist("Morning", podcastId1.toString())
        val playlistTwoId = createAutoAddPlaylist("Commute", podcastId1.toString())
        val newEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "new-ep-1",
            title = "New Episode",
            publishedAt = 3_000L
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newEpisode))

        assertEquals(listOf(newEpisode.id), playlistEpisodeIds(playlistOneId))
        assertEquals(listOf(newEpisode.id), playlistEpisodeIds(playlistTwoId))
    }

    @Test
    fun addsMostRecentUnplayedFromEachPodcastToSharedAutoAddPlaylist() = runTest {
        val playlistId = createAutoAddPlaylist(
            name = "Mixed",
            autoAddPodcastIds = "$podcastId1,$podcastId2"
        )
        val episodeFromPodcastOne = insertEpisode(
            podcastId = podcastId1,
            guid = "pod1-new",
            title = "Podcast One Episode",
            publishedAt = 3_000L
        )
        val episodeFromPodcastTwo = insertEpisode(
            podcastId = podcastId2,
            guid = "pod2-new",
            title = "Podcast Two Episode",
            publishedAt = 4_000L
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(episodeFromPodcastOne))
        handler.addNewEpisodesToAutoAddPlaylists(podcastId2, listOf(episodeFromPodcastTwo))

        val episodeIds = playlistEpisodeIds(playlistId)
        assertEquals(2, episodeIds.size)
        assertTrue(episodeIds.contains(episodeFromPodcastOne.id))
        assertTrue(episodeIds.contains(episodeFromPodcastTwo.id))
    }

    @Test
    fun doesNotAddDuplicateWhenEpisodeAlreadyInPlaylist() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val newEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "new-ep-1",
            title = "New Episode",
            publishedAt = 3_000L
        )

        database.playlistDao().insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(
                playlistId = playlistId,
                episodeId = newEpisode.id,
                position = 0
            )
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newEpisode))

        assertEquals(1, database.playlistDao().getEpisodeCount(playlistId))
        assertEquals(listOf(newEpisode.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun doesNotAddToPlaylistWithoutAutoAddForPodcast() = runTest {
        val playlistId = createAutoAddPlaylist("Other Podcast", podcastId2.toString())
        val newEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "new-ep-1",
            title = "New Episode",
            publishedAt = 3_000L
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newEpisode))

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, newEpisode.id))
    }

    @Test
    fun doesNothingWhenNoNewEpisodesProvided() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, emptyList())

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
    }

    // === Initial auto-add when enabling auto-add on a playlist ===

    @Test
    fun addsMostRecentUnplayedEpisodeWhenEnablingAutoAddOnPlaylist() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        insertEpisode(
            podcastId = podcastId1,
            guid = "older-unplayed",
            title = "Older Unplayed",
            publishedAt = 1_000L
        )
        val mostRecentUnplayed = insertEpisode(
            podcastId = podcastId1,
            guid = "latest-unplayed",
            title = "Latest Unplayed",
            publishedAt = 3_000L
        )

        handler.addMostRecentUnplayedEpisodeToPlaylist(playlistId, podcastId1)

        assertEquals(listOf(mostRecentUnplayed.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun doesNotAddWhenEnablingAutoAddIfMostRecentEpisodeIsPlayed() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val olderUnplayed = insertEpisode(
            podcastId = podcastId1,
            guid = "older-unplayed",
            title = "Older Unplayed",
            publishedAt = 1_000L
        )
        insertEpisode(
            podcastId = podcastId1,
            guid = "latest-played",
            title = "Latest Played",
            publishedAt = 3_000L,
            isPlayed = true
        )

        handler.addMostRecentUnplayedEpisodeToPlaylist(playlistId, podcastId1)

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, olderUnplayed.id))
    }
}
