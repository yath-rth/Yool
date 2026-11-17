package com.yatharth.yool.domain

import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.TargetMetric
import java.time.LocalDate

/** Lookup of versioned targets: the value in force on a date is the latest entry with effectiveFrom <= date. */
class TargetHistory(entries: List<TargetEntry>) {
    private val byMetric: Map<TargetMetric, List<TargetEntry>> =
        entries.groupBy { it.metric }.mapValues { (_, v) -> v.sortedBy { it.effectiveFrom } }

    fun entryOn(metric: TargetMetric, date: LocalDate): TargetEntry? =
        byMetric[metric]?.lastOrNull { !it.effectiveFrom.isAfter(date) }

    fun valueOn(metric: TargetMetric, date: LocalDate): Double? = entryOn(metric, date)?.value

    fun current(metric: TargetMetric): TargetEntry? = entryOn(metric, LocalDate.now())

    fun history(metric: TargetMetric): List<TargetEntry> = byMetric[metric].orEmpty()

    companion object {
        val Empty = TargetHistory(emptyList())
    }
}

/** Whether a day hit its target. Calories is a ceiling (at or under); the rest are floors (at or over). */
object TargetCheck {
    fun caloriesHit(calories: Int?, target: Double?): Boolean? =
        if (calories == null || target == null) null else calories <= target

    fun floorHit(value: Double?, target: Double?): Boolean? =
        if (value == null || target == null) null else value >= target
}
