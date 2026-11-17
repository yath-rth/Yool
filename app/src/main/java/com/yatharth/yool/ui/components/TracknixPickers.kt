package com.yatharth.yool.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yatharth.yool.ui.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.SecondaryBg

/*
 * Material date/time pickers have no Tracknix equivalent, so every picker uses these colours:
 * itemsBg surface, ink text, coral selection. Pair with TxButton(fill = false) actions or TxDialog.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun txDatePickerColors(): DatePickerColors = DatePickerDefaults.colors(
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
    dayInSelectionRangeContainerColor = Coral.copy(alpha = 0.25f),
    dayInSelectionRangeContentColor = Ink,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun txTimePickerColors(): TimePickerColors = TimePickerDefaults.colors(
    clockDialColor = SecondaryBg,
    clockDialSelectedContentColor = ItemsBg,
    clockDialUnselectedContentColor = Ink,
    selectorColor = Coral,
    containerColor = ItemsBg,
    periodSelectorBorderColor = Ink,
    periodSelectorSelectedContainerColor = Coral,
    periodSelectorUnselectedContainerColor = ItemsBg,
    periodSelectorSelectedContentColor = ItemsBg,
    periodSelectorUnselectedContentColor = Ink,
    timeSelectorSelectedContainerColor = Coral,
    timeSelectorUnselectedContainerColor = SecondaryBg,
    timeSelectorSelectedContentColor = ItemsBg,
    timeSelectorUnselectedContentColor = Ink,
)

// ── Date dialogs ─────────────────────────────────────────────────────────────
//
// Material's DatePickerDialog draws its own 28dp-rounded surface and a headlineLarge headline that
// overflows in JetBrains Mono ("End date" wraps a letter per line). These wrap the bare pickers in a
// Tracknix card with a compact headline instead. Always use these, never DatePickerDialog.

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun PickerShell(title: String, headline: String, buttons: @Composable () -> Unit, picker: @Composable () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Material's pickers lay out 7 fixed 48dp day cells; a narrow side margin keeps Saturday unclipped.
        Box(Modifier.padding(horizontal = 6.dp)) {
            ShadowCard(contentPadding = 0.dp) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium)
                    Text(headline, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
                TxDivider()
                picker()
                TxDivider()
                Box(Modifier.padding(16.dp)) { buttons() }
            }
        }
    }
}

/** Single-date picker in a Tracknix card. [selectableUntil] caps the selectable range (inclusive). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDatePickerDialog(
    title: String,
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "OK",
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.utcMillis())
    val picked = state.selectedDateMillis?.utcDate()
    PickerShell(
        title = title,
        headline = picked?.let { Fmt.dateNoRelative(it) } ?: "Pick a date",
        onDismiss = onDismiss,
        buttons = { TxDialogButtons(confirmLabel, { picked?.let(onConfirm) }, onDismiss, confirmEnabled = picked != null) },
        picker = {
            DatePicker(
                state,
                modifier = Modifier.fillMaxWidth(),
                title = null,
                headline = null,
                showModeToggle = false,
                colors = txDatePickerColors(),
            )
        },
    )
}

/** Date-range picker in a Tracknix card. Tap a start day, then an end day (same day = one-day range). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxDateRangePickerDialog(
    title: String,
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onConfirm: (from: LocalDate, to: LocalDate) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Apply",
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.utcMillis(),
        initialSelectedEndDateMillis = initialTo?.utcMillis(),
    )
    val from = state.selectedStartDateMillis?.utcDate()
    val to = state.selectedEndDateMillis?.utcDate()
    val headline = when {
        from == null -> "Pick a start date"
        to == null -> "${Fmt.dateNoRelative(from)} – end?"
        from.year == to.year -> "${Fmt.dateNoRelativeWithoutYear(from)} – ${Fmt.dateNoRelative(to)}"
        else -> "${Fmt.dateNoRelative(from)} – ${Fmt.dateNoRelative(to)}"
    }
    PickerShell(
        title = title,
        headline = headline,
        onDismiss = onDismiss,
        buttons = { TxDialogButtons(confirmLabel, { if (from != null) onConfirm(from, to ?: from) }, onDismiss, confirmEnabled = from != null) },
        picker = {
            DateRangePicker(
                state,
                // The range picker is a scrolling month list; it needs a bounded height inside a dialog.
                modifier = Modifier.fillMaxWidth().height(400.dp),
                title = null,
                headline = null,
                showModeToggle = false,
                colors = txDatePickerColors(),
            )
        },
    )
}
