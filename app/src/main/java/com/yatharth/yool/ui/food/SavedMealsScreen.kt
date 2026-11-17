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

/**
 * List of saved meals (create / edit / delete). Owned by feat/saved-meals.
 */
@Composable
fun SavedMealsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (mealId: Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenTitle("Saved meals")
        EmptyState("Coming soon", "This screen is being built.")
    }
}
