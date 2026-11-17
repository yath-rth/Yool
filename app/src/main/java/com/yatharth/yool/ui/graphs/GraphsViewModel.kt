package com.yatharth.yool.ui.graphs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.domain.RangePreset
import com.yatharth.yool.domain.Ranges
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.Streaks
import com.yatharth.yool.domain.TargetHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class GraphMetric(val label: String) {
    WEIGHT("Weight"), CALORIES("Calories"), PROTEIN("Protein"), STEPS("Steps"), SLEEP("Sleep"),
    MOOD("Mood"), BINGE("Binge"), WORKOUTS("Workouts"), LIFTS("Lifts"),
}

data class GraphData(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<ResolvedDay>,
    val targets: TargetHistory,
    val lifts: List<LiftBestSet>,
)

/** Resolves a selection to concrete dates; ALL looks up the earliest data date. Shared by Graphs and Insights. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<RangeSelection>.resolved(repo: TrackerRepository, today: LocalDate): Flow<Pair<LocalDate, LocalDate>> =
    flatMapLatest { sel ->
        flow {
            val earliest = if (sel.preset == RangePreset.ALL) repo.firstDate() else null
            emit(Ranges.resolve(sel.preset, today, earliest, sel.customFrom, sel.customTo))
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
class GraphsViewModel(private val repo: TrackerRepository) : ViewModel() {
    private val today = LocalDate.now()

    val selection = MutableStateFlow(RangeSelection(RangePreset.M1))
    val metric = MutableStateFlow(GraphMetric.WEIGHT)
    val exercise = MutableStateFlow<String?>(null)

    val exercises: StateFlow<List<String>> = repo.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val data: StateFlow<GraphData?> = selection.resolved(repo, today)
        .flatMapLatest { (from, to) ->
            combine(repo.observeDays(from, to), repo.observeTargets(), repo.observeLifts(from, to)) { days, targets, lifts ->
                GraphData(from, to, days, targets, lifts)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current binge-free streak, looking back far enough that short ranges don't truncate it. */
    val currentStreak: StateFlow<Int> = repo.observeDays(today.minusDays(400), today)
        .map { Streaks.bingeFree(it, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
