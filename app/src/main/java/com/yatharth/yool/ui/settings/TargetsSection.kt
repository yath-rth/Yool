package com.yatharth.yool.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.settings.targets.requiredWeeklyRateKg
import com.yatharth.yool.ui.theme.Ink
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
    var expanded by remember { mutableStateOf<TargetMetric?>(null) }

    ShadowCard {
        SectionLabel("Targets")
        TargetMetric.entries.forEach { metric ->
            val current = history.current(metric)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatValue(
                        label = metric.label + if (metric == TargetMetric.CALORIES) " (max)" else "",
                        value = current?.let { fmtValue(metric, it.value) } ?: "—",
                        unit = metric.unit,
                        modifier = Modifier.weight(1f),
                    )
                    TxChip("Edit", selected = false, onClick = { editing = if (editing == metric) null else metric })
                }
                val goalDate = current?.targetDate
                if (metric == TargetMetric.WEIGHT_GOAL && current != null && goalDate != null) {
                    Text("By ${Fmt.day(goalDate)}", style = MaterialTheme.typography.bodySmall)
                    val latest = latestWeight
                    val rate = requiredWeeklyRateKg(latest, current.value, goalDate, today)
                    if (rate != null && latest != null) {
                        Text(
                            String.format(Locale.US, "Needs %+.2f kg/week from %.1f kg", rate, latest),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (editing == metric) {
                    TargetEditor(
                        metric = metric,
                        initial = current,
                        today = today,
                        onCancel = { editing = null },
                        onSave = { entry -> scope.launch { repo.upsertTarget(entry); editing = null } },
                    )
                }
                val entries = history.history(metric)
                if (entries.isNotEmpty()) {
                    Text(
                        (if (expanded == metric) "Hide history" else "History (${entries.size})").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { expanded = if (expanded == metric) null else metric },
                    )
                }
                if (expanded == metric) {
                    entries.asReversed().forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${fmtValue(metric, e.value)} ${metric.unit}" + (e.targetDate?.let { " · by ${Fmt.day(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text("from ${Fmt.day(e.effectiveFrom)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                            TxChip("Delete", selected = false, onClick = { scope.launch { repo.deleteTarget(e.id) } })
                        }
                    }
                }
            }
        }
    }
}

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
            TxChip(Fmt.day(from), selected = true, onClick = { picking = "from" })
        }
        if (metric == TargetMetric.WEIGHT_GOAL) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TARGET DATE", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TxChip(targetDate?.let { Fmt.day(it) } ?: "None", selected = targetDate != null, onClick = { picking = "target" })
                if (targetDate != null) TxChip("Clear", selected = false, onClick = { targetDate = null })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("Cancel", onCancel, Modifier.weight(1f))
            TxButton(
                "Save",
                onClick = { value?.let { onSave(TargetEntry(metric = metric, value = it, effectiveFrom = from, targetDate = targetDate)) } },
                modifier = Modifier.weight(1f),
                enabled = value != null && value > 0,
            )
        }
    }

    picking?.let { which ->
        val initialDate = if (which == "from") from else (targetDate ?: today.plusMonths(3))
        val state = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (which == "from") from = d else targetDate = d
                    }
                    picking = null
                }) { Text("OK", color = Ink) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel", color = Ink) } },
        ) { DatePicker(state) }
    }
}
