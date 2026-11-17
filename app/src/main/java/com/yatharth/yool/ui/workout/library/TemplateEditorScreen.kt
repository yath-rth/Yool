package com.yatharth.yool.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.WorkoutRepository
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.TemplateFolder
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxConfirmDialog
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateEditorState(
    val loading: Boolean = true,
    val name: String = "",
    val note: String = "",
    val folderId: Long? = null,
    val exercises: List<DraftExercise> = emptyList(),
    val dirty: Boolean = false,
) {
    val canSave: Boolean get() = !loading && name.isNotBlank()
}

class TemplateEditorViewModel(private val repo: WorkoutRepository, private val templateId: Long) : ViewModel() {
    private val _state = MutableStateFlow(TemplateEditorState(loading = templateId != 0L))
    val state: StateFlow<TemplateEditorState> = _state
    private var keys = 0L
    private fun nextKey() = ++keys

    /** All exercises (incl. archived) by id, for names / types / default rest. */
    val exercises: StateFlow<Map<Long, Exercise>> = repo.observeExercises(true).map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val folders: StateFlow<List<TemplateFolder>> = repo.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (templateId != 0L) viewModelScope.launch {
            val t = repo.getTemplate(templateId)
            _state.value = if (t == null) TemplateEditorState(loading = false) else TemplateEditorState(
                loading = false, name = t.template.name, note = t.template.note ?: "", folderId = t.template.folderId,
                exercises = TemplateDraftOps.fromSpecs(com.yatharth.yool.domain.WorkoutMath.specsOf(t), ::nextKey),
            )
        }
    }

    private fun edit(f: (TemplateEditorState) -> TemplateEditorState) = _state.update { f(it).copy(dirty = true) }
    private fun editList(f: (List<DraftExercise>) -> List<DraftExercise>) = edit { it.copy(exercises = f(it.exercises)) }

    fun setName(v: String) = edit { it.copy(name = v) }
    fun setNote(v: String) = edit { it.copy(note = v) }
    fun setFolder(id: Long?) = edit { it.copy(folderId = id) }

    fun addExercises(ids: List<Long>) {
        viewModelScope.launch {
            val added = ids.map { id -> TemplateDraftOps.newExercise(id, repo.getExercise(id)?.restSec, ::nextKey) }
            editList { it + added }
        }
    }

    fun replace(index: Int, newId: Long) {
        viewModelScope.launch {
            val rest = repo.getExercise(newId)?.restSec?.toString() ?: ""
            editList { list ->
                val old = list.getOrNull(index) ?: return@editList list
                val sets = old.sets.map { DraftSet(nextKey(), type = it.type, rest = rest) }
                TemplateDraftOps.replace(list, index, newId, sets)
            }
        }
    }

    fun move(from: Int, to: Int) = editList { TemplateDraftOps.move(it, from, to) }
    fun remove(i: Int) = editList { TemplateDraftOps.remove(it, i) }
    fun toggleSuperset(i: Int) = editList { TemplateDraftOps.toggleSupersetWithNext(it, i) }
    fun addSet(i: Int) = editList { TemplateDraftOps.addSet(it, i, nextKey()) }
    fun removeSet(i: Int, j: Int) = editList { TemplateDraftOps.removeSet(it, i, j) }
    fun updateSet(i: Int, j: Int, f: (DraftSet) -> DraftSet) = editList { TemplateDraftOps.updateSet(it, i, j, f) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repo.saveTemplate(templateId, s.name, TemplateDraftOps.toSpecs(s.exercises), s.folderId, s.note)
            onDone()
        }
    }
}

private val nextType = mapOf(SetType.NORMAL to SetType.WARMUP, SetType.WARMUP to SetType.DROP, SetType.DROP to SetType.FAILURE, SetType.FAILURE to SetType.NORMAL)

private enum class Picking { ADD, REPLACE }

/** Create (templateId 0) or edit a template. Changes are only written on SAVE. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(templateId: Long, contentPadding: PaddingValues, onDone: () -> Unit) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val vm: TemplateEditorViewModel = viewModel(
        key = "template-editor-$templateId",
        factory = viewModelFactory { initializer { TemplateEditorViewModel(repo, templateId) } },
    )
    val s by vm.state.collectAsStateWithLifecycle()
    val byId by vm.exercises.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Picking?>(null) }
    var replaceIndex by remember { mutableStateOf(0) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (s.dirty) confirmDiscard = true else onDone() }
    BackHandler(onBack = leave)

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        TxTopBar(if (templateId == 0L) "New template" else "Edit template", onBack = leave)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TxTextField(s.name, vm::setName, "Name", Modifier.fillMaxWidth(), maxLength = 60, placeholder = "e.g. Push")
            TxTextField(s.note, vm::setNote, "Note", Modifier.fillMaxWidth(), singleLine = false)
            if (folders.isNotEmpty()) {
                SectionLabel("Folder")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TxChip("None", s.folderId == null, { vm.setFolder(null) })
                    folders.forEach { f -> TxChip(f.name, s.folderId == f.id, { vm.setFolder(f.id) }) }
                }
            }
            SectionLabel("Exercises")
            s.exercises.forEachIndexed { i, de ->
                val ex = byId[de.exerciseId]
                ExerciseBlock(
                    de, ex, i, s.exercises.size, vm,
                    onReplace = { replaceIndex = i; picking = Picking.REPLACE },
                )
            }
            TxButton("Add exercise", { picking = Picking.ADD }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxButton("Cancel", leave, Modifier.weight(1f))
            TxButton("Save", { vm.save(onDone) }, Modifier.weight(1f), bg = Coral, textColor = ItemsBg, enabled = s.canSave)
        }
    }

    when (picking) {
        Picking.ADD -> ExercisePickerDialog("Add exercises", true, { picking = null }) { ids -> picking = null; vm.addExercises(ids) }
        Picking.REPLACE -> ExercisePickerDialog("Replace exercise", false, { picking = null }) { ids -> picking = null; vm.replace(replaceIndex, ids.first()) }
        null -> Unit
    }
    if (confirmDiscard) {
        TxConfirmDialog("Discard changes", "You have unsaved changes to this template.", "Discard", { confirmDiscard = false; onDone() }, { confirmDiscard = false })
    }
}

@Composable
private fun ExerciseBlock(
    de: DraftExercise,
    ex: Exercise?,
    index: Int,
    count: Int,
    vm: TemplateEditorViewModel,
    onReplace: () -> Unit,
) {
    val type = ex?.type ?: ExerciseType.MACHINE
    ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex?.name ?: "Exercise", style = MaterialTheme.typography.titleSmall)
                Text(
                    type.label + if (de.supersetGroup != null) " · superset ${de.supersetGroup}" else "",
                    style = MaterialTheme.typography.labelSmall, color = if (de.supersetGroup != null) Coral else Ink,
                )
            }
            IconAction(Icons.Outlined.KeyboardArrowUp, "Move up", { vm.move(index, index - 1) }, enabled = index > 0)
            IconAction(Icons.Outlined.KeyboardArrowDown, "Move down", { vm.move(index, index + 1) }, enabled = index < count - 1)
            IconAction(Icons.Outlined.Close, "Remove exercise", { vm.remove(index) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextAction("Replace", onReplace)
            if (index < count - 1) TextAction("Superset with next", { vm.toggleSuperset(index) })
        }
        de.sets.forEachIndexed { j, st ->
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val shape = RoundedCornerShape(4.dp)
                Box(
                    Modifier.padding(bottom = 4.dp).size(34.dp).background(if (st.type == SetType.NORMAL) ItemsBg else Coral.copy(alpha = 0.15f), shape)
                        .border(1.5.dp, if (st.type == SetType.NORMAL) Ink else Coral, shape)
                        .clickable { vm.updateSet(index, j) { it.copy(type = nextType.getValue(it.type)) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(st.type.short.ifEmpty { "${j + 1}" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                }
                if (type.usesWeight) NumField(st.weight, { v -> vm.updateSet(index, j) { it.copy(weight = v) } }, "kg", Modifier.weight(1f))
                if (type.usesReps) NumField(st.reps, { v -> vm.updateSet(index, j) { it.copy(reps = v) } }, "reps", Modifier.weight(1f), integer = true)
                if (type.usesDuration) NumField(st.duration, { v -> vm.updateSet(index, j) { it.copy(duration = v) } }, "sec", Modifier.weight(1f), integer = true)
                if (type.usesDistance) NumField(st.distance, { v -> vm.updateSet(index, j) { it.copy(distance = v) } }, "km", Modifier.weight(1f))
                NumField(st.rest, { v -> vm.updateSet(index, j) { it.copy(rest = v) } }, "rest s", Modifier.weight(1f), integer = true)
                Box(Modifier.padding(bottom = 4.dp)) { IconAction(Icons.Outlined.Close, "Remove set", { vm.removeSet(index, j) }) }
            }
        }
        TxButton("Add set", { vm.addSet(index) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add)
    }
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, integer: Boolean = false) {
    TxTextField(value, { v -> onChange(if (integer) v.filter(Char::isDigit) else v.filter { it.isDigit() || it == '.' || it == ',' }) }, label, modifier, numeric = true)
}
