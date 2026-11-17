package com.yatharth.yool.data.healthconnect

import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HealthConnectLogicTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val from = LocalDate.of(2026, 1, 1)
    private val to = LocalDate.of(2026, 12, 31)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    @Test
    fun nutritionSplitsByOriginAndLocalDay() {
        val samples = listOf(
            NutritionSample("1", at(2026, 3, 1, 8), HcOrigins.MYFITNESSPAL, 500.0, 30.0),
            NutritionSample("2", at(2026, 3, 1, 20), HcOrigins.MYFITNESSPAL, 700.0, 40.0),
            NutritionSample("3", at(2026, 3, 1, 12), "com.other.app", 100.0, null),
            NutritionSample("4", at(2026, 3, 2, 0, 30), HcOrigins.MYFITNESSPAL, 300.0, 10.0),
        )
        val out = HealthConnectLogic.nutrition(samples, zone, from, to)
        fun find(d: Int, m: Metric, s: Source) = out.single { it.date.dayOfMonth == d && it.metric == m && it.source == s }.value
        assertEquals(1200.0, find(1, Metric.CALORIES, Source.MFP), 0.0)
        assertEquals(70.0, find(1, Metric.PROTEIN_G, Source.MFP), 0.0)
        assertEquals(100.0, find(1, Metric.CALORIES, Source.HEALTH_CONNECT), 0.0)
        assertTrue(out.none { it.date.dayOfMonth == 1 && it.metric == Metric.PROTEIN_G && it.source == Source.HEALTH_CONNECT })
        assertEquals(300.0, find(2, Metric.CALORIES, Source.MFP), 0.0)
    }

    @Test
    fun nutritionUsesLocalNotUtcDay() {
        // 00:30 IST on Mar 2 is still Mar 1 in UTC
        val out = HealthConnectLogic.nutrition(listOf(NutritionSample("a", at(2026, 3, 2, 0, 30), null, 200.0, 5.0)), zone, from, to)
        assertEquals(LocalDate.of(2026, 3, 2), out.first().date)
    }

    @Test
    fun nutritionDedupsAndRespectsRange() {
        val s = NutritionSample("same", at(2026, 3, 1, 8), HcOrigins.MYFITNESSPAL, 500.0, 0.0)
        val old = NutritionSample("old", at(2025, 3, 1, 8), HcOrigins.MYFITNESSPAL, 500.0, 0.0)
        val out = HealthConnectLogic.nutrition(listOf(s, s, old), zone, from, to)
        assertEquals(1, out.size)
        assertEquals(500.0, out[0].value, 0.0)
    }

    @Test
    fun sleepSubtractsAwakeAndOutOfBed() {
        val start = at(2026, 3, 1, 23)
        val end = at(2026, 3, 2, 7) // 8h
        val session = SleepSample(
            "s", start, end,
            stages = listOf(
                SleepStageSample(at(2026, 3, 2, 2), at(2026, 3, 2, 2, 30), awake = true),
                SleepStageSample(at(2026, 3, 2, 2, 15), at(2026, 3, 2, 3), awake = true), // overlaps previous
                SleepStageSample(at(2026, 3, 2, 4), at(2026, 3, 2, 5), awake = false),
            ),
        )
        assertEquals(8.0 - 1.0, HealthConnectLogic.sleepHours(session), 1e-9) // awake union 02:00-03:00
    }

    @Test
    fun sleepAssignedToWakeUpDayAndSummed() {
        val night = SleepSample("n", at(2026, 3, 1, 23), at(2026, 3, 2, 7))
        val nap = SleepSample("p", at(2026, 3, 2, 14), at(2026, 3, 2, 15))
        val out = HealthConnectLogic.sleep(listOf(night, nap), zone, from, to)
        assertEquals(1, out.size)
        assertEquals(LocalDate.of(2026, 3, 2), out[0].date)
        assertEquals(9.0, out[0].value, 1e-9)
        assertEquals(Source.HEALTH_CONNECT, out[0].source)
    }

    @Test
    fun workoutFilterKeepsStrongAndStrengthOnly() {
        val strong = ExerciseSample("1", at(2026, 3, 1, 18), HcOrigins.STRONG, strengthLike = false)
        val strengthOther = ExerciseSample("2", at(2026, 3, 2, 18), "com.x", strengthLike = true)
        val walk = ExerciseSample("3", at(2026, 3, 3, 18), "com.xiaomi.wearable", strengthLike = false)
        assertTrue(HealthConnectLogic.isWorkout(strong))
        assertTrue(HealthConnectLogic.isWorkout(strengthOther))
        assertFalse(HealthConnectLogic.isWorkout(walk))
        val out = HealthConnectLogic.workouts(listOf(strong, strengthOther, walk, strong.copy(id = "4")), zone, from, to)
        assertEquals(listOf(1, 2), out.map { it.date.dayOfMonth })
        assertTrue(out.all { it.value == 1.0 && it.metric == Metric.WORKOUT })
    }
}
