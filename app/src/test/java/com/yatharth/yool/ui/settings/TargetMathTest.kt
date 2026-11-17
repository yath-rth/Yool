package com.yatharth.yool.ui.settings

import com.yatharth.yool.ui.settings.targets.requiredWeeklyRateKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TargetMathTest {
    private val today = LocalDate.of(2026, 1, 1)

    @Test fun lossRateIsNegative() {
        val r = requiredWeeklyRateKg(80.0, 76.0, today.plusWeeks(8), today)!!
        assertEquals(-0.5, r, 1e-9)
    }

    @Test fun pastOrMissingGivesNull() {
        assertNull(requiredWeeklyRateKg(80.0, 76.0, today, today))
        assertNull(requiredWeeklyRateKg(null, 76.0, today.plusDays(10), today))
        assertNull(requiredWeeklyRateKg(80.0, 76.0, null, today))
    }
}
