package com.lazysimulation.freecasts.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.lazysimulation.freecasts.data.local.FreeCastsDatabase
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Podcast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class EpisodeDaoTest {
    
    private lateinit var database: FreeCastsDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private var testPodcastId: Long = 0
    
    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        podcastDao = database.podcastDao()
        episodeDao = database.episodeDao()
        
        // Create a podcast for episodes
        testPodcastId = podcastDao.insert(
            Podcast(
                feedUrl = "https://test.com/feed.xml",
                title = "Test Podcast"
            )
        )
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    private fun createTestEpisode(
        id: Long = 0,
        guid: String = "test-guid",
        title: String = "Test Episode",
        podcastId: Long = testPodcastId,
        publishedAt: Long? = System.currentTimeMillis()
    ) = Episode(
        id = id,
        podcastId = podcastId,
        guid = guid,
        title = title,
        audioUrl = "https://example.com/episode.mp3",
        description = "Test description",
        publishedAt = publishedAt
    )
    
    @Test
    fun insertConflictingGuidIsIgnoredAndPreservesExistingRow() = runTest {
        val originalId = episodeDao.insert(createTestEpisode(guid = "dup", title = "Original"))
        val ignoredId = episodeDao.insert(createTestEpisode(guid = "dup", title = "Duplicate"))

        assertEquals(-1L, ignoredId)
        val retrieved = episodeDao.getByGuid("dup")
        assertNotNull(retrieved)
        assertEquals(originalId, retrieved?.id)
        assertEquals("Original", retrieved?.title)
    }
    
    @Test
    fun insertAndGetEpisode() = runTest {
        val episode = createTestEpisode()
        val id = episodeDao.insert(episode)
        
        val retrieved = episodeDao.getById(id)
        
        assertNotNull(retrieved)
        assertEquals("Test Episode", retrieved?.title)
        assertEquals(testPodcastId, retrieved?.podcastId)
    }
    
    @Test
    fun getByGuid() = runTest {
        val episode = createTestEpisode(guid = "unique-guid-123")
        episodeDao.insert(episode)
        
        val retrieved = episodeDao.getByGuid("unique-guid-123")
        
        assertNotNull(retrieved)
        assertEquals("Test Episode", retrieved?.title)
    }
    
    @Test
    fun observeByPodcastId() = runTest {
        val episode1 = createTestEpisode(guid = "ep1", title = "Episode 1", publishedAt = 1000L)
        val episode2 = createTestEpisode(guid = "ep2", title = "Episode 2", publishedAt = 2000L)
        
        episodeDao.insertAll(listOf(episode1, episode2))
        
        val episodes = episodeDao.observeByPodcastId(testPodcastId).first()
        
        assertEquals(2, episodes.size)
        // Should be ordered by publishedAt DESC
        assertEquals("Episode 2", episodes[0].title)
        assertEquals("Episode 1", episodes[1].title)
    }
    
    @Test
    fun addToAndRemoveFromFavorites() = runTest {
        val episode = createTestEpisode()
        val id = episodeDao.insert(episode)
        
        // Add to favorites
        episodeDao.addToFavorites(id)
        var retrieved = episodeDao.getById(id)
        assertTrue(retrieved?.isFavorite == true)
        assertNotNull(retrieved?.favoritedAt)
        
        // Remove from favorites
        episodeDao.removeFromFavorites(id)
        retrieved = episodeDao.getById(id)
        assertFalse(retrieved?.isFavorite == true)
        assertNull(retrieved?.favoritedAt)
    }
    
    @Test
    fun toggleFavorite() = runTest {
        val episode = createTestEpisode()
        val id = episodeDao.insert(episode)
        
        episodeDao.toggleFavorite(id, true)
        var retrieved = episodeDao.getById(id)
        assertTrue(retrieved?.isFavorite == true)
        
        episodeDao.toggleFavorite(id, false)
        retrieved = episodeDao.getById(id)
        assertFalse(retrieved?.isFavorite == true)
    }
    
    @Test
    fun observeFavorites() = runTest {
        val episode1 = createTestEpisode(guid = "ep1", title = "Episode 1")
        val episode2 = createTestEpisode(guid = "ep2", title = "Episode 2")
        val episode3 = createTestEpisode(guid = "ep3", title = "Episode 3")
        
        val id1 = episodeDao.insert(episode1)
        val id2 = episodeDao.insert(episode2)
        episodeDao.insert(episode3)
        
        episodeDao.addToFavorites(id1)
        episodeDao.addToFavorites(id2)
        
        val favorites = episodeDao.observeFavorites().first()
        
        assertEquals(2, favorites.size)
        assertTrue(favorites.all { it.isFavorite })
    }
    
    @Test
    fun updatePlaybackPosition() = runTest {
        val episode = createTestEpisode()
        val id = episodeDao.insert(episode)
        
        episodeDao.updatePlaybackPosition(id, 30000L) // 30 seconds
        
        val retrieved = episodeDao.getById(id)
        assertEquals(30000L, retrieved?.playbackPositionMs)
        assertNotNull(retrieved?.lastPlayedAt)
    }
    
    @Test
    fun markAsPlayedAndUnplayed() = runTest {
        val episode = createTestEpisode()
        val id = episodeDao.insert(episode)
        
        // Mark as played
        episodeDao.markAsPlayed(id)
        var retrieved = episodeDao.getById(id)
        assertTrue(retrieved?.isPlayed == true)
        assertEquals(0L, retrieved?.playbackPositionMs)
        
        // Mark as unplayed
        episodeDao.markAsUnplayed(id)
        retrieved = episodeDao.getById(id)
        assertFalse(retrieved?.isPlayed == true)
    }
    
    @Test
    fun observeInProgress() = runTest {
        val episode1 = createTestEpisode(guid = "ep1", title = "Episode 1")
        val episode2 = createTestEpisode(guid = "ep2", title = "Episode 2")
        val episode3 = createTestEpisode(guid = "ep3", title = "Episode 3")
        
        val id1 = episodeDao.insert(episode1)
        val id2 = episodeDao.insert(episode2)
        val id3 = episodeDao.insert(episode3)
        
        episodeDao.updatePlaybackPosition(id1, 5000L)  // In progress
        episodeDao.updatePlaybackPosition(id2, 10000L) // In progress
        episodeDao.markAsPlayed(id3)                    // Completed
        
        val inProgress = episodeDao.observeInProgress().first()
        
        assertEquals(2, inProgress.size)
        assertTrue(inProgress.all { it.playbackPositionMs > 0 && !it.isPlayed })
    }
    
    @Test
    fun searchEpisodes() = runTest {
        episodeDao.insert(createTestEpisode(guid = "ep1", title = "Kotlin Coroutines Deep Dive"))
        episodeDao.insert(createTestEpisode(guid = "ep2", title = "Android Architecture"))
        episodeDao.insert(createTestEpisode(guid = "ep3", title = "Swift UI Basics"))
        
        val results = episodeDao.search("Kotlin").first()
        
        assertEquals(1, results.size)
        assertEquals("Kotlin Coroutines Deep Dive", results[0].title)
    }
    
    @Test
    fun episodeWithPodcast() = runTest {
        val episode = createTestEpisode()
        val id = episodeDao.insert(episode)
        
        val episodeWithPodcast = episodeDao.getEpisodeWithPodcast(id)
        
        assertNotNull(episodeWithPodcast)
        assertEquals("Test Episode", episodeWithPodcast?.episode?.title)
        assertEquals("Test Podcast", episodeWithPodcast?.podcast?.title)
    }
    
    @Test
    fun deleteByPodcastId() = runTest {
        val episode1 = createTestEpisode(guid = "ep1")
        val episode2 = createTestEpisode(guid = "ep2")
        
        episodeDao.insertAll(listOf(episode1, episode2))
        
        var episodes = episodeDao.observeByPodcastId(testPodcastId).first()
        assertEquals(2, episodes.size)
        
        episodeDao.deleteByPodcastId(testPodcastId)
        
        episodes = episodeDao.observeByPodcastId(testPodcastId).first()
        assertEquals(0, episodes.size)
    }
    
    @Test
    fun observeRecent() = runTest {
        val episode1 = createTestEpisode(guid = "ep1", title = "Old", publishedAt = 1000L)
        val episode2 = createTestEpisode(guid = "ep2", title = "Middle", publishedAt = 2000L)
        val episode3 = createTestEpisode(guid = "ep3", title = "New", publishedAt = 3000L)
        
        episodeDao.insertAll(listOf(episode1, episode2, episode3))
        
        val recent = episodeDao.observeRecent(2).first()
        
        assertEquals(2, recent.size)
        assertEquals("New", recent[0].title)
        assertEquals("Middle", recent[1].title)
    }
}

