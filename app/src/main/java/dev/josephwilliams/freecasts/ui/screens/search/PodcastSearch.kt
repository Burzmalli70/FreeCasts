package dev.josephwilliams.freecasts.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.josephwilliams.freecasts.model.entities.Podcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSearch(
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel,
    onSearch: (String) -> Unit
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val isActive by searchViewModel.isActive.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()

    Column(modifier = modifier) {
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isActive) 0.dp else 16.dp) // Full width when active
                .padding(vertical = 8.dp),
            query = searchQuery,
            onQueryChange = { searchViewModel.onQueryChange(it) },
            onSearch = {
                searchViewModel.onActiveChange(false) // Typically close search bar
                // Perform search or navigate to results screen
            },
            active = isActive,
            onActiveChange = { searchViewModel.onActiveChange(it) },
            placeholder = { Text("Search something...") },
            leadingIcon = {
                if (isActive) {
                    IconButton(onClick = { searchViewModel.onActiveChange(false) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        ) {
            if (searchResults?.isNotEmpty() == true) {
                searchResults?.let { results ->
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(results.size) { item ->
                            val result = results[item]
                            ListItem(
                                headlineContent = { Text(result.title ?: "") },
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable {
                                        searchViewModel.onActiveChange(false)   // Close search
                                    }
                            )
                        }
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                // Show if query is not blank but no results
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No results found for \"$searchQuery\"")
                }
            }
        }
    }
}

@Composable
fun PodcastResult(
    modifier: Modifier = Modifier,
    podcast: Podcast
) {

}