package com.ansh.recalm.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ansh.recalm.Incoming

@Composable
fun RecalmNav(incoming: Incoming?, onIncomingHandled: () -> Unit) {
    val nav = rememberNavController()
    var pendingIncoming by remember { mutableStateOf<Incoming?>(null) }
    LaunchedEffect(incoming) {
        if (incoming != null) {
            pendingIncoming = incoming
            onIncomingHandled()
            nav.popBackStack("library", inclusive = false)
        }
    }
    NavHost(
        navController = nav,
        startDestination = "library",
        enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 8 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(320)) { it / 8 } }
    ) {
        composable("library") {
            LibraryScreen(
                incoming = pendingIncoming,
                onIncomingConsumed = { pendingIncoming = null },
                openBook = { nav.navigate("reader/$it") },
                openVoices = { nav.navigate("voices") },
                openSettings = { nav.navigate("settings") }
            )
        }
        composable("reader/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            ReaderScreen(bookId = id, onBack = { nav.popBackStack() }, openVoices = { nav.navigate("voices") })
        }
        composable("voices") { VoicesScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }, openVoices = { nav.navigate("voices") }) }
    }
}
