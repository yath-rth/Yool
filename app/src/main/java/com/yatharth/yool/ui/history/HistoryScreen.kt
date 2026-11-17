package com.yatharth.yool.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.ScreenTitle
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.day.moodColor
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Green
import com.yatharth.yool.ui.theme.Ink
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class HistoryViewModel(repo: TrackerRepository) : ViewModel() {
    private val today = LocalDate.now()
    val days: StateFlow<List<ResolvedDay>> = repo.observeDays(today.minusYears(5), today)
        .map { list ->
            val firstWithData = list.indexOfFirst { it.hasAnyData() }
            (if (firstWithData < 0) list.takeLast(14) else list.drop(firstWithData)).reversed()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private fun ResolvedDay.hasAnyData() =
    weightKg != null || calories != null || steps != null || sleepHours != null || mood != null || binge != null || workoutDone != null

@Composable
fun HistoryScreen(contentPadding: PaddingValues, onOpenDay: (LocalDate) -> Unit) {
    val repo = LocalContext.current.appContainer.repository
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repo) } })
    val days by vm.days.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("History", subtitle = "Tap a day to edit it") }
        items(days, key = { it.date.toEpochDay() }) { d -> DayRow(d) { onOpenDay(d.date) } }
    }
}

@Composable
private fun DayRow(d: ResolvedDay, onClick: () -> Unit) {
    ShadowCard(shadow = 3.dp, contentPadding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.date(d.date), style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        d.weightKg?.let { "${Fmt.kg(it)}kg" },
                        d.calories?.let { "${Fmt.int(it)}kcal" },
                        d.steps?.let { "${Fmt.int(it)}st" },
                        d.sleepHours?.let { "${Fmt.one(it)}h" },
                        if (d.workoutDone == true) "lift" else null,
                    ).joinToString(" · ").ifEmpty { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.copy(alpha = 0.8f),
                )
            }
            d.mood?.let { m ->
                Box(Modifier.size(28.dp).background(moodColor(m).copy(alpha = 0.3f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text("$m", style = MaterialTheme.typography.bodyMedium)
                }
            }
            d.binge?.let { b ->
                Box(Modifier.size(12.dp).background(if (b) Coral else Green, RoundedCornerShape(2.dp)))
            }
        }
    }
}
