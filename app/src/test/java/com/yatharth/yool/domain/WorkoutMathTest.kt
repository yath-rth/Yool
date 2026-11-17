package com.yatharth.yool.domain

import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.ExerciseSetRow
import com.yatharth.yool.data.db.LoggedBestSet
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.db.TemplateExercise
import com.yatharth.yool.data.db.TemplateExerciseFull
import com.yatharth.yool.data.db.TemplateFull
import com.yatharth.yool.data.db.TemplateSet
import com.yatharth.yool.data.db.Workout
import com.yatharth.yool.data.db.WorkoutDayCount
import com.yatharth.yool.data.db.WorkoutExercise
import com.yatharth.yool.data.db.WorkoutExerciseFull
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.db.WorkoutTemplate
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutMathTest {
    private val d = LocalDate.of(2026, 1, 10)
    private val bench = Exercise(1, "Bench", ExerciseType.BARBELL, BodyPart.CHEST)

    private fun row(workoutId: Long, w: Double?, reps: Int?, type: SetType = SetType.NORMAL, pos: Int = 0, started: Long = workoutId) =
        ExerciseSetRow(workoutId, "W", d.plusDays(workoutId), started, workoutId, 1, pos, type, w, reps, null, null, null)

    private fun set(pos: Int, w: Double?, reps: Int?, type: SetType = SetType.NORMAL, done: Boolean = true) =
        WorkoutSet(id = pos.toLong() + 1, workoutExerciseId = 1, position = pos, type = type, weightKg = w, reps = reps, completed = done)

    private fun workout(sets: List<WorkoutSet>, started: Long = 100, ended: Long? = 200) = WorkoutFull(
        Workout(1, "Push", d, started, ended),
        listOf(WorkoutExerciseFull(WorkoutExercise(1, 1, 1, 0), bench, sets)),
    )

    @Test fun epley() {
        assertEquals(100.0, WorkoutMath.oneRepMax(100.0, 1)!!, 1e-9)
        assertEquals(120.0, WorkoutMath.oneRepMax(100.0, 6)!!, 1e-9)
        assertNull(WorkoutMath.oneRepMax(null, 5))
        assertNull(WorkoutMath.oneRepMax(50.0, 0))
    }

    @Test fun volumeSkipsWarmupsAndIncomplete() {
        val w = workout(listOf(set(0, 20.0, 10, SetType.WARMUP), set(1, 50.0, 5), set(2, 40.0, 8, SetType.DROP), set(3, 60.0, 5, done = false)))
        assertEquals(250.0 + 320.0, WorkoutMath.volume(w), 1e-9)
        assertEquals(3, WorkoutMath.completedSets(w))
    }

    @Test fun previousMatchesWarmupsSeparately() {
        val prev = listOf(row(1, 20.0, 10, SetType.WARMUP, 0), row(1, 50.0, 5, pos = 1), row(1, 52.5, 4, pos = 2))
        val current = listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL, SetType.NORMAL)
        assertEquals(20.0, WorkoutMath.previousFor(0, current, prev)!!.weightKg!!, 0.0)
        assertEquals(50.0, WorkoutMath.previousFor(1, current, prev)!!.weightKg!!, 0.0)
        assertEquals(52.5, WorkoutMath.previousFor(2, current, prev)!!.weightKg!!, 0.0)
        assertNull(WorkoutMath.previousFor(3, current, prev))
        // Current has no warm-up: first working set still matches previous first working set.
        assertEquals(50.0, WorkoutMath.previousFor(0, listOf(SetType.NORMAL), prev)!!.weightKg!!, 0.0)
    }

    @Test fun recordsAndRepMaxes() {
        val rows = listOf(row(1, 100.0, 3), row(1, 80.0, 10), row(2, 20.0, 15, SetType.WARMUP), row(2, 90.0, 6))
        val r = WorkoutMath.records(rows)
        assertEquals(100.0, r.heaviestKg!!, 0.0)
        assertEquals(100.0 * (1 + 3 / 30.0), r.best1RmKg!!, 1e-9)
        assertEquals(800.0, r.bestSetVolumeKg!!, 0.0)
        assertEquals(1100.0, r.bestSessionVolumeKg!!, 0.0)
        assertEquals(100.0, r.repMaxes[3]!!, 0.0)
        assertEquals(90.0, r.repMaxes[6]!!, 0.0)
        assertEquals(80.0, r.repMaxes[10]!!, 0.0)
        assertNull(r.repMaxes[11])
        assertEquals(2, r.sessions)
    }

    @Test fun prsOnlyFlagBestSetPerKindAndNeedHistory() {
        val history = mapOf(1L to listOf(row(1, 50.0, 5, started = 10)))
        val w = workout(listOf(set(0, 52.5, 5), set(1, 55.0, 3), set(2, 20.0, 10, SetType.WARMUP)))
        val prs = WorkoutMath.workoutPrs(w, history)
        // Set 0: 1RM 61.25 & volume 262.5 beat history; set 1 beats weight (55) and 1RM (60.5 < 61.25 -> no).
        val bySet = prs.associate { it.second to it.third }
        assertEquals(setOf(PrKind.ONE_RM, PrKind.VOLUME), bySet[0])
        assertEquals(setOf(PrKind.WEIGHT), bySet[1])
        assertTrue(WorkoutMath.workoutPrs(w, emptyMap()).isEmpty())
    }

    @Test fun laterSetSupersedesEarlierPrOfSameKind() {
        val history = mapOf(1L to listOf(row(1, 50.0, 5, started = 10)))
        val prs = WorkoutMath.workoutPrs(workout(listOf(set(0, 52.5, 5), set(1, 55.0, 5))), history)
        assertEquals(1, prs.size)
        assertEquals(1, prs[0].second)
        assertEquals(setOf(PrKind.WEIGHT, PrKind.ONE_RM, PrKind.VOLUME), prs[0].third)
    }

    @Test fun bestPerDayAndMerge() {
        val logged = WorkoutMath.bestPerDay(
            listOf(LoggedBestSet(d, "Bench", 50.0, 5), LoggedBestSet(d, "Bench", 50.0, 7), LoggedBestSet(d, "Bench", 45.0, 10)),
        )
        assertEquals(listOf(LiftBestSet(d, "Bench", 50.0, 7)), logged)
        val merged = WorkoutMath.mergeLifts(listOf(LiftBestSet(d, "Bench", 99.0, 1), LiftBestSet(d.minusDays(1), "Bench", 40.0, 5)), logged)
        assertEquals(listOf(40.0, 50.0), merged.map { it.weightKg })
    }

    @Test fun workoutDaysBecomeWorkoutLogSource() {
        val v = WorkoutMath.asImported(listOf(WorkoutDayCount(d, 2), WorkoutDayCount(d.plusDays(1), 0))).single()
        assertEquals(Metric.WORKOUT, v.metric)
        assertEquals(Source.WORKOUT_LOG, v.source)
        assertTrue(Source.WORKOUT_LOG.priority < Source.STRONG_CSV.priority)
        assertTrue(Source.MANUAL.priority < Source.WORKOUT_LOG.priority)
    }

    @Test fun structureDiff() {
        val t = TemplateFull(
            WorkoutTemplate(1, "Push"),
            listOf(TemplateExerciseFull(TemplateExercise(1, 1, 1, 0), bench, listOf(TemplateSet(1, 1, 0), TemplateSet(2, 1, 1)))),
        )
        assertFalse(WorkoutMath.structureChanged(t, workout(listOf(set(0, 50.0, 5), set(1, 60.0, 3)))))
        assertTrue(WorkoutMath.structureChanged(t, workout(listOf(set(0, 50.0, 5)))))
        assertTrue(WorkoutMath.structureChanged(t, workout(listOf(set(0, 50.0, 5, SetType.WARMUP), set(1, 60.0, 3)))))
    }

    @Test fun specsFromWorkoutDropIncomplete() {
        val specs = WorkoutMath.specsOf(workout(listOf(set(0, 50.0, 5), set(1, 60.0, 3, done = false))), completedOnly = true)
        assertEquals(1, specs.single().sets.size)
    }

    @Test fun plates() {
        val (plates, left) = WorkoutMath.platesPerSide(100.0, 20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25))
        assertEquals(listOf(25.0, 15.0), plates)
        assertEquals(0.0, left, 0.0)
        val (p2, left2) = WorkoutMath.platesPerSide(61.0, 20.0, listOf(20.0, 10.0, 5.0, 2.5))
        assertEquals(listOf(20.0), p2)
        assertEquals(0.5, left2, 1e-9)
        assertTrue(WorkoutMath.platesPerSide(15.0, 20.0, listOf(5.0)).first.isEmpty())
    }

    @Test fun warmups() {
        val w = WorkoutMath.warmupSets(100.0, 20.0)
        assertEquals(listOf(20.0, 50.0, 70.0, 85.0), w.map { it.weightKg })
        assertEquals(listOf(10, 5, 3, 2), w.map { it.reps })
        assertTrue(w.all { it.type == SetType.WARMUP })
        assertTrue(WorkoutMath.warmupSets(20.0, 20.0).isEmpty())
        assertEquals(listOf(20.0), WorkoutMath.warmupSets(25.0, 20.0).map { it.weightKg })
    }

    @Test fun formatting() {
        assertEquals("1h 05m", WorkoutMath.formatDuration(3_900_000))
        assertEquals("42m", WorkoutMath.formatDuration(42 * 60_000L))
        assertEquals("1:30", WorkoutMath.formatClock(90))
        assertEquals("52.5", WorkoutMath.kg(52.5))
        assertEquals("50", WorkoutMath.kg(50.0))
        assertEquals("Morning Workout", WorkoutMath.defaultName(7))
    }

    @Test fun exerciseLinesCountCompletedSetsOnly() {
        val w = workout(listOf(set(0, 60.0, 5), set(1, 60.0, 5), set(2, 60.0, 5, done = false)))
        assertEquals(listOf("2 × ${bench.name}"), WorkoutMath.exerciseLines(w))
        assertEquals(emptyList<String>(), WorkoutMath.exerciseLines(workout(listOf(set(0, 60.0, 5, done = false)))))
    }
}
