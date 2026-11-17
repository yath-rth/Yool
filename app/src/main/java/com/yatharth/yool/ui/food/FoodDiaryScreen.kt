package com.yatharth.yool.ui.food

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxActionSheet
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxDialogButtons
import com.yatharth.yool.ui.components.TxDivider
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxTopBar
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
    var menuMeal by remember { mutableStateOf<Meal?>(null) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        TxTopBar(
            "Food", subtitle = Fmt.date(date), onBack = onBack,
            actions = {
                TxIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day", { onNavigateDay(date.minusDays(1)) })
                if (date.isBefore(LocalDate.now())) {
                    Spacer(Modifier.size(8.dp))
                    TxIconButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day", { onNavigateDay(date.plusDays(1)) })
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TotalsCard(state, date)

            for (meal in Meal.entries) {
                MealCard(
                    meal = meal,
                    entries = state.entriesFor(meal),
                    onAdd = { onAddFood(meal) },
                    onEntry = { editing = it },
                    onMore = { menuMeal = meal },
                )
            }

            TxListGroup {
                TxListRow("Saved meals", onClick = onOpenSavedMeals, divider = false)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    menuMeal?.let { meal ->
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Copy from another day" to { copyInto = meal; menuMeal = null })
            if (state.entriesFor(meal).isNotEmpty()) add("Save as meal" to { saveMeal = meal; menuMeal = null })
        }
        TxActionSheet(meal.label, onDismiss = { menuMeal = null }, actions = actions)
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
            TxButton("Cancel", { copyInto = null }, Modifier.fillMaxWidth(), fill = false)
        }
    }

    saveMeal?.let { meal ->
        var name by remember { mutableStateOf("") }
        TxDialog("Save ${meal.label} as meal", onDismiss = { saveMeal = null }) {
            TxTextField(name, { name = it }, "Name", Modifier.fillMaxWidth(), maxLength = 40, placeholder = "e.g. Usual breakfast")
            TxDialogButtons("Save", {
                vm.saveAsMeal(meal, name) {
                    Toast.makeText(context, "Saved \"${name.trim()}\"", Toast.LENGTH_SHORT).show()
                }
                saveMeal = null
            }, onDismiss = { saveMeal = null }, confirmEnabled = name.isNotBlank())
        }
    }
}

@Composable
private fun TotalsCard(state: FoodDiaryUiState, date: LocalDate) {
    val t = state.total
    val calTarget = state.targets.valueOn(TargetMetric.CALORIES, date)
    val proTarget = state.targets.valueOn(TargetMetric.PROTEIN, date)
    val over = calTarget != null && t.kcal > calTarget
    ShadowCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            StatValue("Eaten", FoodInput.kcal(t.kcal), Modifier.weight(1f), unit = "kcal", color = if (over) Coral else Ink, big = true)
            if (calTarget != null) {
                val diff = Math.round(kotlin.math.abs(calTarget - t.kcal))
                Text(
                    "${Fmt.int(diff)} ${if (over) "over" else "left"}",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
                    color = if (over) Coral else Ink,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (calTarget != null && calTarget > 0) {
            TxProgress((t.kcal / calTarget).toFloat(), color = if (over) Coral else MetricColors.Calories)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatValue("Protein", FoodInput.grams(t.proteinG), Modifier.weight(1f), unit = "g")
            StatValue("Carbs", FoodInput.grams(t.carbsG), Modifier.weight(1f), unit = "g")
            StatValue("Fat", FoodInput.grams(t.fatG), Modifier.weight(1f), unit = "g")
        }
        if (proTarget != null && proTarget > 0) {
            val hit = t.proteinG >= proTarget
            TxProgress((t.proteinG / proTarget).toFloat(), color = if (hit) Green else MetricColors.Protein, height = 8.dp)
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<FoodLogEntry>,
    onAdd: () -> Unit,
    onEntry: (FoodLogEntry) -> Unit,
    onMore: () -> Unit,
) {
    ShadowCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                meal.label.uppercase(), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
            )
            Text("${FoodInput.kcal(entries.total().kcal)} kcal", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
            TxIconButton(Icons.Outlined.Add, "Add to ${meal.label}", onAdd, size = 32.dp)
            TxIconButton(Icons.Outlined.MoreVert, "${meal.label} options", onMore, size = 32.dp)
        }
        if (entries.isEmpty()) {
            Text("Nothing logged", style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.5f))
        }
        entries.forEachIndexed { i, e ->
            if (i > 0) TxDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onEntry(e) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        e.nameSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        FoodInput.servingsLabel(e.servings, e.servingLabelSnapshot),
                        style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("${FoodInput.kcal(e.kcal)} kcal", style = MaterialTheme.typography.labelMedium)
            }
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
        TxDialogButtons("Save", {
            parsed?.let { p -> if (p != entry.servings) vm.setServings(entry.id, p) }
            if (meal != entry.meal) vm.move(entry.id, meal)
            onDismiss()
        }, onDismiss, confirmEnabled = parsed != null)
        TxButton("Delete", { vm.delete(entry.id); onDismiss() }, Modifier.fillMaxWidth(), fill = false, textColor = Coral)
    }
}
