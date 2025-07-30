package dev.josephwilliams.freecasts.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.josephwilliams.freecasts.model.entities.Podcast

@Composable
fun PodcastsView(
    modifier: Modifier = Modifier,
    podcasts: List<Podcast>,
    selectedPodcast: Podcast? = null,
    onPodcastTapped: (Podcast) -> Unit
) {
}