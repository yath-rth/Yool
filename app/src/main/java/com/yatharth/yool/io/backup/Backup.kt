package com.yatharth.yool.io.backup

import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.Food
import com.yatharth.yool.data.db.FoodLogEntry
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.FoodSource
import com.yatharth.yool.data.model.Meal
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.data.ImportedTemplate
import com.yatharth.yool.data.ImportedWorkout
import com.yatharth.yool.data.ImportedWorkoutExercise
import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.Workout
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.data.model.WorkoutSource
import com.yatharth.yool.domain.ExerciseSpec
import com.yatharth.yool.domain.SetSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** Versioned JSON snapshot of all tables. Schema documented in docs/backup-format.md. */
@Serializable
data class BackupFile(
    val formatVersion: Int = Backup.FORMAT_VERSION,
    val exportedAt: String,
    val entries: List<EntryDto> = emptyList(),
    val imported: List<ImportedDto> = emptyList(),
    val lifts: List<LiftDto> = emptyList(),
    val targets: List<TargetDto> = emptyList(),
    val foods: List<FoodDto> = emptyList(),
    val foodLog: List<FoodLogDto> = emptyList(),
    val savedMeals: List<SavedMealDto> = emptyList(),
    val exercises: List<ExerciseDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val workouts: List<WorkoutDto> = emptyList(),
)

@Serializable
data class ExerciseDto(
    val name: String,
    val type: String,
    val bodyPart: String,
    val restSec: Int? = null,
    val note: String? = null,
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val source: String = "USER",
)

/** A set in a template or workout. `completed`/`rpe` are only meaningful for workouts. */
@Serializable
data class SetDto(
    val type: String = "NORMAL",
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val rpe: Double? = null,
    val restSec: Int? = null,
    val completed: Boolean = true,
    val completedAt: Long? = null,
)

/** An exercise inside a template or workout; [exercise] is the exercise name. */
@Serializable
data class ExerciseEntryDto(
    val exercise: String,
    val supersetGroup: Int? = null,
    val note: String? = null,
    val sets: List<SetDto> = emptyList(),
)

@Serializable
data class TemplateDto(
    val name: String,
    val folder: String? = null,
    val note: String? = null,
    val exercises: List<ExerciseEntryDto> = emptyList(),
)

/** Times are epoch millis. Only finished workouts are backed up. */
@Serializable
data class WorkoutDto(
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val note: String? = null,
    val source: String = "USER",
    val exercises: List<ExerciseEntryDto> = emptyList(),
)

/** Nutrients per serving. [id] is only a reference key within this file. */
@Serializable
data class FoodDto(
    val id: Long,
    val name: String,
    val brand: String? = null,
    val servingLabel: String,
    val servingGrams: Double? = null,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val source: String = "USER",
)

/** Nutrients are entry totals (already × servings). */
@Serializable
data class FoodLogDto(
    val date: String,
    val meal: String,
    val foodId: Long? = null,
    val name: String,
    val servingLabel: String = "",
    val servings: Double = 1.0,
    val kcal: Double,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val createdAt: Long = 0,
)

@Serializable
data class SavedMealDto(val name: String, val items: List<SavedMealItemDto> = emptyList())

@Serializable
data class SavedMealItemDto(val foodId: Long, val servings: Double)

@Serializable
data class EntryDto(
    val date: String,
    val weightKg: Double? = null,
    val calories: Int? = null,
    val proteinG: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val workoutDone: Boolean? = null,
    val binge: Boolean? = null,
    val bingeTrigger: String? = null,
    val bingeNote: String? = null,
    val mood: Int? = null,
    val note: String? = null,
    val updatedAt: Long = 0,
)

@Serializable
data class ImportedDto(
    val date: String,
    val metric: String,
    val source: String,
    val value: Double,
    val importedAt: Long = 0,
)

@Serializable
data class LiftDto(val date: String, val exercise: String, val weightKg: Double, val reps: Int)

@Serializable
data class TargetDto(
    val metric: String,
    val value: Double,
    val effectiveFrom: String,
    val targetDate: String? = null,
)

/** Domain-side contents of a backup. */
data class BackupData(
    val entries: List<DailyEntry>,
    val imported: List<ImportedValue>,
    val lifts: List<LiftBestSet>,
    val targets: List<TargetEntry>,
    val foods: List<Food> = emptyList(),
    val foodLog: List<FoodLogEntry> = emptyList(),
    /** name → (backup food id, servings). */
    val savedMeals: List<Pair<String, List<Pair<Long, Double>>>> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val templates: List<ImportedTemplate> = emptyList(),
    val workouts: List<ImportedWorkout> = emptyList(),
)

class BackupException(message: String) : Exception(message)

object Backup {
    const val FORMAT_VERSION = 3

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(data: BackupData, exportedAt: Instant = Instant.now()): String {
        val file = BackupFile(
            exportedAt = exportedAt.toString(),
            entries = data.entries.sortedBy { it.date }.map {
                EntryDto(
                    it.date.toString(), it.weightKg, it.calories, it.proteinG, it.steps, it.sleepHours,
                    it.workoutDone, it.binge, it.bingeTrigger, it.bingeNote, it.mood, it.note, it.updatedAt,
                )
            },
            imported = data.imported.sortedWith(compareBy({ it.date }, { it.metric }, { it.source })).map {
                ImportedDto(it.date.toString(), it.metric.name, it.source.name, it.value, it.importedAt)
            },
            lifts = data.lifts.sortedWith(compareBy({ it.date }, { it.exercise })).map {
                LiftDto(it.date.toString(), it.exercise, it.weightKg, it.reps)
            },
            targets = data.targets.sortedWith(compareBy({ it.metric }, { it.effectiveFrom })).map {
                TargetDto(it.metric.name, it.value, it.effectiveFrom.toString(), it.targetDate?.toString())
            },
            foods = data.foods.sortedBy { it.id }.map {
                FoodDto(
                    it.id, it.name, it.brand, it.servingLabel, it.servingGrams, it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG,
                    it.archived, it.createdAt, it.source.name,
                )
            },
            foodLog = data.foodLog.sortedWith(compareBy({ it.date }, { it.meal }, { it.createdAt })).map {
                FoodLogDto(
                    it.date.toString(), it.meal.name, it.foodId, it.nameSnapshot, it.servingLabelSnapshot, it.servings,
                    it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG, it.createdAt,
                )
            },
            savedMeals = data.savedMeals.map { (name, items) -> SavedMealDto(name, items.map { SavedMealItemDto(it.first, it.second) }) },
            exercises = data.exercises.sortedBy { it.name.lowercase() }.map {
                ExerciseDto(it.name, it.type.name, it.bodyPart.name, it.restSec, it.note, it.archived, it.createdAt, it.source.name)
            },
            templates = data.templates.map { t ->
                TemplateDto(t.name, t.folder, t.note, t.exercises.map { (name, spec) ->
                    ExerciseEntryDto(name, spec.supersetGroup, spec.note, spec.sets.map {
                        SetDto(it.type.name, it.weightKg, it.reps, it.durationSec, it.distanceM, null, it.restSec)
                    })
                })
            },
            workouts = data.workouts.filter { it.workout.endedAt != null }.sortedBy { it.workout.startedAt }.map { w ->
                WorkoutDto(w.workout.name, w.workout.startedAt, w.workout.endedAt!!, w.workout.note, w.workout.source.name, w.exercises.map { e ->
                    ExerciseEntryDto(e.name, e.supersetGroup, e.note, e.sets.sortedBy { it.position }.map {
                        SetDto(it.type.name, it.weightKg, it.reps, it.durationSec, it.distanceM, it.rpe, it.restSec, it.completed, it.completedAt)
                    })
                })
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    fun decode(text: String): BackupData {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text.removePrefix("﻿"))
        } catch (e: Exception) {
            throw BackupException("Not a valid yool backup file (${e.message?.lineSequence()?.firstOrNull() ?: "parse error"}).")
        }
        if (file.formatVersion > FORMAT_VERSION) {
            throw BackupException("Backup format ${file.formatVersion} is newer than this app understands ($FORMAT_VERSION).")
        }
        try {
            return BackupData(
                entries = file.entries.map {
                    DailyEntry(
                        LocalDate.parse(it.date), it.weightKg, it.calories, it.proteinG, it.steps, it.sleepHours,
                        it.workoutDone, it.binge, it.bingeTrigger, it.bingeNote, it.mood, it.note, it.updatedAt,
                    )
                },
                imported = file.imported.map {
                    ImportedValue(LocalDate.parse(it.date), Metric.valueOf(it.metric), Source.valueOf(it.source), it.value, it.importedAt)
                },
                lifts = file.lifts.map { LiftBestSet(LocalDate.parse(it.date), it.exercise, it.weightKg, it.reps) },
                targets = file.targets.map {
                    TargetEntry(
                        metric = TargetMetric.valueOf(it.metric),
                        value = it.value,
                        effectiveFrom = LocalDate.parse(it.effectiveFrom),
                        targetDate = it.targetDate?.let(LocalDate::parse),
                    )
                },
                foods = file.foods.map {
                    Food(
                        it.id, it.name, it.brand, it.servingLabel, it.servingGrams, it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG,
                        it.archived, it.createdAt, FoodSource.valueOf(it.source),
                    )
                },
                foodLog = file.foodLog.map {
                    FoodLogEntry(
                        date = LocalDate.parse(it.date), meal = Meal.valueOf(it.meal), foodId = it.foodId,
                        nameSnapshot = it.name, servingLabelSnapshot = it.servingLabel, servings = it.servings,
                        kcal = it.kcal, proteinG = it.proteinG, carbsG = it.carbsG, fatG = it.fatG, fiberG = it.fiberG, createdAt = it.createdAt,
                    )
                },
                savedMeals = file.savedMeals.map { m -> m.name to m.items.map { it.foodId to it.servings } },
                exercises = file.exercises.map {
                    Exercise(
                        name = it.name, type = ExerciseType.valueOf(it.type), bodyPart = BodyPart.valueOf(it.bodyPart), restSec = it.restSec,
                        note = it.note, archived = it.archived, createdAt = it.createdAt, source = WorkoutSource.valueOf(it.source),
                    )
                },
                templates = file.templates.map { t ->
                    ImportedTemplate(t.name, t.folder, t.note, t.exercises.map { e ->
                        e.exercise to ExerciseSpec(
                            0,
                            e.sets.map { SetSpec(SetType.valueOf(it.type), it.weightKg, it.reps, it.durationSec, it.distanceM, it.restSec) },
                            e.supersetGroup, e.note,
                        )
                    })
                },
                workouts = file.workouts.map { w ->
                    ImportedWorkout(
                        // date is recomputed from startedAt in the local zone on import.
                        Workout(name = w.name, date = LocalDate.EPOCH, startedAt = w.startedAt, endedAt = w.endedAt, note = w.note, source = WorkoutSource.valueOf(w.source)),
                        w.exercises.map { e ->
                            ImportedWorkoutExercise(e.exercise, e.sets.mapIndexed { i, s ->
                                WorkoutSet(
                                    workoutExerciseId = 0, position = i, type = SetType.valueOf(s.type), weightKg = s.weightKg, reps = s.reps,
                                    durationSec = s.durationSec, distanceM = s.distanceM, rpe = s.rpe, restSec = s.restSec,
                                    completed = s.completed, completedAt = s.completedAt,
                                )
                            }, e.note, e.supersetGroup)
                        },
                    )
                },
            )
        } catch (e: IllegalArgumentException) {
            throw BackupException("Backup contains an invalid value: ${e.message}")
        } catch (e: java.time.format.DateTimeParseException) {
            throw BackupException("Backup contains an invalid date: ${e.parsedString}")
        }
    }
}
