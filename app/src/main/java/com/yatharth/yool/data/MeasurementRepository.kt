package com.yatharth.yool.data

import androidx.room.withTransaction
import com.yatharth.yool.data.db.BodyMeasurement
import com.yatharth.yool.data.db.YoolDatabase
import com.yatharth.yool.data.model.MeasurementPart
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Body measurements (cm, body fat %). One value per part per day. */
class MeasurementRepository(private val db: YoolDatabase) {
    private val dao = db.measurementDao()

    fun observeAll(): Flow<List<BodyMeasurement>> = dao.observeAll()
    fun observePart(part: MeasurementPart): Flow<List<BodyMeasurement>> = dao.observePart(part)
    suspend fun onDate(date: LocalDate): List<BodyMeasurement> = dao.onDate(date)

    /**
     * Save one day's values: a non-null value sets that part (replacing any value that day), a null
     * value clears it. Parts not in [values] are left alone.
     */
    suspend fun save(date: LocalDate, values: Map<MeasurementPart, Double?>) = db.withTransaction {
        for ((part, v) in values) {
            if (v == null) dao.delete(date, part)
            else {
                val existing = dao.find(date, part)
                dao.upsert(BodyMeasurement(id = existing?.id ?: 0, date = date, part = part, value = v))
            }
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun all(): List<BodyMeasurement> = dao.all()

    /** Merge a backup: only (date, part) pairs not already present are added. */
    suspend fun restore(list: List<BodyMeasurement>) = db.withTransaction {
        for (m in list) if (dao.find(m.date, m.part) == null) dao.upsert(m.copy(id = 0))
    }
}
