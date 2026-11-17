package com.yatharth.yool.data

import androidx.room.withTransaction
import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.db.YoolDatabase
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.domain.DayResolver
import com.yatharth.yool.domain.FoodMath
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.TargetHistory
import com.yatharth.yool.domain.WorkoutMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Single entry point for reading/writing tracker data. */
class TrackerRepository(private val db: YoolDatabase) {
    private val entries = db.dailyEntryDao()
    private val imported = db.importedValueDao()
    private val lifts = db.liftDao()
    private val targets = db.targetDao()
    private val food = db.foodDao()
    private val workouts = db.workoutDao()

    // ── Resolved days ────────────────────────────────────────────────────────

    /**
     * Food-diary totals and finished workouts join imported values as sources FOOD_LOG / WORKOUT_LOG
     * (ranked above MFP/Strong/HC, below typed).
     */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<ResolvedDay>> =
        combine(
            entries.observeRange(from, to), imported.observeRange(from, to), food.observeTotals(from, to), workouts.observeDayCounts(from, to),
        ) { e, i, f, w ->
            DayResolver.resolveRange(from, to, e, i + FoodMath.asImported(f) + WorkoutMath.asImported(w))
        }

    fun observeDay(date: LocalDate): Flow<ResolvedDay> =
        observeDays(date, date).map { it.first() }

    /** Raw manual entry (for editors that need to know what was typed vs imported). */
    fun observeEntry(date: LocalDate): Flow<DailyEntry?> = entries.observe(date)

    /** Read-modify-write the manual entry for [date]. */
    suspend fun updateEntry(date: LocalDate, transform: (DailyEntry) -> DailyEntry) {
        db.withTransaction {
            val current = entries.get(date) ?: DailyEntry(date = date)
            entries.upsert(transform(current).copy(date = date, updatedAt = System.currentTimeMillis()))
        }
    }

    // ── Imports ──────────────────────────────────────────────────────────────

    suspend fun upsertImported(values: List<ImportedValue>) {
        if (values.isNotEmpty()) imported.upsertAll(values)
    }

    /** Replace all values of one source+metric in a date range (used by syncs so deletions upstream propagate). */
    suspend fun replaceImported(source: Source, metric: Metric, from: LocalDate, to: LocalDate, values: List<ImportedValue>) {
        db.withTransaction {
            imported.deleteRange(source, metric, from, to)
            if (values.isNotEmpty()) imported.upsertAll(values)
        }
    }

    // ── Lifts ────────────────────────────────────────────────────────────────

    /** Best set per day per exercise: the workout log, with imported Strong best sets filling other days. */
    fun observeLifts(from: LocalDate, to: LocalDate): Flow<List<LiftBestSet>> =
        combine(lifts.observeRange(from, to), workouts.observeWorkingSets(from, to)) { imported, logged ->
            WorkoutMath.mergeLifts(imported, WorkoutMath.bestPerDay(logged))
        }

    /** Exercise names with lift data, most frequently trained first. */
    fun observeExercises(): Flow<List<String>> =
        combine(workouts.observeLoggedExerciseNames(), lifts.observeExercises()) { logged, imported ->
            (logged + imported).distinctBy { it.lowercase() }
        }
    suspend fun upsertLifts(sets: List<LiftBestSet>) {
        if (sets.isNotEmpty()) lifts.upsertAll(sets)
    }

    // ── Targets ──────────────────────────────────────────────────────────────

    fun observeTargets(): Flow<TargetHistory> = targets.observeAll().map(::TargetHistory)
    suspend fun upsertTarget(target: TargetEntry) = targets.upsert(target)
    suspend fun deleteTarget(id: Long) = targets.delete(id)

    // ── Whole-database snapshot (backup/export) ──────────────────────────────

    suspend fun allEntries() = entries.all()
    suspend fun allImported() = imported.all()
    suspend fun allLifts() = lifts.all()
    suspend fun allTargets() = targets.all()

    /** Earliest date that has any data, or null if empty. */
    suspend fun firstDate(): LocalDate? =
        listOfNotNull(
            entries.all().firstOrNull()?.date,
            imported.all().firstOrNull()?.date,
            food.loggedDates().minOrNull(),
            workouts.firstWorkoutDate(),
        ).minOrNull()

    suspend fun restore(
        entryList: List<DailyEntry>,
        importedList: List<ImportedValue>,
        liftList: List<LiftBestSet>,
        targetList: List<TargetEntry>,
        overwriteManual: Boolean,
    ) {
        db.withTransaction {
            val existing = if (overwriteManual) emptySet() else entries.all().map { it.date }.toSet()
            entries.upsertAll(entryList.filter { it.date !in existing })
            imported.upsertAll(importedList)
            lifts.upsertAll(liftList)
            val existingTargets = targets.all().map { it.metric to it.effectiveFrom }.toSet()
            targets.upsertAll(targetList.filter { (it.metric to it.effectiveFrom) !in existingTargets }.map { it.copy(id = 0) })
        }
    }
}
