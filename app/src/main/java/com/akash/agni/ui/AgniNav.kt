package com.akash.agni.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Restaurant
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.akash.agni.ui.coach.CoachScreen
import com.akash.agni.ui.food.FoodScreen
import com.akash.agni.ui.progress.ProgressScreen
import com.akash.agni.ui.rewards.RewardsScreen
import com.akash.agni.ui.settings.SettingsScreen
import com.akash.agni.ui.today.TodayScreen
import com.akash.agni.ui.workout.PlayerScreen
import com.akash.agni.ui.workout.WorkoutScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Rounded.Home),
    Tab("workout", "Workout", Icons.Rounded.FitnessCenter),
    Tab("food", "Food", Icons.Rounded.Restaurant),
    Tab("progress", "Progress", Icons.Rounded.Insights),
    Tab("rewards", "Rewards", Icons.Rounded.EmojiEvents),
)

fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

fun NavHostController.startWorkout(workoutId: String) = navigate("player/$workoutId")

@Composable
fun AgniNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(padding)) {
            composable("today") { TodayScreen(nav) }
            composable("workout") { WorkoutScreen(nav) }
            composable("food") { FoodScreen() }
            composable("progress") { ProgressScreen() }
            composable("rewards") { RewardsScreen() }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("coach") { CoachScreen(onBack = { nav.popBackStack() }) }
            composable(
                "player/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
            ) { e ->
                PlayerScreen(
                    workoutId = e.arguments?.getString("workoutId").orEmpty(),
                    onExit = { nav.popBackStack() },
                )
            }
        }
    }
}
