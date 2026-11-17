package com.yatharth.yool.ui.food

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.data.db.FoodLogEntry
import com.yatharth.yool.data.model.Meal
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.domain.Nutrients
import com.yatharth.yool.domain.TargetHistory
import com.yatharth.yool.domain.total
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxProgress
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxSegmented
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.MetricColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FoodDiaryUiState(
    val entries: List<FoodLogEntry> = emptyList(),
    val targets: TargetHistory = TargetHistory.Empty,
    val loggedDates: List<LocalDate> = emptyList(),
) {
    val total: Nutrients get() = entries.total()
    fun entriesFor(meal: Meal) = entries.filter { it.meal == meal }.sortedBy { it.createdAt }
}

class FoodDiaryViewModel(
    private val foods: FoodRepository,
    tracker: TrackerRepository,
    val date: LocalDate,
) : ViewModel() {
    val state: StateFlow<FoodDiaryUiState> = combine(
        foods.observeLog(date), tracker.observeTargets(), foods.observeLoggedDates(),
    ) { entries, targets, dates -> FoodDiaryUiState(entries, targets, dates.filter { it != date }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDiaryUiState())

    fun copyMeal(from: LocalDate, meal: Meal, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(foods.copyMeal(from, meal, date, meal)) }
    }

    fun saveAsMeal(meal: Meal, name: String, onDone: () -> Unit) {
        viewModelScope.launch { foods.saveMealFromDiary(date, meal, name); onDone() }
    }

    fun setServings(id: Long, servings: Double) { viewModelScope.launch { foods.setServings(id, servings) } }
    fun move(id: Long, meal: Meal) { viewModelScope.launch { foods.moveEntry(id, meal) } }
    fun delete(id: Long) { viewModelScope.launch { foods.deleteEntry(id) } }
    suspend fun foodFor(entry: FoodLogEntry): Food? = entry.foodId?.let { foods.getFood(it) }
}

/** Food diary for [date]: four meal sections with logged entries and day totals. */
@Composable
fun FoodDiaryScreen(
    date: LocalDate,
    contentPadding: PaddingValues,
    /** null when shown as the Food tab root. */
    onBack: (() -> Unit)?,
    onAddFood: (Meal) -> Unit,
    onNavigateDay: (LocalDate) -> Unit,
    onOpenSavedMeals: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.appContainer
    val vm: FoodDiaryViewModel = viewModel(
        key = "food-$date",
        factory = viewModelFactory { initializer { FoodDiaryViewModel(app.foodRepository, app.repository, date) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FoodLogEntry?>(null) }
    var copyInto by remember { mutableStateOf<Meal?>(null) }
    var saveMeal by remember { mutableStateOf<Meal?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            ScreenTitle("Food", Modifier.weight(1f), subtitle = Fmt.date(date))
            NavArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day") { onNavigateDay(date.minusDays(1)) }
            Spacer(Modifier.size(8.dp))
            if (date.isBefore(LocalDate.now())) NavArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day") { onNavigateDay(date.plusDays(1)) }
        }

        TotalsCard(state, date)

        for (meal in Meal.entries) {
            MealCard(
                meal = meal,
                entries = state.entriesFor(meal),
                onAdd = { onAddFood(meal) },
                onEntry = { editing = it },
                onCopy = { copyInto = meal },
                onSave = { saveMeal = meal },
            )
        }

        TxButton("Saved meals", onClick = onOpenSavedMeals, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { entry ->
        EntryDialog(
            entry = entry,
            vm = vm,
            onDismiss = { editing = null },
        )
    }

    copyInto?.let { meal ->
        TxDialog("Copy ${meal.label} from…", onDismiss = { copyInto = null }) {
            if (state.loggedDates.isEmpty()) {
                Text("No other days have food logged yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(Modifier.height(280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in state.loggedDates.take(30)) {
                        TxButton(Fmt.date(d), modifier = Modifier.fillMaxWidth(), onClick = {
                            copyInto = null
                            vm.copyMeal(d, meal) { n ->
                                val msg = if (n == 0) "Nothing logged for ${meal.label} on ${Fmt.date(d)}" else "Copied $n ${if (n == 1) "item" else "items"}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }
            TxButton("Cancel", { copyInto = null }, Modifier.fillMaxWidth())
        }
    }

    saveMeal?.let { meal ->
        var name by remember { mutableStateOf("") }
        TxDialog("Save ${meal.label} as meal", onDismiss = { saveMeal = null }) {
            TxTextField(name, { name = it }, "Name", Modifier.fillMaxWidth(), maxLength = 40, placeholder = "e.g. Usual breakfast")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TxButton("Cancel", { saveMeal = null }, Modifier.weight(1f))
                TxButton("Save", {
                    vm.saveAsMeal(meal, name) {
                        Toast.makeText(context, "Saved \"${name.trim()}\"", Toast.LENGTH_SHORT).show()
                    }
                    saveMeal = null
                }, Modifier.weight(1f), bg = Green, textColor = ItemsBg, enabled = name.isNotBlank())
            }
        }
    }
}

@Composable
private fun NavArrow(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(ItemsBg, RoundedCornerShape(4.dp))
            .border(1.5.dp, Ink, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(18.dp)) }
}

@Composable
private fun TotalsCard(state: FoodDiaryUiState, date: LocalDate) {
    val t = state.total
    val calTarget = state.targets.valueOn(TargetMetric.CALORIES, date)
    val proTarget = state.targets.valueOn(TargetMetric.PROTEIN, date)
    ShadowCard(Modifier.fillMaxWidth()) {
        SectionLabel("Day total")
        TargetRow("Calories", "${FoodInput.kcal(t.kcal)}", "kcal", calTarget, t.kcal, MetricColors.Calories, ceiling = true)
        TargetRow("Protein", FoodInput.grams(t.proteinG), "g", proTarget, t.proteinG, MetricColors.Protein, ceiling = false)
        Text(
            "Carbs ${FoodInput.grams(t.carbsG)} g · Fat ${FoodInput.grams(t.fatG)} g · Fiber ${FoodInput.grams(t.fiberG)} g",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun TargetRow(label: String, value: String, unit: String, target: Double?, actual: Double, color: androidx.compose.ui.graphics.Color, ceiling: Boolean) {
    val over = target != null && actual > target
    val hit = target != null && if (ceiling) actual <= target else actual >= target
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f))
                Text(
                    "$value $unit",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (ceiling && over) Coral else Ink,
                )
            }
            if (target != null) {
                Text(
                    "${if (ceiling) "of" else "goal"} ${FoodInput.num(target, 0)} $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hit && !ceiling) Green else Ink.copy(alpha = 0.7f),
                )
            }
        }
        if (target != null && target > 0) {
            TxProgress((actual / target).toFloat(), color = if (ceiling && over) Coral else if (!ceiling && hit) Green else color)
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<FoodLogEntry>,
    onAdd: () -> Unit,
    onEntry: (FoodLogEntry) -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    ShadowCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meal.label.uppercase(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${FoodInput.kcal(entries.total().kcal)} kcal", style = MaterialTheme.typography.labelMedium, color = MetricColors.Calories)
        }
        if (entries.isEmpty()) {
            Text("Nothing logged.", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.5f))
        }
        for (e in entries) {
            Row(
                Modifier.fillMaxWidth().clickable { onEntry(e) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.nameSnapshot, style = MaterialTheme.typography.titleSmall)
                    Text(
                        FoodInput.servingsLabel(e.servings, e.servingLabelSnapshot),
                        style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${FoodInput.kcal(e.kcal)} kcal", style = MaterialTheme.typography.labelMedium, color = MetricColors.Calories)
                    Text("${FoodInput.grams(e.proteinG)} g P", style = MaterialTheme.typography.labelSmall, color = MetricColors.Protein)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TxButton("+ Add", onAdd, Modifier.weight(1f))
            TxButton("Copy from…", onCopy, Modifier.weight(1f))
        }
        if (entries.isNotEmpty()) {
            Text(
                "SAVE AS MEAL",
                style = MaterialTheme.typography.labelMedium,
                color = Coral,
                modifier = Modifier.clickable(onClick = onSave).padding(vertical = 4.dp),
            )
        }
    }
}

/** Edit servings/grams, move to another meal, or delete a logged entry. */
@Composable
private fun EntryDialog(entry: FoodLogEntry, vm: FoodDiaryViewModel, onDismiss: () -> Unit) {
    var food by remember(entry.id) { mutableStateOf<Food?>(null) }
    LaunchedEffect(entry.id) { food = vm.foodFor(entry) }
    val servingGrams = food?.servingGrams
    var servings by remember(entry.id) { mutableStateOf(FoodInput.num(entry.servings, 3)) }
    var grams by remember(entry.id, servingGrams) { mutableStateOf(FoodInput.gramsFromServings(servingGrams, servings)) }
    var meal by remember(entry.id) { mutableStateOf(entry.meal) }
    val parsed = FoodInput.parsePositive(servings)

    TxDialog(entry.nameSnapshot, onDismiss) {
        Text(entry.servingLabelSnapshot, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
        TxTextField(servings, {
            servings = it
            if ((servingGrams ?: 0.0) > 0) grams = FoodInput.gramsFromServings(servingGrams, it)
        }, "Servings", Modifier.fillMaxWidth(), numeric = true)
        if ((servingGrams ?: 0.0) > 0) {
            TxTextField(grams, {
                grams = it
                FoodInput.servingsFromGrams(servingGrams, it).takeIf { s -> s.isNotEmpty() }?.let { s -> servings = s }
            }, "Grams", Modifier.fillMaxWidth(), unit = "g", numeric = true)
        }
        SectionLabel("Meal")
        // Full meal names don't fit a 4-way segmented control, so use a 2×2 grid of chips.
        Meal.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { m -> TxChip(m.label, m == meal, { meal = m }, Modifier.weight(1f)) }
            }
        }
        TxButton("Save", {
            parsed?.let { p -> if (p != entry.servings) vm.setServings(entry.id, p) }
            if (meal != entry.meal) vm.move(entry.id, meal)
            onDismiss()
        }, Modifier.fillMaxWidth(), bg = Green, textColor = ItemsBg, enabled = parsed != null)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TxButton("Delete", { vm.delete(entry.id); onDismiss() }, Modifier.weight(1f), textColor = Coral)
            TxButton("Cancel", onDismiss, Modifier.weight(1f))
        }
    }
}
