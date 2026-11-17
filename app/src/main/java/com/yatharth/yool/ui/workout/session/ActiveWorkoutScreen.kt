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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxProgress
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
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
import java.time.format.DateTimeFormatter

private val TimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

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

    val w = workout
    if (w == null) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) { EmptyState("Loading", "Opening workout…") }
    } else {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            if (!editing && rest != null) {
                RestBar(rest!!.label, { rest!!.remainingSec(it) }, { rest!!.progress(it) }, ctx)
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { Header(vm, w, editing, onEditTimes = { editTimes = true }, onFinish = { if (editing) onClose() else vm.requestFinish() }) }
                val list = w.ordered
                items(list, key = { it.entry.id }) { ex ->
                    val i = list.indexOf(ex)
                    ExerciseCard(
                        vm, ex, i, list.size, hasNext = i < list.lastIndex,
                        previousRows = previous[ex.exercise.id].orEmpty(),
                        settings = settings, editing = editing, onOpenExercise = onOpenExercise,
                    )
                }
                item(key = "add") { TxButton("+ Add exercises", onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) }
                if (!editing) item(key = "discard") {
                    TxButton("Discard workout", onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth(), textColor = Coral)
                }
            }
        }
    }

    if (adding) {
        ExercisePickerDialog("Add exercises", true, { adding = false }) { ids ->
            adding = false
            vm.addExercises(ids)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("Everything logged in this workout will be deleted.") },
            confirmButton = { TextButton({ confirmDiscard = false; vm.discard(onClose) }) { Text("Discard", color = Coral) } },
            dismissButton = { TextButton({ confirmDiscard = false }) { Text("Keep", color = Ink) } },
        )
    }

    if (editTimes && w != null) EditTimesDialog(w, onDismiss = { editTimes = false }) { start, end -> editTimes = false; vm.setTimes(start, end) }

    notice?.let {
        AlertDialog(
            onDismissRequest = { vm.notice.value = null },
            text = { Text(it) },
            confirmButton = { TextButton({ vm.notice.value = null }) { Text("OK", color = Ink) } },
        )
    }

    when (val s = stage) {
        null -> {}
        FinishStage.ChooseIncomplete -> AlertDialog(
            onDismissRequest = vm::dismissFinish,
            title = { Text("Unfinished sets") },
            text = { Text("Some sets aren't checked off yet.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton({ vm.finish(dropIncomplete = false) }) { Text("Complete all", color = Ink) }
                    TextButton({ vm.finish(dropIncomplete = true) }) { Text("Discard unchecked", color = Coral) }
                    TextButton(vm::dismissFinish) { Text("Cancel", color = Ink) }
                }
            },
        )
        is FinishStage.Summary -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Workout complete") },
            text = { SummaryBody(s.summary) },
            confirmButton = {
                TextButton({
                    val t = s.templateId
                    if (t != null) vm.askUpdateTemplate(t, s.templateName ?: "template") else onClose()
                }) { Text("Done", color = Ink) }
            },
        )
        is FinishStage.UpdateTemplate -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Update template '${s.templateName}'?") },
            text = { Text("You changed the exercises or sets compared with the template.") },
            confirmButton = { TextButton({ vm.updateTemplate(s.templateId, onClose) }) { Text("Update", color = Ink) } },
            dismissButton = { TextButton(onClose) { Text("Keep as is", color = Ink) } },
        )
    }
}

@Composable
private fun Header(vm: ActiveWorkoutViewModel, w: WorkoutFull, editing: Boolean, onEditTimes: () -> Unit, onFinish: () -> Unit) {
    var name by remember(w.workout.id) { mutableStateOf(w.workout.name) }
    LaunchedEffect(name) {
        if (name == w.workout.name || name.isBlank()) return@LaunchedEffect
        delay(800)
        vm.setName(name)
    }
    ShadowCard {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TxTextField(name, { name = it }, "Workout", modifier = Modifier.weight(1f), maxLength = 60)
            TxButton(if (editing) "Save" else "Finish", onClick = onFinish, bg = Green, textColor = androidx.compose.ui.graphics.Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editing) {
                val end = w.workout.endedAt
                val ms = (end ?: System.currentTimeMillis()) - w.workout.startedAt
                StatValue("Duration", WorkoutMath.formatDuration(ms), Modifier.weight(1f))
                TxButton("Edit time", onClick = onEditTimes)
            } else {
                val now by rememberNow()
                StatValue("Elapsed", SessionMath.elapsedClock(now - w.workout.startedAt), Modifier.weight(1f), big = true)
            }
        }
        WorkoutNoteField(w.workout.note ?: "", "Note") { vm.setNote(it) }
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
            TxButton("−15", { RestTimer.adjust(ctx, -15) })
            TxButton("+15", { RestTimer.adjust(ctx, 15) })
            TxButton("Skip", { RestTimer.stop(ctx) }, bg = Coral, textColor = androidx.compose.ui.graphics.Color.White)
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

@Composable
private fun EditTimesDialog(w: WorkoutFull, onDismiss: () -> Unit, onSave: (startedAt: Long, endedAt: Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    var start by remember { mutableStateOf(LocalDateTime.ofInstant(Instant.ofEpochMilli(w.workout.startedAt), zone).format(TimeFmt)) }
    var minutes by remember { mutableStateOf((((w.workout.endedAt ?: System.currentTimeMillis()) - w.workout.startedAt) / 60_000).toString()) }
    val startMs = runCatching { LocalDateTime.parse(start.trim(), TimeFmt).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    val mins = minutes.trim().toLongOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TxTextField(start, { start = it }, "Start (yyyy-MM-dd HH:mm)")
                TxTextField(minutes, { minutes = it }, "Duration", unit = "min", numeric = true)
            }
        },
        confirmButton = {
            TextButton({ if (startMs != null && mins != null) onSave(startMs, startMs + mins * 60_000) }, enabled = startMs != null && mins != null) {
                Text("Save", color = Ink)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Ink) } },
    )
}
