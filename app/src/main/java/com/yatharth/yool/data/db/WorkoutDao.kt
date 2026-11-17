package com.yatharth.yool.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WorkoutDao {
    // ── Exercises ────────────────────────────────────────────────────────────

    /** Active exercises, most performed first, then alphabetical. */
    @Query(
        """SELECT e.* FROM exercise e
           LEFT JOIN (SELECT exerciseId, COUNT(*) AS c FROM workout_exercise GROUP BY exerciseId) u ON u.exerciseId = e.id
           WHERE e.archived = 0 OR :includeArchived
           ORDER BY e.archived, IFNULL(u.c, 0) DESC, e.name COLLATE NOCASE""",
    )
    fun observeExercises(includeArchived: Boolean): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observeExercise(id: Long): Flow<Exercise?>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getExercise(id: Long): Exercise?

    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findExercise(name: String): Exercise?

    @Query("SELECT * FROM exercise ORDER BY id")
    suspend fun allExercises(): List<Exercise>

    @Insert suspend fun insertExercise(e: Exercise): Long
    @Update suspend fun updateExercise(e: Exercise)
    @Delete suspend fun deleteExercise(e: Exercise)

    @Query(
        """SELECT (SELECT COUNT(*) FROM workout_exercise WHERE exerciseId = :id) +
                  (SELECT COUNT(*) FROM template_exercise WHERE exerciseId = :id)""",
    )
    suspend fun exerciseUseCount(id: Long): Int

    // ── Folders & templates ──────────────────────────────────────────────────

    @Query("SELECT * FROM template_folder ORDER BY position, id")
    fun observeFolders(): Flow<List<TemplateFolder>>

    @Query("SELECT * FROM template_folder ORDER BY position, id")
    suspend fun allFolders(): List<TemplateFolder>

    @Insert suspend fun insertFolder(f: TemplateFolder): Long
    @Update suspend fun updateFolder(f: TemplateFolder)

    @Query("DELETE FROM template_folder WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Transaction
    @Query("SELECT * FROM workout_template ORDER BY position, name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<TemplateFull>>

    @Transaction
    @Query("SELECT * FROM workout_template WHERE id = :id")
    fun observeTemplate(id: Long): Flow<TemplateFull?>

    @Transaction
    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun getTemplate(id: Long): TemplateFull?

    @Transaction
    @Query("SELECT * FROM workout_template ORDER BY position, id")
    suspend fun allTemplates(): List<TemplateFull>

    @Query("SELECT * FROM workout_template WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTemplate(name: String): WorkoutTemplate?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM workout_template")
    suspend fun nextTemplatePosition(): Int

    @Insert suspend fun insertTemplate(t: WorkoutTemplate): Long
    @Update suspend fun updateTemplate(t: WorkoutTemplate)

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("UPDATE workout_template SET lastUsedAt = :at WHERE id = :id")
    suspend fun markTemplateUsed(id: Long, at: Long)

    @Query("DELETE FROM template_exercise WHERE templateId = :templateId")
    suspend fun clearTemplateExercises(templateId: Long)

    @Insert suspend fun insertTemplateExercise(e: TemplateExercise): Long
    @Insert suspend fun insertTemplateSets(sets: List<TemplateSet>)

    // ── Workouts ─────────────────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM workout WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutFull?>

    @Query("SELECT * FROM workout WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): Workout?

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    fun observeWorkout(id: Long): Flow<WorkoutFull?>

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkoutFull(id: Long): WorkoutFull?

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkout(id: Long): Workout?

    /** Finished workouts, newest first. */
    @Transaction
    @Query("SELECT * FROM workout WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<WorkoutFull>>

    @Transaction
    @Query("SELECT * FROM workout WHERE endedAt IS NOT NULL AND date BETWEEN :from AND :to ORDER BY startedAt")
    fun observeWorkoutsBetween(from: LocalDate, to: LocalDate): Flow<List<WorkoutFull>>

    @Transaction
    @Query("SELECT * FROM workout WHERE endedAt IS NOT NULL ORDER BY startedAt")
    suspend fun allWorkouts(): List<WorkoutFull>

    @Query("SELECT MIN(date) FROM workout WHERE endedAt IS NOT NULL")
    suspend fun firstWorkoutDate(): LocalDate?

    @Query("SELECT startedAt FROM workout")
    suspend fun allStartTimes(): List<Long>

    @Query("SELECT date, COUNT(*) AS count FROM workout WHERE endedAt IS NOT NULL AND date BETWEEN :from AND :to GROUP BY date")
    fun observeDayCounts(from: LocalDate, to: LocalDate): Flow<List<WorkoutDayCount>>

    @Query("SELECT * FROM workout WHERE endedAt IS NOT NULL AND name = :name COLLATE NOCASE ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestWorkoutNamed(name: String): Workout?

    @Insert suspend fun insertWorkout(w: Workout): Long
    @Update suspend fun updateWorkout(w: Workout)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Insert suspend fun insertWorkoutExercise(e: WorkoutExercise): Long
    @Update suspend fun updateWorkoutExercise(e: WorkoutExercise)
    @Update suspend fun updateWorkoutExercises(e: List<WorkoutExercise>)

    @Query("SELECT * FROM workout_exercise WHERE id = :id")
    suspend fun getWorkoutExercise(id: Long): WorkoutExercise?

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY position")
    suspend fun workoutExercises(workoutId: Long): List<WorkoutExercise>

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Insert suspend fun insertSet(s: WorkoutSet): Long
    @Insert suspend fun insertSets(s: List<WorkoutSet>)
    @Update suspend fun updateSet(s: WorkoutSet)
    @Update suspend fun updateSets(s: List<WorkoutSet>)

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getSet(id: Long): WorkoutSet?

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY position")
    suspend fun sets(workoutExerciseId: Long): List<WorkoutSet>

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    // ── Exercise history ─────────────────────────────────────────────────────

    /** Every completed set of [exerciseId] in finished workouts, oldest first. */
    @Query(
        """SELECT w.id AS workoutId, w.name AS workoutName, w.date AS date, w.startedAt AS startedAt,
                  we.id AS workoutExerciseId, we.exerciseId AS exerciseId,
                  s.position AS position, s.type AS type, s.weightKg AS weightKg, s.reps AS reps,
                  s.durationSec AS durationSec, s.distanceM AS distanceM, s.rpe AS rpe
           FROM workout_set s
           JOIN workout_exercise we ON we.id = s.workoutExerciseId
           JOIN workout w ON w.id = we.workoutId
           WHERE we.exerciseId = :exerciseId AND w.endedAt IS NOT NULL AND s.completed = 1
           ORDER BY w.startedAt, we.position, s.position""",
    )
    fun observeExerciseSets(exerciseId: Long): Flow<List<ExerciseSetRow>>

    /**
     * Sets of the most recent finished workout before [beforeStartedAt] that included [exerciseId]
     * (Strong's "Previous" column). Only that one workout-exercise's sets, in order.
     */
    @Query(
        """SELECT w.id AS workoutId, w.name AS workoutName, w.date AS date, w.startedAt AS startedAt,
                  we.id AS workoutExerciseId, we.exerciseId AS exerciseId,
                  s.position AS position, s.type AS type, s.weightKg AS weightKg, s.reps AS reps,
                  s.durationSec AS durationSec, s.distanceM AS distanceM, s.rpe AS rpe
           FROM workout_set s
           JOIN workout_exercise we ON we.id = s.workoutExerciseId
           JOIN workout w ON w.id = we.workoutId
           WHERE we.id = (
               SELECT we2.id FROM workout_exercise we2 JOIN workout w2 ON w2.id = we2.workoutId
               WHERE we2.exerciseId = :exerciseId AND w2.endedAt IS NOT NULL AND w2.startedAt < :beforeStartedAt
               ORDER BY w2.startedAt DESC, we2.position LIMIT 1
           ) AND s.completed = 1
           ORDER BY s.position""",
    )
    suspend fun previousSets(exerciseId: Long, beforeStartedAt: Long): List<ExerciseSetRow>

    /** Completed working sets with weight and reps in finished workouts between [from] and [to]. */
    @Query(
        """SELECT w.date AS date, e.name AS exercise, s.weightKg AS weightKg, s.reps AS reps
           FROM workout_set s
           JOIN workout_exercise we ON we.id = s.workoutExerciseId
           JOIN workout w ON w.id = we.workoutId
           JOIN exercise e ON e.id = we.exerciseId
           WHERE w.endedAt IS NOT NULL AND w.date BETWEEN :from AND :to AND s.completed = 1
             AND s.type IN ('NORMAL', 'FAILURE') AND s.weightKg IS NOT NULL AND s.reps > 0""",
    )
    fun observeWorkingSets(from: LocalDate, to: LocalDate): Flow<List<LoggedBestSet>>

    @Query(
        """SELECT w.date AS date, e.name AS exercise, s.weightKg AS weightKg, s.reps AS reps
           FROM workout_set s
           JOIN workout_exercise we ON we.id = s.workoutExerciseId
           JOIN workout w ON w.id = we.workoutId
           JOIN exercise e ON e.id = we.exerciseId
           WHERE w.endedAt IS NOT NULL AND s.completed = 1
             AND s.type IN ('NORMAL', 'FAILURE') AND s.weightKg IS NOT NULL AND s.reps > 0""",
    )
    suspend fun allWorkingSets(): List<LoggedBestSet>

    /** Names of exercises with logged working sets, most frequently trained first. */
    @Query(
        """SELECT e.name FROM workout_exercise we JOIN exercise e ON e.id = we.exerciseId
           JOIN workout w ON w.id = we.workoutId WHERE w.endedAt IS NOT NULL
           GROUP BY e.id ORDER BY COUNT(*) DESC, e.name""",
    )
    fun observeLoggedExerciseNames(): Flow<List<String>>
}
