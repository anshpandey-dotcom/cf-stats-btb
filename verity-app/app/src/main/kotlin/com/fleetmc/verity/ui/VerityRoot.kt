package com.fleetmc.verity.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.fleetmc.verity.ads.RewardedAdProvider
import com.fleetmc.verity.ui.launch.DownloadViewModel
import com.fleetmc.verity.ui.launch.LaunchHandler
import com.fleetmc.verity.ui.nav.Routes
import com.fleetmc.verity.ui.nav.VerityBottomBar
import com.fleetmc.verity.ui.nav.VerityNavHost

@Composable
fun VerityRoot(adProvider: RewardedAdProvider) {
    val navController = rememberNavController()
    val catalog: CatalogViewModel = viewModel(factory = VerityViewModelFactory)
    val downloads: DownloadViewModel = viewModel(factory = VerityViewModelFactory)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (route in Routes.topLevel) {
                    VerityBottomBar(currentRoute = route) { target ->
                        navController.navigate(target) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            },
        ) { padding ->
            VerityNavHost(
                navController = navController,
                catalog = catalog,
                downloads = downloads,
                modifier = Modifier.padding(padding),
            )
        }
        adProvider.Host()
        LaunchHandler(downloads.events)
    }
}
