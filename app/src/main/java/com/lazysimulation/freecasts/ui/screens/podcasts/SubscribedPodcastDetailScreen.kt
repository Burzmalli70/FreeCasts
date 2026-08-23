@file:OptIn(ExperimentalTime::class)

package com.lazysimulation.freecasts.ui.screens.podcasts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.playback.PlaybackManager
import com.lazysimulation.freecasts.data.playback.PlayingEpisode
import com.lazysimulation.freecasts.ui.components.PlaylistPickerDialog
import com.lazysimulation.freecasts.ui.components.formatEpisodeListDuration
import kotlinx.datetime.Instant
import org.koin.compose.koinInject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubscribedPodcastDetailScreen(
    podcastId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscribedPodcastDetailViewModel = koinViewModel(),
    playbackManager: PlaybackManager = koinInject()
) {
    val state by viewModel.state.collectAsState()
    var episodeForPlaylist by remember { mutableStateOf<EpisodeDisplayState?>(null) }
    
    LaunchedEffect(podcastId) {
        viewModel.loadPodcast(podcastId)
    }
    
    episodeForPlaylist?.let { episodeState ->
        PlaylistPickerDialog(
            playlists = state.playlists,
            episodeTitle = episodeState.episode.title,
            onPlaylistSelected = { playlistWithCount ->
                viewModel.addEpisodeToPlaylist(playlistWithCount.playlist.id, episodeState.episode.id)
                episodeForPlaylist = null
            },
            onDismiss = { episodeForPlaylist = null }
        )
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.podcast?.title ?: "Podcast",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading && state.podcast == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            state.podcast == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Podcast not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    SubscribedPodcastContent(
                        podcast = state.podcast!!,
                        episodes = state.episodes,
                        onUnsubscribe = {
                            viewModel.unsubscribe()
                            onNavigateBack()
                        },
                        onDownloadEpisode = { viewModel.downloadEpisode(it.episode) },
                        onCancelDownload = { viewModel.cancelDownload(it.episode.id) },
                        onDeleteDownload = { viewModel.deleteDownload(it.episode.id) },
                        onTogglePlayed = { episode ->
                            if (episode.isPlayed) {
                                viewModel.markAsUnplayed(episode.episode.id)
                            } else {
                                viewModel.markAsPlayed(episode.episode.id)
                            }
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it.episode) },
                        onPlayEpisode = { episodeState ->
                            val podcast = state.podcast!!
                            playbackManager.play(
                                PlayingEpisode(
                                    episodeId = episodeState.episode.id,
                                    episodeTitle = episodeState.episode.title,
                                    podcastId = podcast.id,
                                    podcastName = podcast.title,
                                    artworkUrl = episodeState.episode.artworkUrl ?: podcast.artworkUrl,
                                    audioUrl = episodeState.episode.audioUrl,
                                    localFilePath = episodeState.localFilePath
                                )
                            )
                        },
                        onSwipeToAddToPlaylist = { episodeForPlaylist = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscribedPodcastContent(
    podcast: Podcast,
    episodes: List<EpisodeDisplayState>,
    onUnsubscribe: () -> Unit,
    onDownloadEpisode: (EpisodeDisplayState) -> Unit,
    onCancelDownload: (EpisodeDisplayState) -> Unit,
    onDeleteDownload: (EpisodeDisplayState) -> Unit,
    onTogglePlayed: (EpisodeDisplayState) -> Unit,
    onToggleFavorite: (EpisodeDisplayState) -> Unit,
    onPlayEpisode: (EpisodeDisplayState) -> Unit,
    onSwipeToAddToPlaylist: (EpisodeDisplayState) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var episodeSearchQuery by remember { mutableStateOf("") }
    var isEpisodeSearchFocused by remember { mutableStateOf(false) }
    val tabs = listOf("About", "Episodes")
    val isEpisodeSearchActive = selectedTabIndex == 1 &&
        (episodeSearchQuery.isNotBlank() || isEpisodeSearchFocused)
    
    Column(modifier = modifier.fillMaxSize()) {
        if (!isEpisodeSearchActive) {
            PodcastDetailHeader(
                podcast = podcast,
                onUnsubscribe = onUnsubscribe
            )
        }
        
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = if (index == 1 && episodes.isNotEmpty()) {
                                "$title (${episodes.size})"
                            } else {
                                title
                            }
                        )
                    }
                )
            }
        }
        
        when (selectedTabIndex) {
            0 -> AboutTabContent(podcast = podcast)
            1 -> EpisodesTabContent(
                episodes = episodes,
                searchQuery = episodeSearchQuery,
                onSearchQueryChange = { episodeSearchQuery = it },
                onSearchFocusChange = { isEpisodeSearchFocused = it },
                onDownloadEpisode = onDownloadEpisode,
                onCancelDownload = onCancelDownload,
                onDeleteDownload = onDeleteDownload,
                onTogglePlayed = onTogglePlayed,
                onToggleFavorite = onToggleFavorite,
                onPlayEpisode = onPlayEpisode,
                onSwipeToAddToPlaylist = onSwipeToAddToPlaylist
            )
        }
    }
}

@Composable
private fun PodcastDetailHeader(
    podcast: Podcast,
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = podcast.artworkUrl,
            contentDescription = podcast.title,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            podcast.author?.let { author ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            podcast.categories?.let { categories ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = categories,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subscribed indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Subscribed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                // Unsubscribe button
                Text(
                    text = "Unsubscribe",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable(onClick = onUnsubscribe)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutTabContent(
    podcast: Podcast,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            if (podcast.description.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No description available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = podcast.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (podcast.episodeCount > 0) {
                    DetailRow(label = "Episodes", value = "${podcast.episodeCount}")
                }
                podcast.websiteUrl?.let {
                    DetailRow(label = "Website", value = it)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodesTabContent(
    episodes: List<EpisodeDisplayState>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onDownloadEpisode: (EpisodeDisplayState) -> Unit,
    onCancelDownload: (EpisodeDisplayState) -> Unit,
    onDeleteDownload: (EpisodeDisplayState) -> Unit,
    onTogglePlayed: (EpisodeDisplayState) -> Unit,
    onToggleFavorite: (EpisodeDisplayState) -> Unit,
    onPlayEpisode: (EpisodeDisplayState) -> Unit,
    onSwipeToAddToPlaylist: (EpisodeDisplayState) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val filteredEpisodes = remember(episodes, searchQuery) {
        filterEpisodes(episodes, searchQuery)
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onFocusChanged { onSearchFocusChange(it.isFocused) },
            placeholder = { Text("Search episodes...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search episodes"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() }
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        when {
            episodes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No episodes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            filteredEpisodes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No episodes match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredEpisodes,
                        key = { it.episode.id }
                    ) { episodeState ->
                        SwipeableEpisodeCard(
                            episodeState = episodeState,
                            onDownload = { onDownloadEpisode(episodeState) },
                            onCancelDownload = { onCancelDownload(episodeState) },
                            onDeleteDownload = { onDeleteDownload(episodeState) },
                            onTogglePlayed = { onTogglePlayed(episodeState) },
                            onToggleFavorite = { onToggleFavorite(episodeState) },
                            onLongPress = { onPlayEpisode(episodeState) },
                            onSwipeToAddToPlaylist = { onSwipeToAddToPlaylist(episodeState) }
                        )
                    }
                }
            }
        }
    }
}

private fun filterEpisodes(
    episodes: List<EpisodeDisplayState>,
    query: String
): List<EpisodeDisplayState> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return episodes
    
    return episodes.filter { episodeState ->
        val episode = episodeState.episode
        episode.title.contains(trimmed, ignoreCase = true) ||
            episode.description?.contains(trimmed, ignoreCase = true) == true
    }
}

@Composable
private fun SwipeableEpisodeCard(
    episodeState: EpisodeDisplayState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeToAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd,
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeToAddToPlaylist()
                    false
                }
                else -> true
            }
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val backgroundColor = MaterialTheme.colorScheme.primaryContainer
            val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Add to playlist",
                            tint = contentColor
                        )
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = "Add to playlist",
                            tint = contentColor
                        )
                    }
                }
                else -> {}
            }
        },
        content = {
            EpisodeCard(
                episodeState = episodeState,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDeleteDownload = onDeleteDownload,
                onTogglePlayed = onTogglePlayed,
                onToggleFavorite = onToggleFavorite,
                onLongPress = onLongPress
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCard(
    episodeState: EpisodeDisplayState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = episodeState.episode
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (episodeState.isPlayed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Title row with played indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Played indicator
                if (episodeState.isPlayed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Played",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = if (episodeState.isPlayed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                // Favorite button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (episode.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (episode.isFavorite) "Remove from favorites" else "Add to favorites",
                        modifier = Modifier.size(18.dp),
                        tint = if (episode.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Metadata row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Publication date
                episode.publishedAt?.let { timestamp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDate(timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Duration or remaining time
                formatEpisodeListDuration(
                    playbackPositionMs = episodeState.playbackPositionMs,
                    durationSeconds = episode.durationSeconds,
                    isPlayed = episodeState.isPlayed
                )?.let { durationLabel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (episodeState.hasProgress) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (episodeState.hasProgress) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                
                // Listen count
                if (episodeState.listenCount > 1) {
                    Text(
                        text = "Played ${episodeState.listenCount}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Playback progress bar (if in progress)
            if (episodeState.hasProgress) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { episodeState.progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    strokeCap = StrokeCap.Round
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Download status and actions row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Download status indicator
                DownloadStatusIndicator(
                    status = episodeState.downloadStatus,
                    progress = episodeState.downloadProgress
                )
                
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mark played/unplayed
                    Text(
                        text = if (episodeState.isPlayed) "Mark unplayed" else "Mark played",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onTogglePlayed)
                            .padding(4.dp)
                    )
                    
                    // Download/Delete action
                    when (episodeState.downloadStatus) {
                        EpisodeDownloadDisplayStatus.NOT_DOWNLOADED,
                        EpisodeDownloadDisplayStatus.FAILED -> {
                            DownloadButton(onClick = onDownload)
                        }
                        EpisodeDownloadDisplayStatus.QUEUED,
                        EpisodeDownloadDisplayStatus.DOWNLOADING -> {
                            CancelButton(onClick = onCancelDownload)
                        }
                        EpisodeDownloadDisplayStatus.DOWNLOADED -> {
                            DeleteButton(onClick = onDeleteDownload)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatusIndicator(
    status: EpisodeDownloadDisplayStatus,
    progress: Int?,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        when (status) {
            EpisodeDownloadDisplayStatus.NOT_DOWNLOADED -> {
                // No indicator
            }
            EpisodeDownloadDisplayStatus.QUEUED -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Queued",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            EpisodeDownloadDisplayStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Downloading${progress?.let { " $it%" } ?: "..."}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            EpisodeDownloadDisplayStatus.DOWNLOADED -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Downloaded",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            EpisodeDownloadDisplayStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Download failed",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DownloadButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Text(
            text = "Download",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = "Cancel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete download",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error
        )
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${localDateTime.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${localDateTime.dayOfMonth}, ${localDateTime.year}"
    } catch (e: Exception) {
        ""
    }
}

