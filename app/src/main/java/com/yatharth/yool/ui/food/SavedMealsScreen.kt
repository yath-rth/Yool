package com.yatharth.yool.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.db.SavedMealWithItems
import com.yatharth.yool.domain.SavedMealMath
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
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

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        BackTitle("Saved meals", onBack)
        val list = meals.orEmpty()
        if (meals != null && list.isEmpty()) {
            EmptyState("No saved meals", "Save a meal from the food diary, or build one here.", Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.meal.id }) { m ->
                    val t = SavedMealMath.total(m)
                    ShadowCard(Modifier.fillMaxWidth(), onClick = { onEdit(m.meal.id) }) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(m.meal.name.uppercase(), style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "${m.items.size} ${if (m.items.size == 1) "item" else "items"} · ${Math.round(t.kcal)} kcal · ${Math.round(t.proteinG)} g protein",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                if (m.items.isNotEmpty()) {
                                    val more = m.items.size - 3
                                    Text(
                                        m.items.take(3).joinToString(", ") { it.food.name } + if (more > 0) " +$more more" else "",
                                        style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            Icon(
                                Icons.Outlined.Delete, "Delete ${m.meal.name}", tint = Coral,
                                modifier = Modifier.clickable { deleting = m }.padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
        TxButton("New saved meal", { onEdit(0) }, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Icons.Outlined.Add)
    }

    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete saved meal?") },
            text = { Text("\"${m.meal.name}\" will be removed. Food already logged is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.deleteSavedMeal(m.meal.id) }
                    deleting = null
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Ink) } },
        )
    }
}
