package com.yatharth.yool.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.domain.TargetHistory
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxCollapsible
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxDialogButtons
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.settings.targets.requiredWeeklyRateKg
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private fun fmtValue(metric: TargetMetric, v: Double): String = when (metric) {
    TargetMetric.WEIGHT_GOAL, TargetMetric.SLEEP_HOURS -> Fmt.one(v)
    else -> Fmt.int(v.toLong())
}

@Composable
fun TargetsSection() {
    val context = LocalContext.current
    val repo = remember { context.appContainer.repository }
    val scope = rememberCoroutineScope()
    val history by repo.observeTargets().collectAsState(initial = TargetHistory.Empty)
    val today = remember { LocalDate.now() }
    val latestWeight by remember {
        repo.observeDays(today.minusDays(60), today).map { days -> days.lastOrNull { it.weightKg != null }?.weightKg }
    }.collectAsState(initial = null)

    var editing by remember { mutableStateOf<TargetMetric?>(null) }

    TxListGroup {
        val metrics = TargetMetric.entries
        metrics.forEachIndexed { index, metric ->
            val current = history.current(metric)
            val goalDate = current?.targetDate
            var subtitle: String? = null
            if (metric == TargetMetric.WEIGHT_GOAL && current != null && goalDate != null) {
                subtitle = "By ${Fmt.date(goalDate)}"
                val rate = requiredWeeklyRateKg(latestWeight, current.value, goalDate, today)
                if (rate != null) subtitle += String.format(Locale.US, " · needs %+.2f kg/week", rate)
            }
            TxListRow(
                title = metric.label + if (metric == TargetMetric.CALORIES) " (max)" else "",
                onClick = { editing = metric },
                subtitle = subtitle,
                value = current?.let { fmtValue(metric, it.value) + " " + metric.unit } ?: "—",
                divider = index < metrics.lastIndex,
            )
        }
    }

    editing?.let { metric ->
        val current = history.current(metric)
        TxDialog(metric.label, onDismiss = { editing = null }) {
            TargetEditor(
                metric = metric,
                initial = current,
                today = today,
                onCancel = { editing = null },
                onSave = { entry -> scope.launch { repo.upsertTarget(entry); editing = null } },
            )
            val entries = history.history(metric)
            if (entries.isNotEmpty()) {
                TxCollapsible("History (${entries.size})", key = "target-history-${metric.name}") {
                    entries.asReversed().forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${fmtValue(metric, e.value)} ${metric.unit}" + (e.targetDate?.let { " · by ${Fmt.date(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text("from ${Fmt.date(e.effectiveFrom)}", style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f))
                            }
                            TxButton("Delete", onClick = { scope.launch { repo.deleteTarget(e.id) } }, fill = false)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetEditor(
    metric: TargetMetric,
    initial: TargetEntry?,
    today: LocalDate,
    onCancel: () -> Unit,
    onSave: (TargetEntry) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.let { fmtValue(metric, it.value).replace(",", "") } ?: "") }
    var from by remember { mutableStateOf(today) }
    var targetDate by remember { mutableStateOf(if (metric == TargetMetric.WEIGHT_GOAL) initial?.targetDate else null) }
    var picking by remember { mutableStateOf<String?>(null) }
    val value = Fmt.parseDouble(text)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TxTextField(text, { text = it }, label = metric.label, unit = metric.unit, numeric = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("EFFECTIVE FROM", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            TxChip(Fmt.date(from), selected = true, onClick = { picking = "from" })
        }
        if (metric == TargetMetric.WEIGHT_GOAL) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TARGET DATE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TxChip(targetDate?.let { Fmt.date(it) } ?: "None", selected = targetDate != null, onClick = { picking = "target" })
                if (targetDate != null) TxChip("Clear", selected = false, onClick = { targetDate = null })
            }
        }
        TxDialogButtons(
            confirmLabel = "Save",
            onConfirm = { value?.let { onSave(TargetEntry(metric = metric, value = it, effectiveFrom = from, targetDate = targetDate)) } },
            onDismiss = onCancel,
            confirmEnabled = value != null && value > 0,
        )
    }

    picking?.let { which ->
        val initialDate = if (which == "from") from else (targetDate ?: today.plusMonths(3))
        val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TxButton("OK", fill = false, onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "from") from = d else targetDate = d
                    }
                    picking = null
                })
            },
            dismissButton = { TxButton("Cancel", fill = false, onClick = { picking = null }) },
            colors = txDatePickerColors(),
        ) { DatePicker(state, colors = txDatePickerColors()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun txDatePickerColors() = DatePickerDefaults.colors(
    containerColor = ItemsBg,
    titleContentColor = Ink,
    headlineContentColor = Ink,
    weekdayContentColor = Ink.copy(alpha = 0.7f),
    subheadContentColor = Ink,
    navigationContentColor = Ink,
    yearContentColor = Ink,
    currentYearContentColor = Ink,
    selectedYearContainerColor = Coral,
    selectedYearContentColor = ItemsBg,
    dayContentColor = Ink,
    selectedDayContainerColor = Coral,
    selectedDayContentColor = ItemsBg,
    todayContentColor = Ink,
    todayDateBorderColor = Ink,
)
