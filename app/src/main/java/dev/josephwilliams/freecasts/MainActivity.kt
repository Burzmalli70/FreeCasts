package dev.josephwilliams.freecasts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.josephwilliams.freecasts.ui.screens.PodcastsView
import dev.josephwilliams.freecasts.ui.screens.search.PodcastSearch
import dev.josephwilliams.freecasts.ui.screens.search.SearchViewModel
import dev.josephwilliams.freecasts.ui.theme.FreeCastsTheme
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
    val viewModel: PodcastViewModel by inject(PodcastViewModel::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val mainUiState = viewModel.mainUiStateFlow.collectAsState()
            FreeCastsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavRoute.PODCASTS.name,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(NavRoute.PODCASTS.name) {
                            val podcasts = viewModel.allPodcasts.collectAsState(initial = emptyList())

                            PodcastsView(
                                podcasts = podcasts.value,
                                selectedPodcast = mainUiState.value.selectedPodcast,
                                onPodcastTapped = { podcast ->
                                    viewModel.selectPodcast(podcast)
                                    navController.navigate(NavRoute.PODCASTS.name)
                                }
                            )
                        }

                        composable(NavRoute.SEARCH.name) {
                            val searchViewModel: SearchViewModel by inject(SearchViewModel::class.java)
                            PodcastSearch(
                                searchViewModel = searchViewModel,
                                onSearch = { query ->
                                    viewModel.searchPodcasts(query)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class NavRoute {
    PODCASTS,
    SEARCH,
    PLAYLISTS,
    SETTINGS
}