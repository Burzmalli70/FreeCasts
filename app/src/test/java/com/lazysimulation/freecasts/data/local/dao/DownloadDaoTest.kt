package com.lazysimulation.freecasts.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import com.lazysimulation.freecasts.data.local.FreeCastsDatabase
import com.lazysimulation.freecasts.data.local.entity.Download
import com.lazysimulation.freecasts.data.local.entity.DownloadStatus
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
class DownloadDaoTest {
    
    private lateinit var database: FreeCastsDatabase
    private lateinit var downloadDao: DownloadDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var podcastDao: PodcastDao
    private var testPodcastId: Long = 0
    
    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeCastsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        downloadDao = database.downloadDao()
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
    
    private suspend fun createTestEpisode(guid: String = "ep1"): Long {
        return episodeDao.insert(
            Episode(
                podcastId = testPodcastId,
                guid = guid,
                title = "Test Episode",
                audioUrl = "https://example.com/$guid.mp3"
            )
        )
    }
    
    private fun createTestDownload(
        id: Long = 0,
        episodeId: Long,
        status: DownloadStatus = DownloadStatus.PENDING
    ) = Download(
        id = id,
        episodeId = episodeId,
        status = status,
        totalBytes = 10_000_000L
    )
    
    @Test
    fun insertAndGetDownload() = runTest {
        val episodeId = createTestEpisode()
        val download = createTestDownload(episodeId = episodeId)
        val id = downloadDao.insert(download)
        
        val retrieved = downloadDao.getById(id)
        
        assertNotNull(retrieved)
        assertEquals(episodeId, retrieved?.episodeId)
        assertEquals(DownloadStatus.PENDING, retrieved?.status)
    }
    
    @Test
    fun getByEpisodeId() = runTest {
        val episodeId = createTestEpisode()
        downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        val retrieved = downloadDao.getByEpisodeId(episodeId)
        
        assertNotNull(retrieved)
        assertEquals(episodeId, retrieved?.episodeId)
    }
    
    @Test
    fun deleteDownload() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.deleteById(id)
        
        val retrieved = downloadDao.getById(id)
        assertNull(retrieved)
    }
    
    @Test
    fun deleteByEpisodeId() = runTest {
        val episodeId = createTestEpisode()
        downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.deleteByEpisodeId(episodeId)
        
        val retrieved = downloadDao.getByEpisodeId(episodeId)
        assertNull(retrieved)
    }
    
    @Test
    fun updateStatus() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING)
        
        val retrieved = downloadDao.getById(id)
        assertEquals(DownloadStatus.DOWNLOADING, retrieved?.status)
    }
    
    @Test
    fun markAsDownloading() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.markAsDownloading(id)
        
        val retrieved = downloadDao.getById(id)
        assertEquals(DownloadStatus.DOWNLOADING, retrieved?.status)
        assertNotNull(retrieved?.startedAt)
    }
    
    @Test
    fun markAsCompleted() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.markAsCompleted(id)
        
        val retrieved = downloadDao.getById(id)
        assertEquals(DownloadStatus.COMPLETED, retrieved?.status)
        assertEquals(100, retrieved?.progressPercent)
        assertNotNull(retrieved?.completedAt)
    }
    
    @Test
    fun markAsFailed() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.markAsFailed(id, "Network error")
        
        val retrieved = downloadDao.getById(id)
        assertEquals(DownloadStatus.FAILED, retrieved?.status)
        assertEquals("Network error", retrieved?.errorMessage)
    }
    
    @Test
    fun markAsPaused() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(
            createTestDownload(episodeId = episodeId, status = DownloadStatus.DOWNLOADING)
        )
        
        downloadDao.markAsPaused(id)
        
        val retrieved = downloadDao.getById(id)
        assertEquals(DownloadStatus.PAUSED, retrieved?.status)
    }
    
    @Test
    fun markAsCancelled() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.markAsCancelled(id)
        
        val retrieved = downloadDao.getById(id)
        assertEquals(DownloadStatus.CANCELLED, retrieved?.status)
    }
    
    @Test
    fun updateProgress() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.updateProgress(id, 50, 5_000_000L)
        
        val retrieved = downloadDao.getById(id)
        assertEquals(50, retrieved?.progressPercent)
        assertEquals(5_000_000L, retrieved?.downloadedBytes)
    }
    
    @Test
    fun updateLocalFilePath() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.updateLocalFilePath(id, "/storage/podcasts/episode.mp3")
        
        val retrieved = downloadDao.getById(id)
        assertEquals("/storage/podcasts/episode.mp3", retrieved?.localFilePath)
    }
    
    @Test
    fun incrementRetryCount() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        downloadDao.incrementRetryCount(id)
        downloadDao.incrementRetryCount(id)
        
        val retrieved = downloadDao.getById(id)
        assertEquals(2, retrieved?.retryCount)
    }
    
    @Test
    fun isEpisodeDownloaded() = runTest {
        val episodeId = createTestEpisode()
        val id = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        var isDownloaded = downloadDao.isEpisodeDownloaded(episodeId)
        assertFalse(isDownloaded)
        
        downloadDao.markAsCompleted(id)
        
        isDownloaded = downloadDao.isEpisodeDownloaded(episodeId)
        assertTrue(isDownloaded)
    }
    
    @Test
    fun observeByStatus() = runTest {
        val ep1 = createTestEpisode("ep1")
        val ep2 = createTestEpisode("ep2")
        val ep3 = createTestEpisode("ep3")
        
        downloadDao.insert(createTestDownload(episodeId = ep1, status = DownloadStatus.PENDING))
        downloadDao.insert(createTestDownload(episodeId = ep2, status = DownloadStatus.DOWNLOADING))
        downloadDao.insert(createTestDownload(episodeId = ep3, status = DownloadStatus.COMPLETED))
        
        val pending = downloadDao.observeByStatus(DownloadStatus.PENDING).first()
        val downloading = downloadDao.observeByStatus(DownloadStatus.DOWNLOADING).first()
        val completed = downloadDao.observeByStatus(DownloadStatus.COMPLETED).first()
        
        assertEquals(1, pending.size)
        assertEquals(1, downloading.size)
        assertEquals(1, completed.size)
    }
    
    @Test
    fun observeActive() = runTest {
        val ep1 = createTestEpisode("ep1")
        val ep2 = createTestEpisode("ep2")
        val ep3 = createTestEpisode("ep3")
        
        downloadDao.insert(createTestDownload(episodeId = ep1, status = DownloadStatus.PENDING))
        downloadDao.insert(createTestDownload(episodeId = ep2, status = DownloadStatus.DOWNLOADING))
        downloadDao.insert(createTestDownload(episodeId = ep3, status = DownloadStatus.COMPLETED))
        
        val active = downloadDao.observeActive().first()
        
        assertEquals(2, active.size)
    }
    
    @Test
    fun observeCompleted() = runTest {
        val ep1 = createTestEpisode("ep1")
        val ep2 = createTestEpisode("ep2")
        
        val id1 = downloadDao.insert(createTestDownload(episodeId = ep1))
        downloadDao.insert(createTestDownload(episodeId = ep2))
        
        downloadDao.markAsCompleted(id1)
        
        val completed = downloadDao.observeCompleted().first()
        
        assertEquals(1, completed.size)
    }
    
    @Test
    fun observeFailed() = runTest {
        val ep1 = createTestEpisode("ep1")
        val ep2 = createTestEpisode("ep2")
        
        val id1 = downloadDao.insert(createTestDownload(episodeId = ep1))
        downloadDao.insert(createTestDownload(episodeId = ep2))
        
        downloadDao.markAsFailed(id1, "Error")
        
        val failed = downloadDao.observeFailed().first()
        
        assertEquals(1, failed.size)
    }
    
    @Test
    fun observeDownloadedCount() = runTest {
        val ep1 = createTestEpisode("ep1")
        val ep2 = createTestEpisode("ep2")
        val ep3 = createTestEpisode("ep3")
        
        val id1 = downloadDao.insert(createTestDownload(episodeId = ep1))
        val id2 = downloadDao.insert(createTestDownload(episodeId = ep2))
        downloadDao.insert(createTestDownload(episodeId = ep3))
        
        downloadDao.markAsCompleted(id1)
        downloadDao.markAsCompleted(id2)
        
        val count = downloadDao.observeDownloadedCount().first()
        
        assertEquals(2, count)
    }
    
    @Test
    fun deletingEpisodeCascadesDownload() = runTest {
        val episodeId = createTestEpisode()
        val downloadId = downloadDao.insert(createTestDownload(episodeId = episodeId))
        
        episodeDao.deleteById(episodeId)
        
        val retrieved = downloadDao.getById(downloadId)
        assertNull(retrieved)
    }
    
    @Test
    fun observeTotalDownloadedBytes() = runTest {
        val ep1 = createTestEpisode("ep1")
        val ep2 = createTestEpisode("ep2")
        
        val id1 = downloadDao.insert(
            createTestDownload(episodeId = ep1).copy(downloadedBytes = 5_000_000L)
        )
        val id2 = downloadDao.insert(
            createTestDownload(episodeId = ep2).copy(downloadedBytes = 3_000_000L)
        )
        
        downloadDao.markAsCompleted(id1)
        downloadDao.markAsCompleted(id2)
        
        // Update downloaded bytes after completing
        downloadDao.updateProgress(id1, 100, 5_000_000L)
        downloadDao.updateProgress(id2, 100, 3_000_000L)
        
        val total = downloadDao.observeTotalDownloadedBytes().first()
        
        assertEquals(8_000_000L, total)
    }
}

