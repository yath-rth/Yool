package com.yatharth.yool.io.strong

import com.yatharth.yool.data.ImportedWorkout
import com.yatharth.yool.data.ImportedWorkoutExercise
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.Workout
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.data.model.WorkoutSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full Strong workouts (every set, rest timers, notes) plus inferred exercise definitions. */
data class StrongWorkoutsResult(
    val workouts: List<ImportedWorkout>,
    /** Exercise name → definition inferred from its name and the data logged for it. */
    val exercises: Map<String, Exercise>,
)

/**
 * Parses a Strong CSV export into complete workouts. Strong writes one row per set, plus:
 * `Rest Timer` rows (rest after the preceding set, in Seconds), `Note` rows (exercise note), and set
 * orders `W` (warm-up), `D` (drop), `F` (failure).
 */
object StrongWorkouts {
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val HM = Regex("""(?:(\d+)\s*h)?\s*(?:(\d+)\s*m(?:in)?)?\s*(?:(\d+)\s*s)?""")

    fun parse(text: String, defaultUnit: WeightUnit = WeightUnit.KG, zone: ZoneId = ZoneId.systemDefault()): StrongWorkoutsResult {
        val delimiter = Csv.detectDelimiter(text.removePrefix("﻿"))
        val rows = Csv.parse(text, delimiter)
        if (rows.isEmpty()) throw StrongImport.FormatException("The file is empty.")
        val header = rows[0].map { it.trim().lowercase() }
        fun col(vararg names: String): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        fun colStarting(prefix: String, except: String? = null): Int =
            header.indexOfFirst { it.startsWith(prefix) && it != except }

        val numCol = col("workout #")
        val dateCol = col("date")
        val nameCol = col("workout name")
        val durCol = col("duration (sec)", "duration")
        val exCol = col("exercise name", "exercise")
        val orderCol = col("set order")
        val weightCol = col("weight").let { if (it >= 0) it else colStarting("weight", except = "weight unit") }
        val unitCol = col("weight unit")
        val repsCol = col("reps")
        val rpeCol = col("rpe")
        val distCol = col("distance (meters)", "distance").let { if (it >= 0) it else colStarting("distance", except = "distance unit") }
        val distUnitCol = col("distance unit")
        val secCol = col("seconds")
        val notesCol = col("notes")
        val workoutNotesCol = col("workout notes")
        if (dateCol < 0 || exCol < 0) {
            throw StrongImport.FormatException("This doesn't look like a Strong export (needs Date and Exercise Name columns).")
        }
        val headerUnit = when {
            weightCol < 0 -> null
            header[weightCol].contains("lb") -> WeightUnit.LB
            header[weightCol].contains("kg") -> WeightUnit.KG
            else -> null
        }

        fun cell(r: List<String>, i: Int): String = if (i >= 0 && i in r.indices) r[i].trim() else ""
        fun num(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

        data class SetAcc(var set: WorkoutSet)
        class ExAcc(val name: String) {
            val sets = mutableListOf<SetAcc>()
            var note: String? = null
        }
        class WAcc(val key: String, val startedAt: Long, val name: String, val durationSec: Long?, val note: String?) {
            val exercises = mutableListOf<ExAcc>()
        }

        val workouts = LinkedHashMap<String, WAcc>()
        // Per exercise name: (has distance, has seconds w/o reps, ever weighted).
        val shape = HashMap<String, BooleanArray>()

        for (r in rows.drop(1)) {
            val exercise = cell(r, exCol)
            val dateText = cell(r, dateCol)
            if (exercise.isEmpty() || dateText.isEmpty()) continue
            val start = runCatching { LocalDateTime.parse(dateText.take(19), DATE_TIME) }.getOrNull()
                ?: runCatching { java.time.LocalDate.parse(dateText.take(10)).atTime(12, 0) }.getOrNull()
                ?: continue
            val key = cell(r, numCol).ifEmpty { dateText }
            val w = workouts.getOrPut(key) {
                WAcc(
                    key, start.atZone(zone).toInstant().toEpochMilli(),
                    cell(r, nameCol).ifEmpty { "Workout" }, parseDuration(cell(r, durCol)),
                    cell(r, workoutNotesCol).ifEmpty { null },
                )
            }
            // Consecutive rows of the same exercise form one exercise block.
            val ex = w.exercises.lastOrNull()?.takeIf { it.name == exercise } ?: ExAcc(exercise).also { w.exercises += it }
            val order = cell(r, orderCol)
            when {
                order.equals("Rest Timer", true) -> {
                    val rest = num(cell(r, secCol))?.toInt()
                    ex.sets.lastOrNull()?.let { if (rest != null && rest > 0) it.set = it.set.copy(restSec = rest) }
                }
                order.equals("Note", true) -> ex.note = listOfNotNull(ex.note, cell(r, notesCol).ifEmpty { null }).joinToString("\n").ifEmpty { null }
                else -> {
                    val type = when (order.uppercase()) {
                        "W" -> SetType.WARMUP
                        "D" -> SetType.DROP
                        "F" -> SetType.FAILURE
                        else -> SetType.NORMAL
                    }
                    val unit = when (cell(r, unitCol).lowercase()) {
                        "kg", "kgs" -> WeightUnit.KG
                        "lb", "lbs" -> WeightUnit.LB
                        else -> headerUnit ?: defaultUnit
                    }
                    val weight = num(cell(r, weightCol))?.takeIf { it > 0 }?.let { StrongImport.toKg(it, unit) }
                    val reps = num(cell(r, repsCol))?.toInt()?.takeIf { it > 0 }
                    var dist = num(cell(r, distCol))?.takeIf { it > 0 }
                    when (cell(r, distUnitCol).lowercase()) {
                        "km" -> dist = dist?.times(1000)
                        "mi", "miles" -> dist = dist?.times(1609.344)
                    }
                    val secs = num(cell(r, secCol))?.toInt()?.takeIf { it > 0 }
                    val rpe = num(cell(r, rpeCol))?.takeIf { it > 0 }
                    val s = shape.getOrPut(exercise) { BooleanArray(3) }
                    if (dist != null) s[0] = true
                    if (secs != null && reps == null) s[1] = true
                    if (weight != null) s[2] = true
                    ex.sets += SetAcc(
                        WorkoutSet(
                            workoutExerciseId = 0, position = ex.sets.size, type = type,
                            weightKg = weight, reps = reps, durationSec = secs, distanceM = dist, rpe = rpe,
                            completed = true,
                        ),
                    )
                    val note = cell(r, notesCol)
                    if (note.isNotEmpty() && ex.note?.contains(note) != true) ex.note = listOfNotNull(ex.note, note).joinToString("\n")
                }
            }
        }

        val result = workouts.values.map { w ->
            val endedAt = w.startedAt + (w.durationSec ?: 0L) * 1000
            ImportedWorkout(
                Workout(
                    name = w.name, date = java.time.Instant.ofEpochMilli(w.startedAt).atZone(zone).toLocalDate(),
                    startedAt = w.startedAt, endedAt = endedAt, note = w.note, source = WorkoutSource.STRONG_IMPORT,
                ),
                w.exercises.filter { it.sets.isNotEmpty() }.map { e -> ImportedWorkoutExercise(e.name, e.sets.map { it.set }, e.note) },
            )
        }.filter { it.exercises.isNotEmpty() }.sortedBy { it.workout.startedAt }

        // Default rest per exercise: the most common rest in its latest session that has rest timers.
        val restByExercise = HashMap<String, Int>()
        for (w in result.asReversed()) for (e in w.exercises) {
            if (e.name in restByExercise) continue
            e.sets.mapNotNull { it.restSec }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.let { restByExercise[e.name] = it.key }
        }
        val exercises = shape.mapValues { (name, s) ->
            Exercise(
                name = name,
                type = typeFor(name, hasDistance = s[0], secondsOnly = s[1], weighted = s[2]),
                bodyPart = bodyPartFor(name),
                restSec = restByExercise[name],
                source = WorkoutSource.STRONG_IMPORT,
            )
        }
        return StrongWorkoutsResult(result, exercises)
    }

    /** "3389" seconds, or Strong's older "1h 2m" form. */
    fun parseDuration(s: String): Long? {
        if (s.isBlank()) return null
        s.replace(',', '.').toDoubleOrNull()?.let { return it.toLong() }
        val m = HM.matchEntire(s.trim()) ?: return null
        val (h, min, sec) = m.destructured
        val total = (h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (sec.toLongOrNull() ?: 0)
        return total.takeIf { it > 0 }
    }

    fun typeFor(name: String, hasDistance: Boolean, secondsOnly: Boolean, weighted: Boolean): ExerciseType {
        val n = name.lowercase()
        return when {
            hasDistance || listOf("running", "cycling", "elliptical", "rowing machine", "walk").any { it in n } -> ExerciseType.CARDIO
            secondsOnly -> ExerciseType.DURATION
            !weighted -> if (listOf("plank", "hold").any { it in n }) ExerciseType.DURATION else ExerciseType.REPS_ONLY
            "smith machine" in n -> ExerciseType.MACHINE
            "barbell" in n || "barbel " in n || n.startsWith("barbel") -> ExerciseType.BARBELL
            "dumbbell" in n || "kettlebell" in n -> ExerciseType.DUMBBELL
            listOf("machine", "cable", "pulldown", "leg press", "pec deck", "chest fly", "row").any { it in n } -> ExerciseType.MACHINE
            else -> ExerciseType.WEIGHTED_BODYWEIGHT
        }
    }

    /** Keyword body-part guess; order matters (e.g. "leg curl" before "curl", "reverse fly" before "fly"). */
    fun bodyPartFor(name: String): BodyPart {
        val n = name.lowercase()
        fun has(vararg k: String) = k.any { it in n }
        return when {
            has("neck") -> BodyPart.NECK
            has("running", "cycling", "elliptical", "treadmill") -> BodyPart.CARDIO
            has("wrist", "pinch", "farmer", "briefcase", "reverse curl", "zercher hold", "hold") -> BodyPart.FOREARMS
            has("crunch", "knee raise", "plank", "sit up", "sit-up", "leg raise", "ab ") -> BodyPart.CORE
            has("leg curl", "leg extension", "leg press", "squat", "lunge", "deadlift", "calf", "hip thrust", "split squat", "glute") -> BodyPart.LEGS
            has("reverse fly", "face pull", "lateral raise", "overhead press", "shoulder press", "arnold", "shrug", "front raise", "upright row") -> BodyPart.SHOULDERS
            has("curl") -> BodyPart.BICEPS
            has("tricep", "skullcrusher", "pushdown", "dip") -> BodyPart.TRICEPS
            has("bench", "chest", "fly", "pec deck", "push up", "push-up") -> BodyPart.CHEST
            has("row", "pulldown", "chin up", "pull up", "pull-up", "chin-up", "pullover") -> BodyPart.BACK
            else -> BodyPart.OTHER
        }
    }

    /** Templates seeded from history after the first import. */
    val SeedTemplateNames = listOf("Push", "Pull", "Legs")
}
