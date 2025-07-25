package dev.joewilliams.downloadmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class DownloaderService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationManager: NotificationManager
    private val ktorClient = HttpClient(Android)

    companion object {
        const val ACTION_START_DOWNLOAD = "dev.joewilliams.downloadmanager.ACTION_START_DOWNLOAD"
        const val EXTRA_DOWNLOAD_URL = "DOWNLOAD_URL"
        const val EXTRA_OUTPUT_FILE_PATH = "OUTPUT_FILE_PATH"
        private const val NOTIFICATION_ID = 1234
        private const val CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_DOWNLOAD_URL)
            val outputFilePath = intent.getStringExtra(EXTRA_OUTPUT_FILE_PATH)

            if (url != null && outputFilePath != null) {
                val notification = createNotification("Download starting...")
                startForeground(NOTIFICATION_ID, notification)
                serviceScope.launch {
                    downloadFile(url, File(outputFilePath))
                }
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun downloadFile(url: String, outputFile: File) {
        try {
            ktorClient.prepareGet(url).execute { httpResponse ->
                if (httpResponse.status.isSuccess()) {
                    val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                    val totalBytes = httpResponse.contentLength() ?: -1L
                    var bytesCopied = 0L

                    outputFile.outputStream().use { output ->
                        while (!channel.isClosedForRead) {
                            val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                            while (!packet.isEmpty) {
                                val bytes = packet.readBytes()
                                output.write(bytes)
                                bytesCopied += bytes.size
                                val progress = if (totalBytes > 0) (bytesCopied * 100 / totalBytes).toInt() else -1
                                updateNotification("Downloading...", progress)
                            }
                        }
                    }
                    updateNotification("Download complete", 100)
                } else {
                    updateNotification("Download failed: ${httpResponse.status}", -1)
                }
            }
        } catch (e: Exception) {
            updateNotification("Download error: ${e.localizedMessage}", -1)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Download Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(contentText: String, progress: Int = -1): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Download")
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)

        if (progress >= 0 && progress <= 100) {
            builder.setProgress(100, progress, false)
        } else if (progress == -1 && contentText.contains("Downloading...")) {
            builder.setProgress(0,0,true)
        }
        return builder.build()
    }

    private fun updateNotification(contentText: String, progress: Int = -1) {
        val notification = createNotification(contentText, progress)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private val binder = DownloaderBinder()

    inner class DownloaderBinder : Binder() {
        fun getService(): DownloaderService = this@DownloaderService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        ktorClient.close()
    }
}