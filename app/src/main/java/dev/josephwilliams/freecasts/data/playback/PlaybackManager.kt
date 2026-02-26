package dev.josephwilliams.freecasts.data.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
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
import java.io.File

/**
 * Manages audio playback using Android's MediaPlayer API.
 * 
 * Features:
 * - Play/pause control
 * - Skip forward/backward by 30 seconds
 * - Position tracking and progress updates
 * - Audio focus handling
 * - Saves playback position to database
 * 
 * Inject via Koin: `val playbackManager: PlaybackManager by inject()`
 */
class PlaybackManager(
    private val context: Context,
    private val episodeDao: EpisodeDao
) {
    companion object {
        private const val TAG = "PlaybackManager"
        private const val SKIP_DURATION_MS = 30_000L
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioManager: AudioManager? = context.getSystemService()
    
    private val _state = MutableStateFlow(PlaybackState())
    
    /**
     * Observable state of playback.
     * Subscribe to this in ViewModels/composables to display playback status.
     */
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    
    private var positionUpdateJob: Job? = null
    private var positionSaveJob: Job? = null
    private var lastSavedPositionMs: Long = 0
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (_state.value.currentEpisode != null && !_state.value.isPlaying) {
                    resume()
                }
            }
        }
    }
    
    /**
     * Start playing an episode.
     * 
     * @param episode The episode to play
     */
    fun play(episode: PlayingEpisode) {
        scope.launch {
            try {
                // Stop any current playback
                stopInternal()
                
                _state.update { it.copy(isBuffering = true, error = null, currentEpisode = episode) }
                
                // Request audio focus
                if (!requestAudioFocus()) {
                    _state.update { it.copy(error = "Could not obtain audio focus", isBuffering = false) }
                    return@launch
                }
                
                // Determine the audio source (local file or URL)
                val audioSource = episode.localFilePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) path else null
                } ?: episode.audioUrl
                
                // Create and configure MediaPlayer
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    
                    setOnPreparedListener { mp ->
                        val duration = mp.duration.toLong()
                        _state.update { 
                            it.copy(
                                isBuffering = false, 
                                durationMs = duration,
                                isPlaying = true
                            ) 
                        }
                        
                        // Seek to saved position if available
                        scope.launch(Dispatchers.IO) {
                            val savedPosition = episodeDao.getPlaybackPosition(episode.episodeId)
                            if (savedPosition > 0 && savedPosition < duration - 5000) {
                                mp.seekTo(savedPosition.toInt())
                                _state.update { it.copy(currentPositionMs = savedPosition) }
                            }
                        }
                        
                        mp.start()
                        startPositionUpdates()
                    }
                    
                    setOnCompletionListener {
                        onPlaybackComplete()
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        _state.update { 
                            it.copy(
                                error = "Playback error (code: $what)", 
                                isPlaying = false,
                                isBuffering = false
                            ) 
                        }
                        true
                    }
                    
                    setOnBufferingUpdateListener { _, percent ->
                        // Could track buffering progress if needed
                    }
                    
                    setDataSource(audioSource)
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback", e)
                _state.update { 
                    it.copy(
                        error = "Failed to play: ${e.message}", 
                        isPlaying = false,
                        isBuffering = false
                    ) 
                }
            }
        }
    }
    
    /**
     * Toggle play/pause state.
     */
    fun togglePlayPause() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }
    
    /**
     * Pause playback.
     */
    fun pause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _state.update { it.copy(isPlaying = false) }
                stopPositionUpdates()
                saveCurrentPosition()
            }
        }
    }
    
    /**
     * Resume playback.
     */
    fun resume() {
        mediaPlayer?.let { mp ->
            if (!mp.isPlaying && _state.value.currentEpisode != null) {
                if (requestAudioFocus()) {
                    mp.start()
                    _state.update { it.copy(isPlaying = true) }
                    startPositionUpdates()
                }
            }
        }
    }
    
    /**
     * Skip forward by 30 seconds.
     */
    fun skipForward() {
        mediaPlayer?.let { mp ->
            val newPosition = (mp.currentPosition + SKIP_DURATION_MS).coerceAtMost(_state.value.durationMs)
            mp.seekTo(newPosition.toInt())
            _state.update { it.copy(currentPositionMs = newPosition) }
        }
    }
    
    /**
     * Skip backward by 30 seconds.
     */
    fun skipBackward() {
        mediaPlayer?.let { mp ->
            val newPosition = (mp.currentPosition - SKIP_DURATION_MS).coerceAtLeast(0)
            mp.seekTo(newPosition.toInt())
            _state.update { it.copy(currentPositionMs = newPosition) }
        }
    }
    
    /**
     * Seek to a specific position.
     * 
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            val validPosition = positionMs.coerceIn(0, _state.value.durationMs)
            mp.seekTo(validPosition.toInt())
            _state.update { it.copy(currentPositionMs = validPosition) }
        }
    }
    
    /**
     * Stop playback and release resources.
     */
    fun stop() {
        scope.launch {
            saveCurrentPosition()
            stopInternal()
            _state.update { PlaybackState() }
        }
    }
    
    private fun stopInternal() {
        stopPositionUpdates()
        abandonAudioFocus()
        
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
                reset()
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping MediaPlayer", e)
            }
        }
        mediaPlayer = null
    }
    
    private fun onPlaybackComplete() {
        scope.launch {
            _state.value.currentEpisode?.let { episode ->
                // Mark as played
                episodeDao.markAsPlayed(episode.episodeId)
                episodeDao.setPlaybackPosition(episode.episodeId, 0)
                episodeDao.incrementListenCount(episode.episodeId)
            }
            
            _state.update { 
                it.copy(
                    isPlaying = false, 
                    currentPositionMs = it.durationMs
                ) 
            }
            stopPositionUpdates()
            abandonAudioFocus()
        }
    }
    
    private fun startPositionUpdates() {
        stopPositionUpdates()
        
        positionUpdateJob = scope.launch {
            while (true) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _state.update { it.copy(currentPositionMs = mp.currentPosition.toLong()) }
                    }
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
        
        positionSaveJob = scope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveCurrentPosition()
            }
        }
    }
    
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
        positionSaveJob?.cancel()
        positionSaveJob = null
    }
    
    private fun saveCurrentPosition() {
        val currentState = _state.value
        val episode = currentState.currentEpisode ?: return
        val position = currentState.currentPositionMs
        
        if (position != lastSavedPositionMs && position > 0) {
            lastSavedPositionMs = position
            scope.launch(Dispatchers.IO) {
                episodeDao.setPlaybackPosition(episode.episodeId, position)
                episodeDao.setLastPlayedAt(episode.episodeId, System.currentTimeMillis())
            }
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            
            am.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }
    
    /**
     * Clean up resources when the manager is no longer needed.
     */
    fun cleanup() {
        stop()
    }
}
