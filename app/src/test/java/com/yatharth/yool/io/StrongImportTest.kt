package com.yatharth.yool.io

import com.yatharth.yool.io.strong.StrongImport
import com.yatharth.yool.io.strong.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StrongImportTest {
    private val d1 = LocalDate.of(2024, 1, 15)
    private val d2 = LocalDate.of(2024, 1, 17)

    private val commaCsv = """
Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Weight Unit,Reps,Distance,Seconds,Notes,Workout Notes,RPE
2024-01-15 18:30:00,"Push, heavy",1h,Bench Press,W,40,kg,10,0,0,,,
2024-01-15 18:30:00,"Push, heavy",1h,Bench Press,1,80,kg,5,0,0,,,
2024-01-15 18:30:00,"Push, heavy",1h,Bench Press,2,80,kg,8,0,0,"felt ""good""
second line",,
2024-01-15 18:30:00,"Push, heavy",1h,Bench Press,3,70,kg,12,0,0,,,
2024-01-15 18:30:00,"Push, heavy",1h,Push-up,1,0,kg,20,0,0,,,
2024-01-15 18:30:00,"Push, heavy",1h,Push-up,2,,kg,25,0,0,,,
2024-01-15 18:30:00,"Push, heavy",1h,Plank,1,0,kg,,0,60,,,
2024-01-15 18:30:00,"Push, heavy",1h,Rest Timer,Rest Timer,0,kg,,0,90,,,
2024-01-17 09:00,Legs,45m,Squat,D,100,kg,3,0,0,,,
""".trimStart()

    @Test fun commaDelimitedBestSets() {
        val r = StrongImport.parse(commaCsv)
        assertEquals(9, r.rowCount)
        assertEquals(setOf(d1, d2), r.workoutDates)
        assertEquals(setOf("Bench Press", "Push-up", "Squat"), r.exercises)
        val best = r.bestSets(WeightUnit.KG).associateBy { it.date to it.exercise }
        // heaviest weight wins, tie broken by reps; warm-up (40x10) ignored
        assertEquals(80.0, best[d1 to "Bench Press"]!!.weightKg, 0.0)
        assertEquals(8, best[d1 to "Bench Press"]!!.reps)
        // bodyweight: most reps at weight 0
        assertEquals(0.0, best[d1 to "Push-up"]!!.weightKg, 0.0)
        assertEquals(25, best[d1 to "Push-up"]!!.reps)
        // D set order counts, "yyyy-MM-dd HH:mm" date accepted
        assertEquals(100.0, best[d2 to "Squat"]!!.weightKg, 0.0)
        // time-only plank row has no reps -> skipped
        assertTrue(best[d1 to "Plank"] == null)
    }

    @Test fun semicolonDelimiterAndDecimalComma() {
        val csv = "Date;Exercise Name;Set Order;Weight;Reps\n" +
            "2024-02-01T10:00:00;\"Row; cable\";1;52,5;10\n"
        val r = StrongImport.parse(csv)
        val s = r.bestSets(WeightUnit.KG).single()
        assertEquals("Row; cable", s.exercise)
        assertEquals(52.5, s.weightKg, 0.0)
        assertEquals(10, s.reps)
    }

    @Test fun lbsConvertedToKg() {
        val csv = "Date,Exercise Name,Set Order,Weight,Weight Unit,Reps\n" +
            "2024-02-01 10:00:00,Deadlift,1,225,lbs,5\n"
        val r = StrongImport.parse(csv)
        assertEquals(102.06, r.bestSets(WeightUnit.KG).single().weightKg, 0.001)
    }

    @Test fun userUnitUsedWhenFileHasNoUnitColumn() {
        val csv = "Date,Exercise Name,Set Order,Weight,Reps\n2024-02-01 10:00:00,Deadlift,1,100,5\n"
        val r = StrongImport.parse(csv)
        assertEquals(100.0, r.bestSets(WeightUnit.KG).single().weightKg, 0.0)
        assertEquals(45.36, r.bestSets(WeightUnit.LB).single().weightKg, 0.001)
    }

    @Test fun headersCaseInsensitiveAndBom() {
        val csv = "﻿date,EXERCISE NAME,reps,weight\r\n2024-02-01 10:00:00,Curl,12,15\r\n"
        val r = StrongImport.parse(csv)
        assertEquals(12, r.bestSets(WeightUnit.KG).single().reps)
    }

    @Test fun workoutValuesOnePerDate() {
        val r = StrongImport.parse(commaCsv)
        assertEquals(2, r.workoutValues().size)
    }

    @Test(expected = StrongImport.FormatException::class)
    fun rejectsNonStrongFiles() {
        StrongImport.parse("foo,bar\n1,2\n")
    }
}
