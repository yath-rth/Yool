package com.yatharth.yool.domain

import com.yatharth.yool.data.model.TargetMetric
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** One value on one date. */
data class DatePoint(val date: LocalDate, val value: Double)

/** Pure calculations behind the Graphs screen. */
object Stats {

    fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

    /** Trailing moving average over a calendar window: for each point, the mean of points in (date-window, date]. */
    fun movingAverage(points: List<DatePoint>, windowDays: Int = 7): List<DatePoint> {
        val sorted = points.sortedBy { it.date }
        return sorted.map { p ->
            val from = p.date.minusDays(windowDays - 1L)
            val inWindow = sorted.filter { !it.date.isBefore(from) && !it.date.isAfter(p.date) }
            DatePoint(p.date, inWindow.sumOf { it.value } / inWindow.size)
        }
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Average per Monday-start week, dated at the week start. Weeks without points are skipped. */
    fun weeklyAverages(points: List<DatePoint>): List<DatePoint> =
        points.groupBy { weekStart(it.date) }
            .toSortedMap()
            .map { (week, ps) -> DatePoint(week, ps.sumOf { it.value } / ps.size) }

    /** Least-squares slope in value-units per day (x = epoch day). Null with fewer than 2 points or no x spread. */
    fun slopePerDay(points: List<DatePoint>): Double? {
        if (points.size < 2) return null
        val xs = points.map { it.date.toEpochDay().toDouble() }
        val ys = points.map { it.value }
        val mx = xs.average()
        val my = ys.average()
        val denom = xs.sumOf { (it - mx) * (it - mx) }
        if (denom == 0.0) return null
        return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / denom
    }

    fun slopePerWeek(points: List<DatePoint>): Double? = slopePerDay(points)?.times(7)

    /** Number of workout days in each Monday-start week touching [from]..[to] (weeks with zero included). */
    fun workoutsPerWeek(days: List<ResolvedDay>, from: LocalDate, to: LocalDate): List<DatePoint> {
        val counts = days.filter { it.workoutDone == true && !it.date.isBefore(from) && !it.date.isAfter(to) }
            .groupingBy { weekStart(it.date) }.eachCount()
        val out = mutableListOf<DatePoint>()
        var w = weekStart(from)
        while (!w.isAfter(to)) {
            out += DatePoint(w, (counts[w] ?: 0).toDouble())
            w = w.plusWeeks(1)
        }
        return out
    }

    /** Longest run of consecutive calendar days with binge == false. */
    fun longestBingeFree(days: List<ResolvedDay>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.sortedBy { it.date }) {
            if (d.binge == false) {
                run = if (prev != null && prev.plusDays(1) == d.date && run > 0) run + 1 else 1
                best = maxOf(best, run)
            } else run = 0
            prev = d.date
        }
        return best
    }

    /** Percentage 0..100 of non-null checks that are true; null if there are none. */
    fun percentHit(checks: List<Boolean?>): Double? {
        val known = checks.filterNotNull()
        return if (known.isEmpty()) null else known.count { it } * 100.0 / known.size
    }

    /** Ranked (trigger, count) among binge days; blank triggers are ignored. */
    fun triggerCounts(days: List<ResolvedDay>): List<Pair<String, Int>> =
        days.filter { it.binge == true }
            .mapNotNull { it.bingeTrigger?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    /** Mood 1..5 -> count. */
    fun moodDistribution(days: List<ResolvedDay>): Map<Int, Int> =
        (1..5).associateWith { m -> days.count { it.mood == m } }

    /** Aggregates a day series so long ranges stay readable. Returns (points, isWeekly). */
    fun bucketForDisplay(points: List<DatePoint>, rangeDays: Long, maxDaily: Long = 90): Pair<List<DatePoint>, Boolean> =
        if (rangeDays > maxDaily) weeklyAverages(points) to true else points.sortedBy { it.date } to false

    /** Target in force as a step series: the value at [from] plus one point per change within [from]..[to]. */
    fun targetSteps(history: TargetHistory, metric: TargetMetric, from: LocalDate, to: LocalDate): List<DatePoint> {
        val out = mutableListOf<DatePoint>()
        history.valueOn(metric, from)?.let { out += DatePoint(from, it) }
        history.history(metric).filter { it.effectiveFrom.isAfter(from) && !it.effectiveFrom.isAfter(to) }
            .forEach { out += DatePoint(it.effectiveFrom, it.value) }
        return out
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
