package com.yatharth.yool.reminders

import com.yatharth.yool.domain.ResolvedDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderLogicTest {
    private val d = LocalDate.of(2026, 3, 10)
    private val utc = ZoneId.of("UTC")

    @Test fun nextTriggerLaterToday() {
        val now = ZonedDateTime.of(LocalDateTime.of(d, LocalTime.of(6, 0)), utc)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(d, LocalTime.of(7, 30)), utc), nextTrigger(now, LocalTime.of(7, 30)))
    }

    @Test fun nextTriggerRollsToTomorrowWhenPastOrEqual() {
        val now = ZonedDateTime.of(LocalDateTime.of(d, LocalTime.of(7, 30)), utc)
        assertEquals(d.plusDays(1), nextTrigger(now, LocalTime.of(7, 30)).toLocalDate())
        val later = ZonedDateTime.of(LocalDateTime.of(d, LocalTime.of(23, 0)), utc)
        assertEquals(d.plusDays(1), nextTrigger(later, LocalTime.of(7, 30)).toLocalDate())
    }

    @Test fun nextTriggerKeepsWallClockAcrossDst() {
        // US spring forward: 2026-03-08 02:00 -> 03:00
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 3, 7, 20, 0), ny)
        val next = nextTrigger(now, LocalTime.of(7, 30))
        assertEquals(LocalDateTime.of(2026, 3, 8, 7, 30), next.toLocalDateTime())
        // Next one is 23h later in real time, still 07:30 on the wall.
        val after = nextTrigger(next, LocalTime.of(7, 30))
        assertEquals(LocalDateTime.of(2026, 3, 9, 7, 30), after.toLocalDateTime())
        assertEquals(24 * 3600L, after.toEpochSecond() - next.toEpochSecond())
    }

    @Test fun nextTriggerInDstGapLandsAfterGap() {
        val ny = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(LocalDateTime.of(2026, 3, 7, 20, 0), ny)
        val next = nextTrigger(now, LocalTime.of(2, 30))
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), next.toLocalDateTime())
    }

    @Test fun weightRemindersSkipWhenLogged() {
        val logged = ResolvedDay(d, weightKg = 80.0)
        val empty = ResolvedDay(d)
        assertFalse(shouldFire(ReminderType.WEIGHT, logged))
        assertFalse(shouldFire(ReminderType.WEIGHT_FOLLOW_UP, logged))
        assertTrue(shouldFire(ReminderType.WEIGHT, empty))
        assertTrue(shouldFire(ReminderType.WEIGHT_FOLLOW_UP, null))
    }

    @Test fun checkInRemindersSkipWhenDone() {
        val done = ResolvedDay(d, binge = false, mood = 4)
        val partial = ResolvedDay(d, binge = false)
        assertFalse(shouldFire(ReminderType.CHECK_IN, done))
        assertFalse(shouldFire(ReminderType.CHECK_IN_FOLLOW_UP, done))
        assertTrue(shouldFire(ReminderType.CHECK_IN, partial))
        assertTrue(shouldFire(ReminderType.CHECK_IN_FOLLOW_UP, null))
    }

    @Test fun defaults() {
        assertEquals(LocalTime.of(21, 30), ReminderType.CHECK_IN_FOLLOW_UP.defaultTime)
        assertEquals(4, ReminderType.entries.size)
    }
}
