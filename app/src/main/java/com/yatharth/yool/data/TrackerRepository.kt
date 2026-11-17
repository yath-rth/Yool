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

    // ── Resolved days ────────────────────────────────────────────────────────

    /** Food-diary totals join imported values as source FOOD_LOG (ranked above MFP, below typed). */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<ResolvedDay>> =
        combine(entries.observeRange(from, to), imported.observeRange(from, to), food.observeTotals(from, to)) { e, i, f ->
            DayResolver.resolveRange(from, to, e, i + FoodMath.asImported(f))
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

    fun observeLifts(from: LocalDate, to: LocalDate): Flow<List<LiftBestSet>> = lifts.observeRange(from, to)
    fun observeExercises(): Flow<List<String>> = lifts.observeExercises()
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
