package com.yatharth.yool.ui.workout

import com.yatharth.yool.data.db.Exercise
import com.yatharth.yool.data.db.Workout
import com.yatharth.yool.data.db.WorkoutExercise
import com.yatharth.yool.data.db.WorkoutExerciseFull
import com.yatharth.yool.data.db.WorkoutFull
import com.yatharth.yool.data.db.WorkoutSet
import com.yatharth.yool.data.model.BodyPart
import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LibraryLogicTest {
    private var k = 0L
    private fun key() = ++k
    private fun ex(id: Long, group: Int? = null) = DraftExercise(key(), id, group, sets = listOf(DraftSet(key())))

    @Test fun groupsByMonthKeepingOrder() {
        val dates = listOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 30), LocalDate.of(2025, 11, 2))
        val g = groupByMonth(dates) { it }
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2025, 11)), g.map { it.month })
        assertEquals(2, g[0].items.size)
    }

    @Test fun weeklyCounts() {
        val today = LocalDate.of(2026, 11, 17) // Tuesday
        val r = workoutsPerWeek(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2020, 1, 1)), today, 3)
        assertEquals(listOf(0, 2, 1), r.map { it.second })
        assertEquals(LocalDate.of(2026, 11, 16), r.last().first)
    }

    @Test fun filtersExercises() {
        val a = Exercise(1, "Bench Press", ExerciseType.BARBELL, BodyPart.CHEST)
        val b = Exercise(2, "Cable Fly", ExerciseType.MACHINE, BodyPart.CHEST)
        val c = Exercise(3, "Squat", ExerciseType.BARBELL, BodyPart.LEGS)
        val all = listOf(a, b, c)
        assertEquals(listOf(a, b), filterExercises(all, "", BodyPart.CHEST, null))
        assertEquals(listOf(a), filterExercises(all, " bench", null, null))
        assertEquals(listOf(a, c), filterExercises(all, "", null, ExerciseType.BARBELL))
    }

    @Test fun movesAndRemovesKeepSupersetsValid() {
        var l = listOf(ex(1), ex(2), ex(3), ex(4))
        l = TemplateDraftOps.toggleSupersetWithNext(l, 0)
        assertEquals(listOf(1, 1, null, null), l.map { it.supersetGroup })
        l = TemplateDraftOps.toggleSupersetWithNext(l, 1)
        assertEquals(listOf(1, 1, 1, null), l.map { it.supersetGroup })
        l = TemplateDraftOps.remove(l, 2)
        assertEquals(listOf(1L, 2L, 4L), l.map { it.exerciseId })
        assertEquals(listOf(1, 1, null), l.map { it.supersetGroup })
        l = TemplateDraftOps.remove(l, 1) // leaves a lone member
        assertEquals(listOf(null, null), l.map { it.supersetGroup })
    }

    @Test fun toggleSplitsExistingSuperset() {
        var l = listOf(ex(1), ex(2), ex(3))
        l = TemplateDraftOps.toggleSupersetWithNext(l, 0)
        l = TemplateDraftOps.toggleSupersetWithNext(l, 1)
        l = TemplateDraftOps.toggleSupersetWithNext(l, 1) // split between 2 and 3
        assertEquals(listOf(1, 1, null), l.map { it.supersetGroup })
        assertNull(TemplateDraftOps.toggleSupersetWithNext(listOf(ex(1)), 0).first().supersetGroup)
    }

    @Test fun moveBreaksSplitSuperset() {
        var l = listOf(ex(1), ex(2), ex(3))
        l = TemplateDraftOps.toggleSupersetWithNext(l, 0)
        l = TemplateDraftOps.move(l, 1, 2)
        assertEquals(listOf(1L, 3L, 2L), l.map { it.exerciseId })
        assertEquals(listOf(null, null, null), l.map { it.supersetGroup })
        assertEquals(l, TemplateDraftOps.move(l, 0, 9))
    }

    @Test fun setOpsAndSpecRoundTrip() {
        var l = listOf(TemplateDraftOps.newExercise(5, 90) { key() })
        assertEquals(3, l[0].sets.size)
        l = TemplateDraftOps.updateSet(l, 0, 0) { it.copy(type = SetType.WARMUP, weight = "20", reps = "10") }
        l = TemplateDraftOps.addSet(l, 0, key())
        l = TemplateDraftOps.removeSet(l, 0, 1)
        assertEquals(3, l[0].sets.size)
        val spec = TemplateDraftOps.toSpecs(l)[0]
        assertEquals(20.0, spec.sets[0].weightKg!!, 0.0)
        assertEquals(10, spec.sets[0].reps)
        assertEquals(90, spec.sets[1].restSec)
        assertNull(spec.sets[1].weightKg)
        val back = TemplateDraftOps.fromSpecs(listOf(spec)) { key() }
        assertEquals(SetType.WARMUP, back[0].sets[0].type)
        assertEquals("20", back[0].sets[0].weight)
    }

    @Test fun distanceUsesKilometresInEditor() {
        val d = DraftExercise(1, 1, sets = listOf(DraftSet(1, distance = "5", duration = "1800")))
        val s = TemplateDraftOps.toSpec(d).sets[0]
        assertEquals(5000.0, s.distanceM!!, 0.0)
        assertEquals("5", TemplateDraftOps.fromSpecs(listOf(TemplateDraftOps.toSpec(d))) { key() }[0].sets[0].distance)
    }

    @Test fun formatsSets() {
        assertEquals("60 kg x 8", setText(ExerciseType.BARBELL, 60.0, 8, null, null))
        assertEquals("12 reps", setText(ExerciseType.REPS_ONLY, null, 12, null, null))
        assertEquals("30:00 / 5 km", setText(ExerciseType.CARDIO, null, null, 1800, 5000.0))
        assertEquals("-", setText(ExerciseType.BARBELL, null, null, null, null))
    }

    private fun workout(id: Long, start: Long, vararg sets: Pair<Double, Int>): WorkoutFull {
        val e = Exercise(1, "Bench", ExerciseType.BARBELL, BodyPart.CHEST)
        val s = sets.mapIndexed { i, (w, r) -> WorkoutSet(id * 100 + i, id * 10, i, SetType.NORMAL, w, r, completed = true) }
        return WorkoutFull(
            Workout(id, "W", LocalDate.of(2026, 1, 1), start, start + 1000),
            listOf(WorkoutExerciseFull(WorkoutExercise(id * 10, id, 1, 0), e, s)),
        )
    }

    @Test fun prCountsChronological() {
        val w1 = workout(1, 1000, 100.0 to 5)
        val w2 = workout(2, 2000, 100.0 to 5)
        val w3 = workout(3, 3000, 105.0 to 5, 110.0 to 5) // later set supersedes earlier
        val r = prCounts(listOf(w3, w1, w2))
        assertEquals(0, r[1]); assertEquals(0, r[2]); assertEquals(1, r[3])
    }
}
