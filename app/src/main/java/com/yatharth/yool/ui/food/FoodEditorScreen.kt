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
 * Create ([foodId] == 0) or edit a library food. Calls [onDone] after save/archive/cancel.
 * Owned by feat/food-diary.
 */
@Composable
fun FoodEditorScreen(
    foodId: Long,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        ScreenTitle("Food")
        EmptyState("Coming soon", "This screen is being built.")
    }
}
