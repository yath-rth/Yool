package com.yatharth.yool.ui.settings

import com.yatharth.yool.ui.components.txTimePickerColors
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yatharth.yool.reminders.ReminderNotifier
import com.yatharth.yool.reminders.ReminderPrefs
import com.yatharth.yool.reminders.ReminderScheduler
import com.yatharth.yool.reminders.ReminderSetting
import com.yatharth.yool.reminders.ReminderType
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxDialog
import com.yatharth.yool.ui.components.TxDialogButtons
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxSwitch
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.theme.ItemsBg
import com.yatharth.yool.ui.theme.SecondaryBg
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

    TxListGroup {
        val types = ReminderType.entries
        types.forEachIndexed { index, type ->
            val s = settings.getValue(type)
            TxListRow(
                title = type.label,
                onClick = { editing = type },
                subtitle = when {
                    type == ReminderType.WEIGHT_FOLLOW_UP -> "Only if weight is still missing"
                    type.isFollowUp -> "Only if check-in isn't done"
                    else -> null
                },
                value = s.time.format(TimeFmt),
                chevron = false,
                divider = index < types.lastIndex,
                trailing = {
                    TxSwitch(s.enabled, { on ->
                        apply(type, enabled = on)
                        if (on && !permitted && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                },
            )
        }
    }

    ShadowCard {
        SectionLabel("Notifications")
        Text(
            if (permitted) "Allowed" else "Blocked: reminders will not show",
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
        TxDialog(type.label, onDismiss = { editing = null }) {
            TimePicker(state, colors = txTimePickerColors())
            TxDialogButtons(
                confirmLabel = "OK",
                onConfirm = {
                    apply(type, time = LocalTime.of(state.hour, state.minute))
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
}

