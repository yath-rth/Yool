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
 * Food diary for [date]: four meal sections with logged entries and day totals.
 * Owned by feat/food-diary.
 */
@Composable
fun FoodDiaryScreen(
    date: LocalDate,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAddFood: (Meal) -> Unit,
    onNavigateDay: (LocalDate) -> Unit,
    onOpenSavedMeals: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenTitle("Food diary")
        EmptyState("Coming soon", "This screen is being built.")
    }
}
