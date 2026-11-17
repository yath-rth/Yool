package com.yatharth.yool.ui.settings.targets

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Required weekly change (kg/week, negative = lose) to reach [goalKg] from [latestKg] by [targetDate].
 * Null if the date is not in the future or inputs are missing.
 */
fun requiredWeeklyRateKg(latestKg: Double?, goalKg: Double, targetDate: LocalDate?, today: LocalDate): Double? {
    if (latestKg == null || targetDate == null) return null
    val days = ChronoUnit.DAYS.between(today, targetDate)
    if (days <= 0) return null
    return (goalKg - latestKg) / (days / 7.0)
}
