package com.yatharth.yool.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.domain.WorkoutMath
import com.yatharth.yool.ui.Fmt
import com.yatharth.yool.ui.components.EmptyState
import com.yatharth.yool.ui.components.SectionLabel
import com.yatharth.yool.ui.components.ShadowCard
import com.yatharth.yool.ui.components.TxButton
import com.yatharth.yool.ui.theme.Coral
import com.yatharth.yool.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PAGE = 40

data class HistoryItem(val workout: WorkoutFull, val prs: Int)

data class HistoryState(val items: List<HistoryItem>, val total: Int)

class WorkoutHistoryViewModel(repo: WorkoutRepository) : ViewModel() {
    private val shown = MutableStateFlow(PAGE)

    /** Newest first. PR counts are computed over the whole history, but only [shown] items are exposed. */
    private val all = repo.observeHistory(100_000)
        .map { list -> val pr = prCounts(list); list.sortedByDescending { it.workout.startedAt }.map { HistoryItem(it, pr[it.workout.id] ?: 0) } }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<HistoryState?> = combine(all, shown) { l, n -> HistoryState(l.take(n), l.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadMore() { shown.value += PAGE }
}

internal fun workoutTime(w: WorkoutFull): String = Fmt.time(w.workout.startedAt)

/** Card used by history and the home "Recent" list. */
@Composable
internal fun WorkoutCard(w: WorkoutFull, prs: Int?, onClick: () -> Unit, maxLines: Int = 6) {
    ShadowCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
        Text(w.workout.name, style = MaterialTheme.typography.titleSmall)
        Text("${Fmt.date(w.workout.date)} · ${workoutTime(w)}", style = MaterialTheme.typography.labelMedium)
        val ended = w.workout.endedAt
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ended != null) Text(WorkoutMath.formatDuration(ended - w.workout.startedAt), style = MaterialTheme.typography.labelMedium)
            Text("${WorkoutMath.kg(Math.round(WorkoutMath.volume(w)).toDouble())} kg", style = MaterialTheme.typography.labelMedium)
            if (prs != null && prs > 0) Text("$prs PR", style = MaterialTheme.typography.labelMedium, color = Coral)
        }
        val lines = exerciseLines(w)
        lines.take(maxLines).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.85f)) }
        if (lines.size > maxLines) Text("+${lines.size - maxLines} more", style = MaterialTheme.typography.labelSmall)
    }
}

/** Finished workouts, newest first, grouped by month. */
@Composable
fun WorkoutHistoryScreen(contentPadding: PaddingValues, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val repo = LocalContext.current.appContainer.workoutRepository
    val vm: WorkoutHistoryViewModel = viewModel(factory = viewModelFactory { initializer { WorkoutHistoryViewModel(repo) } })
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
        LibBackTitle("History", onBack, subtitle = state?.let { "${it.total} workouts" })
        val st = state
        if (st != null && st.total == 0) {
            EmptyState("No workouts yet", "Finished workouts will be listed here.")
        } else if (st != null) {
            val groups = groupByMonth(st.items) { it.workout.workout.date }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g ->
                    item(key = "m-${g.month}") { SectionLabel(monthLabel(g.month)) }
                    items(g.items, key = { it.workout.workout.id }) { h ->
                        WorkoutCard(h.workout, h.prs, onClick = { onOpenWorkout(h.workout.workout.id) })
                    }
                }
                if (st.items.size < st.total) {
                    item(key = "more") { TxButton("Load more", vm::loadMore, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}
