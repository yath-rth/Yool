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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Icon
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
import com.yatharth.yool.domain.perServing
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
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

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        BackTitle("My foods", onBack)
        TxTextField(query, { query = it }, "Search", Modifier.fillMaxWidth(), placeholder = "Name or brand")
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${shown.size} ${if (shown.size == 1) "food" else "foods"}",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
            )
            Text("SHOW ARCHIVED", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
            TxSwitch(showArchived, { showArchived = it })
        }
        if (shown.isEmpty()) {
            EmptyState(
                if (foods.isEmpty()) "No foods yet" else "No matches",
                if (foods.isEmpty()) "Add a food to start building your library." else "Try another search or show archived foods.",
                Modifier.weight(1f),
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { f ->
                    ShadowCard(
                        Modifier.fillMaxWidth().alpha(if (f.archived) 0.5f else 1f),
                        onClick = { onEditFood(f.id) }, contentPadding = 12.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(f.name, style = MaterialTheme.typography.titleSmall)
                                Text(listOfNotNull(f.brand, f.servingLabel).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                                Text(macroLine(f.perServing), style = MaterialTheme.typography.labelSmall)
                                if (f.archived) TxChip("Archived", false, {}, Modifier.padding(top = 4.dp))
                            }
                            Icon(
                                if (f.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                if (f.archived) "Unarchive ${f.name}" else "Archive ${f.name}",
                                Modifier.clickable { scope.launch { repo.setArchived(f.id, !f.archived) } }.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
        TxButton("New food", onNewFood, Modifier.fillMaxWidth().padding(top = 8.dp), icon = Icons.Outlined.Add)
    }
}
