package dev.josephwilliams.filedownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class DownloadService : Service() {

    private val persistenceManager by lazy {
        PersistenceManager()
    }

    private val fileDownloader by lazy {
        FileDownloader(applicationContext, persistenceManager)
    }

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val NOTIFICATION_CHANNEL_ID = "download_channel"
    private val NOTIFICATION_ID = 1

    private val activeDownloads = ConcurrentHashMap<String, Job>()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let { downloadId ->
                    startDownload(downloadId)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let { downloadId ->
                    pauseDownload(downloadId)
                }
            }
            ACTION_RESUME_DOWNLOAD -> {
                intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let { downloadId ->
                    resumeDownload(downloadId)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                intent.getStringExtra(EXTRA_DOWNLOAD_ID)?.let { downloadId ->
                    cancelDownload(downloadId)
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        downloadScope.cancel()
        super.onDestroy()
    }

    fun startDownload(downloadId: String) {
        if (activeDownloads.containsKey(downloadId)) {
            return // Already downloading
        }

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification("Downloading..."))

        downloadScope.launch {
            val downloadInfo = persistenceManager.getDownloadInfo(downloadId)
                ?: throw IllegalArgumentException("Download not found")
            try {

                // Parse headers from JSON
                val headers: Map<String, String> = downloadInfo.headers?.let {
                    Json.decodeFromString(it)
                } ?: emptyMap()

                // Start the download
                val job = downloadScope.launch {
                    fileDownloader.download(downloadInfo, headers)
                }

                activeDownloads[downloadId] = job

                // Wait for download to complete
                job.join()

                // If no more active downloads, stop foreground service
                if (activeDownloads.isEmpty()) {
                    stopForeground(true)
                    stopSelf()
                }

            } catch (e: Exception) {
                Log.e("DownloadService", "Error downloading file", e)

                // Update download info persistence with error
            } finally {
                activeDownloads.remove(downloadId)
            }
        }
    }

    fun pauseDownload(downloadId: String) {
        fileDownloader.pause(downloadId)
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)

        // If no more active downloads, stop foreground service
        if (activeDownloads.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    fun resumeDownload(downloadId: String) {
        startDownload(downloadId)
    }

    fun cancelDownload(downloadId: String) {
        fileDownloader.cancel(downloadId)
        activeDownloads[downloadId]?.cancel()
        activeDownloads.remove(downloadId)

        // If no more active downloads, stop foreground service
        if (activeDownloads.isEmpty()) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, Class.forName("MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download Manager")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.example.action.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.example.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.example.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.action.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun startDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun resumeDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }
}