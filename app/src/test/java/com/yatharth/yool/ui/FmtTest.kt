package com.yatharth.yool.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class FmtTest {
    private val today = LocalDate.of(2026, 11, 17)

    init {
        Locale.setDefault(Locale.US)
    }

    @Test fun relativeDates() {
        assertEquals("Today", Fmt.date(today, today))
        assertEquals("Yesterday", Fmt.date(today.minusDays(1), today))
        assertEquals("Nov 05, 2026", Fmt.date(LocalDate.of(2026, 11, 5), today))
        assertEquals("Dec 31, 2025", Fmt.date(LocalDate.of(2025, 12, 31), today))
    }

    @Test fun datesWithoutYear() {
        assertEquals("Today", Fmt.dateWithoutYear(today, today))
        assertEquals("Nov 05", Fmt.dateWithoutYear(LocalDate.of(2026, 11, 5), today))
        assertEquals("Nov 17", Fmt.dateNoRelativeWithoutYear(today))
        assertEquals("Nov 17, 2026", Fmt.dateNoRelative(today))
    }

    @Test fun twelveHourTimes() {
        assertEquals("06:30 PM", Fmt.time(LocalTime.of(18, 30)))
        assertEquals("12:05 AM", Fmt.time(LocalTime.of(0, 5)))
    }

    @Test fun timeAgo() {
        val now = 10_000_000L
        assertEquals("JUST NOW", Fmt.timeAgo(now - 4 * 60_000, now))
        assertEquals("12 MIN AGO", Fmt.timeAgo(now - 12 * 60_000, now))
    }
}
