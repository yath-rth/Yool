package com.yatharth.yool.io

import com.yatharth.yool.data.db.DailyEntry
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.db.LiftBestSet
import com.yatharth.yool.data.db.TargetEntry
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import com.yatharth.yool.data.model.TargetMetric
import com.yatharth.yool.io.backup.Backup
import com.yatharth.yool.io.backup.BackupData
import com.yatharth.yool.io.backup.BackupException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val d = LocalDate.of(2026, 11, 17)

    private val data = BackupData(
        entries = listOf(
            DailyEntry(d, 71.4, 1900, 120.5, 9000, 7.5, true, true, "Stress", "late \"snack\"", 3, "note, with comma", 123L),
            DailyEntry(d.minusDays(1), mood = 4, updatedAt = 5L),
        ),
        imported = listOf(ImportedValue(d, Metric.STEPS, Source.MIGRATED, 8000.0, 77L)),
        lifts = listOf(LiftBestSet(d, "Bench Press", 80.0, 8)),
        targets = listOf(TargetEntry(1, TargetMetric.WEIGHT_GOAL, 68.0, d, d.plusDays(90))),
    )

    @Test fun roundTrip() {
        val back = Backup.decode(Backup.encode(data))
        assertEquals(data.entries.sortedBy { it.date }, back.entries)
        assertEquals(data.imported, back.imported)
        assertEquals(data.lifts, back.lifts)
        assertEquals(data.targets.map { it.copy(id = 0) }, back.targets) // ids are not part of the format
    }

    @Test fun formatUsesIsoDatesAndEnumNames() {
        val text = Backup.encode(data)
        assertTrue(text.contains("\"formatVersion\": 2"))
        assertTrue(text.contains("\"date\": \"2026-11-17\""))
        assertTrue(text.contains("\"source\": \"MIGRATED\""))
        assertTrue(text.contains("\"metric\": \"WEIGHT_GOAL\""))
    }

    @Test fun minimalMigrationFileWithDefaults() {
        val text = """{"formatVersion":1,"exportedAt":"2020-01-01T00:00:00Z",
            "imported":[{"date":"2019-05-04","metric":"CALORIES","source":"MFP","value":2100}]}"""
        val back = Backup.decode(text)
        assertEquals(Source.MFP, back.imported.single().source)
        assertEquals(2100.0, back.imported.single().value, 0.0)
        assertTrue(back.entries.isEmpty())
    }

    @Test(expected = BackupException::class) fun rejectsNewerVersion() {
        Backup.decode("""{"formatVersion":3,"exportedAt":"x"}""")
    }

    @Test(expected = BackupException::class) fun rejectsGarbage() {
        Backup.decode("hello")
    }

    @Test(expected = BackupException::class) fun rejectsUnknownEnum() {
        Backup.decode("""{"formatVersion":1,"exportedAt":"x","imported":[{"date":"2019-05-04","metric":"NOPE","source":"MFP","value":1}]}""")
    }
}
