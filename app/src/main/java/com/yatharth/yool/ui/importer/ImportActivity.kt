package com.yatharth.yool.ui.importer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.io.strong.StrongImport
import com.yatharth.yool.io.strong.StrongWorkouts
import com.yatharth.yool.io.strong.StrongParseResult
import com.yatharth.yool.io.strong.WeightUnit
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.StatValue
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxSegmented
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.YoolTheme
import com.yatharth.yool.ui.theme.dotGridBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receives a Strong CSV (share sheet, "open with", or launched from Settings), shows a summary, imports on confirm. */
class ImportActivity : ComponentActivity() {

    private sealed interface Stage {
        data object Loading : Stage
        data class Error(val message: String) : Stage
        data class Ready(val parsed: StrongParseResult, val text: String) : Stage
        data class Done(val workouts: Int, val lifts: Int, val logged: Int, val templates: List<String>) : Stage
    }

    private var stage by mutableStateOf<Stage>(Stage.Loading)
    private var unit by mutableStateOf(WeightUnit.KG)
    private var importing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        load(intent)
        setContent {
            YoolTheme {
                Box(Modifier.fillMaxSize().dotGridBackground().safeDrawingPadding()) {
                    ImportScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stage = Stage.Loading
        load(intent)
    }

    private fun extractUri(intent: Intent): Uri? =
        if (intent.action == Intent.ACTION_SEND) IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else intent.data

    private fun load(intent: Intent) {
        lifecycleScope.launch {
            stage = try {
                val text = withContext(Dispatchers.IO) {
                    val uri = extractUri(intent)
                    when {
                        uri != null -> contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                            ?: throw StrongImport.FormatException("Couldn't open the file.")
                        intent.hasExtra(Intent.EXTRA_TEXT) -> intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                        else -> throw StrongImport.FormatException("No file was shared.")
                    }
                }
                val parsed = withContext(Dispatchers.Default) { StrongImport.parse(text) }
                if (parsed.sets.isEmpty()) Stage.Error("No importable sets found (rows need reps; warm-ups are skipped).")
                else Stage.Ready(parsed, text)
            } catch (e: StrongImport.FormatException) {
                Stage.Error(e.message ?: "Couldn't read the file.")
            } catch (e: Exception) {
                Stage.Error("Couldn't read the file: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun runImport(parsed: StrongParseResult, text: String) {
        if (importing) return
        importing = true
        lifecycleScope.launch {
            try {
                val lifts = withContext(Dispatchers.Default) { parsed.bestSets(unit) }
                val workouts = parsed.workoutValues()
                val repo = applicationContext.appContainer.repository
                repo.upsertLifts(lifts)
                repo.upsertImported(workouts)
                // Full set-by-set history into the workout log (idempotent by start time), then seed templates.
                val full = withContext(Dispatchers.Default) { StrongWorkouts.parse(text, unit) }
                val workoutRepo = applicationContext.appContainer.workoutRepository
                val logged = workoutRepo.importWorkouts(full.workouts) { name ->
                    full.exercises[name] ?: Exercise(name = name, type = ExerciseType.MACHINE, bodyPart = BodyPart.OTHER)
                }
                val templates = workoutRepo.seedTemplatesFromHistory(StrongWorkouts.SeedTemplateNames)
                stage = Stage.Done(workouts.size, lifts.size, logged, templates)
            } catch (e: Exception) {
                stage = Stage.Error("Import failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                importing = false
            }
        }
    }

    @Composable
    private fun ImportScreen() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTitle("Import from Strong")
            when (val s = stage) {
                Stage.Loading -> ShadowCard { Text("Reading file…", style = MaterialTheme.typography.bodyMedium) }

                is Stage.Error -> {
                    ShadowCard {
                        SectionLabel("Couldn't import")
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = Coral)
                    }
                    TxButton("Close", onClick = { finish() })
                }

                is Stage.Ready -> {
                    val p = s.parsed
                    ShadowCard {
                        SectionLabel("File summary")
                        StatValue("Rows", Fmt.int(p.rowCount))
                        StatValue("Workouts", Fmt.int(p.workoutDates.size))
                        StatValue("Exercises", Fmt.int(p.exercises.size))
                        p.dateRange?.let {
                            StatValue("Date range", if (it.start == it.endInclusive) it.start.toString() else "${it.start} to ${it.endInclusive}")
                        }
                    }
                    ShadowCard {
                        SectionLabel("Weight unit")
                        Text(
                            if (p.hasUnitInfo) "The file states its own unit; this is only used for rows without one."
                            else "The file doesn't say which unit it uses. Pick the one you log in Strong.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TxSegmented(WeightUnit.entries, unit, { unit = it }, label = { it.label })
                    }
                    Text(
                        "Every set goes into your workout log. Re-importing is safe: workouts already in yool are skipped, nothing is duplicated.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TxButton(if (importing) "Importing…" else "Import", onClick = { runImport(p, s.text) }, bg = Green, enabled = !importing)
                    TxButton("Cancel", onClick = { finish() })
                }

                is Stage.Done -> {
                    ShadowCard {
                        SectionLabel("Imported")
                        StatValue("Workout days", Fmt.int(s.workouts), color = Green)
                        StatValue("Best sets saved", Fmt.int(s.lifts), color = Green)
                        StatValue("New workouts in log", Fmt.int(s.logged), color = Green)
                        if (s.templates.isNotEmpty()) StatValue("Templates created", s.templates.joinToString(", "), color = Green)
                    }
                    TxButton("Done", onClick = { finish() })
                }
            }
        }
    }
}
