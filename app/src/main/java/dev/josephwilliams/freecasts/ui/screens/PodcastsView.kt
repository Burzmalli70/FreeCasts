package dev.josephwilliams.freecasts.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.josephwilliams.freecasts.R
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.ui.debugPlaceholder

@Composable
fun PodcastsView(
    modifier: Modifier = Modifier,
    podcasts: List<Podcast>,
    selectedPodcast: Podcast? = null,
    onPodcastTapped: (Podcast) -> Unit
) {
    if (selectedPodcast == null) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 40.dp)
        ) {
            items(podcasts.size) {
                val podcast = podcasts[it]
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    AsyncImage(
                        model = podcast.smallImageUrl,
                        contentDescription = null,
                        placeholder = debugPlaceholder(R.drawable.debug_preview_img),
                        fallback = debugPlaceholder(R.drawable.ic_launcher_foreground),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PodcastsViewPreview() {
    PodcastsView(
        podcasts = listOf(
            Podcast(
                title = "Test Podcast",
                smallImageUrl = "https://images.squarespace-cdn.com/content/v1/606d4bb793879d12d807d4c8/1617957363630-9JLRJABO10DYIUKUDSQG/album-art-temp_smaller.jpg"
            ),
            Podcast(title = "Test Pod 2", smallImageUrl = "")
        ), selectedPodcast = null
    ) { }
}
