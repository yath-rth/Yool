package com.yatharth.yool.ui

import com.yatharth.yool.data.model.Source
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    private val dayYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

    fun day(d: LocalDate): String = when (d) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> if (d.year == LocalDate.now().year) d.format(dayFmt) else d.format(dayYearFmt)
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
        Source.MFP -> "MyFitnessPal"
        Source.HEALTH_CONNECT -> "Health Connect"
        Source.STRONG_CSV -> "Strong"
        Source.MIGRATED -> "imported history"
    }
}
