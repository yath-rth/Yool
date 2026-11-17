package com.yatharth.yool.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.DuplicateExerciseException
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Background
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.launch

/**
 * Full-screen dialog to pick exercises (search, body-part filter, create new inline).
 * [multiple] allows picking several (adding to a workout/template); otherwise one (replace).
 * Exercises are listed most-performed first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExercisePickerDialog(
    title: String,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onPicked: (exerciseIds: List<Long>) -> Unit,
) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val scope = rememberCoroutineScope()
    val all by remember { repo.observeExercises(false) }.collectAsStateWithLifecycle(emptyList())
    val counts by remember { repo.observeExerciseUseCounts() }.collectAsStateWithLifecycle(emptyMap())
    var query by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf<BodyPart?>(null) }
    val selected = remember { mutableStateListOf<Long>() }
    var creating by remember { mutableStateOf(false) }
    val visible = filterExercises(all, query, bodyPart, null)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Background).systemBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(title, Modifier.weight(1f))
                Icon(Icons.Outlined.Close, "Close", Modifier.clickable(onClick = onDismiss).padding(6.dp))
            }
            TxTextField(query, { query = it }, "Search", Modifier.fillMaxWidth(), placeholder = "Exercise name")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TxChip("All", bodyPart == null, { bodyPart = null })
                BodyPart.entries.forEach { bp -> TxChip(bp.label, bodyPart == bp, { bodyPart = if (bodyPart == bp) null else bp }) }
            }
            TxButton("+ New exercise", { creating = true }, Modifier.fillMaxWidth())
            if (visible.isEmpty()) EmptyState("No matches", "Create it with + NEW EXERCISE.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { e ->
                    val isSel = e.id in selected
                    val shape = RoundedCornerShape(4.dp)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (isSel) Coral.copy(alpha = 0.12f) else ItemsBg, shape)
                            .border(if (isSel) 2.dp else 1.5.dp, if (isSel) Coral else Ink, shape)
                            .clickable {
                                if (!multiple) onPicked(listOf(e.id))
                                else if (isSel) selected.remove(e.id) else selected.add(e.id)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall)
                            Text("${e.bodyPart.label} · ${timesLabel(counts[e.id] ?: 0)}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (isSel) Icon(Icons.Outlined.Check, "Selected", Modifier.size(20.dp), tint = Coral)
                    }
                }
            }
            if (multiple) {
                TxButton(
                    if (selected.isEmpty()) "Add" else "Add (${selected.size})",
                    { onPicked(selected.toList()) },
                    Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg, enabled = selected.isNotEmpty(),
                )
            }
        }
    }

    if (creating) {
        QuickExerciseDialog(
            initialName = query.trim(),
            onDismiss = { creating = false },
            onCreate = { e ->
                // Returns an error message or null on success.
                try {
                    val id = repo.saveExercise(e)
                    creating = false
                    if (multiple) { if (id !in selected) selected.add(id); query = "" } else onPicked(listOf(id))
                    null
                } catch (ex: DuplicateExerciseException) {
                    ex.message
                }
            },
            scope = { block -> scope.launch { block() } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickExerciseDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onCreate: suspend (Exercise) -> String?,
    scope: (suspend () -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(ExerciseType.BARBELL) }
    var bodyPart by remember { mutableStateOf(BodyPart.CHEST) }
    var error by remember { mutableStateOf<String?>(null) }
    TxDialog(onDismiss) {
        Text("NEW EXERCISE", style = MaterialTheme.typography.labelLarge)
        TxTextField(name, { name = it; error = null }, "Name", Modifier.fillMaxWidth(), maxLength = 60)
        if (error != null) Text(error!!, style = MaterialTheme.typography.labelMedium, color = Coral)
        SectionLabel("Type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExerciseType.entries.forEach { t -> TxChip(t.label, type == t, { type = t }) }
        }
        SectionLabel("Body part")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BodyPart.entries.forEach { b -> TxChip(b.label, bodyPart == b, { bodyPart = b }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("Cancel", onDismiss, Modifier.weight(1f))
            TxButton(
                "Create",
                { scope { error = onCreate(Exercise(name = name.trim(), type = type, bodyPart = bodyPart)) } },
                Modifier.weight(1f), bg = Coral, textColor = ItemsBg, enabled = name.isNotBlank(),
            )
        }
    }
}
