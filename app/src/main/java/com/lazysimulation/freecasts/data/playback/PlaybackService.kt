package com.lazysimulation.freecasts.data.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.lazysimulation.freecasts.MainActivity
import com.lazysimulation.freecasts.data.local.dao.DownloadDao
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.dao.PodcastDao
import com.lazysimulation.freecasts.data.local.entity.DownloadStatus
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.playback.auto.AutoMediaBrowser
import com.lazysimulation.freecasts.data.playback.auto.AutoMediaIds
import com.lazysimulation.freecasts.data.playback.auto.PackageValidator
import com.lazysimulation.freecasts.data.preferences.UserPreferencesRepository
import android.view.KeyEvent
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
        private const val TAG = "PlaybackService"

        const val CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE = "PLAY_RANDOM_FAVORITE"
        
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_PODCAST_ID = "podcast_id"
        const val EXTRA_LOCAL_FILE_PATH = "local_file_path"
        const val EXTRA_RANDOM_FAVORITE_MODE = "random_favorite_mode"
        const val ARG_EXCLUDE_CURRENT_EPISODE = "exclude_current_episode"
    }

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var exoPlayer: ExoPlayer? = null
    private var player: Player? = null
    private var externalControlsPlayer: ExternalControlsPlayer? = null
    
    private val episodeDao: EpisodeDao by inject()

    private val podcastDao: PodcastDao by inject()

    private val downloadDao: DownloadDao by inject()

    private val userPreferencesRepository: UserPreferencesRepository by inject ()

    private val episodeCompletionHandler: PlaybackEpisodeCompletionHandler by inject()

    private val autoMediaBrowser: AutoMediaBrowser by inject()

    private val packageValidator: PackageValidator by inject()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var skipForwardDurationMs = 30_000L
    private var skipBackwardDurationMs = 30_000L
    private var externalPrevNextUsesSkipIntervals = false

    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .setSeekBackIncrementMs(skipBackwardDurationMs)
            .setSeekForwardIncrementMs(skipForwardDurationMs)
            .build()
        externalControlsPlayer = ExternalControlsPlayer(exoPlayer!!)
        player = externalControlsPlayer

        serviceScope.launch {
            userPreferencesRepository.userPreferences.collect { preferences ->
                skipForwardDurationMs = preferences.skipForwardIntervalSeconds * 1000L
                skipBackwardDurationMs = preferences.skipBackwardIntervalSeconds * 1000L
                externalPrevNextUsesSkipIntervals = preferences.externalPrevNextUsesSkipIntervals
                applyExternalPlaybackControls()
            }
        }

        exoPlayer?.addListener(PlayerListener())
        
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
        applyExternalPlaybackControls()
    }

    private fun currentExternalControlButtons(): List<CommandButton> {
        return ExternalPlaybackControls.mediaButtonPreferences(
            useSkipIntervals = externalPrevNextUsesSkipIntervals,
            skipBackwardDurationMs = skipBackwardDurationMs,
            skipForwardDurationMs = skipForwardDurationMs,
        )
    }

    private fun applyExternalPlaybackControls() {
        // Force PlaybackState rebuild from filtered player commands (what Auto reads).
        externalControlsPlayer?.invalidateExternalControls()

        val session = mediaLibrarySession ?: return
        val buttons = currentExternalControlButtons()
        // Custom layout → legacy PlaybackState custom actions (Android Auto).
        session.setCustomLayout(buttons)
        // Media button preferences → Media3 notification / modern surfaces.
        session.setMediaButtonPreferences(buttons)

        val sessionCommands = sessionCommandsForExternalControls()
        val playerCommands = playerCommandsForExternalSurfaces(session.player.availableCommands)
        for (controller in session.connectedControllers) {
            if (isInAppController(controller)) continue
            session.setCustomLayout(controller, buttons)
            session.setMediaButtonPreferences(controller, buttons)
            session.setAvailableCommands(controller, sessionCommands, playerCommands)
        }
    }

    private fun sessionCommandsForExternalControls(): androidx.media3.session.SessionCommands {
        val builder = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(SessionCommand(CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE, Bundle.EMPTY))
        if (externalPrevNextUsesSkipIntervals) {
            builder
                .add(ExternalPlaybackControls.seekBackCommand())
                .add(ExternalPlaybackControls.seekForwardCommand())
        }
        return builder.build()
    }

    private fun playerCommandsForExternalSurfaces(defaultCommands: Player.Commands): Player.Commands {
        return if (externalPrevNextUsesSkipIntervals) {
            ExternalPlaybackControls.filterPlayerCommandsForSkipIntervals(defaultCommands)
        } else {
            defaultCommands
        }
    }

    private fun isInAppController(controller: MediaSession.ControllerInfo): Boolean {
        return controller.packageName == packageName
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
            player?.release()
            release()
            mediaLibrarySession = null
        }
        serviceScope.cancel()
        player = null
        externalControlsPlayer = null
        exoPlayer = null
        super.onDestroy()
    }

    /**
     * Filters track prev/next out of the player's advertised commands when skip-interval
     * mode is on, so Android Auto's PlaybackState no longer enables ACTION_SKIP_TO_*.
     */
    private inner class ExternalControlsPlayer(
        player: ExoPlayer,
    ) : ForwardingSimpleBasePlayer(player) {
        fun invalidateExternalControls() {
            invalidateState()
        }

        override fun getState(): State {
            val state = super.getState()
            val withIncrements = state.buildUpon()
                .setSeekBackIncrementMs(skipBackwardDurationMs)
                .setSeekForwardIncrementMs(skipForwardDurationMs)
            if (!externalPrevNextUsesSkipIntervals) {
                return withIncrements.build()
            }
            return withIncrements
                .setAvailableCommands(
                    ExternalPlaybackControls.filterPlayerCommandsForSkipIntervals(
                        state.availableCommands
                    )
                )
                .build()
        }
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
            val mediaItem = resolveRandomFavoriteMediaItem(excludeCurrentEpisode) ?: return@launch
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.play()
        }
    }

    /**
     * Picks a random favorite episode and builds a playable [MediaItem].
     * Used by in-app / custom-command playback and Android Auto browse.
     */
    private suspend fun resolveRandomFavoriteMediaItem(
        excludeCurrentEpisode: Boolean = false,
    ): MediaItem? {
        val currentEpisodeId = if (excludeCurrentEpisode) {
            player?.currentMediaItem?.mediaMetadata?.extras
                ?.getLong(EXTRA_EPISODE_ID, -1L)
                ?.takeIf { it > 0 }
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

        if (favorites.isEmpty()) return null

        val candidates = if (currentEpisodeId != null) {
            favorites.filter { it.id != currentEpisodeId }
        } else {
            favorites
        }
        val pool = candidates.ifEmpty { favorites }

        val randomEpisode = pool.random()
        val (podcastTitle, localFilePath) = withContext(Dispatchers.IO) {
            val podcast = podcastDao.getById(randomEpisode.podcastId)
            val download = downloadDao.getByEpisodeId(randomEpisode.id)
            val filePath = if (download?.status == DownloadStatus.COMPLETED) {
                download.localFilePath
            } else {
                null
            }
            (podcast?.title ?: "") to filePath
        }
        return randomEpisode.toMediaItem(
            podcastTitle = podcastTitle,
            randomFavoriteMode = true,
            localFilePath = localFilePath
        )
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
    
    private fun isAuthorizedController(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean {
        if (controller.packageName == AutoMediaBrowser.LEGACY_BROWSER_PACKAGE) {
            return true
        }
        if (controller.packageName == MediaSession.ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME) {
            return true
        }
        return session.isAutoCompanionController(controller) ||
            session.isAutomotiveController(controller) ||
            packageValidator.isKnownCaller(controller.packageName, controller.uid)
    }

    private fun <T : Any> runLibraryOperation(
        block: suspend () -> LibraryResult<T>,
    ): ListenableFuture<LibraryResult<T>> {
        val future = SettableFuture.create<LibraryResult<T>>()
        serviceScope.launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (_: Exception) {
                future.set(LibraryResult.ofError<T>(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    /**
     * Resolves browse-tree media IDs into playable items for Android Auto and other
     * external controllers. Auto typically sends mediaId-only items (or items whose
     * [MediaItem.localConfiguration] was stripped across process boundaries).
     * ExoPlayer requires localConfiguration.uri, so we must rebuild items here.
     */
    private suspend fun resolveMediaItemsForPlayback(mediaItems: List<MediaItem>): List<MediaItem> {
        val resolved = mutableListOf<MediaItem>()
        for (item in mediaItems) {
            val playable = resolveMediaItemForPlayback(item)
            if (playable != null) {
                Log.i(
                    TAG,
                    "Resolved mediaId=${item.mediaId} -> playableId=${playable.mediaId} uri=${playable.localConfiguration?.uri}"
                )
                resolved.add(playable)
            } else {
                Log.w(TAG, "Failed to resolve mediaId=${item.mediaId} for playback")
            }
        }
        return resolved
    }

    private suspend fun resolveMediaItemForPlayback(item: MediaItem): MediaItem? {
        // Only trust localConfiguration — requestMetadata survives IPC but is not
        // enough for ExoPlayer to load/play the stream.
        if (item.localConfiguration?.uri != null &&
            item.mediaId != AutoMediaIds.PLAY_RANDOM_FAVORITE
        ) {
            return item
        }

        if (item.mediaId == AutoMediaIds.PLAY_RANDOM_FAVORITE) {
            return resolveRandomFavoriteMediaItem(excludeCurrentEpisode = false)
        }

        val lookedUp = withContext(Dispatchers.IO) {
            autoMediaBrowser.getItem(item.mediaId)
        } ?: return null

        return when {
            lookedUp.mediaId == AutoMediaIds.PLAY_RANDOM_FAVORITE ->
                resolveRandomFavoriteMediaItem(excludeCurrentEpisode = false)
            lookedUp.localConfiguration?.uri != null -> lookedUp
            else -> null
        }
    }

    private fun resolveMediaItemsFuture(
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        serviceScope.launch {
            try {
                Log.i(TAG, "onAdd/SetMediaItems resolving ${mediaItems.size} item(s): ${mediaItems.map { it.mediaId }}")
                future.set(resolveMediaItemsForPlayback(mediaItems))
            } catch (e: Exception) {
                Log.e(TAG, "Failed resolving media items for playback", e)
                future.set(emptyList())
            }
        }
        return future
    }

    private fun resolveMediaItemsWithStartPositionFuture(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch {
            try {
                Log.i(TAG, "onSetMediaItems resolving ${mediaItems.size} item(s): ${mediaItems.map { it.mediaId }}")
                val resolved = resolveMediaItemsForPlayback(mediaItems)
                future.set(
                    MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed resolving media items for setMediaItems", e)
                future.set(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
                )
            }
        }
        return future
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            // Track-skip commands are removed from available commands in skip mode; keep a
            // remap here for any controller that still sends them.
            if (!externalPrevNextUsesSkipIntervals || isInAppController(controller)) {
                return androidx.media3.session.SessionResult.RESULT_SUCCESS
            }
            return when (playerCommand) {
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_WINDOW -> {
                    player?.seekBack()
                    androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT_WINDOW -> {
                    player?.seekForward()
                    androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED
                }
                else -> androidx.media3.session.SessionResult.RESULT_SUCCESS
            }
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            if (!externalPrevNextUsesSkipIntervals) return false
            val keyEvent = intent.getMediaKeyEvent() ?: return false
            if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
            return when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    player?.seekBack()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    player?.seekForward()
                    true
                }
                else -> false
            }
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            if (!isAuthorizedController(session, controller)) {
                return MediaSession.ConnectionResult.reject()
            }
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE, Bundle.EMPTY))
            if (externalPrevNextUsesSkipIntervals) {
                availableSessionCommands
                    .add(ExternalPlaybackControls.seekBackCommand())
                    .add(ExternalPlaybackControls.seekForwardCommand())
            }
            val availablePlayerCommands = if (isInAppController(controller)) {
                connectionResult.availablePlayerCommands
            } else {
                playerCommandsForExternalSurfaces(connectionResult.availablePlayerCommands)
            }
            val mediaButtonPreferences = if (isInAppController(controller)) {
                emptyList()
            } else {
                currentExternalControlButtons()
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands.build())
                .setAvailablePlayerCommands(availablePlayerCommands)
                .setCustomLayout(mediaButtonPreferences)
                .setMediaButtonPreferences(mediaButtonPreferences)
                .build()
        }

        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            if (isInAppController(controller)) return
            val buttons = currentExternalControlButtons()
            session.setCustomLayout(controller, buttons)
            session.setMediaButtonPreferences(controller, buttons)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<androidx.media3.session.SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_COMMAND_PLAY_RANDOM_FAVORITE -> {
                    val excludeCurrent = args.getBoolean(ARG_EXCLUDE_CURRENT_EPISODE, true)
                    playRandomFavorite(excludeCurrentEpisode = excludeCurrent)
                }
                ExternalPlaybackControls.CUSTOM_ACTION_SEEK_BACK -> {
                    player?.seekBack()
                }
                ExternalPlaybackControls.CUSTOM_ACTION_SEEK_FORWARD -> {
                    player?.seekForward()
                }
            }
            return Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            return resolveMediaItemsFuture(mediaItems)
        }

        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return resolveMediaItemsWithStartPositionFuture(mediaItems, startIndex, startPositionMs)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!isAuthorizedController(session, browser)) {
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                )
            }
            return Futures.immediateFuture(
                LibraryResult.ofItem(autoMediaBrowser.createRootItem(), params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!isAuthorizedController(session, browser)) {
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                )
            }
            val effectivePageSize = if (pageSize > 0) pageSize else AutoMediaBrowser.AUTO_PAGE_SIZE
            return runLibraryOperation {
                val items = autoMediaBrowser.getChildren(parentId, page, effectivePageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!isAuthorizedController(session, browser)) {
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                )
            }
            return runLibraryOperation {
                val item = autoMediaBrowser.getItem(mediaId)
                if (item != null) {
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!isAuthorizedController(session, browser)) {
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED)
                )
            }
            val effectivePageSize = if (pageSize > 0) pageSize else AutoMediaBrowser.AUTO_PAGE_SIZE
            return runLibraryOperation {
                val items = autoMediaBrowser.search(query, page, effectivePageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
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

    val audioUri = playbackUri(localFilePath, audioUrl)

    return MediaItem.Builder()
        .setMediaId(episodeId.toString())
        .setUri(audioUri)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(audioUri)
                .build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episodeTitle)
                .setArtist(podcastName)
                .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                .setIsBrowsable(false)
                .setIsPlayable(true)
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
    randomFavoriteMode: Boolean = false,
    localFilePath: String? = null
): MediaItem {
    val extras = Bundle().apply {
        putLong(PlaybackService.EXTRA_EPISODE_ID, id)
        putLong(PlaybackService.EXTRA_PODCAST_ID, podcastId)
        if (randomFavoriteMode) {
            putBoolean(PlaybackService.EXTRA_RANDOM_FAVORITE_MODE, true)
        }
        localFilePath?.let { putString(PlaybackService.EXTRA_LOCAL_FILE_PATH, it) }
    }

    val audioUri = playbackUri(localFilePath, audioUrl)

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(audioUri)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(audioUri)
                .build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(podcastTitle)
                .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(extras)
                .build()
        )
        .build()
}

/**
 * Prefer a local download when the file still exists; otherwise use the remote URL.
 * Local paths are normalized to file:// URIs so ExoPlayer can open them.
 */
private fun playbackUri(localFilePath: String?, audioUrl: String): Uri {
    if (!localFilePath.isNullOrBlank()) {
        val localUri = when {
            localFilePath.startsWith("file:", ignoreCase = true) ||
                localFilePath.startsWith("content:", ignoreCase = true) -> Uri.parse(localFilePath)
            else -> Uri.fromFile(java.io.File(localFilePath))
        }
        val path = localUri.path
        if (path != null && java.io.File(path).exists()) {
            return localUri
        }
    }
    return Uri.parse(audioUrl)
}

@Suppress("DEPRECATION")
private fun Intent.getMediaKeyEvent(): KeyEvent? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
    }
}
