package com.yatharth.yool.data.healthconnect

import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Package names of apps we treat specially. */
object HcOrigins {
    const val MYFITNESSPAL = "com.myfitnesspal.android"
    const val STRONG = "io.strongapp.strong"
}

/** One day's value for one metric, already attributed to a [Source]. */
data class DayValue(val date: LocalDate, val metric: Metric, val source: Source, val value: Double)

// Plain samples (decoupled from the HC SDK so the logic is unit-testable).

data class NutritionSample(
    val id: String,
    val start: Instant,
    val origin: String?,
    val kcal: Double?,
    val proteinG: Double?,
)

data class SleepStageSample(val start: Instant, val end: Instant, val awake: Boolean)

data class SleepSample(
    val id: String,
    val start: Instant,
    val end: Instant,
    val stages: List<SleepStageSample> = emptyList(),
)

data class ExerciseSample(
    val id: String,
    val start: Instant,
    val origin: String?,
    val strengthLike: Boolean,
)

/** Pure transformations from raw samples to per-local-day values. */
object HealthConnectLogic {

    fun sourceForNutrition(origin: String?): Source =
        if (origin == HcOrigins.MYFITNESSPAL) Source.MFP else Source.HEALTH_CONNECT

    /**
     * Sum kcal and protein per local day of each record's start time, split by MFP vs other origins.
     * Days whose total is zero are omitted.
     */
    fun nutrition(samples: List<NutritionSample>, zone: ZoneId, from: LocalDate, to: LocalDate): List<DayValue> {
        val out = ArrayList<DayValue>()
        samples.distinctBy { it.id }
            .groupBy { sourceForNutrition(it.origin) to it.start.atZone(zone).toLocalDate() }
            .forEach { (key, recs) ->
                val (source, date) = key
                if (date.isBefore(from) || date.isAfter(to)) return@forEach
                val kcal = recs.sumOf { it.kcal ?: 0.0 }
                val protein = recs.sumOf { it.proteinG ?: 0.0 }
                if (kcal > 0) out += DayValue(date, Metric.CALORIES, source, kcal)
                if (protein > 0) out += DayValue(date, Metric.PROTEIN_G, source, protein)
            }
        return out.sortedWith(compareBy({ it.date }, { it.metric }, { it.source }))
    }

    /** Hours asleep for one session: its duration minus the union of AWAKE / OUT_OF_BED stages. */
    fun sleepHours(s: SleepSample): Double {
        val total = (s.end.toEpochMilli() - s.start.toEpochMilli()).coerceAtLeast(0)
        val awake = mergedMillis(
            s.stages.filter { it.awake }
                .map { maxOf(it.start.toEpochMilli(), s.start.toEpochMilli()) to minOf(it.end.toEpochMilli(), s.end.toEpochMilli()) }
                .filter { it.second > it.first },
        )
        return (total - awake).coerceAtLeast(0) / 3_600_000.0
    }

    private fun mergedMillis(ranges: List<Pair<Long, Long>>): Long {
        var sum = 0L
        var curStart = 0L
        var curEnd = 0L
        var open = false
        for ((a, b) in ranges.sortedBy { it.first }) {
            if (!open) {
                curStart = a; curEnd = b; open = true
            } else if (a > curEnd) {
                sum += curEnd - curStart
                curStart = a; curEnd = b
            } else curEnd = maxOf(curEnd, b)
        }
        if (open) sum += curEnd - curStart
        return sum
    }

    /** Each session goes to the local date it ENDED on (wake-up day); multiple sessions per day are summed. */
    fun sleep(samples: List<SleepSample>, zone: ZoneId, from: LocalDate, to: LocalDate): List<DayValue> =
        samples.distinctBy { it.id }
            .groupBy { it.end.atZone(zone).toLocalDate() }
            .filterKeys { !it.isBefore(from) && !it.isAfter(to) }
            .map { (date, list) -> DayValue(date, Metric.SLEEP_HOURS, Source.HEALTH_CONNECT, list.sumOf(::sleepHours)) }
            .filter { it.value > 0 }
            .sortedBy { it.date }

    fun isWorkout(s: ExerciseSample): Boolean = s.origin == HcOrigins.STRONG || s.strengthLike

    /** WORKOUT = 1.0 on the local start date of each qualifying session. */
    fun workouts(samples: List<ExerciseSample>, zone: ZoneId, from: LocalDate, to: LocalDate): List<DayValue> =
        samples.distinctBy { it.id }
            .filter(::isWorkout)
            .map { it.start.atZone(zone).toLocalDate() }
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .distinct()
            .sorted()
            .map { DayValue(it, Metric.WORKOUT, Source.HEALTH_CONNECT, 1.0) }
}
