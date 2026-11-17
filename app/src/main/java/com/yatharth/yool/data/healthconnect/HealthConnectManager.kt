package com.yatharth.yool.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.yatharth.yool.data.model.Metric
import com.yatharth.yool.data.model.Source
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

enum class HcAvailability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNAVAILABLE }

/** Thin wrapper over the Health Connect client: availability, permissions and per-day readers. */
class HealthConnectManager(private val context: Context) {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.NEEDS_INSTALL_OR_UPDATE
        else -> HcAvailability.UNAVAILABLE
    }

    // Permissions

    private fun read(type: KClass<out Record>) = HealthPermission.getReadPermission(type)

    val nutritionPermission = read(NutritionRecord::class)
    val stepsPermission = read(StepsRecord::class)
    val sleepPermission = read(SleepSessionRecord::class)
    val exercisePermission = read(ExerciseSessionRecord::class)
    val historyPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
    val backgroundPermission = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val dataPermissions: Set<String> = setOf(nutritionPermission, stepsPermission, sleepPermission, exercisePermission)
    val allPermissions: Set<String> = dataPermissions + historyPermission + backgroundPermission

    /** Display names for the settings screen. */
    val permissionLabels: Map<String, String> = linkedMapOf(
        nutritionPermission to "Nutrition",
        stepsPermission to "Steps",
        sleepPermission to "Sleep",
        exercisePermission to "Workouts",
        historyPermission to "Past data (1 yr)",
        backgroundPermission to "Background sync",
    )

    fun requestPermissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun grantedPermissions(): Set<String> =
        if (availability() == HcAvailability.AVAILABLE) client.permissionController.getGrantedPermissions() else emptySet()

    // Readers (each returns per-local-day values within [from, to])

    suspend fun readNutrition(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayValue> {
        val samples = chunked(from, to, zone) { filter ->
            readAll(NutritionRecord::class, filter).map {
                NutritionSample(
                    id = it.metadata.id,
                    start = it.startTime,
                    origin = it.metadata.dataOrigin.packageName,
                    kcal = it.energy?.inKilocalories,
                    proteinG = it.protein?.inGrams,
                )
            }
        }
        return HealthConnectLogic.nutrition(samples, zone, from, to)
    }

    suspend fun readSteps(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayValue> {
        val out = ArrayList<DayValue>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val chunkEnd = minOf(cursor.plusDays(CHUNK_DAYS - 1), to)
            val response = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.of(cursor, java.time.LocalTime.MIN),
                        LocalDateTime.of(chunkEnd.plusDays(1), java.time.LocalTime.MIN),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
            for (bucket in response) {
                val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: continue
                if (steps > 0) out += DayValue(bucket.startTime.toLocalDate(), Metric.STEPS, Source.HEALTH_CONNECT, steps.toDouble())
            }
            cursor = chunkEnd.plusDays(1)
        }
        return out.distinctBy { it.date }
    }

    suspend fun readSleep(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayValue> {
        val samples = chunked(from, to, zone) { filter ->
            readAll(SleepSessionRecord::class, filter).map { rec ->
                SleepSample(
                    id = rec.metadata.id,
                    start = rec.startTime,
                    end = rec.endTime,
                    stages = rec.stages.map {
                        SleepStageSample(
                            it.startTime, it.endTime,
                            awake = it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE || it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                        )
                    },
                )
            }
        }
        return HealthConnectLogic.sleep(samples, zone, from, to)
    }

    suspend fun readWorkouts(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayValue> {
        val samples = chunked(from, to, zone) { filter ->
            readAll(ExerciseSessionRecord::class, filter).map {
                ExerciseSample(
                    id = it.metadata.id,
                    start = it.startTime,
                    origin = it.metadata.dataOrigin.packageName,
                    strengthLike = it.exerciseType in STRENGTH_TYPES,
                )
            }
        }
        return HealthConnectLogic.workouts(samples, zone, from, to)
    }

    // Helpers

    /** Runs [block] over consecutive [CHUNK_DAYS]-day windows covering from..to and concatenates results. */
    private suspend fun <T> chunked(
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        block: suspend (TimeRangeFilter) -> List<T>,
    ): List<T> {
        val out = ArrayList<T>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            val chunkEnd = minOf(cursor.plusDays(CHUNK_DAYS - 1), to)
            val filter = TimeRangeFilter.between(
                cursor.atStartOfDay(zone).toInstant(),
                chunkEnd.plusDays(1).atStartOfDay(zone).toInstant(),
            )
            out += block(filter)
            cursor = chunkEnd.plusDays(1)
        }
        return out
    }

    /** Reads every page of records of one type in [filter]. */
    private suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageSize = PAGE_SIZE, pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }

    companion object {
        const val CHUNK_DAYS = 30L
        private const val PAGE_SIZE = 1000
        private val STRENGTH_TYPES = setOf(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        )
        const val HC_PACKAGE = "com.google.android.apps.healthdata"
    }
}
