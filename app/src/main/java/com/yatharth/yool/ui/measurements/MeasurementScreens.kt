package com.yatharth.yool.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.model.MeasurementPart
import com.yatharth.yool.domain.DatePoint
import com.yatharth.yool.domain.MeasurementMath
import com.yatharth.yool.domain.Ranges
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxDatePickerDialog
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxMetricTile
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.graphs.LineSeries
import com.yatharth.yool.ui.graphs.RangeSelection
import com.yatharth.yool.ui.graphs.RangeSelector
import com.yatharth.yool.ui.graphs.TimeChart
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.MetricColors
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun MeasurementPart.fmt(v: Double) = "${Fmt.one(v)} $unit"

@Composable
private fun ScrollColumn(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

// ── Overview ─────────────────────────────────────────────────────────────────

/** Latest value per measured part, grouped by body area, with the change since the previous measurement. */
@Composable
fun MeasurementsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onLog: () -> Unit,
    onOpenPart: (MeasurementPart) -> Unit,
) {
    val repo = LocalContext.current.appContainer.measurementRepository
    val all by remember { repo.observeAll() }.collectAsStateWithLifecycle(null)
    val summaries = remember(all) { MeasurementMath.summaries(all.orEmpty()) }
    val last = summaries.maxOfOrNull { it.latestDate }

    ScrollColumn(contentPadding) {
        TxTopBar("Measurements", subtitle = last?.let { "Last logged ${Fmt.date(it)}" }, onBack = onBack)
        TxButton("Log measurements", onLog, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
        if (all != null && summaries.isEmpty()) {
            EmptyState("No measurements yet", "Log your waist, arms, chest and more to track them over time.", icon = Icons.Outlined.Straighten)
        }
        summaries.groupBy { it.part.group }.forEach { (group, rows) ->
            TxListGroup(title = group) {
                rows.forEachIndexed { i, s ->
                    TxListRow(
                        s.part.label,
                        onClick = { onOpenPart(s.part) },
                        subtitle = Fmt.date(s.latestDate) + (s.change?.let { " · ${MeasurementMath.signed(it)} ${s.part.unit}" } ?: ""),
                        value = s.part.fmt(s.latest),
                        divider = i < rows.lastIndex,
                    )
                }
            }
        }
    }
}

// ── Log / edit one day ───────────────────────────────────────────────────────

/**
 * Enter any subset of parts for [initialDate] (null = today). Empty fields are left out; clearing a
 * value that was saved for that day deletes it. The last known value shows as a placeholder.
 */
@Composable
fun MeasurementLogScreen(initialDate: LocalDate?, contentPadding: PaddingValues, onDone: () -> Unit) {
    val repo = LocalContext.current.appContainer.measurementRepository
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val fields = remember { mutableStateMapOf<MeasurementPart, String>() }
    /** Parts that already had a value on [date] when it was loaded (so clearing them deletes). */
    var savedParts by remember { mutableStateOf(emptySet<MeasurementPart>()) }
    val all by remember { repo.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val previous = remember(all, date) {
        all.filter { it.date.isBefore(date) }.groupBy { it.part }.mapValues { (_, l) -> l.maxBy { it.date }.value }
    }

    LaunchedEffect(date) {
        val existing = repo.onDate(date)
        fields.clear()
        existing.forEach { fields[it.part] = Fmt.one(it.value) }
        savedParts = existing.map { it.part }.toSet()
    }

    ScrollColumn(contentPadding) {
        TxTopBar(if (savedParts.isEmpty()) "Log measurements" else "Edit measurements", onBack = onDone)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxChip(Fmt.date(date), selected = true, onClick = { picking = true })
        }
        MeasurementPart.entries.groupBy { it.group }.forEach { (group, parts) ->
            ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Text(group.uppercase(), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    parts.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { part ->
                                TxTextField(
                                    fields[part].orEmpty(), { fields[part] = it }, part.label,
                                    Modifier.weight(1f), unit = part.unit, numeric = true,
                                    placeholder = previous[part]?.let { Fmt.one(it) }.orEmpty(),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        val parsed = MeasurementMath.parseForm(fields.filterKeys { it in savedParts || fields[it].orEmpty().isNotBlank() })
        val anything = parsed.isNotEmpty()
        TxButton(
            "Save", {
                scope.launch {
                    repo.save(date, parsed)
                    onDone()
                }
            },
            Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg, enabled = anything,
        )
    }

    if (picking) {
        TxDatePickerDialog("Measured on", date, onConfirm = { date = it; picking = false }, onDismiss = { picking = false })
    }
}

// ── One part: chart + history ────────────────────────────────────────────────

@Composable
fun MeasurementPartScreen(part: MeasurementPart, contentPadding: PaddingValues, onBack: () -> Unit, onEditDay: (LocalDate) -> Unit) {
    val repo = LocalContext.current.appContainer.measurementRepository
    val list by remember(part) { repo.observePart(part) }.collectAsStateWithLifecycle(null)
    var sel by remember { mutableStateOf(RangeSelection(preset = com.yatharth.yool.domain.RangePreset.ALL)) }
    val rows = list.orEmpty()

    ScrollColumn(contentPadding) {
        TxTopBar(part.label, subtitle = part.group, onBack = onBack)
        if (list != null && rows.isEmpty()) {
            EmptyState("No ${part.label.lowercase()} measurements", "Log one from the Measurements screen.")
            return@ScrollColumn
        }
        val latest = rows.lastOrNull() ?: return@ScrollColumn
        val first = rows.first()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxMetricTile("Latest", Fmt.one(latest.value), Modifier.weight(1f), unit = part.unit, footnote = Fmt.date(latest.date))
            val total = latest.value - first.value
            TxMetricTile(
                "Change",
                if (rows.size < 2) "—" else MeasurementMath.signed(total),
                Modifier.weight(1f),
                unit = if (rows.size < 2) null else part.unit,
                footnote = if (rows.size < 2) "Log again to compare" else "since ${Fmt.date(first.date)}",
            )
        }
        val today = LocalDate.now()
        val range = Ranges.resolve(sel.preset, today, first.date, sel.customFrom, sel.customTo)
        RangeSelector(sel, range, { sel = it })
        val pts = rows.filter { !it.date.isBefore(range.first) && !it.date.isAfter(range.second) }.map { DatePoint(it.date, it.value) }
        ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
            if (pts.isEmpty()) androidx.compose.material3.Text("No measurements in this range.")
            else TimeChart(range.first, range.second, height = 180.dp, lines = listOf(LineSeries(pts, MetricColors.Weight, dots = true)))
        }
        TxListGroup(title = "History") {
            val newestFirst = rows.asReversed()
            newestFirst.forEachIndexed { i, m ->
                val prev = newestFirst.getOrNull(i + 1)
                TxListRow(
                    Fmt.date(m.date),
                    onClick = { onEditDay(m.date) },
                    subtitle = prev?.let { "${MeasurementMath.signed(m.value - it.value)} ${part.unit}" },
                    value = part.fmt(m.value),
                    divider = i < newestFirst.lastIndex,
                )
            }
        }
    }
}
