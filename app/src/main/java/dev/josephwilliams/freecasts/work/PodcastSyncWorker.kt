package dev.josephwilliams.freecasts.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.josephwilliams.freecasts.MainActivity
import dev.josephwilliams.freecasts.R
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.repository.PodcastRepository

/**
 * Worker that periodically checks all subscribed podcasts for new episodes.
 * Runs approximately every hour in the background, even when the app is closed.
 * Also handles auto-adding new episodes to playlists that have auto-add enabled.
 */
class PodcastSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val podcastRepository: PodcastRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PodcastSyncWorker"
        const val WORK_NAME = "podcast_sync_work"
        private const val CHANNEL_ID = "new_episodes_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting podcast sync...")
        
        return try {
            val subscribedPodcasts = podcastDao.getSubscribed()
            
            if (subscribedPodcasts.isEmpty()) {
                Log.d(TAG, "No subscribed podcasts to sync")
                return Result.success()
            }
            
            Log.d(TAG, "Syncing ${subscribedPodcasts.size} podcasts")
            
            var totalNewEpisodes = 0
            val podcastsWithNewEpisodes = mutableListOf<String>()
            
            for (podcast in subscribedPodcasts) {
                try {
                    // Get current episode count before refresh
                    val beforeCount = episodeDao.getEpisodeCountForPodcast(podcast.id)
                    
                    // Refresh the podcast
                    val result = podcastRepository.refreshPodcast(podcast.id)
                    
                    if (result.isSuccess) {
                        // Get new episode count
                        val afterCount = episodeDao.getEpisodeCountForPodcast(podcast.id)
                        val newEpisodes = afterCount - beforeCount
                        
                        if (newEpisodes > 0) {
                            totalNewEpisodes += newEpisodes
                            podcastsWithNewEpisodes.add(podcast.title)
                            Log.d(TAG, "Found $newEpisodes new episodes for ${podcast.title}")
                        }
                    } else {
                        Log.w(TAG, "Failed to refresh ${podcast.title}: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing ${podcast.title}", e)
                }
            }
            
            // Show notification if new episodes were found
            if (totalNewEpisodes > 0) {
                showNewEpisodesNotification(totalNewEpisodes, podcastsWithNewEpisodes)
            }
            
            Log.d(TAG, "Podcast sync completed. Found $totalNewEpisodes new episodes across ${podcastsWithNewEpisodes.size} podcasts")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Podcast sync failed", e)
            Result.retry()
        }
    }
    
    private fun showNewEpisodesNotification(
        newEpisodeCount: Int,
        podcastNames: List<String>
    ) {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Notification permission not granted, skipping notification")
                return
            }
        }
        
        createNotificationChannel()
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val title = if (newEpisodeCount == 1) {
            "1 New Episode"
        } else {
            "$newEpisodeCount New Episodes"
        }
        
        val body = when {
            podcastNames.size == 1 -> "from ${podcastNames.first()}"
            podcastNames.size == 2 -> "from ${podcastNames[0]} and ${podcastNames[1]}"
            podcastNames.size > 2 -> "from ${podcastNames[0]}, ${podcastNames[1]}, and ${podcastNames.size - 2} more"
            else -> "Check your podcasts for new content"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        val name = "New Episodes"
        val descriptionText = "Notifications for new podcast episodes"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
