package dev.josephwilliams.freecasts.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.josephwilliams.freecasts.data.local.dao.EpisodeDao
import dev.josephwilliams.freecasts.data.playback.PlaybackState
import dev.josephwilliams.freecasts.data.preferences.DEFAULT_SKIP_INTERVAL_SECONDS
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onSeekTo: (Long) -> Unit,
    skipForwardIntervalSeconds: Int = DEFAULT_SKIP_INTERVAL_SECONDS,
    skipBackwardIntervalSeconds: Int = DEFAULT_SKIP_INTERVAL_SECONDS,
    modifier: Modifier = Modifier,
    episodeDao: EpisodeDao = koinInject()
) {
    val episode = playbackState.currentEpisode ?: return

    val episodeWithPodcast by episodeDao
        .observeEpisodeWithPodcast(episode.episodeId)
        .collectAsState(initial = null)

    val artworkUrl = episodeWithPodcast?.episode?.artworkUrl
        ?: episodeWithPodcast?.podcast?.artworkUrl
        ?: episode.artworkUrl

    val description = episodeWithPodcast?.episode?.description
        ?.takeIf { it.isNotBlank() }
        ?.let { stripHtml(it) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(episode.episodeId) {
        isScrubbing = false
    }

    val progress = when {
        isScrubbing -> scrubProgress
        playbackState.durationMs > 0 -> {
            playbackState.currentPositionMs.toFloat() / playbackState.durationMs
        }
        else -> 0f
    }

    val displayPositionMs = if (isScrubbing && playbackState.durationMs > 0) {
        (scrubProgress * playbackState.durationMs).toLong()
    } else {
        playbackState.currentPositionMs
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close now playing"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = "Episode artwork",
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = episode.podcastName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = episode.episodeTitle,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (playbackState.durationMs > 0) {
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { value ->
                        isScrubbing = true
                        scrubProgress = value
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onSeekTo((scrubProgress * playbackState.durationMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = formatPlaybackTime(displayPositionMs, playbackState.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canGoToPreviousTrack =
                    playbackState.hasPreviousInQueue || playbackState.currentPositionMs > 3000
                val canGoToNextTrack =
                    playbackState.hasNextInQueue || playbackState.canPlayRandomFavoriteNext
                val disabledControlTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

                IconButton(
                    onClick = onPreviousTrack,
                    enabled = canGoToPreviousTrack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous track",
                        modifier = Modifier.size(28.dp),
                        tint = if (canGoToPreviousTrack) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            disabledControlTint
                        }
                    )
                }

                IconButton(
                    onClick = onSkipBackward,
                    modifier = Modifier.size(48.dp)
                ) {
                    SkipBackwardIcon(
                        intervalSeconds = skipBackwardIntervalSeconds,
                        iconSize = 28.dp,
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))

                IconButton(
                    onClick = onSkipForward,
                    modifier = Modifier.size(48.dp)
                ) {
                    SkipForwardIcon(
                        intervalSeconds = skipForwardIntervalSeconds,
                        iconSize = 28.dp,
                    )
                }

                IconButton(
                    onClick = onNextTrack,
                    enabled = canGoToNextTrack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track",
                        modifier = Modifier.size(28.dp),
                        tint = if (canGoToNextTrack) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            disabledControlTint
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (description.isNullOrBlank()) "No description available" else description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun NowPlayingOverlay(
    visible: Boolean,
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onSeekTo: (Long) -> Unit,
    skipForwardIntervalSeconds: Int = DEFAULT_SKIP_INTERVAL_SECONDS,
    skipBackwardIntervalSeconds: Int = DEFAULT_SKIP_INTERVAL_SECONDS,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && playbackState.hasMedia,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        NowPlayingScreen(
            playbackState = playbackState,
            onDismiss = onDismiss,
            onPlayPauseClick = onPlayPauseClick,
            onSkipForward = onSkipForward,
            onSkipBackward = onSkipBackward,
            onNextTrack = onNextTrack,
            onPreviousTrack = onPreviousTrack,
            onSeekTo = onSeekTo,
            skipForwardIntervalSeconds = skipForwardIntervalSeconds,
            skipBackwardIntervalSeconds = skipBackwardIntervalSeconds,
        )
    }
}
