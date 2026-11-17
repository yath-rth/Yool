package com.yatharth.yool.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow

/** Entry points to the food library and saved meals, with counts. */
@Composable
fun FoodSection(onOpenFoodLibrary: () -> Unit, onOpenSavedMeals: () -> Unit) {
    val repo = LocalContext.current.appContainer.foodRepository
    val foods by remember { repo.observeAllFoods() }.collectAsStateWithLifecycle(initialValue = null)
    val meals by remember { repo.observeSavedMeals() }.collectAsStateWithLifecycle(initialValue = null)
    val fc = foods?.count { !it.archived }
    val mc = meals?.size
    TxListGroup {
        TxListRow(
            "My foods", onClick = onOpenFoodLibrary, icon = Icons.Outlined.Restaurant,
            value = fc?.toString(),
        )
        TxListRow(
            "Saved meals", onClick = onOpenSavedMeals, icon = Icons.Outlined.Bookmarks,
            value = mc?.toString(), divider = false,
        )
    }
}
