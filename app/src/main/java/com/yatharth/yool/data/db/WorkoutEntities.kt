package com.yatharth.yool.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.data.model.WorkoutSource
import java.time.LocalDate

/** An exercise in the user's library. Referenced exercises are archived, never deleted. */
@Entity(tableName = "exercise", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val type: ExerciseType,
    val bodyPart: BodyPart,
    /** Default rest after each set; null = use the global default from settings. */
    val restSec: Int? = null,
    /** Sticky note shown every time the exercise is performed. */
    val note: String? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val source: WorkoutSource = WorkoutSource.USER,
)

@Entity(tableName = "template_folder")
data class TemplateFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
)

@Entity(
    tableName = "workout_template",
    foreignKeys = [ForeignKey(entity = TemplateFolder::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("folderId")],
)
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folderId: Long? = null,
    val note: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
)

@Entity(
    tableName = "template_exercise",
    foreignKeys = [
        ForeignKey(entity = WorkoutTemplate::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    /** Exercises sharing the same non-null group number form a superset. */
    val supersetGroup: Int? = null,
    val note: String? = null,
)

@Entity(
    tableName = "template_set",
    foreignKeys = [ForeignKey(entity = TemplateExercise::class, parentColumns = ["id"], childColumns = ["templateExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("templateExerciseId")],
)
data class TemplateSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateExerciseId: Long,
    val position: Int,
    val type: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    /** Rest after this set; null = exercise default, then global default. */
    val restSec: Int? = null,
)

/** A performed (or in-progress) workout. [endedAt] == null means it's the active workout (at most one). */
@Entity(
    tableName = "workout",
    foreignKeys = [ForeignKey(entity = WorkoutTemplate::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["startedAt"], unique = true), Index("date"), Index("templateId")],
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Local date of [startedAt]. */
    val date: LocalDate,
    /** Epoch millis. */
    val startedAt: Long,
    val endedAt: Long? = null,
    val templateId: Long? = null,
    val note: String? = null,
    val source: WorkoutSource = WorkoutSource.USER,
)

@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val supersetGroup: Int? = null,
    /** Note for this exercise in this workout only. */
    val note: String? = null,
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [ForeignKey(entity = WorkoutExercise::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val position: Int,
    val type: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    /** 6.0..10.0 in 0.5 steps. */
    val rpe: Double? = null,
    val restSec: Int? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

// ── Relations ────────────────────────────────────────────────────────────────

data class TemplateExerciseFull(
    @Embedded val entry: TemplateExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "templateExerciseId") val sets: List<TemplateSet>,
) {
    val orderedSets: List<TemplateSet> get() = sets.sortedBy { it.position }
}

data class TemplateFull(
    @Embedded val template: WorkoutTemplate,
    @Relation(entity = TemplateExercise::class, parentColumn = "id", entityColumn = "templateId")
    val exercises: List<TemplateExerciseFull>,
) {
    val ordered: List<TemplateExerciseFull> get() = exercises.sortedBy { it.entry.position }
}

data class WorkoutExerciseFull(
    @Embedded val entry: WorkoutExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id") val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId") val sets: List<WorkoutSet>,
) {
    val orderedSets: List<WorkoutSet> get() = sets.sortedBy { it.position }
}

data class WorkoutFull(
    @Embedded val workout: Workout,
    @Relation(entity = WorkoutExercise::class, parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<WorkoutExerciseFull>,
) {
    val ordered: List<WorkoutExerciseFull> get() = exercises.sortedBy { it.entry.position }
}

/** One set of an exercise with its workout's date/time (query result for history, records, previous). */
data class ExerciseSetRow(
    val workoutId: Long,
    val workoutName: String,
    val date: LocalDate,
    val startedAt: Long,
    val workoutExerciseId: Long,
    val exerciseId: Long,
    val position: Int,
    val type: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceM: Double?,
    val rpe: Double?,
)

/** Best completed working set per (date, exercise name), derived from the workout log. */
data class LoggedBestSet(val date: LocalDate, val exercise: String, val weightKg: Double, val reps: Int)

/** Finished-workout count per date. */
data class WorkoutDayCount(val date: LocalDate, val count: Int)
