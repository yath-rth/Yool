package com.yatharth.yool.ui.graphs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yatharth.yool.domain.RangePreset
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxSegmented
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** User's range choice; [custom] only matters for [RangePreset.CUSTOM]. */
data class RangeSelection(
    val preset: RangePreset = RangePreset.M1,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
)

/** 1W / 1M / 3M / 1Y / All / Dates (opens a date range picker). [resolved] is shown beneath as the active window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSelector(
    selection: RangeSelection,
    resolved: Pair<LocalDate, LocalDate>?,
    onChange: (RangeSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier) {
        TxSegmented(
            options = RangePreset.entries,
            selected = selection.preset,
            onSelect = { p -> if (p == RangePreset.CUSTOM) picking = true else onChange(selection.copy(preset = p)) },
            label = { it.label },
        )
        if (resolved != null) {
            Text(
                "${Fmt.day(resolved.first)} to ${Fmt.day(resolved.second)}",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = selection.customFrom?.toUtcMillis(),
            initialSelectedEndDateMillis = selection.customTo?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TxButton(
                    "Apply",
                    onClick = {
                        val s = state.selectedStartDateMillis?.fromUtcMillis()
                        val e = state.selectedEndDateMillis?.fromUtcMillis() ?: s
                        if (s != null && e != null) onChange(RangeSelection(RangePreset.CUSTOM, s, e))
                        picking = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                    modifier = Modifier.width(110.dp).padding(end = 8.dp, bottom = 8.dp),
                )
            },
            dismissButton = {
                TxButton("Cancel", onClick = { picking = false }, modifier = Modifier.width(110.dp).padding(end = 8.dp, bottom = 8.dp))
            },
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.fromUtcMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
