package dev.josephwilliams.freecasts.data.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.josephwilliams.freecasts.MainActivity
import dev.josephwilliams.freecasts.R
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service for audio playback using Media3.
 * Supports background playback, media controls notification, and lock screen controls.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    
    companion object {
        const val CUSTOM_COMMAND_SKIP_BACK = "SKIP_BACK_30"
        const val CUSTOM_COMMAND_SKIP_FORWARD = "SKIP_FORWARD_30"
        private const val SKIP_DURATION_MS = 30_000L
        
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_PODCAST_ID = "podcast_id"
        const val EXTRA_LOCAL_FILE_PATH = "local_file_path"
    }
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    private val episodeDao: EpisodeDao by inject()
    
    override fun onCreate() {
        super.onCreate()
        
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .build()
        
        player?.addListener(PlayerListener())
        
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .setCustomLayout(buildCustomLayout())
            .build()
    }
    
    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        return ImmutableList.of(
            CommandButton.Builder()
                .setDisplayName("Skip back 30s")
                .setIconResId(R.drawable.ic_replay_30)
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_BACK, Bundle.EMPTY))
                .build(),
            CommandButton.Builder()
                .setDisplayName("Skip forward 30s")
                .setIconResId(R.drawable.ic_forward_30)
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SKIP_FORWARD, Bundle.EMPTY))
                .build()
        )
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }
    
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
    
    private inner class MediaSessionCallback : MediaSession.Callback {
        
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SKIP_BACK, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_SKIP_FORWARD, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                availableCommands,
                connectionResult.availablePlayerCommands
            )
        }
        
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_COMMAND_SKIP_BACK -> {
                    val newPosition = (player?.currentPosition ?: 0) - SKIP_DURATION_MS
                    player?.seekTo(maxOf(0, newPosition))
                }
                CUSTOM_COMMAND_SKIP_FORWARD -> {
                    val duration = player?.duration ?: 0
                    val newPosition = (player?.currentPosition ?: 0) + SKIP_DURATION_MS
                    player?.seekTo(minOf(duration, newPosition))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val updatedMediaItems = mediaItems.map { mediaItem ->
                mediaItem.buildUpon()
                    .setUri(mediaItem.requestMetadata.mediaUri)
                    .build()
            }
            return Futures.immediateFuture(updatedMediaItems)
        }
    }
    
    private inner class PlayerListener : Player.Listener {
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    savePlaybackPosition(0)
                    markEpisodeAsPlayed()
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                savePlaybackPosition(player?.currentPosition ?: 0)
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                savePlaybackPosition(0)
                markEpisodeAsPlayed()
            }
        }
    }
    
    private fun savePlaybackPosition(position: Long) {
        val currentMediaItem = player?.currentMediaItem ?: return
        val episodeId = currentMediaItem.mediaMetadata.extras?.getLong(EXTRA_EPISODE_ID, -1L) ?: -1L
        if (episodeId > 0) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                episodeDao.setPlaybackPosition(episodeId, position)
                episodeDao.setLastPlayedAt(episodeId, System.currentTimeMillis())
            }
        }
    }
    
    private fun markEpisodeAsPlayed() {
        val currentMediaItem = player?.currentMediaItem ?: return
        val episodeId = currentMediaItem.mediaMetadata.extras?.getLong(EXTRA_EPISODE_ID, -1L) ?: -1L
        if (episodeId > 0) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                episodeDao.markAsPlayed(episodeId)
                episodeDao.incrementListenCount(episodeId)
            }
        }
    }
}

/**
 * Create a MediaItem from a PlayingEpisode.
 */
fun PlayingEpisode.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putLong(PlaybackService.EXTRA_EPISODE_ID, episodeId)
        putLong(PlaybackService.EXTRA_PODCAST_ID, podcastId)
        localFilePath?.let { putString(PlaybackService.EXTRA_LOCAL_FILE_PATH, it) }
    }
    
    val audioSource = localFilePath?.let { path ->
        val file = java.io.File(path)
        if (file.exists()) path else audioUrl
    } ?: audioUrl
    
    return MediaItem.Builder()
        .setMediaId(episodeId.toString())
        .setUri(audioSource)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(android.net.Uri.parse(audioSource))
                .build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episodeTitle)
                .setArtist(podcastName)
                .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                .setExtras(extras)
                .build()
        )
        .build()
}

/**
 * Extract episode info from a MediaItem.
 */
fun MediaItem.toPlayingEpisode(): PlayingEpisode? {
    val extras = mediaMetadata.extras ?: return null
    val episodeId = extras.getLong(PlaybackService.EXTRA_EPISODE_ID, -1L)
    if (episodeId < 0) return null
    
    return PlayingEpisode(
        episodeId = episodeId,
        episodeTitle = mediaMetadata.title?.toString() ?: "",
        podcastId = extras.getLong(PlaybackService.EXTRA_PODCAST_ID, 0L),
        podcastName = mediaMetadata.artist?.toString() ?: "",
        artworkUrl = mediaMetadata.artworkUri?.toString(),
        audioUrl = requestMetadata.mediaUri?.toString() ?: "",
        localFilePath = extras.getString(PlaybackService.EXTRA_LOCAL_FILE_PATH)
    )
}
