package com.yatharth.yool.ui

import com.yatharth.yool.data.model.Source
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * All user-facing number/date/time formatting. Date and time rules follow the Formater guide:
 * "Today" / "Yesterday", otherwise "MMM dd, yyyy" (or "MMM dd" without the year); times as "hh:mm a";
 * recent moments as "JUST NOW" / "N MIN AGO". Machine formats (backup, CSV export) stay ISO.
 */
object Fmt {
    private val dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    private val dateNoYearFmt = DateTimeFormatter.ofPattern("MMM dd")
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a")

    fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    fun toTime(millis: Long): LocalTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()

    /** "Today", "Yesterday" or "Nov 15, 2026". */
    fun date(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dateFmt)
    }

    fun date(millis: Long): String = date(toDate(millis))

    /** "Today", "Yesterday" or "Nov 15". */
    fun dateWithoutYear(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dateNoYearFmt)
    }

    fun dateWithoutYear(millis: Long): String = dateWithoutYear(toDate(millis))

    /** Always the calendar date: "Nov 15, 2026". */
    fun dateNoRelative(d: LocalDate): String = d.format(dateFmt)

    /** Always the calendar date without year: "Nov 15". */
    fun dateNoRelativeWithoutYear(d: LocalDate): String = d.format(dateNoYearFmt)

    /** "06:30 PM". */
    fun time(t: LocalTime): String = t.format(timeFmt)
    fun time(millis: Long): String = time(toTime(millis))

    /** "Today, 06:30 PM" / "Nov 15, 2026, 06:30 PM". */
    fun dateTime(millis: Long): String = "${date(millis)}, ${time(millis)}"

    /** "JUST NOW" (under 5 min) or "N MIN AGO". */
    fun timeAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ChronoUnit.MINUTES.between(Instant.ofEpochMilli(millis), Instant.ofEpochMilli(now))
        return if (minutes < 5) "JUST NOW" else "$minutes MIN AGO"
    }

    fun kg(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
    fun one(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
    fun int(v: Number?): String = v?.let { String.format(Locale.US, "%,d", it.toLong()) } ?: "—"

    /** Parse a user-typed decimal ("72,4" or "72.4"); blank → null. */
    fun parseDouble(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    fun parseInt(s: String): Int? = s.trim().replace(",", "").toIntOrNull()

    fun source(s: Source?): String = when (s) {
        null -> ""
        Source.MANUAL -> "typed"
        Source.FOOD_LOG -> "food log"
        Source.WORKOUT_LOG -> "workout log"
        Source.MFP -> "MyFitnessPal"
        Source.HEALTH_CONNECT -> "Health Connect"
        Source.STRONG_CSV -> "Strong"
        Source.MIGRATED -> "imported history"
    }
}
