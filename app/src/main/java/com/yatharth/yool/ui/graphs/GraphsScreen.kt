package com.yatharth.yool.ui.graphs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.domain.DatePoint
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.Stats
import com.yatharth.yool.domain.Streaks
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxBadge
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxSegmented
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.insights.InsightsScreen
import com.yatharth.yool.ui.day.moodColor
import com.yatharth.yool.ui.theme.Amber
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.MetricColors
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private fun GraphMetric.color(): Color = when (this) {
    GraphMetric.WEIGHT -> MetricColors.Weight
    GraphMetric.CALORIES -> MetricColors.Calories
    GraphMetric.PROTEIN -> MetricColors.Protein
    GraphMetric.STEPS -> MetricColors.Steps
    GraphMetric.SLEEP -> MetricColors.Sleep
    GraphMetric.MOOD -> MetricColors.Mood
    GraphMetric.BINGE -> MetricColors.Binge
    GraphMetric.WORKOUTS -> MetricColors.Workout
    GraphMetric.LIFTS -> MetricColors.Weight
}

private enum class GraphsTab(val label: String) { GRAPHS("Graphs"), INSIGHTS("Insights") }

@Composable
fun GraphsScreen(contentPadding: PaddingValues) {
    var tab by rememberSaveable { mutableStateOf(GraphsTab.GRAPHS) }
    val switch: @Composable () -> Unit = {
        TxSegmented(GraphsTab.entries, tab, onSelect = { tab = it }, label = { it.label.uppercase() })
    }
    when (tab) {
        GraphsTab.GRAPHS -> GraphsContent(contentPadding, switch)
        GraphsTab.INSIGHTS -> InsightsScreen(contentPadding, switch)
    }
}

@Composable
private fun GraphsContent(contentPadding: PaddingValues, header: @Composable () -> Unit) {
    val repo = LocalContext.current.appContainer.repository
    val vm: GraphsViewModel = viewModel(factory = viewModelFactory { initializer { GraphsViewModel(repo) } })
    val selection by vm.selection.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val exercise by vm.exercise.collectAsStateWithLifecycle()
    val streak by vm.currentStreak.collectAsStateWithLifecycle()

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
        RangeSelector(selection, data?.let { it.from to it.to }, onChange = { vm.selection.value = it })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GraphMetric.entries.forEach { m ->
                TxChip(m.label, selected = m == metric, onClick = { vm.metric.value = m }, color = m.color().takeIf { m != GraphMetric.WEIGHT && m != GraphMetric.LIFTS } ?: Ink)
            }
        }
        val d = data
        if (d == null) {
            EmptyState("Loading", "Crunching your numbers.")
        } else when (metric) {
            GraphMetric.WEIGHT -> WeightSection(d)
            GraphMetric.CALORIES -> BarSection(
                d, "Calories", MetricColors.Calories, TargetMetric.CALORIES, ceiling = true, unit = "kcal",
                value = { it.calories?.toDouble() }, fmt = ::compact, targetHint = "Bars over the limit turn red.",
            )
            GraphMetric.PROTEIN -> BarSection(
                d, "Protein", MetricColors.Protein, TargetMetric.PROTEIN, ceiling = false, unit = "g",
                value = { it.proteinG }, fmt = ::compact, targetHint = "",
            )
            GraphMetric.STEPS -> BarSection(
                d, "Steps", MetricColors.Steps, TargetMetric.STEPS, ceiling = false, unit = "steps",
                value = { it.steps?.toDouble() }, fmt = ::compact, targetHint = "",
            )
            GraphMetric.SLEEP -> BarSection(
                d, "Sleep", MetricColors.Sleep, TargetMetric.SLEEP_HOURS, ceiling = false, unit = "h",
                value = { it.sleepHours }, fmt = { Fmt.one(it) }, targetHint = "",
            )
            GraphMetric.MOOD -> MoodSection(d)
            GraphMetric.BINGE -> BingeSection(d, if (d.to == LocalDate.now()) streak else Streaks.bingeFree(d.days, d.to))
            GraphMetric.WORKOUTS -> WorkoutSection(d)
            GraphMetric.LIFTS -> LiftSection(d, exercises, exercise, onPick = { vm.exercise.value = it })
        }
    }
}

// ── Shared pieces ────────────────────────────────────────────────────────────

private fun compact(v: Double): String = when {
    abs(v) >= 10_000 -> String.format(Locale.US, "%.0fk", v / 1000)
    abs(v) >= 1_000 -> String.format(Locale.US, "%.1fk", v / 1000).replace(".0k", "k")
    abs(v) >= 100 -> String.format(Locale.US, "%.0f", v)
    else -> String.format(Locale.US, "%.1f", v).removeSuffix(".0")
}

private fun rangeLength(d: GraphData) = Stats.daysBetween(d.from, d.to) + 1

@Composable
private fun ChartCard(
    title: String,
    caption: String? = null,
    legend: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    ShadowCard {
        SectionLabel(title)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), content = legend)
        Box(Modifier.padding(top = 4.dp)) { content() }
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f))
    }
}

@Composable
private fun StatRow(vararg stats: Triple<String, String, String?>, color: Color = Ink) {
    ShadowCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.forEach { (label, value, unit) -> StatValue(label, value, Modifier.weight(1f), unit = unit, color = color) }
        }
    }
}

@Composable
private fun NoData(what: String) {
    ShadowCard { EmptyState("Nothing here yet", "No $what logged in this range. Try a longer range or log a few days.") }
}

private fun series(days: List<ResolvedDay>, f: (ResolvedDay) -> Double?): List<DatePoint> =
    days.mapNotNull { d -> f(d)?.let { DatePoint(d.date, it) } }

// ── Weight ───────────────────────────────────────────────────────────────────

@Composable
private fun WeightSection(d: GraphData) {
    val pts = series(d.days) { it.weightKg }
    if (pts.isEmpty()) return NoData("weigh-ins")
    val ma = Stats.movingAverage(pts, 7)
    val goal = Stats.targetSteps(d.targets, TargetMetric.WEIGHT_GOAL, d.from, d.to)
    val long = rangeLength(d) > 90
    ChartCard(
        "Weight (kg)",
        caption = if (long) "Faint line is daily, solid is the 7-day average." else null,
        legend = {
            LegendItem(MetricColors.Weight.copy(alpha = 0.45f), "Daily")
            LegendItem(MetricColors.Weight, "7d avg")
            if (goal.isNotEmpty()) LegendItem(Green, "Goal", dashed = true)
        },
    ) {
        TimeChart(
            d.from, d.to,
            lines = listOf(
                LineSeries(pts, MetricColors.Weight.copy(alpha = 0.4f), strokeDp = 1.5f, dots = !long, dotRadiusDp = 2f),
                LineSeries(ma, MetricColors.Weight, strokeDp = 2.5f),
            ),
            stepLine = goal, stepColor = Green,
        )
    }
    val start = pts.minBy { it.date }.value
    val current = ma.last().value
    val perWeek = Stats.slopePerWeek(pts)
    StatRow(
        Triple("Start", Fmt.kg(start), "kg"),
        Triple("7d avg", Fmt.kg(current), "kg"),
        Triple("Change", signed(current - start), "kg"),
        Triple("Per week", perWeek?.let { signed(it) } ?: "—", "kg"),
    )
}

private fun signed(v: Double): String = (if (v > 0) "+" else "") + Fmt.kg(v)

// ── Bar metrics ──────────────────────────────────────────────────────────────

@Composable
private fun BarSection(
    d: GraphData,
    title: String,
    color: Color,
    target: TargetMetric,
    ceiling: Boolean,
    unit: String,
    value: (ResolvedDay) -> Double?,
    fmt: (Double) -> String,
    targetHint: String,
) {
    val daily = series(d.days, value)
    if (daily.isEmpty()) return NoData(title.lowercase())
    val (pts, weekly) = Stats.bucketForDisplay(daily, rangeLength(d))
    val chartFrom = if (weekly) Stats.weekStart(d.from) else d.from
    val chartTo = if (weekly) Stats.weekStart(d.to).plusDays(6) else d.to
    val steps = Stats.targetSteps(d.targets, target, chartFrom, chartTo)
    val bars = pts.map { p ->
        val t = d.targets.valueOn(target, p.date)
        BarDatum(p.date, p.value, if (ceiling && t != null && p.value > t) Coral else color)
    }
    ChartCard(
        if (weekly) "$title, weekly avg" else title,
        caption = (if (steps.isNotEmpty()) targetHint else "No $title target set. Add one in Settings.").ifBlank { null },
        legend = {
            LegendItem(color, if (weekly) "Weekly avg" else "Daily")
            if (ceiling) LegendItem(Coral, "Over limit")
            if (steps.isNotEmpty()) LegendItem(Ink, "Target", dashed = true)
        },
    ) {
        TimeChart(chartFrom, chartTo, bars = bars, barSpanDays = if (weekly) 7 else 1, stepLine = steps, stepColor = Ink, yFormat = fmt)
    }
    val checks = d.days.map { day ->
        val v = value(day)
        val t = d.targets.valueOn(target, day.date)
        if (v == null || t == null) null else if (ceiling) v <= t else v >= t
    }
    val pct = Stats.percentHit(checks)
    StatRow(
        Triple("Average", fmt(Stats.mean(daily.map { it.value })!!), unit),
        Triple(if (ceiling) "Within limit" else "Target hit", pct?.let { "${it.roundToInt()}%" } ?: "—", null),
        Triple("Days logged", "${daily.size}", null),
        color = color.takeIf { it != MetricColors.Weight } ?: Ink,
    )
}

// ── Mood ─────────────────────────────────────────────────────────────────────

@Composable
private fun MoodSection(d: GraphData) {
    val daily = series(d.days) { it.mood?.toDouble() }
    if (daily.isEmpty()) return NoData("moods")
    val (pts, weekly) = Stats.bucketForDisplay(daily, rangeLength(d))
    val chartFrom = if (weekly) Stats.weekStart(d.from) else d.from
    val chartTo = if (weekly) Stats.weekStart(d.to).plusDays(6) else d.to
    ChartCard(
        if (weekly) "Mood, weekly avg" else "Mood") {
        TimeChart(
            chartFrom, chartTo,
            lines = listOf(
                LineSeries(
                    // weekly points sit at the week start; shift to mid-week for readability
                    if (weekly) pts.map { it.copy(date = it.date.plusDays(3)) } else pts,
                    MetricColors.Mood, strokeDp = 2f, dots = true,
                    dotColor = { moodColor(it.value.roundToInt().coerceIn(1, 5)) }, dotRadiusDp = 3.5f,
                )
            ),
            yMin = 1.0, yMax = 5.0, height = 180.dp,
            yFormat = { String.format(Locale.US, "%.0f", it) },
        )
    }
    val dist = Stats.moodDistribution(d.days)
    val maxCount = (dist.values.maxOrNull() ?: 1).coerceAtLeast(1)
    StatRow(
        Triple("Average", Fmt.one(Stats.mean(daily.map { it.value })), "/ 5"),
        Triple("Days logged", "${daily.size}", null),
        color = MetricColors.Mood,
    )
    ShadowCard {
        SectionLabel("Distribution")
        for (m in 5 downTo 1) {
            HBarRow("Mood $m", (dist[m] ?: 0) / maxCount.toFloat(), "${dist[m] ?: 0}", moodColor(m))
        }
    }
}

// ── Binge ────────────────────────────────────────────────────────────────────

@Composable
private fun BingeSection(d: GraphData, streak: Int) {
    val logged = d.days.count { it.binge != null }
    if (logged == 0) return NoData("check-ins")
    val bingeCount = d.days.count { it.binge == true }
    ChartCard(
        "Binge calendar",
        legend = {
            LegendItem(Green, "No binge")
            LegendItem(Coral, "Binge")
            LegendItem(Ink.copy(alpha = 0.15f), "Not logged")
        },
    ) {
        BingeHeatmap(d.days, d.from, d.to)
    }
    StatRow(
        Triple("Streak", "$streak", "days"),
        Triple("Longest", "${Stats.longestBingeFree(d.days)}", "days"),
        Triple("Binges", "$bingeCount", "of $logged"),
    )
    val triggers = Stats.triggerCounts(d.days)
    if (triggers.isNotEmpty()) {
        val max = triggers.maxOf { it.second }.toFloat()
        ShadowCard {
            SectionLabel("Triggers")
            triggers.forEach { (name, n) -> HBarRow(name, n / max, "$n", Coral) }
        }
    }
}

// ── Workouts ─────────────────────────────────────────────────────────────────

@Composable
private fun WorkoutSection(d: GraphData) {
    val weeks = Stats.workoutsPerWeek(d.days, d.from, d.to)
    val total = weeks.sumOf { it.value }.roundToInt()
    if (total == 0) return NoData("workouts")
    ChartCard("Workouts per week") {
        TimeChart(
            Stats.weekStart(d.from), Stats.weekStart(d.to).plusDays(6),
            bars = weeks.map { BarDatum(it.date, it.value, MetricColors.Workout) },
            barSpanDays = 7, height = 180.dp, yMax = maxOf(4.0, weeks.maxOf { it.value }),
            yFormat = { String.format(Locale.US, "%.0f", it) },
        )
    }
    StatRow(
        Triple("Total", "$total", "workouts"),
        Triple("Per week", Fmt.one(total / weeks.size.toDouble()), null),
        color = MetricColors.Workout,
    )
}

// ── Lifts ────────────────────────────────────────────────────────────────────

@Composable
private fun LiftSection(d: GraphData, exercises: List<String>, selected: String?, onPick: (String) -> Unit) {
    if (exercises.isEmpty()) {
        ShadowCard {
            EmptyState("No lifts yet", "Import a Strong CSV export from Settings, then Import, to see your top sets here.")
        }
        return
    }
    val current = selected?.takeIf { it in exercises } ?: exercises.first()
    SectionLabel("Exercise")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        exercises.forEach { ex -> TxChip(ex, selected = ex == current, onClick = { onPick(ex) }, color = Ink) }
    }
    val sets = d.lifts.filter { it.exercise == current }.sortedBy { it.date }
    if (sets.isEmpty()) return NoData("$current sets")
    val pr = sets.maxWith(compareBy({ it.weightKg }, { it.reps }, { it.date }))
    ChartCard(
        "$current, top set (kg)",
        legend = { LegendItem(MetricColors.Weight, "Top set"); LegendItem(Amber, "PR") },
    ) {
        TimeChart(
            d.from, d.to,
            lines = listOf(LineSeries(sets.map { DatePoint(it.date, it.weightKg) }, MetricColors.Weight, dots = true)),
            highlight = DatePoint(pr.date, pr.weightKg),
        )
    }
    var showAll by rememberSaveable(current) { mutableStateOf(false) }
    val recent = remember(sets) { sets.asReversed() }
    ShadowCard {
        SectionLabel("Sessions")
        (if (showAll) recent else recent.take(5)).forEach { s ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Fmt.date(s.date), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (s == pr) TxBadge("PR", color = Amber, textColor = Ink)
                Text("${Fmt.kg(s.weightKg).removeSuffix(".0")} kg × ${s.reps}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (recent.size > 5) {
            TxButton(if (showAll) "Show less" else "Show all (${recent.size})", onClick = { showAll = !showAll }, fill = false)
        }
    }
}
