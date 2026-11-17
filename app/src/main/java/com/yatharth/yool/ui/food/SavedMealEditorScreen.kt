package com.yatharth.yool.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.FoodRepository
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.domain.Nutrients
import com.yatharth.yool.domain.SavedMealMath
import com.yatharth.yool.domain.perServing
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorItem(val food: Food, val servings: String = "1")

data class SavedMealEditorState(
    val loading: Boolean = true,
    val name: String = "",
    val items: List<EditorItem> = emptyList(),
) {
    val totals: Nutrients
        get() = SavedMealMath.total(items.mapNotNull { i -> SavedMealMath.parseServings(i.servings)?.let { i.food to it } })
    val canSave: Boolean get() = !loading && SavedMealMath.canSave(name, items.map { it.servings })
}

class SavedMealEditorViewModel(private val repo: FoodRepository, private val mealId: Long) : ViewModel() {
    private val _state = MutableStateFlow(SavedMealEditorState(loading = mealId != 0L))
    val state: StateFlow<SavedMealEditorState> = _state

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Food>> = _query.flatMapLatest { repo.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (mealId != 0L) viewModelScope.launch {
            val m = repo.getSavedMeal(mealId)
            _state.value = if (m == null) SavedMealEditorState(loading = false)
            else SavedMealEditorState(
                loading = false, name = m.meal.name,
                items = m.items.map { EditorItem(it.food, SavedMealMath.formatServings(it.item.servings)) },
            )
        }
    }

    fun setQuery(q: String) { _query.value = q }
    fun setName(n: String) = _state.update { it.copy(name = n) }
    fun addFood(f: Food) = _state.update { it.copy(items = it.items + EditorItem(f)) }
    fun removeAt(i: Int) = _state.update { it.copy(items = it.items.filterIndexed { idx, _ -> idx != i }) }
    fun setServings(i: Int, text: String) =
        _state.update { s -> s.copy(items = s.items.mapIndexed { idx, e -> if (idx == i) e.copy(servings = text) else e }) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repo.saveSavedMeal(mealId, s.name, s.items.map { it.food.id to SavedMealMath.parseServings(it.servings)!! })
            onDone()
        }
    }
}

/** Create ([mealId] == 0) or edit a saved meal: name + foods with servings. */
@Composable
fun SavedMealEditorScreen(
    mealId: Long,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
) {
    val repo = LocalContext.current.appContainer.foodRepository
    val vm: SavedMealEditorViewModel = viewModel(
        key = "saved-meal-$mealId",
        factory = viewModelFactory { initializer { SavedMealEditorViewModel(repo, mealId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        BackTitle(if (mealId == 0L) "New saved meal" else "Edit saved meal", onDone)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TxTextField(state.name, vm::setName, "Name", Modifier.fillMaxWidth(), placeholder = "e.g. Usual breakfast", maxLength = 60)

            SectionLabel("Foods")
            state.items.forEachIndexed { i, item ->
                ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.food.name, style = MaterialTheme.typography.titleSmall)
                            Text(item.food.servingLabel, style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Outlined.Close, "Remove ${item.food.name}", Modifier.clickable { vm.removeAt(i) }.padding(4.dp))
                    }
                    TxTextField(item.servings, { vm.setServings(i, it) }, "Servings", Modifier.fillMaxWidth(), numeric = true)
                }
            }
            TxButton("Add food", { picking = true }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)

            val t = state.totals
            SectionLabel("Totals")
            ShadowCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatValue("Kcal", Fmt.int(Math.round(t.kcal)), Modifier.weight(1f))
                    StatValue("Protein", Fmt.one(t.proteinG), Modifier.weight(1f), unit = "g")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatValue("Carbs", Fmt.one(t.carbsG), Modifier.weight(1f), unit = "g")
                    StatValue("Fat", Fmt.one(t.fatG), Modifier.weight(1f), unit = "g")
                    StatValue("Fiber", Fmt.one(t.fiberG), Modifier.weight(1f), unit = "g")
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("Cancel", onDone, Modifier.weight(1f))
            TxButton("Save", { vm.save(onDone) }, Modifier.weight(1f), bg = Coral, textColor = ItemsBg, enabled = state.canSave)
        }
    }

    if (picking) {
        FoodPickerDialog(vm, onDismiss = { picking = false; vm.setQuery("") }) {
            vm.addFood(it)
            picking = false
            vm.setQuery("")
        }
    }
}

@Composable
private fun FoodPickerDialog(vm: SavedMealEditorViewModel, onDismiss: () -> Unit, onPick: (Food) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(4.dp)
        Column(
            Modifier.fillMaxWidth().background(ItemsBg, shape).border(2.dp, Ink, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TxTextField(query, vm::setQuery, "Search foods", Modifier.fillMaxWidth(), placeholder = "Name or brand")
            if (results.isEmpty()) Text("No foods found.", style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = { it.id }) { f ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 6.dp)) {
                        Text(f.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(f.brand, f.servingLabel).joinToString(" · ") + " · " + macroLine(f.perServing),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            TxButton("Close", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
