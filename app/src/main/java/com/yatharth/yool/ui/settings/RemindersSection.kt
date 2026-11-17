package com.yatharth.yool.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yatharth.yool.reminders.ReminderNotifier
import com.yatharth.yool.reminders.ReminderPrefs
import com.yatharth.yool.reminders.ReminderScheduler
import com.yatharth.yool.reminders.ReminderSetting
import com.yatharth.yool.reminders.ReminderType
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.components.TxSwitch
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFmt = DateTimeFormatter.ofPattern("hh:mm a")

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun RemindersSection() {
    val context = LocalContext.current
    val prefs = remember { ReminderPrefs(context) }
    val scope = rememberCoroutineScope()
    val settings by prefs.observe().collectAsState(initial = ReminderType.entries.associateWith { ReminderSetting(true, it.defaultTime) })
    var permitted by remember { mutableStateOf(ReminderNotifier.hasPermission(context)) }
    var editing by remember { mutableStateOf<ReminderType?>(null) }
    var askedBefore by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
    }
    LifecycleResumeEffect(Unit) {
        permitted = ReminderNotifier.hasPermission(context)
        onPauseOrDispose { }
    }
    // Ask once on first view.
    LaunchedEffect(Unit) {
        if (!permitted && Build.VERSION.SDK_INT >= 33) {
            askedBefore = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun apply(type: ReminderType, enabled: Boolean? = null, time: LocalTime? = null) {
        scope.launch {
            enabled?.let { prefs.setEnabled(type, it) }
            time?.let { prefs.setTime(type, it) }
            ReminderScheduler.schedule(context, type, prefs.current().getValue(type))
        }
    }

    ShadowCard {
        SectionLabel("Reminders")
        ReminderType.entries.forEach { type ->
            val s = settings.getValue(type)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(type.label, style = MaterialTheme.typography.bodyLarge)
                    if (type.isFollowUp) {
                        Text(
                            if (type == ReminderType.WEIGHT_FOLLOW_UP) "Only if weight is still missing" else "Only if check-in isn't done",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TxChip(s.time.format(TimeFmt), selected = s.enabled, onClick = { editing = type })
                TxSwitch(s.enabled, { on ->
                    apply(type, enabled = on)
                    if (on && !permitted && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
        }

        Text(
            if (permitted) "Notifications allowed" else "Notifications blocked — reminders will not show",
            style = MaterialTheme.typography.labelMedium,
            color = if (permitted) Green else Coral,
        )
        if (!permitted) {
            TxButton("Allow notifications", onClick = {
                // After a denial Android stops showing the prompt, so fall back to app settings.
                if (Build.VERSION.SDK_INT >= 33 && !askedBefore) {
                    askedBefore = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }, Modifier.fillMaxWidth())
        }
        TxButton("Send test notification", onClick = { ReminderNotifier.showTest(context) }, modifier = Modifier.fillMaxWidth(), enabled = permitted)
    }

    editing?.let { type ->
        val t = settings.getValue(type).time
        val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(type.label) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    apply(type, time = LocalTime.of(state.hour, state.minute))
                    editing = null
                }) { Text("OK", color = Ink) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = Ink) } },
        )
    }
}
