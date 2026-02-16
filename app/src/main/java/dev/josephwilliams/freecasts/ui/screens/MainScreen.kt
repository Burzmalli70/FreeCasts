package dev.josephwilliams.freecasts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.josephwilliams.freecasts.data.remote.model.ItunesPodcast
import dev.josephwilliams.freecasts.ui.screens.search.SearchPodcastDetailScreen
import dev.josephwilliams.freecasts.ui.screens.search.SearchScreen
import kotlinx.serialization.json.Json

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            PodcastBottomBar(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                onButtonTapped = { 
                    navController.navigate(it.name) {
                        // Pop up to the start destination to avoid building up a back stack
                        popUpTo(NavRoute.PODCASTS.name) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when navigating back to a previously selected tab
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.PODCASTS.name,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(innerPadding)
        ) {
            composable(NavRoute.PODCASTS.name) {
                // TODO: Implement subscribed podcasts view
            }

            composable(NavRoute.SEARCH.name) {
                SearchScreen(
                    onPodcastSelected = { podcast ->
                        // Encode podcast as JSON and navigate to detail
                        val podcastJson = Json.encodeToString(ItunesPodcast.serializer(), podcast)
                        val encodedJson = java.net.URLEncoder.encode(podcastJson, "UTF-8")
                        navController.navigate("${NavRoute.SEARCH_DETAIL.name}/$encodedJson")
                    }
                )
            }
            
            composable(
                route = "${NavRoute.SEARCH_DETAIL.name}/{podcastJson}",
                arguments = listOf(
                    navArgument("podcastJson") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val podcastJson = backStackEntry.arguments?.getString("podcastJson") ?: ""
                val decodedJson = java.net.URLDecoder.decode(podcastJson, "UTF-8")
                val podcast = remember(decodedJson) {
                    Json.decodeFromString(ItunesPodcast.serializer(), decodedJson)
                }
                
                SearchPodcastDetailScreen(
                    itunesPodcast = podcast,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(NavRoute.PLAYLISTS.name) {
                // TODO: Implement playlists view
            }

            composable(NavRoute.SETTINGS.name) {
                // TODO: Implement settings view
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
        for(route in MAIN_NAV_ROUTES) {
            BottomBarButton(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp).clickable { onButtonTapped(route) },
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        route.icon?.let { icon ->
            Icon(icon, contentDescription = null)
        }
        Text(text = route.name, fontSize = 12.sp)
    }
}

enum class NavRoute(val icon: ImageVector?) {
    PODCASTS(icon = Icons.Default.Home),
    SEARCH(icon = Icons.Default.Search),
    SEARCH_DETAIL(icon = null), // Detail screen, not shown in bottom bar
    PLAYLISTS(icon = Icons.AutoMirrored.Default.List),
    SETTINGS(icon = Icons.Default.Settings)
}

val MAIN_NAV_ROUTES = listOf(NavRoute.PODCASTS, NavRoute.SEARCH, NavRoute.PLAYLISTS, NavRoute.SETTINGS)