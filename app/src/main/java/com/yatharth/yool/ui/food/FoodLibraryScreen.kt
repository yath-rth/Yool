package com.yatharth.yool.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.domain.SavedMealMath
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.components.TxSwitch
import com.yatharth.yool.ui.components.TxTextField
import kotlinx.coroutines.launch

/** Browse the whole food library (incl. archived): edit, archive/unarchive, add. */
@Composable
fun FoodLibraryScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEditFood: (foodId: Long) -> Unit,
    onNewFood: () -> Unit,
) {
    val repo = LocalContext.current.appContainer.foodRepository
    val foods by remember { repo.observeAllFoods() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    val shown = remember(foods, query, showArchived) { SavedMealMath.filterFoods(foods, query, showArchived) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TxTopBar(
            "My foods", modifier = Modifier.padding(horizontal = 16.dp), subtitle = "${shown.size} ${if (shown.size == 1) "food" else "foods"}", onBack = onBack,
            actions = { TxIconButton(Icons.Outlined.Add, "New food", onNewFood) },
        )
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TxTextField(query, { query = it }, "Search", Modifier.fillMaxWidth(), placeholder = "Name or brand")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SHOW ARCHIVED", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TxSwitch(showArchived, { showArchived = it })
            }
            if (shown.isEmpty()) {
                EmptyState(
                    if (foods.isEmpty()) "No foods yet" else "No matches",
                    if (foods.isEmpty()) "Add a food to start building your library." else "Try another search or show archived foods.",
                    Modifier.weight(1f),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        TxListGroup {
                            shown.forEachIndexed { i, f ->
                                TxListRow(
                                    f.name,
                                    onClick = { onEditFood(f.id) },
                                    modifier = Modifier.alpha(if (f.archived) 0.5f else 1f),
                                    subtitle = listOfNotNull(f.brand, f.servingLabel).joinToString(" · ") +
                                        (if (f.archived) " · Archived" else ""),
                                    value = "${FoodInput.kcal(f.kcal)} kcal",
                                    chevron = false,
                                    divider = i < shown.lastIndex,
                                    trailing = {
                                        TxIconButton(
                                            if (f.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                            if (f.archived) "Unarchive ${f.name}" else "Archive ${f.name}",
                                            { scope.launch { repo.setArchived(f.id, !f.archived) } },
                                            size = 32.dp,
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
