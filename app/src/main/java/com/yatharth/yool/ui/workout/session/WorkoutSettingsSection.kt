package com.yatharth.yool.ui.workout

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.reminders.ReminderNotifier
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxSwitch
import com.yatharth.yool.ui.components.TxTextField
import com.yatharth.yool.ui.workout.session.WorkoutPrefs
import com.yatharth.yool.ui.workout.session.WorkoutSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings › Workout section (rest defaults, timer sound/vibrate, keep screen on, bar weight, plates). */
@Composable
fun WorkoutSettingsSection() {
    val context = LocalContext.current
    val prefs = remember { WorkoutPrefs(context) }
    val scope = rememberCoroutineScope()
    val settings by prefs.observe().collectAsState(initial = WorkoutSettings())
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun update(transform: (WorkoutSettings) -> WorkoutSettings) {
        scope.launch { prefs.update(transform) }
    }

    ShadowCard {
        SectionLabel("Workout")
        SwitchRow("Auto-start rest timer", settings.autoStartRest) { on ->
            update { it.copy(autoStartRest = on) }
            if (on && Build.VERSION.SDK_INT >= 33 && !ReminderNotifier.hasPermission(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        SwitchRow("Timer sound", settings.timerSound) { on -> update { it.copy(timerSound = on) } }
        SwitchRow("Vibrate when rest ends", settings.vibrate) { on -> update { it.copy(vibrate = on) } }
        SwitchRow("Keep screen on during workout", settings.keepScreenOn) { on -> update { it.copy(keepScreenOn = on) } }

        DebouncedField(WorkoutMath.kg(settings.defaultRestSec.toDouble()), "Default rest", "sec", true) { t ->
            Fmt.parseInt(t)?.takeIf { it > 0 }?.let { v -> update { it.copy(defaultRestSec = v) } }
        }
        DebouncedField(WorkoutMath.kg(settings.barKg), "Bar weight", "kg", true) { t ->
            Fmt.parseDouble(t)?.takeIf { it >= 0 }?.let { v -> update { it.copy(barKg = v) } }
        }
        DebouncedField(WorkoutMath.kg(settings.stepKg), "Weight step", "kg", true) { t ->
            Fmt.parseDouble(t)?.takeIf { it > 0 }?.let { v -> update { it.copy(stepKg = v) } }
        }
        DebouncedField(WorkoutSettings.formatPlates(settings.plates), "Available plates (comma separated)", "kg", false) { t ->
            WorkoutSettings.parsePlates(t).takeIf { it.isNotEmpty() }?.let { v -> update { it.copy(plates = v) } }
        }
        Text("Rest notifications need the notification permission.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TxSwitch(checked, onChange)
    }
}

/** Text field that keeps its own text while typing and saves a moment after the last keystroke. */
@Composable
private fun DebouncedField(stored: String, label: String, unit: String, numeric: Boolean, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(stored) }
    LaunchedEffect(stored) { if (text != stored && text.isEmpty()) text = stored }
    LaunchedEffect(text) {
        if (text == stored) return@LaunchedEffect
        delay(700)
        onSave(text)
    }
    TxTextField(text, { text = it }, label, unit = unit, numeric = numeric)
}
