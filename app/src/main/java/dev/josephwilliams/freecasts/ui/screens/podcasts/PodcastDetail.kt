package dev.josephwilliams.freecasts.ui.screens.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import dev.josephwilliams.freecasts.data.local.entity.Episode
import dev.josephwilliams.freecasts.data.local.entity.Podcast

@Composable
fun PodcastDetail(
    modifier: Modifier = Modifier,
    podcast: Podcast,
    episodes: List<Episode>,
    onUpdateSubscription: (Podcast) -> Unit = {},
    onDownloadEpisode: (Episode) -> Unit = {},
    onDeleteEpisode: (Episode) -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(PodcastDetailTab.DESCRIPTION) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            Icon(
                imageVector = if (podcast.isSubscribed) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.clickable { onUpdateSubscription(podcast) }
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.clickable { onDismiss() }
            )
        }
        AsyncImage(
            model = podcast.artworkUrl,
            contentDescription = null
        )

        Text(podcast.title)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (tab in PodcastDetailTab.entries) {
                Text(
                    text = tab.name,
                    modifier = Modifier.clickable { selectedTab = tab }
                )
            }
        }

        when(selectedTab) {
            PodcastDetailTab.DESCRIPTION -> {
                Text(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    text = podcast.description ?: ""
                )
            }
            PodcastDetailTab.EPISODES -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(episodes.size) {
                        EpisodeItem(
                            modifier = Modifier.fillMaxWidth().clickable {

                            },
                            episode = episodes[it],
                            downloadEpisode = {
                                onDownloadEpisode(episodes[it])
                            },
                            deleteEpisode = {
                                onDeleteEpisode(episodes[it])
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(
    modifier: Modifier = Modifier,
    episode: Episode,
    downloadEpisode: () -> Unit,
    deleteEpisode: () -> Unit
) {
    Row(modifier = modifier) {
        if (episode.isDownloaded) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.clickable { deleteEpisode() }
            )
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.clickable { downloadEpisode() }
            )
        }
        Text(episode.title ?: "")
    }
}

enum class PodcastDetailTab {
    DESCRIPTION,
    EPISODES
}