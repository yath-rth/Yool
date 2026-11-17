package com.yatharth.yool.ui.workout

import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.TemplateFull
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.domain.ExerciseRecords
import com.yatharth.yool.domain.ExerciseSpec
import com.yatharth.yool.domain.PrKind
import com.yatharth.yool.domain.SetSpec
import com.yatharth.yool.domain.WorkoutMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/* Pure helpers for the workout library screens (no Compose, unit tested). */

// ── History grouping ─────────────────────────────────────────────────────────

data class MonthGroup<T>(val month: YearMonth, val items: List<T>)

/** Groups consecutive items by month, keeping their order (pass newest first for a history list). */
fun <T> groupByMonth(items: List<T>, dateOf: (T) -> LocalDate): List<MonthGroup<T>> {
    val out = mutableListOf<MonthGroup<T>>()
    for (item in items) {
        val m = YearMonth.from(dateOf(item))
        val last = out.lastOrNull()
        if (last != null && last.month == m) out[out.lastIndex] = last.copy(items = last.items + item)
        else out += MonthGroup(m, listOf(item))
    }
    return out
}

fun monthLabel(m: YearMonth): String =
    m.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + m.year

/** Workouts per week for the last [weeks] weeks (Monday-based), oldest first; last entry is the current week. */
fun workoutsPerWeek(dates: List<LocalDate>, today: LocalDate, weeks: Int = 8): List<Pair<LocalDate, Int>> {
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val starts = (weeks - 1 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
    return starts.map { s -> s to dates.count { !it.isBefore(s) && it.isBefore(s.plusWeeks(1)) } }
}

// ── Exercise filtering ───────────────────────────────────────────────────────

fun filterExercises(all: List<Exercise>, query: String, bodyPart: BodyPart?, type: ExerciseType?): List<Exercise> {
    val q = query.trim()
    return all.filter { e ->
        (q.isEmpty() || e.name.contains(q, ignoreCase = true)) &&
            (bodyPart == null || e.bodyPart == bodyPart) &&
            (type == null || e.type == type)
    }
}

/** "x" when 1, "Nx" otherwise: "performed 12 times". */
fun timesLabel(n: Int): String = when (n) {
    0 -> "never performed"
    1 -> "performed once"
    else -> "performed $n times"
}

// ── Formatting ───────────────────────────────────────────────────────────────

fun formatDistance(m: Double): String =
    if (m >= 1000) WorkoutMath.kg(Math.round(m / 10.0) / 100.0) + " km" else WorkoutMath.kg(m) + " m"

fun formatSeconds(sec: Int): String = if (sec >= 3600) WorkoutMath.formatDuration(sec * 1000L) else WorkoutMath.formatClock(sec)

/** One set as text: "60 kg x 8", "12 reps", "30:00 / 5 km". Empty targets give "-". */
fun setText(type: ExerciseType, weightKg: Double?, reps: Int?, durationSec: Int?, distanceM: Double?): String {
    val parts = mutableListOf<String>()
    if (type.usesWeight && weightKg != null && reps != null) {
        parts += (if (type == ExerciseType.ASSISTED_BODYWEIGHT) "-" else "") + WorkoutMath.kg(weightKg) + " kg x " + reps
    } else if (type.usesReps && reps != null) {
        parts += "$reps reps"
    } else if (type.usesWeight && weightKg != null) {
        parts += WorkoutMath.kg(weightKg) + " kg"
    }
    if (type.usesDuration && durationSec != null) parts += formatSeconds(durationSec)
    if (type.usesDistance && distanceM != null) parts += formatDistance(distanceM)
    return parts.joinToString(" / ").ifEmpty { "-" }
}

/** "3 x Bench Press" lines. Finished workouts count completed sets; others count all sets. */
fun exerciseLines(w: WorkoutFull): List<String> = w.ordered.mapNotNull { ex ->
    val n = if (w.workout.endedAt != null) ex.sets.count { it.completed } else ex.sets.size
    if (n == 0) null else "$n × ${ex.exercise.name}"
}

fun exerciseLines(t: TemplateFull): List<String> = t.ordered.map { "${it.sets.size} × ${it.exercise.name}" }

// ── PR counting across a whole history ───────────────────────────────────────

/**
 * PR count per workout id, computed in one chronological pass (instead of re-querying each exercise's
 * history for every workout). Uses the same rules as [WorkoutMath.workoutPrs].
 */
fun prCounts(workouts: List<WorkoutFull>): Map<Long, Int> {
    val running = HashMap<Long, ExerciseRecords>()
    val out = HashMap<Long, Int>()
    for (w in workouts.sortedBy { it.workout.startedAt }) {
        var total = 0
        for (ex in w.ordered) {
            var rec = running[ex.exercise.id] ?: ExerciseRecords()
            val kindsBySet = LinkedHashMap<Int, MutableSet<PrKind>>()
            val owner = HashMap<PrKind, Int>()
            var any = false
            for (s in ex.orderedSets) {
                val kinds = WorkoutMath.prKinds(s, rec)
                if (kinds.isNotEmpty()) {
                    for (k in kinds) owner[k]?.let { prev -> kindsBySet[prev]?.remove(k) }
                    kindsBySet[s.position] = kinds.toMutableSet()
                    kinds.forEach { owner[it] = s.position }
                }
                if (s.completed) any = true
                if (s.completed && WorkoutMath.isWorking(s.type) && s.weightKg != null && (s.reps ?: 0) > 0) {
                    rec = rec.copy(
                        heaviestKg = maxOf(rec.heaviestKg ?: 0.0, s.weightKg),
                        best1RmKg = maxOf(rec.best1RmKg ?: 0.0, WorkoutMath.oneRepMax(s.weightKg, s.reps) ?: 0.0),
                        bestSetVolumeKg = maxOf(rec.bestSetVolumeKg ?: 0.0, WorkoutMath.setVolume(s.weightKg, s.reps)),
                    )
                }
            }
            total += kindsBySet.values.count { it.isNotEmpty() }
            if (any) running[ex.exercise.id] = rec.copy(sessions = rec.sessions + 1)
        }
        out[w.workout.id] = total
    }
    return out
}

// ── Template editor state ────────────────────────────────────────────────────

data class DraftSet(
    val key: Long,
    val type: SetType = SetType.NORMAL,
    val weight: String = "",
    val reps: String = "",
    val duration: String = "",
    /** Kilometres as typed. */
    val distance: String = "",
    val rest: String = "",
)

data class DraftExercise(
    val key: Long,
    val exerciseId: Long,
    val supersetGroup: Int? = null,
    val note: String? = null,
    val sets: List<DraftSet> = emptyList(),
)

/** Ordering / superset / set operations of the template editor on immutable lists. */
object TemplateDraftOps {
    fun move(list: List<DraftExercise>, from: Int, to: Int): List<DraftExercise> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return normalizeSupersets(m)
    }

    fun remove(list: List<DraftExercise>, index: Int): List<DraftExercise> =
        if (index !in list.indices) list else normalizeSupersets(list.filterIndexed { i, _ -> i != index })

    fun replace(list: List<DraftExercise>, index: Int, newExerciseId: Long, newSets: List<DraftSet>): List<DraftExercise> =
        list.mapIndexed { i, e -> if (i == index) e.copy(exerciseId = newExerciseId, sets = newSets, note = null) else e }

    /** Join exercise [index] with the next one in a superset, or split them if they already share one. */
    fun toggleSupersetWithNext(list: List<DraftExercise>, index: Int): List<DraftExercise> {
        if (index !in 0 until list.lastIndex) return list
        val a = list[index]
        val b = list[index + 1]
        val m = list.toMutableList()
        if (a.supersetGroup != null && a.supersetGroup == b.supersetGroup) {
            // Split after a: the rest of the group gets a fresh number.
            val old = a.supersetGroup
            val fresh = (list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1
            for (i in index + 1..list.lastIndex) if (m[i].supersetGroup == old) m[i] = m[i].copy(supersetGroup = fresh) else break
        } else {
            val g = a.supersetGroup ?: ((list.mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1)
            m[index] = a.copy(supersetGroup = g)
            // b (and anything grouped with b) joins g
            val bGroup = b.supersetGroup
            for (i in index + 1..list.lastIndex) {
                if (i == index + 1 || (bGroup != null && m[i].supersetGroup == bGroup)) m[i] = m[i].copy(supersetGroup = g) else break
            }
        }
        return normalizeSupersets(m)
    }

    /** Groups need 2+ adjacent members; others are cleared. Numbers are renumbered 1.. in order. */
    fun normalizeSupersets(list: List<DraftExercise>): List<DraftExercise> {
        val out = list.toMutableList()
        var i = 0
        var next = 1
        while (i < out.size) {
            val g = out[i].supersetGroup
            if (g == null) { i++; continue }
            var j = i
            while (j + 1 < out.size && out[j + 1].supersetGroup == g) j++
            if (j == i) out[i] = out[i].copy(supersetGroup = null)
            else { for (k in i..j) out[k] = out[k].copy(supersetGroup = next); next++ }
            i = j + 1
        }
        return out
    }

    fun addSet(list: List<DraftExercise>, index: Int, key: Long): List<DraftExercise> =
        list.mapIndexed { i, e ->
            if (i != index) e else {
                val last = e.sets.lastOrNull()
                e.copy(sets = e.sets + (last?.copy(key = key) ?: DraftSet(key)))
            }
        }

    fun removeSet(list: List<DraftExercise>, exIndex: Int, setIndex: Int): List<DraftExercise> =
        list.mapIndexed { i, e -> if (i == exIndex) e.copy(sets = e.sets.filterIndexed { j, _ -> j != setIndex }) else e }

    fun updateSet(list: List<DraftExercise>, exIndex: Int, setIndex: Int, f: (DraftSet) -> DraftSet): List<DraftExercise> =
        list.mapIndexed { i, e ->
            if (i == exIndex) e.copy(sets = e.sets.mapIndexed { j, s -> if (j == setIndex) f(s) else s }) else e
        }

    private fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    fun toSpec(e: DraftExercise): ExerciseSpec = ExerciseSpec(
        e.exerciseId,
        e.sets.map { s ->
            SetSpec(
                s.type, num(s.weight), num(s.reps)?.toInt(), num(s.duration)?.toInt(),
                num(s.distance)?.let { it * 1000 }, num(s.rest)?.toInt(),
            )
        },
        e.supersetGroup, e.note?.trim()?.ifEmpty { null },
    )

    fun toSpecs(list: List<DraftExercise>): List<ExerciseSpec> = list.map(::toSpec)

    private fun fmt(v: Double?): String = v?.let { WorkoutMath.kg(it) } ?: ""

    fun fromSpecs(specs: List<ExerciseSpec>, nextKey: () -> Long): List<DraftExercise> = specs.map { sp ->
        DraftExercise(
            nextKey(), sp.exerciseId, sp.supersetGroup, sp.note,
            sp.sets.map { s ->
                DraftSet(
                    nextKey(), s.type, fmt(s.weightKg), s.reps?.toString() ?: "", s.durationSec?.toString() ?: "",
                    fmt(s.distanceM?.div(1000)), s.restSec?.toString() ?: "",
                )
            },
        )
    }

    /** A fresh exercise with 3 empty sets, resting [restSec] after each. */
    fun newExercise(exerciseId: Long, restSec: Int?, nextKey: () -> Long): DraftExercise =
        DraftExercise(nextKey(), exerciseId, sets = List(3) { DraftSet(nextKey(), rest = restSec?.toString() ?: "") })
}
