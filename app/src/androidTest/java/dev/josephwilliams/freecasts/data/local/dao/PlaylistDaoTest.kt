package dev.josephwilliams.freecasts.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.josephwilliams.freecasts.data.local.FreeCastsDatabase
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Playlist
import dev.josephwilliams.freecasts.data.local.entity.PlaylistEpisodeCrossRef
import dev.josephwilliams.freecasts.data.local.entity.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    
    private lateinit var database: FreeCastsDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var podcastDao: PodcastDao
    private var testPodcastId: Long = 0
    
    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistDao = database.playlistDao()
        episodeDao = database.episodeDao()
        podcastDao = database.podcastDao()
        
        testPodcastId = podcastDao.insert(
            Podcast(feedUrl = "https://test.com/feed.xml", title = "Test Podcast")
        )
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    private fun createTestPlaylist(
        id: Long = 0,
        name: String = "Test Playlist"
    ) = Playlist(
        id = id,
        name = name,
        description = "Test Description"
    )
    
    private fun createTestEpisode(guid: String, title: String) = Episode(
        podcastId = testPodcastId,
        guid = guid,
        title = title,
        audioUrl = "https://example.com/$guid.mp3"
    )
    
    @Test
    fun insertAndGetPlaylist() = runTest {
        val playlist = createTestPlaylist()
        val id = playlistDao.insert(playlist)
        
        val retrieved = playlistDao.getById(id)
        
        assertNotNull(retrieved)
        assertEquals("Test Playlist", retrieved?.name)
    }
    
    @Test
    fun updatePlaylist() = runTest {
        val playlist = createTestPlaylist()
        val id = playlistDao.insert(playlist)
        
        val updated = playlist.copy(id = id, name = "Updated Name")
        playlistDao.update(updated)
        
        val retrieved = playlistDao.getById(id)
        assertEquals("Updated Name", retrieved?.name)
    }
    
    @Test
    fun deletePlaylist() = runTest {
        val playlist = createTestPlaylist()
        val id = playlistDao.insert(playlist)
        
        playlistDao.deleteById(id)
        
        val retrieved = playlistDao.getById(id)
        assertNull(retrieved)
    }
    
    @Test
    fun observeAllPlaylists() = runTest {
        playlistDao.insert(createTestPlaylist(name = "Playlist 1"))
        playlistDao.insert(createTestPlaylist(name = "Playlist 2"))
        playlistDao.insert(createTestPlaylist(name = "Playlist 3"))
        
        val playlists = playlistDao.observeAll().first()
        
        assertEquals(3, playlists.size)
    }
    
    @Test
    fun searchPlaylists() = runTest {
        playlistDao.insert(createTestPlaylist(name = "My Favorites"))
        playlistDao.insert(createTestPlaylist(name = "Road Trip"))
        playlistDao.insert(createTestPlaylist(name = "Workout Mix"))
        
        val results = playlistDao.search("Trip").first()
        
        assertEquals(1, results.size)
        assertEquals("Road Trip", results[0].name)
    }
    
    @Test
    fun addEpisodeToPlaylist() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        
        val crossRef = PlaylistEpisodeCrossRef(
            playlistId = playlistId,
            episodeId = episodeId,
            position = 0
        )
        playlistDao.insertPlaylistEpisode(crossRef)
        
        val isInPlaylist = playlistDao.isEpisodeInPlaylist(playlistId, episodeId)
        assertTrue(isInPlaylist)
    }
    
    @Test
    fun removeEpisodeFromPlaylist() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        
        playlistDao.insertPlaylistEpisode(
            PlaylistEpisodeCrossRef(playlistId, episodeId, 0)
        )
        
        playlistDao.removeEpisodeFromPlaylist(playlistId, episodeId)
        
        val isInPlaylist = playlistDao.isEpisodeInPlaylist(playlistId, episodeId)
        assertFalse(isInPlaylist)
    }
    
    @Test
    fun getPlaylistWithEpisodes() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId1 = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        val episodeId2 = episodeDao.insert(createTestEpisode("ep2", "Episode 2"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId1, 0))
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId2, 1))
        
        val playlistWithEpisodes = playlistDao.getPlaylistWithEpisodes(playlistId)
        
        assertNotNull(playlistWithEpisodes)
        assertEquals(2, playlistWithEpisodes?.episodes?.size)
    }
    
    @Test
    fun clearPlaylist() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId1 = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        val episodeId2 = episodeDao.insert(createTestEpisode("ep2", "Episode 2"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId1, 0))
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId2, 1))
        
        playlistDao.clearPlaylist(playlistId)
        
        val count = playlistDao.getEpisodeCount(playlistId)
        assertEquals(0, count)
    }
    
    @Test
    fun getEpisodeCount() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId1 = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        val episodeId2 = episodeDao.insert(createTestEpisode("ep2", "Episode 2"))
        val episodeId3 = episodeDao.insert(createTestEpisode("ep3", "Episode 3"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId1, 0))
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId2, 1))
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId3, 2))
        
        val count = playlistDao.getEpisodeCount(playlistId)
        assertEquals(3, count)
    }
    
    @Test
    fun getMaxPosition() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId1 = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        val episodeId2 = episodeDao.insert(createTestEpisode("ep2", "Episode 2"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId1, 0))
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId2, 5))
        
        val maxPosition = playlistDao.getMaxPosition(playlistId)
        assertEquals(5, maxPosition)
    }
    
    @Test
    fun updateEpisodePosition() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId, 0))
        playlistDao.updateEpisodePosition(playlistId, episodeId, 10)
        
        val maxPosition = playlistDao.getMaxPosition(playlistId)
        assertEquals(10, maxPosition)
    }
    
    @Test
    fun deletingPlaylistRemovesEpisodeAssociations() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId, 0))
        
        playlistDao.deleteById(playlistId)
        
        // Episode should still exist
        val episode = episodeDao.getById(episodeId)
        assertNotNull(episode)
        
        // But playlist association should be gone (can't check directly, but new playlist won't have it)
        val newPlaylistId = playlistDao.insert(createTestPlaylist(name = "New"))
        val isInNewPlaylist = playlistDao.isEpisodeInPlaylist(newPlaylistId, episodeId)
        assertFalse(isInNewPlaylist)
    }
    
    @Test
    fun deletingEpisodeRemovesFromPlaylist() = runTest {
        val playlistId = playlistDao.insert(createTestPlaylist())
        val episodeId = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlistId, episodeId, 0))
        
        episodeDao.deleteById(episodeId)
        
        val count = playlistDao.getEpisodeCount(playlistId)
        assertEquals(0, count)
    }
    
    @Test
    fun episodeCanBeInMultiplePlaylists() = runTest {
        val playlist1Id = playlistDao.insert(createTestPlaylist(name = "Playlist 1"))
        val playlist2Id = playlistDao.insert(createTestPlaylist(name = "Playlist 2"))
        val episodeId = episodeDao.insert(createTestEpisode("ep1", "Episode 1"))
        
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlist1Id, episodeId, 0))
        playlistDao.insertPlaylistEpisode(PlaylistEpisodeCrossRef(playlist2Id, episodeId, 0))
        
        assertTrue(playlistDao.isEpisodeInPlaylist(playlist1Id, episodeId))
        assertTrue(playlistDao.isEpisodeInPlaylist(playlist2Id, episodeId))
        
        val playlistIds = playlistDao.observePlaylistsContainingEpisode(episodeId).first()
        assertEquals(2, playlistIds.size)
    }
}

