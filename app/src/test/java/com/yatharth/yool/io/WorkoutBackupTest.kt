package com.yatharth.yool.io

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
import com.yatharth.yool.io.backup.Backup
import com.yatharth.yool.io.backup.BackupData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutBackupTest {
    private val d = LocalDate.of(2026, 11, 17)

    private val data = BackupData(
        entries = emptyList(), imported = emptyList(), lifts = emptyList(), targets = emptyList(),
        exercises = listOf(Exercise(name = "Lat Pulldown (Cable)", type = ExerciseType.MACHINE, bodyPart = BodyPart.BACK, restSec = 180, note = "Thumbs over", source = WorkoutSource.STRONG_IMPORT)),
        templates = listOf(
            ImportedTemplate(
                "Pull", "PPL", "Heavy day",
                listOf("Lat Pulldown (Cable)" to ExerciseSpec(0, listOf(SetSpec(SetType.WARMUP, 30.0, 10, restSec = 60), SetSpec(weightKg = 50.0, reps = 6)), supersetGroup = 1)),
            ),
        ),
        workouts = listOf(
            ImportedWorkout(
                Workout(name = "Pull", date = d, startedAt = 1_000L, endedAt = 3_000L, note = "ok", source = WorkoutSource.USER),
                listOf(
                    ImportedWorkoutExercise(
                        "Lat Pulldown (Cable)",
                        listOf(
                            WorkoutSet(workoutExerciseId = 9, position = 0, type = SetType.NORMAL, weightKg = 49.5, reps = 4, rpe = 9.0, restSec = 180, completed = true, completedAt = 2_000L),
                            WorkoutSet(workoutExerciseId = 9, position = 1, type = SetType.DROP, weightKg = 40.0, reps = 6, completed = true),
                        ),
                        note = "grip",
                    ),
                ),
            ),
            // Active workouts are not backed up.
            ImportedWorkout(Workout(name = "Live", date = d, startedAt = 5_000L), emptyList()),
        ),
    )

    @Test fun roundTrip() {
        val back = Backup.decode(Backup.encode(data))
        assertEquals(data.exercises.single().copy(createdAt = data.exercises.single().createdAt), back.exercises.single())
        val t = back.templates.single()
        assertEquals("Pull", t.name)
        assertEquals("PPL", t.folder)
        assertEquals("Lat Pulldown (Cable)", t.exercises.single().first)
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL), t.exercises.single().second.sets.map { it.type })
        assertEquals(60, t.exercises.single().second.sets[0].restSec)
        assertEquals(1, t.exercises.single().second.supersetGroup)

        val w = back.workouts.single()
        assertEquals(1_000L, w.workout.startedAt)
        assertEquals(3_000L, w.workout.endedAt)
        assertEquals("ok", w.workout.note)
        val ex = w.exercises.single()
        assertEquals("grip", ex.note)
        assertEquals(listOf(49.5, 40.0), ex.sets.map { it.weightKg })
        assertEquals(9.0, ex.sets[0].rpe!!, 0.0)
        assertEquals(SetType.DROP, ex.sets[1].type)
        assertTrue(ex.sets.all { it.completed })
    }

    @Test fun v2FilesStillDecode() {
        val back = Backup.decode("""{"formatVersion":2,"exportedAt":"x"}""")
        assertTrue(back.workouts.isEmpty() && back.exercises.isEmpty() && back.templates.isEmpty())
    }
}
