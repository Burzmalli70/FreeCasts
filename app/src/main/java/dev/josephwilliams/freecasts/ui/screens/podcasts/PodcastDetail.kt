package dev.josephwilliams.freecasts.ui.screens.podcasts

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Podcast

@Composable
fun PodcastDetail(
    modifier: Modifier = Modifier,
    podcast: Podcast,
    episodes: List<Episode>,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(PodcastDetailTab.DESCRIPTION) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.align(Alignment.End).clickable { onDismiss() }
        )
        AsyncImage(
            model = podcast.smallImageUrl,
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
                Column(modifier = Modifier.weight(1f).scrollable(rememberScrollState(), orientation = Orientation.Vertical)) {
                    for (episode in episodes) {
                        EpisodeItem(episode = episode)
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeItem(
    modifier: Modifier = Modifier,
    episode: Episode
) {
    Row(modifier = modifier) {
        Text(episode.title ?: "")
    }
}

enum class PodcastDetailTab {
    DESCRIPTION,
    EPISODES
}