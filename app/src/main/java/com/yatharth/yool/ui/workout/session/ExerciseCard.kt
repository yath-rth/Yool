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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yatharth.yool.ui.components.TxButton
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
            Box {
                Icon(Icons.Default.MoreVert, "Exercise menu", tint = Ink, modifier = Modifier.clickable { menu = true }.padding(4.dp))
                DropdownMenu(menu, { menu = false }) {
                    @Composable fun item(text: String, enabled: Boolean = true, color: Color = Ink, action: () -> Unit) =
                        DropdownMenuItem(text = { Text(text, color = color) }, enabled = enabled, onClick = { menu = false; action() })
                    item(if (showNote) "Hide note" else "Add note") { showNote = !showNote }
                    item("Replace exercise") { replacing = true }
                    item("Add warm-up sets") { vm.addWarmups(ex) }
                    item("Plate calculator") { plates = true }
                    item("Move up", enabled = index > 0) { vm.moveExercise(ex.entry.id, index - 1) }
                    item("Move down", enabled = index < count - 1) { vm.moveExercise(ex.entry.id, index + 1) }
                    if (ex.entry.supersetGroup == null) item("Superset with next", enabled = hasNext) { vm.supersetWithNext(ex) }
                    else {
                        if (hasNext) item("Superset with next") { vm.supersetWithNext(ex) }
                        item("Remove from superset") { vm.removeFromSuperset(ex.entry.id) }
                    }
                    item("Remove exercise", color = Coral) { vm.removeExercise(ex.entry.id) }
                }
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plate calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close", color = Ink) } },
    )
}
