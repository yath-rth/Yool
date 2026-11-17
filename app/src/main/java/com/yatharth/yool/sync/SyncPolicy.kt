package com.yatharth.yool.sync

import java.time.LocalDate

/** Decides how far back a sync reaches. Pure so it can be unit-tested. */
object SyncPolicy {
    const val RECENT_DAYS = 10L
    const val BACKFILL_DAYS = 400L
    const val LIMITED_BACKFILL_DAYS = 30L

    /**
     * @param backfillDone whether a first/full backfill already completed
     * @param historyGranted whether READ_HEALTH_DATA_HISTORY is granted (lets us read more than 30 days back)
     * @param forceBackfill the "re-run full backfill" action
     */
    fun range(today: LocalDate, backfillDone: Boolean, historyGranted: Boolean, forceBackfill: Boolean = false): ClosedRange<LocalDate> {
        val days = when {
            forceBackfill || !backfillDone -> if (historyGranted) BACKFILL_DAYS else LIMITED_BACKFILL_DAYS
            else -> RECENT_DAYS
        }
        return today.minusDays(days)..today
    }

    /** True if this sync counts as completing the backfill. */
    fun completesBackfill(backfillDone: Boolean, forceBackfill: Boolean): Boolean = forceBackfill || !backfillDone
}
