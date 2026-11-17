package com.yatharth.yool.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton

/** Entry points to the food library and saved meals, with counts. */
@Composable
fun FoodSection(onOpenFoodLibrary: () -> Unit, onOpenSavedMeals: () -> Unit) {
    val repo = LocalContext.current.appContainer.foodRepository
    val foods by remember { repo.observeAllFoods() }.collectAsStateWithLifecycle(initialValue = null)
    val meals by remember { repo.observeSavedMeals() }.collectAsStateWithLifecycle(initialValue = null)
    ShadowCard {
        SectionLabel("Food")
        val f = foods
        val m = meals
        if (f != null && m != null) {
            val fc = f.count { !it.archived }
            Text(
                "$fc ${if (fc == 1) "food" else "foods"} · ${m.size} saved ${if (m.size == 1) "meal" else "meals"}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("My foods", onOpenFoodLibrary, Modifier.weight(1f), icon = Icons.Outlined.Restaurant)
            TxButton("Saved meals", onOpenSavedMeals, Modifier.weight(1f), icon = Icons.Outlined.Bookmarks)
        }
    }
}
