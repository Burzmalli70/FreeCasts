package dev.josephwilliams.freecasts.downloader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
@LargeTest
class SystemDownloaderTest {

    private lateinit var context: Context
    private lateinit var systemDownloader: SystemDownloader
    private lateinit var mockWebServer: MockWebServer
    private lateinit var downloadDir: File

    private val downloadCompletionChannel = Channel<Long>(Channel.CONFLATED)

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != -1L) {
                    println("TestReceiver: Download $downloadId complete.")

                    downloadCompletionChannel.trySend(downloadId)
                }
            }
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        systemDownloader = SystemDownloader(context)
        mockWebServer = MockWebServer()
        mockWebServer.start()

        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "test_downloads_${UUID.randomUUID()}")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        context.unregisterReceiver(downloadReceiver)
        downloadCompletionChannel.close()

        downloadDir.deleteRecursively()
    }

    @Test
    fun startDownload_successfulDownload_completesAndFileExists() = runBlocking {
        val fileName = "test_audio.mp3"
        val fileContent = "This is some test audio content."
        val mockUrl = mockWebServer.url("/episodes/$fileName").toString()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fileContent)
                .setHeader("Content-Type", "audio/mpeg")
        )

        val expectedAppSpecificFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS),
            fileName
        )

        if (expectedAppSpecificFile.exists()) expectedAppSpecificFile.delete()

        val downloadId = systemDownloader.startDownload(
            url = mockUrl,
            title = "Test Episode",
            description = "Downloading a test episode",
            destinationFileName = fileName,
            subfolder = "test_pod"
        ) ?: throw AssertionError("downloadId should not be null")

        var receivedDownloadId: Long? = null

        withTimeoutOrNull(20000) {
            receivedDownloadId = downloadCompletionChannel.receive()

            assertThat(receivedDownloadId).isNotNull()
            assertThat(receivedDownloadId).isEqualTo(downloadId)
        }

        assertThat(downloadId).isGreaterThan(0L)

        println("Test: Download started with ID: $downloadId for $mockUrl, expecting file: ${expectedAppSpecificFile.absolutePath}")

        val statusInfo = systemDownloader.getDownloadStatus(downloadId) ?: throw AssertionError("statusInfo should not be null")
        println("Test: StatusInfo for $downloadId: $statusInfo")

        assertThat(statusInfo.isSuccessful).isTrue()

        statusInfo.localUri ?: throw AssertionError("localUri should not be null")

        val downloadedFileUri = statusInfo.localUri
        val contentResolver = context.contentResolver
        var bytesRead = -1
        val buffer = ByteArray(fileContent.toByteArray().size + 10)
        contentResolver.openInputStream(downloadedFileUri).use { inputStream ->
            assertThat(inputStream).isNotNull()
            bytesRead = inputStream!!.read(buffer)
        }
        assertThat(bytesRead).isEqualTo(fileContent.toByteArray().size)
        assertThat(String(buffer, 0, bytesRead)).isEqualTo(fileContent)

        assertThat(expectedAppSpecificFile.exists()).isTrue()
        assertThat(expectedAppSpecificFile.readText()).isEqualTo(fileContent)

        if (expectedAppSpecificFile.exists()) {
            expectedAppSpecificFile.delete()
        }
    }

    @Test
    fun startDownload_serverError404_downloadFails() = runBlocking {
        val fileName = "non_existent_episode.mp3"
        val mockUrl = mockWebServer.url("/episodes/$fileName").toString()

        mockWebServer.enqueue(
            MockResponse().setResponseCode(404)
        )

        val downloadId = systemDownloader.startDownload(
            url = mockUrl,
            title = "Non Existent Episode",
            description = "Attempting to download a non-existent episode",
            destinationFileName = fileName,
            subfolder = "test_pod"
        )

        downloadId ?: throw AssertionError("downloadId should not be null")

        var receivedDownloadId: Long? = null
        withTimeoutOrNull(15000) {
            receivedDownloadId = downloadCompletionChannel.receive()

            assertThat(receivedDownloadId).isEqualTo(downloadId)
        }

        val statusInfo = systemDownloader.getDownloadStatus(downloadId) ?: throw AssertionError("statusInfo should not be null")
        assertThat(statusInfo.isFailed).isTrue()

        println("Test 404: Status: ${statusInfo.status}, Reason: ${statusInfo.reason}")

        val expectedAppSpecificFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS),
            fileName
        )
        assertThat(expectedAppSpecificFile.exists()).isFalse()
    }
}