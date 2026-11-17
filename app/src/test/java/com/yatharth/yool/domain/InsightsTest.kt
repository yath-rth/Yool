package com.yatharth.yool.domain

import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.TargetMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private val start = LocalDate.of(2026, 10, 1)
    private fun d(i: Long, f: (ResolvedDay) -> ResolvedDay = { it }) = f(ResolvedDay(date = start.plusDays(i)))

    @Test fun sleepVsNextDayMood() {
        // sleep on day i, mood on day i+1
        val days = listOf(
            d(0) { it.copy(sleepHours = 5.0) }, d(1) { it.copy(mood = 2, sleepHours = 8.5) },
            d(2) { it.copy(mood = 4) },
            d(3) { it.copy(sleepHours = 5.5) }, d(4) { it.copy(mood = 3) },
        )
        val g = Insights.sleepVsNextMood(days)
        assertEquals(2.5, g[0].avg!!, 1e-9) // under 6h: next moods 2 and 3
        assertEquals(2, g[0].n)
        assertFalse(g[0].enough)
        assertEquals(4.0, g[3].avg!!, 1e-9)
        assertNull(g[1].avg)
    }

    @Test fun bingeComparisonUsesPreviousDay() {
        val days = listOf(
            d(0) { it.copy(calories = 1000, sleepHours = 5.0, binge = false, mood = 4) },
            d(1) { it.copy(binge = true, mood = 2, bingeTrigger = "Stress") },
            d(2) { it.copy(calories = 2000, sleepHours = 8.0, binge = false, mood = 5) },
            d(3) { it.copy(binge = false, mood = 4) },
        )
        val c = Insights.bingeComparison(days)
        assertEquals(1, c.bingeDays)
        assertEquals(3, c.otherDays)
        assertEquals(1000.0, c.prevCalories.first.avg!!, 1e-9) // day before the binge
        assertEquals(2.0, c.mood.first.avg!!, 1e-9)
        assertEquals(listOf("Stress" to 1), c.triggers)
        assertEquals(1, c.prevCalories.second.n) // only day 3 has a preceding day with calories (day 2)
    }

    private val stepTargets = TargetHistory(
        listOf(
            TargetEntry(1, TargetMetric.STEPS, 8000.0, start.minusDays(10)),
            TargetEntry(2, TargetMetric.STEPS, 10000.0, start.plusDays(2)),
        )
    )

    @Test fun stepsVsMoodUsesTargetInForce() {
        val days = listOf(
            d(0) { it.copy(steps = 9000, mood = 5) },  // hit (8000)
            d(1) { it.copy(steps = 7000, mood = 2) },  // miss
            d(2) { it.copy(steps = 9000, mood = 3) },  // miss under new 10000
            d(3) { it.copy(steps = 11000, mood = 4) }, // hit
        )
        val (hit, miss) = Insights.stepsVsMood(days, stepTargets)
        assertEquals(4.5, hit.avg!!, 1e-9)
        assertEquals(2.5, miss.avg!!, 1e-9)
    }

    @Test fun adherenceUsesEffectiveTargets() {
        val targets = TargetHistory(
            listOf(
                TargetEntry(1, TargetMetric.CALORIES, 2000.0, start.minusDays(1)),
                TargetEntry(2, TargetMetric.CALORIES, 1500.0, start.plusDays(1)),
            )
        )
        val days = listOf(d(0) { it.copy(calories = 1900) }, d(1) { it.copy(calories = 1900) }, d(2) { it.copy() })
        val cal = Insights.adherence(days, targets).first()
        assertEquals(2, cal.n)
        assertEquals(50.0, cal.percent!!, 1e-9)
        assertNull(Insights.adherence(days, targets)[1].percent) // no protein target
    }

    @Test fun weightTrendVerdicts() {
        val today = start.plusDays(10)
        val losing = (0L..10L).map { i -> d(i) { it.copy(weightKg = 80.0 - 0.1 * i) } } // -0.7 kg/week
        fun targets(goal: Double, date: LocalDate?) =
            TargetHistory(listOf(TargetEntry(1, TargetMetric.WEIGHT_GOAL, goal, start.minusDays(30), date)))

        // current 79.0, goal 77 in 4 weeks => need -0.5/wk; actual -0.7 => on track
        val on = Insights.weightTrend(losing, targets(77.0, today.plusDays(28)), today)
        assertEquals(TrendVerdict.ON_TRACK, on.verdict)
        assertEquals(-0.7, on.actualKgPerWeek!!, 1e-6)
        assertEquals(-0.5, on.requiredKgPerWeek!!, 1e-6)

        // goal 74 in 4 weeks => need -1.25/wk => off track
        assertEquals(TrendVerdict.OFF_TRACK, Insights.weightTrend(losing, targets(74.0, today.plusDays(28)), today).verdict)
        // no date: moving toward goal counts
        assertEquals(TrendVerdict.ON_TRACK, Insights.weightTrend(losing, targets(70.0, null), today).verdict)
        assertEquals(TrendVerdict.OFF_TRACK, Insights.weightTrend(losing, targets(90.0, null), today).verdict)
        // no goal / too little data
        assertEquals(TrendVerdict.UNKNOWN, Insights.weightTrend(losing, TargetHistory.Empty, today).verdict)
        assertEquals(TrendVerdict.UNKNOWN, Insights.weightTrend(listOf(losing[0]), targets(70.0, null), today).verdict)
    }

    @Test fun workoutVsRest() {
        val days = listOf(
            d(0) { it.copy(workoutDone = true, mood = 5, calories = 2200) },
            d(1) { it.copy(workoutDone = false, mood = 3, calories = 1800) },
            d(2) { it.copy(mood = 1) },
        )
        val r = Insights.workoutVsRest(days)
        assertEquals(5.0, r.workoutMood.avg!!, 1e-9)
        assertEquals(2.0, r.restMood.avg!!, 1e-9)
        assertEquals(2, r.restMood.n)
        assertEquals(1800.0, r.restCalories.avg!!, 1e-9)
        assertTrue(!r.workoutMood.enough)
    }
}
