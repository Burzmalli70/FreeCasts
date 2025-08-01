package dev.josephwilliams.freecasts.ui.screens.podcasts

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import dev.josephwilliams.freecasts.model.entities.Podcast

@Composable
fun PodcastDetail(
    modifier: Modifier = Modifier,
    podcast: Podcast,
    onDismiss: () -> Unit
) {
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

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = podcast.description ?: ""
        )
    }
}