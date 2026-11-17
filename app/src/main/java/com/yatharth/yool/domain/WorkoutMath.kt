package com.yatharth.yool.domain

import com.yatharth.yool.data.db.ExerciseSetRow
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.db.LoggedBestSet
import com.yatharth.yool.data.db.TemplateFull
import com.yatharth.yool.data.db.WorkoutDayCount
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.data.model.Source
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/** A set as typed in a template or produced from a workout (no ids). */
data class SetSpec(
    val type: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val restSec: Int? = null,
)

/** One exercise of a template, in order. */
data class ExerciseSpec(
    val exerciseId: Long,
    val sets: List<SetSpec>,
    val supersetGroup: Int? = null,
    val note: String? = null,
)

enum class PrKind(val label: String) { WEIGHT("Heaviest weight"), ONE_RM("Best est. 1RM"), VOLUME("Best set volume") }

/** Personal records for one exercise, from all its completed working sets. */
data class ExerciseRecords(
    val heaviestKg: Double? = null,
    val best1RmKg: Double? = null,
    val bestSetVolumeKg: Double? = null,
    val bestSessionVolumeKg: Double? = null,
    val longestDurationSec: Int? = null,
    val longestDistanceM: Double? = null,
    /** reps (1..12) → heaviest weight lifted for at least that many reps. */
    val repMaxes: Map<Int, Double> = emptyMap(),
    val sessions: Int = 0,
)

/** One point per workout session for exercise charts. */
data class ExerciseSessionPoint(
    val date: LocalDate,
    val workoutId: Long,
    val best1RmKg: Double?,
    val maxWeightKg: Double?,
    val volumeKg: Double,
    val bestSet: Pair<Double, Int>?,
)

data class WorkoutSummary(
    val durationMs: Long,
    val volumeKg: Double,
    val completedSets: Int,
    /** (exercise name, set position, PR kinds). */
    val prs: List<Triple<String, Int, Set<PrKind>>>,
)

object WorkoutMath {
    /** Epley estimated one-rep max; exact weight for singles. Null when reps/weight missing. */
    fun oneRepMax(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null || reps <= 0 || weightKg <= 0) return null
        return if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
    }

    fun isWorking(type: SetType) = type == SetType.NORMAL || type == SetType.FAILURE

    fun setVolume(weightKg: Double?, reps: Int?): Double = if (weightKg != null && reps != null && reps > 0) weightKg * reps else 0.0

    /** Volume of completed non-warm-up sets. */
    fun volume(workout: WorkoutFull): Double =
        workout.exercises.sumOf { ex -> ex.sets.filter { it.completed && it.type != SetType.WARMUP }.sumOf { setVolume(it.weightKg, it.reps) } }

    fun completedSets(workout: WorkoutFull): Int = workout.exercises.sumOf { ex -> ex.sets.count { it.completed } }

    /** Best set per (date, exercise): heaviest weight, ties by most reps. */
    fun bestPerDay(sets: List<LoggedBestSet>): List<LiftBestSet> =
        sets.groupBy { it.date to it.exercise }.map { (k, g) ->
            val b = g.maxWith(compareBy<LoggedBestSet> { it.weightKg }.thenBy { it.reps })
            LiftBestSet(k.first, k.second, b.weightKg, b.reps)
        }

    /** Merge best sets: the workout log wins over imported Strong best sets for the same (date, exercise). */
    fun mergeLifts(imported: List<LiftBestSet>, logged: List<LiftBestSet>): List<LiftBestSet> {
        val keys = logged.map { it.date to it.exercise }.toHashSet()
        return (imported.filter { (it.date to it.exercise) !in keys } + logged).sortedWith(compareBy({ it.date }, { it.exercise }))
    }

    /** Finished-workout days as synthetic imported values (source WORKOUT_LOG). */
    fun asImported(counts: List<WorkoutDayCount>): List<ImportedValue> =
        counts.filter { it.count > 0 }.map { ImportedValue(it.date, Metric.WORKOUT, Source.WORKOUT_LOG, 1.0, 0) }

    /**
     * Strong's "Previous" column: the set at the same index among sets of the same kind (warm-ups match
     * warm-ups, everything else matches non-warm-ups) in the last session. Null if there's no such set.
     */
    fun previousFor(index: Int, current: List<SetType>, previous: List<ExerciseSetRow>): ExerciseSetRow? {
        val type = current.getOrNull(index) ?: return null
        val warm = type == SetType.WARMUP
        val nth = current.take(index).count { (it == SetType.WARMUP) == warm }
        return previous.filter { (it.type == SetType.WARMUP) == warm }.getOrNull(nth)
    }

    fun records(rows: List<ExerciseSetRow>): ExerciseRecords {
        val working = rows.filter { isWorking(it.type) }
        if (rows.isEmpty()) return ExerciseRecords()
        val repMaxes = (1..12).mapNotNull { r ->
            working.filter { (it.reps ?: 0) >= r && it.weightKg != null }.maxOfOrNull { it.weightKg!! }?.let { r to it }
        }.toMap()
        return ExerciseRecords(
            heaviestKg = working.mapNotNull { it.weightKg?.takeIf { w -> (it.reps ?: 0) > 0 && w > 0 } }.maxOrNull(),
            best1RmKg = working.mapNotNull { oneRepMax(it.weightKg, it.reps) }.maxOrNull(),
            bestSetVolumeKg = working.map { setVolume(it.weightKg, it.reps) }.filter { it > 0 }.maxOrNull(),
            bestSessionVolumeKg = rows.filter { it.type != SetType.WARMUP }.groupBy { it.workoutId }
                .map { (_, s) -> s.sumOf { setVolume(it.weightKg, it.reps) } }.filter { it > 0 }.maxOrNull(),
            longestDurationSec = rows.mapNotNull { it.durationSec }.filter { it > 0 }.maxOrNull(),
            longestDistanceM = rows.mapNotNull { it.distanceM }.filter { it > 0 }.maxOrNull(),
            repMaxes = repMaxes,
            sessions = rows.map { it.workoutId }.distinct().size,
        )
    }

    fun sessions(rows: List<ExerciseSetRow>): List<ExerciseSessionPoint> =
        rows.groupBy { it.workoutId }.map { (id, s) ->
            val working = s.filter { isWorking(it.type) }
            val best = working.filter { it.weightKg != null && (it.reps ?: 0) > 0 }
                .maxWithOrNull(compareBy<ExerciseSetRow> { it.weightKg }.thenBy { it.reps })
            ExerciseSessionPoint(
                date = s.first().date,
                workoutId = id,
                best1RmKg = working.mapNotNull { oneRepMax(it.weightKg, it.reps) }.maxOrNull(),
                maxWeightKg = best?.weightKg,
                volumeKg = s.filter { it.type != SetType.WARMUP }.sumOf { setVolume(it.weightKg, it.reps) },
                bestSet = best?.let { it.weightKg!! to it.reps!! },
            )
        }.sortedBy { it.date }

    /** PR kinds a completed working [set] beats compared with [history] (rows from before this workout). */
    fun prKinds(set: WorkoutSet, history: ExerciseRecords): Set<PrKind> {
        if (!set.completed || !isWorking(set.type)) return emptySet()
        val w = set.weightKg ?: return emptySet()
        val reps = set.reps ?: return emptySet()
        if (reps <= 0 || w <= 0) return emptySet()
        if (history.sessions == 0) return emptySet() // first time doing it isn't a PR
        val out = mutableSetOf<PrKind>()
        if (w > (history.heaviestKg ?: 0.0)) out += PrKind.WEIGHT
        if ((oneRepMax(w, reps) ?: 0.0) > (history.best1RmKg ?: 0.0) + 1e-9) out += PrKind.ONE_RM
        if (setVolume(w, reps) > (history.bestSetVolumeKg ?: 0.0) + 1e-9) out += PrKind.VOLUME
        return out
    }

    /**
     * PRs in [workout], comparing each set against [historyByExercise] (rows from strictly earlier workouts)
     * plus earlier sets of the same workout, so only the best set per kind is flagged.
     */
    fun workoutPrs(workout: WorkoutFull, historyByExercise: Map<Long, List<ExerciseSetRow>>): List<Triple<String, Int, Set<PrKind>>> {
        val out = mutableListOf<Triple<String, Int, Set<PrKind>>>()
        for (ex in workout.ordered) {
            val past = historyByExercise[ex.exercise.id].orEmpty().filter { it.startedAt < workout.workout.startedAt }
            var rec = records(past)
            val flagged = mutableMapOf<PrKind, Int>() // kind -> index into out
            for (s in ex.orderedSets) {
                val kinds = prKinds(s, rec)
                if (kinds.isNotEmpty()) {
                    // A later set in the same workout supersedes an earlier one for the same kind.
                    for (k in kinds) flagged[k]?.let { i -> out[i] = out[i].copy(third = out[i].third - k) }
                    out += Triple(ex.exercise.name, s.position, kinds)
                    kinds.forEach { flagged[it] = out.lastIndex }
                }
                if (s.completed && isWorking(s.type) && s.weightKg != null && (s.reps ?: 0) > 0) {
                    rec = rec.copy(
                        heaviestKg = maxOf(rec.heaviestKg ?: 0.0, s.weightKg),
                        best1RmKg = maxOf(rec.best1RmKg ?: 0.0, oneRepMax(s.weightKg, s.reps) ?: 0.0),
                        bestSetVolumeKg = maxOf(rec.bestSetVolumeKg ?: 0.0, setVolume(s.weightKg, s.reps)),
                    )
                }
            }
        }
        return out.filter { it.third.isNotEmpty() }
    }

    fun summary(workout: WorkoutFull, historyByExercise: Map<Long, List<ExerciseSetRow>>, now: Long = System.currentTimeMillis()): WorkoutSummary =
        WorkoutSummary(
            durationMs = (workout.workout.endedAt ?: now) - workout.workout.startedAt,
            volumeKg = volume(workout),
            completedSets = completedSets(workout),
            prs = workoutPrs(workout, historyByExercise),
        )

    // ── Templates ────────────────────────────────────────────────────────────

    fun specsOf(template: TemplateFull): List<ExerciseSpec> = template.ordered.map { ex ->
        ExerciseSpec(
            ex.exercise.id,
            ex.orderedSets.map { SetSpec(it.type, it.weightKg, it.reps, it.durationSec, it.distanceM, it.restSec) },
            ex.entry.supersetGroup, ex.entry.note,
        )
    }

    /** Template specs from a workout. With [completedOnly], unfinished sets (and empty exercises) are dropped. */
    fun specsOf(workout: WorkoutFull, completedOnly: Boolean = false): List<ExerciseSpec> = workout.ordered.mapNotNull { ex ->
        val sets = ex.orderedSets.filter { !completedOnly || it.completed }
        if (sets.isEmpty() && completedOnly) null
        else ExerciseSpec(
            ex.exercise.id,
            sets.map { SetSpec(it.type, it.weightKg, it.reps, it.durationSec, it.distanceM, it.restSec) },
            ex.entry.supersetGroup,
        )
    }

    /** True when the workout's structure (exercise order, set count and set types) differs from the template's. */
    fun structureChanged(template: TemplateFull, workout: WorkoutFull): Boolean {
        fun shape(specs: List<ExerciseSpec>) = specs.map { Triple(it.exerciseId, it.supersetGroup, it.sets.map { s -> s.type }) }
        return shape(specsOf(template)) != shape(specsOf(workout))
    }

    // ── Rest, plates, warm-ups ───────────────────────────────────────────────

    /** Rest after a set: the set's own, else the exercise default, else the global default. */
    fun restFor(setRest: Int?, exerciseRest: Int?, defaultRest: Int): Int = setRest ?: exerciseRest ?: defaultRest

    /**
     * Plates per side to load [targetKg] on a [barKg] bar, greedy from heaviest. Returns the plates and
     * the leftover kg per side that couldn't be matched (0 when exact).
     */
    fun platesPerSide(targetKg: Double, barKg: Double, available: List<Double>): Pair<List<Double>, Double> {
        var perSide = (targetKg - barKg) / 2
        if (perSide <= 0) return emptyList<Double>() to 0.0
        val out = mutableListOf<Double>()
        for (p in available.filter { it > 0 }.sortedDescending()) {
            while (perSide + 1e-9 >= p) {
                out += p; perSide -= p
            }
        }
        return out to (Math.round(perSide * 1000) / 1000.0)
    }

    /** Round to the nearest multiple of [step]. */
    fun roundTo(value: Double, step: Double): Double = if (step <= 0) value else Math.round(value / step) * step

    /**
     * Strong-style warm-up ramp towards [workingKg]: empty bar ×10, 50% ×5, 70% ×3, 85% ×2 (steps that
     * aren't above the previous one are dropped). Weights are rounded down to [step].
     */
    fun warmupSets(workingKg: Double, barKg: Double, step: Double = 2.5): List<SetSpec> {
        if (workingKg <= barKg) return emptyList()
        val ramp = listOf(0.0 to 10, 0.5 to 5, 0.7 to 3, 0.85 to 2)
        val out = mutableListOf<SetSpec>()
        var last = -1.0
        for ((pct, reps) in ramp) {
            val w = if (pct == 0.0) barKg else maxOf(barKg, floor(workingKg * pct / step) * step)
            if (w > last && w < workingKg) {
                out += SetSpec(SetType.WARMUP, w, reps); last = w
            }
        }
        return out
    }

    /** One line per exercise with completed sets, e.g. "3 × Lat Pulldown (Cable)". */
    fun exerciseLines(workout: WorkoutFull): List<String> =
        workout.ordered.mapNotNull { ex ->
            val n = ex.sets.count { it.completed }
            if (n > 0) "$n × ${ex.exercise.name}" else null
        }

    /** "1h 05m" / "42m" / "45s". */
    fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return when {
            h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
            m > 0 -> "${m}m"
            else -> "${totalSec}s"
        }
    }

    /** "1:30" countdown format. */
    fun formatClock(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** Strong's default names by time of day. */
    fun defaultName(hour: Int): String = when (hour) {
        in 5..11 -> "Morning Workout"
        in 12..16 -> "Afternoon Workout"
        in 17..21 -> "Evening Workout"
        else -> "Night Workout"
    }

    fun kg(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}
