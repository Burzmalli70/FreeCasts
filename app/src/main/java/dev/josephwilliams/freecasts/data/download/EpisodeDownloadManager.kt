package dev.josephwilliams.freecasts.data.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dev.josephwilliams.freecasts.data.local.dao.DownloadDao
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.entity.Download
import dev.josephwilliams.freecasts.data.local.entity.DownloadStatus as DbDownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Manages episode downloads using Android's DownloadManager.
 * 
 * This class handles:
 * - Starting downloads via Android DownloadManager
 * - Queueing downloads when max concurrent limit is reached
 * - Tracking download progress
 * - Persisting download state to Room database
 * - Exposing observable state for UI consumption
 * 
 * Inject via Koin: `val downloadManager: EpisodeDownloadManager by inject()`
 */
class EpisodeDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val episodeDao: EpisodeDao,
    private val resumeFavoriteDownloads: suspend () -> Unit = {},
    private val onFavoriteDownloadsMayBeComplete: suspend () -> Unit = {}
) : EpisodeDownloadEnqueuer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val androidDownloadManager: DownloadManager? = context.getSystemService()
    
    // OkHttp client configured to follow redirects for URL resolution
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Maps Android DownloadManager ID to our episode ID
    private val downloadIdToEpisodeId = mutableMapOf<Long, Long>()
    
    // Maps episode ID to Android DownloadManager ID
    private val episodeIdToDownloadId = mutableMapOf<Long, Long>()
    
    private val _state = MutableStateFlow(DownloadManagerState())
    
    /**
     * Observable state of the download manager.
     * Subscribe to this in ViewModels to display download progress and queue status.
     */
    val state: StateFlow<DownloadManagerState> = _state.asStateFlow()
    
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != -1L) {
                    scope.launch {
                        handleDownloadComplete(downloadId)
                    }
                }
            }
        }
    }
    
    private var isReceiverRegistered = false
    private var progressPollingJob: kotlinx.coroutines.Job? = null
    
    init {
        scope.launch {
            initialize()
        }
    }
    
    private suspend fun initialize() {
        // Register broadcast receiver for download completion
        registerReceiver()
        
        // Restore any pending/downloading states from database
        restoreState()
        
        // Start progress polling
        startProgressPolling()
    }
    
    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                ContextCompat.registerReceiver(
                    context,
                    downloadCompleteReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
            isReceiverRegistered = true
        }
    }
    
    private suspend fun restoreState() {
        val pendingDownloads = downloadDao.getPendingAndDownloading()
        val downloadManager = androidDownloadManager

        for (download in pendingDownloads) {
            val request = buildDownloadRequest(download.episodeId) ?: continue

            if (download.status == DbDownloadStatus.DOWNLOADING &&
                download.androidDownloadManagerId != null &&
                downloadManager != null
            ) {
                val androidDownloadId = download.androidDownloadManagerId
                val query = DownloadManager.Query().setFilterById(androidDownloadId)
                val cursor = downloadManager.query(query)
                val isStillActive = cursor?.use {
                    if (it.moveToFirst()) {
                        val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = it.getInt(statusIndex)
                        status == DownloadManager.STATUS_RUNNING ||
                            status == DownloadManager.STATUS_PENDING ||
                            status == DownloadManager.STATUS_PAUSED
                    } else {
                        false
                    }
                } ?: false

                if (isStillActive) {
                    downloadIdToEpisodeId[androidDownloadId] = download.episodeId
                    episodeIdToDownloadId[download.episodeId] = androidDownloadId
                    pendingRequests[download.episodeId] = request
                    _state.update { state ->
                        state.copy(
                            activeDownloads = state.activeDownloads + DownloadState(
                                episodeId = request.episodeId,
                                episodeName = request.episodeName,
                                podcastName = request.podcastName,
                                status = DownloadStatus.DOWNLOADING,
                                progressPercent = download.progressPercent,
                                downloadedBytes = download.downloadedBytes,
                                downloadManagerId = androidDownloadId
                            )
                        )
                    }
                    continue
                }

                downloadDao.updateStatus(download.id, DbDownloadStatus.PENDING)
            }

            pendingRequests[download.episodeId] = request
            _state.update { state ->
                if (state.queuedDownloads.any { it.episodeId == request.episodeId } ||
                    state.activeDownloads.any { it.episodeId == request.episodeId }
                ) {
                    state
                } else {
                    state.copy(
                        queuedDownloads = state.queuedDownloads + DownloadState(
                            episodeId = request.episodeId,
                            episodeName = request.episodeName,
                            podcastName = request.podcastName,
                            status = DownloadStatus.QUEUED
                        )
                    )
                }
            }
        }

        processQueue()
        resumeFavoriteDownloads()
    }

    private suspend fun buildDownloadRequest(episodeId: Long): DownloadRequest? {
        val episodeWithPodcast = episodeDao.getEpisodeWithPodcast(episodeId) ?: return null
        if (episodeWithPodcast.episode.audioUrl.isBlank()) return null

        return DownloadRequest(
            episodeId = episodeWithPodcast.episode.id,
            episodeName = episodeWithPodcast.episode.title,
            podcastName = episodeWithPodcast.podcast.title,
            downloadUrl = episodeWithPodcast.episode.audioUrl,
            mimeType = episodeWithPodcast.episode.mimeType
        )
    }
    
    /**
     * Enqueue an episode for download.
     * 
     * If the maximum number of concurrent downloads is already in progress,
     * the download will be added to a queue and started automatically when
     * a slot becomes available.
     * 
     * @param request The download request containing episode information
     * @return true if the download was started or queued successfully
     */
    override suspend fun enqueueDownload(request: DownloadRequest): Boolean {
        // Check if already downloading or queued
        val existingDownload = downloadDao.getByEpisodeId(request.episodeId)
        if (existingDownload != null) {
            when (existingDownload.status) {
                DbDownloadStatus.PENDING, DbDownloadStatus.DOWNLOADING -> {
                    // Already in progress or queued
                    return false
                }
                DbDownloadStatus.COMPLETED -> {
                    // Already downloaded
                    return false
                }
                else -> {
                    // Failed or cancelled, allow re-download
                    downloadDao.deleteByEpisodeId(request.episodeId)
                }
            }
        }
        
        val currentState = _state.value
        val canStartImmediately = currentState.activeDownloads.size < currentState.maxConcurrentDownloads
        
        // Create database entry
        val download = Download(
            episodeId = request.episodeId,
            status = if (canStartImmediately) DbDownloadStatus.DOWNLOADING else DbDownloadStatus.PENDING
        )
        val downloadDbId = downloadDao.insert(download)
        
        val downloadState = DownloadState(
            episodeId = request.episodeId,
            episodeName = request.episodeName,
            podcastName = request.podcastName,
            status = if (canStartImmediately) DownloadStatus.DOWNLOADING else DownloadStatus.QUEUED
        )
        
        if (canStartImmediately) {
            // Start download immediately
            startDownload(request, downloadDbId)
            _state.update { state ->
                state.copy(activeDownloads = state.activeDownloads + downloadState)
            }
        } else {
            // Add to queue
            _state.update { state ->
                state.copy(queuedDownloads = state.queuedDownloads + downloadState)
            }
            // Store request for later
            pendingRequests[request.episodeId] = request
        }
        
        return true
    }
    
    // Store pending requests that are queued
    private val pendingRequests = mutableMapOf<Long, DownloadRequest>()
    
    private suspend fun startDownload(request: DownloadRequest, downloadDbId: Long) {
        val downloadManager = androidDownloadManager ?: return
        
        // Resolve redirects to get the final URL
        // This prevents "too many redirects" errors from DownloadManager
        val resolvedUrl = resolveRedirects(request.downloadUrl) ?: request.downloadUrl
        
        // Create download directory if needed
        val podcastDir = sanitizeFileName(request.podcastName)
        val fileName = sanitizeFileName(request.episodeName) + getExtensionFromUrl(resolvedUrl)
        
        val downloadRequest = DownloadManager.Request(resolvedUrl.toUri()).apply {
            setTitle(request.episodeName)
            setDescription("Downloading from ${request.podcastName}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            
            // Set destination
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_PODCASTS,
                "$podcastDir/$fileName"
            )
            
            // Set network constraints based on settings
            val allowedNetworkTypes = if (_state.value.allowMeteredDownloads) {
                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
            } else {
                DownloadManager.Request.NETWORK_WIFI
            }
            setAllowedNetworkTypes(allowedNetworkTypes)
            
            // Set MIME type if provided
            request.mimeType?.let { setMimeType(it) }
        }
        
        val downloadId = downloadManager.enqueue(downloadRequest)
        
        // Track the mapping
        downloadIdToEpisodeId[downloadId] = request.episodeId
        episodeIdToDownloadId[request.episodeId] = downloadId
        
        // Update state with download manager ID
        _state.update { state ->
            state.copy(
                activeDownloads = state.activeDownloads.map { download ->
                    if (download.episodeId == request.episodeId) {
                        download.copy(downloadManagerId = downloadId)
                    } else {
                        download
                    }
                }
            )
        }
        
        downloadDao.markAsDownloading(downloadDbId)
        downloadDao.updateAndroidDownloadManagerId(downloadDbId, downloadId)
    }
    
    private suspend fun handleDownloadComplete(downloadId: Long) {
        val episodeId = downloadIdToEpisodeId[downloadId] ?: return
        val downloadManager = androidDownloadManager ?: return
        
        // Query download status
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        cursor?.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = it.getInt(statusIndex)
                
                val localUriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUri = it.getString(localUriIndex)
                
                val reasonIndex = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                val reason = it.getInt(reasonIndex)
                
                val download = downloadDao.getByEpisodeId(episodeId)
                
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        download?.let { dl ->
                            downloadDao.markAsCompleted(dl.id)
                            localUri?.let { uri ->
                                downloadDao.updateLocalFilePath(dl.id, Uri.parse(uri).path ?: uri)
                            }
                        }
                        
                        // Update state
                        _state.update { state ->
                            state.copy(
                                activeDownloads = state.activeDownloads.map { dl ->
                                    if (dl.episodeId == episodeId) {
                                        dl.copy(
                                            status = DownloadStatus.COMPLETED,
                                            progressPercent = 100
                                        )
                                    } else {
                                        dl
                                    }
                                }
                            )
                        }
                        
                        // Remove from active after a delay (so UI can show completion)
                        scope.launch {
                            delay(2000)
                            removeFromActive(episodeId)
                            processQueue()
                            onFavoriteDownloadsMayBeComplete()
                        }
                    }
                    
                    DownloadManager.STATUS_FAILED -> {
                        val errorMessage = getErrorMessage(reason)
                        download?.let { dl ->
                            downloadDao.markAsFailed(dl.id, errorMessage)
                        }
                        
                        _state.update { state ->
                            state.copy(
                                activeDownloads = state.activeDownloads.map { dl ->
                                    if (dl.episodeId == episodeId) {
                                        dl.copy(
                                            status = DownloadStatus.FAILED,
                                            errorMessage = errorMessage
                                        )
                                    } else {
                                        dl
                                    }
                                }
                            )
                        }
                        
                        // Remove from active and process queue
                        scope.launch {
                            delay(3000)
                            removeFromActive(episodeId)
                            processQueue()
                        }
                    }
                }
            }
        }
        
        // Clean up mappings
        downloadIdToEpisodeId.remove(downloadId)
        episodeIdToDownloadId.remove(episodeId)
    }
    
    private fun removeFromActive(episodeId: Long) {
        _state.update { state ->
            state.copy(
                activeDownloads = state.activeDownloads.filterNot { it.episodeId == episodeId }
            )
        }
    }
    
    private suspend fun processQueue() {
        val currentState = _state.value
        val availableSlots = currentState.maxConcurrentDownloads - currentState.activeDownloads.size
        
        if (availableSlots <= 0 || currentState.queuedDownloads.isEmpty()) return
        
        // Take items from queue and start them
        val toStart = currentState.queuedDownloads.take(availableSlots)
        
        for (queuedDownload in toStart) {
            val request = pendingRequests.remove(queuedDownload.episodeId) ?: continue
            
            // Move from queued to active
            _state.update { state ->
                state.copy(
                    queuedDownloads = state.queuedDownloads.filterNot { it.episodeId == queuedDownload.episodeId },
                    activeDownloads = state.activeDownloads + queuedDownload.copy(status = DownloadStatus.DOWNLOADING)
                )
            }
            
            // Get or create database entry
            val download = downloadDao.getByEpisodeId(request.episodeId)
            val downloadDbId = download?.id ?: downloadDao.insert(
                Download(episodeId = request.episodeId, status = DbDownloadStatus.DOWNLOADING)
            )
            
            startDownload(request, downloadDbId)
        }
    }
    
    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (true) {
                delay(1000) // Poll every second
                updateProgress()
            }
        }
    }
    
    private suspend fun updateProgress() {
        val downloadManager = androidDownloadManager ?: return
        val currentState = _state.value
        
        if (currentState.activeDownloads.isEmpty()) return
        
        val downloadIds = currentState.activeDownloads.mapNotNull { it.downloadManagerId }
        if (downloadIds.isEmpty()) return
        
        val query = DownloadManager.Query().setFilterById(*downloadIds.toLongArray())
        val cursor = downloadManager.query(query)
        
        cursor?.use {
            val updates = mutableMapOf<Long, Pair<Int, Long>>() // downloadId -> (percent, downloadedBytes)
            
            while (it.moveToNext()) {
                val idIndex = it.getColumnIndex(DownloadManager.COLUMN_ID)
                val downloadId = it.getLong(idIndex)
                
                val bytesDownloadedIndex = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesDownloaded = it.getLong(bytesDownloadedIndex)
                
                val bytesTotalIndex = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val bytesTotal = it.getLong(bytesTotalIndex)
                
                val percent = if (bytesTotal > 0) {
                    ((bytesDownloaded * 100) / bytesTotal).toInt()
                } else {
                    0
                }
                
                updates[downloadId] = Pair(percent, bytesDownloaded)
                
                // Also update database
                val episodeId = downloadIdToEpisodeId[downloadId]
                if (episodeId != null) {
                    val download = downloadDao.getByEpisodeId(episodeId)
                    download?.let { dl ->
                        downloadDao.updateProgress(dl.id, percent, bytesDownloaded)
                    }
                }
            }
            
            // Update state
            if (updates.isNotEmpty()) {
                _state.update { state ->
                    state.copy(
                        activeDownloads = state.activeDownloads.map { download ->
                            val update = download.downloadManagerId?.let { updates[it] }
                            if (update != null) {
                                download.copy(
                                    progressPercent = update.first,
                                    downloadedBytes = update.second
                                )
                            } else {
                                download
                            }
                        }
                    )
                }
            }
        }
    }
    
    /**
     * Cancel a download by episode ID.
     * Removes from queue if queued, or cancels the active download.
     */
    suspend fun cancelDownload(episodeId: Long) {
        // Check if it's in the queue
        val isQueued = _state.value.queuedDownloads.any { it.episodeId == episodeId }
        
        if (isQueued) {
            pendingRequests.remove(episodeId)
            _state.update { state ->
                state.copy(
                    queuedDownloads = state.queuedDownloads.filterNot { it.episodeId == episodeId }
                )
            }
        } else {
            // Cancel active download
            val downloadId = episodeIdToDownloadId[episodeId]
            if (downloadId != null) {
                androidDownloadManager?.remove(downloadId)
                downloadIdToEpisodeId.remove(downloadId)
                episodeIdToDownloadId.remove(episodeId)
            }
            
            removeFromActive(episodeId)
        }
        
        // Update database
        val download = downloadDao.getByEpisodeId(episodeId)
        download?.let {
            downloadDao.markAsCancelled(it.id)
        }
        
        // Process queue to fill the slot
        processQueue()
    }
    
    /**
     * Retry a failed download.
     */
    suspend fun retryDownload(episodeId: Long) {
        val download = downloadDao.getByEpisodeId(episodeId)
        if (download?.status != DbDownloadStatus.FAILED) return
        
        // Get the original request info - would need to be stored or fetched
        // For now, mark as pending and let the caller re-enqueue
        downloadDao.deleteByEpisodeId(episodeId)
    }
    
    /**
     * Update the maximum number of concurrent downloads.
     */
    fun setMaxConcurrentDownloads(max: Int) {
        _state.update { it.copy(maxConcurrentDownloads = max.coerceIn(1, 5)) }
        scope.launch {
            processQueue()
        }
    }
    
    /**
     * Update whether downloads are allowed on metered connections.
     */
    fun setAllowMeteredDownloads(allow: Boolean) {
        _state.update { it.copy(allowMeteredDownloads = allow) }
    }
    
    /**
     * Get the current position in the queue for an episode.
     * Returns null if not queued.
     */
    fun getQueuePosition(episodeId: Long): Int? {
        val index = _state.value.queuedDownloads.indexOfFirst { it.episodeId == episodeId }
        return if (index >= 0) index + 1 else null
    }
    
    /**
     * Check if an episode is currently downloading or queued.
     */
    fun isDownloadingOrQueued(episodeId: Long): Boolean {
        val state = _state.value
        return state.activeDownloads.any { it.episodeId == episodeId } ||
                state.queuedDownloads.any { it.episodeId == episodeId }
    }
    
    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(100) // Limit length
    }
    
    private fun getExtensionFromUrl(url: String): String {
        val path = Uri.parse(url).path ?: return ".mp3"
        val lastDot = path.lastIndexOf('.')
        return if (lastDot > 0 && lastDot < path.length - 1) {
            path.substring(lastDot)
        } else {
            ".mp3" // Default extension
        }
    }
    
    /**
     * Resolve redirects to get the final URL.
     * This prevents "too many redirects" errors from DownloadManager.
     * Uses a HEAD request to follow redirects without downloading the full file.
     */
    private suspend fun resolveRedirects(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Use HEAD request to follow redirects without downloading content
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    // The final URL after all redirects
                    val finalUrl = response.request.url.toString()
                    Log.d("EpisodeDownloadManager", "Resolved URL: $url -> $finalUrl")
                    finalUrl
                }
            } catch (e: Exception) {
                Log.e("EpisodeDownloadManager", "Failed to resolve redirects for $url", e)
                // Fall back to original URL if resolution fails
                null
            }
        }
    }
    
    private fun getErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP error"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Download failed (error code: $reason)"
        }
    }
    
    /**
     * Clean up resources when the manager is no longer needed.
     * Called when the application is destroyed.
     */
    fun cleanup() {
        progressPollingJob?.cancel()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(downloadCompleteReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
        
        // Shut down OkHttp client
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
