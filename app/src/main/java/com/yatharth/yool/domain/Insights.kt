package com.yatharth.yool.domain

import com.yatharth.yool.data.model.TargetMetric
import java.time.LocalDate

/** A labelled group with its sample size and average (avg is null when n == 0). */
data class GroupAvg(val label: String, val n: Int, val avg: Double?) {
    val enough: Boolean get() = n >= Insights.MIN_N
}

data class BingeComparison(
    val bingeDays: Int,
    val otherDays: Int,
    val prevCalories: Pair<GroupAvg, GroupAvg>,
    val prevSleep: Pair<GroupAvg, GroupAvg>,
    val mood: Pair<GroupAvg, GroupAvg>,
    val triggers: List<Pair<String, Int>>,
)

data class Adherence(val label: String, val n: Int, val percent: Double?)

enum class TrendVerdict { ON_TRACK, OFF_TRACK, UNKNOWN }

data class WeightTrend(
    val actualKgPerWeek: Double?,
    val requiredKgPerWeek: Double?,
    val currentKg: Double?,
    val goalKg: Double?,
    val verdict: TrendVerdict,
    val n: Int,
)

data class WorkoutVsRest(
    val workoutMood: GroupAvg,
    val restMood: GroupAvg,
    val workoutCalories: GroupAvg,
    val restCalories: GroupAvg,
)

/** Pure insight calculations over resolved days. */
object Insights {
    const val MIN_N = 3

    private fun group(label: String, values: List<Double>) = GroupAvg(label, values.size, Stats.mean(values))

    /** Average mood on the day AFTER sleep fell in each band: <6h, 6-7h, 7-8h, >=8h. */
    fun sleepVsNextMood(days: List<ResolvedDay>): List<GroupAvg> {
        val byDate = days.associateBy { it.date }
        val bands = listOf<Pair<String, (Double) -> Boolean>>(
            "Under 6h" to { h -> h < 6.0 },
            "6 to 7h" to { h -> h >= 6.0 && h < 7.0 },
            "7 to 8h" to { h -> h >= 7.0 && h < 8.0 },
            "8h or more" to { h -> h >= 8.0 },
        )
        return bands.map { (label, test) ->
            val moods = days.mapNotNull { d ->
                val sleep = d.sleepHours ?: return@mapNotNull null
                if (!test(sleep)) return@mapNotNull null
                byDate[d.date.plusDays(1)]?.mood?.toDouble()
            }
            group(label, moods)
        }
    }

    fun bingeComparison(days: List<ResolvedDay>): BingeComparison {
        val byDate = days.associateBy { it.date }
        val binge = days.filter { it.binge == true }
        val other = days.filter { it.binge == false }
        fun pair(f: (ResolvedDay) -> Double?, usePrev: Boolean): Pair<GroupAvg, GroupAvg> {
            fun pick(set: List<ResolvedDay>) =
                if (usePrev) set.mapNotNull { d -> byDate[d.date.minusDays(1)]?.let(f) } else set.mapNotNull(f)
            return group("Binge days", pick(binge)) to group("Other days", pick(other))
        }
        return BingeComparison(
            bingeDays = binge.size,
            otherDays = other.size,
            prevCalories = pair({ it.calories?.toDouble() }, true),
            prevSleep = pair({ it.sleepHours }, true),
            mood = pair({ it.mood?.toDouble() }, false),
            triggers = Stats.triggerCounts(days),
        )
    }

    /** Average mood on days at/above the step target in force that day vs below it. */
    fun stepsVsMood(days: List<ResolvedDay>, targets: TargetHistory): Pair<GroupAvg, GroupAvg> {
        val hit = mutableListOf<Double>()
        val miss = mutableListOf<Double>()
        for (d in days) {
            val steps = d.steps ?: continue
            val mood = d.mood?.toDouble() ?: continue
            val target = targets.valueOn(TargetMetric.STEPS, d.date) ?: continue
            if (steps >= target) hit += mood else miss += mood
        }
        return group("Target hit", hit) to group("Under target", miss)
    }

    /** % of days meeting each target, using the target in force on each day. */
    fun adherence(days: List<ResolvedDay>, targets: TargetHistory): List<Adherence> {
        fun of(label: String, metric: TargetMetric, check: (ResolvedDay, Double?) -> Boolean?): Adherence {
            val checks = days.map { check(it, targets.valueOn(metric, it.date)) }
            return Adherence(label, checks.count { it != null }, Stats.percentHit(checks))
        }
        return listOf(
            of("Calorie limit", TargetMetric.CALORIES) { d, t -> TargetCheck.caloriesHit(d.calories, t) },
            of("Protein", TargetMetric.PROTEIN) { d, t -> TargetCheck.floorHit(d.proteinG, t) },
            of("Steps", TargetMetric.STEPS) { d, t -> TargetCheck.floorHit(d.steps?.toDouble(), t) },
            of("Sleep", TargetMetric.SLEEP_HOURS) { d, t -> TargetCheck.floorHit(d.sleepHours, t) },
        )
    }

    /**
     * Actual weight slope (kg/week, least squares) vs the slope needed to reach the goal weight by its target date.
     * Without a target date, ON_TRACK just means moving toward the goal.
     */
    fun weightTrend(days: List<ResolvedDay>, targets: TargetHistory, today: LocalDate = LocalDate.now()): WeightTrend {
        val pts = days.mapNotNull { d -> d.weightKg?.let { DatePoint(d.date, it) } }
        val actual = Stats.slopePerWeek(pts)
        val current = pts.maxByOrNull { it.date }?.value
        val goalEntry = targets.entryOn(TargetMetric.WEIGHT_GOAL, today)
        val goal = goalEntry?.value
        val targetDate = goalEntry?.targetDate
        val required = if (goal != null && current != null && targetDate != null) {
            val daysLeft = Stats.daysBetween(today, targetDate)
            if (daysLeft > 0) (goal - current) / (daysLeft / 7.0) else null
        } else null
        val verdict = when {
            actual == null || goal == null || current == null -> TrendVerdict.UNKNOWN
            required != null -> when {
                required == 0.0 -> TrendVerdict.ON_TRACK
                required < 0 -> if (actual <= required) TrendVerdict.ON_TRACK else TrendVerdict.OFF_TRACK
                else -> if (actual >= required) TrendVerdict.ON_TRACK else TrendVerdict.OFF_TRACK
            }
            else -> {
                val dir = goal - current
                if (dir == 0.0 || (dir < 0 && actual < 0) || (dir > 0 && actual > 0)) TrendVerdict.ON_TRACK else TrendVerdict.OFF_TRACK
            }
        }
        return WeightTrend(actual, required, current, goal, verdict, pts.size)
    }

    /** Average mood and calories on workout days vs rest days. */
    fun workoutVsRest(days: List<ResolvedDay>): WorkoutVsRest {
        val w = days.filter { it.workoutDone == true }
        val r = days.filter { it.workoutDone != true }
        return WorkoutVsRest(
            group("Workout days", w.mapNotNull { it.mood?.toDouble() }),
            group("Rest days", r.mapNotNull { it.mood?.toDouble() }),
            group("Workout days", w.mapNotNull { it.calories?.toDouble() }),
            group("Rest days", r.mapNotNull { it.calories?.toDouble() }),
        )
    }
}
