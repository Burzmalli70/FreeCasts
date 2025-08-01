package dev.josephwilliams.freecasts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.josephwilliams.freecasts.ui.screens.playlists.PlaylistsView
import dev.josephwilliams.freecasts.ui.screens.playlists.PlaylistsViewModel
import dev.josephwilliams.freecasts.ui.screens.podcasts.PodcastDetail
import dev.josephwilliams.freecasts.ui.screens.podcasts.PodcastsView
import dev.josephwilliams.freecasts.ui.screens.search.PodcastSearch
import dev.josephwilliams.freecasts.ui.screens.search.SearchViewModel
import dev.josephwilliams.freecasts.ui.screens.settings.SettingsView
import dev.josephwilliams.freecasts.ui.screens.settings.SettingsViewModel
import dev.josephwilliams.freecasts.ui.theme.FreeCastsTheme
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
    val viewModel: PodcastViewModel by inject(PodcastViewModel::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val mainUiState by viewModel.mainUiStateFlow.collectAsState()
            FreeCastsTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        PodcastBottomBar(
                            onButtonTapped = { navController.navigate(it.name) }
                        )
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavRoute.PODCASTS.name,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(NavRoute.PODCASTS.name) {
                            val podcasts by viewModel.allPodcasts.collectAsState(initial = emptyList())

                            PodcastsView(
                                podcasts = podcasts,
                                selectedPodcast = mainUiState.selectedPodcast,
                                onPodcastTapped = { podcast ->
                                    viewModel.selectPodcast(podcast)
                                }
                            )
                        }

                        composable(NavRoute.SEARCH.name) {
                            val searchViewModel: SearchViewModel by inject(SearchViewModel::class.java)
                            PodcastSearch(
                                searchViewModel = searchViewModel
                            )
                        }

                        composable(NavRoute.PLAYLISTS.name) {
                            val playlistsViewModel: PlaylistsViewModel by inject(PlaylistsViewModel::class.java)
                            PlaylistsView(
                                playlistsViewModel = playlistsViewModel
                            )
                        }

                        composable(NavRoute.SETTINGS.name) {
                            val settingsViewModel: SettingsViewModel by inject(SettingsViewModel::class.java)
                            SettingsView(
                                settingsViewModel = settingsViewModel
                            )
                        }
                    }
                }
            }

            mainUiState.value.selectedPodcast?.let {
                PodcastDetail(podcast = it) {
                    viewModel.selectPodcast(null)
                }
            }
        }
    }
}

@Composable
fun PodcastBottomBar(
    modifier: Modifier = Modifier,
    onButtonTapped: (NavRoute) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for(route in NavRoute.entries) {
            BottomBarButton(
                modifier = Modifier.padding(20.dp).clickable { onButtonTapped(route) },
                route = route
            )
        }
    }
}

@Composable
fun BottomBarButton(
    modifier: Modifier = Modifier,
    route: NavRoute
) {
    Text(route.name, modifier = modifier)
}

enum class NavRoute {
    PODCASTS,
    SEARCH,
    PLAYLISTS,
    SETTINGS
}

val MAIN_NAV_ROUTES = listOf(NavRoute.PODCASTS, NavRoute.SEARCH, NavRoute.PLAYLISTS, NavRoute.SETTINGS)