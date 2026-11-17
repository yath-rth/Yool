package com.yatharth.yool.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.data.model.TargetMetric
import java.time.LocalDate

/**
 * Everything the user typed for a day. Metric columns (weight..workoutDone) are manual values that
 * override imported ones; binge/mood/note only ever come from the user.
 */
@Entity(tableName = "daily_entry")
data class DailyEntry(
    @PrimaryKey val date: LocalDate,
    val weightKg: Double? = null,
    val calories: Int? = null,
    val proteinG: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val workoutDone: Boolean? = null,
    val binge: Boolean? = null,
    val bingeTrigger: String? = null,
    val bingeNote: String? = null,
    /** 1..5 */
    val mood: Int? = null,
    /** Max 200 chars. */
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A value pulled from an external source for one day. One row per (date, metric, source). */
@Entity(tableName = "imported_value", primaryKeys = ["date", "metric", "source"], indices = [Index("date")])
data class ImportedValue(
    val date: LocalDate,
    val metric: Metric,
    val source: Source,
    val value: Double,
    val importedAt: Long = System.currentTimeMillis(),
)

/** Best set of an exercise on a day (heaviest weight, ties broken by most reps). From Strong CSV. */
@Entity(tableName = "lift_best_set", primaryKeys = ["date", "exercise"], indices = [Index("exercise")])
data class LiftBestSet(
    val date: LocalDate,
    val exercise: String,
    val weightKg: Double,
    val reps: Int,
)

/** A target value in force from [effectiveFrom] until the next entry for the same metric. */
@Entity(tableName = "target", indices = [Index(value = ["metric", "effectiveFrom"], unique = true)])
data class TargetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: TargetMetric,
    val value: Double,
    val effectiveFrom: LocalDate,
    /** Only for WEIGHT_GOAL: optional date to reach the goal by. */
    val targetDate: LocalDate? = null,
)
