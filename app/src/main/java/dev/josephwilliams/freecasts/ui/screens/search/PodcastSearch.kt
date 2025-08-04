package dev.josephwilliams.freecasts.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.josephwilliams.freecasts.R
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.ui.debugPlaceholder
import dev.josephwilliams.freecasts.ui.screens.podcasts.PodcastDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSearch(
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val isActive by searchViewModel.isActive.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val searchingState by searchViewModel.searchingState.collectAsState()

    Column(modifier = modifier) {
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isActive) 0.dp else 16.dp) // Full width when active
                .padding(vertical = 8.dp),
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { searchViewModel.onQueryChange(it) },
                    onSearch = {
                        searchViewModel.onActiveChange(false)
                        searchViewModel.executeSearch(it)
                    },
                    expanded = isActive,
                    onExpandedChange = { searchViewModel.onActiveChange(it) },
                    placeholder = { Text("Search") },
                    leadingIcon = {
                        if (isActive) {
                            IconButton(onClick = { searchViewModel.onActiveChange(false) }) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchViewModel.clearSearchQuery() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    }
                )
            },
            expanded = isActive,
            onExpandedChange = { searchViewModel.onActiveChange(it) }
        ) {
            when(searchingState) {
                is SearchingState.Searching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp), contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SearchingState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp), contentAlignment = Alignment.Center
                    ) {
                        Text("Error: ${(searchingState as? SearchingState.Error)?.exception?.message}")
                    }
                }
                is SearchingState.Done -> {
                    if (searchResults?.isNotEmpty() == true) {
                        searchResults?.let { results ->
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(results.size) { item ->
                                    val result = results[item]
                                    PodcastResult(
                                        modifier = Modifier.clickable {
                                            searchViewModel.onActiveChange(false)
                                            searchViewModel.selectPodcast(result)
                                        },
                                        podcast = result
                                    )
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        // Show if query is not blank but no results
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp), contentAlignment = Alignment.Center
                        ) {
                            Text("No results found for \"$searchQuery\"")
                        }
                    }
                }
                else -> {

                }
            }
        }
        when(searchingState) {
            is SearchingState.Searching -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is SearchingState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), contentAlignment = Alignment.Center
                ) {
                    Text("Error: ${(searchingState as? SearchingState.Error)?.exception?.message}")
                }
            }
            is SearchingState.Done -> {
                if (searchResults?.isNotEmpty() == true) {
                    searchResults?.let { results ->
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(results.size) { item ->
                                val result = results[item]
                                PodcastResult(
                                    modifier = Modifier.clickable {
                                        searchViewModel.onActiveChange(false)
                                        searchViewModel.selectPodcast(result)
                                    },
                                    podcast = result
                                )
                            }
                        }
                    }
                } else if (searchQuery.isNotBlank()) {
                    // Show if query is not blank but no results
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp), contentAlignment = Alignment.Center
                    ) {
                        Text("No results found for \"$searchQuery\"")
                    }
                }
            }
            else -> {

            }
        }
    }

    val selectedPodcast by searchViewModel.selectedPodcast.collectAsState()
    val podcastEpisodes by searchViewModel.podcastEpisodes.collectAsState()

    selectedPodcast?.let {
        PodcastDetail(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            podcast = it,
            episodes = podcastEpisodes
        ) {
            searchViewModel.selectPodcast(null)
        }
    }
}

@Composable
fun PodcastResult(
    modifier: Modifier = Modifier,
    podcast: Podcast
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = podcast.smallImageUrl,
            contentDescription = null,
            placeholder = debugPlaceholder(R.drawable.debug_preview_img),
            fallback = debugPlaceholder(R.drawable.ic_launcher_foreground),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.width(40.dp).clip(RoundedCornerShape(4.dp))
        )
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}