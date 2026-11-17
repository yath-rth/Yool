package com.yatharth.yool.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yatharth.yool.data.model.Meal
import com.yatharth.yool.ui.day.DayScreen
import com.yatharth.yool.ui.food.FoodDiaryScreen
import com.yatharth.yool.ui.food.FoodEditorScreen
import com.yatharth.yool.ui.food.FoodLibraryScreen
import com.yatharth.yool.ui.food.FoodSearchScreen
import com.yatharth.yool.ui.food.SavedMealEditorScreen
import com.yatharth.yool.ui.food.SavedMealsScreen
import com.yatharth.yool.ui.graphs.GraphsScreen
import com.yatharth.yool.ui.history.HistoryScreen
import com.yatharth.yool.ui.insights.InsightsScreen
import com.yatharth.yool.ui.settings.SettingsScreen
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.workout.ActiveWorkoutBanner
import com.yatharth.yool.ui.workout.ActiveWorkoutScreen
import com.yatharth.yool.ui.workout.ExerciseDetailScreen
import com.yatharth.yool.ui.workout.ExerciseEditorScreen
import com.yatharth.yool.ui.workout.ExerciseLibraryScreen
import com.yatharth.yool.ui.workout.TemplateEditorScreen
import com.yatharth.yool.ui.workout.WorkoutDetailScreen
import com.yatharth.yool.ui.workout.WorkoutHistoryScreen
import com.yatharth.yool.ui.workout.WorkoutHomeScreen
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

    const val FOOD_DIARY = "food/{epochDay}"
    fun foodDiary(date: LocalDate) = "food/${date.toEpochDay()}"
    const val FOOD_ADD = "food/{epochDay}/add/{meal}"
    fun foodAdd(date: LocalDate, meal: Meal) = "food/${date.toEpochDay()}/add/${meal.name}"
    /** foodId 0 = new food. */
    const val FOOD_EDITOR = "foodEditor/{foodId}"
    fun foodEditor(foodId: Long = 0) = "foodEditor/$foodId"
    const val FOOD_LIBRARY = "foodLibrary"
    const val SAVED_MEALS = "savedMeals"
    /** mealId 0 = new saved meal. */
    const val SAVED_MEAL_EDITOR = "savedMeal/{mealId}"
    fun savedMealEditor(mealId: Long = 0) = "savedMeal/$mealId"

    const val WORKOUT = "workout"
    const val ACTIVE_WORKOUT = "workout/active"
    /** Edit a finished workout. */
    const val WORKOUT_EDIT = "workout/edit/{workoutId}"
    fun workoutEdit(id: Long) = "workout/edit/$id"
    const val WORKOUT_DETAIL = "workout/detail/{workoutId}"
    fun workoutDetail(id: Long) = "workout/detail/$id"
    const val WORKOUT_HISTORY = "workout/history"
    /** templateId 0 = new template. */
    const val TEMPLATE_EDITOR = "template/{templateId}"
    fun templateEditor(id: Long = 0) = "template/$id"
    const val EXERCISES = "exercises"
    const val EXERCISE_DETAIL = "exercise/{exerciseId}"
    fun exerciseDetail(id: Long) = "exercise/$id"
    /** exerciseId 0 = new exercise. */
    const val EXERCISE_EDITOR = "exerciseEditor/{exerciseId}"
    fun exerciseEditor(id: Long = 0) = "exerciseEditor/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Outlined.Today),
    Tab(Routes.WORKOUT, "Workout", Icons.Outlined.FitnessCenter),
    Tab(Routes.HISTORY, "History", Icons.Outlined.CalendarMonth),
    Tab(Routes.GRAPHS, "Graphs", Icons.Outlined.ShowChart),
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
          Column {
            if (current != Routes.ACTIVE_WORKOUT) ActiveWorkoutBanner(onOpen = { navController.navigate(Routes.ACTIVE_WORKOUT) { launchSingleTop = true } })
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
          }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.TODAY) {
            composable(Routes.TODAY) {
                DayScreen(
                    LocalDate.now(), padding,
                    onOpenFood = { navController.navigate(Routes.foodDiary(it)) },
                    onStartWorkout = { navController.navigate(Routes.ACTIVE_WORKOUT) },
                    onOpenWorkout = { navController.navigate(Routes.workoutDetail(it)) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen(padding) { navController.navigate(Routes.day(it)) } }
            composable(Routes.GRAPHS) { GraphsScreen(padding) }
            composable(Routes.INSIGHTS) { InsightsScreen(padding) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    padding,
                    onOpenFoodLibrary = { navController.navigate(Routes.FOOD_LIBRARY) },
                    onOpenSavedMeals = { navController.navigate(Routes.SAVED_MEALS) },
                )
            }
            composable(Routes.DAY, arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) { entry ->
                val date = LocalDate.ofEpochDay(entry.arguments!!.getLong("epochDay"))
                DayScreen(
                    date = date,
                    contentPadding = padding,
                    onNavigateDay = { d ->
                        navController.navigate(Routes.day(d)) { popUpTo(Routes.DAY) { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() },
                    onOpenFood = { navController.navigate(Routes.foodDiary(it)) },
                    onStartWorkout = { navController.navigate(Routes.ACTIVE_WORKOUT) },
                    onOpenWorkout = { navController.navigate(Routes.workoutDetail(it)) },
                )
            }
            foodGraph(navController, padding)
            workoutGraph(navController, padding)
        }
    }
}

private val epochDayArg = navArgument("epochDay") { type = NavType.LongType }

private fun NavGraphBuilder.foodGraph(nav: NavHostController, padding: PaddingValues) {
    composable(Routes.FOOD_DIARY, arguments = listOf(epochDayArg)) { entry ->
        val date = LocalDate.ofEpochDay(entry.arguments!!.getLong("epochDay"))
        FoodDiaryScreen(
            date = date,
            contentPadding = padding,
            onBack = { nav.popBackStack() },
            onAddFood = { meal -> nav.navigate(Routes.foodAdd(date, meal)) },
            onNavigateDay = { d -> nav.navigate(Routes.foodDiary(d)) { popUpTo(Routes.FOOD_DIARY) { inclusive = true } } },
            onOpenSavedMeals = { nav.navigate(Routes.SAVED_MEALS) },
        )
    }
    composable(
        Routes.FOOD_ADD,
        arguments = listOf(epochDayArg, navArgument("meal") { type = NavType.StringType }),
    ) { entry ->
        FoodSearchScreen(
            date = LocalDate.ofEpochDay(entry.arguments!!.getLong("epochDay")),
            meal = Meal.valueOf(entry.arguments!!.getString("meal")!!),
            contentPadding = padding,
            onBack = { nav.popBackStack() },
            onNewFood = { nav.navigate(Routes.foodEditor()) },
        )
    }
    composable(Routes.FOOD_EDITOR, arguments = listOf(navArgument("foodId") { type = NavType.LongType })) { entry ->
        FoodEditorScreen(entry.arguments!!.getLong("foodId"), padding, onDone = { nav.popBackStack() })
    }
    composable(Routes.FOOD_LIBRARY) {
        FoodLibraryScreen(
            padding,
            onBack = { nav.popBackStack() },
            onEditFood = { nav.navigate(Routes.foodEditor(it)) },
            onNewFood = { nav.navigate(Routes.foodEditor()) },
        )
    }
    composable(Routes.SAVED_MEALS) {
        SavedMealsScreen(padding, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.savedMealEditor(it)) })
    }
    composable(Routes.SAVED_MEAL_EDITOR, arguments = listOf(navArgument("mealId") { type = NavType.LongType })) { entry ->
        SavedMealEditorScreen(entry.arguments!!.getLong("mealId"), padding, onDone = { nav.popBackStack() })
    }
}

private fun NavGraphBuilder.workoutGraph(nav: NavHostController, padding: PaddingValues) {
    fun idArg(name: String) = navArgument(name) { type = NavType.LongType }
    composable(Routes.WORKOUT) {
        WorkoutHomeScreen(
            padding,
            onOpenActive = { nav.navigate(Routes.ACTIVE_WORKOUT) { launchSingleTop = true } },
            onEditTemplate = { nav.navigate(Routes.templateEditor(it)) },
            onOpenHistory = { nav.navigate(Routes.WORKOUT_HISTORY) },
            onOpenWorkout = { nav.navigate(Routes.workoutDetail(it)) },
            onOpenExercises = { nav.navigate(Routes.EXERCISES) },
        )
    }
    composable(Routes.ACTIVE_WORKOUT) {
        ActiveWorkoutScreen(null, padding, onClose = { nav.popBackStack() }, onOpenExercise = { nav.navigate(Routes.exerciseDetail(it)) })
    }
    composable(Routes.WORKOUT_EDIT, arguments = listOf(idArg("workoutId"))) { e ->
        ActiveWorkoutScreen(e.arguments!!.getLong("workoutId"), padding, onClose = { nav.popBackStack() }, onOpenExercise = { nav.navigate(Routes.exerciseDetail(it)) })
    }
    composable(Routes.WORKOUT_DETAIL, arguments = listOf(idArg("workoutId"))) { e ->
        WorkoutDetailScreen(
            e.arguments!!.getLong("workoutId"), padding,
            onBack = { nav.popBackStack() },
            onEdit = { nav.navigate(Routes.workoutEdit(it)) },
            onOpenActive = { nav.navigate(Routes.ACTIVE_WORKOUT) { launchSingleTop = true } },
            onOpenExercise = { nav.navigate(Routes.exerciseDetail(it)) },
        )
    }
    composable(Routes.WORKOUT_HISTORY) {
        WorkoutHistoryScreen(padding, onBack = { nav.popBackStack() }, onOpenWorkout = { nav.navigate(Routes.workoutDetail(it)) })
    }
    composable(Routes.TEMPLATE_EDITOR, arguments = listOf(idArg("templateId"))) { e ->
        TemplateEditorScreen(e.arguments!!.getLong("templateId"), padding, onDone = { nav.popBackStack() })
    }
    composable(Routes.EXERCISES) {
        ExerciseLibraryScreen(
            padding, onBack = { nav.popBackStack() },
            onOpenExercise = { nav.navigate(Routes.exerciseDetail(it)) },
            onNewExercise = { nav.navigate(Routes.exerciseEditor()) },
        )
    }
    composable(Routes.EXERCISE_DETAIL, arguments = listOf(idArg("exerciseId"))) { e ->
        ExerciseDetailScreen(
            e.arguments!!.getLong("exerciseId"), padding, onBack = { nav.popBackStack() },
            onEdit = { nav.navigate(Routes.exerciseEditor(it)) },
            onOpenWorkout = { nav.navigate(Routes.workoutDetail(it)) },
        )
    }
    composable(Routes.EXERCISE_EDITOR, arguments = listOf(idArg("exerciseId"))) { e ->
        ExerciseEditorScreen(e.arguments!!.getLong("exerciseId"), padding, onDone = { nav.popBackStack() })
    }
}

fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
