package dev.josephwilliams.freecasts.ui.screens.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.josephwilliams.freecasts.model.entities.Download

@Composable
fun DownloadsView(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel
) {
    val downloads by viewModel.downloads.collectAsState(emptyList())

    LazyColumn(
        modifier = modifier
    ) {
        items(downloads.size) { idx ->
            val download = downloads[idx]
            DownloadItem(download = download)
        }
    }
}

@Composable
fun DownloadItem(
    modifier: Modifier = Modifier,
    download: Download
) {
    Row(
        modifier = modifier
    ) {
        if (download.completed) {
            Icon(Icons.Filled.Check, contentDescription = "Completed")
        } else {
            CircularProgressIndicator(progress = { download.downloadedBytes.toFloat() / download.targetBytes.toFloat() })
        }
        Column {
            Text(text = download.podcastTitle ?: "")
            Text(text = download.episodeTitle ?: "")
        }
    }
}