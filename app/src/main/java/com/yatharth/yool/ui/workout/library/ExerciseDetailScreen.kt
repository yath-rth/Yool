package com.yatharth.yool.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.ExerciseSetRow
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.domain.DatePoint
import com.yatharth.yool.domain.ExerciseSessionPoint
import com.yatharth.yool.domain.Ranges
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxTopBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import com.yatharth.yool.ui.components.TxSegmented
import com.yatharth.yool.ui.graphs.LineSeries
import com.yatharth.yool.ui.graphs.RangeSelection
import com.yatharth.yool.ui.graphs.RangeSelector
import com.yatharth.yool.ui.graphs.TimeChart
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.MetricColors
import com.yatharth.yool.ui.theme.Ink
import java.time.LocalDate

private enum class DetailTab(val label: String) { HISTORY("History"), RECORDS("Records"), CHARTS("Charts") }

/** History / Records / Charts for one exercise. */
@Composable
fun ExerciseDetailScreen(exerciseId: Long, contentPadding: PaddingValues, onBack: () -> Unit, onEdit: (Long) -> Unit, onOpenWorkout: (Long) -> Unit) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val exercise by remember(exerciseId) { repo.observeExercise(exerciseId) }.collectAsStateWithLifecycle(null)
    val rows by remember(exerciseId) { repo.observeExerciseHistory(exerciseId) }.collectAsStateWithLifecycle(null)
    var tab by remember { mutableStateOf(DetailTab.HISTORY) }
    val ex = exercise

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TxTopBar(
            ex?.name ?: "Exercise",
            subtitle = ex?.let { "${it.bodyPart.label} · ${it.type.label}" + if (it.archived) " · archived" else "" },
            onBack = onBack,
        ) {
            TxIconButton(Icons.Outlined.Edit, "Edit exercise", { onEdit(exerciseId) })
        }
        if (ex?.note != null) Text(ex.note, style = MaterialTheme.typography.bodyMedium)
        TxSegmented(DetailTab.entries, tab, { tab = it }, label = { it.label })
        val data = rows
        if (ex == null || data == null) return@Column
        if (data.isEmpty()) {
            EmptyState("No history yet", "Completed sets of this exercise will show up here.")
            return@Column
        }
        when (tab) {
            DetailTab.HISTORY -> HistoryTab(ex, data, onOpenWorkout)
            DetailTab.RECORDS -> RecordsTab(ex, data)
            DetailTab.CHARTS -> ChartsTab(data)
        }
    }
}

@Composable
private fun HistoryTab(ex: Exercise, rows: List<ExerciseSetRow>, onOpenWorkout: (Long) -> Unit) {
    val sessions = remember(rows) { rows.groupBy { it.workoutId }.values.sortedByDescending { it.first().startedAt } }
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) sessions else sessions.take(5)
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sessions.size > 5) item(key = "toggle") {
            TxButton(if (showAll) "Show latest 5" else "Show all ${sessions.size} sessions", { showAll = !showAll }, Modifier.fillMaxWidth())
        }
        items(visible, key = { it.first().workoutId }) { s ->
            ShadowCard(Modifier.fillMaxWidth(), onClick = { onOpenWorkout(s.first().workoutId) }, contentPadding = 12.dp) {
                Text(Fmt.date(s.first().date), style = MaterialTheme.typography.titleSmall)
                Text(s.first().workoutName, style = MaterialTheme.typography.labelMedium)
                s.sortedBy { it.position }.forEachIndexed { i, r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(r.type.short.ifEmpty { "${i + 1}" }, style = MaterialTheme.typography.labelMedium, color = if (r.type == SetType.NORMAL) Ink else Coral)
                        Text(setText(ex.type, r.weightKg, r.reps, r.durationSec, r.distanceM), style = MaterialTheme.typography.bodyMedium)
                        if (r.rpe != null) Text("RPE ${WorkoutMath.kg(r.rpe)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsTab(ex: Exercise, rows: List<ExerciseSetRow>) {
    val rec = remember(rows) { WorkoutMath.records(rows) }
    fun kgText(v: Double?) = v?.let { WorkoutMath.kg(it) } ?: "-"
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShadowCard(Modifier.fillMaxWidth()) {
            Text("${rec.sessions} sessions", style = MaterialTheme.typography.labelMedium)
            if (ex.type.usesWeight) {
                Row(Modifier.fillMaxWidth()) {
                    StatValue("Heaviest", kgText(rec.heaviestKg), Modifier.weight(1f), unit = "kg")
                    StatValue("Best est. 1RM", kgText(rec.best1RmKg?.let { Math.round(it * 10) / 10.0 }), Modifier.weight(1f), unit = "kg")
                }
            }
            if (ex.type.usesWeight || ex.type.usesReps) {
                Row(Modifier.fillMaxWidth()) {
                    StatValue("Best set volume", kgText(rec.bestSetVolumeKg), Modifier.weight(1f), unit = "kg")
                    StatValue("Best session volume", kgText(rec.bestSessionVolumeKg), Modifier.weight(1f), unit = "kg")
                }
            }
            if (ex.type.usesDuration || ex.type.usesDistance) {
                Row(Modifier.fillMaxWidth()) {
                    if (ex.type.usesDuration) StatValue("Longest duration", rec.longestDurationSec?.let(::formatSeconds) ?: "-", Modifier.weight(1f))
                    if (ex.type.usesDistance) StatValue("Longest distance", rec.longestDistanceM?.let(::formatDistance) ?: "-", Modifier.weight(1f))
                }
            }
        }
        if (ex.type.usesWeight && rec.repMaxes.isNotEmpty()) {
            SectionLabel("Rep maxes")
            ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                (1..12).forEach { r ->
                    val w = rec.repMaxes[r]
                    Row(Modifier.fillMaxWidth()) {
                        Text("$r ${if (r == 1) "rep" else "reps"}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text(if (w == null) "-" else "${WorkoutMath.kg(w)} kg", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartsTab(rows: List<ExerciseSetRow>) {
    val sessions = remember(rows) { WorkoutMath.sessions(rows) }
    var sel by remember { mutableStateOf(RangeSelection()) }
    val today = LocalDate.now()
    val range = Ranges.resolve(sel.preset, today, sessions.firstOrNull()?.date, sel.customFrom, sel.customTo)
    val inRange = sessions.filter { !it.date.isBefore(range.first) && !it.date.isAfter(range.second) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RangeSelector(sel, range, { sel = it })
        ChartCard("Est. 1RM (kg)", sessions, inRange, range, MetricColors.Weight) { it.best1RmKg }
        ChartCard("Max weight (kg)", sessions, inRange, range, MetricColors.Protein) { it.maxWeightKg }
        ChartCard("Session volume (kg)", sessions, inRange, range, MetricColors.Workout) { it.volumeKg.takeIf { v -> v > 0 } }
    }
}

@Composable
private fun ChartCard(
    title: String,
    all: List<ExerciseSessionPoint>,
    sessions: List<ExerciseSessionPoint>,
    range: Pair<LocalDate, LocalDate>,
    color: androidx.compose.ui.graphics.Color,
    value: (ExerciseSessionPoint) -> Double?,
) {
    val pts = sessions.mapNotNull { s -> value(s)?.let { DatePoint(s.date, it) } }
    ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium)
        if (pts.isEmpty()) Text("No data in this range.", style = MaterialTheme.typography.bodyMedium)
        else {
            // "PR" marks the all-time best, and only when it falls inside the visible range.
            val allTimeBest = all.mapNotNull { s -> value(s)?.let { DatePoint(s.date, it) } }.maxByOrNull { it.value }
            val best = allTimeBest?.takeIf { b -> pts.any { it.date == b.date && it.value == b.value } }
            TimeChart(range.first, range.second, height = 160.dp, lines = listOf(LineSeries(pts, color, dots = true)), highlight = best)
        }
    }
}
