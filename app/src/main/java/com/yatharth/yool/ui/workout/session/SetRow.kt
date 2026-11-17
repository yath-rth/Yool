package com.yatharth.yool.ui.workout.session

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yatharth.yool.data.db.WorkoutExerciseFull
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Amber
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.delay

private val FieldShape = RoundedCornerShape(5.dp)
private val Warmup = Color(0xFFB8862B)
private val Drop = Color(0xFF6E87A6)

/** Small bordered numeric input used in set rows. Saves on focus loss and after a short typing pause. */
@Composable
internal fun SetField(
    text: String,
    onText: (String) -> Unit,
    hint: String,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    val commit by rememberUpdatedState(onCommit)
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        delay(800)
        commit()
    }
    Box(
        modifier.height(40.dp).clip(FieldShape).background(ItemsBg, FieldShape).border(1.5.dp, Ink, FieldShape),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty() && hint.isNotEmpty()) {
            Text(hint, style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center), color = Ink.copy(alpha = 0.35f), maxLines = 1)
        }
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center, color = Ink),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .onFocusChanged {
                    if (focused && !it.isFocused) commit()
                    focused = it.isFocused
                },
        )
    }
}

private fun num(v: Double?) = v?.let { WorkoutMath.kg(it) } ?: ""

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SetRow(
    vm: ActiveWorkoutViewModel,
    ex: WorkoutExerciseFull,
    set: WorkoutSet,
    label: String,
    prevValues: SetValues?,
    settings: WorkoutSettings,
    editing: Boolean,
) {
    val type = ex.exercise.type
    val setNow by rememberUpdatedState(set)

    var w by remember(set.id) { mutableStateOf(num(set.weightKg)) }
    var r by remember(set.id) { mutableStateOf(set.reps?.toString() ?: "") }
    var t by remember(set.id) { mutableStateOf(set.durationSec?.let { SessionMath.durationText(it) } ?: "") }
    var d by remember(set.id) { mutableStateOf(num(set.distanceM?.div(1000))) }
    // External changes (copy from previous, fill on check) overwrite the text only when it really differs.
    LaunchedEffect(set.weightKg) { if (Fmt.parseDouble(w) != set.weightKg) w = num(set.weightKg) }
    LaunchedEffect(set.reps) { if (Fmt.parseInt(r) != set.reps) r = set.reps?.toString() ?: "" }
    LaunchedEffect(set.durationSec) { if (SessionMath.parseDuration(t) != set.durationSec) t = set.durationSec?.let { SessionMath.durationText(it) } ?: "" }
    LaunchedEffect(set.distanceM) { if (Fmt.parseDouble(d)?.times(1000) != set.distanceM) d = num(set.distanceM?.div(1000)) }

    fun typed() = SetValues(Fmt.parseDouble(w), Fmt.parseInt(r), SessionMath.parseDuration(t), Fmt.parseDouble(d)?.times(1000))
    fun commitW() { val p = Fmt.parseDouble(w); if (p != setNow.weightKg) vm.patchSet(set.id) { it.copy(weightKg = p) } }
    fun commitR() { val p = Fmt.parseInt(r); if (p != setNow.reps) vm.patchSet(set.id) { it.copy(reps = p) } }
    fun commitT() { val p = SessionMath.parseDuration(t); if (p != setNow.durationSec) vm.patchSet(set.id) { it.copy(durationSec = p) } }
    fun commitD() { val p = Fmt.parseDouble(d)?.times(1000); if (p != setNow.distanceM) vm.patchSet(set.id) { it.copy(distanceM = p) } }

    var typeMenu by remember { mutableStateOf(false) }
    var optMenu by remember { mutableStateOf(false) }
    var rpeDialog by remember { mutableStateOf(false) }
    var restDialog by remember { mutableStateOf(false) }

    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) {
            vm.deleteSet(set.id)
            true
        } else false
    })

    Column {
        SwipeToDismissBox(
            state = swipe,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(Modifier.fillMaxSize().background(Coral, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    Text("DELETE", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (set.completed) Green.copy(alpha = 0.22f) else ItemsBg, RoundedCornerShape(4.dp))
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.width(32.dp).height(40.dp).combinedClickable(onClick = { typeMenu = true }, onLongClick = { optMenu = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = when (set.type) {
                            SetType.WARMUP -> Warmup
                            SetType.DROP -> Drop
                            SetType.FAILURE -> Coral
                            SetType.NORMAL -> Ink
                        },
                    )
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        SetType.entries.forEach { st ->
                            DropdownMenuItem(
                                text = { Text(st.label) },
                                onClick = { typeMenu = false; vm.patchSet(set.id) { it.copy(type = st) } },
                            )
                        }
                    }
                    DropdownMenu(optMenu, { optMenu = false }) {
                        DropdownMenuItem(text = { Text("RPE") }, onClick = { optMenu = false; rpeDialog = true })
                        if (!editing) DropdownMenuItem(text = { Text("Rest time") }, onClick = { optMenu = false; restDialog = true })
                        DropdownMenuItem(text = { Text("Delete set", color = Coral) }, onClick = { optMenu = false; vm.deleteSet(set.id) })
                    }
                }

                Text(
                    prevValues?.let { SessionMath.valuesLabel(type, it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = prevValues != null) {
                            val p = prevValues ?: return@clickable
                            val filled = SessionMath.forComplete(SetValues(), p, type)
                            vm.patchSet(set.id) { it.copy(weightKg = filled.weightKg, reps = filled.reps, durationSec = filled.durationSec, distanceM = filled.distanceM) }
                        },
                )

                val fieldW = Modifier.width(60.dp)
                if (type.usesWeight) SetField(w, { w = it }, num(prevValues?.weightKg), ::commitW, fieldW)
                if (type.usesReps) SetField(r, { r = it }, prevValues?.reps?.toString() ?: "", ::commitR, fieldW, KeyboardType.Number)
                if (type.usesDuration) SetField(t, { t = it }, prevValues?.durationSec?.let { SessionMath.durationText(it) } ?: "", ::commitT, Modifier.width(68.dp), KeyboardType.Text)
                if (type.usesDistance) SetField(d, { d = it }, num(prevValues?.distanceM?.div(1000)), ::commitD, fieldW)

                Box(
                    Modifier
                        .size(36.dp)
                        .clip(FieldShape)
                        .background(if (set.completed) Green else ItemsBg, FieldShape)
                        .border(1.5.dp, Ink, FieldShape)
                        .clickable { vm.toggleComplete(setNow, ex, typed(), prevValues) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, "Complete set", tint = if (set.completed) Color.White else Ink.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
                }
            }
        }

        // Rest chip between this set and the next (active workouts), plus RPE if set.
        if (!editing || set.rpe != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (set.rpe != null) {
                    Text(
                        "RPE ${SessionMath.rpeText(set.rpe)}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { rpeDialog = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (!editing) {
                    val sec = WorkoutMath.restFor(set.restSec, ex.exercise.restSec, settings.defaultRestSec)
                    Text(
                        "REST ${WorkoutMath.formatClock(sec)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (set.restSec != null) FontWeight.ExtraBold else FontWeight.ExtraLight,
                        modifier = Modifier.clickable { restDialog = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    if (rpeDialog) {
        AlertDialog(
            onDismissRequest = { rpeDialog = false },
            title = { Text("RPE") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionMath.RpeOptions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { v ->
                                TxChip(SessionMath.rpeText(v), set.rpe == v, { rpeDialog = false; vm.patchSet(set.id) { it.copy(rpe = v) } })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ rpeDialog = false; vm.patchSet(set.id) { it.copy(rpe = null) } }) { Text("Clear", color = Ink) } },
            dismissButton = { TextButton({ rpeDialog = false }) { Text("Cancel", color = Ink) } },
        )
    }

    if (restDialog) {
        var text by remember { mutableStateOf(set.restSec?.let { SessionMath.durationText(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { restDialog = false },
            title = { Text("Rest after this set") },
            text = {
                TxTextField(
                    text, { text = it }, "Rest (m:ss or seconds)",
                    placeholder = WorkoutMath.formatClock(WorkoutMath.restFor(null, ex.exercise.restSec, settings.defaultRestSec)),
                )
            },
            confirmButton = {
                TextButton({
                    restDialog = false
                    val sec = SessionMath.parseDuration(text)?.takeIf { it > 0 }
                    vm.patchSet(set.id) { it.copy(restSec = sec) }
                }) { Text("Save", color = Ink) }
            },
            dismissButton = {
                TextButton({ restDialog = false; vm.patchSet(set.id) { it.copy(restSec = null) } }) { Text("Use default", color = Ink) }
            },
        )
    }
}
