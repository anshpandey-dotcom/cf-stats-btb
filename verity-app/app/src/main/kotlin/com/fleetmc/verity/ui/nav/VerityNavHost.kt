package com.fleetmc.verity.ui.nav

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fleetmc.verity.ui.CatalogViewModel
import com.fleetmc.verity.ui.LoadState
import com.fleetmc.verity.ui.detail.DetailScreen
import com.fleetmc.verity.ui.home.HomeScreen
import com.fleetmc.verity.ui.info.InfoScreen
import com.fleetmc.verity.ui.launch.DownloadViewModel
import com.fleetmc.verity.ui.saved.SavedScreen

@Composable
fun VerityNavHost(
    navController: NavHostController,
    catalog: CatalogViewModel,
    downloads: DownloadViewModel,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val busy by downloads.busy.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            val state by catalog.home.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onQueryChange = catalog::onQueryChange,
                onCategorySelect = catalog::onCategorySelect,
                onOpen = { navController.navigate(Routes.detail(it.id)) },
            )
        }
        composable(Routes.SAVED) {
            val saved by catalog.saved.collectAsStateWithLifecycle()
            SavedScreen(
                addons = saved,
                busy = busy,
                onOpenInMinecraft = downloads::open,
                onDetails = { navController.navigate(Routes.detail(it.id)) },
            )
        }
        composable(Routes.INFO) {
            InfoScreen()
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.DETAIL_ARG) { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString(Routes.DETAIL_ARG)
            val loadState by catalog.loadState.collectAsStateWithLifecycle()
            val unlocked by catalog.unlocked.collectAsStateWithLifecycle()
            val addon = (loadState as? LoadState.Loaded)?.addons?.firstOrNull { it.id == id }
            DetailScreen(
                addon = addon,
                loadState = loadState,
                unlocked = addon != null && addon.id in unlocked,
                busy = busy,
                onBack = { navController.popBackStack() },
                onDownload = { target ->
                    activity?.let { downloads.download(it, target, target.id in unlocked) }
                },
            )
        }
    }
}
