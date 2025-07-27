package dev.josephwilliams.freecasts.downloader


import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L && context != null) {
                Log.d("DownloadReceiver", "Download $downloadId completed.")
                // You can now query the status of this downloadId
                // Or, ideally, your ViewModel/Repository is already aware and can react.
                // For simplicity, you might trigger a check or update here.
                // Consider using a GlobalScope for a quick operation or passing to a Foreground Service/WorkManager
                // if more significant processing is needed.
                // A better approach would be for your ViewModel/Repository to observe a Flow/LiveData
                // that this receiver (or a service it communicates with) updates.

                // Example: Trigger an update via a repository or event bus
                // This is a simplified example. In a real app, you'd likely use dependency injection
                // or a more structured way to communicate this event back to your active components.
                val systemDownloader = SystemDownloader(context.applicationContext)
                CoroutineScope(Dispatchers.IO).launch { // Use an appropriate scope
                    val statusInfo = systemDownloader.getDownloadStatus(downloadId)
                    Log.d("DownloadReceiver", "Status for $downloadId: $statusInfo")
                    // TODO: Update your Room database here with the final status and local file URI
                    // e.g., updateEpisodeDownloadPath(downloadId, statusInfo?.localUri.toString())
                }
            }
        }
        // You can also listen for DownloadManager.ACTION_NOTIFICATION_CLICKED
        // if (intent?.action == DownloadManager.ACTION_NOTIFICATION_CLICKED) { ... }
    }
}