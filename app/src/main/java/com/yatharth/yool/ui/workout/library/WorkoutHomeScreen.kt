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
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxActionSheet
import com.yatharth.yool.ui.components.TxConfirmDialog
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxIconButton
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxPromptDialog
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.domain.WorkoutMath
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.time.DayOfWeek
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
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
    data object AddMenu : HomeDialog
    data object NewFolder : HomeDialog
    data class FolderMenu(val f: TemplateFolder) : HomeDialog
    data class RenameFolder(val f: TemplateFolder) : HomeDialog
    data class DeleteFolder(val f: TemplateFolder) : HomeDialog
}

/** Workout tab home: weekly hero + start, templates (with folders), recent history, library links. */
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
    onOpenMeasurements: () -> Unit = {},
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
    val weekStart = today.with(DayOfWeek.MONDAY)
    val thisWeek = weekly.count { it.workout.endedAt != null && !it.workout.date.isBefore(weekStart) && !it.workout.date.isAfter(weekStart.plusDays(6)) }
    val lastDate = recent.firstOrNull()?.workout?.date

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TxTopBar("Workout") }
        item {
            ShadowCard(Modifier.fillMaxWidth()) {
                StatValue("This week", if (thisWeek == 1) "1 workout" else "$thisWeek workouts", big = true)
                Text(
                    if (lastDate != null) "Last workout: ${Fmt.date(lastDate)}" else "No workouts yet",
                    style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f),
                )
                if (active != null) TxButton("Resume workout", onOpenActive, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
                else TxButton("Start empty workout", { startFrom(null) }, Modifier.fillMaxWidth(), bg = Coral, textColor = ItemsBg)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Templates", Modifier.weight(1f))
                TxIconButton(Icons.Outlined.Add, "Add template or folder", { dialog = HomeDialog.AddMenu })
            }
        }
        if (templates.isEmpty() && folders.isEmpty()) {
            item { EmptyState("No templates yet", "Tap + to create one, or save a finished workout as a template.") }
        }
        loose.forEach { t -> item(key = "t-${t.template.id}") { TemplateCard(t, { dialog = HomeDialog.Preview(t) }, { dialog = HomeDialog.Menu(t) }) } }
        folders.forEach { f ->
            val inFolder = ordered.filter { it.template.folderId == f.id }
            val isCollapsed = f.id in collapsed
            item(key = "f-${f.id}") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clickable { collapsed = if (isCollapsed) collapsed - f.id else collapsed + f.id }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(if (isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown, if (isCollapsed) "Expand" else "Collapse")
                        Text(f.name.uppercase() + " (${inFolder.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                    }
                    IconAction(Icons.Outlined.MoreVert, "Folder menu", { dialog = HomeDialog.FolderMenu(f) })
                }
            }
            if (!isCollapsed) inFolder.forEach { t -> item(key = "t-${t.template.id}") { TemplateCard(t, { dialog = HomeDialog.Preview(t) }, { dialog = HomeDialog.Menu(t) }) } }
        }

        if (recent.isNotEmpty()) {
            item {
                TxListGroup(title = "Recent") {
                    recent.take(3).forEachIndexed { i, w ->
                        val ended = w.workout.endedAt
                        val stats = listOfNotNull(
                            ended?.let { WorkoutMath.formatDuration(it - w.workout.startedAt) },
                            "${WorkoutMath.kg(Math.round(WorkoutMath.volume(w)).toDouble())} kg",
                        ).joinToString(" · ")
                        TxListRow(
                            w.workout.name, { onOpenWorkout(w.workout.id) },
                            subtitle = "${Fmt.date(w.workout.date)} · $stats",
                            divider = i < minOf(recent.size, 3) - 1,
                        )
                    }
                }
            }
        }
        item {
            TxListGroup(title = "Library") {
                TxListRow("All history", onOpenHistory)
                TxListRow("Exercises", onOpenExercises)
                TxListRow("Measurements", onOpenMeasurements, divider = false)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    when (val d = dialog) {
        null -> Unit
        is HomeDialog.Preview -> TxDialog(d.t.template.name, { dialog = null }) {
            if (d.t.template.note != null) Text(d.t.template.note, style = MaterialTheme.typography.bodyMedium)
            exerciseLines(d.t).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (active != null) Text("A workout is already in progress; START resumes it.", style = MaterialTheme.typography.labelSmall, color = Coral)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxButton("Edit", { dialog = null; onEditTemplate(d.t.template.id) }, Modifier.weight(1f))
                TxButton("Start", { startFrom(d.t.template.id) }, Modifier.weight(1f), bg = Coral, textColor = ItemsBg)
            }
        }
        HomeDialog.AddMenu -> TxActionSheet(
            null, { dialog = null },
            listOf(
                "New template" to { dialog = null; onEditTemplate(0) },
                "New folder" to { dialog = HomeDialog.NewFolder },
            ),
        )
        is HomeDialog.Menu -> TxActionSheet(
            d.t.template.name, { dialog = null },
            listOf(
                "Edit" to { dialog = null; onEditTemplate(d.t.template.id) },
                "Rename" to { dialog = HomeDialog.Rename(d.t) },
                "Duplicate" to { vm.duplicate(d.t.template.id); dialog = null },
                "Move to folder" to { dialog = HomeDialog.MoveTo(d.t) },
                "Delete" to { dialog = HomeDialog.Delete(d.t) },
            ),
            destructive = setOf("Delete"),
        )
        is HomeDialog.FolderMenu -> TxActionSheet(
            d.f.name, { dialog = null },
            listOf(
                "Rename" to { dialog = HomeDialog.RenameFolder(d.f) },
                "Delete" to { dialog = HomeDialog.DeleteFolder(d.f) },
            ),
            destructive = setOf("Delete"),
        )
        is HomeDialog.Rename -> TxPromptDialog("Rename template", "Name", d.t.template.name, "Rename", { vm.rename(d.t.template.id, it); dialog = null }, { dialog = null })
        is HomeDialog.MoveTo -> TxDialog("Move to folder", { dialog = null }) {
            TxChip("No folder", d.t.template.folderId == null, { vm.move(d.t.template.id, null); dialog = null })
            folders.forEach { f -> TxChip(f.name, d.t.template.folderId == f.id, { vm.move(d.t.template.id, f.id); dialog = null }) }
            if (folders.isEmpty()) Text("Create a folder first.", style = MaterialTheme.typography.labelSmall)
        }
        is HomeDialog.Delete -> TxConfirmDialog(
            "Delete template", "Delete \"${d.t.template.name}\"? Past workouts are kept.", "Delete",
            { vm.delete(d.t.template.id); dialog = null }, { dialog = null },
        )
        HomeDialog.NewFolder -> TxPromptDialog("New folder", "Name", "", "Create", { vm.saveFolder(TemplateFolder(name = it, position = folders.size)); dialog = null }, { dialog = null })
        is HomeDialog.RenameFolder -> TxPromptDialog("Rename folder", "Name", d.f.name, "Rename", { vm.saveFolder(d.f.copy(name = it)); dialog = null }, { dialog = null })
        is HomeDialog.DeleteFolder -> TxConfirmDialog(
            "Delete folder", "Delete folder \"${d.f.name}\"? Its templates move out of the folder.", "Delete",
            { vm.deleteFolder(d.f.id); dialog = null }, { dialog = null },
        )
    }
}

@Composable
private fun TemplateCard(t: TemplateFull, onClick: () -> Unit, onMenu: () -> Unit) {
    ShadowCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.template.name, style = MaterialTheme.typography.titleSmall)
                val names = t.ordered.map { it.exercise.name }
                Text(
                    if (names.isEmpty()) "No exercises" else summaryLine(names),
                    style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val used = t.template.lastUsedAt
                Text(
                    if (used == null) "Never used" else "Last used " + Fmt.date(used),
                    style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f),
                )
            }
            IconAction(Icons.Outlined.MoreVert, "Template menu", onMenu)
        }
    }
}
