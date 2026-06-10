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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.josephwilliams.freecasts.MainActivity
import dev.josephwilliams.freecasts.R
import dev.josephwilliams.freecasts.data.local.dao.DownloadDao
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.local.dao.PodcastDao
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Foreground service for audio playback using Media3.
 * Supports background playback, media controls notification, and lock screen controls.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    
    companion object {
        const val CUSTOM_COMMAND_SKIP_BACK = "SKIP_BACK_30"
        const val CUSTOM_COMMAND_SKIP_FORWARD = "SKIP_FORWARD_30"
        const val CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE = "PLAY_RANDOM_FAVORITE"
        private const val SKIP_DURATION_MS = 30_000L
        
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_PODCAST_ID = "podcast_id"
        const val EXTRA_LOCAL_FILE_PATH = "local_file_path"
        const val EXTRA_RANDOM_FAVORITE_MODE = "random_favorite_mode"
        const val ARG_EXCLUDE_CURRENT_EPISODE = "exclude_current_episode"
    }

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    
    private val episodeDao: EpisodeDao by inject()

    private val podcastDao: PodcastDao by inject()

    private val downloadDao: DownloadDao by inject()

    private val userPreferencesRepository: UserPreferencesRepository by inject ()

    private val episodeCompletionHandler: PlaybackEpisodeCompletionHandler by inject()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

        mediaLibrarySession = MediaLibrarySession.Builder(this, player!!, LibraryCallback())
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        player?.let {
            if (!player.playWhenReady) {
                stopSelf()
            }
        }
    }
    
    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        serviceScope.cancel()
        player = null
        super.onDestroy()
    }
    
    private inner class PlayerListener : Player.Listener {
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    episodeCompletionHandler.onPlaybackEnded(
                        scope = serviceScope,
                        hasNextMediaItem = player?.hasNextMediaItem() == true
                    )

                    if (player?.hasNextMediaItem() != true) {
                        playRandomFavorite()
                    }
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                savePlaybackPosition(player?.currentPosition ?: 0)
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            episodeCompletionHandler.onMediaItemTransition(
                scope = serviceScope,
                newMediaItem = mediaItem,
                reason = reason
            )
        }
    }

    fun playRandomFavorite(excludeCurrentEpisode: Boolean = false) {
        serviceScope.launch(Dispatchers.Main) {
            val currentEpisodeId = if (excludeCurrentEpisode) {
                player?.currentMediaItem?.mediaMetadata?.extras?.getLong(EXTRA_EPISODE_ID, -1L)?.takeIf { it > 0 }
            } else {
                null
            }

            if (excludeCurrentEpisode && currentEpisodeId != null) {
                savePlaybackPosition(player?.currentPosition ?: 0)
                withContext(Dispatchers.IO) {
                    episodeDao.incrementReplayPriority(currentEpisodeId)
                }
            }

            val favorites = withContext(Dispatchers.IO) {
                val favoriteId = userPreferencesRepository.randomPodcastId.first()
                if (favoriteId >= 0L) {
                    val podcastFavorites = episodeDao.getFavoriteEpisodesForPodcast(favoriteId)
                    if (podcastFavorites.isNotEmpty()) {
                        val minPriority = podcastFavorites.minOf { it.replayPriority }
                        podcastFavorites.filter { it.replayPriority == minPriority }
                    } else {
                        emptyList()
                    }
                } else {
                    episodeDao.getFavoritesWithLowestReplayPriority()
                }
            }

            if (favorites.isEmpty()) return@launch

            val candidates = if (currentEpisodeId != null) {
                favorites.filter { it.id != currentEpisodeId }
            } else {
                favorites
            }
            val pool = candidates.ifEmpty { favorites }

            val randomEpisode = pool.random()
            val podcastTitle = podcastDao.getById(randomEpisode.podcastId)
            val mediaItem = randomEpisode.toMediaItem(
                podcastTitle = podcastTitle?.title ?: "",
                randomFavoriteMode = true
            )

            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.play()
        }
    }
    
    private fun savePlaybackPosition(position: Long) {
        val currentMediaItem = player?.currentMediaItem ?: return
        val episodeId = currentMediaItem.mediaMetadata.extras?.getLong(EXTRA_EPISODE_ID, -1L) ?: -1L
        if (episodeId > 0) {
            serviceScope.launch(Dispatchers.IO) {
                episodeDao.setPlaybackPosition(episodeId, position)
                episodeDao.setLastPlayedAt(episodeId, System.currentTimeMillis())
            }
        }
    }
    
    private fun createBrowsableItem(id: String, title: String, iconRes: Int): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SKIP_BACK, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_SKIP_FORWARD, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                availableCommands,
                connectionResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle
        ): ListenableFuture<androidx.media3.session.SessionResult> {
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
                CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE -> {
                    val excludeCurrent = args.getBoolean(ARG_EXCLUDE_CURRENT_EPISODE, true)
                    playRandomFavorite(excludeCurrentEpisode = excludeCurrent)
                }
            }
            return Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }

        // Root of the menu (e.g., "Subscriptions", "Downloads")
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("node_root")
                .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).build())
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        // When a user clicks "Subscriptions", fetch them from Room
        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {

            // Note: You should launch a coroutine to fetch from your EpisodeDao/PodcastDao
            // For Android Auto, you'd map your Room Entities to MediaItems here
            val items = mutableListOf<MediaItem>()

            if (parentId == "node_root") {
                items.add(createBrowsableItem("node_subscriptions", "Subscriptions", R.drawable.ic_subscriptions))
                items.add(createBrowsableItem("node_downloads", "Downloads", R.drawable.ic_download))
            }

            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
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

fun Episode.toMediaItem(
    podcastTitle: String,
    randomFavoriteMode: Boolean = false
): MediaItem {
    val extras = Bundle().apply {
        putLong(PlaybackService.EXTRA_EPISODE_ID, id)
        putLong(PlaybackService.EXTRA_PODCAST_ID, podcastId)
        if (randomFavoriteMode) {
            putBoolean(PlaybackService.EXTRA_RANDOM_FAVORITE_MODE, true)
        }
    }

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(podcastTitle)
                .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                .setExtras(extras)
                .build()
        )
        .build()
}
