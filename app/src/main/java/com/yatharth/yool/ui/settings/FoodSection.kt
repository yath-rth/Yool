package com.yatharth.yool.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton

/** Entry points to the food library and saved meals. */
@Composable
fun FoodSection(onOpenFoodLibrary: () -> Unit, onOpenSavedMeals: () -> Unit) {
    ShadowCard {
        SectionLabel("Food")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("My foods", onOpenFoodLibrary, Modifier.weight(1f), icon = Icons.Outlined.Restaurant)
            TxButton("Saved meals", onOpenSavedMeals, Modifier.weight(1f), icon = Icons.Outlined.Bookmarks)
        }
    }
}
