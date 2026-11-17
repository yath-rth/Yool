package com.yatharth.yool.ui.workout

import com.yatharth.yool.ui.components.txTimePickerColors
import com.yatharth.yool.ui.components.txDatePickerColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.SecondaryBg
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.domain.WorkoutSummary
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxActionSheet
import com.yatharth.yool.ui.components.TxConfirmDialog
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxDialogButtons
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxPromptDialog
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.components.TxProgress
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.workout.session.ActiveWorkoutViewModel
import com.yatharth.yool.ui.workout.session.ExerciseCard
import com.yatharth.yool.ui.workout.session.FinishStage
import com.yatharth.yool.ui.workout.session.RestTimer
import com.yatharth.yool.ui.workout.session.SessionMath
import com.yatharth.yool.ui.workout.session.SetValues
import com.yatharth.yool.ui.workout.session.WorkoutNoteField
import com.yatharth.yool.ui.workout.session.WorkoutPrefs
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.LocalTime
import java.time.ZoneOffset

/** Wall-clock millis that refresh every [intervalMs]; keeps ticking out of the big composables. */
@Composable
internal fun rememberNow(intervalMs: Long = 1000): State<Long> = produceState(System.currentTimeMillis(), intervalMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(intervalMs)
    }
}

/**
 * The active workout (workoutId null) or a finished workout being edited (no timers, "Save" instead of
 * "Finish").
 */
@Composable
fun ActiveWorkoutScreen(workoutId: Long?, contentPadding: PaddingValues, onClose: () -> Unit, onOpenExercise: (Long) -> Unit) {
    val ctx = LocalContext.current
    val container = ctx.appContainer
    val vm: ActiveWorkoutViewModel = viewModel(
        key = "workout_${workoutId ?: "active"}",
        factory = viewModelFactory {
            initializer { ActiveWorkoutViewModel(workoutId, container.workoutRepository, WorkoutPrefs(ctx), ctx.applicationContext) }
        },
    )
    val workout by vm.workout.collectAsState()
    val settings by vm.settings.collectAsState()
    val previous by vm.previous.collectAsState()
    val stage by vm.finishStage.collectAsState()
    val notice by vm.notice.collectAsState()
    val rest by RestTimer.state(ctx).collectAsState()
    val editing = vm.editing

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var adding by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var editTimes by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }

    val w = workout
    if (w == null) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) { EmptyState("Loading", "Opening workout…") }
    } else {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Header(
                w, editing,
                onMenu = { menu = true },
                onFinish = { if (editing) onClose() else vm.requestFinish() },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (!editing && rest != null) {
                RestBar(rest!!.label, { rest!!.remainingSec(it) }, { rest!!.progress(it) }, ctx)
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val list = w.ordered
                items(list, key = { it.entry.id }) { ex ->
                    val i = list.indexOf(ex)
                    ExerciseCard(
                        vm, ex, i, list.size, hasNext = i < list.lastIndex,
                        previousRows = previous[ex.exercise.id].orEmpty(),
                        settings = settings, editing = editing, onOpenExercise = onOpenExercise,
                    )
                }
                item(key = "add") { TxButton("Add exercises", onClick = { adding = true }, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.Add) }
            }
        }
    }

    if (menu && w != null) {
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Rename workout" to { menu = false; renaming = true })
            add((if (w.workout.note.isNullOrEmpty()) "Add note" else "Edit note") to { menu = false; noting = true })
            if (editing) add("Edit time" to { menu = false; editTimes = true })
            if (!editing) add("Discard workout" to { menu = false; confirmDiscard = true })
        }
        TxActionSheet(w.workout.name, { menu = false }, actions, destructive = setOf("Discard workout"))
    }
    if (renaming && w != null) {
        TxPromptDialog("Rename workout", "Workout", w.workout.name, "Save", { renaming = false; vm.setName(it) }, { renaming = false })
    }
    if (noting && w != null) {
        TxDialog("Workout note", { noting = false }) {
            WorkoutNoteField(w.workout.note ?: "", "Note") { vm.setNote(it) }
            TxButton("Done", { noting = false }, Modifier.fillMaxWidth())
        }
    }

    if (adding) {
        ExercisePickerDialog("Add exercises", true, { adding = false }) { ids ->
            adding = false
            vm.addExercises(ids)
        }
    }

    if (confirmDiscard) {
        TxConfirmDialog(
            "Discard workout?", "Everything logged in this workout will be deleted.", "Discard",
            onConfirm = { confirmDiscard = false; vm.discard(onClose) },
            onDismiss = { confirmDiscard = false },
            dismissLabel = "Keep",
        )
    }

    if (editTimes && w != null) EditTimesDialog(w, onDismiss = { editTimes = false }) { start, end -> editTimes = false; vm.setTimes(start, end) }

    notice?.let {
        TxDialog(null, { vm.notice.value = null }) {
            Text(it, style = MaterialTheme.typography.bodyMedium)
            TxButton("OK", { vm.notice.value = null }, Modifier.fillMaxWidth())
        }
    }

    when (val s = stage) {
        null -> {}
        FinishStage.ChooseIncomplete -> TxDialog("Unfinished sets", vm::dismissFinish) {
            Text("Some sets aren't checked off yet.", style = MaterialTheme.typography.bodyMedium)
            TxButton("Complete all", { vm.finish(dropIncomplete = false) }, Modifier.fillMaxWidth(), bg = Ink, textColor = ItemsBg)
            TxButton("Discard unchecked", { vm.finish(dropIncomplete = true) }, Modifier.fillMaxWidth(), textColor = Coral)
            TxButton("Cancel", vm::dismissFinish, Modifier.fillMaxWidth())
        }
        is FinishStage.Summary -> TxDialog("Workout complete", {}) {
            SummaryBody(s.summary)
            TxButton(
                "Done",
                {
                    val t = s.templateId
                    if (t != null) vm.askUpdateTemplate(t, s.templateName ?: "template") else onClose()
                },
                Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg,
            )
        }
        is FinishStage.UpdateTemplate -> TxDialog("Update template '${s.templateName}'?", {}) {
            Text("You changed the exercises or sets compared with the template.", style = MaterialTheme.typography.bodyMedium)
            TxDialogButtons("Update", { vm.updateTemplate(s.templateId, onClose) }, onClose, dismissLabel = "Keep as is")
        }
    }
}

@Composable
private fun Header(w: WorkoutFull, editing: Boolean, onMenu: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val now by rememberNow()
    val subtitle = if (editing) {
        "Duration " + WorkoutMath.formatDuration((w.workout.endedAt ?: now) - w.workout.startedAt)
    } else {
        "Elapsed " + SessionMath.elapsedClock(now - w.workout.startedAt)
    }
    TxTopBar(w.workout.name, modifier, subtitle = subtitle) {
        TxIconButton(Icons.Outlined.MoreVert, "Workout menu", onMenu)
        TxButton(if (editing) "Save" else "Finish", onClick = onFinish, bg = Coral, textColor = ItemsBg, fill = false)
    }
}

@Composable
private fun RestBar(label: String, remaining: (Long) -> Int, progress: (Long) -> Float, ctx: android.content.Context) {
    val now by rememberNow(250)
    ShadowCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), contentPadding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("REST · $label".uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(WorkoutMath.formatClock(remaining(now)), style = MaterialTheme.typography.titleMedium)
            }
            TxButton("−15", { RestTimer.adjust(ctx, -15) }, fill = false)
            TxButton("+15", { RestTimer.adjust(ctx, 15) }, fill = false)
            TxButton("Skip", { RestTimer.stop(ctx) }, textColor = Coral, fill = false)
        }
        TxProgress(progress(now))
    }
}

@Composable
private fun SummaryBody(s: WorkoutSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatValue("Duration", WorkoutMath.formatDuration(s.durationMs))
            StatValue("Volume", WorkoutMath.kg(s.volumeKg), unit = "kg")
            StatValue("Sets", s.completedSets.toString())
        }
        if (s.prs.isNotEmpty()) {
            SectionLabel("Personal records")
            s.prs.forEach { (exercise, pos, kinds) ->
                Text("🏆 $exercise · set ${pos + 1}: ${kinds.joinToString { it.label }}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTimesDialog(w: WorkoutFull, onDismiss: () -> Unit, onSave: (startedAt: Long, endedAt: Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = LocalDateTime.ofInstant(Instant.ofEpochMilli(w.workout.startedAt), zone)
    var day by remember { mutableStateOf(initial.toLocalDate()) }
    var time by remember { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    var minutes by remember { mutableStateOf((((w.workout.endedAt ?: System.currentTimeMillis()) - w.workout.startedAt) / 60_000).toString()) }
    var picking by remember { mutableStateOf<String?>(null) }
    val startMs = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val mins = minutes.trim().toLongOrNull()?.takeIf { it > 0 }
    TxDialog("Edit time", onDismiss) {
        SectionLabel("Started")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxChip(Fmt.date(day), selected = true, onClick = { picking = "date" })
            TxChip(Fmt.time(time), selected = true, onClick = { picking = "time" })
        }
        TxTextField(minutes, { minutes = it }, "Duration", unit = "min", numeric = true)
        TxDialogButtons("Save", { if (mins != null) onSave(startMs, startMs + mins * 60_000) }, onDismiss, confirmEnabled = mins != null)
    }
    when (picking) {
        "date" -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TxButton("OK", {
                        state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        picking = null
                    }, fill = false)
                },
                dismissButton = { TxButton("Cancel", { picking = null }, fill = false) },
                colors = txDatePickerColors(),
            ) {
                DatePicker(
                    state,
                    colors = txDatePickerColors(),
                )
            }
        }
        "time" -> {
            val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
            TxDialog("Start time", { picking = null }) {
                TimePicker(
                    state,
                    colors = txTimePickerColors(),
                )
                TxDialogButtons("OK", { time = LocalTime.of(state.hour, state.minute); picking = null }, { picking = null })
            }
        }
    }
}
