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
class PodcastDaoTest {
    
    private lateinit var database: FreeCastsDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        podcastDao = database.podcastDao()
        episodeDao = database.episodeDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    private fun createTestPodcast(
        id: Long = 0,
        feedUrl: String = "https://example.com/feed.xml",
        title: String = "Test Podcast",
        isSubscribed: Boolean = false
    ) = Podcast(
        id = id,
        feedUrl = feedUrl,
        title = title,
        author = "Test Author",
        description = "Test Description",
        artworkUrl = "https://example.com/art.jpg",
        isSubscribed = isSubscribed
    )
    
    @Test
    fun insertAndGetPodcast() = runTest {
        val podcast = createTestPodcast()
        val id = podcastDao.insert(podcast)
        
        val retrieved = podcastDao.getById(id)
        
        assertNotNull(retrieved)
        assertEquals("Test Podcast", retrieved?.title)
        assertEquals("https://example.com/feed.xml", retrieved?.feedUrl)
    }
    
    @Test
    fun insertDuplicateFeedUrlReplaces() = runTest {
        val podcast1 = createTestPodcast(title = "Original")
        val id1 = podcastDao.insert(podcast1)
        
        val podcast2 = createTestPodcast(title = "Updated")
        podcastDao.insert(podcast2)
        
        val all = podcastDao.observeAll().first()
        // Should only have one entry due to unique feedUrl constraint with REPLACE strategy
        assertEquals(1, all.size)
    }
    
    @Test
    fun getByFeedUrl() = runTest {
        val podcast = createTestPodcast(feedUrl = "https://unique.com/feed.xml")
        podcastDao.insert(podcast)
        
        val retrieved = podcastDao.getByFeedUrl("https://unique.com/feed.xml")
        
        assertNotNull(retrieved)
        assertEquals("Test Podcast", retrieved?.title)
    }
    
    @Test
    fun deletePodcast() = runTest {
        val podcast = createTestPodcast()
        val id = podcastDao.insert(podcast)
        
        podcastDao.deleteById(id)
        
        val retrieved = podcastDao.getById(id)
        assertNull(retrieved)
    }
    
    @Test
    fun subscribeAndUnsubscribe() = runTest {
        val podcast = createTestPodcast(isSubscribed = false)
        val id = podcastDao.insert(podcast)
        
        // Subscribe
        podcastDao.subscribe(id)
        var retrieved = podcastDao.getById(id)
        assertTrue(retrieved?.isSubscribed == true)
        assertNotNull(retrieved?.subscribedAt)
        
        // Unsubscribe
        podcastDao.unsubscribe(id)
        retrieved = podcastDao.getById(id)
        assertFalse(retrieved?.isSubscribed == true)
        assertNull(retrieved?.subscribedAt)
    }
    
    @Test
    fun observeSubscribedPodcasts() = runTest {
        val podcast1 = createTestPodcast(feedUrl = "https://feed1.com", title = "Podcast 1")
        val podcast2 = createTestPodcast(feedUrl = "https://feed2.com", title = "Podcast 2")
        val podcast3 = createTestPodcast(feedUrl = "https://feed3.com", title = "Podcast 3")
        
        val id1 = podcastDao.insert(podcast1)
        val id2 = podcastDao.insert(podcast2)
        podcastDao.insert(podcast3)
        
        podcastDao.subscribe(id1)
        podcastDao.subscribe(id2)
        
        val subscribed = podcastDao.observeSubscribed().first()
        
        assertEquals(2, subscribed.size)
        assertTrue(subscribed.all { it.isSubscribed })
    }
    
    @Test
    fun searchPodcasts() = runTest {
        podcastDao.insert(createTestPodcast(feedUrl = "https://1.com", title = "Kotlin Weekly"))
        podcastDao.insert(createTestPodcast(feedUrl = "https://2.com", title = "Android Podcast"))
        podcastDao.insert(createTestPodcast(feedUrl = "https://3.com", title = "Swift Talk"))
        
        val results = podcastDao.search("Kotlin").first()
        
        assertEquals(1, results.size)
        assertEquals("Kotlin Weekly", results[0].title)
    }
    
    @Test
    fun podcastWithEpisodes() = runTest {
        val podcast = createTestPodcast()
        val podcastId = podcastDao.insert(podcast)
        
        val episode1 = Episode(
            podcastId = podcastId,
            guid = "ep1",
            title = "Episode 1",
            audioUrl = "https://example.com/ep1.mp3"
        )
        val episode2 = Episode(
            podcastId = podcastId,
            guid = "ep2",
            title = "Episode 2",
            audioUrl = "https://example.com/ep2.mp3"
        )
        
        episodeDao.insert(episode1)
        episodeDao.insert(episode2)
        
        val podcastWithEpisodes = podcastDao.getPodcastWithEpisodes(podcastId)
        
        assertNotNull(podcastWithEpisodes)
        assertEquals(2, podcastWithEpisodes?.episodes?.size)
    }
    
    @Test
    fun deletePodcastCascadesEpisodes() = runTest {
        val podcast = createTestPodcast()
        val podcastId = podcastDao.insert(podcast)
        
        val episode = Episode(
            podcastId = podcastId,
            guid = "ep1",
            title = "Episode 1",
            audioUrl = "https://example.com/ep1.mp3"
        )
        val episodeId = episodeDao.insert(episode)
        
        // Delete podcast
        podcastDao.deleteById(podcastId)
        
        // Episode should be deleted via cascade
        val retrievedEpisode = episodeDao.getById(episodeId)
        assertNull(retrievedEpisode)
    }
    
    @Test
    fun updateEpisodeCount() = runTest {
        val podcast = createTestPodcast()
        val id = podcastDao.insert(podcast)
        
        podcastDao.updateEpisodeCount(id, 42)
        
        val retrieved = podcastDao.getById(id)
        assertEquals(42, retrieved?.episodeCount)
    }
    
    @Test
    fun updateLastFetchedAt() = runTest {
        val podcast = createTestPodcast()
        val id = podcastDao.insert(podcast)
        val timestamp = System.currentTimeMillis()
        
        podcastDao.updateLastFetchedAt(id, timestamp)
        
        val retrieved = podcastDao.getById(id)
        assertEquals(timestamp, retrieved?.lastFetchedAt)
    }
}

