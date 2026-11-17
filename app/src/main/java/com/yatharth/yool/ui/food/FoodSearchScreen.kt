package com.yatharth.yool.ui.food

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.data.model.Meal
import java.time.LocalDate

/**
 * Pick a food (search, recent, frequent, saved meals) and log it into [date]/[meal]; pops back after logging.
 * Owned by feat/food-diary.
 */
@Composable
fun FoodSearchScreen(
    date: LocalDate,
    meal: Meal,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onNewFood: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenTitle("Add food")
        EmptyState("Coming soon", "This screen is being built.")
    }
}
