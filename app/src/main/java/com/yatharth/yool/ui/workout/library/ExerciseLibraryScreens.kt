package com.yatharth.yool.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.DuplicateExerciseException
import com.yatharth.yool.data.WorkoutRepository
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxSwitch
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Library ──────────────────────────────────────────────────────────────────

data class LibraryRow(val exercise: Exercise, val count: Int)

class ExerciseLibraryViewModel(private val repo: WorkoutRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val bodyPart = MutableStateFlow<BodyPart?>(null)
    val type = MutableStateFlow<ExerciseType?>(null)
    val showArchived = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<LibraryRow>?> = showArchived.flatMapLatest { archived ->
        combine(repo.observeExercises(archived), repo.observeExerciseUseCounts(), query, bodyPart, type) { all, counts, q, bp, t ->
            filterExercises(all, q, bp, t).map { LibraryRow(it, counts[it.id] ?: 0) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseLibraryScreen(contentPadding: PaddingValues, onBack: () -> Unit, onOpenExercise: (Long) -> Unit, onNewExercise: () -> Unit) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val vm: ExerciseLibraryViewModel = viewModel(factory = viewModelFactory { initializer { ExerciseLibraryViewModel(repo) } })
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val bodyPart by vm.bodyPart.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val showArchived by vm.showArchived.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LibBackTitle("Exercises", onBack, subtitle = rows?.let { "${it.size} shown" }) {
            TxButton("+ New", onNewExercise, Modifier.padding(start = 8.dp), fill = false)
        }
        TxTextField(query, { vm.query.value = it }, "Search", Modifier.fillMaxWidth(), placeholder = "Exercise name")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TxChip("All", bodyPart == null, { vm.bodyPart.value = null })
            BodyPart.entries.forEach { bp -> TxChip(bp.label, bodyPart == bp, { vm.bodyPart.value = if (bodyPart == bp) null else bp }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TxChip("Any type", type == null, { vm.type.value = null }, color = Ink)
            ExerciseType.entries.forEach { t -> TxChip(t.label, type == t, { vm.type.value = if (type == t) null else t }, color = Ink) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxSwitch(showArchived, { vm.showArchived.value = it })
            Text("SHOW ARCHIVED", style = MaterialTheme.typography.labelMedium)
        }
        val list = rows
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState("No exercises", "Nothing matches. Create one with + NEW.")
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.exercise.id }) { r ->
                    ShadowCard(Modifier.fillMaxWidth(), onClick = { onOpenExercise(r.exercise.id) }, contentPadding = 12.dp) {
                        Text(r.exercise.name + if (r.exercise.archived) "  (archived)" else "", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${r.exercise.bodyPart.label} · ${r.exercise.type.label} · ${timesLabel(r.count)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

// ── Editor ───────────────────────────────────────────────────────────────────

data class ExerciseEditorState(
    val loading: Boolean = true,
    val name: String = "",
    val type: ExerciseType = ExerciseType.BARBELL,
    val bodyPart: BodyPart = BodyPart.CHEST,
    val rest: String = "",
    val note: String = "",
    val archived: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = !loading && name.isNotBlank()
}

class ExerciseEditorViewModel(private val repo: WorkoutRepository, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow(ExerciseEditorState(loading = id != 0L))
    val state: StateFlow<ExerciseEditorState> = _state
    private var original: Exercise? = null

    init {
        if (id != 0L) viewModelScope.launch {
            val e = repo.getExercise(id)
            original = e
            _state.value = if (e == null) ExerciseEditorState(loading = false) else ExerciseEditorState(
                loading = false, name = e.name, type = e.type, bodyPart = e.bodyPart, rest = e.restSec?.toString() ?: "",
                note = e.note ?: "", archived = e.archived,
            )
        }
    }

    fun edit(f: (ExerciseEditorState) -> ExerciseEditorState) = _state.update { f(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val base = original ?: Exercise(name = s.name, type = s.type, bodyPart = s.bodyPart)
            try {
                repo.saveExercise(
                    base.copy(
                        name = s.name, type = s.type, bodyPart = s.bodyPart, restSec = Fmt.parseInt(s.rest)?.takeIf { it > 0 },
                        note = s.note.ifBlank { null }, archived = s.archived,
                    ),
                )
                onDone()
            } catch (e: DuplicateExerciseException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun remove(onDone: () -> Unit) = viewModelScope.launch { repo.removeExercise(id); onDone() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(exerciseId: Long, contentPadding: PaddingValues, onDone: () -> Unit) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val vm: ExerciseEditorViewModel = viewModel(
        key = "exercise-editor-$exerciseId",
        factory = viewModelFactory { initializer { ExerciseEditorViewModel(repo, exerciseId) } },
    )
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        LibBackTitle(if (exerciseId == 0L) "New exercise" else "Edit exercise", onDone)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TxTextField(s.name, { v -> vm.edit { it.copy(name = v) } }, "Name", Modifier.fillMaxWidth(), maxLength = 60, placeholder = "e.g. Incline Dumbbell Press")
            if (s.error != null) Text(s.error!!, style = MaterialTheme.typography.labelMedium, color = Coral)
            SectionLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExerciseType.entries.forEach { t -> TxChip(t.label, s.type == t, { vm.edit { it.copy(type = t) } }) }
            }
            SectionLabel("Body part")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BodyPart.entries.forEach { b -> TxChip(b.label, s.bodyPart == b, { vm.edit { it.copy(bodyPart = b) } }) }
            }
            TxTextField(
                s.rest, { v -> vm.edit { it.copy(rest = v.filter(Char::isDigit)) } }, "Default rest", Modifier.fillMaxWidth(),
                unit = "sec", numeric = true, placeholder = "Blank = global default",
            )
            TxTextField(s.note, { v -> vm.edit { it.copy(note = v) } }, "Sticky note", Modifier.fillMaxWidth(), singleLine = false, placeholder = "Shown every time (seat height, grip...)")
            if (exerciseId != 0L) {
                SectionLabel("Manage")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxButton(if (s.archived) "Unarchive" else "Archive", { vm.edit { it.copy(archived = !it.archived) } }, Modifier.weight(1f))
                    TxButton("Delete", { confirmDelete = true }, Modifier.weight(1f), bg = Coral, textColor = ItemsBg)
                }
                Text(
                    if (s.archived) "Archived exercises are hidden from pickers. Press SAVE to apply." else "Exercises with history are archived instead of deleted.",
                    style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("Cancel", onDone, Modifier.weight(1f))
            TxButton("Save", { vm.save(onDone) }, Modifier.weight(1f), bg = Coral, textColor = ItemsBg, enabled = s.canSave)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete exercise", "Delete \"${s.name}\"? If it appears in any workout or template it will be archived instead.",
            "Delete", onConfirm = { confirmDelete = false; vm.remove(onDone) }, onDismiss = { confirmDelete = false },
        )
    }
}
