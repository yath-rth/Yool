package com.yatharth.yool.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.yatharth.yool.data.WorkoutRepository
import com.yatharth.yool.data.db.TemplateFolder
import com.yatharth.yool.data.db.TemplateFull
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.graphs.BarDatum
import com.yatharth.yool.ui.graphs.TimeChart
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.MetricColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorkoutHomeViewModel(val repo: WorkoutRepository) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(init: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), init)

    val templates = repo.observeTemplates().state(emptyList())
    val folders = repo.observeFolders().state(emptyList())
    val active = repo.observeActive().state(null)
    val recent = repo.observeHistory(3).state(emptyList())
    val weekly = repo.observeWorkoutsBetween(LocalDate.now().minusWeeks(9), LocalDate.now()).state(emptyList())

    fun start(templateId: Long?, then: () -> Unit) = viewModelScope.launch { repo.startWorkout(templateId); then() }
    fun rename(id: Long, name: String) = viewModelScope.launch { repo.renameTemplate(id, name) }
    fun duplicate(id: Long) = viewModelScope.launch { repo.duplicateTemplate(id) }
    fun move(id: Long, folderId: Long?) = viewModelScope.launch { repo.moveTemplateToFolder(id, folderId) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteTemplate(id) }
    fun saveFolder(f: TemplateFolder) = viewModelScope.launch { repo.saveFolder(f) }
    fun deleteFolder(id: Long) = viewModelScope.launch { repo.deleteFolder(id) }
}

private sealed interface HomeDialog {
    data class Preview(val t: TemplateFull) : HomeDialog
    data class Menu(val t: TemplateFull) : HomeDialog
    data class Rename(val t: TemplateFull) : HomeDialog
    data class MoveTo(val t: TemplateFull) : HomeDialog
    data class Delete(val t: TemplateFull) : HomeDialog
    data object NewFolder : HomeDialog
    data class RenameFolder(val f: TemplateFolder) : HomeDialog
    data class DeleteFolder(val f: TemplateFolder) : HomeDialog
}

/** Workout tab home: start empty, templates (with folders), recent history. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun WorkoutHomeScreen(
    contentPadding: PaddingValues,
    /** Called after a workout was started (or an active one exists) — opens the active workout. */
    onOpenActive: () -> Unit,
    onEditTemplate: (templateId: Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWorkout: (workoutId: Long) -> Unit,
    onOpenExercises: () -> Unit,
) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val vm: WorkoutHomeViewModel = viewModel(factory = viewModelFactory { initializer { WorkoutHomeViewModel(repo) } })
    val templates by vm.templates.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val weekly by vm.weekly.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    var collapsed by remember { mutableStateOf(setOf<Long>()) }
    val today = LocalDate.now()

    fun startFrom(templateId: Long?) { dialog = null; vm.start(templateId, onOpenActive) }

    val ordered = templates.sortedWith(compareBy({ it.template.position }, { it.template.name.lowercase() }))
    val loose = ordered.filter { t -> folders.none { it.id == t.template.folderId } }

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Workout") }
        item {
            if (active != null) TxButton("Resume workout", onOpenActive, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
            else TxButton("Start an empty workout", { startFrom(null) }, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxButton("Exercises", onOpenExercises, Modifier.weight(1f))
                TxButton("History", onOpenHistory, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Templates", Modifier.weight(1f))
                TxButton("+ Template", { onEditTemplate(0) }, Modifier.padding(end = 8.dp))
                TxButton("+ Folder", { dialog = HomeDialog.NewFolder })
            }
        }
        if (templates.isEmpty() && folders.isEmpty()) {
            item { Text("No templates yet. Create one, or save a finished workout as a template.", style = MaterialTheme.typography.bodyMedium) }
        }
        if (loose.isNotEmpty()) {
            item { Text("MY TEMPLATES", style = MaterialTheme.typography.labelSmall) }
            loose.forEach { t -> item(key = "t-${t.template.id}") { TemplateCard(t, today, { dialog = HomeDialog.Preview(t) }, { dialog = HomeDialog.Menu(t) }) } }
        }
        folders.forEach { f ->
            val inFolder = ordered.filter { it.template.folderId == f.id }
            val isCollapsed = f.id in collapsed
            item(key = "f-${f.id}") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (isCollapsed) "+ " else "- ") + f.name.uppercase() + " (${inFolder.size})",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f).clickable { collapsed = if (isCollapsed) collapsed - f.id else collapsed + f.id }.padding(vertical = 8.dp),
                    )
                    TextAction("Rename", { dialog = HomeDialog.RenameFolder(f) })
                    TextAction("Delete", { dialog = HomeDialog.DeleteFolder(f) }, color = Coral)
                }
            }
            if (!isCollapsed) inFolder.forEach { t -> item(key = "t-${t.template.id}") { TemplateCard(t, today, { dialog = HomeDialog.Preview(t) }, { dialog = HomeDialog.Menu(t) }) } }
        }

        val weeks = workoutsPerWeek(weekly.map { it.workout.date }, today, 8)
        if (weeks.any { it.second > 0 }) {
            item { SectionLabel("Workouts per week") }
            item {
                ShadowCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                    TimeChart(
                        weeks.first().first, weeks.last().first.plusDays(6), height = 120.dp,
                        bars = weeks.map { BarDatum(it.first, it.second.toDouble(), MetricColors.Workout) }, barSpanDays = 7,
                        yFormat = { v -> "%.0f".format(v) },
                    )
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionLabel("Recent") }
            recent.forEach { w -> item(key = "r-${w.workout.id}") { WorkoutCard(w, null, { onOpenWorkout(w.workout.id) }, maxLines = 3) } }
            item { TxButton("All history", onOpenHistory, Modifier.fillMaxWidth()) }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
    }

    when (val d = dialog) {
        null -> Unit
        is HomeDialog.Preview -> TxDialog({ dialog = null }) {
            Text(d.t.template.name.uppercase(), style = MaterialTheme.typography.labelLarge)
            if (d.t.template.note != null) Text(d.t.template.note, style = MaterialTheme.typography.bodyMedium)
            exerciseLines(d.t).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (active != null) Text("A workout is already in progress; START resumes it.", style = MaterialTheme.typography.labelSmall, color = Coral)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxButton("Edit", { dialog = null; onEditTemplate(d.t.template.id) }, Modifier.weight(1f))
                TxButton("Start", { startFrom(d.t.template.id) }, Modifier.weight(1f), bg = Coral, textColor = ItemsBg)
            }
        }
        is HomeDialog.Menu -> TxDialog({ dialog = null }) {
            Text(d.t.template.name.uppercase(), style = MaterialTheme.typography.labelLarge)
            TextAction("Edit", { dialog = null; onEditTemplate(d.t.template.id) })
            TextAction("Rename", { dialog = HomeDialog.Rename(d.t) })
            TextAction("Duplicate", { vm.duplicate(d.t.template.id); dialog = null })
            TextAction("Move to folder", { dialog = HomeDialog.MoveTo(d.t) })
            TextAction("Delete", { dialog = HomeDialog.Delete(d.t) }, color = Coral)
        }
        is HomeDialog.Rename -> PromptDialog("Rename template", "Name", d.t.template.name, "Rename", { vm.rename(d.t.template.id, it); dialog = null }, { dialog = null })
        is HomeDialog.MoveTo -> TxDialog({ dialog = null }) {
            Text("MOVE TO FOLDER", style = MaterialTheme.typography.labelLarge)
            TxChip("No folder", d.t.template.folderId == null, { vm.move(d.t.template.id, null); dialog = null })
            folders.forEach { f -> TxChip(f.name, d.t.template.folderId == f.id, { vm.move(d.t.template.id, f.id); dialog = null }) }
            if (folders.isEmpty()) Text("Create a folder first with + FOLDER.", style = MaterialTheme.typography.labelSmall)
        }
        is HomeDialog.Delete -> ConfirmDialog(
            "Delete template", "Delete \"${d.t.template.name}\"? Past workouts are kept.", "Delete",
            { vm.delete(d.t.template.id); dialog = null }, { dialog = null },
        )
        HomeDialog.NewFolder -> PromptDialog("New folder", "Name", "", "Create", { vm.saveFolder(TemplateFolder(name = it, position = folders.size)); dialog = null }, { dialog = null })
        is HomeDialog.RenameFolder -> PromptDialog("Rename folder", "Name", d.f.name, "Rename", { vm.saveFolder(d.f.copy(name = it)); dialog = null }, { dialog = null })
        is HomeDialog.DeleteFolder -> ConfirmDialog(
            "Delete folder", "Delete folder \"${d.f.name}\"? Its templates move to My templates.", "Delete",
            { vm.deleteFolder(d.f.id); dialog = null }, { dialog = null },
        )
    }
}

@Composable
private fun TemplateCard(t: TemplateFull, today: LocalDate, onClick: () -> Unit, onMenu: () -> Unit) {
    ShadowCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.template.name, style = MaterialTheme.typography.titleSmall)
                val lines = exerciseLines(t)
                if (lines.isEmpty()) Text("No exercises", style = MaterialTheme.typography.labelMedium)
                lines.take(5).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.85f)) }
                if (lines.size > 5) Text("+${lines.size - 5} more", style = MaterialTheme.typography.labelSmall)
                val used = t.template.lastUsedAt
                Text(
                    if (used == null) "Never used" else "Last used " + relativeDay(Instant.ofEpochMilli(used).atZone(ZoneId.systemDefault()).toLocalDate(), today),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconAction(Icons.Outlined.MoreVert, "Template menu", onMenu)
        }
    }
}
