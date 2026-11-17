package com.yatharth.yool.ui.workout.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yatharth.yool.data.db.ExerciseSetRow
import com.yatharth.yool.data.db.WorkoutExerciseFull
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxActionSheet
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Amber
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.workout.ExercisePickerDialog
import kotlinx.coroutines.delay

private val SupersetColors = listOf(Coral, Color(0xFF6E87A6), Color(0xFF8D7AB8), Color(0xFF708A36), Color(0xFFC4905B))

/** Colour and letter for a superset group number. */
internal fun supersetStyle(group: Int): Pair<Color, String> =
    SupersetColors[(group - 1).mod(SupersetColors.size)] to ('A' + (group - 1).mod(26)).toString()

@Composable
internal fun ExerciseCard(
    vm: ActiveWorkoutViewModel,
    ex: WorkoutExerciseFull,
    index: Int,
    count: Int,
    hasNext: Boolean,
    previousRows: List<ExerciseSetRow>,
    settings: WorkoutSettings,
    editing: Boolean,
    onOpenExercise: (Long) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    var plates by remember { mutableStateOf(false) }
    var showNote by remember(ex.entry.id) { mutableStateOf(!ex.entry.note.isNullOrEmpty()) }
    val sets = ex.orderedSets
    val labels = SessionMath.setLabels(sets.map { it.type })
    val types = sets.map { it.type }

    ShadowCard(contentPadding = 12.dp) {
        ex.entry.supersetGroup?.let { g ->
            val (color, letter) = supersetStyle(g)
            Text("SUPERSET $letter", style = MaterialTheme.typography.labelMedium, color = color)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ex.exercise.name,
                style = MaterialTheme.typography.titleSmall,
                color = ex.entry.supersetGroup?.let { supersetStyle(it).first } ?: Ink,
                modifier = Modifier.weight(1f).clickable { onOpenExercise(ex.exercise.id) },
            )
            TxIconButton(Icons.Default.MoreVert, "Exercise menu", { menu = true }, size = 32.dp)
        }

        ex.exercise.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = Ink.copy(alpha = 0.75f))
        }
        if (showNote) WorkoutNoteField(ex.entry.note ?: "", "Note for this workout") { vm.setExerciseNote(ex.entry.id, it) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val h = MaterialTheme.typography.labelSmall
            Text("SET", style = h, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
            Text("PREVIOUS", style = h, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            val type = ex.exercise.type
            if (type.usesWeight) Text("KG", style = h, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
            if (type.usesReps) Text("REPS", style = h, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
            if (type.usesDuration) Text("TIME", style = h, textAlign = TextAlign.Center, modifier = Modifier.width(68.dp))
            if (type.usesDistance) Text("KM", style = h, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
            Box(Modifier.width(36.dp))
        }

        sets.forEachIndexed { i, set ->
            key(set.id) {
                val prev = WorkoutMath.previousFor(i, types, previousRows)?.let { SetValues.of(it) }
                SetRow(vm, ex, set, labels[i], prev, settings, editing)
            }
        }
        TxButton("+ Add set", onClick = { vm.addSet(ex.entry.id) }, modifier = Modifier.fillMaxWidth())
    }

    if (menu) {
        val actions = buildList<Pair<String, () -> Unit>> {
            add((if (showNote) "Hide note" else "Add note") to { menu = false; showNote = !showNote })
            add("Replace exercise" to { menu = false; replacing = true })
            add("Add warm-up sets" to { menu = false; vm.addWarmups(ex) })
            add("Plate calculator" to { menu = false; plates = true })
            if (index > 0) add("Move up" to { menu = false; vm.moveExercise(ex.entry.id, index - 1) })
            if (index < count - 1) add("Move down" to { menu = false; vm.moveExercise(ex.entry.id, index + 1) })
            if (hasNext) add("Superset with next" to { menu = false; vm.supersetWithNext(ex) })
            if (ex.entry.supersetGroup != null) add("Remove from superset" to { menu = false; vm.removeFromSuperset(ex.entry.id) })
            add("Remove exercise" to { menu = false; vm.removeExercise(ex.entry.id) })
        }
        TxActionSheet(ex.exercise.name, { menu = false }, actions, destructive = setOf("Remove exercise"))
    }
    if (replacing) {
        ExercisePickerDialog(
            title = "Replace ${ex.exercise.name}",
            multiple = false,
            onDismiss = { replacing = false },
            onPicked = { ids ->
                replacing = false
                ids.firstOrNull()?.let { vm.replaceExercise(ex.entry.id, it) }
            },
        )
    }
    if (plates) PlateDialog(ex.sets.let { SessionMath.heaviestWorking(it) }, settings) { plates = false }
}

/** Multi-line-ish note field that saves after a short pause. */
@Composable
internal fun WorkoutNoteField(initial: String, label: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    LaunchedEffect(text) {
        if (text == initial) return@LaunchedEffect
        delay(800)
        onSave(text)
    }
    TxTextField(text, { text = it }, label, singleLine = false)
}

@Composable
internal fun PlateDialog(initialKg: Double?, settings: WorkoutSettings, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialKg?.let { WorkoutMath.kg(it) } ?: "") }
    val target = Fmt.parseDouble(text)
    TxDialog("Plate calculator", onDismiss) {
        TxTextField(text, { text = it }, "Target weight", unit = "kg", numeric = true)
        Text("Bar ${WorkoutMath.kg(settings.barKg)} kg", style = MaterialTheme.typography.labelMedium)
        if (target != null) {
            if (target <= settings.barKg) {
                Text("Just the bar.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val (plates, left) = WorkoutMath.platesPerSide(target, settings.barKg, settings.plates)
                Text("Per side", style = MaterialTheme.typography.labelMedium)
                Text(
                    plates.groupingBy { it }.eachCount().entries.joinToString("  ") { (p, n) -> "${WorkoutMath.kg(p)} × $n" }.ifEmpty { "—" },
                    style = MaterialTheme.typography.titleSmall,
                )
                if (left > 0) Text("${WorkoutMath.kg(left)} kg per side can't be matched with your plates.", style = MaterialTheme.typography.bodySmall, color = Coral)
            }
        }
        TxButton("Close", onDismiss, Modifier.fillMaxWidth())
    }
}
