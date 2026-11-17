package com.yatharth.yool.io

import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.domain.ResolvedDay
import com.yatharth.yool.domain.TargetHistory
import com.yatharth.yool.io.export.ExportBuilder
import com.yatharth.yool.io.export.ExportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ExportTest {
    private val d = LocalDate.of(2026, 11, 16) // Monday

    @Test fun csvEscaping() {
        assertEquals("plain", ExportBuilder.csvField("plain"))
        assertEquals("\"a,b\"", ExportBuilder.csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", ExportBuilder.csvField("say \"hi\""))
        assertEquals("\"l1\nl2\"", ExportBuilder.csvField("l1\nl2"))
        assertEquals("", ExportBuilder.csvField(null))
    }

    private val days = listOf(
        ResolvedDay(
            d, weightKg = 72.0, calories = 2000, proteinG = 130.0, steps = 9000, sleepHours = 7.5, workoutDone = true,
            binge = false, mood = 4, note = "ok, \"fine\"", sources = mapOf(Metric.CALORIES to Source.MFP, Metric.WEIGHT_KG to Source.MANUAL),
        ),
        ResolvedDay(d.plusDays(1), weightKg = 71.0, calories = 2600, binge = true, bingeTrigger = "Stress", bingeNote = "late", mood = 2),
        ResolvedDay(d.plusDays(2)),
        ResolvedDay(d.plusDays(3), binge = false, mood = 3),
    )

    @Test fun dailyCsvRows() {
        val lines = ExportBuilder.dailyCsv(days).trim().lines()
        assertEquals(4, lines.size) // header + 3 days with data (empty day skipped)
        assertTrue(lines[0].startsWith("date,weight_kg,weight_source,calories,calories_source"))
        assertEquals(lines[0].split(",").size, 18)
        assertTrue(lines[1].startsWith("2026-11-16,72,manual,2000,mfp,130,"))
        assertTrue(lines[1].endsWith(",0,,,4,\"ok, \"\"fine\"\"\""))
        assertTrue(lines[2].contains(",1,Stress,late,2,"))
    }

    @Test fun liftsCsv() {
        val csv = ExportBuilder.liftsCsv(listOf(LiftBestSet(d, "Bench, flat", 82.5, 6)))
        assertEquals("date,exercise,weight_kg,reps\n2026-11-16,\"Bench, flat\",82.5,6\n", csv)
    }

    @Test fun summaryContents() {
        val targets = TargetHistory(
            listOf(
                TargetEntry(1, TargetMetric.CALORIES, 2200.0, LocalDate.of(2026, 1, 1)),
                TargetEntry(2, TargetMetric.CALORIES, 2000.0, d.plusDays(2)),
            )
        )
        val md = ExportBuilder.summaryMd(d, d.plusDays(3), days, targets, emptyList())
        assertTrue(md.contains("AI coach"))
        assertTrue(md.contains("2200 kcal from 2026-01-01; 2000 kcal from 2026-11-18"))
        assertTrue(md.contains("| 2026-11-16 | 71.5 | 2300 |")) // weekly averages
        assertTrue(md.contains("Binge days: 1 of 3"))
        assertTrue(md.contains("Stress x1"))
        assertTrue(md.contains("-1.0 kg"))
        assertTrue(md.contains("Calories (at or under target): 50% (1 of 2 logged days)"))
        assertTrue(md.contains("streak (as of 2026-11-19): 1 days"))
        assertTrue(md.contains("2026-11-17: binge note: late"))
    }

    @Test fun zipContainsThreeFiles() {
        val bytes = ExportBuilder.buildZip(d, d.plusDays(3), days, emptyList(), TargetHistory.Empty)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) names += (z.nextEntry ?: break).name
        }
        assertEquals(listOf("summary.md", "daily.csv", "lifts.csv"), names)
    }

    @Test fun presetRanges() {
        val today = LocalDate.of(2026, 11, 17)
        assertEquals(today, ExportRange.DAY.start(today))
        assertEquals(LocalDate.of(2026, 11, 11), ExportRange.WEEK.start(today))
        assertEquals(LocalDate.of(2026, 10, 18), ExportRange.MONTH.start(today))
        assertEquals(LocalDate.of(2025, 11, 18), ExportRange.YEAR.start(today))
    }
}
