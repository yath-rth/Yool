package com.yatharth.yool.sync

import com.yatharth.yool.data.TrackerRepository
import com.yatharth.yool.data.db.ImportedValue
import com.yatharth.yool.data.healthconnect.DayValue
import com.yatharth.yool.data.healthconnect.HcAvailability
import com.yatharth.yool.data.healthconnect.HealthConnectManager
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val atMillis: Long, val days: Int) : SyncStatus
    data class Error(val message: String) : SyncStatus
}

/** Pulls Health Connect data into [TrackerRepository] as imported values. */
class SyncEngine(
    private val manager: HealthConnectManager,
    private val repo: TrackerRepository,
    val prefs: HealthConnectPrefs,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var lastAutoAttempt = 0L

    /** Fire-and-forget sync for app open; throttled, never throws. */
    fun launchAutoSync(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastAutoAttempt < AUTO_THROTTLE_MS) return
        lastAutoAttempt = now
        scope.launch { syncAuto() }
    }

    fun launchRecentSync() {
        scope.launch { sync(forceBackfill = false, recentOnly = true) }
    }

    fun launchBackfill() {
        scope.launch { sync(forceBackfill = true) }
    }

    /** First-ever sync backfills; later ones cover the recent window. */
    suspend fun syncAuto(): SyncStatus = sync(forceBackfill = false)

    suspend fun sync(forceBackfill: Boolean, recentOnly: Boolean = false): SyncStatus {
        if (manager.availability() != HcAvailability.AVAILABLE) return _status.value
        val granted = try { manager.grantedPermissions() } catch (e: Exception) { return fail(e) }
        if (granted.none { it in manager.dataPermissions }) return _status.value // nothing connected yet
        if (!mutex.tryLock()) return _status.value // already running
        try {
            _status.value = SyncStatus.Running
            val backfillDone = prefs.isBackfillDone()
            val range = if (recentOnly && backfillDone) {
                SyncPolicy.range(LocalDate.now(), true, false)
            } else {
                SyncPolicy.range(LocalDate.now(), backfillDone, manager.historyPermission in granted, forceBackfill)
            }
            val days = syncRange(range.start, range.endInclusive, granted)
            val now = System.currentTimeMillis()
            prefs.recordSync(now, SyncPolicy.completesBackfill(backfillDone, forceBackfill))
            return SyncStatus.Done(now, days).also { _status.value = it }
        } catch (e: Exception) {
            return fail(e)
        } finally {
            mutex.unlock()
        }
    }

    private fun fail(e: Exception): SyncStatus =
        SyncStatus.Error(e.message ?: e.javaClass.simpleName).also { _status.value = it }

    /** Reads every granted type for [from]..[to] and replaces the matching imported rows. Returns days touched. */
    suspend fun syncRange(from: LocalDate, to: LocalDate, granted: Set<String>): Int {
        val touched = HashSet<LocalDate>()

        suspend fun write(values: List<DayValue>, groups: List<Pair<Source, Metric>>) {
            for ((source, metric) in groups) {
                val rows = values.filter { it.source == source && it.metric == metric }
                    .map { ImportedValue(it.date, it.metric, it.source, it.value) }
                repo.replaceImported(source, metric, from, to, rows)
                rows.forEach { touched += it.date }
            }
        }

        if (manager.nutritionPermission in granted) {
            write(
                manager.readNutrition(from, to),
                listOf(
                    Source.MFP to Metric.CALORIES, Source.MFP to Metric.PROTEIN_G,
                    Source.HEALTH_CONNECT to Metric.CALORIES, Source.HEALTH_CONNECT to Metric.PROTEIN_G,
                ),
            )
        }
        if (manager.stepsPermission in granted) write(manager.readSteps(from, to), listOf(Source.HEALTH_CONNECT to Metric.STEPS))
        if (manager.sleepPermission in granted) write(manager.readSleep(from, to), listOf(Source.HEALTH_CONNECT to Metric.SLEEP_HOURS))
        if (manager.exercisePermission in granted) write(manager.readWorkouts(from, to), listOf(Source.HEALTH_CONNECT to Metric.WORKOUT))
        return touched.size
    }

    private companion object {
        const val AUTO_THROTTLE_MS = 2 * 60_000L
    }
}
