package dev.josephwilliams.freecasts.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.josephwilliams.freecasts.model.entities.Podcast

@Composable
fun SearchView(
    modifier: Modifier = Modifier,
    results: List<Podcast>? = null,
    onSearch: (String) -> Unit
) {

}