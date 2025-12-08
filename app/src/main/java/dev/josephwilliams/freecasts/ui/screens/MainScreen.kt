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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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
                onButtonTapped = { navController.navigate(it.name) }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.PODCASTS.name,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(innerPadding)
        ) {
            composable(NavRoute.PODCASTS.name) {

            }

            composable(NavRoute.SEARCH.name) {

            }

            composable(NavRoute.PLAYLISTS.name) {

            }

            composable(NavRoute.SETTINGS.name) {

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
        Icon(route.icon, contentDescription = null)
        Text(text = route.name, modifier = modifier, fontSize = 12.sp)
    }
}

enum class NavRoute(val icon: ImageVector) {
    PODCASTS(icon = Icons.Default.Home),
    SEARCH(icon = Icons.Default.Search),
    PLAYLISTS(icon = Icons.AutoMirrored.Default.List),
    SETTINGS(icon = Icons.Default.Settings)
}

val MAIN_NAV_ROUTES = listOf(NavRoute.PODCASTS, NavRoute.SEARCH, NavRoute.PLAYLISTS, NavRoute.SETTINGS)