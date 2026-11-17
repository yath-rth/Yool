package com.yatharth.yool.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yatharth.yool.ui.components.TxListGroup
import com.yatharth.yool.ui.components.TxListRow
import com.yatharth.yool.ui.components.TxTopBar
import com.yatharth.yool.ui.theme.Ink
import com.yatharth.yool.ui.workout.WorkoutSettingsSection

/** Each settings area gets its own sub-screen; the hub only lists them. */
enum class SettingsSection(val title: String, val subtitle: String, val icon: ImageVector) {
    TARGETS("Targets", "Calories, protein, steps, sleep, weight goal", Icons.Outlined.Flag),
    FOOD("Food", "My foods and saved meals", Icons.Outlined.Restaurant),
    WORKOUT("Workout", "Rest timer and session defaults", Icons.Outlined.FitnessCenter),
    HEALTH_CONNECT("Health Connect", "Sync steps, sleep and nutrition", Icons.Outlined.Sync),
    REMINDERS("Reminders", "Daily nudges to log", Icons.Outlined.Notifications),
    DATA("Data", "Backup, restore, export, Strong import", Icons.Outlined.Storage),
}

@Composable
fun SettingsScreen(contentPadding: PaddingValues, onOpenSection: (SettingsSection) -> Unit) {
    SettingsColumn(contentPadding) {
        TxTopBar("Settings")
        val groups = listOf(
            "Tracking" to listOf(SettingsSection.TARGETS, SettingsSection.FOOD, SettingsSection.WORKOUT),
            "Connections" to listOf(SettingsSection.HEALTH_CONNECT, SettingsSection.REMINDERS),
            "Your data" to listOf(SettingsSection.DATA),
        )
        groups.forEach { (title, sections) ->
            TxListGroup(title = title) {
                sections.forEachIndexed { i, s ->
                    TxListRow(s.title, onClick = { onOpenSection(s) }, subtitle = s.subtitle, icon = s.icon, divider = i < sections.lastIndex)
                }
            }
        }
        Text("yool · all data stays on this phone", style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFoodLibrary: () -> Unit,
    onOpenSavedMeals: () -> Unit,
) {
    SettingsColumn(contentPadding) {
        TxTopBar(section.title, subtitle = section.subtitle, onBack = onBack)
        when (section) {
            SettingsSection.TARGETS -> TargetsSection()
            SettingsSection.FOOD -> FoodSection(onOpenFoodLibrary, onOpenSavedMeals)
            SettingsSection.WORKOUT -> WorkoutSettingsSection()
            SettingsSection.HEALTH_CONNECT -> HealthConnectSection()
            SettingsSection.REMINDERS -> RemindersSection()
            SettingsSection.DATA -> DataSection()
        }
    }
}

@Composable
private fun SettingsColumn(contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}
