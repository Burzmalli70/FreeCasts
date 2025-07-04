package dev.josephwilliams.filedownloader

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import dev.josephwilliams.filedownloader.model.DownloadInfo
import dev.josephwilliams.filedownloader.model.DownloadState
import dev.josephwilliams.filedownloader.service.DownloadService
import dev.josephwilliams.filedownloader.service.PersistenceManager
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DownloadManagerTests {
    @RelaxedMockK
    private lateinit var mockContext: Context

    @MockK
    private lateinit var mockWorkManager: WorkManager

    @MockK
    private lateinit var mockPersistenceManager: PersistenceManager

    private lateinit var downloadManager: DownloadManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        // Setup for WorkManager
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        // Setup for DownloadService (mocking static methods)
        mockkStatic(DownloadService::class)
        every { DownloadService.startDownload(any(), any()) } just Runs
        every { DownloadService.pauseDownload(any(), any()) } just Runs
        every { DownloadService.resumeDownload(any(), any()) } just Runs
        every { DownloadService.cancelDownload(any(), any()) } just Runs

        // Create instance of class under test
        downloadManager = DownloadManager(mockContext)

        // Use reflection to inject mocked PersistenceManager
        val field = DownloadManager::class.java.getDeclaredField("persistenceManager")
        field.isAccessible = true
        field.set(downloadManager, mockPersistenceManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getAllDownloads_returns_flow_from_persistence_manager() = runTest {
        // Arrange
        val downloads = listOf(
            DownloadInfo(
                id = "test-id",
                url = "https://example.com/file.mp3",
                fileName = "file.mp3",
                destination = "/storage/downloads",
                totalBytes = 1000,
                _state = DownloadState.COMPLETED.name
            )
        )
        every { mockPersistenceManager.getAllDownloadsAsFlow() } returns flowOf(downloads)

        // Act
        val result = downloadManager.getAllDownloads()

        // Assert
        verify { mockPersistenceManager.getAllDownloadsAsFlow() }
        assertEquals(flowOf(downloads).toString(), result.toString())
    }
}