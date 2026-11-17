package com.yatharth.yool.io.export

import java.time.LocalDate

enum class ExportRange(val label: String) {
    DAY("1D"), WEEK("1W"), MONTH("1M"), YEAR("1Y"), CUSTOM("Custom");

    /** Inclusive start for presets ending [today]; null for CUSTOM. */
    fun start(today: LocalDate): LocalDate? = when (this) {
        DAY -> today
        WEEK -> today.minusDays(6)
        MONTH -> today.minusMonths(1).plusDays(1)
        YEAR -> today.minusYears(1).plusDays(1)
        CUSTOM -> null
    }
}
