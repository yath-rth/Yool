package com.yatharth.yool.domain

import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.TargetMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private val mon = LocalDate.of(2026, 11, 2) // a Monday
    private fun p(offset: Long, v: Double) = DatePoint(mon.plusDays(offset), v)
    private fun day(offset: Long, binge: Boolean? = null, workout: Boolean? = null) =
        ResolvedDay(date = mon.plusDays(offset), binge = binge, workoutDone = workout)

    @Test fun movingAverageUsesCalendarWindow() {
        val out = Stats.movingAverage(listOf(p(0, 80.0), p(1, 82.0), p(10, 70.0)), 7)
        assertEquals(80.0, out[0].value, 1e-9)
        assertEquals(81.0, out[1].value, 1e-9)
        assertEquals(70.0, out[2].value, 1e-9) // earlier points fall outside the window
    }

    @Test fun weeklyAveragesGroupByMonday() {
        val out = Stats.weeklyAverages(listOf(p(0, 2.0), p(6, 4.0), p(7, 10.0)))
        assertEquals(2, out.size)
        assertEquals(mon, out[0].date)
        assertEquals(3.0, out[0].value, 1e-9)
        assertEquals(mon.plusDays(7), out[1].date)
    }

    @Test fun slopeIsLinear() {
        assertEquals(-0.5, Stats.slopePerDay(listOf(p(0, 80.0), p(2, 79.0), p(4, 78.0)))!!, 1e-9)
        assertEquals(-3.5, Stats.slopePerWeek(listOf(p(0, 80.0), p(2, 79.0), p(4, 78.0)))!!, 1e-9)
        assertNull(Stats.slopePerDay(listOf(p(0, 80.0))))
        assertNull(Stats.slopePerDay(listOf(p(0, 80.0), p(0, 81.0))))
    }

    @Test fun workoutsPerWeekIncludesEmptyWeeks() {
        val days = listOf(day(0, workout = true), day(2, workout = true), day(15, workout = true), day(3, workout = false))
        val out = Stats.workoutsPerWeek(days, mon, mon.plusDays(20))
        assertEquals(listOf(2.0, 0.0, 1.0), out.map { it.value })
    }

    @Test fun longestBingeFreeBreaksOnBingeAndGaps() {
        val days = listOf(
            day(0, false), day(1, false), day(2, false), day(3, true),
            day(4, false), day(5, false),
            day(7, false), // gap on day 6 breaks run
        )
        assertEquals(3, Stats.longestBingeFree(days))
        assertEquals(0, Stats.longestBingeFree(emptyList()))
    }

    @Test fun percentHitIgnoresUnknown() {
        assertEquals(50.0, Stats.percentHit(listOf(true, false, null))!!, 1e-9)
        assertNull(Stats.percentHit(listOf(null, null)))
    }

    @Test fun triggersRankedByCount() {
        val days = listOf(
            ResolvedDay(mon, binge = true, bingeTrigger = "Stress"),
            ResolvedDay(mon.plusDays(1), binge = true, bingeTrigger = "Social"),
            ResolvedDay(mon.plusDays(2), binge = true, bingeTrigger = "Stress"),
            ResolvedDay(mon.plusDays(3), binge = false, bingeTrigger = "Ignored"),
        )
        assertEquals(listOf("Stress" to 2, "Social" to 1), Stats.triggerCounts(days))
    }

    @Test fun bucketForDisplayAggregatesLongRanges() {
        val pts = (0L..99L).map { p(it, 1.0) }
        assertTrue(Stats.bucketForDisplay(pts, 100).second)
        assertEquals(pts.size, Stats.bucketForDisplay(pts, 30).first.size)
    }

    @Test fun targetStepsFollowsHistory() {
        val h = TargetHistory(
            listOf(
                TargetEntry(1, TargetMetric.CALORIES, 2000.0, mon.minusDays(30)),
                TargetEntry(2, TargetMetric.CALORIES, 1800.0, mon.plusDays(5)),
                TargetEntry(3, TargetMetric.CALORIES, 1700.0, mon.plusDays(50)),
            )
        )
        val out = Stats.targetSteps(h, TargetMetric.CALORIES, mon, mon.plusDays(10))
        assertEquals(listOf(2000.0, 1800.0), out.map { it.value })
        assertEquals(mon, out[0].date)
    }
}

class RangesTest {
    private val today = LocalDate.of(2026, 11, 17)

    @Test fun presetsAreInclusiveWindowsEndingToday() {
        assertEquals(today.minusDays(6) to today, Ranges.resolve(RangePreset.W1, today))
        assertEquals(today.minusDays(364) to today, Ranges.resolve(RangePreset.Y1, today))
    }

    @Test fun allStartsAtEarliestData() {
        val e = LocalDate.of(2025, 1, 5)
        assertEquals(e to today, Ranges.resolve(RangePreset.ALL, today, earliest = e))
        assertEquals(today.minusDays(29) to today, Ranges.resolve(RangePreset.ALL, today))
    }

    @Test fun customSwapsReversedDates() {
        val a = LocalDate.of(2026, 1, 1)
        val b = LocalDate.of(2026, 2, 1)
        assertEquals(a to b, Ranges.resolve(RangePreset.CUSTOM, today, customFrom = b, customTo = a))
        assertEquals(today.minusDays(29) to today, Ranges.resolve(RangePreset.CUSTOM, today))
    }
}
