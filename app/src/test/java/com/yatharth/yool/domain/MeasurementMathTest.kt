package com.yatharth.yool.domain

import com.yatharth.yool.data.db.BodyMeasurement
import com.yatharth.yool.data.model.MeasurementPart
import com.yatharth.yool.io.backup.Backup
import com.yatharth.yool.io.backup.BackupData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MeasurementMathTest {
    private val d = LocalDate.of(2026, 11, 17)

    @Test fun summariesUseLatestAndChangeFromPrevious() {
        val all = listOf(
            BodyMeasurement(1, d.minusDays(14), MeasurementPart.WAIST, 84.0),
            BodyMeasurement(2, d, MeasurementPart.WAIST, 82.5),
            BodyMeasurement(3, d.minusDays(7), MeasurementPart.WAIST, 83.0),
            BodyMeasurement(4, d, MeasurementPart.LEFT_BICEP, 36.0),
        )
        val s = MeasurementMath.summaries(all)
        assertEquals(listOf(MeasurementPart.LEFT_BICEP, MeasurementPart.WAIST), s.map { it.part })
        val waist = s.last()
        assertEquals(82.5, waist.latest, 0.0)
        assertEquals(-0.5, waist.change!!, 1e-9)
        assertEquals(3, waist.count)
        assertNull(s.first().change)
    }

    @Test fun parseFormClearsBlanksAndRejectsJunk() {
        val m = MeasurementMath.parseForm(
            mapOf(
                MeasurementPart.WAIST to "82,5",
                MeasurementPart.CHEST to "",
                MeasurementPart.NECK to "abc",
                MeasurementPart.BODY_FAT to "95",
            ),
        )
        assertEquals(82.5, m[MeasurementPart.WAIST]!!, 0.0)
        assertTrue(m.containsKey(MeasurementPart.CHEST) && m[MeasurementPart.CHEST] == null)
        assertFalse(m.containsKey(MeasurementPart.NECK))
        assertFalse(m.containsKey(MeasurementPart.BODY_FAT)) // over 70 %
    }

    @Test fun signed() {
        assertEquals("+1.2", MeasurementMath.signed(1.2))
        assertEquals("−0.5", MeasurementMath.signed(-0.5))
        assertEquals("0.0", MeasurementMath.signed(0.01))
    }

    @Test fun backupRoundTrip() {
        val data = BackupData(
            entries = emptyList(), imported = emptyList(), lifts = emptyList(), targets = emptyList(),
            measurements = listOf(BodyMeasurement(date = d, part = MeasurementPart.RIGHT_CALF, value = 38.5)),
        )
        val back = Backup.decode(Backup.encode(data)).measurements.single()
        assertEquals(d, back.date)
        assertEquals(MeasurementPart.RIGHT_CALF, back.part)
        assertEquals(38.5, back.value, 0.0)
        assertTrue(Backup.decode("""{"formatVersion":3,"exportedAt":"x"}""").measurements.isEmpty())
    }
}
