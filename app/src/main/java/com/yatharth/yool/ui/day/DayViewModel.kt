package com.yatharth.yool.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.Streaks
import com.yatharth.yool.domain.TargetHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate,
    val day: ResolvedDay,
    /** What the user typed (null fields = not overridden). */
    val entry: DailyEntry?,
    val targets: TargetHistory,
    val bingeFreeStreak: Int,
    val lifts: List<LiftBestSet>,
    val loaded: Boolean,
)

class DayViewModel(private val repo: TrackerRepository, val date: LocalDate) : ViewModel() {

    val state: StateFlow<DayUiState> = combine(
        repo.observeDay(date),
        repo.observeEntry(date),
        repo.observeTargets(),
        repo.observeDays(date.minusDays(400), date),
        repo.observeLifts(date, date),
    ) { day, entry, targets, history, lifts ->
        DayUiState(date, day, entry, targets, Streaks.bingeFree(history, date), lifts, loaded = true)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayUiState(date, ResolvedDay(date), null, TargetHistory.Empty, 0, emptyList(), loaded = false),
    )

    fun update(transform: (DailyEntry) -> DailyEntry) {
        viewModelScope.launch { repo.updateEntry(date, transform) }
    }
}
