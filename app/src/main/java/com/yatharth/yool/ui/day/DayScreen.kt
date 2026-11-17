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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
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
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.sync.SyncStatus
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxBadge
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxDivider
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxMetricTile
import com.yatharth.yool.ui.components.TxSwitch
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.theme.Amber
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.MetricColors
import java.time.LocalDate

/**
 * The daily log for one date. Used as the Today tab (date = today) and for editing any past day.
 */
@Composable
fun DayScreen(
    date: LocalDate,
    contentPadding: PaddingValues,
    onNavigateDay: ((LocalDate) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    /** Opens the food diary for a date. */
    onOpenFood: (LocalDate) -> Unit = {},
    /** Starts an empty workout (or resumes the active one) and opens it. */
    onStartWorkout: () -> Unit = {},
    /** Opens a finished workout's detail. */
    onOpenWorkout: (workoutId: Long) -> Unit = {},
    /** Opens the history list (Today tab only). */
    onOpenHistory: (() -> Unit)? = null,
) {
    val repo = LocalContext.current.appContainer.repository
    val workoutRepo = LocalContext.current.appContainer.workoutRepository
    val vm: DayViewModel = viewModel(key = "day-$date", factory = viewModelFactory { initializer { DayViewModel(repo, workoutRepo, date) } })
    val state by vm.state.collectAsStateWithLifecycle()

    val syncEngine = LocalContext.current.appContainer.syncEngine
    val syncStatus by syncEngine.status.collectAsStateWithLifecycle()
    var editing by remember(date) { mutableStateOf<Tile?>(null) }

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
        DayHeader(date, onNavigateDay, onBack, onOpenHistory)
        if (!state.loaded) return@Column

        // keys reset local text state when switching days / after first load
        val k = "${date}-${state.loaded}"
        HeroCard(state)
        MetricGrid(state) { editing = it }
        CheckInCard(state, k, vm::update)
        Spacer(Modifier.height(24.dp))

        editing?.let { tile ->
            MetricEditorDialog(
                tile, state, k, vm::update,
                onDismiss = { editing = null },
                onOpenFood = { editing = null; onOpenFood(date) },
                onStartWorkout = { editing = null; onStartWorkout() },
                onOpenWorkout = { editing = null; onOpenWorkout(it) },
            )
        }
    }
    }
}

@Composable
private fun DayHeader(date: LocalDate, onNavigateDay: ((LocalDate) -> Unit)?, onBack: (() -> Unit)?, onOpenHistory: (() -> Unit)?) {
    val sub = if (Fmt.date(date) != Fmt.dateNoRelative(date)) Fmt.dateNoRelative(date)
    else date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    TxTopBar(Fmt.date(date), subtitle = sub, onBack = onBack, actions = {
        if (onOpenHistory != null) TxIconButton(Icons.Outlined.CalendarMonth, "History", onOpenHistory)
        if (onNavigateDay != null) {
            TxIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Previous day", { onNavigateDay(date.minusDays(1)) })
            if (date.isBefore(LocalDate.now())) TxIconButton(Icons.AutoMirrored.Outlined.ArrowForward, "Next day", { onNavigateDay(date.plusDays(1)) })
        }
    })
}

// ── Hero ─────────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(state: DayUiState) {
    val d = state.day
    val logged = listOf(d.weightKg, d.calories, d.proteinG, d.steps, d.sleepHours, d.workoutDone).count { it != null }
    ShadowCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatValue(
                "Binge-free", "${state.bingeFreeStreak}",
                unit = if (state.bingeFreeStreak == 1) "day" else "days", color = Green, big = true, modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TxBadge(if (d.checkInDone) "Check-in done" else "Check-in to do", color = if (d.checkInDone) Green else Coral)
                Text("$logged of 6 logged", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
            }
        }
    }
}

// ── Metric grid ──────────────────────────────────────────────────────────────

private enum class Tile { Weight, Calories, Protein, Steps, Sleep, Workout }

@Composable
private fun MetricGrid(state: DayUiState, onOpen: (Tile) -> Unit) {
    val d = state.day
    val t = state.targets
    val goal = t.valueOn(TargetMetric.WEIGHT_GOAL, state.date)
    val calT = t.valueOn(TargetMetric.CALORIES, state.date)
    val proT = t.valueOn(TargetMetric.PROTEIN, state.date)
    val stepT = t.valueOn(TargetMetric.STEPS, state.date)
    val sleepT = t.valueOn(TargetMetric.SLEEP_HOURS, state.date)

    fun frac(actual: Double?, target: Double?): Float? =
        if (actual == null || target == null || target <= 0.0) null else (actual / target).toFloat().coerceIn(0f, 1f)

    val weight = d.weightKg
    val diff = if (weight != null && goal != null) weight - goal else null
    val calOver = TargetCheck.caloriesHit(d.calories, calT) == false

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TxMetricTile(
                "Weight", Fmt.kg(weight), Modifier.weight(1f).fillMaxHeight(), unit = "kg", color = MetricColors.Weight,
                footnote = when {
                    goal == null -> null
                    diff == null -> "goal ${Fmt.kg(goal)} kg"
                    else -> "goal ${Fmt.kg(goal)} kg · ${Fmt.kg(kotlin.math.abs(diff))} ${if (diff > 0) "to go" else "below"}"
                },
                onClick = { onOpen(Tile.Weight) },
            )
            TxMetricTile(
                "Calories", Fmt.int(d.calories), Modifier.weight(1f).fillMaxHeight(), unit = "kcal", color = MetricColors.Calories,
                progress = frac(d.calories?.toDouble(), calT), progressColor = if (calOver) Coral else MetricColors.Calories,
                footnote = calT?.let { "of ${Fmt.int(it.toLong())} limit" },
                footnoteColor = if (calOver) Coral else Ink.copy(alpha = 0.7f),
                onClick = { onOpen(Tile.Calories) },
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TxMetricTile(
                "Protein", Fmt.int(d.proteinG?.toInt()), Modifier.weight(1f).fillMaxHeight(), unit = "g", color = MetricColors.Protein,
                progress = frac(d.proteinG, proT), footnote = proT?.let { "of ${Fmt.int(it.toLong())} g" },
                onClick = { onOpen(Tile.Protein) },
            )
            TxMetricTile(
                "Steps", Fmt.int(d.steps), Modifier.weight(1f).fillMaxHeight(), color = MetricColors.Steps,
                progress = frac(d.steps?.toDouble(), stepT), footnote = stepT?.let { "of ${Fmt.int(it.toLong())}" },
                onClick = { onOpen(Tile.Steps) },
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TxMetricTile(
                "Sleep", Fmt.one(d.sleepHours), Modifier.weight(1f).fillMaxHeight(), unit = "h", color = MetricColors.Sleep,
                progress = frac(d.sleepHours, sleepT), footnote = sleepT?.let { "target ${Fmt.one(it)} h" },
                onClick = { onOpen(Tile.Sleep) },
            )
            TxMetricTile(
                "Workout",
                when (d.workoutDone) { true -> "Done"; false -> "Rest"; null -> "—" },
                Modifier.weight(1f).fillMaxHeight(), color = MetricColors.Workout,
                footnote = when (d.workoutDone) { true -> "Trained"; false -> "Rest day"; null -> "Not logged" },
                onClick = { onOpen(Tile.Workout) },
            )
        }
    }
}

// ── Editor dialogs ───────────────────────────────────────────────────────────

@Composable
private fun MetricEditorDialog(
    tile: Tile,
    state: DayUiState,
    key: String,
    update: ((DailyEntry) -> DailyEntry) -> Unit,
    onDismiss: () -> Unit,
    onOpenFood: () -> Unit,
    onStartWorkout: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
) {
    val d = state.day
    val e = state.entry
    when (tile) {
        Tile.Weight -> {
            var text by remember(key) { mutableStateOf(e?.weightKg?.let(Fmt::kg) ?: "") }
            val goal = state.targets.entryOn(TargetMetric.WEIGHT_GOAL, state.date)
            TxDialog("Weight", onDismiss) {
                TxTextField(
                    value = text,
                    onValueChange = { v -> text = v; update { it.copy(weightKg = Fmt.parseDouble(v)) } },
                    label = "Morning weight",
                    unit = "kg",
                    numeric = true,
                    placeholder = d.weightKg?.let(Fmt::kg) ?: "0.0",
                )
                if (e?.weightKg == null && d.weightKg != null) {
                    SourceTag("From ${Fmt.source(d.sources[Metric.WEIGHT_KG])}: ${Fmt.kg(d.weightKg)} kg")
                }
                if (goal != null) {
                    val diff = d.weightKg?.let { it - goal.value }
                    Text(
                        "Goal ${Fmt.kg(goal.value)} kg" + (diff?.let { " · ${Fmt.kg(kotlin.math.abs(it))} kg ${if (it > 0) "to go" else "below"}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TxButton("Done", onDismiss, Modifier.fillMaxWidth())
            }
        }
        Tile.Calories -> OverrideDialog(
            "Calories", Fmt.int(d.calories), "kcal", d.sources[Metric.CALORIES]?.let(Fmt::source),
            e?.calories?.toString() ?: "", key + "cal", onDismiss,
            onOverride = { v -> update { it.copy(calories = Fmt.parseInt(v)) } },
            primaryLabel = "Open food diary", onPrimary = onOpenFood,
        )
        Tile.Protein -> OverrideDialog(
            "Protein", Fmt.int(d.proteinG?.toInt()), "g", d.sources[Metric.PROTEIN_G]?.let(Fmt::source),
            e?.proteinG?.let { Fmt.one(it) } ?: "", key + "pro", onDismiss,
            onOverride = { v -> update { it.copy(proteinG = Fmt.parseDouble(v)) } },
            primaryLabel = "Open food diary", onPrimary = onOpenFood,
        )
        Tile.Steps -> OverrideDialog(
            "Steps", Fmt.int(d.steps), "steps", d.sources[Metric.STEPS]?.let(Fmt::source),
            e?.steps?.toString() ?: "", key + "steps", onDismiss,
            onOverride = { v -> update { it.copy(steps = Fmt.parseInt(v)) } },
        )
        Tile.Sleep -> OverrideDialog(
            "Sleep", Fmt.one(d.sleepHours), "h", d.sources[Metric.SLEEP_HOURS]?.let(Fmt::source),
            e?.sleepHours?.let { Fmt.one(it) } ?: "", key + "sleep", onDismiss,
            onOverride = { v -> update { it.copy(sleepHours = Fmt.parseDouble(v)) } },
        )
        Tile.Workout -> TxDialog("Workout", onDismiss) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (d.workoutDone) { true -> "Done"; false -> "Rest day"; null -> "Not logged" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (d.workoutDone == true) MetricColors.Workout else Ink,
                    )
                    d.sources[Metric.WORKOUT]?.let { SourceTag("Source: ${Fmt.source(it)}") }
                }
                TxSwitch(checked = d.workoutDone == true, onCheckedChange = { v -> update { it.copy(workoutDone = v) } })
            }
            state.workouts.forEach { w ->
                TxDivider()
                Column(
                    Modifier.fillMaxWidth().clickable { onOpenWorkout(w.workout.id) }.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(w.workout.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text(
                            WorkoutMath.formatDuration((w.workout.endedAt ?: w.workout.startedAt) - w.workout.startedAt) +
                                " · ${WorkoutMath.kg(WorkoutMath.volume(w))} kg",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    WorkoutMath.exerciseLines(w).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            // Best sets only for imported days with no logged workout.
            if (state.workouts.isEmpty() && state.lifts.isNotEmpty()) {
                SectionLabel("Best sets")
                state.lifts.forEach { l ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(l.exercise, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${Fmt.kg(l.weightKg)} kg × ${l.reps}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            if (state.activeWorkout != null) {
                TxButton("Resume workout", onStartWorkout, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
            } else if (state.date == LocalDate.now()) {
                TxButton("Start workout", onStartWorkout, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
            }
            TxButton("Close", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Resolved value + source + manual override field (blank = use imported). */
@Composable
private fun OverrideDialog(
    title: String,
    value: String,
    unit: String,
    source: String?,
    overrideText: String,
    key: String,
    onDismiss: () -> Unit,
    onOverride: (String) -> Unit,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
) {
    var text by remember(key) { mutableStateOf(overrideText) }
    TxDialog(title, onDismiss) {
        StatValue(title, value, unit = unit)
        if (!source.isNullOrEmpty()) SourceTag("Source: $source")
        TxTextField(
            value = text,
            onValueChange = { v -> text = v; onOverride(v) },
            label = "Override (blank = use imported)",
            unit = unit,
            numeric = true,
        )
        if (primaryLabel != null) TxButton(primaryLabel, onPrimary, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
        TxButton("Done", onDismiss, Modifier.fillMaxWidth())
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
    var showNote by remember(key) { mutableStateOf(!e?.note.isNullOrBlank()) }

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
        if (showNote) {
            TxTextField(
                value = note,
                onValueChange = { v -> note = v; update { it.copy(note = v.ifBlank { null }) } },
                label = "Anything else",
                singleLine = false,
                maxLength = 200,
            )
        } else {
            TxChip("Add note", selected = false, onClick = { showNote = true }, color = Ink)
        }
    }
}

fun moodColor(n: Int): Color = when (n) {
    1 -> Coral
    2 -> Color(0xFFC4905B)
    3 -> Amber
    4 -> Color(0xFF8DAF55)
    else -> Green
}

@Composable
private fun SourceTag(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.6f))
}
