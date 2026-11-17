package com.yatharth.yool.ui.workout

import com.yatharth.yool.data.model.ExerciseType
import com.yatharth.yool.data.model.SetType
import com.yatharth.yool.ui.workout.session.RestState
import com.yatharth.yool.ui.workout.session.SessionMath
import com.yatharth.yool.ui.workout.session.SetValues
import com.yatharth.yool.ui.workout.session.WorkoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMathTest {
    @Test fun restRemainingRoundsUpAndNeverNegative() {
        val s = RestState.start(120, "Bench", now = 1_000)
        assertEquals(120, s.remainingSec(1_000))
        assertEquals(120, s.remainingSec(1_400))
        assertEquals(119, s.remainingSec(2_000))
        assertEquals(0, s.remainingSec(200_000))
    }

    @Test fun restProgress() {
        val s = RestState.start(100, "x", now = 0)
        assertEquals(0f, s.progress(0), 1e-6f)
        assertEquals(0.5f, s.progress(50_000), 1e-3f)
        assertEquals(1f, s.progress(500_000), 1e-6f)
    }

    @Test fun restAdjustExtendsAndShrinks() {
        val s = RestState.start(60, "x", now = 0)
        val more = s.adjusted(15, now = 10_000)
        assertEquals(75, more.totalSec)
        assertEquals(65, more.remainingSec(10_000))
        val less = s.adjusted(-15, now = 10_000)
        assertEquals(45, less.totalSec)
        assertEquals(35, less.remainingSec(10_000))
        val gone = s.adjusted(-15, now = 55_000)
        assertEquals(0, gone.remainingSec(55_000))
        assertTrue(gone.totalSec >= 1)
    }

    @Test fun completeFillsEmptyFieldsFromPrevious() {
        val prev = SetValues(50.0, 8)
        assertEquals(SetValues(50.0, 8), SessionMath.forComplete(SetValues(), prev, ExerciseType.BARBELL))
        assertEquals(SetValues(60.0, 8), SessionMath.forComplete(SetValues(weightKg = 60.0), prev, ExerciseType.BARBELL))
        assertEquals(SetValues(60.0, 5), SessionMath.forComplete(SetValues(60.0, 5), prev, ExerciseType.BARBELL))
        assertEquals(SetValues(), SessionMath.forComplete(SetValues(), null, ExerciseType.BARBELL))
    }

    @Test fun completeIgnoresFieldsTheTypeDoesNotUse() {
        val prev = SetValues(50.0, 8, 30, 100.0)
        assertEquals(SetValues(null, 8, null, null), SessionMath.forComplete(SetValues(), prev, ExerciseType.REPS_ONLY))
        assertEquals(SetValues(null, null, 30, 100.0), SessionMath.forComplete(SetValues(), prev, ExerciseType.CARDIO))
    }

    @Test fun valuesLabels() {
        assertEquals("50 × 8", SessionMath.valuesLabel(ExerciseType.BARBELL, SetValues(50.0, 8)))
        assertEquals("52.5 × 5", SessionMath.valuesLabel(ExerciseType.DUMBBELL, SetValues(52.5, 5)))
        assertEquals("12", SessionMath.valuesLabel(ExerciseType.REPS_ONLY, SetValues(null, 12)))
        assertEquals("5 km · 30:00", SessionMath.valuesLabel(ExerciseType.CARDIO, SetValues(null, null, 1800, 5000.0)))
        assertEquals("1:30", SessionMath.valuesLabel(ExerciseType.DURATION, SetValues(null, null, 90)))
        assertEquals("—", SessionMath.valuesLabel(ExerciseType.BARBELL, SetValues()))
    }

    @Test fun setLabelsNumberOnlyNormalSets() {
        val types = listOf(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL, SetType.DROP, SetType.FAILURE, SetType.NORMAL)
        assertEquals(listOf("W", "1", "2", "D", "F", "3"), SessionMath.setLabels(types))
    }

    @Test fun durationParsing() {
        assertEquals(90, SessionMath.parseDuration("90"))
        assertEquals(90, SessionMath.parseDuration("1:30"))
        assertEquals(3723, SessionMath.parseDuration("1:02:03"))
        assertNull(SessionMath.parseDuration(""))
        assertNull(SessionMath.parseDuration("a:b"))
        assertEquals("1:30", SessionMath.durationText(90))
        assertEquals("1:02:03", SessionMath.durationText(3723))
    }

    @Test fun platesParsing() {
        assertEquals(listOf(25.0, 20.0, 2.5, 1.25), WorkoutSettings.parsePlates("20, 25 ; 2.5,1.25, 20, x, -5"))
        assertEquals("25, 1.25", WorkoutSettings.formatPlates(listOf(25.0, 1.25)))
    }

    @Test fun rpeOptions() {
        assertEquals(9, SessionMath.RpeOptions.size)
        assertEquals("8", SessionMath.rpeText(8.0))
        assertEquals("8.5", SessionMath.rpeText(8.5))
    }
}
