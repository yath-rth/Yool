package com.yatharth.yool.io

import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.io.strong.StrongWorkouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneOffset

class StrongWorkoutsTest {
    private val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""

    private fun row(n: Int, date: String, name: String, ex: String, order: String, w: String = "", reps: String = "", rpe: String = "", dist: String = "", sec: String = "", notes: String = "", wnotes: String = "", dur: String = "3600") =
        listOf("$n", date, name, dur, ex, order, w, reps, rpe, dist, sec, notes, wnotes).joinToString(";") { "\"$it\"" }

    private val csv = listOf(
        header,
        row(1, "2024-01-02 18:00:00", "Push", "Bench Press (Dumbbell)", "W", "10.0", "10"),
        row(1, "2024-01-02 18:00:00", "Push", "Bench Press (Dumbbell)", "Rest Timer", sec = "60.0"),
        row(1, "2024-01-02 18:00:00", "Push", "Bench Press (Dumbbell)", "1", "20.0", "8", rpe = "8.5"),
        row(1, "2024-01-02 18:00:00", "Push", "Bench Press (Dumbbell)", "Rest Timer", sec = "180.0"),
        row(1, "2024-01-02 18:00:00", "Push", "Bench Press (Dumbbell)", "D", "15.0", "6"),
        row(1, "2024-01-02 18:00:00", "Push", "Bench Press (Dumbbell)", "Note", notes = "Felt strong"),
        row(1, "2024-01-02 18:00:00", "Push", "Running (Treadmill)", "1", dist = "1200.0", sec = "600.0"),
        row(2, "2024-01-04 07:30:00", "Legs", "Plank", "1", sec = "60.0", wnotes = "Short one", dur = "1200"),
    ).joinToString("\n")

    @Test fun parsesSetsRestNotesAndTypes() {
        val r = StrongWorkouts.parse(csv, zone = ZoneOffset.UTC)
        assertEquals(2, r.workouts.size)
        val push = r.workouts[0]
        assertEquals("Push", push.workout.name)
        assertEquals(push.workout.startedAt + 3_600_000, push.workout.endedAt)
        val bench = push.exercises[0]
        assertEquals("Bench Press (Dumbbell)", bench.name)
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.DROP), bench.sets.map { it.type })
        assertEquals(listOf(60, 180, null), bench.sets.map { it.restSec })
        assertEquals(8.5, bench.sets[1].rpe!!, 0.0)
        assertEquals("Felt strong", bench.note)
        assertTrue(bench.sets.all { it.completed })
        val run = push.exercises[1].sets.single()
        assertEquals(1200.0, run.distanceM!!, 0.0)
        assertEquals(600, run.durationSec)
        assertNull(run.weightKg)
        assertEquals("Short one", r.workouts[1].workout.note)
    }

    @Test fun infersExerciseDefinitions() {
        val r = StrongWorkouts.parse(csv, zone = ZoneOffset.UTC)
        assertEquals(ExerciseType.DUMBBELL, r.exercises["Bench Press (Dumbbell)"]!!.type)
        assertEquals(BodyPart.CHEST, r.exercises["Bench Press (Dumbbell)"]!!.bodyPart)
        assertEquals(60, r.exercises["Bench Press (Dumbbell)"]!!.restSec) // mode, ties -> first seen
        assertEquals(ExerciseType.CARDIO, r.exercises["Running (Treadmill)"]!!.type)
        assertEquals(ExerciseType.DURATION, r.exercises["Plank"]!!.type)
        assertEquals(BodyPart.CORE, r.exercises["Plank"]!!.bodyPart)
    }

    @Test fun bodyPartsForCommonNames() {
        assertEquals(BodyPart.LEGS, StrongWorkouts.bodyPartFor("Seated Leg Curl (Machine)"))
        assertEquals(BodyPart.SHOULDERS, StrongWorkouts.bodyPartFor("Reverse Fly (Machine)"))
        assertEquals(BodyPart.BICEPS, StrongWorkouts.bodyPartFor("Hammer Curl (Cable)"))
        assertEquals(BodyPart.FOREARMS, StrongWorkouts.bodyPartFor("Barbel Wrist Curl Overhand"))
        assertEquals(BodyPart.BACK, StrongWorkouts.bodyPartFor("Lat Pulldown (Cable)"))
        assertEquals(BodyPart.TRICEPS, StrongWorkouts.bodyPartFor("Triceps Pushdown (Cable - Straight Bar)"))
        assertEquals(BodyPart.NECK, StrongWorkouts.bodyPartFor("Neck Curl"))
    }

    @Test fun parsesDurations() {
        assertEquals(3389L, StrongWorkouts.parseDuration("3389"))
        assertEquals(3720L, StrongWorkouts.parseDuration("1h 2m"))
        assertEquals(2700L, StrongWorkouts.parseDuration("45m"))
        assertNull(StrongWorkouts.parseDuration(""))
    }

    /** Runs against the user's real export when it's present (git-ignored, local only). */
    @Test fun realExportIfPresent() {
        val f = listOf("../migration/strong/workout_data.csv", "migration/strong/workout_data.csv").map(::File).firstOrNull { it.exists() } ?: return
        val r = StrongWorkouts.parse(f.readText(), zone = ZoneOffset.UTC)
        assertEquals(493, r.workouts.size)
        assertEquals(91, r.exercises.size)
        val other = r.exercises.values.filter { it.bodyPart == BodyPart.OTHER }.map { it.name }
        assertTrue("Unclassified: $other", other.size <= 3)
    }
}
