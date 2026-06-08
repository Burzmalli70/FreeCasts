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

        handler = PlaylistAutoAddHandler(database.playlistDao())

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

    @Test
    fun addsNewEpisodeToPlaylistWithAutoAddEnabledForPodcast() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val newEpisode = insertEpisode(podcastId1, "new-ep-1", "New Episode")

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newEpisode))

        val episodeIds = playlistEpisodeIds(playlistId)
        assertEquals(listOf(newEpisode.id), episodeIds)
    }

    @Test
    fun addsNewEpisodeToMultiplePlaylistsForSamePodcast() = runTest {
        val playlistOneId = createAutoAddPlaylist("Morning", podcastId1.toString())
        val playlistTwoId = createAutoAddPlaylist("Commute", podcastId1.toString())
        val newEpisode = insertEpisode(podcastId1, "new-ep-1", "New Episode")

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newEpisode))

        assertEquals(listOf(newEpisode.id), playlistEpisodeIds(playlistOneId))
        assertEquals(listOf(newEpisode.id), playlistEpisodeIds(playlistTwoId))
    }

    @Test
    fun addsNewEpisodesFromMultiplePodcastsToSamePlaylist() = runTest {
        val playlistId = createAutoAddPlaylist(
            name = "Mixed",
            autoAddPodcastIds = "$podcastId1,$podcastId2"
        )
        val episodeFromPodcastOne = insertEpisode(podcastId1, "pod1-new", "Podcast One Episode")
        val episodeFromPodcastTwo = insertEpisode(podcastId2, "pod2-new", "Podcast Two Episode")

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
        val newEpisode = insertEpisode(podcastId1, "new-ep-1", "New Episode")

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
        val newEpisode = insertEpisode(podcastId1, "new-ep-1", "New Episode")

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newEpisode))

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
        assertFalse(database.playlistDao().isEpisodeInPlaylist(playlistId, newEpisode.id))
    }

    @Test
    fun doesNotAddPlayedEpisodes() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val playedEpisode = insertEpisode(
            podcastId = podcastId1,
            guid = "played-ep",
            title = "Already Played",
            isPlayed = true
        )

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(playedEpisode))

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
    }

    @Test
    fun doesNothingWhenNoNewEpisodesProvided() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, emptyList())

        assertEquals(0, database.playlistDao().getEpisodeCount(playlistId))
    }

    @Test
    fun onlyAddsProvidedNewEpisodesNotExistingUnplayedBacklog() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        val oldUnplayedEpisode = insertEpisode(podcastId1, "old-ep", "Old Unplayed Episode")
        val newlySyncedEpisode = insertEpisode(podcastId1, "new-ep", "Newly Synced Episode")

        handler.addNewEpisodesToAutoAddPlaylists(podcastId1, listOf(newlySyncedEpisode))

        val episodeIds = playlistEpisodeIds(playlistId)
        assertEquals(listOf(newlySyncedEpisode.id), episodeIds)
        assertFalse(episodeIds.contains(oldUnplayedEpisode.id))
    }

    @Test
    fun appendsMultipleNewEpisodesInOrder() = runTest {
        val playlistId = createAutoAddPlaylist("Daily", podcastId1.toString())
        database.playlistDao().insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(
                playlistId = playlistId,
                episodeId = insertEpisode(podcastId1, "existing", "Existing Episode").id,
                position = 2
            )
        )

        val firstNewEpisode = insertEpisode(podcastId1, "new-1", "First New")
        val secondNewEpisode = insertEpisode(podcastId1, "new-2", "Second New")

        handler.addNewEpisodesToAutoAddPlaylists(
            podcastId1,
            listOf(firstNewEpisode, secondNewEpisode)
        )

        assertEquals(3, database.playlistDao().getEpisodeCount(playlistId))
        assertEquals(4, database.playlistDao().getMaxPosition(playlistId))
        assertTrue(database.playlistDao().isEpisodeInPlaylist(playlistId, firstNewEpisode.id))
        assertTrue(database.playlistDao().isEpisodeInPlaylist(playlistId, secondNewEpisode.id))
    }
}
