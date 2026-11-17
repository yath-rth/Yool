package com.yatharth.yool.ui.food

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.db.SavedMealWithItems
import com.yatharth.yool.domain.SavedMealMath
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.TxConfirmDialog
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.theme.Coral
import kotlinx.coroutines.launch

/** List of saved meals (create / edit / delete). */
@Composable
fun SavedMealsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (mealId: Long) -> Unit,
) {
    val repo = LocalContext.current.appContainer.foodRepository
    val meals by remember { repo.observeSavedMeals() }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<SavedMealWithItems?>(null) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TxTopBar(
            "Saved meals", modifier = Modifier.padding(horizontal = 16.dp), onBack = onBack,
            actions = { TxIconButton(Icons.Outlined.Add, "New saved meal", { onEdit(0) }) },
        )
        val list = meals.orEmpty()
        if (meals != null && list.isEmpty()) {
            EmptyState("No saved meals", "Save a meal from the food diary, or build one here.", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                item {
                    TxListGroup {
                        list.forEachIndexed { i, m ->
                            val t = SavedMealMath.total(m)
                            TxListRow(
                                m.meal.name,
                                onClick = { onEdit(m.meal.id) },
                                subtitle = "${m.items.size} ${if (m.items.size == 1) "item" else "items"} · ${Math.round(t.proteinG)} g protein",
                                value = "${Math.round(t.kcal)} kcal",
                                chevron = false,
                                divider = i < list.lastIndex,
                                trailing = {
                                    TxIconButton(Icons.Outlined.Delete, "Delete ${m.meal.name}", { deleting = m }, size = 32.dp, tint = Coral)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    deleting?.let { m ->
        TxConfirmDialog(
            "Delete saved meal?",
            "\"${m.meal.name}\" will be removed. Food already logged is not affected.",
            "Delete",
            onConfirm = {
                scope.launch { repo.deleteSavedMeal(m.meal.id) }
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
