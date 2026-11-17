package com.yatharth.yool.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.FoodRepository
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.data.db.SavedMealWithItems
import com.yatharth.yool.data.model.Meal
import com.yatharth.yool.domain.FoodMath
import com.yatharth.yool.domain.perServing
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxDialogButtons
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FoodSearchUiState(
    val query: String = "",
    val results: List<Food> = emptyList(),
    val recent: List<Food> = emptyList(),
    val frequent: List<Food> = emptyList(),
    val savedMeals: List<SavedMealWithItems> = emptyList(),
    /** null until the library size is known. */
    val libraryEmpty: Boolean? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FoodSearchViewModel(private val repo: FoodRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    private val results = query.debounce(150).flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else repo.search(q)
    }

    val state: StateFlow<FoodSearchUiState> = combine(
        query, results, repo.observeRecent(), repo.observeFrequent(), repo.observeSavedMeals(),
    ) { q, res, recent, frequent, saved ->
        FoodSearchUiState(q, res, recent, frequent, saved)
    }.combine(repo.observeAllFoods().map { it.isEmpty() }) { s, empty -> s.copy(libraryEmpty = empty) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodSearchUiState())

    fun setQuery(q: String) { query.value = q }

    fun log(food: Food, date: LocalDate, meal: Meal, servings: Double, then: () -> Unit) {
        viewModelScope.launch { repo.log(food, date, meal, servings); then() }
    }

    fun logSavedMeal(id: Long, date: LocalDate, meal: Meal, then: () -> Unit) {
        viewModelScope.launch { repo.logSavedMeal(id, date, meal); then() }
    }
}

/** Pick a food (search, recent, frequent, saved meals) and log it into [date]/[meal]; pops back after logging. */
@Composable
fun FoodSearchScreen(
    date: LocalDate,
    meal: Meal,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onNewFood: () -> Unit,
) {
    val repo = LocalContext.current.appContainer.foodRepository
    val vm: FoodSearchViewModel = viewModel(factory = viewModelFactory { initializer { FoodSearchViewModel(repo) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var picked by remember { mutableStateOf<Food?>(null) }
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TxTopBar(
            "Add food", subtitle = "${meal.label} � ${com.yatharth.yool.ui.Fmt.date(date)}", onBack = onBack,
            actions = { TxIconButton(Icons.Outlined.Add, "New food", onNewFood) },
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TxTextField(query, { query = it; vm.setQuery(it) }, "Search foods", Modifier.fillMaxWidth(), placeholder = "e.g. oats")

            if (state.libraryEmpty == true && state.savedMeals.isEmpty()) {
                EmptyState("No foods yet", "Add your first food. It's saved to your library for next time.")
                TxButton("New food", onNewFood, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg, icon = Icons.Outlined.Add)
            } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (searching) {
                    if (state.results.isEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("No matches", style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f))
                                TxButton("New food", onNewFood, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg, icon = Icons.Outlined.Add)
                            }
                        }
                    } else {
                        item { FoodGroup(null, state.results) { picked = it } }
                    }
                } else {
                    if (state.savedMeals.isNotEmpty()) {
                        item {
                            TxListGroup(title = "Saved meals") {
                                state.savedMeals.forEachIndexed { i, m ->
                                    val kcal = m.items.sumOf { it.food.kcal * it.item.servings }
                                    TxListRow(
                                        m.meal.name,
                                        onClick = { vm.logSavedMeal(m.meal.id, date, meal, onBack) },
                                        subtitle = "${m.items.size} ${if (m.items.size == 1) "item" else "items"}",
                                        value = "${FoodInput.kcal(kcal)} kcal",
                                        chevron = false,
                                        divider = i < state.savedMeals.lastIndex,
                                    )
                                }
                            }
                        }
                    }
                    if (state.recent.isNotEmpty()) item { FoodGroup("Recent", state.recent) { picked = it } }
                    if (state.frequent.isNotEmpty()) item { FoodGroup("Frequent (last 60 days)", state.frequent) { picked = it } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    picked?.let { food ->
        ServingsDialog(
            food = food,
            onDismiss = { picked = null },
            onLog = { servings -> vm.log(food, date, meal, servings, onBack) },
        )
    }
}

@Composable
private fun FoodGroup(title: String?, foods: List<Food>, onClick: (Food) -> Unit) {
    TxListGroup(title = title) {
        foods.forEachIndexed { i, food ->
            TxListRow(
                food.name,
                onClick = { onClick(food) },
                subtitle = listOfNotNull(food.brand, food.servingLabel).joinToString(" � "),
                value = "${FoodInput.kcal(food.kcal)} kcal",
                chevron = false,
                divider = i < foods.lastIndex,
            )
        }
    }
}

/** Servings (and grams, when known) input with a live nutrient preview. */
@Composable
private fun ServingsDialog(food: Food, onDismiss: () -> Unit, onLog: (Double) -> Unit) {
    var servings by remember { mutableStateOf("1") }
    var grams by remember { mutableStateOf(FoodInput.gramsFromServings(food.servingGrams, "1")) }
    val hasGrams = (food.servingGrams ?: 0.0) > 0
    val n = FoodInput.parsePositive(servings)?.let { food.perServing * it }

    TxDialog(food.name, onDismiss) {
        Text(
            listOfNotNull(food.brand, food.servingLabel).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f),
        )
        TxTextField(servings, {
            servings = it
            if (hasGrams) grams = FoodInput.gramsFromServings(food.servingGrams, it)
        }, "Servings", Modifier.fillMaxWidth(), numeric = true)
        if (hasGrams) {
            TxTextField(grams, {
                grams = it
                FoodInput.servingsFromGrams(food.servingGrams, it).takeIf { s -> s.isNotEmpty() }?.let { s -> servings = s }
            }, "Grams", Modifier.fillMaxWidth(), unit = "g", numeric = true)
        }
        Text(
            if (n != null) "${FoodInput.kcal(n.kcal)} kcal · P ${FoodInput.grams(n.proteinG)} · C ${FoodInput.grams(n.carbsG)} · F ${FoodInput.grams(n.fatG)} · Fiber ${FoodInput.grams(n.fiberG)} g"
            else "Enter a servings amount above 0.",
            style = MaterialTheme.typography.labelMedium, color = if (n != null) Ink else Coral,
        )
        TxDialogButtons("Log", { FoodInput.parsePositive(servings)?.let(onLog) }, onDismiss, confirmEnabled = n != null)
    }
}
