package com.yatharth.yool.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.model.DefaultBingeTriggers
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.domain.TargetCheck
import com.yatharth.yool.sync.SyncStatus
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxProgress
import com.yatharth.yool.ui.components.TxSwitch
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Amber
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.MetricColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The daily log for one date. Used as the Today tab (date = today) and for editing any past day.
 */
@Composable
fun DayScreen(
    date: LocalDate,
    contentPadding: PaddingValues,
    onNavigateDay: ((LocalDate) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    /** Opens the food diary for a date (wired by feat/food-diary into the Nutrition card). */
    onOpenFood: (LocalDate) -> Unit = {},
    /** Starts an empty workout (or resumes the active one) and opens it. Wired by feat/graphs-insights. */
    onStartWorkout: () -> Unit = {},
    /** Opens a finished workout's detail. */
    onOpenWorkout: (workoutId: Long) -> Unit = {},
) {
    val repo = LocalContext.current.appContainer.repository
    val vm: DayViewModel = viewModel(key = "day-$date", factory = viewModelFactory { initializer { DayViewModel(repo, date) } })
    val state by vm.state.collectAsStateWithLifecycle()

    val syncEngine = LocalContext.current.appContainer.syncEngine
    val syncStatus by syncEngine.status.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = syncStatus == SyncStatus.Running,
        onRefresh = syncEngine::launchRecentSync,
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DayHeader(date, onNavigateDay, onBack)
        if (!state.loaded) return@Column

        // keys reset local text state when switching days / after first load
        val k = "${date}-${state.loaded}"
        StreakAndMoodRow(state)
        WeightCard(state, k, vm::update)
        CheckInCard(state, k, vm::update)
        NutritionCard(state, k, vm::update) { onOpenFood(date) }
        ActivityCard(state, k, vm::update)
        WorkoutCard(state, vm::update)
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun DayHeader(date: LocalDate, onNavigateDay: ((LocalDate) -> Unit)?, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.clickable(onClick = onBack).padding(end = 12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(Fmt.day(date).uppercase(), style = MaterialTheme.typography.labelLarge)
            Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE), style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
        }
        if (onNavigateDay != null) {
            NavArrow(Icons.AutoMirrored.Outlined.ArrowBack) { onNavigateDay(date.minusDays(1)) }
            Spacer(Modifier.size(8.dp))
            if (date.isBefore(LocalDate.now())) NavArrow(Icons.AutoMirrored.Outlined.ArrowForward) { onNavigateDay(date.plusDays(1)) }
        }
    }
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(ItemsBg, RoundedCornerShape(4.dp))
            .border(1.5.dp, Ink, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(18.dp)) }
}

@Composable
private fun StreakAndMoodRow(state: DayUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ShadowCard(Modifier.weight(1f)) {
            StatValue("Binge-free", "${state.bingeFreeStreak}", unit = if (state.bingeFreeStreak == 1) "day" else "days", color = Green, big = true)
        }
        ShadowCard(Modifier.weight(1f)) {
            val done = state.day.checkInDone
            StatValue("Check-in", if (done) "Done" else "To do", color = if (done) Green else Coral, big = true)
        }
    }
}

// ── Weight ───────────────────────────────────────────────────────────────────

@Composable
private fun WeightCard(state: DayUiState, key: String, update: ((DailyEntry) -> DailyEntry) -> Unit) {
    var text by remember(key) { mutableStateOf(state.entry?.weightKg?.let(Fmt::kg) ?: "") }
    val goal = state.targets.entryOn(TargetMetric.WEIGHT_GOAL, state.date)
    ShadowCard {
        SectionLabel("Weight · first weigh-in")
        TxTextField(
            value = text,
            onValueChange = { v -> text = v; update { it.copy(weightKg = Fmt.parseDouble(v)) } },
            label = "Morning weight",
            unit = "kg",
            numeric = true,
            placeholder = state.day.weightKg?.let(Fmt::kg) ?: "0.0",
        )
        if (state.entry?.weightKg == null && state.day.weightKg != null) {
            SourceTag("From ${Fmt.source(state.day.sources[Metric.WEIGHT_KG])}: ${Fmt.kg(state.day.weightKg)} kg")
        }
        if (goal != null) {
            val diff = state.day.weightKg?.let { it - goal.value }
            Text(
                "Goal ${Fmt.kg(goal.value)} kg" + (diff?.let { " · ${Fmt.kg(kotlin.math.abs(it))} kg ${if (it > 0) "to go" else "below"}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// ── Check-in: binge + mood + note ────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckInCard(state: DayUiState, key: String, update: ((DailyEntry) -> DailyEntry) -> Unit) {
    val e = state.entry
    var bingeNote by remember(key) { mutableStateOf(e?.bingeNote ?: "") }
    var customTrigger by remember(key) { mutableStateOf(e?.bingeTrigger?.takeIf { it !in DefaultBingeTriggers } ?: "") }
    var note by remember(key) { mutableStateOf(e?.note ?: "") }

    ShadowCard {
        SectionLabel("Did you binge?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxChip("No", selected = e?.binge == false, onClick = { update { it.copy(binge = if (it.binge == false) null else false) } }, color = Green)
            TxChip("Yes", selected = e?.binge == true, onClick = { update { it.copy(binge = if (it.binge == true) null else true) } }, color = Coral)
        }
        if (e?.binge == true) {
            SectionLabel("Trigger")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultBingeTriggers.forEach { t ->
                    TxChip(t, selected = e.bingeTrigger == t, onClick = {
                        customTrigger = ""
                        update { it.copy(bingeTrigger = if (it.bingeTrigger == t) null else t) }
                    }, color = Amber)
                }
            }
            TxTextField(
                value = customTrigger,
                onValueChange = { v -> customTrigger = v; update { it.copy(bingeTrigger = v.ifBlank { null }) } },
                label = "Other trigger",
                placeholder = "e.g. late night",
            )
            TxTextField(
                value = bingeNote,
                onValueChange = { v -> bingeNote = v; update { it.copy(bingeNote = v.ifBlank { null }) } },
                label = "What happened",
                singleLine = false,
                maxLength = 200,
            )
        }

        SectionLabel("How did today feel?")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                val sel = e?.mood == n
                val c = moodColor(n)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(if (sel) c.copy(alpha = 0.25f) else ItemsBg, RoundedCornerShape(4.dp))
                        .border(if (sel) 2.5.dp else 1.5.dp, if (sel) c else Ink, RoundedCornerShape(4.dp))
                        .clickable { update { it.copy(mood = if (it.mood == n) null else n) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", style = MaterialTheme.typography.titleSmall, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.ExtraLight)
                }
            }
        }
        TxTextField(
            value = note,
            onValueChange = { v -> note = v; update { it.copy(note = v.ifBlank { null }) } },
            label = "Anything else",
            singleLine = false,
            maxLength = 200,
        )
    }
}

fun moodColor(n: Int): Color = when (n) {
    1 -> Coral
    2 -> Color(0xFFC4905B)
    3 -> Amber
    4 -> Color(0xFF8DAF55)
    else -> Green
}

// ── Nutrition ────────────────────────────────────────────────────────────────

@Composable
private fun NutritionCard(state: DayUiState, key: String, update: ((DailyEntry) -> DailyEntry) -> Unit, onLogFood: () -> Unit) {
    val d = state.day
    val calTarget = state.targets.valueOn(TargetMetric.CALORIES, state.date)
    val proTarget = state.targets.valueOn(TargetMetric.PROTEIN, state.date)
    ShadowCard {
        SectionLabel("Nutrition")
        MetricBlock(
            label = "Calories", value = Fmt.int(d.calories), unit = "kcal", color = MetricColors.Calories,
            target = calTarget, actual = d.calories?.toDouble(), hit = TargetCheck.caloriesHit(d.calories, calTarget), ceiling = true,
            source = d.sources[Metric.CALORIES]?.let(Fmt::source),
            overrideText = state.entry?.calories?.toString() ?: "", key = key + "cal", numericUnit = "kcal",
            onOverride = { v -> update { it.copy(calories = Fmt.parseInt(v)) } },
        )
        MetricBlock(
            label = "Protein", value = Fmt.int(d.proteinG?.toInt()), unit = "g", color = MetricColors.Protein,
            target = proTarget, actual = d.proteinG, hit = TargetCheck.floorHit(d.proteinG, proTarget),
            source = d.sources[Metric.PROTEIN_G]?.let(Fmt::source),
            overrideText = state.entry?.proteinG?.let { Fmt.one(it) } ?: "", key = key + "pro", numericUnit = "g",
            onOverride = { v -> update { it.copy(proteinG = Fmt.parseDouble(v)) } },
        )
        TxButton("Log food", onClick = onLogFood, modifier = Modifier.fillMaxWidth())
    }
}

// ── Steps + sleep ────────────────────────────────────────────────────────────

@Composable
private fun ActivityCard(state: DayUiState, key: String, update: ((DailyEntry) -> DailyEntry) -> Unit) {
    val d = state.day
    val stepTarget = state.targets.valueOn(TargetMetric.STEPS, state.date)
    val sleepTarget = state.targets.valueOn(TargetMetric.SLEEP_HOURS, state.date)
    ShadowCard {
        SectionLabel("Activity & recovery")
        MetricBlock(
            label = "Steps", value = Fmt.int(d.steps), unit = "steps", color = MetricColors.Steps,
            target = stepTarget, actual = d.steps?.toDouble(), hit = TargetCheck.floorHit(d.steps?.toDouble(), stepTarget),
            source = d.sources[Metric.STEPS]?.let(Fmt::source),
            overrideText = state.entry?.steps?.toString() ?: "", key = key + "steps", numericUnit = "steps",
            onOverride = { v -> update { it.copy(steps = Fmt.parseInt(v)) } },
        )
        MetricBlock(
            label = "Sleep", value = Fmt.one(d.sleepHours), unit = "h", color = MetricColors.Sleep,
            target = sleepTarget, actual = d.sleepHours, hit = TargetCheck.floorHit(d.sleepHours, sleepTarget),
            source = d.sources[Metric.SLEEP_HOURS]?.let(Fmt::source),
            overrideText = state.entry?.sleepHours?.let { Fmt.one(it) } ?: "", key = key + "sleep", numericUnit = "h",
            onOverride = { v -> update { it.copy(sleepHours = Fmt.parseDouble(v)) } },
        )
    }
}

/** Resolved value + target progress + source, with an optional manual override field. */
@Composable
private fun MetricBlock(
    label: String,
    value: String,
    unit: String,
    color: Color,
    target: Double?,
    actual: Double?,
    hit: Boolean?,
    source: String?,
    overrideText: String,
    key: String,
    numericUnit: String,
    onOverride: (String) -> Unit,
    ceiling: Boolean = false,
) {
    var editing by remember(key) { mutableStateOf(false) }
    var text by remember(key) { mutableStateOf(overrideText) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            StatValue(label, value, unit = unit, color = color, modifier = Modifier.weight(1f))
            if (hit != null) {
                Text(if (hit) "✓" else "✗", style = MaterialTheme.typography.titleSmall, color = if (hit) Green else Coral, modifier = Modifier.padding(end = 12.dp))
            }
            TxChip(if (editing) "Done" else "Edit", selected = editing, onClick = { editing = !editing }, color = Ink)
        }
        if (target != null && actual != null) {
            TxProgress((actual / target).toFloat(), color = if (hit == false) Coral else color)
            Text(
                (if (ceiling) "Limit " else "Target ") + Fmt.int(target.toLong()) + " $unit",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (!source.isNullOrEmpty()) SourceTag("Source: $source")
        if (editing) {
            TxTextField(
                value = text,
                onValueChange = { v -> text = v; onOverride(v) },
                label = "Override (blank = use imported)",
                unit = numericUnit,
                numeric = true,
            )
        }
    }
}

// ── Workout ──────────────────────────────────────────────────────────────────

@Composable
private fun WorkoutCard(state: DayUiState, update: ((DailyEntry) -> DailyEntry) -> Unit) {
    val d = state.day
    ShadowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Workout")
                Text(
                    when (d.workoutDone) {
                        true -> "Done"
                        false -> "Rest day"
                        null -> "Not logged"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (d.workoutDone == true) MetricColors.Workout else Ink,
                )
            }
            TxSwitch(checked = d.workoutDone == true, onCheckedChange = { v -> update { it.copy(workoutDone = v) } })
        }
        d.sources[Metric.WORKOUT]?.let { SourceTag("Source: ${Fmt.source(it)}") }
        if (state.lifts.isNotEmpty()) {
            SectionLabel("Best sets")
            state.lifts.forEach { l ->
                Row(Modifier.fillMaxWidth()) {
                    Text(l.exercise, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${Fmt.kg(l.weightKg)} kg × ${l.reps}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun SourceTag(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.6f))
}
