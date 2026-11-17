package com.yatharth.yool.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncPolicyTest {
    private val today = LocalDate.of(2026, 6, 1)

    @Test
    fun firstSyncWithHistoryPermissionCoversAYear() {
        val r = SyncPolicy.range(today, backfillDone = false, historyGranted = true)
        assertEquals(today.minusDays(400), r.start)
        assertEquals(today, r.endInclusive)
    }

    @Test
    fun firstSyncWithoutHistoryPermissionIs30Days() {
        assertEquals(today.minusDays(30), SyncPolicy.range(today, false, false).start)
    }

    @Test
    fun normalSyncIs10Days() {
        assertEquals(today.minusDays(10), SyncPolicy.range(today, true, true).start)
    }

    @Test
    fun forcedBackfillOverridesDone() {
        assertEquals(today.minusDays(400), SyncPolicy.range(today, true, true, forceBackfill = true).start)
        assertTrue(SyncPolicy.completesBackfill(true, true))
        assertTrue(SyncPolicy.completesBackfill(false, false))
        assertFalse(SyncPolicy.completesBackfill(true, false))
    }
}
