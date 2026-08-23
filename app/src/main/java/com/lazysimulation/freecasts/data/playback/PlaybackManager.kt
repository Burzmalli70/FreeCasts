package com.lazysimulation.freecasts.data.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages audio playback via Media3 MediaController.
 * Connects to PlaybackService for background-capable playback.
 * 
 * Features:
 * - Background playback with notification
 * - Play/pause control
 * - Skip forward/backward using user-configured intervals
 * - Position tracking and progress updates
 * - Queue support for playlist playback
 * - Lock screen and Bluetooth controls
 * 
 * Inject via Koin: `val playbackManager: PlaybackManager by inject()`
 */
class PlaybackManager(
    private val context: Context,
    private val episodeDao: EpisodeDao
) {
    companion object {
        private const val TAG = "PlaybackManager"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val _state = MutableStateFlow(PlaybackState())
    
    /**
     * Observable state of playback.
     * Subscribe to this in ViewModels/composables to display playback status.
     */
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    
    private var positionUpdateJob: Job? = null
    private var isConnected = false
    
    init {
        connect()
    }
    
    /**
     * Connect to the PlaybackService.
     */
    private fun connect() {
        if (isConnected) return
        
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                isConnected = true
                Log.d(TAG, "Connected to PlaybackService")
                
                setupPlayerListener()
                syncStateFromPlayer()
                startPositionUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to PlaybackService", e)
                isConnected = false
            }
        }, MoreExecutors.directExecutor())
    }
    
    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                _state.update { it.copy(isBuffering = isBuffering) }
                
                if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackComplete()
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncStateFromPlayer()
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.update { it.copy(error = error.message ?: "Playback error") }
            }
        })
    }
    
    private fun syncStateFromPlayer() {
        val controller = mediaController ?: return
        
        val currentMediaItem = controller.currentMediaItem
        val currentEpisode = currentMediaItem?.toPlayingEpisode()
        val isRandomFavoriteMode = currentMediaItem?.mediaMetadata?.extras?.getBoolean(
            PlaybackService.EXTRA_RANDOM_FAVORITE_MODE,
            false
        ) ?: false
        
        val queue = mutableListOf<PlayingEpisode>()
        for (i in 0 until controller.mediaItemCount) {
            controller.getMediaItemAt(i).toPlayingEpisode()?.let { queue.add(it) }
        }
        
        _state.update {
            it.copy(
                currentEpisode = currentEpisode,
                isPlaying = controller.isPlaying,
                currentPositionMs = controller.currentPosition,
                durationMs = controller.duration.coerceAtLeast(0),
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                queue = queue,
                currentQueueIndex = controller.currentMediaItemIndex,
                isRandomFavoriteMode = isRandomFavoriteMode
            )
        }
    }
    
    /**
     * Start playing an episode.
     * Clears the current queue.
     * 
     * @param episode The episode to play
     */
    fun play(episode: PlayingEpisode) {
        scope.launch {
            val controller = mediaController
            if (controller == null) {
                Log.e(TAG, "MediaController not connected")
                return@launch
            }
            
            val mediaItem = episode.toMediaItem()
            
            // Restore saved position
            val savedPosition = kotlinx.coroutines.withContext(Dispatchers.IO) {
                episodeDao.getPlaybackPosition(episode.episodeId)
            }
            
            controller.setMediaItem(mediaItem)
            controller.prepare()
            
            if (savedPosition > 0 && savedPosition < (controller.duration - 5000).coerceAtLeast(0)) {
                controller.seekTo(savedPosition)
            }
            
            controller.play()
        }
    }
    
    /**
     * Play a list of episodes as a queue (e.g., from a playlist).
     * Starts playing the episode at startIndex.
     * 
     * @param episodes The list of episodes to play
     * @param startIndex The index of the episode to start playing (default 0)
     */
    fun playQueue(episodes: List<PlayingEpisode>, startIndex: Int = 0) {
        if (episodes.isEmpty()) return
        
        scope.launch {
            val controller = mediaController
            if (controller == null) {
                Log.e(TAG, "MediaController not connected")
                return@launch
            }
            
            val validStartIndex = startIndex.coerceIn(0, episodes.size - 1)
            val mediaItems = episodes.map { it.toMediaItem() }
            
            // Restore saved position for the starting episode
            val startingEpisode = episodes[validStartIndex]
            val savedPosition = kotlinx.coroutines.withContext(Dispatchers.IO) {
                episodeDao.getPlaybackPosition(startingEpisode.episodeId)
            }
            
            controller.setMediaItems(mediaItems, validStartIndex, savedPosition.coerceAtLeast(0))
            controller.prepare()
            controller.play()
        }
    }
    
    /**
     * Skip to the next episode in the queue, or another random favorite when in that mode.
     */
    fun playNext() {
        mediaController?.let { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNext()
            } else if (_state.value.canPlayRandomFavoriteNext) {
                controller.sendCustomCommand(
                    SessionCommand(PlaybackService.CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE, Bundle.EMPTY),
                    Bundle().apply {
                        putBoolean(PlaybackService.ARG_EXCLUDE_CURRENT_EPISODE, true)
                    }
                )
            }
        }
    }
    
    /**
     * Start playing a random favorite episode.
     */
    fun playRandomFavorite() {
        mediaController?.sendCustomCommand(
            SessionCommand(PlaybackService.CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE, Bundle.EMPTY),
            Bundle().apply {
                putBoolean(PlaybackService.ARG_EXCLUDE_CURRENT_EPISODE, false)
            }
        )
    }
    
    /**
     * Skip to the previous episode in the queue.
     * If current position is > 3 seconds, restarts the current episode instead.
     */
    fun playPrevious() {
        mediaController?.let { controller ->
            if (controller.currentPosition > 3000) {
                controller.seekTo(0)
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPrevious()
            }
        }
    }
    
    /**
     * Add an episode to the end of the queue.
     */
    fun addToQueue(episode: PlayingEpisode) {
        mediaController?.addMediaItem(episode.toMediaItem())
        syncStateFromPlayer()
    }
    
    /**
     * Remove an episode from the queue by index.
     */
    fun removeFromQueue(index: Int) {
        mediaController?.let { controller ->
            if (index >= 0 && index < controller.mediaItemCount) {
                controller.removeMediaItem(index)
                syncStateFromPlayer()
            }
        }
    }
    
    /**
     * Clear the queue but keep playing the current episode.
     */
    fun clearQueue() {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            val currentItem = controller.currentMediaItem
            
            if (currentItem != null) {
                controller.clearMediaItems()
                controller.setMediaItem(currentItem)
            } else {
                controller.clearMediaItems()
            }
            syncStateFromPlayer()
        }
    }
    
    /**
     * Toggle play/pause state.
     */
    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }
    
    /**
     * Pause playback.
     */
    fun pause() {
        mediaController?.pause()
    }
    
    /**
     * Resume playback.
     */
    fun resume() {
        mediaController?.play()
    }
    
    /**
     * Skip forward by the configured interval.
     */
    fun skipForward() {
        mediaController?.seekForward()
        scope.launch {
            delay(100)
            syncStateFromPlayer()
        }
    }
    
    /**
     * Skip backward by the configured interval.
     */
    fun skipBackward() {
        mediaController?.seekBack()
        scope.launch {
            delay(100)
            syncStateFromPlayer()
        }
    }
    
    /**
     * Seek to a specific position.
     * 
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _state.update { it.copy(currentPositionMs = positionMs) }
    }
    
    /**
     * Stop playback and release resources.
     */
    fun stop() {
        mediaController?.stop()
        _state.update { PlaybackState() }
    }
    
    private fun handlePlaybackComplete() {
        val controller = mediaController ?: return
        
        if (!controller.hasNextMediaItem()) {
            _state.update { 
                it.copy(
                    isPlaying = false, 
                    currentPositionMs = it.durationMs
                ) 
            }
        }
    }
    
    private fun startPositionUpdates() {
        stopPositionUpdates()
        
        positionUpdateJob = scope.launch {
            while (true) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _state.update { 
                            it.copy(
                                currentPositionMs = controller.currentPosition,
                                durationMs = controller.duration.coerceAtLeast(0)
                            ) 
                        }
                    }
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
    
    /**
     * Clean up resources when the manager is no longer needed.
     */
    fun cleanup() {
        stopPositionUpdates()
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        mediaController = null
        isConnected = false
    }
}
