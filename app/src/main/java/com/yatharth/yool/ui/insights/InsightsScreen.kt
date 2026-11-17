package com.yatharth.yool.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.domain.Adherence
import com.yatharth.yool.domain.BingeComparison
import com.yatharth.yool.domain.GroupAvg
import com.yatharth.yool.domain.Insights
import com.yatharth.yool.domain.RangePreset
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.TargetHistory
import com.yatharth.yool.domain.TrendVerdict
import com.yatharth.yool.domain.WeightTrend
import com.yatharth.yool.domain.WorkoutVsRest
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxCollapsible
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.graphs.HBarRow
import com.yatharth.yool.ui.graphs.RangeSelection
import com.yatharth.yool.ui.graphs.RangeSelector
import com.yatharth.yool.ui.graphs.resolved
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.MetricColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class InsightsUi(
    val from: LocalDate,
    val to: LocalDate,
    val sleepVsMood: List<GroupAvg>,
    val binge: BingeComparison,
    val stepsVsMood: Pair<GroupAvg, GroupAvg>,
    val adherence: List<Adherence>,
    val weight: WeightTrend,
    val workout: WorkoutVsRest,
    val hasStepTarget: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(repo: TrackerRepository) : ViewModel() {
    private val today = LocalDate.now()
    val selection = MutableStateFlow(RangeSelection(RangePreset.M3))

    val ui: StateFlow<InsightsUi?> = selection.resolved(repo, today)
        .flatMapLatest { (from, to) ->
            combine(repo.observeDays(from, to), repo.observeTargets()) { days, targets -> build(from, to, days, targets) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(from: LocalDate, to: LocalDate, days: List<ResolvedDay>, targets: TargetHistory) = InsightsUi(
        from, to,
        sleepVsMood = Insights.sleepVsNextMood(days),
        binge = Insights.bingeComparison(days),
        stepsVsMood = Insights.stepsVsMood(days, targets),
        adherence = Insights.adherence(days, targets),
        weight = Insights.weightTrend(days, targets, minOf(today, to)),
        workout = Insights.workoutVsRest(days),
        hasStepTarget = targets.history(com.yatharth.yool.data.model.TargetMetric.STEPS).isNotEmpty(),
    )
}

@Composable
fun InsightsScreen(contentPadding: PaddingValues, header: @Composable () -> Unit = {}) {
    val repo = LocalContext.current.appContainer.repository
    val vm: InsightsViewModel = viewModel(factory = viewModelFactory { initializer { InsightsViewModel(repo) } })
    val selection by vm.selection.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TxTopBar("Progress")
        header()
        RangeSelector(selection, ui?.let { it.from to it.to }, onChange = { vm.selection.value = it })
        val u = ui
        if (u == null) {
            Text("Crunching your numbers.", style = MaterialTheme.typography.bodyMedium)
        } else {
            SleepCard(u.sleepVsMood)
            BingeCard(u.binge)
            StepsCard(u.stepsVsMood, u.hasStepTarget)
            AdherenceCard(u.adherence)
            WeightCard(u.weight)
            WorkoutCard(u.workout)
            Text(
                "Comparisons need at least ${Insights.MIN_N} days per group. Correlation is not cause.",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = 0.6f),
            )
        }
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

private const val NOT_ENOUGH = "Not enough data yet"

/** Title + one headline sentence by default; the supporting numbers and bars expand on tap. */
@Composable
private fun InsightCard(title: String, headline: String, content: @Composable () -> Unit) {
    TxCollapsible(title, summary = headline) { content() }
}

/** One group as a bar scaled to [max], with its average and sample size. */
@Composable
private fun GroupRow(g: GroupAvg, max: Double, color: Color, fmt: (Double) -> String = { Fmt.one(it) }) {
    val ok = g.enough && g.avg != null
    HBarRow(
        label = g.label,
        fraction = if (ok) (g.avg!! / max).toFloat() else 0f,
        valueText = if (ok) "${fmt(g.avg!!)} (n=${g.n})" else "n=${g.n}",
        color = color,
        labelWidth = 96.dp,
        valueWidth = 78.dp,
    )
}

@Composable
private fun SubLabel(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))

private fun pair(a: GroupAvg, b: GroupAvg) = a.enough && b.enough && a.avg != null && b.avg != null

// ── 1. Sleep vs next-day mood ────────────────────────────────────────────────

@Composable
private fun SleepCard(groups: List<GroupAvg>) {
    val usable = groups.filter { it.enough && it.avg != null }
    val best = usable.maxByOrNull { it.avg!! }
    val headline = if (usable.size < 2 || best == null) NOT_ENOUGH
    else "Your mood is best the day after ${best.label.lowercase()} of sleep (avg ${Fmt.one(best.avg)} of 5)."
    InsightCard("Sleep vs next-day mood", headline) {
        groups.forEach { GroupRow(it, 5.0, MetricColors.Sleep) }
        Text("Mood tomorrow by last night's sleep. n = nights.", style = MaterialTheme.typography.labelSmall)
    }
}

// ── 2. Binge days vs others ──────────────────────────────────────────────────

@Composable
private fun BingeCard(c: BingeComparison) {
    val enough = c.bingeDays >= Insights.MIN_N && c.otherDays >= Insights.MIN_N
    val headline = when {
        c.bingeDays == 0 -> "No binge days logged in this range."
        !enough -> NOT_ENOUGH + " (${c.bingeDays} binge days, ${c.otherDays} other days)"
        else -> "${c.bingeDays} binge days vs ${c.otherDays} other days."
    }
    InsightCard("Binge days vs others", headline) {
        if (enough) {
            ComparePair("Previous-day calories", c.prevCalories, MetricColors.Calories) { Fmt.int(it) }
            ComparePair("Previous-day sleep (h)", c.prevSleep, MetricColors.Sleep) { Fmt.one(it) }
            ComparePair("Same-day mood", c.mood, MetricColors.Mood) { Fmt.one(it) }
        }
        if (c.triggers.isNotEmpty()) {
            SubLabel("Triggers, most common first")
            val max = c.triggers.maxOf { it.second }.toFloat()
            c.triggers.forEach { (name, n) -> HBarRow(name, n / max, "$n", Coral) }
        }
    }
}

@Composable
private fun ComparePair(title: String, p: Pair<GroupAvg, GroupAvg>, color: Color, fmt: (Double) -> String) {
    SubLabel(title)
    val max = maxOf(p.first.avg ?: 0.0, p.second.avg ?: 0.0).coerceAtLeast(1e-9)
    GroupRow(p.first, max, Coral, fmt)
    GroupRow(p.second, max, color, fmt)
}

// ── 3. Steps vs mood ─────────────────────────────────────────────────────────

@Composable
private fun StepsCard(p: Pair<GroupAvg, GroupAvg>, hasTarget: Boolean) {
    val headline = when {
        !hasTarget -> "Set a step target in Settings to compare."
        !pair(p.first, p.second) -> NOT_ENOUGH
        else -> {
            val diff = p.first.avg!! - p.second.avg!!
            when {
                abs(diff) < 0.15 -> "Mood is about the same whether or not you hit your step target."
                diff > 0 -> "Mood runs ${Fmt.one(diff)} higher on days you hit your step target."
                else -> "Mood runs ${Fmt.one(-diff)} lower on days you hit your step target."
            }
        }
    }
    InsightCard("Steps vs mood", headline) {
        if (hasTarget) {
            GroupRow(p.first, 5.0, MetricColors.Steps)
            GroupRow(p.second, 5.0, MetricColors.Steps.copy(alpha = 0.5f))
        }
    }
}

// ── 4. Target adherence ──────────────────────────────────────────────────────

@Composable
private fun AdherenceCard(rows: List<Adherence>) {
    val usable = rows.filter { it.n >= Insights.MIN_N && it.percent != null }
    val headline = if (usable.isEmpty()) NOT_ENOUGH
    else usable.maxByOrNull { it.percent!! }!!.let { "Best adherence: ${it.label.lowercase()} target met on ${it.percent!!.roundToInt()}% of days." }
    InsightCard("Target adherence", headline) {
        rows.forEach { r ->
            val ok = r.n >= Insights.MIN_N && r.percent != null
            HBarRow(
                r.label,
                if (ok) (r.percent!! / 100.0).toFloat() else 0f,
                if (ok) "${r.percent!!.roundToInt()}% (n=${r.n})" else "n=${r.n}",
                if (ok && r.percent!! >= 70.0) Green else if (ok) Coral else Ink,
                labelWidth = 96.dp, valueWidth = 78.dp,
            )
        }
        Text("Uses the target that was in force on each day. No target set means the metric is skipped.", style = MaterialTheme.typography.labelSmall)
    }
}

// ── 5. Weight trend ──────────────────────────────────────────────────────────

@Composable
private fun WeightCard(w: WeightTrend) {
    val verdict = if (w.n < Insights.MIN_N) TrendVerdict.UNKNOWN else w.verdict
    val headline = when (verdict) {
        TrendVerdict.ON_TRACK -> "On track toward your goal weight."
        TrendVerdict.OFF_TRACK -> "Off track: current pace will not reach your goal on time."
        TrendVerdict.UNKNOWN -> if (w.goalKg == null && w.n >= Insights.MIN_N) "Set a goal weight in Settings to get a verdict." else NOT_ENOUGH
    }
    InsightCard("Weight trend", headline) {
        if (verdict != TrendVerdict.UNKNOWN || w.n >= Insights.MIN_N) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatValue("Actual", w.actualKgPerWeek?.let(::signedKg) ?: "—", Modifier.weight(1f), unit = "kg/wk",
                    color = when (verdict) { TrendVerdict.ON_TRACK -> Green; TrendVerdict.OFF_TRACK -> Coral; else -> Ink })
                StatValue("Required", w.requiredKgPerWeek?.let(::signedKg) ?: "—", Modifier.weight(1f), unit = "kg/wk")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatValue("Latest", Fmt.kg(w.currentKg), Modifier.weight(1f), unit = "kg")
                StatValue("Goal", Fmt.kg(w.goalKg), Modifier.weight(1f), unit = "kg")
            }
            if (w.goalKg != null && w.requiredKgPerWeek == null) {
                Text("Add a target date to your goal weight in Settings to see the required pace.", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text("Least-squares slope over ${w.n} weigh-ins in this range.", style = MaterialTheme.typography.labelSmall)
    }
}

private fun signedKg(v: Double) = (if (v > 0) "+" else "") + String.format(java.util.Locale.US, "%.2f", v)

// ── 6. Workout days vs rest days ─────────────────────────────────────────────

@Composable
private fun WorkoutCard(r: WorkoutVsRest) {
    val headline = when {
        !pair(r.workoutMood, r.restMood) -> NOT_ENOUGH
        else -> {
            val diff = r.workoutMood.avg!! - r.restMood.avg!!
            when {
                abs(diff) < 0.15 -> "Mood is about the same on workout and rest days."
                diff > 0 -> "Mood is ${Fmt.one(diff)} higher on workout days."
                else -> "Mood is ${Fmt.one(-diff)} lower on workout days."
            }
        }
    }
    InsightCard("Workout days vs rest days", headline) {
        SubLabel("Average mood")
        GroupRow(r.workoutMood, 5.0, MetricColors.Workout)
        GroupRow(r.restMood, 5.0, MetricColors.Workout.copy(alpha = 0.5f))
        SubLabel("Average calories")
        val max = maxOf(r.workoutCalories.avg ?: 0.0, r.restCalories.avg ?: 0.0).coerceAtLeast(1.0)
        GroupRow(r.workoutCalories, max, MetricColors.Calories) { Fmt.int(it) }
        GroupRow(r.restCalories, max, MetricColors.Calories.copy(alpha = 0.5f)) { Fmt.int(it) }
    }
}
