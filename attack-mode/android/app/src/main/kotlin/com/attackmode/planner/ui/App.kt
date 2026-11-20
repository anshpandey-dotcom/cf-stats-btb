package com.attackmode.planner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as M3Text
import com.attackmode.planner.data.Store
import com.attackmode.planner.logic.Dates

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Rounded.Bolt),
    Tab("habits", "Habits", Icons.Rounded.CheckCircle),
    Tab("focus", "Focus", Icons.Rounded.Timer),
    Tab("journal", "Journal", Icons.Rounded.AutoStories),
    Tab("more", "More", Icons.Rounded.MoreHoriz),
)

fun routeForUrl(url: String): String? = when {
    url.isBlank() -> null
    url == "/today" -> "today"
    url == "/tomorrow" -> "tomorrow"
    url == "/habits" -> "habits"
    url == "/focus" -> "focus"
    url == "/journal" -> "journal"
    url.startsWith("/day/") -> "day/" + url.removePrefix("/day/")
    else -> null
}

@Composable
fun AttackApp(pendingRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    val state by Store.state.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteHandled()
        }
    }

    Scaffold(
        containerColor = Palette.Black,
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar(containerColor = Palette.Surface, contentColor = Palette.Text) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { nav.goTop(tab.route) },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Accent,
                                selectedTextColor = Palette.Accent,
                                indicatorColor = Palette.AccentSoft,
                                unselectedIconColor = Palette.Muted,
                                unselectedTextColor = Palette.Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            NavHost(nav, startDestination = "today") {
                composable("today") {
                    DayScreen(state, Dates.today(), { nav.navigate(dayRoute(it)) }, { nav.navigate("habit/$it") })
                }
                composable("tomorrow") {
                    DayScreen(state, Dates.shift(Dates.today(), 1), { nav.navigate(dayRoute(it)) }, { nav.navigate("habit/$it") })
                }
                composable("day/{date}") { entry ->
                    val date = entry.arguments?.getString("date") ?: Dates.today()
                    DayScreen(state, date, { nav.navigate(dayRoute(it)) }, { nav.navigate("habit/$it") })
                }
                composable("habits") { HabitsScreen(state, { nav.navigate("habit/$it") }, { nav.navigate("habitform") }) }
                composable("habit/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: ""
                    HabitDetailScreen(state, id, { nav.popBackStack() }, { nav.navigate("habitform?id=$id") })
                }
                composable("habitform") { HabitFormScreen(state, null) { nav.popBackStack() } }
                composable("habitform?id={id}") { entry ->
                    HabitFormScreen(state, entry.arguments?.getString("id")) { nav.popBackStack() }
                }
                composable("focus") { FocusScreen(state) }
                composable("journal") { SoonScreen("Journal") }
                composable("more") { MoreScreen { nav.navigate(dayOrRoute(it)) } }
                composable("settings") { SettingsScreen(state) { nav.popBackStack() } }
                composable("reminders") { RemindersScreen(state) { nav.popBackStack() } }
                composable("nudges") { NudgesScreen(state) { nav.popBackStack() } }
                listOf("insights", "woop", "manual", "history", "calendar", "stats").forEach { route ->
                    composable(route) { SoonScreen(route.replaceFirstChar { it.uppercase() }) { nav.popBackStack() } }
                }
            }
        }
    }
}

private fun dayRoute(date: String): String = when (date) {
    Dates.today() -> "today"
    Dates.shift(Dates.today(), 1) -> "tomorrow"
    else -> "day/$date"
}

private fun dayOrRoute(route: String) = route

private fun NavHostController.goTop(route: String) {
    navigate(route) {
        popUpTo("today") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun SoonScreen(title: String, onBack: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize()) {
        if (onBack != null) BackBar(title, onBack)
        else ScreenTitle("Coming next", title, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun AppRoot(pendingRoute: String?, onRouteHandled: () -> Unit) {
    AttackTheme { AttackApp(pendingRoute, onRouteHandled) }
}
