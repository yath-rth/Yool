package com.yatharth.yool.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
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
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.MetricColors
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

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            ScreenTitle("Add food", Modifier.weight(1f), subtitle = "${meal.label} · ${com.yatharth.yool.ui.Fmt.date(date)}")
        }
        TxTextField(query, { query = it; vm.setQuery(it) }, "Search foods", Modifier.fillMaxWidth(), placeholder = "e.g. oats")
        TxButton("+ New food", onClick = onNewFood, bg = Coral, textColor = ItemsBg, modifier = Modifier.fillMaxWidth())

        if (state.libraryEmpty == true && state.savedMeals.isEmpty()) {
            EmptyState("No foods yet", "Add your first food with + NEW FOOD. It's saved to your library for next time.")
        } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (searching) {
                if (state.results.isEmpty()) item { Text("No matches. Try + NEW FOOD.", style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f)) }
                items(state.results, key = { "r${it.id}" }) { FoodRow(it) { f -> picked = f } }
            } else {
                if (state.savedMeals.isNotEmpty()) {
                    item { SectionLabel("Saved meals") }
                    items(state.savedMeals, key = { "m${it.meal.id}" }) { m ->
                        val kcal = m.items.sumOf { it.food.kcal * it.item.servings }
                        ShadowCard(Modifier.fillMaxWidth(), shadow = 3.dp, contentPadding = 12.dp, onClick = {
                            vm.logSavedMeal(m.meal.id, date, meal, onBack)
                        }) {
                            Text(m.meal.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${m.items.size} ${if (m.items.size == 1) "item" else "items"} · ${FoodInput.kcal(kcal)} kcal",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (state.recent.isNotEmpty()) {
                    item { SectionLabel("Recent") }
                    items(state.recent, key = { "c${it.id}" }) { FoodRow(it) { f -> picked = f } }
                }
                if (state.frequent.isNotEmpty()) {
                    item { SectionLabel("Frequent (last 60 days)") }
                    items(state.frequent, key = { "f${it.id}" }) { FoodRow(it) { f -> picked = f } }
                }
                item { Spacer(Modifier.height(24.dp)) }
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
private fun FoodRow(food: Food, onClick: (Food) -> Unit) {
    ShadowCard(Modifier.fillMaxWidth(), shadow = 3.dp, contentPadding = 12.dp, onClick = { onClick(food) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.titleSmall)
                val sub = listOfNotNull(food.brand, food.servingLabel).joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${FoodInput.kcal(food.kcal)} kcal", style = MaterialTheme.typography.labelMedium, color = MetricColors.Calories)
                Text("${FoodInput.grams(food.proteinG)} g P", style = MaterialTheme.typography.labelSmall, color = MetricColors.Protein)
            }
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TxButton("Cancel", onDismiss, Modifier.weight(1f))
            TxButton("Log", { FoodInput.parsePositive(servings)?.let(onLog) }, Modifier.weight(1f), bg = Green, textColor = ItemsBg, enabled = n != null)
        }
    }
}
