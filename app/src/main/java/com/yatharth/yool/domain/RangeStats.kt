package com.yatharth.yool.domain

import java.time.LocalDate

/** Range choices shared by Graphs and Insights. */
enum class RangePreset(val label: String, val days: Long?) {
    W1("1W", 7),
    M1("1M", 30),
    M3("3M", 90),
    Y1("1Y", 365),
    ALL("All", null),
    CUSTOM("Dates", null),
}

object Ranges {
    /**
     * Inclusive (from, to) for a preset. ALL starts at [earliest] (or 30 days back when there is no data);
     * CUSTOM uses the given dates (swapped if reversed) and falls back to 1M when none were picked.
     */
    fun resolve(
        preset: RangePreset,
        today: LocalDate,
        earliest: LocalDate? = null,
        customFrom: LocalDate? = null,
        customTo: LocalDate? = null,
    ): Pair<LocalDate, LocalDate> = when (preset) {
        RangePreset.ALL -> (earliest?.takeIf { !it.isAfter(today) } ?: today.minusDays(29)) to today
        RangePreset.CUSTOM ->
            if (customFrom != null && customTo != null) {
                if (customFrom.isAfter(customTo)) customTo to customFrom else customFrom to customTo
            } else today.minusDays(29) to today
        else -> today.minusDays(preset.days!! - 1) to today
    }
}
