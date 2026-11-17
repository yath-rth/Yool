package com.yatharth.yool.ui.settings

import com.yatharth.yool.ui.components.txDatePickerColors
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.core.content.FileProvider
import com.yatharth.yool.appContainer
import com.yatharth.yool.io.backup.Backup
import com.yatharth.yool.io.backup.BackupData
import com.yatharth.yool.io.backup.BackupException
import com.yatharth.yool.io.export.ExportBuilder
import com.yatharth.yool.io.export.ExportRange
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxSegmented
import com.yatharth.yool.ui.importer.ImportActivity
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Strong CSV import, export for AI/other tools, and JSON backup/restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSection() {
    val context = LocalContext.current
    val repo = context.appContainer.repository
    val foodRepo = context.appContainer.foodRepository
    val workoutRepo = context.appContainer.workoutRepository
    val scope = rememberCoroutineScope()

    var range by remember { mutableStateOf(ExportRange.WEEK) }
    var custom by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingZip by remember { mutableStateOf<ByteArray?>(null) }

    fun say(msg: String, error: Boolean = false) {
        status = msg; statusIsError = error
    }

    // ── Launchers ────────────────────────────────────────────────────────────
    val pickStrong = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.startActivity(
                Intent(context, ImportActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = pendingZip
        pendingZip = null
        if (uri != null && bytes != null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } } }
                    .onSuccess { say("Export saved.") }
                    .onFailure { say("Couldn't save: ${it.message}", true) }
            }
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                val data = BackupData(
                    repo.allEntries(), repo.allImported(), repo.allLifts(), repo.allTargets(),
                    foodRepo.allFoods(), foodRepo.allLogEntries(),
                    foodRepo.allSavedMeals().map { m -> m.meal.name to m.items.map { it.item.foodId to it.item.servings } },
                    workoutRepo.allExercises(), workoutRepo.templatesForBackup(), workoutRepo.workoutsForBackup(),
                )
                val text = withContext(Dispatchers.Default) { Backup.encode(data) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
                data
            }.onSuccess {
                say("Backed up ${it.entries.size} entries, ${it.imported.size} imported values, ${it.lifts.size} lifts, ${it.targets.size} targets, ${it.foods.size} foods, ${it.foodLog.size} food log entries, ${it.workouts.size} workouts, ${it.templates.size} templates.")
            }.onFailure { say("Backup failed: ${it.message}", true) }
            busy = false
        }
    }

    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
                val data = withContext(Dispatchers.Default) { Backup.decode(text) }
                repo.restore(data.entries, data.imported, data.lifts, data.targets, overwriteManual = false)
                foodRepo.restore(data.foods, data.foodLog, data.savedMeals)
                workoutRepo.restore(data.exercises, data.templates, data.workouts)
                data
            }.onSuccess {
                say("Restored (merged): ${it.entries.size} entries, ${it.imported.size} imported values, ${it.lifts.size} lifts, ${it.targets.size} targets, ${it.foods.size} foods, ${it.foodLog.size} food log entries, ${it.workouts.size} workouts. Typed entries and already-logged days were kept.")
            }.onFailure {
                say(if (it is BackupException) it.message ?: "Invalid backup." else "Restore failed: ${it.message}", true)
            }
            busy = false
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────
    fun resolveRange(): Pair<LocalDate, LocalDate>? {
        val today = LocalDate.now()
        return if (range == ExportRange.CUSTOM) custom else range.start(today)?.let { it to today }
    }

    fun buildExport(onReady: (ByteArray, LocalDate, LocalDate) -> Unit) {
        val r = resolveRange()
        if (r == null) {
            say("Pick a custom date range first.", true); return
        }
        scope.launch {
            busy = true
            runCatching {
                val (from, to) = r
                val days = repo.observeDays(from, to).first()
                val targets = repo.observeTargets().first()
                val lifts = repo.allLifts()
                withContext(Dispatchers.Default) { ExportBuilder.buildZip(from, to, days, lifts, targets) }
            }.onSuccess { onReady(it, r.first, r.second) }
                .onFailure { say("Export failed: ${it.message}", true) }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ShadowCard {
            SectionLabel("Backup & restore")
            Text("Save everything as one JSON file. Restore merges it in and keeps days you typed.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TxButton(
                    "Backup", enabled = !busy, icon = Icons.Default.FileDownload, modifier = Modifier.weight(1f),
                    onClick = { saveBackup.launch("yool-backup-${LocalDate.now()}.json") },
                )
                TxButton(
                    "Restore", enabled = !busy, icon = Icons.Default.Restore, modifier = Modifier.weight(1f),
                    onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
        }

        ShadowCard {
            SectionLabel("Export")
            Text("A zip with daily.csv, lifts.csv and a summary for AI or other tools.", style = MaterialTheme.typography.bodySmall)
            TxSegmented(
                ExportRange.entries, range,
                { range = it; if (it == ExportRange.CUSTOM) showPicker = true },
                label = { it.label },
            )
            if (range == ExportRange.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        custom?.let { "${Fmt.date(it.first)} to ${Fmt.date(it.second)}" } ?: "No range chosen",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TxButton("Change", onClick = { showPicker = true }, fill = false)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TxButton(
                    "Share", enabled = !busy, icon = Icons.Default.Share,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        buildExport { bytes, from, to ->
                            runCatching { shareZip(context, bytes, from, to) }.onFailure { say("Couldn't share: ${it.message}", true) }
                        }
                    },
                )
                TxButton(
                    "Save", enabled = !busy, icon = Icons.Default.Save,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        buildExport { bytes, from, to ->
                            pendingZip = bytes
                            saveZip.launch(exportName(from, to))
                        }
                    },
                )
            }
        }

        ShadowCard {
            SectionLabel("Import from Strong")
            Text("Export your workouts from Strong as CSV, then pick the file here. Safe to repeat.", style = MaterialTheme.typography.bodySmall)
            TxButton("Import Strong CSV", onClick = { pickStrong.launch(arrayOf("*/*")) }, icon = Icons.Default.FileUpload, modifier = Modifier.fillMaxWidth())
        }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (statusIsError) Coral else Ink)
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        val pickerColors = txDatePickerColors()
        DatePickerDialog(
            onDismissRequest = { showPicker = false; if (custom == null) range = ExportRange.WEEK },
            confirmButton = {
                TxButton(
                    "OK", fill = false,
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val s = state.selectedStartDateMillis
                        val e = state.selectedEndDateMillis
                        if (s != null && e != null) custom = millisToDate(s) to millisToDate(e)
                        showPicker = false
                    },
                )
            },
            dismissButton = {
                TxButton("Cancel", fill = false, onClick = { showPicker = false; if (custom == null) range = ExportRange.WEEK })
            },
            colors = pickerColors,
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f, fill = false), colors = pickerColors)
        }
    }
}

private fun millisToDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

private fun exportName(from: LocalDate, to: LocalDate) = "yool-export-$from-to-$to.zip"

private fun shareZip(context: Context, bytes: ByteArray, from: LocalDate, to: LocalDate) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, exportName(from, to)).apply { writeBytes(bytes) }
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "yool export $from to $to")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
