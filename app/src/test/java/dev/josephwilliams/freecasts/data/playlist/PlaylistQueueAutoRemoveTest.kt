package dev.josephwilliams.freecasts.data.playlist

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import dev.josephwilliams.freecasts.data.playback.PlaybackEpisodeTracker
import dev.josephwilliams.freecasts.data.playback.PlaybackService
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistQueueAutoRemoveTest {

    private lateinit var database: FreeCastsDatabase
    private lateinit var autoRemoveHandler: PlaylistAutoRemoveHandler
    private lateinit var tracker: PlaybackEpisodeTracker
    private var podcastId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        autoRemoveHandler = PlaylistAutoRemoveHandler(
            playlistDao = database.playlistDao(),
            episodeDao = database.episodeDao()
        )
        tracker = PlaybackEpisodeTracker()

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

    private suspend fun createAutoRemovePlaylist(name: String): Long {
        return database.playlistDao().insert(
            Playlist(name = name, removeAfterListening = true)
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

    private fun testMediaItem(episodeId: Long): MediaItem {
        val extras = Bundle().apply {
            putLong(PlaybackService.EXTRA_EPISODE_ID, episodeId)
            putLong(PlaybackService.EXTRA_PODCAST_ID, podcastId)
        }
        return MediaItem.Builder()
            .setMediaId(episodeId.toString())
            .setUri("https://example.com/$episodeId.mp3")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Episode $episodeId")
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private suspend fun completeEpisode(episodeId: Long) {
        autoRemoveHandler.markEpisodeAsPlayed(episodeId)
    }

    @Test
    fun playingFromTopOfAutoRemovePlaylist_removesFirstEpisodeWhenItFinishes() = runTest {
        val playlistId = createAutoRemovePlaylist("Daily Queue")
        val episodeOne = insertEpisode("ep-1", "First")
        val episodeTwo = insertEpisode("ep-2", "Second")
        val episodeThree = insertEpisode("ep-3", "Third")

        addEpisodeToPlaylist(playlistId, episodeOne.id, position = 0)
        addEpisodeToPlaylist(playlistId, episodeTwo.id, position = 1)
        addEpisodeToPlaylist(playlistId, episodeThree.id, position = 2)

        tracker.onMediaItemTransition(testMediaItem(episodeOne.id), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

        val completedEpisodeId = tracker.onMediaItemTransition(
            testMediaItem(episodeTwo.id),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )

        assertEquals(episodeOne.id, completedEpisodeId)
        completeEpisode(completedEpisodeId!!)

        assertTrue(database.episodeDao().getById(episodeOne.id)?.isPlayed == true)
        assertEquals(
            listOf(episodeTwo.id, episodeThree.id),
            playlistEpisodeIds(playlistId)
        )
    }

    @Test
    fun playingThroughAutoRemovePlaylist_removesEachEpisodeAsItFinishes() = runTest {
        val playlistId = createAutoRemovePlaylist("Daily Queue")
        val episodeOne = insertEpisode("ep-1", "First")
        val episodeTwo = insertEpisode("ep-2", "Second")
        val episodeThree = insertEpisode("ep-3", "Third")

        addEpisodeToPlaylist(playlistId, episodeOne.id, position = 0)
        addEpisodeToPlaylist(playlistId, episodeTwo.id, position = 1)
        addEpisodeToPlaylist(playlistId, episodeThree.id, position = 2)

        tracker.onMediaItemTransition(testMediaItem(episodeOne.id), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        completeEpisode(tracker.onMediaItemTransition(
            testMediaItem(episodeTwo.id),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )!!)

        completeEpisode(tracker.onMediaItemTransition(
            testMediaItem(episodeThree.id),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )!!)

        completeEpisode(tracker.onPlaybackEnded(hasNextMediaItem = false)!!)

        assertEquals(emptyList<Long>(), playlistEpisodeIds(playlistId))
    }

    @Test
    fun playingFromMiddleOfAutoRemovePlaylist_removesCorrectEpisodeWhenItFinishes() = runTest {
        val playlistId = createAutoRemovePlaylist("Daily Queue")
        val episodeOne = insertEpisode("ep-1", "First")
        val episodeTwo = insertEpisode("ep-2", "Second")
        val episodeThree = insertEpisode("ep-3", "Third")

        addEpisodeToPlaylist(playlistId, episodeOne.id, position = 0)
        addEpisodeToPlaylist(playlistId, episodeTwo.id, position = 1)
        addEpisodeToPlaylist(playlistId, episodeThree.id, position = 2)

        tracker.onMediaItemTransition(testMediaItem(episodeTwo.id), Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        val completedEpisodeId = tracker.onMediaItemTransition(
            testMediaItem(episodeThree.id),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )

        assertEquals(episodeTwo.id, completedEpisodeId)
        completeEpisode(completedEpisodeId!!)

        assertEquals(
            listOf(episodeOne.id, episodeThree.id),
            playlistEpisodeIds(playlistId)
        )
    }
}
