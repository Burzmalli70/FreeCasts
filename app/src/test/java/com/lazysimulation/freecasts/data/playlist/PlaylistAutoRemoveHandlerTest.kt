package com.lazysimulation.freecasts.data.playlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lazysimulation.freecasts.data.local.FreeCastsDatabase
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Playlist
import com.lazysimulation.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import com.lazysimulation.freecasts.data.local.entity.Podcast
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
class PlaylistAutoRemoveHandlerTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var handler: PlaylistAutoRemoveHandler
    private var podcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        handler = PlaylistAutoRemoveHandler(
            playlistDao = database.playlistDao(),
            episodeDao = database.episodeDao()
        )

        podcastId = database.podcastDao().insert(
            Podcast(feedUrl = "https://example.com/feed.xml", title = "Test Podcast")
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun insertEpisode(guid: String, title: String): Episode {
        val id = database.episodeDao().insert(
            Episode(
                podcastId = podcastId,
                guid = guid,
                title = title,
                audioUrl = "https://example.com/$guid.mp3"
            )
        )
        return database.episodeDao().getById(id)!!
    }

    private suspend fun createPlaylist(
        name: String,
        removeAfterListening: Boolean
    ): Long {
        return database.playlistDao().insert(
            Playlist(
                name = name,
                removeAfterListening = removeAfterListening
            )
        )
    }

    private suspend fun addEpisodeToPlaylist(playlistId: Long, episodeId: Long) {
        database.playlistDao().insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(
                playlistId = playlistId,
                episodeId = episodeId,
                position = 0
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
    fun markEpisodeAsPlayed_removesEpisodeFromAutoRemovePlaylist() = runTest {
        val playlistId = createPlaylist("Queue", removeAfterListening = true)
        val episode = insertEpisode("ep-1", "Episode One")
        addEpisodeToPlaylist(playlistId, episode.id)

        handler.markEpisodeAsPlayed(episode.id)

        assertTrue(database.episodeDao().getById(episode.id)?.isPlayed == true)
        assertEquals(emptyList<Long>(), playlistEpisodeIds(playlistId))
    }

    @Test
    fun markEpisodeAsPlayed_doesNotRemoveFromPlaylistWithoutAutoRemove() = runTest {
        val playlistId = createPlaylist("Keep", removeAfterListening = false)
        val episode = insertEpisode("ep-1", "Episode One")
        addEpisodeToPlaylist(playlistId, episode.id)

        handler.markEpisodeAsPlayed(episode.id)

        assertTrue(database.episodeDao().getById(episode.id)?.isPlayed == true)
        assertEquals(listOf(episode.id), playlistEpisodeIds(playlistId))
    }

    @Test
    fun markEpisodeAsPlayed_removesFromAllAutoRemovePlaylistsContainingEpisode() = runTest {
        val autoRemovePlaylist1 = createPlaylist("Queue A", removeAfterListening = true)
        val autoRemovePlaylist2 = createPlaylist("Queue B", removeAfterListening = true)
        val keepPlaylist = createPlaylist("Archive", removeAfterListening = false)
        val episode = insertEpisode("ep-1", "Episode One")
        val otherEpisode = insertEpisode("ep-2", "Episode Two")

        addEpisodeToPlaylist(autoRemovePlaylist1, episode.id)
        addEpisodeToPlaylist(autoRemovePlaylist2, episode.id)
        addEpisodeToPlaylist(keepPlaylist, episode.id)
        addEpisodeToPlaylist(autoRemovePlaylist1, otherEpisode.id)

        handler.markEpisodeAsPlayed(episode.id)

        assertEquals(listOf(otherEpisode.id), playlistEpisodeIds(autoRemovePlaylist1))
        assertEquals(emptyList<Long>(), playlistEpisodeIds(autoRemovePlaylist2))
        assertEquals(listOf(episode.id), playlistEpisodeIds(keepPlaylist))
    }

    @Test
    fun removeFromAutoRemovePlaylists_doesNothingWhenEpisodeNotInPlaylist() = runTest {
        val playlistId = createPlaylist("Queue", removeAfterListening = true)
        val episode = insertEpisode("ep-1", "Episode One")

        handler.removeFromAutoRemovePlaylists(episode.id)

        assertFalse(database.episodeDao().getById(episode.id)?.isPlayed == true)
        assertEquals(emptyList<Long>(), playlistEpisodeIds(playlistId))
    }
}
