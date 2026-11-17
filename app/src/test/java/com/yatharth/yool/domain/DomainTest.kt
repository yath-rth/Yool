package com.yatharth.yool.domain

import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.data.model.TargetMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DayResolverTest {
    private val d = LocalDate.of(2026, 11, 17)

    @Test fun manualBeatsEverything() {
        val r = DayResolver.resolve(
            d, DailyEntry(d, calories = 1800),
            listOf(ImportedValue(d, Metric.CALORIES, Source.MFP, 2100.0)),
        )
        assertEquals(1800, r.calories)
        assertEquals(Source.MANUAL, r.sources[Metric.CALORIES])
    }

    @Test fun mfpBeatsHealthConnectBeatsMigrated() {
        val r = DayResolver.resolve(
            d, null,
            listOf(
                ImportedValue(d, Metric.STEPS, Source.MIGRATED, 1.0),
                ImportedValue(d, Metric.STEPS, Source.HEALTH_CONNECT, 2.0),
                ImportedValue(d, Metric.CALORIES, Source.HEALTH_CONNECT, 3.0),
                ImportedValue(d, Metric.CALORIES, Source.MFP, 4.0),
            ),
        )
        assertEquals(2, r.steps)
        assertEquals(4, r.calories)
        assertEquals(Source.MFP, r.sources[Metric.CALORIES])
    }

    @Test fun manualWorkoutFalseOverridesImportedSession() {
        val r = DayResolver.resolve(d, DailyEntry(d, workoutDone = false), listOf(ImportedValue(d, Metric.WORKOUT, Source.HEALTH_CONNECT, 1.0)))
        assertEquals(false, r.workoutDone)
    }

    @Test fun importedWorkoutCounts() {
        val r = DayResolver.resolve(d, null, listOf(ImportedValue(d, Metric.WORKOUT, Source.STRONG_CSV, 1.0)))
        assertEquals(true, r.workoutDone)
    }

    @Test fun rangeIncludesEmptyDays() {
        val days = DayResolver.resolveRange(d, d.plusDays(2), listOf(DailyEntry(d.plusDays(1), mood = 4)), emptyList())
        assertEquals(3, days.size)
        assertNull(days[0].mood)
        assertEquals(4, days[1].mood)
    }
}

class TargetHistoryTest {
    @Test fun valueInForceOnDate() {
        val h = TargetHistory(
            listOf(
                TargetEntry(metric = TargetMetric.CALORIES, value = 2000.0, effectiveFrom = LocalDate.of(2026, 1, 1)),
                TargetEntry(metric = TargetMetric.CALORIES, value = 1800.0, effectiveFrom = LocalDate.of(2026, 6, 1)),
            )
        )
        assertNull(h.valueOn(TargetMetric.CALORIES, LocalDate.of(2025, 12, 31)))
        assertEquals(2000.0, h.valueOn(TargetMetric.CALORIES, LocalDate.of(2026, 5, 31))!!, 0.0)
        assertEquals(1800.0, h.valueOn(TargetMetric.CALORIES, LocalDate.of(2026, 6, 1))!!, 0.0)
    }

    @Test fun caloriesIsCeilingOthersFloor() {
        assertEquals(true, TargetCheck.caloriesHit(1800, 2000.0))
        assertEquals(false, TargetCheck.caloriesHit(2100, 2000.0))
        assertEquals(true, TargetCheck.floorHit(10000.0, 8000.0))
    }
}

class StreaksTest {
    private val today = LocalDate.of(2026, 11, 17)
    private fun day(offset: Long, binge: Boolean?) = ResolvedDay(today.minusDays(offset), binge = binge)

    @Test fun countsUntilBinge() {
        val days = listOf(day(0, false), day(1, false), day(2, true), day(3, false))
        assertEquals(2, Streaks.bingeFree(days, today))
    }

    @Test fun todayNotLoggedStartsYesterday() {
        val days = listOf(day(0, null), day(1, false), day(2, false), day(3, null))
        assertEquals(2, Streaks.bingeFree(days, today))
    }
}
