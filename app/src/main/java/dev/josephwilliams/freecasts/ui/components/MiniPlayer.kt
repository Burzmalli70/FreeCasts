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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.josephwilliams.freecasts.data.playback.PlaybackState
import dev.josephwilliams.freecasts.data.preferences.DEFAULT_SKIP_INTERVAL_SECONDS

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousTrack: () -> Unit,
    onStopClick: () -> Unit,
    onExpandClick: () -> Unit,
    skipForwardIntervalSeconds: Int = DEFAULT_SKIP_INTERVAL_SECONDS,
    skipBackwardIntervalSeconds: Int = DEFAULT_SKIP_INTERVAL_SECONDS,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = playbackState.hasMedia,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Progress bar at the top
                LinearProgressIndicator(
                    progress = { playbackState.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tappable artwork and episode info (opens full screen player)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onExpandClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = playbackState.currentEpisode?.artworkUrl,
                            contentDescription = "Episode artwork",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = playbackState.currentEpisode?.episodeTitle ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = playbackState.currentEpisode?.podcastName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (playbackState.hasQueue) {
                                    Text(
                                        text = " · ${playbackState.queuePosition}/${playbackState.queueSize}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = formatPlaybackTime(playbackState.currentPositionMs, playbackState.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Playback controls
                    Row(
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous track",
                                modifier = Modifier.size(20.dp),
                                tint = if (canGoToPreviousTrack) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    disabledControlTint
                                }
                            )
                        }

                        IconButton(
                            onClick = onSkipBackward,
                            modifier = Modifier.size(32.dp)
                        ) {
                            SkipBackwardIcon(
                                intervalSeconds = skipBackwardIntervalSeconds,
                                iconSize = 20.dp,
                            )
                        }

                        // Play/Pause button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onPlayPauseClick),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playbackState.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
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
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = onSkipForward,
                            modifier = Modifier.size(32.dp)
                        ) {
                            SkipForwardIcon(
                                intervalSeconds = skipForwardIntervalSeconds,
                                iconSize = 20.dp,
                            )
                        }

                        IconButton(
                            onClick = onNextTrack,
                            enabled = canGoToNextTrack,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next track",
                                modifier = Modifier.size(20.dp),
                                tint = if (canGoToNextTrack) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    disabledControlTint
                                }
                            )
                        }

                        IconButton(
                            onClick = onStopClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Stop playback",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
