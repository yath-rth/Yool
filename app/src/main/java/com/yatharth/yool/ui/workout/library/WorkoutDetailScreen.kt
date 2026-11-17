package com.yatharth.yool.ui.workout

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.domain.PrKind
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.domain.WorkoutSummary
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import kotlinx.coroutines.launch

private fun prLabel(kinds: Set<PrKind>): String = kinds.joinToString(" ") {
    when (it) { PrKind.WEIGHT -> "Weight PR"; PrKind.ONE_RM -> "1RM PR"; PrKind.VOLUME -> "Volume PR" }
}

/** One finished workout: sets, volume, PRs; edit / delete / save as template / start again. */
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: (workoutId: Long) -> Unit,
    onOpenActive: () -> Unit,
    onOpenExercise: (exerciseId: Long) -> Unit,
) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val scope = rememberCoroutineScope()
    val w by remember(workoutId) { repo.observeWorkout(workoutId) }.collectAsStateWithLifecycle(null)
    var summary by remember { mutableStateOf<WorkoutSummary?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(w) { if (w != null) summary = repo.summary(workoutId) }

    val workout = w
    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        LibBackTitle(workout?.workout?.name ?: "Workout", onBack, subtitle = workout?.let { "${Fmt.day(it.workout.date)} · ${workoutTime(it)}" })
        if (workout == null) return@Column
        val prs = summary?.prs.orEmpty().associate { (it.first to it.second) to it.third }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadowCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    val ended = workout.workout.endedAt
                    StatValue("Duration", if (ended != null) WorkoutMath.formatDuration(ended - workout.workout.startedAt) else "-", Modifier.weight(1f))
                    StatValue("Volume", WorkoutMath.kg(Math.round(WorkoutMath.volume(workout)).toDouble()), Modifier.weight(1f), unit = "kg")
                }
                Row(Modifier.fillMaxWidth()) {
                    StatValue("Sets", WorkoutMath.completedSets(workout).toString(), Modifier.weight(1f))
                    StatValue("PRs", (summary?.prs?.size ?: 0).toString(), Modifier.weight(1f), color = if ((summary?.prs?.size ?: 0) > 0) Coral else Ink)
                }
                if (workout.workout.note != null) Text(workout.workout.note, style = MaterialTheme.typography.bodyMedium)
            }
            workout.ordered.forEach { ex ->
                ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    Text(
                        ex.exercise.name + if (ex.entry.supersetGroup != null) "  (superset ${ex.entry.supersetGroup})" else "",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.clickable { onOpenExercise(ex.exercise.id) },
                    )
                    Text("${ex.exercise.bodyPart.label} · ${ex.exercise.type.label}", style = MaterialTheme.typography.labelSmall)
                    if (ex.entry.note != null) Text(ex.entry.note, style = MaterialTheme.typography.bodyMedium)
                    ex.orderedSets.forEachIndexed { i, s ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.type.short.ifEmpty { "${i + 1}" }, style = MaterialTheme.typography.labelMedium, color = if (s.type == SetType.NORMAL) Ink else Coral)
                            Text(setText(ex.exercise.type, s.weightKg, s.reps, s.durationSec, s.distanceM), style = MaterialTheme.typography.bodyMedium)
                            if (s.rpe != null) Text("RPE ${WorkoutMath.kg(s.rpe)}", style = MaterialTheme.typography.labelSmall)
                            prs[ex.exercise.name to s.position]?.let { Text(prLabel(it), style = MaterialTheme.typography.labelSmall, color = Coral) }
                        }
                    }
                }
            }
            SectionLabel("Actions")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxButton("Edit", { onEdit(workoutId) }, Modifier.weight(1f))
                TxButton("Start again", { scope.launch { repo.repeatWorkout(workoutId); onOpenActive() } }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxButton("Save as template", { savingTemplate = true }, Modifier.weight(1f))
                TxButton("Delete", { confirmDelete = true }, Modifier.weight(1f), bg = Coral, textColor = com.yatharth.yool.ui.theme.ItemsBg)
            }
            if (savedMessage != null) Text(savedMessage!!, style = MaterialTheme.typography.labelMedium)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "Delete workout", "Delete this workout and all its sets? This can't be undone.", "Delete",
            onConfirm = { confirmDelete = false; scope.launch { repo.deleteWorkout(workoutId); onBack() } },
            onDismiss = { confirmDelete = false },
        )
    }
    if (savingTemplate) {
        PromptDialog(
            "Save as template", "Template name", workout?.workout?.name ?: "", "Save",
            onConfirm = { name -> savingTemplate = false; scope.launch { repo.saveWorkoutAsTemplate(workoutId, name); savedMessage = "Saved template \"$name\"." } },
            onDismiss = { savingTemplate = false },
        )
    }
}
