package dev.joewilliams.downloadmanager

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
class DownloadServiceTests {
    @get:Rule
    val serviceRule = ServiceTestRule()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var targetFile: File

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        context = ApplicationProvider.getApplicationContext<Context>()

        val cacheDir = context.cacheDir
        targetFile = File(cacheDir, "test_download.txt")
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    @Test
    fun testDownloadService_successfulDownload_createsFileWithCorrectContent() = runBlocking {
        val fileContent = "This is a test file content for download."
        val mockUrl = mockWebServer.url("/download/testfile.txt").toString()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fileContent)
        )

        val intent = Intent(context, DownloaderService::class.java).apply {
            action = DownloaderService.ACTION_START_DOWNLOAD
            putExtra(DownloaderService.EXTRA_DOWNLOAD_URL, mockUrl)
            putExtra(DownloaderService.EXTRA_OUTPUT_FILE_PATH, targetFile.absolutePath)
        }

        serviceRule.startService(intent)

        var success = false
        val maxWaitTimeMillis = 10000L // 10 seconds timeout
        var waitedTimeMillis = 0L
        val pollIntervalMillis = 500L

        while (waitedTimeMillis < maxWaitTimeMillis) {
            if (targetFile.exists() && targetFile.length() > 0) {
                delay(1000)
                success = true
                break
            }
            delay(pollIntervalMillis)
            waitedTimeMillis += pollIntervalMillis
        }

        if (!success) {
            throw TimeoutException("Timeout waiting for file to be downloaded or service to finish.")
        }

        assert(targetFile.exists())
        assert(targetFile.readText() == fileContent)
    }
}