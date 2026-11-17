package com.yatharth.yool.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yatharth.yool.appContainer
import com.yatharth.yool.data.healthconnect.HcAvailability
import com.yatharth.yool.data.healthconnect.HealthConnectManager
import com.yatharth.yool.sync.SyncStatus
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.components.TxChip
import com.yatharth.yool.ui.theme.Amber
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.Fmt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthConnectSection() {
    val context = LocalContext.current
    val container = context.appContainer
    val manager = container.healthConnectManager
    val engine = container.syncEngine
    val status by engine.status.collectAsStateWithLifecycle()
    val lastSync by engine.prefs.lastSyncMillis.collectAsStateWithLifecycle(initialValue = null)

    var availability by remember { mutableStateOf(manager.availability()) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun refresh() {
        availability = manager.availability()
        granted = try { manager.grantedPermissions() } catch (e: Exception) { emptySet() }
    }
    LaunchedEffect(status) { refresh() }

    val launcher = rememberLauncherForActivityResult(manager.requestPermissionContract()) { result ->
        granted = result
        if (result.any { it in manager.dataPermissions }) engine.launchAutoSync(force = true)
    }

    ShadowCard {
        SectionLabel("Health Connect")
        when (availability) {
            HcAvailability.AVAILABLE -> {
                val connected = granted.any { it in manager.dataPermissions }
                Text(
                    if (connected) "Connected. Steps, sleep, nutrition and workouts sync automatically." else "Not connected yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    manager.permissionLabels.forEach { (perm, label) ->
                        TxChip(label, selected = perm in granted, onClick = {}, color = Green)
                    }
                }
                if (granted != manager.allPermissions) {
                    TxButton(
                        if (connected) "Grant missing permissions" else "Connect Health Connect",
                        onClick = { launcher.launch(manager.allPermissions) },
                        bg = Amber,
                    )
                }
                if (connected) {
                    Text(
                        "Last sync: " + (lastSync?.let { if (System.currentTimeMillis() - it < 3_600_000) Fmt.timeAgo(it) else Fmt.dateTime(it) } ?: "never"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    when (val s = status) {
                        SyncStatus.Running -> Text("Syncing…", style = MaterialTheme.typography.labelMedium, color = Ink)
                        is SyncStatus.Error -> Text("Error: ${s.message}", style = MaterialTheme.typography.bodySmall, color = Coral)
                        is SyncStatus.Done -> Text("Synced ${s.days} days.", style = MaterialTheme.typography.labelMedium, color = Green)
                        SyncStatus.Idle -> {}
                    }
                    val running = status == SyncStatus.Running
                    TxButton("Sync now", onClick = { engine.launchRecentSync() }, enabled = !running)
                    TxButton("Re-run full backfill (1 year)", onClick = { engine.launchBackfill() }, enabled = !running)
                    if (manager.historyPermission !in granted) {
                        Text(
                            "Past-data permission is missing, so backfill only reaches back 30 days.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            HcAvailability.NEEDS_INSTALL_OR_UPDATE -> {
                Text("Health Connect needs to be installed or updated.", style = MaterialTheme.typography.bodyMedium)
                TxButton("Open Play Store", onClick = {
                    val uri = Uri.parse("market://details?id=${HealthConnectManager.HC_PACKAGE}&url=healthconnect%3A%2F%2Fonboarding")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
            HcAvailability.UNAVAILABLE -> {
                Text("Health Connect isn't available on this device.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
