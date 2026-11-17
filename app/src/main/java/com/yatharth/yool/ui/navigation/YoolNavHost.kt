package com.yatharth.yool.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yatharth.yool.ui.day.DayScreen
import com.yatharth.yool.ui.graphs.GraphsScreen
import com.yatharth.yool.ui.history.HistoryScreen
import com.yatharth.yool.ui.insights.InsightsScreen
import com.yatharth.yool.ui.settings.SettingsScreen
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.SecondaryBg
import com.yatharth.yool.ui.theme.dotGridBackground
import java.time.LocalDate

object Routes {
    const val TODAY = "today"
    const val HISTORY = "history"
    const val GRAPHS = "graphs"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val DAY = "day/{epochDay}"
    fun day(date: LocalDate) = "day/${date.toEpochDay()}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Outlined.Today),
    Tab(Routes.HISTORY, "History", Icons.Outlined.CalendarMonth),
    Tab(Routes.GRAPHS, "Graphs", Icons.Outlined.ShowChart),
    Tab(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun YoolNavHost(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize().dotGridBackground(),
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = SecondaryBg, contentColor = Ink) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { navController.navigateTab(tab.route) },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label.uppercase(), style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Coral,
                            selectedTextColor = Coral,
                            indicatorColor = Coral.copy(alpha = 0.12f),
                            unselectedIconColor = Ink,
                            unselectedTextColor = Ink,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.TODAY) {
            composable(Routes.TODAY) { DayScreen(LocalDate.now(), padding) }
            composable(Routes.HISTORY) { HistoryScreen(padding) { navController.navigate(Routes.day(it)) } }
            composable(Routes.GRAPHS) { GraphsScreen(padding) }
            composable(Routes.INSIGHTS) { InsightsScreen(padding) }
            composable(Routes.SETTINGS) { SettingsScreen(padding) }
            composable(Routes.DAY, arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) { entry ->
                val date = LocalDate.ofEpochDay(entry.arguments!!.getLong("epochDay"))
                DayScreen(
                    date = date,
                    contentPadding = padding,
                    onNavigateDay = { d ->
                        navController.navigate(Routes.day(d)) { popUpTo(Routes.DAY) { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
