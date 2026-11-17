package com.yatharth.yool.domain

import java.time.LocalDate

object Streaks {
    /**
     * Consecutive binge-free days ending at [today] (or yesterday, if today has no check-in yet).
     * Only days where binge == false count; a day with no binge answer breaks the streak.
     */
    fun bingeFree(days: List<ResolvedDay>, today: LocalDate = LocalDate.now()): Int {
        val byDate = days.associateBy { it.date }
        var d = if (byDate[today]?.binge == null) today.minusDays(1) else today
        var count = 0
        while (byDate[d]?.binge == false) {
            count++; d = d.minusDays(1)
        }
        return count
    }
}
